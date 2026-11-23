# ================================================================
# ModX Lab — Hardened ProGuard / R8 Rules
# ================================================================

# Manifest entry points & external game-launch entrypoint
-keep public class com.android.support.MainActivity
-keep public class com.android.support.Launcher
-keepclassmembers class ** {
    public static void Start(***);
    public static void StartWithoutPermission(***);
    public static void startInProcessFallback(***);
}

# Property-animation reflection (ObjectAnimator on maskX/maskY)
-keepclassmembers class com.android.support.TitanicTextView {
    public void setMaskX(float);
    public void setMaskY(float);
    public float getMaskX();
    public float getMaskY();
}

# JNI-bound classes resolved by name or static JNI export
-keep class com.android.support.SecurityNative {
    native <methods>;
}
-keep class com.android.support.Main {
    native <methods>;
}
-keep class com.android.support.Menu {
    native <methods>;
}
-keep class com.android.support.Preferences {
    native <methods>;
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Aggressive obfuscation & shrinking (unique package prevents collisions inside merged game APKs)
-repackageclasses 'mxl'
-allowaccessmodification
-overloadaggressively
-adaptclassstrings

# Keep minimal attributes (omit SourceFile/LineNumberTable leaks)
-keepattributes Exceptions,InnerClasses,EnclosingMethod,Signature,*Annotation*
-renamesourcefileattribute SourceFile

# Strip all android.util.Log calls in release builds
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# AndroidX Multidex
-keep class androidx.multidex.** { *; }
-dontwarn androidx.**

-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn org.jetbrains.annotations.**