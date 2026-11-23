#!/data/data/com.termux/files/usr/bin/env bash
# Fallback-compatible shebang for Termux / Linux:
# Run with: bash scripts/inject-and-sign.sh [options]

set -euo pipefail

# ==============================================================================
# ModX Lab — Termux Host-APK Injector, Page-Aligner & V1+V2+V3 Signer
# ==============================================================================

MOD_APK="${MOD_APK:-}"
HOST_APK="${HOST_APK:-}"
KEYSTORE="${KEYSTORE:-}"
KEY_ALIAS="${KEY_ALIAS:-modxlab}"
KEYSTORE_TYPE="${KEYSTORE_TYPE:-PKCS12}"
OUT_APK="${OUT_APK:-}"
CERT_SHA256="${CERT_SHA256:-}"
CERT_SHA256_FILE="${CERT_SHA256_FILE:-}"

WORK_DIR=""
MXL_KS_PASS=""
MXL_KEY_PASS=""

cleanup() {
    unset MXL_KS_PASS MXL_KEY_PASS KEYSTORE_PASS KEY_PASS 2>/dev/null || true
    if [ -n "${WORK_DIR}" ] && [ -d "${WORK_DIR}" ]; then
        rm -rf "${WORK_DIR}"
    fi
}
trap cleanup EXIT INT TERM

err_exit() {
    printf '\n[ERROR] %s\n' "$1" >&2
    exit 1
}

warn_msg() {
    printf '[WARN] %s\n' "$1" >&2
}

info_msg() {
    printf '[INFO] %s\n' "$1"
}

usage() {
    cat <<'EOF'
Usage:
  bash scripts/inject-and-sign.sh \
    --mod-apk <path/to/app-release.apk> \
    --host-apk <path/to/game.apk> \
    --keystore <path/to/release.keystore> \
    [--alias modxlab] \
    [--ks-type PKCS12] \
    [--out <path/to/game_signed.apk>] \
    [--cert-sha256 <64-hex> | --cert-file <path/to/cert.txt>]

Environment variables (optional alternatives to flags):
  MOD_APK, HOST_APK, KEYSTORE, KEY_ALIAS, KEYSTORE_TYPE, OUT_APK,
  CERT_SHA256, CERT_SHA256_FILE
  (Passwords are prompted safely via hidden input; never pass via CLI args.)
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        -m|--mod-apk)
            [ "$#" -ge 2 ] || err_exit "--mod-apk এর পরে ফাইলের পথ দিন।"
            MOD_APK="$2"; shift 2 ;;
        -g|--host-apk)
            [ "$#" -ge 2 ] || err_exit "--host-apk এর পরে ফাইলের পথ দিন।"
            HOST_APK="$2"; shift 2 ;;
        -k|--keystore)
            [ "$#" -ge 2 ] || err_exit "--keystore এর পরে ফাইলের পথ দিন।"
            KEYSTORE="$2"; shift 2 ;;
        -a|--alias)
            [ "$#" -ge 2 ] || err_exit "--alias এর পরে key alias দিন।"
            KEY_ALIAS="$2"; shift 2 ;;
        --ks-type)
            [ "$#" -ge 2 ] || err_exit "--ks-type এর পরে PKCS12 বা JKS দিন।"
            KEYSTORE_TYPE="$2"; shift 2 ;;
        -o|--out)
            [ "$#" -ge 2 ] || err_exit "--out এর পরে আউটপুট APK পথ দিন।"
            OUT_APK="$2"; shift 2 ;;
        -c|--cert-sha256)
            [ "$#" -ge 2 ] || err_exit "--cert-sha256 এর পরে 64-hex দিন।"
            CERT_SHA256="$2"; shift 2 ;;
        --cert-file)
            [ "$#" -ge 2 ] || err_exit "--cert-file এর পরে ফাইলের পথ দিন।"
            CERT_SHA256_FILE="$2"; shift 2 ;;
        -h|--help)
            usage; exit 0 ;;
        *)
            err_exit "অচেনা অপশন: $1 (--help দেখুন)" ;;
    esac
done

# ------------------------------------------------------------------------------
# 1. Input & Tool Pre-flight Checks
# ------------------------------------------------------------------------------
[ -n "${MOD_APK}" ]  || err_exit "MOD_APK দেওয়া হয়নি (--mod-apk বা MOD_APK ব্যবহার করুন)।"
[ -n "${HOST_APK}" ] || err_exit "HOST_APK দেওয়া হয়নি (--host-apk বা HOST_APK ব্যবহার করুন)।"
[ -n "${KEYSTORE}" ] || err_exit "KEYSTORE দেওয়া হয়নি (--keystore বা KEYSTORE ব্যবহার করুন)।"
[ -n "${KEY_ALIAS}" ] || err_exit "KEY_ALIAS খালি রাখা যাবে না।"

[ -f "${MOD_APK}" ]  || err_exit "MOD_APK পাওয়া যায়নি: ${MOD_APK}"
[ -f "${HOST_APK}" ] || err_exit "HOST_APK পাওয়া যায়নি: ${HOST_APK}"
[ -f "${KEYSTORE}" ] || err_exit "KEYSTORE পাওয়া যায়নি: ${KEYSTORE}"

for tool in unzip zip apksigner sha256sum; do
    command -v "${tool}" >/dev/null 2>&1 || err_exit "প্রয়োজনীয় টুল '${tool}' ইনস্টল নেই। Termux-এ ইনস্টল করে আবার চেষ্টা করুন।"
done

HAS_ZIPALIGN=0
if command -v zipalign >/dev/null 2>&1; then
    HAS_ZIPALIGN=1
elif ! command -v python3 >/dev/null 2>&1; then
    err_exit "'zipalign' এবং 'python3' কোনোটাই পাওয়া যায়নি! Termux-এ 'pkg search zipalign' দিয়ে দেখুন অথবা 'pkg install python -y' দিন।"
else
    warn_msg "'zipalign' পাওয়া যায়নি — Python 4KB page-alignment fallback ব্যবহার করা হবে।"
fi

if [ -z "${OUT_APK}" ]; then
    host_dir="$(dirname "${HOST_APK}")"
    host_base="$(basename "${HOST_APK}")"
    host_stem="${host_base%.apk}"
    OUT_APK="${host_dir}/${host_stem}_signed.apk"
fi

if [ -n "${CERT_SHA256_FILE}" ]; then
    [ -f "${CERT_SHA256_FILE}" ] || err_exit "CERT_SHA256_FILE পাওয়া যায়নি: ${CERT_SHA256_FILE}"
    CERT_SHA256="$(tr -d '[:space:]:' < "${CERT_SHA256_FILE}" | tr '[:upper:]' '[:lower:]')"
elif [ -n "${CERT_SHA256}" ]; then
    CERT_SHA256="$(printf '%s' "${CERT_SHA256}" | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')"
fi

if [ -n "${CERT_SHA256}" ]; then
    if [[ ! "${CERT_SHA256}" =~ ^[0-9a-f]{64}$ ]]; then
        err_exit "প্রদত্ত CERT_SHA256 সঠিক ৬৪-অক্ষরের hex স্ট্রিং নয়।"
    fi
fi

# Read keystore & key passwords safely (never printed or saved to disk/history)
if [ -n "${KEYSTORE_PASS:-}" ]; then
    MXL_KS_PASS="${KEYSTORE_PASS}"
else
    IFS= read -r -s -p "Keystore password দিন (${KEYSTORE}): " MXL_KS_PASS
    printf '\n'
fi
[ -n "${MXL_KS_PASS}" ] || err_exit "Keystore password খালি হতে পারে না।"

if [ -n "${KEY_PASS:-}" ]; then
    MXL_KEY_PASS="${KEY_PASS}"
else
    IFS= read -r -s -p "Key password দিন (alias '${KEY_ALIAS}'; একই হলে শুধু Enter চাপুন): " MXL_KEY_PASS
    printf '\n'
    if [ -z "${MXL_KEY_PASS}" ]; then
        MXL_KEY_PASS="${MXL_KS_PASS}"
    fi
fi
export MXL_KS_PASS MXL_KEY_PASS

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/modxlab_inject.XXXXXX")"
STAGE_DIR="${WORK_DIR}/stage"
mkdir -p "${STAGE_DIR}"

info_msg "MOD_APK  : ${MOD_APK}"
info_msg "HOST_APK : ${HOST_APK}"
info_msg "OUT_APK  : ${OUT_APK}"
info_msg "KEYSTORE : ${KEYSTORE} (alias=${KEY_ALIAS}, type=${KEYSTORE_TYPE})"

# ------------------------------------------------------------------------------
# 2. Pre-flight Inspection (MOD_APK DEX, HOST_APK ABIs, DEX sequence, extractNativeLibs)
# ------------------------------------------------------------------------------
mod_dex_list=()
while IFS= read -r entry; do
    [ -n "${entry}" ] && mod_dex_list+=("${entry}")
done < <(unzip -Z1 "${MOD_APK}" '*.dex' 2>/dev/null | sort || true)

if [ "${#mod_dex_list[@]}" -ne 1 ] || [ "${mod_dex_list[0]}" != "classes.dex" ]; then
    err_exit "MOD_APK-তে অবশ্যই ঠিক ১টি 'classes.dex' থাকতে হবে। পাওয়া গেছে (${#mod_dex_list[@]}টি): ${mod_dex_list[*]:-none}। সঠিক CI release/diag বিল্ড ব্যবহার করুন।"
fi

unzip -q -o "${MOD_APK}" "classes.dex" -d "${WORK_DIR}"
MOD_DEX_SHA256="$(sha256sum "${WORK_DIR}/classes.dex" | awk '{print tolower($1)}')"
info_msg "MOD classes.dex SHA-256: ${MOD_DEX_SHA256}"

# Detect ABIs inside HOST_APK under lib/
host_abis=()
while IFS= read -r abi; do
    [ -n "${abi}" ] && host_abis+=("${abi}")
done < <(unzip -Z1 "${HOST_APK}" 'lib/*/*' 2>/dev/null | awk -F'/' 'NF>=3 && $1=="lib" && $2!="" {print $2}' | sort -u || true)

if [ "${#host_abis[@]}" -eq 0 ]; then
    err_exit "HOST_APK-এর ভেতর 'lib/<abi>/' ফোল্ডার পাওয়া যায়নি। এটি কি split APK-এর base.apk? split APK হলে config.arm64_v8a / config.armeabi_v7a আলাদা করে চেক করুন বা আগে merge করুন।"
fi
info_msg "HOST_APK-তে পাওয়া ABIs: ${host_abis[*]}"

has_arm64=0
has_armv7=0
selected_abis=()
for abi in "${host_abis[@]}"; do
    case "${abi}" in
        arm64-v8a)
            has_arm64=1
            if unzip -Z1 "${MOD_APK}" "lib/arm64-v8a/libModXLab.so" >/dev/null 2>&1; then
                selected_abis+=("arm64-v8a")
            else
                err_exit "MOD_APK-তে lib/arm64-v8a/libModXLab.so নেই!"
            fi
            ;;
        armeabi-v7a)
            has_armv7=1
            if unzip -Z1 "${MOD_APK}" "lib/armeabi-v7a/libModXLab.so" >/dev/null 2>&1; then
                selected_abis+=("armeabi-v7a")
            else
                err_exit "MOD_APK-তে lib/armeabi-v7a/libModXLab.so নেই!"
            fi
            ;;
        *)
            warn_msg "HOST_APK-তে '${abi}' ABI আছে, কিন্তু ModXLab শুধু ARM (arm64-v8a / armeabi-v7a) সাপোর্ট করে।"
            ;;
    esac
done

if [ "${#selected_abis[@]}" -eq 0 ]; then
    err_exit "HOST_APK-এর কোনো ABI (${host_abis[*]}) এর জন্য MOD_APK-তে libModXLab.so মেলেনি।"
fi

if [ "${has_armv7}" -eq 1 ] && [ "${has_arm64}" -eq 0 ]; then
    warn_msg "HOST_APK-তে শুধু 32-bit 'armeabi-v7a' আছে (arm64-v8a নেই)। তাই শুধু lib/armeabi-v7a/libModXLab.so যোগ করা হচ্ছে — এমন হোস্টে arm64-v8a .so দিলে কখনোই লোড হবে না।"
fi

# Detect existing top-level classes*.dex in HOST_APK and choose next sequential name
host_dex_indices=()
host_dex_names=()
while IFS= read -r dname; do
    [ -n "${dname}" ] || continue
    host_dex_names+=("${dname}")
    if [ "${dname}" = "classes.dex" ]; then
        host_dex_indices+=(1)
    elif [[ "${dname}" =~ ^classes([0-9]+)\.dex$ ]]; then
        idx="${BASH_REMATCH[1]}"
        host_dex_indices+=("${idx}")
    fi
done < <(unzip -Z1 "${HOST_APK}" 'classes*.dex' 2>/dev/null | grep -E '^classes([0-9]+)?\.dex$' | sort || true)

[ "${#host_dex_indices[@]}" -gt 0 ] || err_exit "HOST_APK-তে কোনো classes*.dex পাওয়া যায়নি!"

sorted_indices=()
while IFS= read -r n; do
    sorted_indices+=("${n}")
done < <(printf '%s\n' "${host_dex_indices[@]}" | sort -n -u)

max_idx=0
prev=0
has_existing_gap=0
for idx in "${sorted_indices[@]}"; do
    if [ "${prev}" -eq 0 ] && [ "${idx}" -ne 1 ]; then
        has_existing_gap=1
    elif [ "${prev}" -ne 0 ] && [ "${idx}" -ne $((prev + 1)) ]; then
        has_existing_gap=1
    fi
    prev="${idx}"
    if [ "${idx}" -gt "${max_idx}" ]; then
        max_idx="${idx}"
    fi
done

if [ "${has_existing_gap}" -eq 1 ]; then
    warn_msg "সতর্কতা: HOST_APK-তে বিদ্যমান DEX সংখ্যায় আগে থেকেই গ্যাপ আছে (${host_dex_names[*]})! Android ART গ্যাপের পরের dex লোড করে না।"
fi

next_idx=$((max_idx + 1))
NEXT_DEX_NAME="classes${next_idx}.dex"
info_msg "HOST_APK-র বর্তমান DEX সংখ্যা: ${#sorted_indices[@]} (সর্বোচ্চ index=${max_idx}) -> নতুন মোড DEX হবে: ${NEXT_DEX_NAME}"
info_msg "মনে রাখুন: classes.dex (1), classes2.dex (2) ... ক্রমিক সংখ্যায় মাঝখানে কোনো সংখ্যা বাদ পড়লে (যেমন classes10.dex না থাকলে classes11.dex) Android সেই dex লোড করবে না!"

# Check android:extractNativeLibs if aapt / aapt2 / apkanalyzer is available
EXTRACT_NATIVE_LIBS="unknown"
if command -v aapt >/dev/null 2>&1; then
    manifest_dump="$(aapt dump xmltree "${HOST_APK}" AndroidManifest.xml 2>/dev/null || true)"
    if printf '%s\n' "${manifest_dump}" | grep -qE 'extractNativeLibs.*=\(type 0x12\)0x0|extractNativeLibs.*=false'; then
        EXTRACT_NATIVE_LIBS="false"
    elif printf '%s\n' "${manifest_dump}" | grep -qE 'extractNativeLibs.*=\(type 0x12\)0xffffffff|extractNativeLibs.*=true'; then
        EXTRACT_NATIVE_LIBS="true"
    fi
elif command -v aapt2 >/dev/null 2>&1; then
    manifest_dump="$(aapt2 dump xmltree "${HOST_APK}" --file AndroidManifest.xml 2>/dev/null || true)"
    if printf '%s\n' "${manifest_dump}" | grep -qE 'extractNativeLibs.*=false|extractNativeLibs.*0x0'; then
        EXTRACT_NATIVE_LIBS="false"
    elif printf '%s\n' "${manifest_dump}" | grep -qE 'extractNativeLibs.*=true|extractNativeLibs.*0xffffffff'; then
        EXTRACT_NATIVE_LIBS="true"
    fi
elif command -v apkanalyzer >/dev/null 2>&1; then
    manifest_xml="$(apkanalyzer manifest print "${HOST_APK}" 2>/dev/null || true)"
    if printf '%s\n' "${manifest_xml}" | grep -q 'android:extractNativeLibs="false"'; then
        EXTRACT_NATIVE_LIBS="false"
    elif printf '%s\n' "${manifest_xml}" | grep -q 'android:extractNativeLibs="true"'; then
        EXTRACT_NATIVE_LIBS="true"
    fi
fi

if [ "${EXTRACT_NATIVE_LIBS}" = "false" ]; then
    info_msg "Manifest android:extractNativeLibs=\"false\" শনাক্ত হয়েছে -> .so ফাইল অবশ্যই uncompressed (zip -0) এবং 4KB page-aligned (zipalign -p 4) হতে হবে।"
else
    info_msg "android:extractNativeLibs=${EXTRACT_NATIVE_LIBS} (তবুও নিরাপত্তার জন্য .so uncompressed + zipalign -p 4 করা হচ্ছে)।"
fi

# ------------------------------------------------------------------------------
# 3. Copy Host APK -> Strip Old Signature -> Inject DEX & Uncompressed .so -> Align -> Sign
# ------------------------------------------------------------------------------
WORK_APK="${WORK_DIR}/work.apk"
ALIGNED_APK="${WORK_DIR}/aligned.apk"
cp "${HOST_APK}" "${WORK_APK}"

# Delete old V1 signature entries (ignore exit code 12 if no matching files)
set +e
zip -q -d "${WORK_APK}" 'META-INF/MANIFEST.MF' 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC' 'META-INF/*.SIG' >/dev/null 2>&1
zip_del_rc=$?
set -e
if [ "${zip_del_rc}" -ne 0 ] && [ "${zip_del_rc}" -ne 12 ]; then
    err_exit "পুরনো META-INF সিগনেচার মুছতে সমস্যা হয়েছে (zip exit=${zip_del_rc})।"
fi

# Remove any stale libModXLab.so if re-patching, then stage single mod dex & .so
set +e
zip -q -d "${WORK_APK}" 'lib/*/libModXLab.so' >/dev/null 2>&1
set -e

cp "${WORK_DIR}/classes.dex" "${STAGE_DIR}/${NEXT_DEX_NAME}"
(
    cd "${STAGE_DIR}"
    zip -q "${WORK_APK}" "${NEXT_DEX_NAME}"
)

for abi in "${selected_abis[@]}"; do
    mkdir -p "${STAGE_DIR}/lib/${abi}"
    unzip -p "${MOD_APK}" "lib/${abi}/libModXLab.so" > "${STAGE_DIR}/lib/${abi}/libModXLab.so"
    [ -s "${STAGE_DIR}/lib/${abi}/libModXLab.so" ] || err_exit "MOD_APK থেকে lib/${abi}/libModXLab.so বের করা যায়নি।"
done

(
    cd "${STAGE_DIR}"
    # Store .so UNCOMPRESSED (-0) for extractNativeLibs="false" & clean mmap
    zip -q -0 "${WORK_APK}" lib/*/libModXLab.so
)

if [ "${HAS_ZIPALIGN}" -eq 1 ]; then
    info_msg "zipalign -p -f 4 চালানো হচ্ছে (Sign করার আগে)..."
    zipalign -p -f 4 "${WORK_APK}" "${ALIGNED_APK}" || err_exit "zipalign ব্যর্থ হয়েছে!"
else
    info_msg "Python 4KB page-alignment চালানো হচ্ছে (Sign করার আগে)..."
    python3 - "${WORK_APK}" "${ALIGNED_APK}" <<'PY' || err_exit "Python ZIP page-alignment ব্যর্থ হয়েছে!"
import sys, zipfile

src_path, dst_path = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src_path, "r") as zin, zipfile.ZipFile(dst_path, "w") as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        new_info = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        new_info.compress_type = info.compress_type
        new_info.external_attr = info.external_attr
        new_info.internal_attr = info.internal_attr
        new_info.flag_bits = info.flag_bits & ~0x08
        if info.compress_type == zipfile.ZIP_STORED:
            align = 4096 if info.filename.endswith(".so") else 4
            cur_pos = zout.fp.tell()
            fname_len = len(info.filename.encode("utf-8"))
            data_off = cur_pos + 30 + fname_len
            pad = (align - (data_off % align)) % align
            new_info.extra = b"\x00" * pad
        zout.writestr(new_info, data, compress_type=info.compress_type)
PY
fi

info_msg "apksigner দিয়ে V1 + V2 + V3 Sign করা হচ্ছে..."
mkdir -p "$(dirname "${OUT_APK}")"
apksigner sign \
    --ks "${KEYSTORE}" \
    --ks-type "${KEYSTORE_TYPE}" \
    --ks-key-alias "${KEY_ALIAS}" \
    --ks-pass env:MXL_KS_PASS \
    --key-pass env:MXL_KEY_PASS \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "${OUT_APK}" \
    "${ALIGNED_APK}" || err_exit "apksigner sign ব্যর্থ হয়েছে! Keystore পথ, type (${KEYSTORE_TYPE}), alias (${KEY_ALIAS}) ও পাসওয়ার্ড চেক করুন।"

# ------------------------------------------------------------------------------
# 4. Post-flight Verification Checklist (Bengali PASS/FAIL)
# ------------------------------------------------------------------------------
printf '\n================ পোস্ট-ফ্লাইট যাচাই রিপোর্ট ================\n'
verify_failed=0

verify_out="$(apksigner verify --verbose --print-certs "${OUT_APK}" 2>&1 || true)"

v1_ok=0; v2_ok=0; v3_ok=0
printf '%s\n' "${verify_out}" | grep -qiE 'Verified using v1 scheme.*: true' && v1_ok=1
printf '%s\n' "${verify_out}" | grep -qiE 'Verified using v2 scheme.*: true' && v2_ok=1
printf '%s\n' "${verify_out}" | grep -qiE 'Verified using v3 scheme.*: true' && v3_ok=1

if [ "${v1_ok}" -eq 1 ] && [ "${v2_ok}" -eq 1 ] && [ "${v3_ok}" -eq 1 ]; then
    printf '[PASS] V1 + V2 + V3 Signature Scheme: সবগুলো true আছে।\n'
else
    printf '[FAIL] V1 + V2 + V3 Signature Scheme: v1=%s v2=%s v3=%s (সব true হতে হবে)।\n' "${v1_ok}" "${v2_ok}" "${v3_ok}"
    verify_failed=1
fi

actual_cert_sha256="$(printf '%s\n' "${verify_out}" \
    | grep -m1 'SHA-256 digest:' \
    | sed 's/.*SHA-256 digest:[[:space:]]*//' \
    | tr -d ':[:space:]' \
    | tr '[:upper:]' '[:lower:]')"

if [ -n "${CERT_SHA256}" ]; then
    if [ "${actual_cert_sha256}" = "${CERT_SHA256}" ]; then
        printf '[PASS] Certificate SHA-256 প্রত্যাশিত CERT_SHA256-এর সাথে হুবহু মিলেছে (%s)।\n' "${actual_cert_sha256}"
    else
        printf '[FAIL] Certificate SHA-256 অমিল! পাওয়া=%s প্রত্যাশিত=%s\n' "${actual_cert_sha256}" "${CERT_SHA256}"
        verify_failed=1
    fi
else
    printf '[WARN] CERT_SHA256 দেওয়া হয়নি; বর্তমান APK Cert SHA-256 = %s (CI Summary-র সাথে নিজে মিলিয়ে নিন)।\n' "${actual_cert_sha256}"
fi

final_dex_sha256="$(unzip -p "${OUT_APK}" "${NEXT_DEX_NAME}" | sha256sum | awk '{print tolower($1)}')"
if [ "${final_dex_sha256}" = "${MOD_DEX_SHA256}" ]; then
    printf '[PASS] চূড়ান্ত APK-এর %s SHA-256 অক্ষত আছে (%s)।\n' "${NEXT_DEX_NAME}" "${final_dex_sha256}"
else
    printf '[FAIL] %s SHA-256 বদলে গেছে! মূল=%s চূড়ান্ত=%s\n' "${NEXT_DEX_NAME}" "${MOD_DEX_SHA256}" "${final_dex_sha256}"
    verify_failed=1
fi

for abi in "${selected_abis[@]}"; do
    so_path="lib/${abi}/libModXLab.so"
    if ! unzip -Z1 "${OUT_APK}" "${so_path}" >/dev/null 2>&1; then
        printf '[FAIL] চূড়ান্ত APK-তে %s অনুপস্থিত!\n' "${so_path}"
        verify_failed=1
    else
        if unzip -v "${OUT_APK}" "${so_path}" 2>/dev/null | grep -q 'Stored'; then
            printf '[PASS] %s উপস্থিত এবং Uncompressed (Stored)।\n' "${so_path}"
        else
            printf '[FAIL] %s Uncompressed (Stored) অবস্থায় নেই!\n' "${so_path}"
            verify_failed=1
        fi
    fi
done

if [ "${HAS_ZIPALIGN}" -eq 1 ]; then
    if zipalign -c -p 4 "${OUT_APK}" >/dev/null 2>&1; then
        printf '[PASS] zipalign -c -p 4 পেজ অ্যালাইনমেন্ট যাচাই সফল।\n'
    else
        printf '[FAIL] zipalign -c -p 4 যাচাই ব্যর্থ হয়েছে!\n'
        verify_failed=1
    fi
else
    if python3 - "${OUT_APK}" <<'PY'
import struct, sys, zipfile
p = sys.argv[1]
with open(p, "rb") as fp, zipfile.ZipFile(p, "r") as z:
    for info in z.infolist():
        if info.compress_type != zipfile.ZIP_STORED:
            continue
        fp.seek(info.header_offset + 26)
        flen, exlen = struct.unpack("<HH", fp.read(4))
        data_off = info.header_offset + 30 + flen + exlen
        req = 4096 if info.filename.endswith(".so") else 4
        if data_off % req != 0:
            sys.exit(1)
PY
    then
        printf '[PASS] 4KB/.so ও 4-byte পেজ অ্যালাইনমেন্ট যাচাই সফল (Python check)।\n'
    else
        printf '[FAIL] পেজ অ্যালাইনমেন্ট যাচাই ব্যর্থ হয়েছে!\n'
        verify_failed=1
    fi
fi
printf '=============================================================\n'

if [ "${verify_failed}" -ne 0 ]; then
    err_exit "পোস্ট-ফ্লাইট যাচাইয়ে ১ বা একাধিক ত্রুটি পাওয়া গেছে! এই APK ব্যবহার করবেন না।"
fi

cat <<EOF

[সফল] সাইন করা APK তৈরি হয়েছে: ${OUT_APK}

--- মনে রাখবেন (Smali ও AndroidManifest ম্যানুয়াল এডিট চেকলিস্ট) ---
এই স্ক্রিপ্ট শুধু ${NEXT_DEX_NAME}, lib/<abi>/libModXLab.so যোগ ও Sign করে।
হোস্ট গেমে প্রথমবার ইনজেক্ট করতে নিচের ম্যানুয়াল কাজগুলো Sign করার আগেই করা থাকতে হবে:
  1) হোস্ট গেমের Main Activity-র onCreate()-এ 'invoke-super' লাইনের ঠিক পরে বসান:
     invoke-static {p0}, Lcom/android/support/Main;->Start(Landroid/content/Context;)V
  2) AndroidManifest.xml-এ নিশ্চিত করুন:
     - <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
     - <uses-permission android:name="android.permission.INTERNET" />
     - <service android:name="com.android.support.Launcher"
                android:enabled="true"
                android:exported="false"
                android:stopWithTask="true" />
  3) ইনস্টল করার পর ফোনের Settings -> Apps -> [Game] -> Display over other apps = Allow করুন।
EOF
