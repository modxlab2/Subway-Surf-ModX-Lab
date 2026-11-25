// ================================================================
// Security.cpp — Native Multi-Path Integrity, APK/DEX Verifier & Ed25519 Gate
//
// Security properties:
//   - Debug vs Release decided strictly at compile time via -DDEBUG_BUILD
//     (never passed from Java)
//   - Release builds (!DEBUG_BUILD) FAIL CLOSED on all-zero or unreplaced hashes
//   - Computes APK signing cert (APK Sig Block v2/v3 + PackageManager cross-check)
//     and sorted .dex SHA-256 natively in C++ from ApplicationInfo.sourceDir /
//     getPackageCodePath() / /proc/self/maps
//   - Multi-path checks with split obfuscated state words and periodic background
//     re-verification thread
//   - Feature hooks and toggles gated behind BOTH native verified state and a
//     valid Ed25519 server token
// ================================================================

#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <cstring>
#include <cstdint>
#include <cstdio>
#include <cctype>
#include <cstdlib>
#include <ctime>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <dlfcn.h>
#include <pthread.h>
#include <sys/stat.h>
#include <zlib.h>

#include "Includes/obfuscate.h"
#include "Ed25519.hpp"
#include "SecurityGuard.hpp"

#ifdef SEC_DIAG
#include <android/log.h>
#include <cstdarg>
#endif

#define OBF_STR(s) (static_cast<const char*>(OBFUSCATE(s)))

// ================================================================
// CONFIGURATION (injected at CI release build time)
// ================================================================
static const char* SHA256_PRIMARY() {
    return OBF_STR("__CERT_SHA256__");
}
static const char* SHA256_ALT1() { return OBF_STR(""); }
static const char* SHA256_ALT2() { return OBF_STR(""); }

static const char* ED25519_PUBLIC_KEY() {
    return OBF_STR("__ED25519_PUBLIC_KEY__");
}

static const char* EXPECTED_DEX_HASH() {
    return OBF_STR("__DEX_HASH__");
}

// ================================================================
// Split Integrity & Token State
// ================================================================
static constexpr uint32_t kSeedA1 = 0x9A7C5E31u;
static constexpr uint32_t kSeedA2 = 0x11223344u;
static constexpr uint32_t kSeedB1 = 0x4B8D2F69u;
static constexpr uint32_t kSeedB2 = 0x55667788u;
static constexpr uint32_t kSeedC1 = 0x71E93A4Du;
static constexpr uint32_t kSeedT1 = 0x7E3A9C51u;

static std::atomic<bool>     g_Verified{false};
static std::atomic<uint32_t> g_IntegrityWordA{0x13572468u};
static std::atomic<uint32_t> g_IntegrityWordB{0x24681357u};
static std::atomic<uint32_t> g_IntegrityWordC{0xABCDEF01u};

static std::atomic<bool>     g_TokenVerified{false};
static std::atomic<uint32_t> g_TokenWord{0x01020304u};
static std::atomic<int64_t>  g_TokenExpiryMs{0};
static std::atomic<int64_t>  g_SessionMonoDeadlineMs{0};
static std::atomic<int64_t>  g_HighestSeenWallMs{0};
static std::atomic<uint32_t> g_ToggleCounter{0};
static std::atomic<uint32_t> g_ReverifyTick{0};

static std::mutex g_SecMutex;
static JavaVM* g_JavaVm = nullptr;
static std::string g_CachedApkPath;
static bool g_HasExpectedApkStat = false;
static uint64_t g_ExpectedApkSize = 0;
static uint64_t g_ExpectedApkInode = 0;
static std::vector<uint8_t> g_CachedEd25519Pk;

static std::string g_ActiveToken;
static std::string g_ActiveUser;
static std::string g_ActiveDeviceId;
static std::string g_ActiveNonce;

static std::atomic<bool> g_WatchdogStarted{false};
static std::atomic<SecurityGuard::LockdownCallback> g_LockdownCb{nullptr};

static int64_t monotonicMs() {
    struct timespec ts{};
    if (clock_gettime(CLOCK_MONOTONIC, &ts) == 0) {
        return (int64_t)ts.tv_sec * 1000LL + (int64_t)(ts.tv_nsec / 1000000LL);
    }
    return (int64_t)time(nullptr) * 1000LL;
}

static bool updateAndCheckWallClock(int64_t nowWallMs) {
    if (nowWallMs <= 0) return false;
    int64_t prev = g_HighestSeenWallMs.load(std::memory_order_acquire);
    if (prev > 0 && nowWallMs + 120000LL < prev) {
        return false; // Clock rolled back > 2 minutes
    }
    while (nowWallMs > prev) {
        if (g_HighestSeenWallMs.compare_exchange_weak(
                prev, nowWallMs, std::memory_order_acq_rel, std::memory_order_acquire)) {
            break;
        }
    }
    return true;
}

static inline void markWordA(bool ok) {
    g_IntegrityWordA.store(ok ? (kSeedA1 ^ kSeedA2) : 0xDEAD0001u, std::memory_order_release);
}
static inline void markWordB(bool ok) {
    g_IntegrityWordB.store(ok ? (kSeedB1 ^ kSeedB2) : 0xDEAD0002u, std::memory_order_release);
}
static inline void markWordC(bool ok) {
    g_IntegrityWordC.store(ok ? (kSeedC1 ^ kSeedA2) : 0xDEAD0003u, std::memory_order_release);
}
static inline bool isWordAOk() {
    return g_IntegrityWordA.load(std::memory_order_acquire) == (kSeedA1 ^ kSeedA2);
}
static inline bool isWordBOk() {
    return g_IntegrityWordB.load(std::memory_order_acquire) == (kSeedB1 ^ kSeedB2);
}
static inline bool isWordCOk() {
    return g_IntegrityWordC.load(std::memory_order_acquire) == (kSeedC1 ^ kSeedA2);
}

static void triggerLockdown(bool forceExit) {
    g_Verified.store(false, std::memory_order_release);
    g_TokenVerified.store(false, std::memory_order_release);
    g_IntegrityWordA.store(0u, std::memory_order_release);
    g_IntegrityWordB.store(0u, std::memory_order_release);
    g_IntegrityWordC.store(0u, std::memory_order_release);
    g_TokenWord.store(0u, std::memory_order_release);
    g_TokenExpiryMs.store(0, std::memory_order_release);
    g_SessionMonoDeadlineMs.store(0, std::memory_order_release);

    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        g_ActiveToken.clear();
        g_ActiveUser.clear();
        g_ActiveDeviceId.clear();
        g_ActiveNonce.clear();
    }

    SecurityGuard::LockdownCallback cb = g_LockdownCb.load(std::memory_order_acquire);
    if (cb) cb();

    if (forceExit) {
#ifndef DEBUG_BUILD
        _exit(1);
#endif
    }
}

// ================================================================
// String, Hex, Base64URL & JSON helpers
// ================================================================
static std::string toLower(std::string s) {
    for (auto& c : s) c = (char)std::tolower((unsigned char)c);
    return s;
}

static bool constTimeEquals(const std::string& a, const std::string& b) {
    if (a.size() != b.size()) return false;
    unsigned char diff = 0;
    for (size_t i = 0; i < a.size(); i++) diff |= (unsigned char)(a[i] ^ b[i]);
    return diff == 0;
}

static bool isValidSha256Hex(const std::string& s) {
    if (s.size() != 64) return false;
    bool allZero = true;
    for (unsigned char c : s) {
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
            return false;
        }
        if (c != '0') allZero = false;
    }
    return !allZero;
}

static bool isAllZeroOrPlaceholder(const std::string& s) {
    if (s.empty()) return true;
    if (s.find("__") != std::string::npos) return true;
    for (char c : s) {
        if (c != '0') return false;
    }
    return true;
}

static bool hexDecode(const std::string& hex, std::vector<uint8_t>& out) {
    if (hex.empty() || (hex.size() % 2) != 0) return false;
    out.clear();
    out.reserve(hex.size() / 2);
    auto nib = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    };
    for (size_t i = 0; i < hex.size(); i += 2) {
        int hi = nib(hex[i]);
        int lo = nib(hex[i + 1]);
        if (hi < 0 || lo < 0) return false;
        out.push_back((uint8_t)((hi << 4) | lo));
    }
    return true;
}

static bool base64UrlDecode(const std::string& in, std::vector<uint8_t>& out) {
    if (in.empty()) return false;
    size_t len = in.size();
    while (len > 0 && in[len - 1] == '=') len--;
    if (len % 4 == 1) return false;

    auto val = [](unsigned char c) -> int {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '-' || c == '+') return 62;
        if (c == '_' || c == '/') return 63;
        return -1;
    };

    out.clear();
    out.reserve((len * 3) / 4);
    uint32_t acc = 0;
    int bits = 0;
    for (size_t i = 0; i < len; ++i) {
        int v = val((unsigned char)in[i]);
        if (v < 0) return false;
        acc = (acc << 6) | (uint32_t)v;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out.push_back((uint8_t)((acc >> bits) & 0xFF));
        }
    }
    return true;
}

static std::string getJsonField(const std::string& json, const std::string& key) {
    std::string needle = "\"" + key + "\"";
    size_t k = json.find(needle);
    if (k == std::string::npos) return "";
    size_t colon = json.find(':', k + needle.size());
    if (colon == std::string::npos) return "";
    size_t i = colon + 1;
    while (i < json.size() && std::isspace((unsigned char)json[i])) i++;
    if (i >= json.size()) return "";
    if (json[i] == '"') {
        size_t e = json.find('"', i + 1);
        if (e == std::string::npos) return "";
        return json.substr(i + 1, e - i - 1);
    }
    size_t e = i;
    while (e < json.size() && json[e] != ',' && json[e] != '}') e++;
    std::string v = json.substr(i, e - i);
    while (!v.empty() && std::isspace((unsigned char)v.back())) v.pop_back();
    return v;
}

// ================================================================
// SHA-256 (RFC 6234)
// ================================================================
struct Sha256Ctx {
    uint32_t state[8];
    uint64_t bitlen;
    uint8_t  data[64];
    size_t   datalen;
};

static inline uint32_t rotr32(uint32_t x, uint32_t n) {
    return (x >> n) | (x << (32 - n));
}

static void sha256Transform(Sha256Ctx* ctx, const uint8_t block[64]) {
    static const uint32_t K[64] = {
        0x428a2f98u,0x71374491u,0xb5c0fbcfu,0xe9b5dba5u,0x3956c25bu,0x59f111f1u,0x923f82a4u,0xab1c5ed5u,
        0xd807aa98u,0x12835b01u,0x243185beu,0x550c7dc3u,0x72be5d74u,0x80deb1feu,0x9bdc06a7u,0xc19bf174u,
        0xe49b69c1u,0xefbe4786u,0x0fc19dc6u,0x240ca1ccu,0x2de92c6fu,0x4a7484aau,0x5cb0a9dcu,0x76f988dau,
        0x983e5152u,0xa831c66du,0xb00327c8u,0xbf597fc7u,0xc6e00bf3u,0xd5a79147u,0x06ca6351u,0x14292967u,
        0x27b70a85u,0x2e1b2138u,0x4d2c6dfcu,0x53380d13u,0x650a7354u,0x766a0abbu,0x81c2c92eu,0x92722c85u,
        0xa2bfe8a1u,0xa81a664bu,0xc24b8b70u,0xc76c51a3u,0xd192e819u,0xd6990624u,0xf40e3585u,0x106aa070u,
        0x19a4c116u,0x1e376c08u,0x2748774cu,0x34b0bcb5u,0x391c0cb3u,0x4ed8aa4au,0x5b9cca4fu,0x682e6ff3u,
        0x748f82eeu,0x78a5636fu,0x84c87814u,0x8cc70208u,0x90befffau,0xa4506cebu,0xbef9a3f7u,0xc67178f2u
    };

    uint32_t m[64];
    for (int i = 0; i < 16; ++i) {
        m[i] = ((uint32_t)block[i * 4] << 24) | ((uint32_t)block[i * 4 + 1] << 16) |
               ((uint32_t)block[i * 4 + 2] << 8) | (uint32_t)block[i * 4 + 3];
    }
    for (int i = 16; i < 64; ++i) {
        uint32_t s0 = rotr32(m[i - 15], 7) ^ rotr32(m[i - 15], 18) ^ (m[i - 15] >> 3);
        uint32_t s1 = rotr32(m[i - 2], 17) ^ rotr32(m[i - 2], 19) ^ (m[i - 2] >> 10);
        m[i] = m[i - 16] + s0 + m[i - 7] + s1;
    }

    uint32_t a = ctx->state[0], b = ctx->state[1], c = ctx->state[2], d = ctx->state[3];
    uint32_t e = ctx->state[4], f = ctx->state[5], g = ctx->state[6], h = ctx->state[7];

    for (int i = 0; i < 64; ++i) {
        uint32_t S1 = rotr32(e, 6) ^ rotr32(e, 11) ^ rotr32(e, 25);
        uint32_t ch = (e & f) ^ ((~e) & g);
        uint32_t temp1 = h + S1 + ch + K[i] + m[i];
        uint32_t S0 = rotr32(a, 2) ^ rotr32(a, 13) ^ rotr32(a, 22);
        uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        uint32_t temp2 = S0 + maj;

        h = g; g = f; f = e; e = d + temp1;
        d = c; c = b; b = a; a = temp1 + temp2;
    }

    ctx->state[0] += a; ctx->state[1] += b; ctx->state[2] += c; ctx->state[3] += d;
    ctx->state[4] += e; ctx->state[5] += f; ctx->state[6] += g; ctx->state[7] += h;
}

static void sha256Init(Sha256Ctx* ctx) {
    ctx->state[0] = 0x6a09e667u; ctx->state[1] = 0xbb67ae85u;
    ctx->state[2] = 0x3c6ef372u; ctx->state[3] = 0xa54ff53au;
    ctx->state[4] = 0x510e527fu; ctx->state[5] = 0x9b05688cu;
    ctx->state[6] = 0x1f83d9abu; ctx->state[7] = 0x5be0cd19u;
    ctx->bitlen = 0;
    ctx->datalen = 0;
}

static void sha256Update(Sha256Ctx* ctx, const uint8_t* data, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        ctx->data[ctx->datalen++] = data[i];
        if (ctx->datalen == 64) {
            sha256Transform(ctx, ctx->data);
            ctx->bitlen += 512;
            ctx->datalen = 0;
        }
    }
}

static void sha256Final(Sha256Ctx* ctx, uint8_t hash[32]) {
    size_t i = ctx->datalen;
    if (ctx->datalen < 56) {
        ctx->data[i++] = 0x80;
        while (i < 56) ctx->data[i++] = 0x00;
    } else {
        ctx->data[i++] = 0x80;
        while (i < 64) ctx->data[i++] = 0x00;
        sha256Transform(ctx, ctx->data);
        memset(ctx->data, 0, 56);
    }

    ctx->bitlen += ctx->datalen * 8ULL;
    ctx->data[63] = (uint8_t)(ctx->bitlen);
    ctx->data[62] = (uint8_t)(ctx->bitlen >> 8);
    ctx->data[61] = (uint8_t)(ctx->bitlen >> 16);
    ctx->data[60] = (uint8_t)(ctx->bitlen >> 24);
    ctx->data[59] = (uint8_t)(ctx->bitlen >> 32);
    ctx->data[58] = (uint8_t)(ctx->bitlen >> 40);
    ctx->data[57] = (uint8_t)(ctx->bitlen >> 48);
    ctx->data[56] = (uint8_t)(ctx->bitlen >> 56);
    sha256Transform(ctx, ctx->data);

    for (i = 0; i < 8; ++i) {
        hash[i * 4 + 0] = (uint8_t)((ctx->state[i] >> 24) & 0xffu);
        hash[i * 4 + 1] = (uint8_t)((ctx->state[i] >> 16) & 0xffu);
        hash[i * 4 + 2] = (uint8_t)((ctx->state[i] >> 8) & 0xffu);
        hash[i * 4 + 3] = (uint8_t)(ctx->state[i] & 0xffu);
    }
}

static std::string bytesToLowerHex(const uint8_t* data, size_t len) {
    static const char kHex[] = "0123456789abcdef";
    std::string out;
    out.resize(len * 2);
    for (size_t i = 0; i < len; ++i) {
        out[i * 2]     = kHex[(data[i] >> 4) & 0x0Fu];
        out[i * 2 + 1] = kHex[data[i] & 0x0Fu];
    }
    return out;
}

static std::string sha256HexBytes(const uint8_t* data, size_t len) {
    uint8_t digest[32];
    Sha256Ctx ctx;
    sha256Init(&ctx);
    if (len > 0) sha256Update(&ctx, data, len);
    sha256Final(&ctx, digest);
    return bytesToLowerHex(digest, 32);
}

// ================================================================
// Native APK Parser: EOCD, Central Directory, Sorted .dex SHA-256,
// and APK Signature Scheme v2 / v3 Certificate SHA-256
// ================================================================
static inline uint16_t readU16(const uint8_t* p) {
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

static inline uint32_t readU32(const uint8_t* p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
           ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static inline uint64_t readU64(const uint8_t* p) {
    return (uint64_t)readU32(p) | ((uint64_t)readU32(p + 4) << 32);
}

static bool preadExact(int fd, uint64_t offset, void* dst, size_t len) {
    uint8_t* out = static_cast<uint8_t*>(dst);
    size_t done = 0;
    while (done < len) {
        ssize_t n = pread(fd, out + done, len - done, (off64_t)(offset + done));
        if (n <= 0) return false;
        done += (size_t)n;
    }
    return true;
}

struct ApkFileLayout {
    uint64_t fileSize = 0;
    uint64_t cdOffset = 0;
    uint32_t cdSize   = 0;
};

static bool parseZipEocd(int fd, ApkFileLayout& layout) {
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size < 22) return false;
    uint64_t fsz = (uint64_t)st.st_size;
    layout.fileSize = fsz;

    size_t tailLen = (size_t)std::min<uint64_t>(fsz, 65558ULL);
    uint64_t tailStart = fsz - tailLen;
    std::vector<uint8_t> tail(tailLen);
    if (!preadExact(fd, tailStart, tail.data(), tailLen)) return false;

    for (size_t i = tailLen - 22 + 1; i-- > 0;) {
        if (readU32(&tail[i]) == 0x06054b50u) {
            uint16_t commentLen = readU16(&tail[i + 20]);
            if (i + 22 + commentLen == tailLen) {
                uint32_t cdSize = readU32(&tail[i + 12]);
                uint32_t cdOff  = readU32(&tail[i + 16]);
                if (cdSize == 0xFFFFFFFFu || cdOff == 0xFFFFFFFFu) return false;
                if ((uint64_t)cdOff + (uint64_t)cdSize > fsz) return false;
                layout.cdOffset = (uint64_t)cdOff;
                layout.cdSize   = cdSize;
                return true;
            }
        }
    }
    return false;
}

static bool extractV2V3CertFromSchemeBlock(const uint8_t* data, size_t len, std::string& outCertHex) {
    // Layout: uint32 signers_len -> uint32 signer_len -> uint32 signed_data_len
    //         -> uint32 digests_len + bytes -> uint32 certs_len -> uint32 first_cert_len + DER
    if (len < 20) return false;
    size_t pos = 0;
    uint32_t signersLen = readU32(data + pos); pos += 4;
    if (pos + signersLen > len || signersLen < 12) return false;

    uint32_t signerLen = readU32(data + pos); pos += 4;
    if (pos + signerLen > len || signerLen < 12) return false;

    uint32_t signedDataLen = readU32(data + pos); pos += 4;
    if (pos + signedDataLen > len || signedDataLen < 12) return false;

    uint32_t digestsLen = readU32(data + pos); pos += 4;
    if (pos + digestsLen + 4 > len) return false;
    pos += digestsLen;

    uint32_t certsLen = readU32(data + pos); pos += 4;
    if (pos + certsLen > len || certsLen < 4) return false;

    uint32_t firstCertLen = readU32(data + pos); pos += 4;
    if (firstCertLen == 0 || pos + firstCertLen > len) return false;

    outCertHex = sha256HexBytes(data + pos, firstCertLen);
    return outCertHex.size() == 64;
}

static bool extractApkV2V3CertSha256(int fd, const ApkFileLayout& layout, std::string& outCertHex) {
    if (layout.cdOffset < 32) return false;

    uint8_t footer[24];
    if (!preadExact(fd, layout.cdOffset - 24, footer, 24)) return false;

    static const char kMagic[16] = {
        'A','P','K',' ','S','i','g',' ','B','l','o','c','k',' ','4','2'
    };
    if (memcmp(footer + 8, kMagic, 16) != 0) return false;

    uint64_t blockSize = readU64(footer);
    if (blockSize < 24 || blockSize > 16ULL * 1024ULL * 1024ULL) return false;
    if (blockSize + 8 > layout.cdOffset) return false;

    uint64_t blockStart = layout.cdOffset - (blockSize + 8);
    uint8_t headBuf[8];
    if (!preadExact(fd, blockStart, headBuf, 8)) return false;
    if (readU64(headBuf) != blockSize) return false;

    size_t payloadLen = (size_t)(blockSize - 24);
    std::vector<uint8_t> block(payloadLen);
    if (payloadLen > 0 && !preadExact(fd, blockStart + 8, block.data(), payloadLen)) {
        return false;
    }

    size_t pos = 0;
    std::string v2Cert, v3Cert;
    while (pos + 12 <= payloadLen) {
        uint64_t pairLen = readU64(&block[pos]);
        pos += 8;
        if (pairLen < 4 || pos + pairLen > payloadLen) break;
        uint32_t id = readU32(&block[pos]);
        const uint8_t* val = &block[pos + 4];
        size_t valLen = (size_t)(pairLen - 4);
        pos += (size_t)pairLen;

        if (id == 0x7109871au && v2Cert.empty()) {
            extractV2V3CertFromSchemeBlock(val, valLen, v2Cert);
        } else if (id == 0xf05368c0u && v3Cert.empty()) {
            extractV2V3CertFromSchemeBlock(val, valLen, v3Cert);
        }
    }

    if (!v3Cert.empty()) { outCertHex = v3Cert; return true; }
    if (!v2Cert.empty()) { outCertHex = v2Cert; return true; }
    return false;
}

struct ZipDexEntry {
    std::string name;
    uint16_t method;
    uint32_t compSize;
    uint32_t uncompSize;
    uint32_t localOffset;
};

static bool computeApkDexHashes(int fd,
                                const ApkFileLayout& layout,
                                std::vector<std::string>& outEntryNames,
                                std::vector<std::string>& outEntryHexes) {
    outEntryNames.clear();
    outEntryHexes.clear();
    if (layout.cdSize == 0 || layout.cdSize > 32U * 1024U * 1024U) return false;
    std::vector<uint8_t> cd(layout.cdSize);
    if (!preadExact(fd, layout.cdOffset, cd.data(), layout.cdSize)) return false;

    std::vector<ZipDexEntry> dexEntries;
    size_t pos = 0;
    while (pos + 46 <= cd.size()) {
        if (readU32(&cd[pos]) != 0x02014b50u) break;
        uint16_t method     = readU16(&cd[pos + 10]);
        uint32_t compSize   = readU32(&cd[pos + 20]);
        uint32_t uncompSize = readU32(&cd[pos + 24]);
        uint16_t nameLen    = readU16(&cd[pos + 28]);
        uint16_t extraLen   = readU16(&cd[pos + 30]);
        uint16_t commentLen = readU16(&cd[pos + 32]);
        uint32_t localOff   = readU32(&cd[pos + 42]);
        pos += 46;
        if (pos + nameLen + extraLen + commentLen > cd.size()) return false;

        std::string name(reinterpret_cast<const char*>(&cd[pos]), nameLen);
        pos += nameLen + extraLen + commentLen;

        if (name.size() >= 4 && name.compare(name.size() - 4, 4, ".dex") == 0) {
            dexEntries.push_back({name, method, compSize, uncompSize, localOff});
        }
    }

    if (dexEntries.empty()) return false;
    std::sort(dexEntries.begin(), dexEntries.end(),
              [](const ZipDexEntry& a, const ZipDexEntry& b) { return a.name < b.name; });

    std::vector<uint8_t> inBuf(16384);
    std::vector<uint8_t> outBuf(32768);

    for (const auto& entry : dexEntries) {
        Sha256Ctx entrySha;
        sha256Init(&entrySha);

        uint8_t lh[30];
        if ((uint64_t)entry.localOffset + 30 > layout.fileSize) return false;
        if (!preadExact(fd, entry.localOffset, lh, 30)) return false;
        if (readU32(lh) != 0x04034b50u) return false;
        uint16_t lNameLen  = readU16(lh + 26);
        uint16_t lExtraLen = readU16(lh + 28);
        uint64_t dataOff = (uint64_t)entry.localOffset + 30ULL + lNameLen + lExtraLen;
        if (dataOff + entry.compSize > layout.fileSize) return false;

        if (entry.method == 0) {
            uint32_t rem = entry.compSize;
            uint64_t cur = dataOff;
            while (rem > 0) {
                size_t chunk = std::min<size_t>(rem, inBuf.size());
                if (!preadExact(fd, cur, inBuf.data(), chunk)) return false;
                sha256Update(&entrySha, inBuf.data(), chunk);
                cur += chunk;
                rem -= (uint32_t)chunk;
            }
        } else if (entry.method == 8) {
            z_stream zs{};
            if (inflateInit2(&zs, -MAX_WBITS) != Z_OK) return false;

            uint32_t rem = entry.compSize;
            uint64_t cur = dataOff;
            uint64_t totalOut = 0;
            bool streamDone = false;
            bool failed = false;

            while (!streamDone) {
                if (zs.avail_in == 0 && rem > 0) {
                    size_t chunk = std::min<size_t>(rem, inBuf.size());
                    if (!preadExact(fd, cur, inBuf.data(), chunk)) {
                        failed = true;
                        break;
                    }
                    cur += chunk;
                    rem -= (uint32_t)chunk;
                    zs.next_in = inBuf.data();
                    zs.avail_in = (uInt)chunk;
                }

                zs.next_out = outBuf.data();
                zs.avail_out = (uInt)outBuf.size();
                int rc = inflate(&zs, rem == 0 ? Z_FINISH : Z_NO_FLUSH);
                size_t have = outBuf.size() - zs.avail_out;
                if (have > 0) {
                    sha256Update(&entrySha, outBuf.data(), have);
                    totalOut += have;
                }
                if (rc == Z_STREAM_END) {
                    streamDone = true;
                } else if (rc != Z_OK && rc != Z_BUF_ERROR) {
                    failed = true;
                    break;
                } else if (zs.avail_in == 0 && rem == 0 && have == 0) {
                    failed = true;
                    break;
                }
            }
            inflateEnd(&zs);
            if (failed || totalOut != entry.uncompSize) return false;
        } else {
            return false;
        }

        uint8_t entryDigest[32];
        sha256Final(&entrySha, entryDigest);
        outEntryNames.push_back(entry.name);
        outEntryHexes.push_back(bytesToLowerHex(entryDigest, 32));
    }

    return !outEntryHexes.empty();
}

// ================================================================
// JNI Helpers for APK path & PackageManager signing cert (native C++)
// ================================================================
static void clearJniException(JNIEnv* env) {
    if (env && env->ExceptionCheck()) env->ExceptionClear();
}

static std::string jstringToStdString(JNIEnv* env, jstring js) {
    if (!env || !js) return "";
    const char* utf = env->GetStringUTFChars(js, nullptr);
    if (!utf) { clearJniException(env); return ""; }
    std::string out(utf);
    env->ReleaseStringUTFChars(js, utf);
    return out;
}

static std::string getProcessPackageName() {
    char buf[256] = {0};
    int fd = open("/proc/self/cmdline", O_RDONLY);
    if (fd >= 0) {
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n <= 0) return "";
    } else {
        return "";
    }
    std::string s(buf);
    size_t nul = s.find('\0');
    if (nul != std::string::npos) s = s.substr(0, nul);
    size_t col = s.find(':');
    if (col != std::string::npos) s = s.substr(0, col);
    return s;
}

static std::string findApkPathFromProcMaps() {
    FILE* fp = fopen("/proc/self/maps", "r");
    if (!fp) return "";

    std::string pkg = getProcessPackageName();
    char line[1024];
    std::string fallbackApk;
    std::string matchedApk;

    while (fgets(line, sizeof(line), fp)) {
        const char* slash = strchr(line, '/');
        if (!slash) continue;
        std::string path(slash);
        while (!path.empty() && (path.back() == '\n' || path.back() == '\r')) path.pop_back();
        if (path.size() < 5) continue;

        if (path.compare(0, 8, "/system/") == 0 ||
            path.compare(0, 8, "/vendor/") == 0 ||
            path.compare(0, 6, "/apex/") == 0) {
            continue;
        }

        if (path.size() >= 4 && path.compare(path.size() - 4, 4, ".apk") == 0) {
            if (!pkg.empty() && path.find(pkg) != std::string::npos) {
                if (path.find("base.apk") != std::string::npos) {
                    matchedApk = path;
                    break;
                }
                if (matchedApk.empty()) matchedApk = path;
            } else if (fallbackApk.empty() && path.find("base.apk") != std::string::npos) {
                fallbackApk = path;
            }
        }
    }
    fclose(fp);
    return !matchedApk.empty() ? matchedApk : fallbackApk;
}

static jobject getCurrentApplicationViaActivityThread(JNIEnv* env) {
    if (!env) return nullptr;
    jclass atCls = env->FindClass(OBF_STR("android/app/ActivityThread"));
    if (!atCls) { clearJniException(env); return nullptr; }
    jmethodID curAppMid = env->GetStaticMethodID(
            atCls, OBF_STR("currentApplication"), OBF_STR("()Landroid/app/Application;"));
    if (!curAppMid) {
        clearJniException(env);
        env->DeleteLocalRef(atCls);
        return nullptr;
    }
    jobject app = env->CallStaticObjectMethod(atCls, curAppMid);
    if (env->ExceptionCheck()) {
        clearJniException(env);
        app = nullptr;
    }
    env->DeleteLocalRef(atCls);
    return app;
}

static std::string getApkPathFromSourceDir(JNIEnv* env, jobject ctx) {
    if (!env || !ctx) return "";
    jclass ctxCls = env->GetObjectClass(ctx);
    if (!ctxCls) { clearJniException(env); return ""; }

    jmethodID getAppInfo = env->GetMethodID(
            ctxCls, OBF_STR("getApplicationInfo"), OBF_STR("()Landroid/content/pm/ApplicationInfo;"));
    if (!getAppInfo) {
        clearJniException(env);
        env->DeleteLocalRef(ctxCls);
        return "";
    }
    jobject appInfo = env->CallObjectMethod(ctx, getAppInfo);
    if (env->ExceptionCheck() || !appInfo) {
        clearJniException(env);
        env->DeleteLocalRef(ctxCls);
        return "";
    }

    jclass appInfoCls = env->GetObjectClass(appInfo);
    jfieldID srcDirFid = env->GetFieldID(appInfoCls, OBF_STR("sourceDir"), OBF_STR("Ljava/lang/String;"));
    std::string out;
    if (srcDirFid) {
        jstring jSrc = (jstring)env->GetObjectField(appInfo, srcDirFid);
        if (!env->ExceptionCheck() && jSrc) {
            out = jstringToStdString(env, jSrc);
            env->DeleteLocalRef(jSrc);
        } else {
            clearJniException(env);
        }
    } else {
        clearJniException(env);
    }

    env->DeleteLocalRef(appInfoCls);
    env->DeleteLocalRef(appInfo);
    env->DeleteLocalRef(ctxCls);
    return out;
}

static std::string getApkPathFromCodePath(JNIEnv* env, jobject ctx) {
    if (!env || !ctx) return "";
    jclass ctxCls = env->GetObjectClass(ctx);
    if (!ctxCls) { clearJniException(env); return ""; }
    jmethodID mid = env->GetMethodID(ctxCls, OBF_STR("getPackageCodePath"), OBF_STR("()Ljava/lang/String;"));
    std::string out;
    if (mid) {
        jstring jPath = (jstring)env->CallObjectMethod(ctx, mid);
        if (!env->ExceptionCheck() && jPath) {
            out = jstringToStdString(env, jPath);
            env->DeleteLocalRef(jPath);
        } else {
            clearJniException(env);
        }
    } else {
        clearJniException(env);
    }
    env->DeleteLocalRef(ctxCls);
    return out;
}

static std::string readPmCertSha256(JNIEnv* env, jobject ctx) {
    if (!env || !ctx) return "";
    jclass ctxCls = env->GetObjectClass(ctx);
    if (!ctxCls) { clearJniException(env); return ""; }

    jmethodID getPm = env->GetMethodID(
            ctxCls, OBF_STR("getPackageManager"), OBF_STR("()Landroid/content/pm/PackageManager;"));
    jmethodID getPkg = env->GetMethodID(
            ctxCls, OBF_STR("getPackageName"), OBF_STR("()Ljava/lang/String;"));
    if (!getPm || !getPkg) {
        clearJniException(env);
        env->DeleteLocalRef(ctxCls);
        return "";
    }

    jobject pm = env->CallObjectMethod(ctx, getPm);
    jstring pkg = (jstring)env->CallObjectMethod(ctx, getPkg);
    if (env->ExceptionCheck() || !pm || !pkg) {
        clearJniException(env);
        if (pm) env->DeleteLocalRef(pm);
        if (pkg) env->DeleteLocalRef(pkg);
        env->DeleteLocalRef(ctxCls);
        return "";
    }

    int sdkInt = 0;
    jclass buildVerCls = env->FindClass(OBF_STR("android/os/Build$VERSION"));
    if (buildVerCls) {
        jfieldID sdkFid = env->GetStaticFieldID(buildVerCls, OBF_STR("SDK_INT"), OBF_STR("I"));
        if (sdkFid) sdkInt = env->GetStaticIntField(buildVerCls, sdkFid);
        env->DeleteLocalRef(buildVerCls);
    }
    clearJniException(env);

    jclass pmCls = env->GetObjectClass(pm);
    jmethodID getPi = env->GetMethodID(
            pmCls, OBF_STR("getPackageInfo"), OBF_STR("(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;"));
    if (!getPi) {
        clearJniException(env);
        env->DeleteLocalRef(pmCls);
        env->DeleteLocalRef(pm);
        env->DeleteLocalRef(pkg);
        env->DeleteLocalRef(ctxCls);
        return "";
    }

    const jint flags = (sdkInt >= 28) ? 0x08000000 /* GET_SIGNING_CERTIFICATES */ : 0x00000040 /* GET_SIGNATURES */;
    jobject pi = env->CallObjectMethod(pm, getPi, pkg, flags);
    if (env->ExceptionCheck() || !pi) {
        clearJniException(env);
        env->DeleteLocalRef(pmCls);
        env->DeleteLocalRef(pm);
        env->DeleteLocalRef(pkg);
        env->DeleteLocalRef(ctxCls);
        return "";
    }

    jclass piCls = env->GetObjectClass(pi);
    jobjectArray sigArr = nullptr;
    if (sdkInt >= 28) {
        jfieldID siFid = env->GetFieldID(piCls, OBF_STR("signingInfo"), OBF_STR("Landroid/content/pm/SigningInfo;"));
        if (siFid) {
            jobject si = env->GetObjectField(pi, siFid);
            if (si && !env->ExceptionCheck()) {
                jclass siCls = env->GetObjectClass(si);
                jmethodID hasMulti = env->GetMethodID(siCls, OBF_STR("hasMultipleSigners"), OBF_STR("()Z"));
                jboolean multi = hasMulti ? env->CallBooleanMethod(si, hasMulti) : JNI_FALSE;
                jmethodID getSigs = env->GetMethodID(
                        siCls,
                        multi ? OBF_STR("getApkContentsSigners") : OBF_STR("getSigningCertificateHistory"),
                        OBF_STR("()[Landroid/content/pm/Signature;"));
                if (getSigs) {
                    sigArr = (jobjectArray)env->CallObjectMethod(si, getSigs);
                }
                env->DeleteLocalRef(siCls);
                env->DeleteLocalRef(si);
            }
        }
    } else {
        jfieldID sigsFid = env->GetFieldID(piCls, OBF_STR("signatures"), OBF_STR("[Landroid/content/pm/Signature;"));
        if (sigsFid) {
            sigArr = (jobjectArray)env->GetObjectField(pi, sigsFid);
        }
    }
    clearJniException(env);

    std::string outHex;
    if (sigArr && env->GetArrayLength(sigArr) > 0) {
        jobject sig0 = env->GetObjectArrayElement(sigArr, 0);
        if (sig0) {
            jclass sigCls = env->GetObjectClass(sig0);
            jmethodID toBytes = env->GetMethodID(sigCls, OBF_STR("toByteArray"), OBF_STR("()[B"));
            if (toBytes) {
                jbyteArray raw = (jbyteArray)env->CallObjectMethod(sig0, toBytes);
                if (raw && !env->ExceptionCheck()) {
                    jsize len = env->GetArrayLength(raw);
                    if (len > 0) {
                        std::vector<uint8_t> der((size_t)len);
                        env->GetByteArrayRegion(raw, 0, len, reinterpret_cast<jbyte*>(der.data()));
                        outHex = sha256HexBytes(der.data(), der.size());
                    }
                    env->DeleteLocalRef(raw);
                }
            }
            env->DeleteLocalRef(sigCls);
            env->DeleteLocalRef(sig0);
        }
        env->DeleteLocalRef(sigArr);
    }

    clearJniException(env);
    env->DeleteLocalRef(piCls);
    env->DeleteLocalRef(pi);
    env->DeleteLocalRef(pmCls);
    env->DeleteLocalRef(pm);
    env->DeleteLocalRef(pkg);
    env->DeleteLocalRef(ctxCls);
    return outHex;
}

// ================================================================
// Detection helpers — all SILENT
// ================================================================
static bool detectFrida() {
    FILE* fp = fopen("/proc/self/maps", "r");
    if (fp) {
        char line[512];
        bool found = false;
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, "frida-agent") ||
                strstr(line, "frida-gadget") ||
                strstr(line, "libfrida-gadget") ||
                strstr(line, "libfrida-agent") ||
                strstr(line, "gum-js-loop") ||
                strstr(line, "frida-server") ||
                strstr(line, "re.frida.server")) {
                found = true;
                break;
            }
        }
        fclose(fp);
        if (found) return true;
    }

    DIR* dir = opendir("/data/local/tmp");
    if (dir) {
        struct dirent* de;
        bool found = false;
        while ((de = readdir(dir)) != nullptr) {
            if (strstr(de->d_name, "re.frida.server") ||
                strstr(de->d_name, "frida-server")) {
                found = true;
                break;
            }
        }
        closedir(dir);
        if (found) return true;
    }

    void* h1 = dlopen("libfrida-gadget.so", RTLD_NOW);
    if (h1) { dlclose(h1); return true; }
    void* h2 = dlopen("libfrida-agent.so", RTLD_NOW);
    if (h2) { dlclose(h2); return true; }

    return false;
}

static bool detectXposed() {
    const char* paths[] = {
        "/system/framework/XposedBridge.jar",
        "/system/lib/libxposed_art.so",
        "/system/lib64/libxposed_art.so",
        "/data/data/de.robv.android.xposed.installer",
        "/data/data/org.lsposed.manager",
        "/data/adb/lspd",
        "/data/adb/modules/riru_lsposed",
        "/data/adb/modules/zygisk_lsposed",
        nullptr
    };
    for (int i = 0; paths[i]; i++) {
        if (access(paths[i], F_OK) == 0) return true;
    }
    return false;
}

static bool detectTracer() {
    int fd = open("/proc/self/status", O_RDONLY);
    if (fd < 0) return false;
    char buf[4096];
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return false;
    buf[n] = '\0';
    const char* p = strstr(buf, "TracerPid:");
    if (!p) return false;
    p += 10;
    while (*p == ' ' || *p == '\t') p++;
    return atoi(p) > 0;
}

static bool isEnvironmentSafe() {
    if (detectFrida()) {
        SEC_DIAG_LOG(101, "Environment unsafe: Frida detected");
        return false;
    }
    if (detectXposed()) {
        SEC_DIAG_LOG(102, "Environment unsafe: Xposed/LSPosed detected");
        return false;
    }
    if (detectTracer()) {
        SEC_DIAG_LOG(103, "Environment unsafe: TracerPid > 0 detected");
        return false;
    }
    return true;
}

// ================================================================
// Core Native APK & Hash Verification
// ================================================================
static bool matchesExpectedCertHash(const std::string& candidateLower) {
    if (!isValidSha256Hex(candidateLower)) return false;
    std::string primary = toLower(std::string(SHA256_PRIMARY()));
    if (isValidSha256Hex(primary) && constTimeEquals(candidateLower, primary)) return true;
    std::string a1 = toLower(std::string(SHA256_ALT1()));
    if (isValidSha256Hex(a1) && constTimeEquals(candidateLower, a1)) return true;
    std::string a2 = toLower(std::string(SHA256_ALT2()));
    if (isValidSha256Hex(a2) && constTimeEquals(candidateLower, a2)) return true;
    return false;
}

static bool matchesExpectedDex(const std::string& expectedLower,
                               const std::vector<std::string>& entryHexes) {
    if (!isValidSha256Hex(expectedLower)) return false;
    bool matched = false;
    for (const auto& eh : entryHexes) {
        if (constTimeEquals(toLower(eh), expectedLower)) {
            matched = true;
        }
    }
    return matched;
}

static bool verifyApkHashesOnDisk(const std::string& apkPath, const std::string& pmCertHex) {
    if (apkPath.empty()) {
        SEC_DIAG_LOG(104, "APK path empty");
        return false;
    }

    int fd = open(apkPath.c_str(), O_RDONLY);
    if (fd < 0) {
        SEC_DIAG_LOG(105, "Failed to open APK: %s", apkPath.c_str());
        return false;
    }

    struct stat st{};
    if (fstat(fd, &st) != 0) {
        close(fd);
        SEC_DIAG_LOG(105, "fstat failed for APK: %s", apkPath.c_str());
        return false;
    }

    ApkFileLayout layout;
    bool eocdOk = parseZipEocd(fd, layout);
    std::string v2CertHex;
    std::vector<std::string> entryDexNames;
    std::vector<std::string> entryDexHexes;
    bool v2Ok = eocdOk && extractApkV2V3CertSha256(fd, layout, v2CertHex);
    bool dexOk = eocdOk && computeApkDexHashes(fd, layout, entryDexNames, entryDexHexes);
    close(fd);

    if (!eocdOk) {
        SEC_DIAG_LOG(105, "ZIP EOCD parse failed: %s", apkPath.c_str());
        return false;
    }
    if (!dexOk) {
        SEC_DIAG_LOG(109, "DEX hash computation failed: %s", apkPath.c_str());
        return false;
    }

#ifdef SEC_DIAG
    SEC_DIAG_LOG(110, "Found %zu dex entries in %s", entryDexNames.size(), apkPath.c_str());
    for (size_t i = 0; i < entryDexNames.size() && i < entryDexHexes.size(); ++i) {
        SEC_DIAG_LOG(110, "DEX[%zu] %s sha256=%s", i, entryDexNames[i].c_str(), entryDexHexes[i].c_str());
    }
#endif

#ifdef DEBUG_BUILD
    // Debug build: still exercise native parser and verify PackageManager agrees with v2/v3 cert when both exist.
    if (v2Ok && !pmCertHex.empty() && !constTimeEquals(toLower(v2CertHex), toLower(pmCertHex))) {
        SEC_DIAG_LOG(107, "PM cert mismatch v2=%s pm=%s", v2CertHex.c_str(), pmCertHex.c_str());
        return false;
    }
    std::string expectedPrimary = toLower(std::string(SHA256_PRIMARY()));
    if (!isAllZeroOrPlaceholder(expectedPrimary) && v2Ok && !matchesExpectedCertHash(v2CertHex)) {
        SEC_DIAG_LOG(108, "Cert mismatch actual=%s expected=%s", v2CertHex.c_str(), expectedPrimary.c_str());
        return false;
    }
    std::string expectedDex = toLower(std::string(EXPECTED_DEX_HASH()));
    if (!isAllZeroOrPlaceholder(expectedDex)) {
        if (!matchesExpectedDex(expectedDex, entryDexHexes)) {
            SEC_DIAG_LOG(111, "DEX mismatch expected=%s", expectedDex.c_str());
            return false;
        }
    }
#else
    // Release build: STRICT FAIL-CLOSED enforcement!
    std::string expectedPrimary = toLower(std::string(SHA256_PRIMARY()));
    if (isAllZeroOrPlaceholder(expectedPrimary) || !isValidSha256Hex(expectedPrimary)) {
        SEC_DIAG_LOG(108, "Expected cert SHA-256 invalid or placeholder");
        return false;
    }
    if (!v2Ok) {
        SEC_DIAG_LOG(106, "APK v2/v3 signature block missing (v1-only or unsigned)");
        return false;
    }
    if (!matchesExpectedCertHash(v2CertHex)) {
        SEC_DIAG_LOG(108, "APK cert mismatch actual=%s expected=%s", v2CertHex.c_str(), expectedPrimary.c_str());
        return false;
    }
    if (!pmCertHex.empty()) {
        if (!matchesExpectedCertHash(toLower(pmCertHex)) ||
            !constTimeEquals(toLower(v2CertHex), toLower(pmCertHex))) {
            SEC_DIAG_LOG(107, "PackageManager cert mismatch v2=%s pm=%s", v2CertHex.c_str(), pmCertHex.c_str());
            return false;
        }
    }

    // Fail closed if EXPECTED_DEX_HASH is all-zero or unreplaced placeholder!
    std::string expectedDex = toLower(std::string(EXPECTED_DEX_HASH()));
    if (isAllZeroOrPlaceholder(expectedDex) || !isValidSha256Hex(expectedDex)) {
        SEC_DIAG_LOG(111, "Expected DEX hash invalid or placeholder");
        return false;
    }
    if (!matchesExpectedDex(expectedDex, entryDexHexes)) {
        SEC_DIAG_LOG(111, "No .dex entry matched expected DEX hash=%s", expectedDex.c_str());
        return false;
    }
#endif

    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        g_CachedApkPath = apkPath;
        g_HasExpectedApkStat = true;
        g_ExpectedApkSize = (uint64_t)st.st_size;
        g_ExpectedApkInode = (uint64_t)st.st_ino;
    }
    SEC_DIAG_LOG(100, "APK cert and DEX verified OK (%s)", apkPath.c_str());
    return true;
}

// ================================================================
// Internal Ed25519 Session Token Verification
// ================================================================
static bool getEd25519PublicKeyBytes(std::vector<uint8_t>& outPk) {
    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        if (g_CachedEd25519Pk.size() == 32) {
            outPk = g_CachedEd25519Pk;
            return true;
        }
    }

    std::vector<uint8_t> pkBytes;
    if (!hexDecode(std::string(ED25519_PUBLIC_KEY()), pkBytes) || pkBytes.size() != 32) {
        SEC_DIAG_LOG(204, "Ed25519 public key missing or invalid");
        return false;
    }
    bool allZero = true;
    for (uint8_t b : pkBytes) {
        if (b != 0) { allZero = false; break; }
    }
    if (allZero) {
        SEC_DIAG_LOG(204, "Ed25519 public key is all-zero");
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        g_CachedEd25519Pk = pkBytes;
    }
    outPk = std::move(pkBytes);
    return true;
}

static bool verifyTokenInternal(const std::string& token,
                                const std::string& expectedUser,
                                const std::string& expectedDevice,
                                const std::string& expectedNonce,
                                int64_t* outExpiryMs,
                                int64_t* outSessionTtlMs,
                                bool checkActiveMonoDeadline) {
    if (token.empty() || expectedUser.empty() || expectedDevice.empty() || expectedNonce.empty()) {
        return false;
    }

    size_t dot = token.find('.');
    if (dot == std::string::npos || dot == 0 || dot + 1 >= token.size()) {
        return false;
    }
    if (token.find('.', dot + 1) != std::string::npos) {
        return false;
    }

    std::string payloadB64 = token.substr(0, dot);
    std::string sigB64     = token.substr(dot + 1);

    std::vector<uint8_t> sigBytes;
    if (!base64UrlDecode(sigB64, sigBytes) || sigBytes.size() != 64) {
        SEC_DIAG_LOG(204, "Token signature base64url/length invalid");
        return false;
    }

    std::vector<uint8_t> pkBytes;
    if (!getEd25519PublicKeyBytes(pkBytes)) {
        return false;
    }

    if (!Ed25519::verify(
            sigBytes.data(),
            reinterpret_cast<const uint8_t*>(payloadB64.data()),
            payloadB64.size(),
            pkBytes.data())) {
        SEC_DIAG_LOG(204, "Ed25519 signature verification failed");
        return false;
    }

    std::vector<uint8_t> payloadRaw;
    if (!base64UrlDecode(payloadB64, payloadRaw) || payloadRaw.empty()) {
        SEC_DIAG_LOG(204, "Token payload decode failed");
        return false;
    }
    std::string payloadJson(reinterpret_cast<const char*>(payloadRaw.data()), payloadRaw.size());

    std::string tokUser   = getJsonField(payloadJson, "u");
    std::string tokDevice = getJsonField(payloadJson, "d");
    std::string tokExpiry = getJsonField(payloadJson, "e");
    std::string tokSe     = getJsonField(payloadJson, "se");
    std::string tokRe     = getJsonField(payloadJson, "re");
    std::string tokTv     = getJsonField(payloadJson, "tv");
    std::string tokIat    = getJsonField(payloadJson, "i");
    std::string tokNonce  = getJsonField(payloadJson, "n");

    if (tokUser.empty() || tokDevice.empty() || tokNonce.empty() || tokSe.empty() || tokIat.empty()) {
        SEC_DIAG_LOG(204, "Token claims incomplete");
        return false;
    }
    if (!constTimeEquals(toLower(tokUser), toLower(expectedUser))) {
        SEC_DIAG_LOG(204, "Token user mismatch");
        return false;
    }
    if (!constTimeEquals(tokDevice, expectedDevice)) {
        SEC_DIAG_LOG(204, "Token device mismatch");
        return false;
    }
    if (!constTimeEquals(tokNonce, expectedNonce)) {
        SEC_DIAG_LOG(204, "Token nonce mismatch");
        return false;
    }

    int64_t parsedExpiry = 0;
    int64_t parsedSe = 0;
    int64_t parsedRe = 0;
    int64_t parsedIat = 0;
    int64_t parsedTv = 0;
    try {
        parsedIat = std::stoll(tokIat);
        parsedSe = std::stoll(tokSe);
        if (!tokExpiry.empty()) parsedExpiry = std::stoll(tokExpiry);
        if (!tokRe.empty()) parsedRe = std::stoll(tokRe);
        if (!tokTv.empty()) parsedTv = std::stoll(tokTv);
    } catch (...) {
        SEC_DIAG_LOG(204, "Token claim parse error");
        return false;
    }

    if (parsedIat <= 0 || parsedSe <= parsedIat || parsedTv < 1) {
        SEC_DIAG_LOG(204, "Token temporal/version claims invalid");
        return false;
    }

    int64_t nowWallMs = (int64_t)time(nullptr) * 1000LL;
    if (!updateAndCheckWallClock(nowWallMs)) {
        SEC_DIAG_LOG(204, "Device wall-clock rollback detected");
        return false;
    }

    if (parsedExpiry > 0 && nowWallMs > parsedExpiry) {
        SEC_DIAG_LOG(204, "Token license expired");
        return false;
    }
    if (parsedRe > 0 && nowWallMs > parsedRe) {
        SEC_DIAG_LOG(204, "Token remember window expired");
        return false;
    }

    // Clamp short session TTL to 1..30 min + 10 min offline grace (25 min max for 15 min se)
    int64_t rawTtlMs = parsedSe - parsedIat;
    if (rawTtlMs < 60000LL) rawTtlMs = 60000LL;
    if (rawTtlMs > 1800000LL) rawTtlMs = 1800000LL;
    int64_t sessionTtlWithGrace = rawTtlMs + 600000LL;

    // If wall-clock is reasonably synced with server time, also reject wall-stale short tokens
    int64_t clockSkewMs = nowWallMs - parsedIat;
    if (clockSkewMs > -600000LL && clockSkewMs < 86400000LL && nowWallMs > (parsedSe + 600000LL)) {
        SEC_DIAG_LOG(204, "Short session token expired (wall clock)");
        return false;
    }

    if (checkActiveMonoDeadline) {
        int64_t monoDeadline = g_SessionMonoDeadlineMs.load(std::memory_order_acquire);
        if (monoDeadline <= 0 || monotonicMs() > monoDeadline) {
            SEC_DIAG_LOG(204, "Short session token expired (monotonic TTL)");
            return false;
        }
    }

    if (outExpiryMs) *outExpiryMs = parsedExpiry;
    if (outSessionTtlMs) *outSessionTtlMs = sessionTtlWithGrace;
    return true;
}

// ================================================================
// Background Watchdog Thread (periodic re-verification)
// ================================================================
static void* watchdog_thread(void*) {
    while (true) {
        sleep(25);
        if (!SecurityGuard::periodicReverify()) {
            triggerLockdown(true);
            return nullptr;
        }
    }
    return nullptr;
}

static void ensureWatchdogStarted() {
    bool expected = false;
    if (g_WatchdogStarted.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) {
        pthread_t tid;
        if (pthread_create(&tid, nullptr, watchdog_thread, nullptr) == 0) {
            pthread_detach(tid);
        } else {
            g_WatchdogStarted.store(false, std::memory_order_release);
        }
    }
}

// ================================================================
// SecurityGuard Multi-Path Implementations
// ================================================================
namespace SecurityGuard {

#ifdef SEC_DIAG
void diagLog(int stage, const char* fmt, ...) {
    char msg[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(msg, sizeof(msg), fmt, ap);
    va_end(ap);
    __android_log_print(ANDROID_LOG_INFO, "MXL", "[%d] %s", stage, msg);
}
#endif

void registerLockdownCallback(LockdownCallback cb) {
    g_LockdownCb.store(cb, std::memory_order_release);
}

bool verifyPathOnLoad(JavaVM* vm, JNIEnv* env) {
    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        g_JavaVm = vm;
    }

    if (!isEnvironmentSafe()) {
        triggerLockdown(false);
        return false;
    }

    // Try discovering APK early via /proc/self/maps and/or ActivityThread.currentApplication()
    std::string apkPath = findApkPathFromProcMaps();
    std::string pmCert;
    if (env) {
        jobject app = getCurrentApplicationViaActivityThread(env);
        if (app) {
            if (apkPath.empty()) apkPath = getApkPathFromSourceDir(env, app);
            pmCert = readPmCertSha256(env, app);
            env->DeleteLocalRef(app);
        }
    }

    if (!apkPath.empty()) {
        if (!verifyApkHashesOnDisk(apkPath, pmCert)) {
            triggerLockdown(false);
            return false;
        }
        markWordA(true);
    } else {
        // Environment passed at JNI_OnLoad; full APK verification runs in preload / MenuInit.
        markWordA(true);
    }
    return true;
}

bool verifyPathPreload(JNIEnv* env, jobject ctx) {
    if (!env || !ctx) {
        triggerLockdown(false);
        return false;
    }
    if (!isEnvironmentSafe()) {
        triggerLockdown(false);
        return false;
    }

    std::string apkPath = getApkPathFromSourceDir(env, ctx);
    if (apkPath.empty()) apkPath = findApkPathFromProcMaps();
    std::string pmCert = readPmCertSha256(env, ctx);

    if (!verifyApkHashesOnDisk(apkPath, pmCert)) {
        triggerLockdown(false);
        return false;
    }

    markWordA(true);
    markWordB(true);
    g_Verified.store(true, std::memory_order_release);
    ensureWatchdogStarted();
    return true;
}

bool verifyPathMenuInit(JNIEnv* env, jobject ctx) {
    if (!env || !ctx) {
        triggerLockdown(false);
        return false;
    }
    if (!isEnvironmentSafe()) {
        triggerLockdown(true);
        return false;
    }

    std::string codePath = getApkPathFromCodePath(env, ctx);
    if (codePath.empty()) codePath = getApkPathFromSourceDir(env, ctx);
    if (codePath.empty()) codePath = findApkPathFromProcMaps();
    std::string pmCert = readPmCertSha256(env, ctx);

    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        if (!g_CachedApkPath.empty() && !codePath.empty() && codePath != g_CachedApkPath) {
            // Package code path unexpectedly mutated
            SEC_DIAG_LOG(104, "Package codePath mutated (%s vs %s)", codePath.c_str(), g_CachedApkPath.c_str());
            codePath.clear();
        }
    }
    if (!verifyApkHashesOnDisk(codePath, pmCert)) {
        SEC_DIAG_LOG(113, "verifyPathMenuInit failed on disk verification");
        triggerLockdown(true);
        return false;
    }

    markWordB(true);
    markWordC(true);
    g_Verified.store(true, std::memory_order_release);
    ensureWatchdogStarted();
    return true;
}

bool verifyPathFeatureGate(JNIEnv* env) {
    (void)env;
    if (!g_Verified.load(std::memory_order_acquire) || !isWordAOk() || !isWordBOk() || !isWordCOk()) {
        return false;
    }
    if (!isEnvironmentSafe()) {
        triggerLockdown(true);
        return false;
    }
    if (!g_TokenVerified.load(std::memory_order_acquire) ||
        g_TokenWord.load(std::memory_order_acquire) != (kSeedT1 ^ kSeedA2)) {
        return false;
    }

    std::string tok, usr, dev, nonce;
    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        tok = g_ActiveToken;
        usr = g_ActiveUser;
        dev = g_ActiveDeviceId;
        nonce = g_ActiveNonce;
    }
    int64_t expMs = 0;
    if (!verifyTokenInternal(tok, usr, dev, nonce, &expMs, nullptr, true)) {
        triggerLockdown(false);
        return false;
    }
    return true;
}

bool verifyPathToggle(JNIEnv* env, jobject ctx, int featNum) {
    (void)env; (void)ctx;
    if (!g_Verified.load(std::memory_order_acquire) || !isWordAOk() || !isWordBOk() || !isWordCOk()) {
        triggerLockdown(false);
        return false;
    }
    if (!g_TokenVerified.load(std::memory_order_acquire) ||
        g_TokenWord.load(std::memory_order_acquire) != (kSeedT1 ^ kSeedA2)) {
        triggerLockdown(false);
        return false;
    }

    int64_t nowWallMs = (int64_t)time(nullptr) * 1000LL;
    if (!updateAndCheckWallClock(nowWallMs)) {
        triggerLockdown(false);
        return false;
    }
    int64_t expMs = g_TokenExpiryMs.load(std::memory_order_acquire);
    if (expMs > 0 && nowWallMs > expMs) {
        triggerLockdown(false);
        return false;
    }
    int64_t monoDeadline = g_SessionMonoDeadlineMs.load(std::memory_order_acquire);
    if (monoDeadline <= 0 || monotonicMs() > monoDeadline) {
        triggerLockdown(false);
        return false;
    }

    if (detectTracer()) {
        triggerLockdown(true);
        return false;
    }

    // Periodically and on sensitive/mode toggles run deeper checks
    uint32_t count = g_ToggleCounter.fetch_add(1, std::memory_order_relaxed);
    if ((count & 0x0Fu) == 0u || featNum == 150 || featNum >= 800) {
        if (!isEnvironmentSafe()) {
            triggerLockdown(true);
            return false;
        }
        std::string cachedPath, tok, usr, dev, nonce;
        bool hasStat = false;
        uint64_t expSize = 0, expInode = 0;
        {
            std::lock_guard<std::mutex> lock(g_SecMutex);
            cachedPath = g_CachedApkPath;
            hasStat = g_HasExpectedApkStat;
            expSize = g_ExpectedApkSize;
            expInode = g_ExpectedApkInode;
            tok = g_ActiveToken;
            usr = g_ActiveUser;
            dev = g_ActiveDeviceId;
            nonce = g_ActiveNonce;
        }
        if (!verifyTokenInternal(tok, usr, dev, nonce, nullptr, nullptr, true)) {
            triggerLockdown(false);
            return false;
        }
        if (hasStat && !cachedPath.empty()) {
            struct stat st{};
            if (stat(cachedPath.c_str(), &st) != 0 ||
                (uint64_t)st.st_size != expSize ||
                (uint64_t)st.st_ino != expInode) {
                if (!verifyApkHashesOnDisk(cachedPath, "")) {
                    triggerLockdown(true);
                    return false;
                }
            }
        }
    }

    return true;
}

bool isReadyForHooks() {
    if (!g_Verified.load(std::memory_order_acquire) || !isWordAOk() || !isWordBOk() || !isWordCOk()) {
        return false;
    }
    if (!g_TokenVerified.load(std::memory_order_acquire) ||
        g_TokenWord.load(std::memory_order_acquire) != (kSeedT1 ^ kSeedA2)) {
        return false;
    }

    std::string cachedPath, tok, usr, dev, nonce;
    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        cachedPath = g_CachedApkPath;
        tok = g_ActiveToken;
        usr = g_ActiveUser;
        dev = g_ActiveDeviceId;
        nonce = g_ActiveNonce;
    }

    if (!isEnvironmentSafe()) {
        triggerLockdown(true);
        return false;
    }
    if (!verifyApkHashesOnDisk(cachedPath, "")) {
        triggerLockdown(true);
        return false;
    }
    if (!verifyTokenInternal(tok, usr, dev, nonce, nullptr, nullptr, true)) {
        return false;
    }
    return true;
}

bool periodicReverify() {
    if (!isEnvironmentSafe()) return false;

    std::string cachedPath, tok, usr, dev, nonce;
    bool tokActive = false;
    bool hasStat = false;
    uint64_t expSize = 0, expInode = 0;
    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        cachedPath = g_CachedApkPath;
        hasStat = g_HasExpectedApkStat;
        expSize = g_ExpectedApkSize;
        expInode = g_ExpectedApkInode;
        tokActive = g_TokenVerified.load(std::memory_order_acquire);
        tok = g_ActiveToken;
        usr = g_ActiveUser;
        dev = g_ActiveDeviceId;
        nonce = g_ActiveNonce;
    }

    if (!cachedPath.empty()) {
        uint32_t tick = g_ReverifyTick.fetch_add(1, std::memory_order_relaxed);
        bool needFullRehash = !hasStat || (tick % 3u == 0u);
        if (!needFullRehash) {
            struct stat st{};
            if (stat(cachedPath.c_str(), &st) != 0 ||
                (uint64_t)st.st_size != expSize ||
                (uint64_t)st.st_ino != expInode) {
                needFullRehash = true;
            }
        }
        if (needFullRehash && !verifyApkHashesOnDisk(cachedPath, "")) {
            return false;
        }
    }

    if (tokActive && !verifyTokenInternal(tok, usr, dev, nonce, nullptr, nullptr, true)) {
        return false;
    }
    return true;
}

} // namespace SecurityGuard

// ================================================================
// JNI: verifyNative — SILENT NATIVE INTEGRITY GATE (no isDebug / no Java hashes)
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifyNative(
        JNIEnv* env, jclass, jobject ctx) {
    return SecurityGuard::verifyPathPreload(env, ctx) ? JNI_TRUE : JNI_FALSE;
}

// ================================================================
// JNI: verifySessionToken (Ed25519 public-key verification + nonce binding)
// Token format: <base64url(json_payload)>.<base64url(ed25519_sig)>
// Payload fields: {"u":"<user>","e":<expiryMs>,"se":<shortExpMs>,"re":<remExpMs>,"tv":<ver>,"d":"<deviceId>","i":<iatMs>,"n":"<nonce>"}
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifySessionToken(
        JNIEnv* env, jclass,
        jstring jToken, jstring jUser, jstring jDeviceId, jstring jNonce) {

    if (!jToken || !jUser || !jDeviceId || !jNonce) {
        SEC_DIAG_LOG(204, "verifySessionToken missing arguments");
        return JNI_FALSE;
    }
    if (!g_Verified.load(std::memory_order_acquire) || !isWordAOk() || !isWordBOk()) {
        SEC_DIAG_LOG(113, "verifySessionToken rejected: native integrity not verified");
        return JNI_FALSE;
    }
    if (!isEnvironmentSafe()) {
        triggerLockdown(true);
        return JNI_FALSE;
    }

    const char* ct = env->GetStringUTFChars(jToken, nullptr);
    const char* cu = env->GetStringUTFChars(jUser, nullptr);
    const char* cd = env->GetStringUTFChars(jDeviceId, nullptr);
    const char* cn = env->GetStringUTFChars(jNonce, nullptr);

    if (!ct || !cu || !cd || !cn) {
        if (ct) env->ReleaseStringUTFChars(jToken, ct);
        if (cu) env->ReleaseStringUTFChars(jUser, cu);
        if (cd) env->ReleaseStringUTFChars(jDeviceId, cd);
        if (cn) env->ReleaseStringUTFChars(jNonce, cn);
        return JNI_FALSE;
    }

    std::string token(ct), expectedUser(cu), expectedDevice(cd), expectedNonce(cn);
    env->ReleaseStringUTFChars(jToken, ct);
    env->ReleaseStringUTFChars(jUser, cu);
    env->ReleaseStringUTFChars(jDeviceId, cd);
    env->ReleaseStringUTFChars(jNonce, cn);

    int64_t expiryMs = 0;
    int64_t sessionTtlMs = 0;
    if (!verifyTokenInternal(token, expectedUser, expectedDevice, expectedNonce,
                             &expiryMs, &sessionTtlMs, false)) {
        return JNI_FALSE;
    }

    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        g_ActiveToken = token;
        g_ActiveUser = expectedUser;
        g_ActiveDeviceId = expectedDevice;
        g_ActiveNonce = expectedNonce;
    }
    g_TokenExpiryMs.store(expiryMs, std::memory_order_release);
    g_SessionMonoDeadlineMs.store(monotonicMs() + sessionTtlMs, std::memory_order_release);
    g_TokenWord.store(kSeedT1 ^ kSeedA2, std::memory_order_release);
    g_TokenVerified.store(true, std::memory_order_release);
    ensureWatchdogStarted();

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_android_support_SecurityNative_clearVerifiedSession(JNIEnv*, jclass) {
    g_TokenVerified.store(false, std::memory_order_release);
    g_TokenWord.store(0u, std::memory_order_release);
    g_TokenExpiryMs.store(0, std::memory_order_release);
    g_SessionMonoDeadlineMs.store(0, std::memory_order_release);
    {
        std::lock_guard<std::mutex> lock(g_SecMutex);
        g_ActiveToken.clear();
        g_ActiveUser.clear();
        g_ActiveDeviceId.clear();
        g_ActiveNonce.clear();
    }
    SecurityGuard::LockdownCallback cb = g_LockdownCb.load(std::memory_order_acquire);
    if (cb) cb();
}

// ================================================================
// JNI: Obfuscated server endpoints & Appwrite identifiers
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getUpdateUrl(JNIEnv* env, jclass) {
    std::string url;
    url += OBF_STR("https://modx-lab-a5e41");
    url += OBF_STR("-default-rtdb.firebaseio.com");
    url += OBF_STR("/update.json");
    return env->NewStringUTF(url.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getAppwriteEndpoint(JNIEnv* env, jclass) {
    std::string ep;
    ep += OBF_STR("https://sgp.cloud.appwrite.io");
    ep += OBF_STR("/v1");
    return env->NewStringUTF(ep.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getAppwriteProjectId(JNIEnv* env, jclass) {
    return env->NewStringUTF(OBF_STR("6b00580c000a55b46807"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getVerifyLoginFunctionId(JNIEnv* env, jclass) {
    return env->NewStringUTF(OBF_STR("6b005c0f000b971d589f"));
}