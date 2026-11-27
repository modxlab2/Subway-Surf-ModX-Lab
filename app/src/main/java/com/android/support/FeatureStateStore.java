package com.android.support;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 *      snapshot_user     (String: normalized username owning the saved snapshot)
 *      mxl_migrated_v2   (boolean: one-time legacy migration marker)
 *  - When saveEnabled == true:
 *      - Discrete changes commit synchronously; continuous SeekBar drags apply
 *        while dragging and commit on touch stop / batch end.
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
    private static final String KEY_SNAPSHOT_USER       = "snapshot_user";
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
    private static boolean pendingNativeRestore = false;
    private static boolean dirty = false;
    private static int batchDepth = 0;

    private static String snapshotUser = "";
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
        dirty = false;

        if (saveEnabled) {
            sessionRestoredFromDisk = true;
            pendingNativeRestore = true;
            snapshotUser = normalizeUser(prefs.getString(KEY_SNAPSHOT_USER, ""));
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
            pendingNativeRestore = false;
            snapshotUser = "";
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

    private static String normalizeUser(String user) {
        return user == null ? "" : user.trim().toLowerCase(Locale.US);
    }

    /**
     * Binds the current authenticated session user to the FeatureStateStore snapshot.
     * If a DIFFERENT user logs in than the one who owns the current saved/session
     * state, wipes the old state cleanly so features never leak across accounts.
     *
     * @return true if the menu accent color was reset and UI theme should refresh.
     */
    public static synchronized boolean bindSessionUser(Context ctx, String rawUser) {
        ensureInit(ctx);
        String newUser = normalizeUser(rawUser);
        if (newUser.isEmpty()) return false;

        boolean differentUser = !snapshotUser.isEmpty() && !snapshotUser.equals(newUser);
        if (differentUser) {
            int oldAccent = menuAccentColor;
            bools.clear();
            ints.clear();
            longs.clear();
            strings.clear();
            colors.clear();
            saveEnabled = false;
            sessionRestoredFromDisk = false;
            pendingNativeRestore = false;
            dirty = false;
            menuAccentColor = DEFAULT_MENU_COLOR;
            recentMenuColors.clear();
            snapshotUser = newUser;
            clearSnapshotKeysOnDiskLocked(true);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("FeatureStateStore: cleared snapshot on user switch");
            }
            return oldAccent != DEFAULT_MENU_COLOR;
        }

        snapshotUser = newUser;
        if (saveEnabled && prefs != null) {
            prefs.edit().putString(KEY_SNAPSHOT_USER, snapshotUser).commit();
        }
        return false;
    }

    /**
     * Called when native session clears (logout / session expiry / lockdown).
     * Native Main.cpp resets all C++ globals during clearVerifiedSession(), so if
     * the same user logs back in during the same process we must re-push state.
     */
    public static synchronized void onSessionLockedOut() {
        pendingNativeRestore = true;
    }

    public static synchronized String getSnapshotUser() {
        return snapshotUser;
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
        if (!snapshotUser.isEmpty()) {
            ed.putString(KEY_SNAPSHOT_USER, snapshotUser);
        }
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

    /**
     * Removes any stale feature keys from disk while preserving Save flag, user binding,
     * Expand state (-3), and menu accent color theme. Used during Reset All Features.
     */
    public static synchronized void resetAllFeaturesInStore(Context ctx) {
        ensureInit(ctx);
        Boolean expandVal = bools.get(-3);
        bools.clear();
        ints.clear();
        longs.clear();
        strings.clear();
        colors.clear();

        if (expandVal != null) {
            bools.put(-3, expandVal);
        }
        for (Map.Entry<Integer, Boolean> e : defaultBools.entrySet()) {
            int fn = e.getKey();
            if (fn == -1 || fn == -3) continue;
            bools.put(fn, (fn == 803) ? Boolean.FALSE : e.getValue());
        }
        for (Map.Entry<Integer, Integer> e : defaultInts.entrySet()) {
            ints.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<Integer, Long> e : defaultLongs.entrySet()) {
            longs.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<Integer, String> e : defaultStrings.entrySet()) {
            strings.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<Integer, Integer> e : defaultColors.entrySet()) {
            colors.put(e.getKey(), e.getValue());
        }

        if (saveEnabled && prefs != null) {
            SharedPreferences.Editor ed = prefs.edit();
            Map<String, ?> all = prefs.getAll();
            if (all != null) {
                for (String k : all.keySet()) {
                    if (k == null) continue;
                    if (k.startsWith(PREFIX_BOOL) || k.startsWith(PREFIX_INT)
                            || k.startsWith(PREFIX_LONG) || k.startsWith(PREFIX_STRING)
                            || k.startsWith(PREFIX_COLOR)) {
                        ed.remove(k);
                    }
                }
            }
            writeFullSnapshotToEditorLocked(ed);
            boolean ok = ed.commit();
            dirty = !ok;
        }
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
            boolean ok = true;
            if (syncCommit) {
                ok = ed.commit();
            } else {
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
                prefs.edit().remove(PREFIX_INT + featNum).putInt(PREFIX_COLOR + featNum, legacyInt).commit();
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
            if (batchDepth == 0) {
                boolean ok = prefs.edit().putBoolean(PREFIX_BOOL + featNum, value).commit();
                dirty = !ok;
            } else {
                dirty = true;
            }
        }
        return true;
    }

    public static synchronized boolean updateInt(Context ctx, String featName, int featNum, int value) {
        return updateInt(ctx, featName, featNum, value, false, true);
    }

    public static synchronized boolean updateInt(Context ctx, String featName, int featNum, int value, boolean force) {
        return updateInt(ctx, featName, featNum, value, force, true);
    }

    public static synchronized boolean updateIntDeferred(Context ctx, String featName, int featNum, int value) {
        return updateInt(ctx, featName, featNum, value, false, false);
    }

    private static boolean updateInt(Context ctx, String featName, int featNum, int value,
                                     boolean force, boolean syncCommit) {
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
            if (batchDepth == 0) {
                SharedPreferences.Editor ed = prefs.edit().putInt(PREFIX_INT + featNum, value);
                if (syncCommit) {
                    boolean ok = ed.commit();
                    dirty = !ok;
                } else {
                    ed.apply();
                    dirty = true;
                }
            } else {
                dirty = true;
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
            if (batchDepth == 0) {
                boolean ok = prefs.edit().putLong(PREFIX_LONG + featNum, value).commit();
                dirty = !ok;
            } else {
                dirty = true;
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
            if (batchDepth == 0) {
                boolean ok = prefs.edit().putString(PREFIX_STRING + featNum, safeVal).commit();
                dirty = !ok;
            } else {
                dirty = true;
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
            if (batchDepth == 0) {
                boolean ok = prefs.edit().putInt(PREFIX_COLOR + featNum, color).commit();
                dirty = !ok;
            } else {
                dirty = true;
            }
        }
        return true;
    }

    // =========================================================================
    // Deterministic snapshot collection for post-login / relogin native restore
    // Order: 1) Active Mode button (800-802 where true — at most 1)
    //        2) Manual overrides to that mode + modified non-mode features
    //        3) Modified ColorPickers
    // Avoids duplicating features already applied identically by Phase 1 Mode.
    // =========================================================================

    public static synchronized List<NativeEntry> consumePendingNativeRestore() {
        if (!pendingNativeRestore) {
            return null;
        }
        pendingNativeRestore = false;

        List<NativeEntry> list = new ArrayList<NativeEntry>();

        // Phase 1: Mode buttons 800-802 (enforce mutual exclusion before sending)
        normalizeActiveModeLocked();
        int activeModeFn = -1;
        int modeCount = 0;
        int[] modes = new int[]{800, 801, 802};
        for (int fn : modes) {
            Boolean v = bools.get(fn);
            if (v != null && v) {
                activeModeFn = fn;
                list.add(new NativeEntry(NativeEntry.TYPE_BOOL, fn, featNames.get(fn),
                        true, 0, 0L, null));
                modeCount++;
            }
        }
        boolean hasActiveMode = (modeCount > 0);

        // Phase 2: Manual overrides relative to activeModeFn, or non-default non-mode features
        int featCount = 0;
        for (Map.Entry<Integer, Boolean> e : bools.entrySet()) {
            int fn = e.getKey();
            if (fn == -1 || fn == -3 || fn == 800 || fn == 801 || fn == 802 || fn == 803) {
                continue;
            }
            boolean val = e.getValue();
            if (hasActiveMode) {
                Boolean presetBool = getModePresetBoolOnValue(activeModeFn, fn);
                if (presetBool != null) {
                    if (val != presetBool.booleanValue()) {
                        list.add(new NativeEntry(NativeEntry.TYPE_BOOL, fn, featNames.get(fn),
                                val, 0, 0L, null));
                        featCount++;
                    }
                    continue;
                }
            }
            Boolean def = defaultBools.get(fn);
            boolean isDefault = (def != null) ? (def == val) : (!val);
            if (!isDefault) {
                list.add(new NativeEntry(NativeEntry.TYPE_BOOL, fn, featNames.get(fn),
                        val, 0, 0L, null));
                featCount++;
            }
        }
        for (Map.Entry<Integer, Integer> e : ints.entrySet()) {
            int fn = e.getKey();
            if (fn < 0) continue;
            int val = e.getValue();
            if (hasActiveMode) {
                Integer presetInt = getModePresetIntOnValue(activeModeFn, fn);
                if (presetInt != null) {
                    if (val != presetInt.intValue()) {
                        list.add(new NativeEntry(NativeEntry.TYPE_INT, fn, featNames.get(fn),
                                false, val, 0L, null));
                        featCount++;
                    }
                    continue;
                }
            }
            Integer def = defaultInts.get(fn);
            boolean isDefault = (def != null) ? (def == val) : (val == 0);
            if (!isDefault) {
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
            if (!isDefault) {
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
            if (!isDefault) {
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
            if (!isDefault) {
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

    /** Matches native Main.cpp cases 800/801/802 boolean effects exactly. */
    private static Boolean getModePresetBoolOnValue(int modeFn, int featNum) {
        if (modeFn != 800 && modeFn != 801 && modeFn != 802) return null;
        if (featNum == 104 || featNum == 110 || featNum == 150) return Boolean.TRUE;
        if (modeFn == 801 || modeFn == 802) {
            if (featNum == 108 || featNum == 106 || featNum == 140) return Boolean.TRUE;
        }
        if (modeFn == 802) {
            if (featNum == 130 || featNum == 131 || featNum == 132
                    || featNum == 142 || featNum == 119) return Boolean.TRUE;
        }
        return null;
    }

    /** Matches native Main.cpp cases 800/801/802 integer effects exactly. */
    private static Integer getModePresetIntOnValue(int modeFn, int featNum) {
        if (modeFn != 800 && modeFn != 801 && modeFn != 802) return null;
        if (featNum == 103) return 5;
        if (featNum == 151) return 9999;
        if (modeFn == 801 || modeFn == 802) {
            if (featNum == 102) return 999;
            if (featNum == 145) return 999;
        }
        if (modeFn == 802) {
            if (featNum == 161) return 1;
            if (featNum == 152) return 9999;
            if (featNum == 153) return 9999;
        }
        return null;
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
            ed.commit();
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

    // =========================================================================
    // Public state accessors + SEC_DIAG Snapshot Backup/Restore Helpers
    // =========================================================================

    public static synchronized boolean getBool(int featNum, boolean fallback) {
        Boolean v = bools.get(featNum);
        return v != null ? v : fallback;
    }

    public static synchronized int getInt(int featNum, int fallback) {
        Integer v = ints.get(featNum);
        return v != null ? v : fallback;
    }

    public static synchronized long getLong(int featNum, long fallback) {
        Long v = longs.get(featNum);
        return v != null ? v : fallback;
    }

    public static synchronized String getString(int featNum, String fallback) {
        String v = strings.get(featNum);
        return v != null ? v : (fallback != null ? fallback : "");
    }

    public static synchronized int getColor(int featNum, int fallback) {
        Integer v = colors.get(featNum);
        return v != null ? v : fallback;
    }

    static synchronized boolean hasDiskBoolKey(int featNum) {
        return prefs != null && prefs.contains(PREFIX_BOOL + featNum);
    }

    static synchronized boolean hasDiskIntKey(int featNum) {
        return prefs != null && prefs.contains(PREFIX_INT + featNum);
    }

    static synchronized int countDiskFeatureKeys() {
        if (prefs == null) return 0;
        Map<String, ?> all = prefs.getAll();
        if (all == null) return 0;
        int count = 0;
        for (String k : all.keySet()) {
            if (k == null) continue;
            if (k.startsWith(PREFIX_BOOL) || k.startsWith(PREFIX_INT)
                    || k.startsWith(PREFIX_LONG) || k.startsWith(PREFIX_STRING)
                    || k.startsWith(PREFIX_COLOR) || k.equals(KEY_MENU_ACCENT)) {
                count++;
            }
        }
        return count;
    }

    static final class DiagSnapshot {
        final boolean saveEnabled;
        final boolean sessionRestoredFromDisk;
        final boolean pendingNativeRestore;
        final String snapshotUser;
        final int menuAccentColor;
        final ArrayList<Integer> recentColors = new ArrayList<Integer>();
        final LinkedHashMap<Integer, Boolean> bools = new LinkedHashMap<Integer, Boolean>();
        final LinkedHashMap<Integer, Integer> ints = new LinkedHashMap<Integer, Integer>();
        final LinkedHashMap<Integer, Long> longs = new LinkedHashMap<Integer, Long>();
        final LinkedHashMap<Integer, String> strings = new LinkedHashMap<Integer, String>();
        final LinkedHashMap<Integer, Integer> colors = new LinkedHashMap<Integer, Integer>();
        final Map<String, Object> rawDisk = new HashMap<String, Object>();

        DiagSnapshot(boolean saveEnabled, boolean sessionRestoredFromDisk,
                     boolean pendingNativeRestore, String snapshotUser, int menuAccentColor) {
            this.saveEnabled = saveEnabled;
            this.sessionRestoredFromDisk = sessionRestoredFromDisk;
            this.pendingNativeRestore = pendingNativeRestore;
            this.snapshotUser = snapshotUser;
            this.menuAccentColor = menuAccentColor;
        }
    }

    static synchronized DiagSnapshot captureDiagSnapshot(Context ctx) {
        ensureInit(ctx);
        DiagSnapshot snap = new DiagSnapshot(
                saveEnabled, sessionRestoredFromDisk, pendingNativeRestore,
                snapshotUser, menuAccentColor);
        snap.recentColors.addAll(recentMenuColors);
        snap.bools.putAll(bools);
        snap.ints.putAll(ints);
        snap.longs.putAll(longs);
        snap.strings.putAll(strings);
        snap.colors.putAll(colors);
        if (prefs != null) {
            Map<String, ?> all = prefs.getAll();
            if (all != null) {
                snap.rawDisk.putAll(all);
            }
        }
        return snap;
    }

    static synchronized void restoreDiagSnapshot(Context ctx, DiagSnapshot snap) {
        if (snap == null) return;
        ensureInit(ctx);
        saveEnabled = snap.saveEnabled;
        sessionRestoredFromDisk = snap.sessionRestoredFromDisk;
        pendingNativeRestore = snap.pendingNativeRestore;
        snapshotUser = snap.snapshotUser != null ? snap.snapshotUser : "";
        menuAccentColor = snap.menuAccentColor;
        recentMenuColors.clear();
        recentMenuColors.addAll(snap.recentColors);

        bools.clear();
        bools.putAll(snap.bools);
        ints.clear();
        ints.putAll(snap.ints);
        longs.clear();
        longs.putAll(snap.longs);
        strings.clear();
        strings.putAll(snap.strings);
        colors.clear();
        colors.putAll(snap.colors);
        batchDepth = 0;
        dirty = false;

        if (prefs != null) {
            SharedPreferences.Editor ed = prefs.edit();
            ed.clear();
            for (Map.Entry<String, Object> e : snap.rawDisk.entrySet()) {
                String k = e.getKey();
                Object v = e.getValue();
                if (k == null || v == null) continue;
                if (v instanceof Boolean) {
                    ed.putBoolean(k, (Boolean) v);
                } else if (v instanceof Integer) {
                    ed.putInt(k, (Integer) v);
                } else if (v instanceof Long) {
                    ed.putLong(k, (Long) v);
                } else if (v instanceof String) {
                    ed.putString(k, (String) v);
                } else if (v instanceof Float) {
                    ed.putFloat(k, (Float) v);
                }
            }
            ed.commit();
        }
    }
}
