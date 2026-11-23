#!/data/data/com.termux/files/usr/bin/env bash
# Fallback-compatible shebang for Termux / Linux:
# Run with: bash scripts/inject-and-sign.sh [options]

set -euo pipefail

# ==============================================================================
# ModX Lab - Termux Host-APK Injector, Page-Aligner & V1+V2+V3 Signer
# Terminal output: plain ASCII simple English only (safe for all terminals).
# Optional --report-bn: writes inject-report-bn.html in Bengali UTF-8.
# ==============================================================================

MOD_APK="${MOD_APK:-}"
HOST_APK="${HOST_APK:-}"
KEYSTORE="${KEYSTORE:-}"
KEY_ALIAS="${KEY_ALIAS:-modxlab}"
KEYSTORE_TYPE="${KEYSTORE_TYPE:-PKCS12}"
OUT_APK="${OUT_APK:-}"
CERT_SHA256="${CERT_SHA256:-}"
CERT_SHA256_FILE="${CERT_SHA256_FILE:-}"
REPORT_BN="${REPORT_BN:-0}"

REPORT_PATH="${PWD}/inject-report-bn.html"
WORK_DIR=""
MXL_KS_PASS=""
MXL_KEY_PASS=""

CURRENT_STEP=1
OVERALL_STATUS="FAIL"

STEP1_STATUS="PENDING"; STEP1_BN_MSG="ইনপুট ও টুল যাচাই শুরু হয়নি।"; STEP1_BN_FIX=""
STEP2_STATUS="PENDING"; STEP2_BN_MSG="Mod APK ও Host APK পরীক্ষা শুরু হয়নি।"; STEP2_BN_FIX=""
STEP3_STATUS="PENDING"; STEP3_BN_MSG="DEX ও .so ফাইল যোগ করা শুরু হয়নি।"; STEP3_BN_FIX=""
STEP4_STATUS="PENDING"; STEP4_BN_MSG="4KB page alignment শুরু হয়নি।"; STEP4_BN_FIX=""
STEP5_STATUS="PENDING"; STEP5_BN_MSG="V1+V2+V3 সাইন করা শুরু হয়নি।"; STEP5_BN_FIX=""
STEP6_STATUS="PENDING"; STEP6_BN_MSG="চূড়ান্ত যাচাই (verification) শুরু হয়নি।"; STEP6_BN_FIX=""

CHK_SIGN_STATUS="PENDING";  CHK_SIGN_BN=""
CHK_CERT_STATUS="PENDING";  CHK_CERT_BN=""
CHK_DEX_STATUS="PENDING";   CHK_DEX_BN=""
CHK_SO_STATUS="PENDING";    CHK_SO_BN=""
CHK_ALIGN_STATUS="PENDING"; CHK_ALIGN_BN=""

MOD_DEX_SHA256=""
NEXT_DEX_NAME=""
SELECTED_ABIS_STR=""
EXTRACT_NATIVE_LIBS="unknown"
ACTUAL_CERT_SHA256=""

# Pre-scan for --report-bn so even early argument errors can emit HTML report
for _arg in "$@"; do
    if [ "${_arg}" = "--report-bn" ]; then
        REPORT_BN=1
    fi
done

cleanup() {
    unset MXL_KS_PASS MXL_KEY_PASS KEYSTORE_PASS KEY_PASS 2>/dev/null || true
    if [ -n "${WORK_DIR}" ] && [ -d "${WORK_DIR}" ]; then
        rm -rf "${WORK_DIR}"
    fi
}
trap cleanup EXIT INT TERM

step_msg() {
    printf '\n[STEP %s/6] %s\n' "$1" "$2"
}

info_msg() {
    printf '[INFO] %s\n' "$1"
}

ok_msg() {
    printf '[OK] %s\n' "$1"
}

warn_msg() {
    printf '[WARN] %s\n' "$1" >&2
}

html_escape() {
    local s="${1:-}"
    s="${s//&/&amp;}"
    s="${s//</&lt;}"
    s="${s//>/&gt;}"
    s="${s//\"/&quot;}"
    printf '%s' "${s}"
}

set_step_state() {
    local num="$1"
    local st="$2"
    local msg="$3"
    local fix="${4:-}"
    case "${num}" in
        1) STEP1_STATUS="${st}"; STEP1_BN_MSG="${msg}"; STEP1_BN_FIX="${fix}" ;;
        2) STEP2_STATUS="${st}"; STEP2_BN_MSG="${msg}"; STEP2_BN_FIX="${fix}" ;;
        3) STEP3_STATUS="${st}"; STEP3_BN_MSG="${msg}"; STEP3_BN_FIX="${fix}" ;;
        4) STEP4_STATUS="${st}"; STEP4_BN_MSG="${msg}"; STEP4_BN_FIX="${fix}" ;;
        5) STEP5_STATUS="${st}"; STEP5_BN_MSG="${msg}"; STEP5_BN_FIX="${fix}" ;;
        6) STEP6_STATUS="${st}"; STEP6_BN_MSG="${msg}"; STEP6_BN_FIX="${fix}" ;;
    esac
}

badge_class() {
    case "$1" in
        PASS) printf 'badge-pass' ;;
        FAIL) printf 'badge-fail' ;;
        WARN) printf 'badge-warn' ;;
        *)    printf 'badge-pending' ;;
    esac
}

write_bn_report() {
    local overall_badge="FAIL"
    local overall_title="প্রক্রিয়া ব্যর্থ হয়েছে — নিচের সমাধান দেখুন"
    local overall_class="banner-fail"
    if [ "${OVERALL_STATUS}" = "PASS" ]; then
        overall_badge="PASS"
        overall_title="সব ধাপ সফল! সাইন করা APK ব্যবহারের জন্য প্রস্তুত"
        overall_class="banner-pass"
    fi

    local e_mod e_host e_out e_ks e_alias e_kstype e_cert_exp e_cert_act e_dex_sha e_dex_name e_abis e_extract
    e_mod="$(html_escape "${MOD_APK:-দেওয়া হয়নি}")"
    e_host="$(html_escape "${HOST_APK:-দেওয়া হয়নি}")"
    e_out="$(html_escape "${OUT_APK:-তৈরি হয়নি}")"
    e_ks="$(html_escape "${KEYSTORE:-দেওয়া হয়নি}")"
    e_alias="$(html_escape "${KEY_ALIAS}")"
    e_kstype="$(html_escape "${KEYSTORE_TYPE}")"
    e_cert_exp="$(html_escape "${CERT_SHA256:-দেওয়া হয়নি (ঐচ্ছিক)}")"
    e_cert_act="$(html_escape "${ACTUAL_CERT_SHA256:-পাওয়া যায়নি}")"
    e_dex_sha="$(html_escape "${MOD_DEX_SHA256:-হিসাব হয়নি}")"
    e_dex_name="$(html_escape "${NEXT_DEX_NAME:-নির্ধারিত হয়নি}")"
    e_abis="$(html_escape "${SELECTED_ABIS_STR:-পাওয়া যায়নি}")"
    e_extract="$(html_escape "${EXTRACT_NATIVE_LIBS}")"

    cat > "${REPORT_PATH}" <<HTML_EOF
<!DOCTYPE html>
<html lang="bn">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ModX Lab - APK Inject ও Sign রিপোর্ট</title>
<style>
  body {
    margin: 0;
    padding: 16px;
    background: #10131a;
    color: #eef1f7;
    font-family: "Noto Sans Bengali", "Hind Siliguri", "SolaimanLipi", "Kalpurush", sans-serif;
    font-size: 16px;
    line-height: 1.7;
  }
  .wrap { max-width: 760px; margin: 0 auto; }
  h1 { font-size: 22px; margin: 0 0 6px; color: #3ddb87; }
  h2 { font-size: 18px; margin: 0 0 8px; }
  .sub { color: #9aa3b2; font-size: 14px; margin-bottom: 16px; }
  .banner {
    padding: 16px;
    border-radius: 12px;
    margin-bottom: 16px;
    border: 1px solid;
  }
  .banner-pass { background: #12291e; border-color: #3ddb87; }
  .banner-fail { background: #2d1619; border-color: #ef5350; }
  .card {
    background: #181c26;
    border: 1px solid #2b3140;
    border-radius: 12px;
    padding: 14px 16px;
    margin-bottom: 12px;
  }
  .row { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
  .badge {
    display: inline-block;
    padding: 3px 10px;
    border-radius: 999px;
    font-size: 13px;
    font-weight: bold;
    letter-spacing: 0.4px;
  }
  .badge-pass { background: #1e4631; color: #5ef0a1; border: 1px solid #3ddb87; }
  .badge-fail { background: #4a1c21; color: #ff8a80; border: 1px solid #ef5350; }
  .badge-warn { background: #423214; color: #ffd54f; border: 1px solid #ffb300; }
  .badge-pending { background: #262b38; color: #9aa3b2; border: 1px solid #454d61; }
  .fix {
    margin-top: 8px;
    padding: 10px 12px;
    background: #222736;
    border-left: 4px solid #3ddb87;
    border-radius: 6px;
    font-size: 15px;
  }
  .fix-fail { border-left-color: #ef5350; }
  .kv { font-size: 14px; color: #c7cedb; word-break: break-all; }
  .kv b { color: #ffffff; }
  code {
    font-family: monospace;
    background: #0d1016;
    padding: 2px 6px;
    border-radius: 4px;
    color: #7ee787;
    font-size: 14px;
  }
  ul, ol { margin: 8px 0 0 20px; padding: 0; }
  li { margin-bottom: 6px; }
</style>
</head>
<body>
<div class="wrap">
  <h1>ModX Lab - APK Inject ও Sign রিপোর্ট</h1>
  <div class="sub">এই রিপোর্টে কোনো পাসওয়ার্ড, কীস্টোর ডেটা বা প্রাইভেট কী সংরক্ষণ করা হয়নি।</div>

  <div class="banner ${overall_class}">
    <div class="row">
      <h2>${overall_title}</h2>
      <span class="badge $(badge_class "${overall_badge}")">${overall_badge}</span>
    </div>
  </div>

  <div class="card kv">
    <div><b>Mod APK:</b> ${e_mod}</div>
    <div><b>Host APK:</b> ${e_host}</div>
    <div><b>Output APK:</b> ${e_out}</div>
    <div><b>Keystore:</b> ${e_ks} (alias: <code>${e_alias}</code>, type: <code>${e_kstype}</code>)</div>
    <div><b>Mod DEX নাম ও SHA-256:</b> <code>${e_dex_name}</code> | <code>${e_dex_sha}</code></div>
    <div><b>ব্যবহৃত ABIs:</b> <code>${e_abis}</code> | <b>extractNativeLibs:</b> <code>${e_extract}</code></div>
    <div><b>প্রত্যাশিত Cert SHA-256:</b> <code>${e_cert_exp}</code></div>
    <div><b>APK-র আসল Cert SHA-256:</b> <code>${e_cert_act}</code></div>
  </div>

  <div class="card">
    <div class="row"><h2>ধাপ ১/৬: ফাইল, টুল ও পাসওয়ার্ড প্রস্তুতি</h2><span class="badge $(badge_class "${STEP1_STATUS}")">${STEP1_STATUS}</span></div>
    <div>${STEP1_BN_MSG}</div>
$( [ -n "${STEP1_BN_FIX}" ] && printf '    <div class="fix fix-fail"><b>করণীয়:</b> %s</div>\n' "${STEP1_BN_FIX}" )
  </div>

  <div class="card">
    <div class="row"><h2>ধাপ ২/৬: Mod APK ও Host APK পরীক্ষা</h2><span class="badge $(badge_class "${STEP2_STATUS}")">${STEP2_STATUS}</span></div>
    <div>${STEP2_BN_MSG}</div>
$( [ -n "${STEP2_BN_FIX}" ] && printf '    <div class="fix fix-fail"><b>করণীয়:</b> %s</div>\n' "${STEP2_BN_FIX}" )
  </div>

  <div class="card">
    <div class="row"><h2>ধাপ ৩/৬: Host APK-তে DEX ও .so ফাইল যোগ</h2><span class="badge $(badge_class "${STEP3_STATUS}")">${STEP3_STATUS}</span></div>
    <div>${STEP3_BN_MSG}</div>
$( [ -n "${STEP3_BN_FIX}" ] && printf '    <div class="fix fix-fail"><b>করণীয়:</b> %s</div>\n' "${STEP3_BN_FIX}" )
  </div>

  <div class="card">
    <div class="row"><h2>ধাপ ৪/৬: 4KB Page Alignment (zipalign)</h2><span class="badge $(badge_class "${STEP4_STATUS}")">${STEP4_STATUS}</span></div>
    <div>${STEP4_BN_MSG}</div>
$( [ -n "${STEP4_BN_FIX}" ] && printf '    <div class="fix fix-fail"><b>করণীয়:</b> %s</div>\n' "${STEP4_BN_FIX}" )
  </div>

  <div class="card">
    <div class="row"><h2>ধাপ ৫/৬: V1 + V2 + V3 Sign</h2><span class="badge $(badge_class "${STEP5_STATUS}")">${STEP5_STATUS}</span></div>
    <div>${STEP5_BN_MSG}</div>
$( [ -n "${STEP5_BN_FIX}" ] && printf '    <div class="fix fix-fail"><b>করণীয়:</b> %s</div>\n' "${STEP5_BN_FIX}" )
  </div>

  <div class="card">
    <div class="row"><h2>ধাপ ৬/৬: চূড়ান্ত যাচাই (Post-flight Verification)</h2><span class="badge $(badge_class "${STEP6_STATUS}")">${STEP6_STATUS}</span></div>
    <div>${STEP6_BN_MSG}</div>
    <ul>
      <li><b>Signature (V1+V2+V3):</b> <span class="badge $(badge_class "${CHK_SIGN_STATUS}")">${CHK_SIGN_STATUS}</span> ${CHK_SIGN_BN}</li>
      <li><b>Certificate SHA-256:</b> <span class="badge $(badge_class "${CHK_CERT_STATUS}")">${CHK_CERT_STATUS}</span> ${CHK_CERT_BN}</li>
      <li><b>Inject করা DEX SHA-256:</b> <span class="badge $(badge_class "${CHK_DEX_STATUS}")">${CHK_DEX_STATUS}</span> ${CHK_DEX_BN}</li>
      <li><b>Native .so (Stored/Uncompressed):</b> <span class="badge $(badge_class "${CHK_SO_STATUS}")">${CHK_SO_STATUS}</span> ${CHK_SO_BN}</li>
      <li><b>4KB Page Alignment:</b> <span class="badge $(badge_class "${CHK_ALIGN_STATUS}")">${CHK_ALIGN_STATUS}</span> ${CHK_ALIGN_BN}</li>
    </ul>
$( [ -n "${STEP6_BN_FIX}" ] && printf '    <div class="fix fix-fail"><b>করণীয়:</b> %s</div>\n' "${STEP6_BN_FIX}" )
  </div>

  <div class="card">
    <h2>ইনস্টল করার আগে ম্যানুয়াল কাজের চেকলিস্ট</h2>
    <ol>
      <li>Host গেমের Main Activity-র <code>onCreate()</code> মেথডে <code>invoke-super</code>-এর ঠিক পরে এই লাইনটি বসানো থাকতে হবে:<br>
      <code>invoke-static {p0}, Lcom/android/support/Main;-&gt;Start(Landroid/content/Context;)V</code></li>
      <li><code>AndroidManifest.xml</code>-এ <code>SYSTEM_ALERT_WINDOW</code>, <code>INTERNET</code> এবং <code>com.android.support.Launcher</code> সার্ভিস যোগ করা থাকতে হবে।</li>
      <li>ফোনে ইনস্টল করার পর <b>Settings &gt; Apps &gt; [Game] &gt; Display over other apps</b> অপশনটি <b>Allow</b> করে দিন।</li>
    </ol>
  </div>
</div>
</body>
</html>
HTML_EOF
}

open_bn_report() {
    [ "${REPORT_BN}" -eq 1 ] || return 0
    write_bn_report
    info_msg "Bengali HTML report saved to: ${REPORT_PATH}"
    if command -v termux-open >/dev/null 2>&1; then
        termux-open "${REPORT_PATH}" >/dev/null 2>&1 || {
            info_msg "Could not auto-open report. Open this file in your browser or file manager: ${REPORT_PATH}"
        }
    else
        info_msg "Open this file in your phone's file manager or browser to read the Bengali report: ${REPORT_PATH}"
    fi
}

notify_termux_api() {
    local msg="$1"
    if command -v termux-toast >/dev/null 2>&1; then
        termux-toast "${msg}" >/dev/null 2>&1 || true
    fi
}

err_exit() {
    local en_msg="$1"
    local en_fix="${2:-}"
    local bn_msg="${3:-একটি ধাপে সমস্যা হয়েছে।}"
    local bn_fix="${4:-উপরের ইংরেজি এরর মেসেজ দেখে ফাইল ও অপশন ঠিক করে আবার চেষ্টা করুন।}"

    set_step_state "${CURRENT_STEP}" "FAIL" "${bn_msg}" "${bn_fix}"
    OVERALL_STATUS="FAIL"

    printf '\n[FAIL] %s\n' "${en_msg}" >&2
    if [ -n "${en_fix}" ]; then
        printf '[FIX]  %s\n' "${en_fix}" >&2
    fi

    open_bn_report

    if [ "${REPORT_BN}" -eq 1 ]; then
        notify_termux_api "ModXLab FAILED. Report: ${REPORT_PATH}"
    else
        notify_termux_api "ModXLab FAILED: ${en_msg}"
    fi
    exit 1
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
    [--cert-sha256 <64-hex> | --cert-file <path/to/cert.txt>] \
    [--report-bn]

Options:
  --report-bn   Write a Bengali HTML report (inject-report-bn.html) and open it.

Environment variables (optional alternatives to flags):
  MOD_APK, HOST_APK, KEYSTORE, KEY_ALIAS, KEYSTORE_TYPE, OUT_APK,
  CERT_SHA256, CERT_SHA256_FILE, REPORT_BN=1
  (Passwords are prompted safely with hidden typing; never pass via CLI args.)
EOF
}

# ------------------------------------------------------------------------------
# Step 1: Inputs, Tools & Passwords
# ------------------------------------------------------------------------------
CURRENT_STEP=1

while [ "$#" -gt 0 ]; do
    case "$1" in
        -m|--mod-apk)
            [ "$#" -ge 2 ] || err_exit \
                "Missing file path after --mod-apk." \
                "Pass the path to app-release.apk after --mod-apk." \
                "--mod-apk ফ্ল্যাগের পর ফাইলের পথ দেওয়া হয়নি।" \
                "--mod-apk-এর পর app-release.apk-এর সঠিক পথ দিন।"
            MOD_APK="$2"; shift 2 ;;
        -g|--host-apk)
            [ "$#" -ge 2 ] || err_exit \
                "Missing file path after --host-apk." \
                "Pass the path to your patched game APK after --host-apk." \
                "--host-apk ফ্ল্যাগের পর গেমের APK পথ দেওয়া হয়নি।" \
                "--host-apk-এর পর হোস্ট গেমের APK পথ দিন।"
            HOST_APK="$2"; shift 2 ;;
        -k|--keystore)
            [ "$#" -ge 2 ] || err_exit \
                "Missing file path after --keystore." \
                "Pass the path to release.keystore after --keystore." \
                "--keystore ফ্ল্যাগের পর keystore-এর পথ দেওয়া হয়নি।" \
                "--keystore-এর পর release.keystore-এর পথ দিন।"
            KEYSTORE="$2"; shift 2 ;;
        -a|--alias)
            [ "$#" -ge 2 ] || err_exit \
                "Missing alias name after --alias." \
                "Pass your key alias (usually modxlab) after --alias." \
                "--alias ফ্ল্যাগের পর key alias দেওয়া হয়নি।" \
                "--alias-এর পর alias নাম (যেমন modxlab) দিন।"
            KEY_ALIAS="$2"; shift 2 ;;
        --ks-type)
            [ "$#" -ge 2 ] || err_exit \
                "Missing keystore type after --ks-type." \
                "Pass PKCS12 or JKS after --ks-type." \
                "--ks-type ফ্ল্যাগের পর keystore-এর ধরন দেওয়া হয়নি।" \
                "--ks-type-এর পর PKCS12 বা JKS দিন।"
            KEYSTORE_TYPE="$2"; shift 2 ;;
        -o|--out)
            [ "$#" -ge 2 ] || err_exit \
                "Missing output file path after --out." \
                "Pass the output signed APK path after --out." \
                "--out ফ্ল্যাগের পর আউটপুট APK-এর পথ দেওয়া হয়নি।" \
                "--out-এর পর নতুন signed APK-এর পথ দিন।"
            OUT_APK="$2"; shift 2 ;;
        -c|--cert-sha256)
            [ "$#" -ge 2 ] || err_exit \
                "Missing 64-character hex value after --cert-sha256." \
                "Copy the 64-char Cert SHA-256 from GitHub Build Summary." \
                "--cert-sha256 ফ্ল্যাগের পর ৬৪-অক্ষরের কোড দেওয়া হয়নি।" \
                "GitHub Build Summary থেকে ৬৪-অক্ষরের Cert SHA-256 কপি করে দিন।"
            CERT_SHA256="$2"; shift 2 ;;
        --cert-file)
            [ "$#" -ge 2 ] || err_exit \
                "Missing file path after --cert-file." \
                "Pass the path to your cert SHA-256 text file after --cert-file." \
                "--cert-file ফ্ল্যাগের পর ফাইলের পথ দেওয়া হয়নি।" \
                "--cert-file-এর পর cert টেক্সট ফাইলের পথ দিন।"
            CERT_SHA256_FILE="$2"; shift 2 ;;
        --report-bn)
            REPORT_BN=1; shift ;;
        -h|--help)
            usage; exit 0 ;;
        *)
            err_exit \
                "Unknown option: $1" \
                "Run 'bash scripts/inject-and-sign.sh --help' to see valid options." \
                "অচেনা অপশন দেওয়া হয়েছে: $1" \
                "--help দিয়ে সঠিক অপশনগুলো দেখে নিন।" ;;
    esac
done

step_msg 1 "Checking inputs, tools, and keystore password"

[ -n "${MOD_APK}" ]  || err_exit \
    "MOD_APK is missing." \
    "Add --mod-apk <path/to/app-release.apk> to your command." \
    "MOD_APK দেওয়া হয়নি।" \
    "কমান্ডে --mod-apk <app-release.apk-এর পথ> যোগ করুন।"
[ -n "${HOST_APK}" ] || err_exit \
    "HOST_APK is missing." \
    "Add --host-apk <path/to/game.apk> to your command." \
    "HOST_APK দেওয়া হয়নি।" \
    "কমান্ডে --host-apk <গেমের APK-এর পথ> যোগ করুন।"
[ -n "${KEYSTORE}" ] || err_exit \
    "KEYSTORE is missing." \
    "Add --keystore <path/to/release.keystore> to your command." \
    "KEYSTORE দেওয়া হয়নি।" \
    "কমান্ডে --keystore <release.keystore-এর পথ> যোগ করুন।"
[ -n "${KEY_ALIAS}" ] || err_exit \
    "KEY_ALIAS cannot be empty." \
    "Set --alias modxlab (or your keystore alias name)." \
    "KEY_ALIAS খালি রাখা যাবে না।" \
    "--alias modxlab (অথবা আপনার সঠিক alias) দিন।"

[ -f "${MOD_APK}" ]  || err_exit \
    "MOD_APK file not found: ${MOD_APK}" \
    "Check the folder and file name of app-release.apk." \
    "MOD_APK ফাইলটি খুঁজে পাওয়া যায়নি: ${MOD_APK}" \
    "ফাইলের ফোল্ডার ও নাম ঠিক আছে কি না যাচাই করুন।"
[ -f "${HOST_APK}" ] || err_exit \
    "HOST_APK file not found: ${HOST_APK}" \
    "Check the folder and file name of your host game APK." \
    "HOST_APK ফাইলটি খুঁজে পাওয়া যায়নি: ${HOST_APK}" \
    "হোস্ট গেমের APK-এর পথ ও নাম ঠিক আছে কি না দেখুন।"
[ -f "${KEYSTORE}" ] || err_exit \
    "KEYSTORE file not found: ${KEYSTORE}" \
    "Download release-keystore-backup from GitHub Actions and extract release.keystore." \
    "KEYSTORE ফাইলটি খুঁজে পাওয়া যায়নি: ${KEYSTORE}" \
    "GitHub Actions থেকে আপনার release.keystore ডাউনলোড করে সঠিক পথে রাখুন।"

for tool in unzip zip apksigner sha256sum; do
    command -v "${tool}" >/dev/null 2>&1 || err_exit \
        "Required tool '${tool}' is not installed." \
        "Run: pkg install -y zip unzip apksigner" \
        "প্রয়োজনীয় টুল '${tool}' Termux-এ ইনস্টল করা নেই।" \
        "Termux-এ চালান: pkg install -y zip unzip apksigner"
done

HAS_ZIPALIGN=0
if command -v zipalign >/dev/null 2>&1; then
    HAS_ZIPALIGN=1
elif ! command -v python3 >/dev/null 2>&1; then
    err_exit \
        "Neither 'zipalign' nor 'python3' is installed." \
        "Run: pkg install -y python" \
        "'zipalign' এবং 'python3' কোনোটাই ইনস্টল করা নেই।" \
        "Termux-এ ইনস্টল করুন: pkg install -y python"
else
    warn_msg "zipalign not found. Using built-in Python 4KB page alignment (memory alignment)."
fi

if [ -z "${OUT_APK}" ]; then
    host_dir="$(dirname "${HOST_APK}")"
    host_base="$(basename "${HOST_APK}")"
    host_stem="${host_base%.apk}"
    OUT_APK="${host_dir}/${host_stem}_signed.apk"
fi

if [ -n "${CERT_SHA256_FILE}" ]; then
    [ -f "${CERT_SHA256_FILE}" ] || err_exit \
        "CERT_SHA256_FILE not found: ${CERT_SHA256_FILE}" \
        "Check the path passed to --cert-file." \
        "CERT_SHA256_FILE খুঁজে পাওয়া যায়নি: ${CERT_SHA256_FILE}" \
        "--cert-file-এ দেওয়া পথটি যাচাই করুন।"
    CERT_SHA256="$(tr -d '[:space:]:<>' < "${CERT_SHA256_FILE}" | tr '[:upper:]' '[:lower:]')"
elif [ -n "${CERT_SHA256}" ]; then
    CERT_SHA256="$(printf '%s' "${CERT_SHA256}" | tr -d '[:space:]:<>' | tr '[:upper:]' '[:lower:]')"
fi

if [ -n "${CERT_SHA256}" ]; then
    if [[ ! "${CERT_SHA256}" =~ ^[0-9a-f]{64}$ ]]; then
        err_exit \
            "CERT_SHA256 is not a valid 64-character hex string." \
            "Copy only the 64 letters/numbers (0-9, a-f) from GitHub Build Summary without '< >' or ':'." \
            "দেওয়া CERT_SHA256 সঠিক ৬৪-অক্ষরের hex কোড নয়।" \
            "GitHub Build Summary থেকে '< >' বা ':' ছাড়া শুধু ৬৪-অক্ষরের hex কোড কপি করে দিন।"
    fi
fi

# Read keystore & key passwords safely (hidden input, never printed or saved)
if [ -n "${KEYSTORE_PASS:-}" ]; then
    MXL_KS_PASS="${KEYSTORE_PASS}"
else
    IFS= read -r -s -p "Enter keystore password (typing is hidden): " MXL_KS_PASS
    printf '\n'
fi
[ -n "${MXL_KS_PASS}" ] || err_exit \
    "Keystore password cannot be empty." \
    "Re-run the script and type the keystore password from your backup credentials." \
    "Keystore পাসওয়ার্ড খালি রাখা যাবে না।" \
    "স্ক্রিপ্ট আবার চালিয়ে credentials.txt-এ থাকা সঠিক Keystore পাসওয়ার্ড দিন।"

if [ -n "${KEY_PASS:-}" ]; then
    MXL_KEY_PASS="${KEY_PASS}"
else
    IFS= read -r -s -p "Enter key password for alias '${KEY_ALIAS}' (press Enter if same): " MXL_KEY_PASS
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
ok_msg "Inputs, tools, and passwords are ready."
set_step_state 1 "PASS" "সব ইনপুট ফাইল, টুল ও পাসওয়ার্ড সঠিকভাবে নেওয়া হয়েছে।"

# ------------------------------------------------------------------------------
# Step 2: Inspect MOD_APK & HOST_APK
# ------------------------------------------------------------------------------
CURRENT_STEP=2
step_msg 2 "Inspecting Mod APK and Host APK (DEX, ABIs, manifest)"

mod_dex_list=()
while IFS= read -r entry; do
    [ -n "${entry}" ] && mod_dex_list+=("${entry}")
done < <(unzip -Z1 "${MOD_APK}" '*.dex' 2>/dev/null | sort || true)

if [ "${#mod_dex_list[@]}" -ne 1 ] || [ "${mod_dex_list[0]}" != "classes.dex" ]; then
    err_exit \
        "MOD_APK must contain exactly 1 file named classes.dex (found ${#mod_dex_list[@]}: ${mod_dex_list[*]:-none})." \
        "Download the official app-release.apk from GitHub Actions ModXLab-release-signed artifact." \
        "MOD_APK-তে ঠিক ১টি 'classes.dex' থাকতে হবে, কিন্তু পাওয়া গেছে ${#mod_dex_list[@]}টি।" \
        "GitHub Actions-এর ModXLab-release-signed artifact থেকে আসল app-release.apk ব্যবহার করুন।"
fi

unzip -q -o "${MOD_APK}" "classes.dex" -d "${WORK_DIR}"
MOD_DEX_SHA256="$(sha256sum "${WORK_DIR}/classes.dex" | awk '{print tolower($1)}')"
info_msg "Mod classes.dex SHA-256: ${MOD_DEX_SHA256}"

# Detect ABIs (CPU types like arm64-v8a / armeabi-v7a) inside HOST_APK under lib/
host_abis=()
while IFS= read -r abi; do
    [ -n "${abi}" ] && host_abis+=("${abi}")
done < <(unzip -Z1 "${HOST_APK}" 'lib/*/*' 2>/dev/null | awk -F'/' 'NF>=3 && $1=="lib" && $2!="" {print $2}' | sort -u || true)

if [ "${#host_abis[@]}" -eq 0 ]; then
    err_exit \
        "No 'lib/<abi>/' folder found inside HOST_APK." \
        "If this is a Split APK (XAPK/APKS), merge base.apk and config.<abi>.apk first, or pick the universal APK." \
        "HOST_APK-এর ভেতর 'lib/<abi>/' ফোল্ডার পাওয়া যায়নি।" \
        "এটি Split APK-এর base.apk হলে আগে Split APK-গুলো একসাথে merge করুন অথবা universal APK ব্যবহার করুন।"
fi
info_msg "Host APK ABIs (CPU types): ${host_abis[*]}"

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
                err_exit \
                    "MOD_APK is missing lib/arm64-v8a/libModXLab.so." \
                    "Rebuild app-release.apk in GitHub Actions with arm64-v8a enabled." \
                    "MOD_APK-তে lib/arm64-v8a/libModXLab.so পাওয়া যায়নি।" \
                    "GitHub Actions থেকে arm64-v8a সহ app-release.apk পুনরায় বিল্ড করে নিন।"
            fi
            ;;
        armeabi-v7a)
            has_armv7=1
            if unzip -Z1 "${MOD_APK}" "lib/armeabi-v7a/libModXLab.so" >/dev/null 2>&1; then
                selected_abis+=("armeabi-v7a")
            else
                err_exit \
                    "MOD_APK is missing lib/armeabi-v7a/libModXLab.so." \
                    "Rebuild app-release.apk in GitHub Actions with armeabi-v7a enabled." \
                    "MOD_APK-তে lib/armeabi-v7a/libModXLab.so পাওয়া যায়নি।" \
                    "GitHub Actions থেকে armeabi-v7a সহ app-release.apk পুনরায় বিল্ড করে নিন।"
            fi
            ;;
        *)
            warn_msg "Host APK has '${abi}', which ModXLab does not support (only ARM: arm64-v8a / armeabi-v7a)."
            ;;
    esac
done

if [ "${#selected_abis[@]}" -eq 0 ]; then
    err_exit \
        "No matching ARM ABI found between HOST_APK (${host_abis[*]}) and MOD_APK." \
        "Use a host APK that contains arm64-v8a or armeabi-v7a libraries." \
        "HOST_APK-এর কোনো ABI (${host_abis[*]})-এর জন্য MOD_APK-তে libModXLab.so পাওয়া যায়নি।" \
        "arm64-v8a বা armeabi-v7a আছে এমন একটি হোস্ট গেম APK ব্যবহার করুন।"
fi

SELECTED_ABIS_STR="${selected_abis[*]}"

if [ "${has_armv7}" -eq 1 ] && [ "${has_arm64}" -eq 0 ]; then
    warn_msg "Host APK is 32-bit only (armeabi-v7a). Adding only 32-bit .so because 64-bit arm64-v8a would never load on it."
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

[ "${#host_dex_indices[@]}" -gt 0 ] || err_exit \
    "No classes*.dex files found inside HOST_APK." \
    "Check that HOST_APK is a valid Android APK file." \
    "HOST_APK-তে কোনো classes*.dex ফাইল পাওয়া যায়নি।" \
    "সঠিক এবং অক্ষত একটি হোস্ট গেম APK নির্বাচন করুন।"

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
    warn_msg "Host APK already has a numbering gap in its DEX files (${host_dex_names[*]}). Android skips DEX files after a gap."
fi

next_idx=$((max_idx + 1))
NEXT_DEX_NAME="classes${next_idx}.dex"
info_msg "Host has ${#sorted_indices[@]} DEX files (highest=${max_idx}). New mod DEX will be: ${NEXT_DEX_NAME}"
info_msg "Note: DEX numbers must have no gap (classes.dex, classes2.dex ...), otherwise Android stops loading them."

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
    info_msg "Manifest sets extractNativeLibs=false: .so will be stored uncompressed and 4KB page-aligned."
else
    info_msg "Manifest extractNativeLibs=${EXTRACT_NATIVE_LIBS}: still storing .so uncompressed + 4KB page-aligned for safety."
fi
ok_msg "Pre-flight inspection passed."
set_step_state 2 "PASS" "Mod APK (1টি classes.dex) ও Host APK (ABIs: ${SELECTED_ABIS_STR}, নতুন DEX: ${NEXT_DEX_NAME}) যাচাই সফল।"

# ------------------------------------------------------------------------------
# Step 3: Copy Host APK -> Strip Old Signature -> Inject DEX & Uncompressed .so
# ------------------------------------------------------------------------------
CURRENT_STEP=3
step_msg 3 "Injecting ${NEXT_DEX_NAME} and uncompressed libModXLab.so into a copy of Host APK"

WORK_APK="${WORK_DIR}/work.apk"
ALIGNED_APK="${WORK_DIR}/aligned.apk"
cp "${HOST_APK}" "${WORK_APK}" || err_exit \
    "Failed to copy HOST_APK into temp folder." \
    "Check free storage space on your phone and read permissions." \
    "অস্থায়ী ফোল্ডারে HOST_APK কপি করা যায়নি।" \
    "ফোনের খালি স্টোরেজ ও ফাইল পারমিশন চেক করুন।"

# Delete old V1 signature entries (ignore exit code 12 if no matching files)
set +e
zip -q -d "${WORK_APK}" 'META-INF/MANIFEST.MF' 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC' 'META-INF/*.SIG' >/dev/null 2>&1
zip_del_rc=$?
set -e
if [ "${zip_del_rc}" -ne 0 ] && [ "${zip_del_rc}" -ne 12 ]; then
    err_exit \
        "Failed to remove old META-INF signature files (zip code ${zip_del_rc})." \
        "Check that HOST_APK is not corrupted and you have free storage." \
        "পুরনো META-INF সিগনেচার মুছতে সমস্যা হয়েছে (zip exit=${zip_del_rc})।" \
        "HOST_APK অক্ষত আছে কি না এবং ফোনে জায়গা খালি আছে কি না দেখুন।"
fi

# Remove any stale libModXLab.so if re-patching, then stage single mod dex & .so
set +e
zip -q -d "${WORK_APK}" 'lib/*/libModXLab.so' >/dev/null 2>&1
set -e

cp "${WORK_DIR}/classes.dex" "${STAGE_DIR}/${NEXT_DEX_NAME}"
(
    cd "${STAGE_DIR}"
    zip -q "${WORK_APK}" "${NEXT_DEX_NAME}"
) || err_exit \
    "Failed to add ${NEXT_DEX_NAME} into the APK copy." \
    "Check free storage space and re-run." \
    "APK কপিতে ${NEXT_DEX_NAME} যোগ করা যায়নি।" \
    "ফোনের স্টোরেজ খালি আছে কি না দেখে আবার চেষ্টা করুন।"

for abi in "${selected_abis[@]}"; do
    mkdir -p "${STAGE_DIR}/lib/${abi}"
    unzip -p "${MOD_APK}" "lib/${abi}/libModXLab.so" > "${STAGE_DIR}/lib/${abi}/libModXLab.so"
    [ -s "${STAGE_DIR}/lib/${abi}/libModXLab.so" ] || err_exit \
        "Failed to extract lib/${abi}/libModXLab.so from MOD_APK." \
        "Check that MOD_APK is not corrupted and re-download from GitHub Actions." \
        "MOD_APK থেকে lib/${abi}/libModXLab.so বের করা যায়নি।" \
        "GitHub Actions থেকে app-release.apk নতুন করে ডাউনলোড করুন।"
done

(
    cd "${STAGE_DIR}"
    # Store .so UNCOMPRESSED (-0) for extractNativeLibs="false" & clean mmap
    zip -q -0 "${WORK_APK}" lib/*/libModXLab.so
) || err_exit \
    "Failed to add uncompressed libModXLab.so into the APK copy." \
    "Check free storage space and re-run." \
    "APK কপিতে uncompressed libModXLab.so যোগ করা যায়নি।" \
    "ফোনের খালি স্টোরেজ যাচাই করে আবার চালান।"

ok_msg "Injected ${NEXT_DEX_NAME} and uncompressed libModXLab.so (${SELECTED_ABIS_STR})."
set_step_state 3 "PASS" "পুরনো সিগনেচার মুছে ${NEXT_DEX_NAME} এবং uncompressed (.so) ফাইল সফলভাবে যোগ করা হয়েছে।"

# ------------------------------------------------------------------------------
# Step 4: 4KB Page Alignment (before signing)
# ------------------------------------------------------------------------------
CURRENT_STEP=4
step_msg 4 "Aligning APK files (4KB page alignment before signing)"

if [ "${HAS_ZIPALIGN}" -eq 1 ]; then
    info_msg "Running zipalign -p -f 4..."
    zipalign -p -f 4 "${WORK_APK}" "${ALIGNED_APK}" || err_exit \
        "zipalign failed." \
        "Check free storage space, or install python ('pkg install -y python') as fallback." \
        "zipalign ব্যর্থ হয়েছে।" \
        "ফোনে খালি জায়গা আছে কি না দেখুন এবং HOST_APK ঠিক আছে কি না যাচাই করুন।"
    set_step_state 4 "PASS" "zipalign -p -f 4 দিয়ে 4KB page alignment সম্পন্ন হয়েছে।"
else
    info_msg "Running Python 4KB page-alignment fallback..."
    python3 - "${WORK_APK}" "${ALIGNED_APK}" <<'PY' || err_exit \
        "Python 4KB page-alignment failed." \
        "Check free storage space and ensure HOST_APK is a valid ZIP/APK." \
        "Python 4KB page-alignment ব্যর্থ হয়েছে।" \
        "ফোনে খালি স্টোরেজ আছে কি না ও HOST_APK ফাইল অক্ষত আছে কি না দেখুন।"
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
    set_step_state 4 "PASS" "Python fallback দিয়ে 4KB page alignment সম্পন্ন হয়েছে।"
fi
ok_msg "Page alignment complete."

# ------------------------------------------------------------------------------
# Step 5: Sign APK with V1 + V2 + V3
# ------------------------------------------------------------------------------
CURRENT_STEP=5
step_msg 5 "Signing APK with V1 + V2 + V3 schemes"

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
    "${ALIGNED_APK}" || err_exit \
    "apksigner sign failed." \
    "Check keystore path, --ks-type (${KEYSTORE_TYPE}), --alias (${KEY_ALIAS}), and passwords." \
    "apksigner দিয়ে Sign করা ব্যর্থ হয়েছে।" \
    "Keystore পথ, --ks-type (${KEYSTORE_TYPE}), --alias (${KEY_ALIAS}) এবং পাসওয়ার্ড সঠিক কি না দেখুন।"

ok_msg "APK signed: ${OUT_APK}"
set_step_state 5 "PASS" "apksigner দিয়ে V1 + V2 + V3 স্কিমে সফলভাবে সাইন করা হয়েছে।"

# ------------------------------------------------------------------------------
# Step 6: Post-flight Verification Checklist (Simple ASCII English)
# ------------------------------------------------------------------------------
CURRENT_STEP=6
step_msg 6 "Verifying final signed APK"

verify_failed=0
first_fail_en=""
first_fix_en=""
bn_fix_list=""

verify_out="$(apksigner verify --verbose --print-certs "${OUT_APK}" 2>&1 || true)"

v1_ok=0; v2_ok=0; v3_ok=0
printf '%s\n' "${verify_out}" | grep -qiE 'Verified using v1 scheme.*: true' && v1_ok=1
printf '%s\n' "${verify_out}" | grep -qiE 'Verified using v2 scheme.*: true' && v2_ok=1
printf '%s\n' "${verify_out}" | grep -qiE 'Verified using v3 scheme.*: true' && v3_ok=1

# Hosts with minSdkVersion >= 24 skip V1 verification in default `apksigner verify`.
# Re-check with --min-sdk-version 21 to confirm V1 JAR signature was actually written.
if [ "${v1_ok}" -eq 0 ]; then
    verify_v1_out="$(apksigner verify --min-sdk-version 21 --verbose "${OUT_APK}" 2>&1 || true)"
    printf '%s\n' "${verify_v1_out}" | grep -qiE 'Verified using v1 scheme.*: true' && v1_ok=1
fi

if [ "${v2_ok}" -eq 1 ] && [ "${v3_ok}" -eq 1 ]; then
    if [ "${v1_ok}" -eq 1 ]; then
        printf '[PASS] V1 + V2 + V3 signatures: all true.\n'
        CHK_SIGN_STATUS="PASS"
        CHK_SIGN_BN="V1, V2 এবং V3 সবগুলো সিগনেচার সঠিকভাবে যুক্ত হয়েছে।"
    else
        printf '[PASS] V2 + V3 signatures: true (v1=%s; V2+V3 is enough for minSdk >= 24).\n' "${v1_ok}"
        CHK_SIGN_STATUS="PASS"
        CHK_SIGN_BN="V2 + V3 সিগনেচার সফল (minSdk 24+ হলে V2+V3 যথেষ্ট)।"
    fi
else
    printf '[FAIL] V2 + V3 signatures are required: v1=%s v2=%s v3=%s.\n' "${v1_ok}" "${v2_ok}" "${v3_ok}"
    printf '[FIX]  Update apksigner (pkg upgrade -y apksigner) and sign again.\n'
    CHK_SIGN_STATUS="FAIL"
    CHK_SIGN_BN="V2 বা V3 সিগনেচার নেই (v1=${v1_ok}, v2=${v2_ok}, v3=${v3_ok})।"
    bn_fix_list="${bn_fix_list} apksigner আপডেট (pkg upgrade -y apksigner) করে আবার সাইন করুন।"
    first_fail_en="${first_fail_en:-V2 + V3 signature check failed.}"
    first_fix_en="${first_fix_en:-Update apksigner (pkg upgrade -y apksigner) and sign again.}"
    verify_failed=1
fi

actual_cert_sha256="$(printf '%s\n' "${verify_out}" \
    | grep -m1 'SHA-256 digest:' \
    | sed 's/.*SHA-256 digest:[[:space:]]*//' \
    | tr -d ':[:space:]' \
    | tr '[:upper:]' '[:lower:]')"
ACTUAL_CERT_SHA256="${actual_cert_sha256}"

if [ -n "${CERT_SHA256}" ]; then
    if [ "${actual_cert_sha256}" = "${CERT_SHA256}" ]; then
        printf '[PASS] Certificate SHA-256 matches CERT_SHA256 (%s).\n' "${actual_cert_sha256}"
        CHK_CERT_STATUS="PASS"
        CHK_CERT_BN="APK-র সার্টিফিকেট SHA-256 প্রত্যাশিত মানের সাথে হুবহু মিলেছে।"
    else
        printf '[FAIL] Certificate does not match. Found=%s Expected=%s\n' "${actual_cert_sha256}" "${CERT_SHA256}"
        printf '[FIX]  Use the keystore from the LATEST generate-keystore run, then build again.\n'
        CHK_CERT_STATUS="FAIL"
        CHK_CERT_BN="Certificate SHA-256 মেলেনি! পাওয়া মান: ${actual_cert_sha256}"
        bn_fix_list="${bn_fix_list} সর্বশেষ generate-keystore রান থেকে পাওয়া release.keystore ব্যবহার করুন এবং প্রয়োজনে GitHub Actions-এ নতুন করে APK বিল্ড করুন।"
        first_fail_en="${first_fail_en:-Certificate does not match.}"
        first_fix_en="${first_fix_en:-Use the keystore from the LATEST generate-keystore run, then build again.}"
        verify_failed=1
    fi
else
    printf '[WARN] CERT_SHA256 was not provided. APK Cert SHA-256 = %s\n' "${actual_cert_sha256}"
    printf '[INFO] Compare this SHA-256 with your GitHub Build Summary before installing.\n'
    CHK_CERT_STATUS="WARN"
    CHK_CERT_BN="CERT_SHA256 দেওয়া হয়নি; বর্তমান মান ${actual_cert_sha256} (GitHub Summary-র সাথে মিলিয়ে নিন)।"
fi

final_dex_sha256="$(unzip -p "${OUT_APK}" "${NEXT_DEX_NAME}" | sha256sum | awk '{print tolower($1)}')"
if [ "${final_dex_sha256}" = "${MOD_DEX_SHA256}" ]; then
    printf '[PASS] %s SHA-256 matches MOD_APK (%s).\n' "${NEXT_DEX_NAME}" "${final_dex_sha256}"
    CHK_DEX_STATUS="PASS"
    CHK_DEX_BN="${NEXT_DEX_NAME}-এর SHA-256 মূল Mod APK-র সাথে অক্ষত আছে।"
else
    printf '[FAIL] %s SHA-256 changed! Expected=%s Found=%s\n' "${NEXT_DEX_NAME}" "${MOD_DEX_SHA256}" "${final_dex_sha256}"
    printf '[FIX]  Do not edit %s. Re-run this script with a clean host APK.\n' "${NEXT_DEX_NAME}"
    CHK_DEX_STATUS="FAIL"
    CHK_DEX_BN="${NEXT_DEX_NAME}-এর SHA-256 বদলে গেছে!"
    bn_fix_list="${bn_fix_list} ${NEXT_DEX_NAME} কখনোই এডিট করবেন না; পরিষ্কার base_patched.apk দিয়ে আবার স্ক্রিপ্ট চালান।"
    first_fail_en="${first_fail_en:-Injected DEX hash changed.}"
    first_fix_en="${first_fix_en:-Do not edit the mod DEX file and re-run with a clean host APK.}"
    verify_failed=1
fi

so_all_ok=1
for abi in "${selected_abis[@]}"; do
    so_path="lib/${abi}/libModXLab.so"
    if ! unzip -Z1 "${OUT_APK}" "${so_path}" >/dev/null 2>&1; then
        printf '[FAIL] Missing %s in final APK.\n' "${so_path}"
        printf '[FIX]  Re-run this script and verify MOD_APK contains %s.\n' "${so_path}"
        so_all_ok=0
        verify_failed=1
    else
        if unzip -v "${OUT_APK}" "${so_path}" 2>/dev/null | grep -q 'Stored'; then
            printf '[PASS] %s is present and uncompressed (Stored).\n' "${so_path}"
        else
            printf '[FAIL] %s is compressed (must be Stored / uncompressed).\n' "${so_path}"
            printf '[FIX]  Re-run this script so zip -0 stores .so without compression.\n'
            so_all_ok=0
            verify_failed=1
        fi
    fi
done

if [ "${so_all_ok}" -eq 1 ]; then
    CHK_SO_STATUS="PASS"
    CHK_SO_BN="সব libModXLab.so (${SELECTED_ABIS_STR}) Uncompressed (Stored) অবস্থায় আছে।"
else
    CHK_SO_STATUS="FAIL"
    CHK_SO_BN="এক বা একাধিক libModXLab.so অনুপস্থিত অথবা compressed অবস্থায় আছে।"
    bn_fix_list="${bn_fix_list} MOD_APK-তে সঠিক ABI-এর .so আছে কি না দেখে স্ক্রিপ্টটি আবার চালান।"
    first_fail_en="${first_fail_en:-Native .so file missing or compressed.}"
    first_fix_en="${first_fix_en:-Ensure MOD_APK has the matching .so and re-run this script.}"
fi

if [ "${HAS_ZIPALIGN}" -eq 1 ]; then
    if zipalign -c -p 4 "${OUT_APK}" >/dev/null 2>&1; then
        printf '[PASS] 4KB page alignment check passed (zipalign).\n'
        CHK_ALIGN_STATUS="PASS"
        CHK_ALIGN_BN="zipalign -c -p 4 পেজ অ্যালাইনমেন্ট যাচাই সফল।"
    else
        printf '[FAIL] 4KB page alignment check failed (zipalign).\n'
        printf '[FIX]  Make sure zipalign runs before signing, then re-run this script.\n'
        CHK_ALIGN_STATUS="FAIL"
        CHK_ALIGN_BN="zipalign -c -p 4 যাচাই ব্যর্থ হয়েছে।"
        bn_fix_list="${bn_fix_list} ফোনে খালি জায়গা দেখে স্ক্রিপ্টটি আবার চালান যাতে সাইন করার আগে zipalign হয়।"
        first_fail_en="${first_fail_en:-Page alignment check failed.}"
        first_fix_en="${first_fix_en:-Re-run this script so zipalign runs cleanly before signing.}"
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
        printf '[PASS] 4KB page alignment check passed (Python check).\n'
        CHK_ALIGN_STATUS="PASS"
        CHK_ALIGN_BN="Python 4KB/.so ও 4-byte পেজ অ্যালাইনমেন্ট যাচাই সফল।"
    else
        printf '[FAIL] 4KB page alignment check failed.\n'
        printf '[FIX]  Re-run this script so Python page alignment finishes before signing.\n'
        CHK_ALIGN_STATUS="FAIL"
        CHK_ALIGN_BN="4KB পেজ অ্যালাইনমেন্ট যাচাই ব্যর্থ হয়েছে।"
        bn_fix_list="${bn_fix_list} পরিষ্কার HOST_APK দিয়ে স্ক্রিপ্টটি আবার চালান।"
        first_fail_en="${first_fail_en:-Page alignment check failed.}"
        first_fix_en="${first_fix_en:-Re-run this script with a clean HOST_APK.}"
        verify_failed=1
    fi
fi

if [ "${verify_failed}" -ne 0 ]; then
    err_exit \
        "Verification failed (${first_fail_en:-see errors above}). Do NOT install this APK." \
        "${first_fix_en:-Fix the [FAIL] items above and run the script again.}" \
        "চূড়ান্ত যাচাইয়ে ১ বা একাধিক ত্রুটি পাওয়া গেছে! এই APK ফোনে ইনস্টল করবেন না।" \
        "${bn_fix_list:-উপরের লাল [FAIL] ধাপগুলো ঠিক করে স্ক্রিপ্টটি আবার চালান।}"
fi

OVERALL_STATUS="PASS"
set_step_state 6 "PASS" "সব চূড়ান্ত যাচাই সফল হয়েছে। তৈরি হওয়া APK ইনস্টল করার জন্য প্রস্তুত।"

cat <<EOF

[OK] Signed APK created: ${OUT_APK}

--- Manual Check Before Installing ---
This script only injects ${NEXT_DEX_NAME}, lib/<abi>/libModXLab.so, and signs the APK.
Make sure you already edited the host game once before running this script:
  1) In host Main Activity onCreate(), right after invoke-super, add:
     invoke-static {p0}, Lcom/android/support/Main;->Start(Landroid/content/Context;)V
  2) In AndroidManifest.xml, make sure these exist:
     - <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
     - <uses-permission android:name="android.permission.INTERNET" />
     - <service android:name="com.android.support.Launcher"
                android:enabled="true"
                android:exported="false"
                android:stopWithTask="true" />
  3) After installing, open phone Settings -> Apps -> [Game] -> Display over other apps -> Allow.
EOF

open_bn_report

if [ "${REPORT_BN}" -eq 1 ]; then
    notify_termux_api "ModXLab OK! Signed APK ready. Report: ${REPORT_PATH}"
else
    notify_termux_api "ModXLab OK! Signed APK: ${OUT_APK}"
fi
