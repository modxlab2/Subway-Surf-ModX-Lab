package com.android.support;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FeatureStateStore — single in-memory source of truth for all mod features,
 * Mode buttons (800-802), ColorPickers, and the Menu Accent Color.
 *
 * Persistence model:
 *  - Typed SharedPreferences keys:
 *      b_<featNum>       (boolean: Toggle, CheckBox, ButtonOnOff)
 *      i_<featNum>       (int: SeekBar, Spinner, RadioButton, InputNum)
 *      l_<featNum>       (long: InputLNum)
 *      s_<featNum>       (String: InputText)
 *      c_<featNum>       (int ARGB: ColorPicker)
 *      menu_accent_color (int ARGB: Menu accent color)
 *      save_enabled      (boolean: Save features preference, featNum -1)
 *      mxl_migrated_v2   (boolean: one-time legacy migration marker)
 *  - When saveEnabled == true:
 *      - All feature & menu color changes write through immediately.
 *      - Turning Save ON mid-session writes a full synchronous commit() snapshot
 *        of the entire current in-memory state (including earlier changes).
 *  - When saveEnabled == false:
 *      - Nothing is written to disk.
 *      - Turning Save OFF immediately wipes the saved snapshot via commit()
 *        while preserving the current in-memory session until app exit.
 */
public final class FeatureStateStore {

    public static final int DEFAULT_MENU_COLOR = ModxTheme.ACCENT;

    private static final String KEY_SAVE_ENABLED        = "save_enabled";
    private static final String KEY_MENU_ACCENT         = "menu_accent_color";
    private static final String KEY_RECENT_MENU_COLORS  = "recent_menu_colors";
    private static final String KEY_MIGRATED_V2         = "mxl_migrated_v2";
    private static final String LEGACY_THEME_PREF       = "menu_theme";
    private static final String LEGACY_THEME_KEY        = "accent";
    private static final int    MAX_RECENT_MENU_COLORS  = 5;

    private static final String PREFIX_BOOL   = "b_";
    private static final String PREFIX_INT    = "i_";
    private static final String PREFIX_LONG   = "l_";
    private static final String PREFIX_STRING = "s_";
    private static final String PREFIX_COLOR  = "c_";

    private static Context appContext;
    private static SharedPreferences prefs;

    private static boolean initialized = false;
    private static boolean saveEnabled = false;
    private static boolean sessionRestoredFromDisk = false;
    private static boolean nativeRestoredOnce = false;
    private static boolean dirty = false;
    private static int batchDepth = 0;

    private static int menuAccentColor = DEFAULT_MENU_COLOR;
    private static final ArrayList<Integer> recentMenuColors = new ArrayList<Integer>();

    private static final LinkedHashMap<Integer, Boolean> bools   = new LinkedHashMap<Integer, Boolean>();
    private static final LinkedHashMap<Integer, Integer> ints    = new LinkedHashMap<Integer, Integer>();
    private static final LinkedHashMap<Integer, Long>    longs   = new LinkedHashMap<Integer, Long>();
    private static final LinkedHashMap<Integer, String>  strings = new LinkedHashMap<Integer, String>();
    private static final LinkedHashMap<Integer, Integer> colors  = new LinkedHashMap<Integer, Integer>();

    private static final HashMap<Integer, Boolean> defaultBools   = new HashMap<Integer, Boolean>();
    private static final HashMap<Integer, Integer> defaultInts    = new HashMap<Integer, Integer>();
    private static final HashMap<Integer, Long>    defaultLongs   = new HashMap<Integer, Long>();
    private static final HashMap<Integer, String>  defaultStrings = new HashMap<Integer, String>();
    private static final HashMap<Integer, Integer> defaultColors  = new HashMap<Integer, Integer>();

    private static final HashMap<Integer, String> featNames = new HashMap<Integer, String>();

    private FeatureStateStore() { }

    public static final class NativeEntry {
        public static final int TYPE_BOOL   = 1;
        public static final int TYPE_INT    = 2;
        public static final int TYPE_LONG   = 3;
        public static final int TYPE_STRING = 4;
        public static final int TYPE_COLOR  = 5;

        public final int type;
        public final int featNum;
        public final String featName;
        public final boolean boolVal;
        public final int intVal;
        public final long longVal;
        public final String stringVal;

        NativeEntry(int type, int featNum, String featName,
                    boolean boolVal, int intVal, long longVal, String stringVal) {
            this.type = type;
            this.featNum = featNum;
            this.featName = featName != null ? featName : "";
            this.boolVal = boolVal;
            this.intVal = intVal;
            this.longVal = longVal;
            this.stringVal = stringVal;
        }
    }

    public static synchronized void init(Context ctx) {
        if (ctx == null) return;
        Context ac = ctx.getApplicationContext();
        appContext = (ac != null) ? ac : ctx;
        prefs = appContext.getSharedPreferences(
                appContext.getPackageName() + "_preferences",
                Context.MODE_PRIVATE);

        boolean didMigrate = migrateLegacyIfNeeded();

        bools.clear();
        ints.clear();
        longs.clear();
        strings.clear();
        colors.clear();
        defaultBools.clear();
        defaultInts.clear();
        defaultLongs.clear();
        defaultStrings.clear();
        defaultColors.clear();
        featNames.clear();

        saveEnabled = prefs.getBoolean(KEY_SAVE_ENABLED, false);
        nativeRestoredOnce = false;
        dirty = false;

        if (saveEnabled) {
            sessionRestoredFromDisk = true;
            Map<String, ?> all = prefs.getAll();
            if (all != null) {
                for (Map.Entry<String, ?> e : all.entrySet()) {
                    String k = e.getKey();
                    Object v = e.getValue();
                    if (k == null || v == null) continue;
                    if (k.startsWith(PREFIX_BOOL) && v instanceof Boolean) {
                        Integer fn = parseSuffixInt(k, PREFIX_BOOL.length());
                        if (fn != null && fn != -1 && fn != 803) {
                            bools.put(fn, (Boolean) v);
                        }
                    } else if (k.startsWith(PREFIX_INT) && v instanceof Integer) {
                        Integer fn = parseSuffixInt(k, PREFIX_INT.length());
                        if (fn != null) {
                            ints.put(fn, (Integer) v);
                        }
                    } else if (k.startsWith(PREFIX_LONG) && v instanceof Long) {
                        Integer fn = parseSuffixInt(k, PREFIX_LONG.length());
                        if (fn != null) {
                            longs.put(fn, (Long) v);
                        }
                    } else if (k.startsWith(PREFIX_STRING) && v instanceof String) {
                        Integer fn = parseSuffixInt(k, PREFIX_STRING.length());
                        if (fn != null) {
                            strings.put(fn, (String) v);
                        }
                    } else if (k.startsWith(PREFIX_COLOR) && v instanceof Integer) {
                        Integer fn = parseSuffixInt(k, PREFIX_COLOR.length());
                        if (fn != null) {
                            colors.put(fn, (Integer) v);
                        }
                    }
                }
            }
            int savedAccent = prefs.getInt(KEY_MENU_ACCENT, DEFAULT_MENU_COLOR);
            menuAccentColor = (savedAccent != 0) ? (0xFF000000 | savedAccent) : DEFAULT_MENU_COLOR;
            loadRecentMenuColorsLocked(prefs.getString(KEY_RECENT_MENU_COLORS, ""));
            normalizeActiveModeLocked();
        } else {
            sessionRestoredFromDisk = false;
            menuAccentColor = DEFAULT_MENU_COLOR;
            recentMenuColors.clear();
            clearSnapshotKeysOnDiskLocked(false);
        }

        batchDepth = 0;
        initialized = true;
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("FeatureStateStore.init: save=" + saveEnabled
                    + " migrated=" + didMigrate
                    + " bools=" + bools.size()
                    + " ints=" + ints.size()
                    + " longs=" + longs.size()
                    + " strings=" + strings.size()
                    + " colors=" + colors.size()
                    + " menuColor=" + hexColor(menuAccentColor));
        }
    }

    private static boolean migrateLegacyIfNeeded() {
        if (prefs == null) return false;
        boolean already = prefs.getBoolean(KEY_MIGRATED_V2, false);
        SharedPreferences legacyThemePrefs = null;
        try {
            legacyThemePrefs = appContext.getSharedPreferences(LEGACY_THEME_PREF, Context.MODE_PRIVATE);
        } catch (Throwable ignored) { }

        if (already) {
            clearLegacyThemePrefs(legacyThemePrefs);
            return false;
        }

        Map<String, ?> all = prefs.getAll();
        boolean legacySave = prefs.getBoolean(KEY_SAVE_ENABLED, false);
        if (all != null && all.get("-1") instanceof Boolean) {
            legacySave = (Boolean) all.get("-1");
        }

        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(KEY_SAVE_ENABLED, legacySave);
        ed.putBoolean(KEY_MIGRATED_V2, true);

        if (all != null) {
            for (Map.Entry<String, ?> e : all.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                if (k == null || !isPlainNumericKey(k)) continue;
                int fn;
                try {
                    fn = Integer.parseInt(k);
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (fn == -1) {
                    ed.remove(k);
                    continue;
                }
                if (legacySave && fn != 803 && v != null) {
                    if (v instanceof Boolean) {
                        ed.putBoolean(PREFIX_BOOL + fn, (Boolean) v);
                    } else if (v instanceof Integer) {
                        ed.putInt(PREFIX_INT + fn, (Integer) v);
                    } else if (v instanceof Long) {
                        ed.putLong(PREFIX_LONG + fn, (Long) v);
                    } else if (v instanceof String) {
                        ed.putString(PREFIX_STRING + fn, (String) v);
                    }
                }
                ed.remove(k);
            }
        }

        if (legacySave && legacyThemePrefs != null) {
            int oldAccent = legacyThemePrefs.getInt(LEGACY_THEME_KEY, 0);
            if (oldAccent != 0 && !prefs.contains(KEY_MENU_ACCENT)) {
                ed.putInt(KEY_MENU_ACCENT, oldAccent);
            }
        }

        ed.commit();
        clearLegacyThemePrefs(legacyThemePrefs);
        return true;
    }

    private static void clearLegacyThemePrefs(SharedPreferences legacyThemePrefs) {
        if (legacyThemePrefs == null) return;
        try {
            Map<String, ?> themeAll = legacyThemePrefs.getAll();
            if (themeAll != null && !themeAll.isEmpty()) {
                legacyThemePrefs.edit().clear().commit();
            }
        } catch (Throwable ignored) { }
    }

    private static void ensureInit(Context ctx) {
        if (!initialized && ctx != null) {
            init(ctx);
        }
    }

    public static synchronized boolean isSaveEnabled() {
        return saveEnabled;
    }

    public static synchronized boolean setSaveEnabled(Context ctx, boolean enabled) {
        ensureInit(ctx);
        boolean changed = (saveEnabled != enabled);
        saveEnabled = enabled;
        if (enabled) {
            if (prefs != null) {
                SharedPreferences.Editor ed = prefs.edit();
                writeFullSnapshotToEditorLocked(ed);
                boolean ok = ed.commit();
                dirty = !ok;
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("Save=ON snapshot committed: ok=" + ok
                            + " bools=" + bools.size()
                            + " ints=" + ints.size()
                            + " longs=" + longs.size()
                            + " strings=" + strings.size()
                            + " colors=" + colors.size()
                            + " menuColor=" + hexColor(menuAccentColor));
                }
            }
        } else {
            clearSnapshotKeysOnDiskLocked(true);
            dirty = false;
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("Save=OFF snapshot cleared & committed");
            }
        }
        return changed;
    }

    private static void writeFullSnapshotToEditorLocked(SharedPreferences.Editor ed) {
        ed.putBoolean(KEY_SAVE_ENABLED, true);
        ed.putBoolean(KEY_MIGRATED_V2, true);
        ed.putInt(KEY_MENU_ACCENT, menuAccentColor);
        ed.putString(KEY_RECENT_MENU_COLORS, serializeRecentMenuColorsLocked());

        for (Map.Entry<Integer, Boolean> e : bools.entrySet()) {
            int fn = e.getKey();
            if (fn == -1 || fn == 803) continue;
            ed.putBoolean(PREFIX_BOOL + fn, e.getValue());
        }
        for (Map.Entry<Integer, Integer> e : ints.entrySet()) {
            ed.putInt(PREFIX_INT + e.getKey(), e.getValue());
        }
        for (Map.Entry<Integer, Long> e : longs.entrySet()) {
            ed.putLong(PREFIX_LONG + e.getKey(), e.getValue());
        }
        for (Map.Entry<Integer, String> e : strings.entrySet()) {
            ed.putString(PREFIX_STRING + e.getKey(), e.getValue() != null ? e.getValue() : "");
        }
        for (Map.Entry<Integer, Integer> e : colors.entrySet()) {
            ed.putInt(PREFIX_COLOR + e.getKey(), e.getValue());
        }
    }

    private static void clearSnapshotKeysOnDiskLocked(boolean keepSaveOffFlag) {
        if (prefs == null) return;
        SharedPreferences.Editor ed = prefs.edit();
        Map<String, ?> all = prefs.getAll();
        if (all != null) {
            for (String k : all.keySet()) {
                if (k == null) continue;
                if (k.equals(KEY_MIGRATED_V2)) continue;
                if (k.equals(KEY_SAVE_ENABLED)) continue;
                ed.remove(k);
            }
        } else {
            ed.remove(KEY_MENU_ACCENT);
        }
        if (keepSaveOffFlag || prefs.getBoolean(KEY_SAVE_ENABLED, false)) {
            ed.putBoolean(KEY_SAVE_ENABLED, false);
        }
        ed.putBoolean(KEY_MIGRATED_V2, true);
        ed.commit();
        try {
            if (appContext != null) {
                SharedPreferences legacy = appContext.getSharedPreferences(LEGACY_THEME_PREF, Context.MODE_PRIVATE);
                clearLegacyThemePrefs(legacy);
            }
        } catch (Throwable ignored) { }
    }

    public static synchronized void beginBatch(Context ctx) {
        ensureInit(ctx);
        batchDepth++;
    }

    public static synchronized void endBatch(Context ctx, boolean syncCommit) {
        ensureInit(ctx);
        if (batchDepth > 0) {
            batchDepth--;
        }
        if (batchDepth == 0 && saveEnabled && dirty && prefs != null) {
            SharedPreferences.Editor ed = prefs.edit();
            writeFullSnapshotToEditorLocked(ed);
            boolean ok = syncCommit ? ed.commit() : true;
            if (!syncCommit) {
                ed.apply();
            }
            dirty = syncCommit && !ok;
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("endBatch: syncCommit=" + syncCommit + " ok=" + ok);
            }
        }
    }

    public static synchronized void flush(Context ctx, String source) {
        ensureInit(ctx);
        if (prefs == null) return;
        boolean ok;
        if (saveEnabled) {
            SharedPreferences.Editor ed = prefs.edit();
            writeFullSnapshotToEditorLocked(ed);
            ok = ed.commit();
        } else {
            ok = prefs.edit()
                    .putBoolean(KEY_SAVE_ENABLED, false)
                    .putBoolean(KEY_MIGRATED_V2, true)
                    .commit();
        }
        dirty = !ok;
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("flush(" + source + "): save=" + saveEnabled + " ok=" + ok);
        }
    }

    // =========================================================================
    // Menu accent color (part of the same snapshot)
    // =========================================================================

    public static synchronized int getMenuAccentColor() {
        return menuAccentColor;
    }

    public static synchronized void setMenuAccentColor(Context ctx, int color) {
        ensureInit(ctx);
        int resolved = (color != 0) ? (0xFF000000 | color) : DEFAULT_MENU_COLOR;
        menuAccentColor = resolved;
        if (saveEnabled && prefs != null) {
            boolean ok = prefs.edit().putInt(KEY_MENU_ACCENT, resolved).commit();
            dirty = !ok;
        }
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("setMenuAccentColor: color=" + hexColor(resolved)
                    + " save=" + saveEnabled);
        }
    }

    public static synchronized int[] getRecentMenuColors() {
        int[] out = new int[recentMenuColors.size()];
        for (int i = 0; i < recentMenuColors.size(); i++) {
            out[i] = recentMenuColors.get(i);
        }
        return out;
    }

    public static synchronized void recordMenuColorUsed(Context ctx, int color) {
        ensureInit(ctx);
        int opaque = (color != 0) ? (0xFF000000 | color) : DEFAULT_MENU_COLOR;
        for (int i = recentMenuColors.size() - 1; i >= 0; i--) {
            if (recentMenuColors.get(i) == opaque) {
                recentMenuColors.remove(i);
            }
        }
        recentMenuColors.add(0, opaque);
        while (recentMenuColors.size() > MAX_RECENT_MENU_COLORS) {
            recentMenuColors.remove(recentMenuColors.size() - 1);
        }
        if (saveEnabled && prefs != null) {
            boolean ok = prefs.edit()
                    .putString(KEY_RECENT_MENU_COLORS, serializeRecentMenuColorsLocked())
                    .commit();
            if (!ok) dirty = true;
        }
    }

    private static void loadRecentMenuColorsLocked(String raw) {
        recentMenuColors.clear();
        if (raw == null || raw.isEmpty()) return;
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length && recentMenuColors.size() < MAX_RECENT_MENU_COLORS; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;
            try {
                int c = Color.parseColor(p.startsWith("#") ? p : ("#" + p));
                int opaque = 0xFF000000 | c;
                if (!recentMenuColors.contains(opaque)) {
                    recentMenuColors.add(opaque);
                }
            } catch (Exception ignored) { }
        }
    }

    private static String serializeRecentMenuColorsLocked() {
        if (recentMenuColors.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recentMenuColors.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("#%06X", (0xFFFFFF & recentMenuColors.get(i))));
        }
        return sb.toString();
    }

    // =========================================================================
    // Side-effect-free UI reads & default registration
    // =========================================================================

    public static synchronized boolean readInitialBool(Context ctx, String featName, int featNum, boolean defaultVal) {
        ensureInit(ctx);
        if (featNum == -1) return saveEnabled;
        recordName(featNum, featName);
        if (!defaultBools.containsKey(featNum)) {
            defaultBools.put(featNum, defaultVal);
        }
        if (featNum == 803) {
            bools.put(featNum, false);
            return false;
        }
        Boolean existing = bools.get(featNum);
        if (existing != null) return existing;
        bools.put(featNum, defaultVal);
        return defaultVal;
    }

    public static synchronized int readInitialInt(Context ctx, String featName, int featNum, int defaultVal) {
        ensureInit(ctx);
        recordName(featNum, featName);
        if (!defaultInts.containsKey(featNum)) {
            defaultInts.put(featNum, defaultVal);
        }
        Integer existing = ints.get(featNum);
        if (existing != null) return existing;
        ints.put(featNum, defaultVal);
        return defaultVal;
    }

    public static synchronized long readInitialLong(Context ctx, String featName, int featNum, long defaultVal) {
        ensureInit(ctx);
        recordName(featNum, featName);
        if (!defaultLongs.containsKey(featNum)) {
            defaultLongs.put(featNum, defaultVal);
        }
        Long existing = longs.get(featNum);
        if (existing != null) return existing;
        longs.put(featNum, defaultVal);
        return defaultVal;
    }

    public static synchronized String readInitialString(Context ctx, String featName, int featNum, String defaultVal) {
        ensureInit(ctx);
        recordName(featNum, featName);
        String safeDef = (defaultVal != null) ? defaultVal : "";
        if (!defaultStrings.containsKey(featNum)) {
            defaultStrings.put(featNum, safeDef);
        }
        String existing = strings.get(featNum);
        if (existing != null) return existing;
        strings.put(featNum, safeDef);
        return safeDef;
    }

    public static synchronized int readInitialColor(Context ctx, String featName, int featNum, int defaultColor) {
        ensureInit(ctx);
        recordName(featNum, featName);
        if (!defaultColors.containsKey(featNum)) {
            defaultColors.put(featNum, defaultColor);
        }
        Integer existing = colors.get(featNum);
        if (existing != null) return existing;
        // If legacy migration placed a ColorPicker integer into i_<featNum>, promote to c_<featNum>
        Integer legacyInt = ints.remove(featNum);
        if (legacyInt != null && legacyInt != 0) {
            colors.put(featNum, legacyInt);
            if (saveEnabled && prefs != null) {
                prefs.edit().remove(PREFIX_INT + featNum).putInt(PREFIX_COLOR + featNum, legacyInt).apply();
            }
            return legacyInt;
        }
        colors.put(featNum, defaultColor);
        return defaultColor;
    }

    // =========================================================================
    // Unified state updates (memory + disk write-through when Save is ON)
    // Returns true if Preferences.Changes(...) should be called.
    // =========================================================================

    public static synchronized boolean updateBool(Context ctx, String featName, int featNum, boolean value) {
        return updateBool(ctx, featName, featNum, value, false);
    }

    public static synchronized boolean updateBool(Context ctx, String featName, int featNum, boolean value, boolean force) {
        ensureInit(ctx);
        if (featNum == -1) {
            setSaveEnabled(ctx, value);
            return true;
        }
        recordName(featNum, featName);
        if (featNum == 803) {
            bools.put(featNum, false);
            return true;
        }
        Boolean prev = bools.get(featNum);
        if (!force && prev != null && prev == value) {
            return false;
        }
        bools.put(featNum, value);
        if (saveEnabled && prefs != null) {
            dirty = true;
            if (batchDepth == 0) {
                prefs.edit().putBoolean(PREFIX_BOOL + featNum, value).apply();
            }
        }
        return true;
    }

    public static synchronized boolean updateInt(Context ctx, String featName, int featNum, int value) {
        return updateInt(ctx, featName, featNum, value, false);
    }

    public static synchronized boolean updateInt(Context ctx, String featName, int featNum, int value, boolean force) {
        ensureInit(ctx);
        if (featNum < 0) {
            return true;
        }
        recordName(featNum, featName);
        Integer prev = ints.get(featNum);
        if (!force && prev != null && prev == value) {
            return false;
        }
        ints.put(featNum, value);
        if (saveEnabled && prefs != null) {
            dirty = true;
            if (batchDepth == 0) {
                prefs.edit().putInt(PREFIX_INT + featNum, value).apply();
            }
        }
        return true;
    }

    public static synchronized boolean updateLong(Context ctx, String featName, int featNum, long value) {
        return updateLong(ctx, featName, featNum, value, false);
    }

    public static synchronized boolean updateLong(Context ctx, String featName, int featNum, long value, boolean force) {
        ensureInit(ctx);
        if (featNum < 0) {
            return true;
        }
        recordName(featNum, featName);
        Long prev = longs.get(featNum);
        if (!force && prev != null && prev == value) {
            return false;
        }
        longs.put(featNum, value);
        if (saveEnabled && prefs != null) {
            dirty = true;
            if (batchDepth == 0) {
                prefs.edit().putLong(PREFIX_LONG + featNum, value).apply();
            }
        }
        return true;
    }

    public static synchronized boolean updateString(Context ctx, String featName, int featNum, String value) {
        return updateString(ctx, featName, featNum, value, false);
    }

    public static synchronized boolean updateString(Context ctx, String featName, int featNum, String value, boolean force) {
        ensureInit(ctx);
        if (featNum < 0) {
            return true;
        }
        recordName(featNum, featName);
        String safeVal = (value != null) ? value : "";
        String prev = strings.get(featNum);
        if (!force && prev != null && prev.equals(safeVal)) {
            return false;
        }
        strings.put(featNum, safeVal);
        if (saveEnabled && prefs != null) {
            dirty = true;
            if (batchDepth == 0) {
                prefs.edit().putString(PREFIX_STRING + featNum, safeVal).apply();
            }
        }
        return true;
    }

    public static synchronized boolean updateColor(Context ctx, String featName, int featNum, int color) {
        return updateColor(ctx, featName, featNum, color, false);
    }

    public static synchronized boolean updateColor(Context ctx, String featName, int featNum, int color, boolean force) {
        ensureInit(ctx);
        if (featNum < 0) {
            return true;
        }
        recordName(featNum, featName);
        Integer prev = colors.get(featNum);
        if (!force && prev != null && prev == color) {
            return false;
        }
        colors.put(featNum, color);
        if (saveEnabled && prefs != null) {
            dirty = true;
            if (batchDepth == 0) {
                prefs.edit().putInt(PREFIX_COLOR + featNum, color).apply();
            }
        }
        return true;
    }

    // =========================================================================
    // Deterministic snapshot collection for one-time post-login native restore
    // Order: 1) Active Mode buttons (800-802 where true)
    //        2) All other modified (or mode-overriding) non-color features
    //        3) All modified (or mode-overriding) ColorPickers
    // =========================================================================

    public static synchronized List<NativeEntry> consumePendingNativeRestore() {
        if (!sessionRestoredFromDisk || nativeRestoredOnce) {
            return null;
        }
        nativeRestoredOnce = true;

        List<NativeEntry> list = new ArrayList<NativeEntry>();

        // Phase 1: Mode buttons 800-802 (only active == true)
        int modeCount = 0;
        int[] modes = new int[]{800, 801, 802};
        for (int fn : modes) {
            Boolean v = bools.get(fn);
            if (v != null && v) {
                list.add(new NativeEntry(NativeEntry.TYPE_BOOL, fn, featNames.get(fn),
                        true, 0, 0L, null));
                modeCount++;
            }
        }
        boolean hasActiveMode = (modeCount > 0);

        // Phase 2: Other non-color features
        int featCount = 0;
        for (Map.Entry<Integer, Boolean> e : bools.entrySet()) {
            int fn = e.getKey();
            if (fn == -1 || fn == -3 || fn == 800 || fn == 801 || fn == 802 || fn == 803) {
                continue;
            }
            boolean val = e.getValue();
            Boolean def = defaultBools.get(fn);
            boolean isDefault = (def != null) ? (def == val) : (!val);
            if (hasActiveMode || !isDefault) {
                list.add(new NativeEntry(NativeEntry.TYPE_BOOL, fn, featNames.get(fn),
                        val, 0, 0L, null));
                featCount++;
            }
        }
        for (Map.Entry<Integer, Integer> e : ints.entrySet()) {
            int fn = e.getKey();
            if (fn < 0) continue;
            int val = e.getValue();
            Integer def = defaultInts.get(fn);
            boolean isDefault = (def != null) ? (def == val) : (val == 0);
            if (hasActiveMode || !isDefault) {
                list.add(new NativeEntry(NativeEntry.TYPE_INT, fn, featNames.get(fn),
                        false, val, 0L, null));
                featCount++;
            }
        }
        for (Map.Entry<Integer, Long> e : longs.entrySet()) {
            int fn = e.getKey();
            if (fn < 0) continue;
            long val = e.getValue();
            Long def = defaultLongs.get(fn);
            boolean isDefault = (def != null) ? (def == val) : (val == 0L);
            if (hasActiveMode || !isDefault) {
                list.add(new NativeEntry(NativeEntry.TYPE_LONG, fn, featNames.get(fn),
                        false, 0, val, null));
                featCount++;
            }
        }
        for (Map.Entry<Integer, String> e : strings.entrySet()) {
            int fn = e.getKey();
            if (fn < 0) continue;
            String val = e.getValue() != null ? e.getValue() : "";
            String def = defaultStrings.get(fn);
            boolean isDefault = (def != null) ? def.equals(val) : val.isEmpty();
            if (hasActiveMode || !isDefault) {
                list.add(new NativeEntry(NativeEntry.TYPE_STRING, fn, featNames.get(fn),
                        false, 0, 0L, val));
                featCount++;
            }
        }

        // Phase 3: Colors
        int colorCount = 0;
        for (Map.Entry<Integer, Integer> e : colors.entrySet()) {
            int fn = e.getKey();
            if (fn < 0) continue;
            int val = e.getValue();
            Integer def = defaultColors.get(fn);
            boolean isDefault = (def != null && def == val);
            if (hasActiveMode || !isDefault) {
                list.add(new NativeEntry(NativeEntry.TYPE_COLOR, fn, featNames.get(fn),
                        false, val, 0L, null));
                colorCount++;
            }
        }

        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("pushRestoredSnapshotToNative: modes=" + modeCount
                    + " features=" + featCount
                    + " colors=" + colorCount);
        }
        return list;
    }

    private static void normalizeActiveModeLocked() {
        int[] modesDesc = new int[]{802, 801, 800};
        boolean found = false;
        boolean changed = false;
        for (int fn : modesDesc) {
            Boolean v = bools.get(fn);
            if (v != null && v) {
                if (!found) {
                    found = true;
                } else {
                    bools.put(fn, false);
                    changed = true;
                }
            }
        }
        if (changed && saveEnabled && prefs != null) {
            SharedPreferences.Editor ed = prefs.edit();
            for (int fn : modesDesc) {
                Boolean v = bools.get(fn);
                if (v != null) {
                    ed.putBoolean(PREFIX_BOOL + fn, v);
                }
            }
            ed.apply();
        }
    }

    private static void recordName(int featNum, String featName) {
        if (featName != null && !featName.isEmpty()) {
            featNames.put(featNum, featName);
        }
    }

    private static boolean isPlainNumericKey(String k) {
        if (k == null || k.isEmpty()) return false;
        int start = (k.charAt(0) == '-') ? 1 : 0;
        if (start >= k.length()) return false;
        for (int i = start; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static Integer parseSuffixInt(String key, int prefixLen) {
        if (key == null || key.length() <= prefixLen) return null;
        try {
            return Integer.parseInt(key.substring(prefixLen));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String hexColor(int c) {
        return String.format("#%08X", c);
    }
}
