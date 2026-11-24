package com.android.support;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Preferences {

    // ================================================================
    // Static state
    // ================================================================
    private static SharedPreferences sharedPreferences;
    private static Preferences prefsInstance;
    public  static Context context;

    /** Current SavePref setting — backed by FeatureStateStore. */
    public static boolean loadPref        = false;
    /** Expand toggle state */
    public static boolean isExpanded      = false;
    /** Compatibility flag — true when session was restored from a saved snapshot. */
    public static boolean sessionLoadPref = false;

    private static final String LENGTH                = "_length";
    private static final String DEFAULT_STRING_VALUE  = "";
    private static final int    DEFAULT_INT_VALUE     = 0;
    private static final double DEFAULT_DOUBLE_VALUE  = 0d;
    private static final float  DEFAULT_FLOAT_VALUE   = 0f;
    private static final long   DEFAULT_LONG_VALUE    = 0L;
    private static final boolean DEFAULT_BOOLEAN_VALUE = false;

    public static native void Changes(Context context, int featNum, String featName,
                                      int value, long Lvalue, boolean isOn, String inputText);

    // ================================================================
    // init() — called in Menu constructor BEFORE building any UI
    // ================================================================
    public static void init(Context ctx) {
        if (ctx == null) return;
        context = ctx;
        try {
            sharedPreferences = ctx.getApplicationContext().getSharedPreferences(
                    ctx.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );
            FeatureStateStore.init(ctx);
            loadPref        = FeatureStateStore.isSaveEnabled();
            sessionLoadPref = loadPref;
            isExpanded      = FeatureStateStore.readInitialBool(ctx, "Expand", -3, false);
        } catch (Exception e) {
            loadPref        = false;
            sessionLoadPref = false;
            isExpanded      = false;
        }
    }

    public static boolean isSaveEnabled() {
        return FeatureStateStore.isSaveEnabled();
    }

    public static int getMenuAccentColor() {
        return FeatureStateStore.getMenuAccentColor();
    }

    public static void setMenuAccentColor(int color) {
        FeatureStateStore.setMenuAccentColor(context, color);
    }

    public static int[] getRecentMenuColors() {
        return FeatureStateStore.getRecentMenuColors();
    }

    public static void recordMenuColorUsed(int color) {
        FeatureStateStore.recordMenuColorUsed(context, color);
    }

    public static void flush(String reason) {
        try {
            FeatureStateStore.flush(context, reason);
        } catch (Throwable ignored) { }
    }

    // ================================================================
    // Side-effect-free UI initial reads (NEVER invoke native Changes)
    // ================================================================
    public static boolean readInitialBool(String featureName, int featureNum, boolean defaultVal) {
        boolean val = FeatureStateStore.readInitialBool(context, featureName, featureNum, defaultVal);
        if (featureNum == -1) {
            loadPref = val;
        } else if (featureNum == -3) {
            isExpanded = val;
        }
        return val;
    }

    public static int readInitialInt(String featureName, int featureNum, int defaultVal) {
        return FeatureStateStore.readInitialInt(context, featureName, featureNum, defaultVal);
    }

    public static long readInitialLong(String featureName, int featureNum, long defaultVal) {
        return FeatureStateStore.readInitialLong(context, featureName, featureNum, defaultVal);
    }

    public static String readInitialString(String featureName, int featureNum, String defaultVal) {
        return FeatureStateStore.readInitialString(context, featureName, featureNum, defaultVal);
    }

    public static int readInitialColor(String featureName, int featureNum, int defaultColor) {
        return FeatureStateStore.readInitialColor(context, featureName, featureNum, defaultColor);
    }

    // ================================================================
    // Deterministic post-login native restore (once per launch)
    // ================================================================
    public static void pushRestoredSnapshotToNativeOnce() {
        List<FeatureStateStore.NativeEntry> entries = FeatureStateStore.consumePendingNativeRestore();
        if (entries == null || entries.isEmpty()) return;

        for (int i = 0; i < entries.size(); i++) {
            FeatureStateStore.NativeEntry e = entries.get(i);
            switch (e.type) {
                case FeatureStateStore.NativeEntry.TYPE_BOOL:
                    Changes(context, e.featNum, e.featName, 0, 0L, e.boolVal, null);
                    break;
                case FeatureStateStore.NativeEntry.TYPE_INT:
                case FeatureStateStore.NativeEntry.TYPE_COLOR:
                    Changes(context, e.featNum, e.featName, e.intVal, 0L, false, null);
                    break;
                case FeatureStateStore.NativeEntry.TYPE_LONG:
                    Changes(context, e.featNum, e.featName, 0, e.longVal, false, null);
                    break;
                case FeatureStateStore.NativeEntry.TYPE_STRING:
                    Changes(context, e.featNum, e.featName, 0, 0L, false, e.stringVal);
                    break;
            }
        }
    }

    public static void beginBatch() {
        FeatureStateStore.beginBatch(context);
    }

    public static void endBatch(boolean syncCommit) {
        FeatureStateStore.endBatch(context, syncCommit);
    }

    // ================================================================
    // Unified state changes (Memory -> Native Changes -> Disk if Save ON)
    // ================================================================
    public static void triggerActionButton(String featureName, int featureNum) {
        Changes(context, featureNum, featureName, 0, 0L, false, null);
    }

    public static void triggerResetAllButton(String featureName, int featureNum) {
        FeatureStateStore.updateBool(context, featureName, featureNum, false, true);
        Changes(context, featureNum, featureName, 0, 0L, true, null);
    }

    public static void changeFeatureInt(String featureName, int featureNum, int value) {
        boolean changed = FeatureStateStore.updateInt(context, featureName, featureNum, value);
        if (changed) {
            Changes(context, featureNum, featureName, value, 0L, false, null);
        }
    }

    public static void forceSyncInt(String featureName, int featureNum, int value, boolean callNative) {
        FeatureStateStore.updateInt(context, featureName, featureNum, value, true);
        if (callNative) {
            Changes(context, featureNum, featureName, value, 0L, false, null);
        }
    }

    public static void changeFeatureColor(String featureName, int featureNum, int color) {
        boolean changed = FeatureStateStore.updateColor(context, featureName, featureNum, color);
        if (changed) {
            Changes(context, featureNum, featureName, color, 0L, false, null);
        }
    }

    public static void forceSyncColor(String featureName, int featureNum, int color, boolean callNative) {
        FeatureStateStore.updateColor(context, featureName, featureNum, color, true);
        if (callNative) {
            Changes(context, featureNum, featureName, color, 0L, false, null);
        }
    }

    public static void changeFeatureLong(String featureName, int featureNum, long Lvalue) {
        boolean changed = FeatureStateStore.updateLong(context, featureName, featureNum, Lvalue);
        if (changed) {
            Changes(context, featureNum, featureName, 0, Lvalue, false, null);
        }
    }

    public static void forceSyncLong(String featureName, int featureNum, long Lvalue, boolean callNative) {
        FeatureStateStore.updateLong(context, featureName, featureNum, Lvalue, true);
        if (callNative) {
            Changes(context, featureNum, featureName, 0, Lvalue, false, null);
        }
    }

    public static void changeFeatureString(String featureName, int featureNum, String inputString) {
        boolean changed = FeatureStateStore.updateString(context, featureName, featureNum, inputString);
        if (changed) {
            Changes(context, featureNum, featureName, 0, 0L, false, inputString);
        }
    }

    public static void forceSyncString(String featureName, int featureNum, String inputString, boolean callNative) {
        FeatureStateStore.updateString(context, featureName, featureNum, inputString, true);
        if (callNative) {
            Changes(context, featureNum, featureName, 0, 0L, false, inputString);
        }
    }

    public static void changeFeatureBool(String featureName, int featureNum, boolean bool) {
        if (featureNum == -1) {
            FeatureStateStore.setSaveEnabled(context, bool);
            loadPref = bool;
            if (bool) {
                sessionLoadPref = true;
            }
            Changes(context, featureNum, featureName, 0, 0L, bool, null);
            return;
        }
        if (featureNum == -3) {
            boolean changed = FeatureStateStore.updateBool(context, featureName, featureNum, bool);
            isExpanded = bool;
            if (changed) {
                Changes(context, featureNum, featureName, 0, 0L, bool, null);
            }
            return;
        }
        boolean changed = FeatureStateStore.updateBool(context, featureName, featureNum, bool);
        if (changed) {
            Changes(context, featureNum, featureName, 0, 0L, bool, null);
        }
    }

    public static void forceSyncBool(String featureName, int featureNum, boolean bool, boolean callNative) {
        if (featureNum == -1) {
            return;
        }
        FeatureStateStore.updateBool(context, featureName, featureNum, bool, true);
        if (featureNum == -3) {
            isExpanded = bool;
        }
        if (callNative) {
            Changes(context, featureNum, featureName, 0, 0L, bool, null);
        }
    }

    // Legacy read aliases — kept side-effect-free (no native Changes() call)
    public static int loadPrefInt(String featureName, int featureNum) {
        return readInitialInt(featureName, featureNum, 0);
    }

    public static long loadPrefLong(String featureName, int featureNum) {
        return readInitialLong(featureName, featureNum, 0L);
    }

    public static boolean loadPrefBool(String featureName, int featureNum, boolean bDef) {
        return readInitialBool(featureName, featureNum, bDef);
    }

    public static String loadPrefString(String featureName, int featureNum) {
        return readInitialString(featureName, featureNum, "");
    }

    // ================================================================
    // Constructors & Legacy SharedPreferences helpers
    // ================================================================
    private Preferences(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(
                context.getPackageName() + "_preferences",
                Context.MODE_PRIVATE
        );
    }

    private Preferences(Context context, String preferencesName) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(
                preferencesName,
                Context.MODE_PRIVATE
        );
    }

    public static Preferences with(Context context) {
        if (prefsInstance == null) {
            prefsInstance = new Preferences(context);
        }
        return prefsInstance;
    }

    public static Preferences with(Context context, boolean forceInstantiation) {
        if (forceInstantiation) {
            prefsInstance = new Preferences(context);
        }
        return prefsInstance;
    }

    public static Preferences with(Context context, String preferencesName) {
        if (prefsInstance == null) {
            prefsInstance = new Preferences(context, preferencesName);
        }
        return prefsInstance;
    }

    public static Preferences with(Context context, String preferencesName,
                                   boolean forceInstantiation) {
        if (forceInstantiation) {
            prefsInstance = new Preferences(context, preferencesName);
        }
        return prefsInstance;
    }

    public String readString(String what) {
        return sharedPreferences.getString(what, DEFAULT_STRING_VALUE);
    }

    public String readString(int what) {
        try {
            return sharedPreferences.getString(String.valueOf(what), DEFAULT_STRING_VALUE);
        } catch (java.lang.ClassCastException ex) {
            return "";
        }
    }

    public String readString(String what, String defaultString) {
        return sharedPreferences.getString(what, defaultString);
    }

    public void writeString(String where, String what) {
        sharedPreferences.edit().putString(where, what).apply();
    }

    public void writeString(int where, String what) {
        sharedPreferences.edit().putString(String.valueOf(where), what).apply();
    }

    public int readInt(String what) {
        return sharedPreferences.getInt(what, DEFAULT_INT_VALUE);
    }

    public int readInt(int what) {
        try {
            return sharedPreferences.getInt(String.valueOf(what), DEFAULT_INT_VALUE);
        } catch (java.lang.ClassCastException ex) {
            return 0;
        }
    }

    public int readInt(String what, int defaultInt) {
        return sharedPreferences.getInt(what, defaultInt);
    }

    public void writeInt(String where, int what) {
        sharedPreferences.edit().putInt(where, what).apply();
    }

    public void writeInt(int where, int what) {
        sharedPreferences.edit().putInt(String.valueOf(where), what).apply();
    }

    public double readDouble(String what) {
        if (!contains(what))
            return DEFAULT_DOUBLE_VALUE;
        return Double.longBitsToDouble(readLong(what));
    }

    public double readDouble(String what, double defaultDouble) {
        if (!contains(what))
            return defaultDouble;
        return Double.longBitsToDouble(readLong(what));
    }

    public void writeDouble(String where, double what) {
        writeLong(where, Double.doubleToRawLongBits(what));
    }

    public float readFloat(String what) {
        return sharedPreferences.getFloat(what, DEFAULT_FLOAT_VALUE);
    }

    public float readFloat(String what, float defaultFloat) {
        return sharedPreferences.getFloat(what, defaultFloat);
    }

    public void writeFloat(String where, float what) {
        sharedPreferences.edit().putFloat(where, what).apply();
    }

    public long readLong(String what) {
        return sharedPreferences.getLong(what, DEFAULT_LONG_VALUE);
    }

    public long readLong(String what, long defaultLong) {
        return sharedPreferences.getLong(what, defaultLong);
    }

    public void writeLong(String where, long what) {
        sharedPreferences.edit().putLong(where, what).apply();
    }

    public boolean readBoolean(String what) {
        return sharedPreferences.getBoolean(what, DEFAULT_BOOLEAN_VALUE);
    }

    public boolean readBoolean(int what) {
        return sharedPreferences.getBoolean(String.valueOf(what), DEFAULT_BOOLEAN_VALUE);
    }

    public boolean readBoolean(String what, boolean defaultBoolean) {
        return sharedPreferences.getBoolean(what, defaultBoolean);
    }

    public boolean readBoolean(int what, boolean defaultBoolean) {
        try {
            return sharedPreferences.getBoolean(String.valueOf(what), defaultBoolean);
        } catch (java.lang.ClassCastException ex) {
            return defaultBoolean;
        }
    }

    public void writeBoolean(String where, boolean what) {
        sharedPreferences.edit().putBoolean(where, what).apply();
    }

    public void writeBoolean(int where, boolean what) {
        sharedPreferences.edit().putBoolean(String.valueOf(where), what).apply();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void putStringSet(final String key, final Set<String> value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            sharedPreferences.edit().putStringSet(key, value).apply();
        } else {
            putOrderedStringSet(key, value);
        }
    }

    public void putOrderedStringSet(String key, Set<String> value) {
        int stringSetLength = 0;
        if (sharedPreferences.contains(key + LENGTH)) {
            stringSetLength = readInt(key + LENGTH);
        }
        writeInt(key + LENGTH, value.size());
        int i = 0;
        for (String aValue : value) {
            writeString(key + "[" + i + "]", aValue);
            i++;
        }
        for (; i < stringSetLength; i++) {
            remove(key + "[" + i + "]");
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public Set<String> getStringSet(final String key, final Set<String> defValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return sharedPreferences.getStringSet(key, defValue);
        } else {
            return getOrderedStringSet(key, defValue);
        }
    }

    public Set<String> getOrderedStringSet(String key, final Set<String> defValue) {
        if (contains(key + LENGTH)) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            int stringSetLength = readInt(key + LENGTH);
            if (stringSetLength >= 0) {
                for (int i = 0; i < stringSetLength; i++) {
                    set.add(readString(key + "[" + i + "]"));
                }
            }
            return set;
        }
        return defValue;
    }

    public void remove(final String key) {
        if (contains(key + LENGTH)) {
            int stringSetLength = readInt(key + LENGTH);
            if (stringSetLength >= 0) {
                sharedPreferences.edit().remove(key + LENGTH).apply();
                for (int i = 0; i < stringSetLength; i++) {
                    sharedPreferences.edit().remove(key + "[" + i + "]").apply();
                }
            }
        }
        sharedPreferences.edit().remove(key).apply();
    }

    public boolean contains(final String key) {
        return sharedPreferences.contains(key);
    }

    public void clear() {
        sharedPreferences.edit().clear().apply();
    }
}