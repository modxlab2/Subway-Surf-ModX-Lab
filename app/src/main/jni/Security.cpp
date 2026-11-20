// ================================================================
// Security.cpp — Silent Security & Ed25519 Token Verifier
//
// Design:
//   - NO user-visible strings
//   - NO password comparison or signing secrets in client binary
//   - Verifies server-issued Ed25519 tokens using ONLY public key
//   - Appwrite endpoint / project / function IDs obfuscated at compile time
// ================================================================

#include <jni.h>
#include <string>
#include <vector>
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

#include "Includes/obfuscate.h"
#include "Ed25519.hpp"

#define OBF_STR(s) (static_cast<const char*>(OBFUSCATE(s)))

// ================================================================
// CONFIGURATION (injected at build time)
// ================================================================

// APK signing cert SHA-256 (64 lowercase hex, no colons)
static const char* SHA256_PRIMARY() {
    return OBF_STR("__CERT_SHA256__");
}
static const char* SHA256_ALT1() { return OBF_STR(""); }
static const char* SHA256_ALT2() { return OBF_STR(""); }

// Ed25519 PUBLIC key (64 lowercase hex = 32 bytes; NOT a secret)
static const char* ED25519_PUBLIC_KEY() {
    return OBF_STR("__ED25519_PUBLIC_KEY__");
}

// DEX integrity hash (64 lowercase hex)
static const char* EXPECTED_DEX_HASH() {
    return OBF_STR("__DEX_HASH__");
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
                found = true; break;
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
                found = true; break;
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
    if (detectFrida())  return false;
    if (detectXposed()) return false;
    if (detectTracer()) return false;
    return true;
}

// ================================================================
// JNI: verifyHashes — SILENT SECURITY GATE
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifyHashes(
        JNIEnv* env, jclass, jstring jsig, jstring jdex, jboolean jdebug) {

    if (!jsig || !jdex) return JNI_FALSE;

    const char* csig = env->GetStringUTFChars(jsig, nullptr);
    const char* cdex = env->GetStringUTFChars(jdex, nullptr);
    if (!csig || !cdex) {
        if (csig) env->ReleaseStringUTFChars(jsig, csig);
        if (cdex) env->ReleaseStringUTFChars(jdex, cdex);
        return JNI_FALSE;
    }

    std::string sigHash = toLower(std::string(csig));
    std::string dexHash = toLower(std::string(cdex));
    env->ReleaseStringUTFChars(jsig, csig);
    env->ReleaseStringUTFChars(jdex, cdex);

    bool isDebug = (jdebug == JNI_TRUE);

    // Signature verify (skip in debug)
    if (!isDebug) {
        std::string expected = toLower(std::string(SHA256_PRIMARY()));
        bool ok = false;
        if (!expected.empty() && sigHash.size() == expected.size()) {
            if (constTimeEquals(sigHash, expected)) ok = true;
        }
        if (!ok) {
            std::string a1 = toLower(std::string(SHA256_ALT1()));
            if (!a1.empty() && sigHash.size() == a1.size()) {
                if (constTimeEquals(sigHash, a1)) ok = true;
            }
            std::string a2 = toLower(std::string(SHA256_ALT2()));
            if (!ok && !a2.empty() && sigHash.size() == a2.size()) {
                if (constTimeEquals(sigHash, a2)) ok = true;
            }
        }
        if (!ok) return JNI_FALSE;
    }

    // DEX integrity verify (skip in debug)
    if (!isDebug) {
        std::string expected = toLower(std::string(EXPECTED_DEX_HASH()));
        bool placeholder = true;
        for (char c : expected) {
            if (c != '0') { placeholder = false; break; }
        }
        if (!placeholder) {
            if (dexHash.size() != expected.size()) return JNI_FALSE;
            if (!constTimeEquals(dexHash, expected)) return JNI_FALSE;
        }
    }

    // Environment check (always)
    if (!isEnvironmentSafe()) return JNI_FALSE;

    return JNI_TRUE;
}

// ================================================================
// JNI: verifySessionToken (Ed25519 public-key verification)
// Token format: <base64url(json_payload)>.<base64url(ed25519_sig)>
// Payload fields: {"u":"<user>","e":<expiryMs>,"d":"<deviceId>","i":<iatMs>}
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifySessionToken(
        JNIEnv* env, jclass,
        jstring jToken, jstring jUser, jstring jDeviceId) {

    if (!jToken || !jUser || !jDeviceId) return JNI_FALSE;

    const char* ct = env->GetStringUTFChars(jToken, nullptr);
    const char* cu = env->GetStringUTFChars(jUser, nullptr);
    const char* cd = env->GetStringUTFChars(jDeviceId, nullptr);

    if (!ct || !cu || !cd) {
        if (ct) env->ReleaseStringUTFChars(jToken, ct);
        if (cu) env->ReleaseStringUTFChars(jUser, cu);
        if (cd) env->ReleaseStringUTFChars(jDeviceId, cd);
        return JNI_FALSE;
    }

    std::string token(ct), expectedUser(cu), expectedDevice(cd);
    env->ReleaseStringUTFChars(jToken, ct);
    env->ReleaseStringUTFChars(jUser, cu);
    env->ReleaseStringUTFChars(jDeviceId, cd);

    if (token.empty() || expectedUser.empty() || expectedDevice.empty()) {
        return JNI_FALSE;
    }

    size_t dot = token.find('.');
    if (dot == std::string::npos || dot == 0 || dot + 1 >= token.size()) {
        return JNI_FALSE;
    }
    if (token.find('.', dot + 1) != std::string::npos) {
        return JNI_FALSE;
    }

    std::string payloadB64 = token.substr(0, dot);
    std::string sigB64     = token.substr(dot + 1);

    std::vector<uint8_t> sigBytes;
    if (!base64UrlDecode(sigB64, sigBytes) || sigBytes.size() != 64) {
        return JNI_FALSE;
    }

    std::vector<uint8_t> pkBytes;
    if (!hexDecode(std::string(ED25519_PUBLIC_KEY()), pkBytes) || pkBytes.size() != 32) {
        return JNI_FALSE;
    }
    bool allZero = true;
    for (uint8_t b : pkBytes) {
        if (b != 0) { allZero = false; break; }
    }
    if (allZero) return JNI_FALSE;

    if (!Ed25519::verify(
            sigBytes.data(),
            reinterpret_cast<const uint8_t*>(payloadB64.data()),
            payloadB64.size(),
            pkBytes.data())) {
        return JNI_FALSE;
    }

    std::vector<uint8_t> payloadRaw;
    if (!base64UrlDecode(payloadB64, payloadRaw) || payloadRaw.empty()) {
        return JNI_FALSE;
    }
    std::string payloadJson(reinterpret_cast<const char*>(payloadRaw.data()), payloadRaw.size());

    std::string tokUser   = getJsonField(payloadJson, "u");
    std::string tokDevice = getJsonField(payloadJson, "d");
    std::string tokExpiry = getJsonField(payloadJson, "e");

    if (tokUser.empty() || tokDevice.empty()) return JNI_FALSE;
    if (!constTimeEquals(toLower(tokUser), toLower(expectedUser))) return JNI_FALSE;
    if (!constTimeEquals(tokDevice, expectedDevice)) return JNI_FALSE;

    if (!tokExpiry.empty()) {
        try {
            long long expiryMs = std::stoll(tokExpiry);
            if (expiryMs > 0) {
                long long nowMs = (long long)time(nullptr) * 1000LL;
                if (nowMs > expiryMs) return JNI_FALSE;
            }
        } catch (...) {
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
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