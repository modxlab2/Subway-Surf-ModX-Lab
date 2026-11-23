
#include "Jni.hpp"
#include <list>
#include <vector>
#include <cstring>
#include <string>
#include <pthread.h>
#include <thread>
#include <unistd.h>
#include <fstream>
#include <iostream>
#include <sstream>
#include <dlfcn.h>
#include "Includes/obfuscate.h"
#include "Includes/get_device_api_level_inlines.h"
#include "Menu/Jni.hpp"
#include "Includes/Logger.h"
#include "SecurityGuard.hpp"

//Jni stuff from MrDarkRX https://github.com/MrDarkRXx/DarkMod-Floating
void setDialog(jobject ctx, JNIEnv *env, const char *title, const char *msg){
    jclass Alert = env->FindClass(OBFUSCATE("android/app/AlertDialog$Builder"));
    jmethodID AlertCons = env->GetMethodID(Alert, OBFUSCATE("<init>"), OBFUSCATE("(Landroid/content/Context;)V"));

    jobject MainAlert = env->NewObject(Alert, AlertCons, ctx);

    jmethodID setTitle = env->GetMethodID(Alert, OBFUSCATE("setTitle"), OBFUSCATE("(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;"));
    env->CallObjectMethod(MainAlert, setTitle, env->NewStringUTF(title));

    jmethodID setMsg = env->GetMethodID(Alert, OBFUSCATE("setMessage"), OBFUSCATE("(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;"));
    env->CallObjectMethod(MainAlert, setMsg, env->NewStringUTF(msg));

    jmethodID setCa = env->GetMethodID(Alert, OBFUSCATE("setCancelable"), OBFUSCATE("(Z)Landroid/app/AlertDialog$Builder;"));
    env->CallObjectMethod(MainAlert, setCa, false);

    jmethodID setPB = env->GetMethodID(Alert, OBFUSCATE("setPositiveButton"), OBFUSCATE("(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;"));
    env->CallObjectMethod(MainAlert, setPB, env->NewStringUTF("Ok"), static_cast<jobject>(NULL));

    jmethodID create = env->GetMethodID(Alert, OBFUSCATE("create"), OBFUSCATE("()Landroid/app/AlertDialog;"));
    jobject creaetob = env->CallObjectMethod(MainAlert, create);

    jclass AlertN = env->FindClass(OBFUSCATE("android/app/AlertDialog"));

    jmethodID show = env->GetMethodID(AlertN, OBFUSCATE("show"), OBFUSCATE("()V"));
    env->CallVoidMethod(creaetob, show);
}

void Toast(JNIEnv *env, jobject thiz, const char *text, int length) {
    jstring jstr = env->NewStringUTF(text);
    jclass toast = env->FindClass(OBFUSCATE("android/widget/Toast"));
    jmethodID methodMakeText =env->GetStaticMethodID(toast,OBFUSCATE("makeText"),OBFUSCATE("(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;"));
    jobject toastobj = env->CallStaticObjectMethod(toast, methodMakeText,thiz, jstr, length);
    jmethodID methodShow = env->GetMethodID(toast, OBFUSCATE("show"), OBFUSCATE("()V"));
    env->CallVoidMethod(toastobj, methodShow);
}

bool startService(JNIEnv *env, jobject ctx){
    jclass native_context = env->GetObjectClass(ctx);
    jclass intentClass = env->FindClass(OBFUSCATE("android/content/Intent"));
    jclass actionString = env->FindClass(OBFUSCATE("com/android/support/Launcher"));
    if (!native_context || !intentClass || !actionString) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (native_context) env->DeleteLocalRef(native_context);
        if (intentClass) env->DeleteLocalRef(intentClass);
        if (actionString) env->DeleteLocalRef(actionString);
        return false;
    }
    jmethodID newIntent = env->GetMethodID(intentClass, OBFUSCATE("<init>"), OBFUSCATE("(Landroid/content/Context;Ljava/lang/Class;)V"));
    jmethodID startServiceMid = env->GetMethodID(native_context, OBFUSCATE("startService"), OBFUSCATE("(Landroid/content/Intent;)Landroid/content/ComponentName;"));
    if (!newIntent || !startServiceMid) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(native_context);
        env->DeleteLocalRef(intentClass);
        env->DeleteLocalRef(actionString);
        return false;
    }
    jobject intent = env->NewObject(intentClass, newIntent, ctx, actionString);
    jobject compName = env->CallObjectMethod(ctx, startServiceMid, intent);
    bool started = true;
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        started = false;
    } else if (!compName) {
        started = false;
    }
    if (compName) env->DeleteLocalRef(compName);
    if (intent) env->DeleteLocalRef(intent);
    env->DeleteLocalRef(actionString);
    env->DeleteLocalRef(intentClass);
    env->DeleteLocalRef(native_context);
    return started;
}

static void callMainStaticCtxMethod(JNIEnv *env, jclass mainCls, jobject ctx, const char* name) {
    if (!env || !mainCls || !ctx) return;
    jmethodID mid = env->GetStaticMethodID(mainCls, name, OBFUSCATE("(Landroid/content/Context;)V"));
    if (!mid) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }
    env->CallStaticVoidMethod(mainCls, mid, ctx);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

void startActivityPermisson(JNIEnv *env, jobject ctx){
    jclass native_context = env->GetObjectClass(ctx);
    jmethodID startActivity = env->GetMethodID(native_context, OBFUSCATE("startActivity"),OBFUSCATE("(Landroid/content/Intent;)V"));

    jmethodID pack = env->GetMethodID(native_context, OBFUSCATE("getPackageName"),OBFUSCATE("()Ljava/lang/String;"));
    jstring packageName = static_cast<jstring>(env->CallObjectMethod(ctx, pack));

    const char *pkg = env->GetStringUTFChars(packageName, 0);

    std::stringstream strpkg;
    strpkg << OBFUSCATE("package:");
    strpkg << pkg;
    std::string pakg = strpkg.str();
    env->ReleaseStringUTFChars(packageName, pkg);

    jclass Uri = env->FindClass(OBFUSCATE("android/net/Uri"));
    jmethodID Parce = env->GetStaticMethodID(Uri, OBFUSCATE("parse"), OBFUSCATE("(Ljava/lang/String;)Landroid/net/Uri;"));
    jstring pkgUriStr = env->NewStringUTF(pakg.c_str());
    jobject UriMethod = env->CallStaticObjectMethod(Uri, Parce, pkgUriStr);

    jclass intentclass = env->FindClass(OBFUSCATE("android/content/Intent"));
    jmethodID newIntent = env->GetMethodID(intentclass, OBFUSCATE("<init>"), OBFUSCATE("(Ljava/lang/String;Landroid/net/Uri;)V"));
    jstring actionStr = env->NewStringUTF(OBFUSCATE("android.settings.action.MANAGE_OVERLAY_PERMISSION"));
    jobject intent = env->NewObject(intentclass, newIntent, actionStr, UriMethod);

    jmethodID addFlags = env->GetMethodID(intentclass, OBFUSCATE("addFlags"), OBFUSCATE("(I)Landroid/content/Intent;"));
    if (addFlags && intent) {
        jobject ignored = env->CallObjectMethod(intent, addFlags, (jint)0x10000000); // FLAG_ACTIVITY_NEW_TASK
        if (ignored) env->DeleteLocalRef(ignored);
    }

    env->CallVoidMethod(ctx, startActivity, intent);
    if (env->ExceptionCheck()) env->ExceptionClear();

    if (intent) env->DeleteLocalRef(intent);
    if (actionStr) env->DeleteLocalRef(actionStr);
    if (intentclass) env->DeleteLocalRef(intentclass);
    if (UriMethod) env->DeleteLocalRef(UriMethod);
    if (pkgUriStr) env->DeleteLocalRef(pkgUriStr);
    if (Uri) env->DeleteLocalRef(Uri);
    if (packageName) env->DeleteLocalRef(packageName);
    if (native_context) env->DeleteLocalRef(native_context);
}


//Needed jclass parameter because this is a static java method
void CheckOverlayPermission(JNIEnv *env, jclass thiz, jobject ctx){
    //If overlay permission option is greyed out, make sure to add android.permission.SYSTEM_ALERT_WINDOW in manifest
    if (!SecurityGuard::verifyPathMenuInit(env, ctx)) {
        SEC_DIAG_LOG(113, "CheckOverlayPermission blocked by verifyPathMenuInit");
        return;
    }

    LOGI(OBFUSCATE("Check overlay permission"));

    int sdkVer = api_level();
    if (sdkVer >= 23){ //Android 6.0
        jclass Settings = env->FindClass(OBFUSCATE("android/provider/Settings"));
        jmethodID canDraw =env->GetStaticMethodID(Settings, OBFUSCATE("canDrawOverlays"), OBFUSCATE("(Landroid/content/Context;)Z"));
        jboolean allowed = env->CallStaticBooleanMethod(Settings, canDraw, ctx);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            allowed = JNI_FALSE;
        }
        if (Settings) env->DeleteLocalRef(Settings);
        if (!allowed){
            SEC_DIAG_LOG(202, "Overlay permission missing; requesting permission and falling back to Activity window");
            Toast(env, ctx, OBFUSCATE("Overlay permission is required in order to show mod menu."), 1);
            startActivityPermisson(env, ctx);
            callMainStaticCtxMethod(env, thiz, ctx, OBFUSCATE("StartWithoutPermission"));
            return;
        }
    }

    if (!SecurityGuard::periodicReverify()) {
        SEC_DIAG_LOG(113, "CheckOverlayPermission blocked by periodicReverify");
        return;
    }

    LOGI(OBFUSCATE("Start service"));

    //StartMod Normal; if host APK omitted Launcher service in manifest, fall back to in-process overlay
    if (!startService(env, ctx)) {
        SEC_DIAG_LOG(203, "Launcher service missing or blocked in host manifest; using in-process fallback");
        callMainStaticCtxMethod(env, thiz, ctx, OBFUSCATE("startInProcessFallback"));
    }
}