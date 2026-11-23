#ifndef MODXLAB_SECURITY_GUARD_HPP
#define MODXLAB_SECURITY_GUARD_HPP

#include <jni.h>

namespace SecurityGuard {

typedef void (*LockdownCallback)();

// Registers callback invoked on tamper / expiry to zero feature flags and unpatch memory.
void registerLockdownCallback(LockdownCallback cb);

// Integrity path 1: invoked in JNI_OnLoad (Menu/Setup.cpp).
// Stores JavaVM*, inspects environment and mapped/current APK.
bool verifyPathOnLoad(JavaVM* vm, JNIEnv* env);

// Integrity path 2a: invoked by SecurityNative.preload(Context).
// Natively resolves APK path, computes v2/v3 cert & DEX SHA-256, cross-checks PackageManager, starts watchdog.
bool verifyPathPreload(JNIEnv* env, jobject ctx);

// Integrity path 2b: invoked before menu creation / overlay start / Init / SettingsList.
// Independently inspects getPackageCodePath(), environment, and split integrity state.
bool verifyPathMenuInit(JNIEnv* env, jobject ctx);

// Integrity + Token gate invoked in GetFeatureList() before building mod UI toggles.
bool verifyPathFeatureGate(JNIEnv* env);

// Integrity + Token path 3: invoked inside Changes() before every feature toggle.
bool verifyPathToggle(JNIEnv* env, jobject ctx, int featNum);

// Integrity + Token path 4: invoked in hack_thread() before installing IL2CPP hooks or RenderHook().
bool isReadyForHooks();

// Periodic background re-verification of environment, APK integrity, and active server token.
bool periodicReverify();

#ifdef SEC_DIAG
void diagLog(int stage, const char* fmt, ...);
#define SEC_DIAG_LOG(stage, ...) ::SecurityGuard::diagLog((stage), __VA_ARGS__)
#else
#define SEC_DIAG_LOG(stage, ...) do {} while (0)
#endif

} // namespace SecurityGuard

#endif // MODXLAB_SECURITY_GUARD_HPP
