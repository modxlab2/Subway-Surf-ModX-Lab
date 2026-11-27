package com.android.support;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * SEC_DIAG-only built-in state/sync verification suite.
 * Completely stripped by R8 from non-SEC_DIAG release builds when guarded by BuildConfig.SEC_DIAG.
 */
final class DiagSelfTestRunner {

    static final class NativeCallRecord {
        final int featNum;
        final int intVal;
        final long longVal;
        final boolean boolVal;
        final String strVal;

        NativeCallRecord(int featNum, int intVal, long longVal, boolean boolVal, String strVal) {
            this.featNum = featNum;
            this.intVal  = intVal;
            this.longVal = longVal;
            this.boolVal = boolVal;
            this.strVal  = strVal;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean isCapturing = false;
    private static boolean isRunning = false;
    private static final ArrayList<NativeCallRecord> captured = new ArrayList<NativeCallRecord>();

    private DiagSelfTestRunner() { }

    static synchronized void recordNativeCall(int featNum, int value, long Lvalue,
                                              boolean isOn, String inputText) {
        if (!BuildConfig.SEC_DIAG || !isCapturing) return;
        captured.add(new NativeCallRecord(featNum, value, Lvalue, isOn, inputText));
    }

    private static synchronized void beginCapture() {
        captured.clear();
        isCapturing = true;
    }

    private static synchronized List<NativeCallRecord> endCapture() {
        isCapturing = false;
        ArrayList<NativeCallRecord> copy = new ArrayList<NativeCallRecord>(captured);
        captured.clear();
        return copy;
    }

    private static int countCallsFor(List<NativeCallRecord> list, int featNum) {
        int c = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).featNum == featNum) c++;
        }
        return c;
    }

    private static NativeCallRecord lastCallFor(List<NativeCallRecord> list, int featNum) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).featNum == featNum) return list.get(i);
        }
        return null;
    }

    static void runAndShow(final Context ctx, final boolean overlayRequired) {
        if (!BuildConfig.SEC_DIAG || ctx == null) return;
        DiagLogViewer.show(ctx, overlayRequired);
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                runSuite(ctx);
            }
        });
    }

    private static final class Reporter {
        int pass = 0;
        int fail = 0;
        int skip = 0;

        void step(boolean ok, String label, String detail) {
            if (ok) {
                pass++;
                emit("[SELF TEST][PASS] " + label + " -> " + detail);
            } else {
                fail++;
                emit("[SELF TEST][FAIL] " + label + " -> " + detail);
            }
        }

        void skip(String label, String detail) {
            skip++;
            emit("[SELF TEST][SKIP] " + label + " -> " + detail);
        }

        void emit(String line) {
            SecurityNative.diagUi(line);
        }
    }

    private static void runSuite(Context ctx) {
        if (isRunning) {
            DiagLogViewer.appendLiveLine("[SELF TEST] Already running...");
            return;
        }
        isRunning = true;
        Reporter r = new Reporter();
        r.emit("========================================");
        r.emit("[SELF TEST] Starting ModX Lab State/Sync Audit");

        FeatureStateStore.DiagSnapshot backup = FeatureStateStore.captureDiagSnapshot(ctx);
        boolean wasNativeSessionReady = Preferences.isNativeSessionReady();
        Menu menu = Menu.instance;
        int prevAccent = FeatureStateStore.getMenuAccentColor();
        String prevUser = (menu != null) ? menu.diagGetActiveUser() : backup.snapshotUser;

        try {
            // =================================================================
            // PART 1: FeatureStateStore Persistence & User-Isolation Tests
            // =================================================================
            runStorePersistenceTests(ctx, r);

            // =================================================================
            // PART 2: Live Menu UI <-> Store <-> Preferences <-> Changes() Tests
            // =================================================================
            if (menu != null && menu.diagIsLoggedIn() && wasNativeSessionReady) {
                runLiveMenuTests(ctx, menu, prevUser, r);
            } else {
                r.skip("Live Menu + Native Changes() Suite",
                        "Login required first (security gate keeps Changes() locked pre-login). "
                                + "Log in and tap Settings -> SELF TEST or DiagLogViewer -> Self Test.");
            }
        } catch (Throwable t) {
            r.step(false, "Unhandled Self-Test Exception", SecurityNative.diagException(t));
        } finally {
            try {
                FeatureStateStore.restoreDiagSnapshot(ctx, backup);
                Preferences.loadPref = FeatureStateStore.isSaveEnabled();
                Preferences.sessionLoadPref = Preferences.loadPref;
                Preferences.setNativeSessionReadyForDiag(wasNativeSessionReady);
                if (menu != null && menu.diagIsLoggedIn() && wasNativeSessionReady) {
                    menu.diagRestorePostTestUiAndNative(prevAccent);
                }
            } catch (Throwable ignored) { }
            endCapture();
            isRunning = false;
            r.emit("[SELF TEST] SUMMARY: PASS=" + r.pass + " FAIL=" + r.fail + " SKIP=" + r.skip);
            r.emit("========================================");
        }
    }

    private static void runStorePersistenceTests(Context ctx, Reporter r) {
        // S1: Save OFF -> early edits -> Save ON mid-session commits earlier edits -> Save OFF wipes disk
        FeatureStateStore.setSaveEnabled(ctx, false);
        FeatureStateStore.updateBool(ctx, "God Mode", 108, true);
        FeatureStateStore.updateInt(ctx, "Score Boost", 107, 45);
        FeatureStateStore.updateColor(ctx, "Diag Color", 901, 0xFF38BDF8);
        FeatureStateStore.setMenuAccentColor(ctx, 0xFF38BDF8);

        boolean memBeforeSave = FeatureStateStore.getBool(108, false)
                && FeatureStateStore.getInt(107, 0) == 45;
        boolean cleanDiskWhileOff = (FeatureStateStore.countDiskFeatureKeys() == 0);
        r.step(memBeforeSave && cleanDiskWhileOff, "S1a Save=OFF memory-only",
                "inMem=" + memBeforeSave + " diskKeys=" + FeatureStateStore.countDiskFeatureKeys());

        FeatureStateStore.setSaveEnabled(ctx, true);
        boolean committedEarly = FeatureStateStore.hasDiskBoolKey(108)
                && FeatureStateStore.hasDiskIntKey(107)
                && FeatureStateStore.getMenuAccentColor() == 0xFF38BDF8;
        r.step(committedEarly, "S1b Save=ON mid-session capture",
                "b_108=" + FeatureStateStore.hasDiskBoolKey(108)
                        + " i_107=" + FeatureStateStore.hasDiskIntKey(107));

        FeatureStateStore.setSaveEnabled(ctx, false);
        boolean wipedDisk = (FeatureStateStore.countDiskFeatureKeys() == 0)
                && FeatureStateStore.getBool(108, false);
        r.step(wipedDisk, "S1c Save=OFF wipes disk keeps memory",
                "diskKeys=" + FeatureStateStore.countDiskFeatureKeys()
                        + " mem108=" + FeatureStateStore.getBool(108, false));

        // S2: User isolation & relogin native restore
        FeatureStateStore.resetAllFeaturesInStore(ctx);
        FeatureStateStore.bindSessionUser(ctx, "diag_user_a");
        FeatureStateStore.setSaveEnabled(ctx, true);
        FeatureStateStore.updateBool(ctx, "Simple Mode", 800, true);
        FeatureStateStore.updateBool(ctx, "Auto Revive", 104, false); // manual override
        FeatureStateStore.updateInt(ctx, "Jump Height", 103, 5);      // matches 800 preset
        FeatureStateStore.updateInt(ctx, "Extra Coins", 151, 9999);   // matches 800 preset
        FeatureStateStore.updateInt(ctx, "Max Jumps", 102, 50);       // non-800 manual value

        // Simulate same-process logout + same user relogin
        FeatureStateStore.onSessionLockedOut();
        FeatureStateStore.bindSessionUser(ctx, "diag_user_a");
        List<FeatureStateStore.NativeEntry> reloginEntries = FeatureStateStore.consumePendingNativeRestore();
        boolean has800 = false, has104Override = false, has102 = false, dup103 = false, dup151 = false;
        if (reloginEntries != null) {
            for (int i = 0; i < reloginEntries.size(); i++) {
                FeatureStateStore.NativeEntry e = reloginEntries.get(i);
                if (e.featNum == 800 && e.boolVal) has800 = true;
                if (e.featNum == 104 && !e.boolVal) has104Override = true;
                if (e.featNum == 102 && e.intVal == 50) has102 = true;
                if (e.featNum == 103) dup103 = true;
                if (e.featNum == 151) dup151 = true;
            }
        }
        r.step(has800 && has104Override && has102 && !dup103 && !dup151,
                "S2a Relogin restore + mode-override diff",
                "800=" + has800 + " override104=" + has104Override
                        + " 102=" + has102 + " dup103=" + dup103 + " dup151=" + dup151);

        // Now switch to different user -> User A's snapshot must be wiped completely
        FeatureStateStore.bindSessionUser(ctx, "diag_user_b");
        boolean userBIsolated = !FeatureStateStore.getBool(800, true)
                && FeatureStateStore.getInt(102, -1) == 0
                && !FeatureStateStore.isSaveEnabled()
                && FeatureStateStore.countDiskFeatureKeys() == 0;
        r.step(userBIsolated, "S2b Cross-user isolation",
                "800=" + FeatureStateStore.getBool(800, true)
                        + " save=" + FeatureStateStore.isSaveEnabled()
                        + " diskKeys=" + FeatureStateStore.countDiskFeatureKeys());
    }

    private static void runLiveMenuTests(Context ctx, Menu menu, String activeUser, Reporter r) {
        // Re-bind real user & ensure Save=OFF clean baseline via Reset All (803)
        FeatureStateStore.resetAllFeaturesInStore(ctx);
        FeatureStateStore.bindSessionUser(ctx, activeUser);
        Preferences.loadPref = FeatureStateStore.isSaveEnabled();

        // L1: Reset All (803)
        beginCapture();
        menu.diagTriggerResetAll();
        List<NativeCallRecord> resetCalls = endCapture();
        boolean l1CallsOk = countCallsFor(resetCalls, 803) == 1
                && lastCallFor(resetCalls, 803).boolVal
                && countCallsFor(resetCalls, 121) <= 1
                && countCallsFor(resetCalls, 125) <= 1
                && countCallsFor(resetCalls, 126) <= 1
                && countCallsFor(resetCalls, 104) == 0; // covered by native 803
        boolean l1StateOk = !FeatureStateStore.getBool(803, true)
                && !menu.diagIsModeUiActive(800)
                && !menu.diagIsModeUiActive(801)
                && !menu.diagIsModeUiActive(802)
                && !FeatureStateStore.getBool(121, true)
                && FeatureStateStore.getInt(125, -1) == 0
                && FeatureStateStore.getInt(126, -1) == 0;
        r.step(l1CallsOk && l1StateOk, "L1 Reset All (803)",
                "calls803=" + countCallsFor(resetCalls, 803)
                        + " calls104=" + countCallsFor(resetCalls, 104)
                        + " 803Stored=" + FeatureStateStore.getBool(803, true));

        // L2: Mutual exclusion across ALL transitions: 800 -> 801 -> 802 -> 800 -> 802 -> 801 -> OFF
        // Step L2a: OFF -> 800
        beginCapture();
        menu.diagClickMode(800);
        List<NativeCallRecord> c800 = endCapture();
        r.step(countCallsFor(c800, 800) == 1
                        && countCallsFor(c800, 104) == 0
                        && countCallsFor(c800, 103) == 0
                        && verifyModeExclusive(menu, 800)
                        && menu.diagGetToggleUi(104)
                        && menu.diagGetSeekBarUi(103) == 5
                        && "35".equals(menu.diagGetSeekBarChipText(103)),
                "L2a OFF->800 (Simple)",
                "800calls=" + countCallsFor(c800, 800)
                        + " dup104=" + countCallsFor(c800, 104)
                        + " chip103=" + menu.diagGetSeekBarChipText(103));

        // Step L2b: 800 -> 801 (must NOT send 800=false, must send 801=true once, zero dup on shared items)
        beginCapture();
        menu.diagClickMode(801);
        List<NativeCallRecord> c801 = endCapture();
        r.step(countCallsFor(c801, 801) == 1
                        && countCallsFor(c801, 800) == 0
                        && countCallsFor(c801, 104) == 0
                        && countCallsFor(c801, 150) == 0
                        && verifyModeExclusive(menu, 801)
                        && menu.diagGetToggleUi(108)
                        && menu.diagGetSeekBarUi(102) == 999,
                "L2b 800->801 (Max)",
                "801calls=" + countCallsFor(c801, 801)
                        + " 800calls=" + countCallsFor(c801, 800)
                        + " 108Ui=" + menu.diagGetToggleUi(108)
                        + " 102Ui=" + menu.diagGetSeekBarUi(102));

        // Step L2c: 801 -> 802
        beginCapture();
        menu.diagClickMode(802);
        List<NativeCallRecord> c802 = endCapture();
        r.step(countCallsFor(c802, 802) == 1
                        && countCallsFor(c802, 801) == 0
                        && verifyModeExclusive(menu, 802)
                        && menu.diagGetToggleUi(119)
                        && menu.diagGetToggleUi(130)
                        && menu.diagGetSpinnerUi(161) == 1
                        && "Camera Mode: Follow Camera".equals(menu.diagGetSpinnerText(161)),
                "L2c 801->802 (Ultra Max)",
                "802calls=" + countCallsFor(c802, 802)
                        + " cam161=" + menu.diagGetSpinnerText(161));

        // Step L2d: 802 -> 800 (extra 13 features turned OFF once; shared 5 untouched; 800=true once)
        beginCapture();
        menu.diagClickMode(800);
        List<NativeCallRecord> c802to800 = endCapture();
        boolean extraCleared = !menu.diagGetToggleUi(108) && !FeatureStateStore.getBool(108, true)
                && !menu.diagGetToggleUi(119) && !FeatureStateStore.getBool(119, true)
                && menu.diagGetSeekBarUi(102) == 0 && FeatureStateStore.getInt(102, -1) == 0
                && menu.diagGetSpinnerUi(161) == 0 && FeatureStateStore.getInt(161, -1) == 0
                && menu.diagGetSeekBarUi(152) == 0 && FeatureStateStore.getInt(152, -1) == 0;
        r.step(countCallsFor(c802to800, 800) == 1
                        && countCallsFor(c802to800, 802) == 0
                        && countCallsFor(c802to800, 150) == 0
                        && countCallsFor(c802to800, 119) == 1
                        && countCallsFor(c802to800, 161) == 1
                        && verifyModeExclusive(menu, 800)
                        && extraCleared,
                "L2d 802->800 downgrade",
                "extraCleared=" + extraCleared
                        + " dup150=" + countCallsFor(c802to800, 150)
                        + " calls161=" + countCallsFor(c802to800, 161));

        // Step L2e: 800 -> 802 -> 801 -> OFF
        menu.diagClickMode(802);
        boolean ok802 = verifyModeExclusive(menu, 802);
        menu.diagClickMode(801);
        boolean ok801 = verifyModeExclusive(menu, 801)
                && !menu.diagGetToggleUi(119)
                && menu.diagGetSpinnerUi(161) == 0
                && menu.diagGetToggleUi(108);
        beginCapture();
        menu.diagClickMode(801); // turn 801 OFF
        List<NativeCallRecord> cOff = endCapture();
        boolean okOff = verifyModeExclusive(menu, -1)
                && countCallsFor(cOff, 801) == 1
                && !lastCallFor(cOff, 801).boolVal
                && !menu.diagGetToggleUi(108)
                && !menu.diagGetToggleUi(104)
                && menu.diagGetSeekBarUi(103) == 0;
        r.step(ok802 && ok801 && okOff, "L2e 800->802->801->OFF",
                "ok802=" + ok802 + " ok801=" + ok801 + " okOff=" + okOff);

        // L3: Manual override while mode is ON + SeekBar single Changes() call check
        menu.diagClickMode(800);
        beginCapture();
        menu.diagSetToggleUiAndSync(104, false);
        menu.diagSetSeekBarUiAndSync(103, 12);
        List<NativeCallRecord> cManual = endCapture();
        boolean l3Ok = verifyModeExclusive(menu, 800)
                && !menu.diagGetToggleUi(104) && !FeatureStateStore.getBool(104, true)
                && menu.diagGetSeekBarUi(103) == 12 && FeatureStateStore.getInt(103, 0) == 12
                && "42".equals(menu.diagGetSeekBarChipText(103))
                && countCallsFor(cManual, 104) == 1
                && countCallsFor(cManual, 103) == 1;
        r.step(l3Ok, "L3 Manual override while 800=ON",
                "104calls=" + countCallsFor(cManual, 104)
                        + " 103calls=" + countCallsFor(cManual, 103)
                        + " chip103=" + menu.diagGetSeekBarChipText(103));

        // L4: Save ON -> add Chams/Player changes -> simulated cold restart -> exact UI & Native parity
        menu.diagSetToggleUiAndSync(-1, true); // Turn Save ON mid-session (commits earlier 800+104+103)
        menu.diagSetToggleUiAndSync(121, true);
        menu.diagSetSpinnerUiAndSync(126, 1);
        menu.diagSetSpinnerUiAndSync(125, 2);
        menu.diagSetSeekBarUiAndSync(160, 77);
        menu.diagSelectTab("Chams");
        Preferences.flush("diag_before_cold_restart");

        // Simulate cold restart (clear in-memory FeatureStateStore, reload from SharedPreferences, rebuild UI)
        FeatureStateStore.init(ctx);
        FeatureStateStore.bindSessionUser(ctx, activeUser);
        Preferences.loadPref = FeatureStateStore.isSaveEnabled();

        beginCapture();
        menu.diagRebuildMenusPreservingTab();
        List<NativeCallRecord> cRestore = endCapture();

        boolean restoreNativeOk = countCallsFor(cRestore, 800) == 1
                && countCallsFor(cRestore, 104) == 1 && !lastCallFor(cRestore, 104).boolVal
                && countCallsFor(cRestore, 103) == 1 && lastCallFor(cRestore, 103).intVal == 12
                && countCallsFor(cRestore, 110) == 0 // already applied by 800!
                && countCallsFor(cRestore, 150) == 0 // already applied by 800!
                && countCallsFor(cRestore, 151) == 0 // already applied by 800!
                && countCallsFor(cRestore, 121) == 1
                && countCallsFor(cRestore, 126) == 1
                && countCallsFor(cRestore, 125) == 1
                && countCallsFor(cRestore, 160) == 1;
        boolean restoreUiOk = verifyModeExclusive(menu, 800)
                && !menu.diagGetToggleUi(104)
                && menu.diagGetSeekBarUi(103) == 12
                && "42".equals(menu.diagGetSeekBarChipText(103))
                && menu.diagGetToggleUi(121)
                && menu.diagGetSpinnerUi(126) == 1
                && menu.diagGetSpinnerUi(125) == 2
                && menu.diagGetSeekBarUi(160) == 77
                && menu.diagGetToggleUi(-1)
                && "Chams".equals(menu.diagGetSelectedTabName());
        r.step(restoreNativeOk && restoreUiOk, "L4 Simulated Restart + Rebuild Tab/UI/Native",
                "nativeOk=" + restoreNativeOk
                        + " uiOk=" + restoreUiOk
                        + " tab=" + menu.diagGetSelectedTabName()
                        + " dup110=" + countCallsFor(cRestore, 110));

        // L5: Programmatic setter already-equal chip & re-entrancy check
        beginCapture();
        menu.diagSetSeekBarUiAndSync(103, 5);
        List<NativeCallRecord> cReent = endCapture();
        r.step(countCallsFor(cReent, 103) == 1 && "35".equals(menu.diagGetSeekBarChipText(103)),
                "L5 SeekBar programmatic sync (no double fire)",
                "calls103=" + countCallsFor(cReent, 103)
                        + " chip103=" + menu.diagGetSeekBarChipText(103));
    }

    private static boolean verifyModeExclusive(Menu menu, int expectedActiveMode) {
        int[] modes = new int[]{800, 801, 802};
        for (int i = 0; i < modes.length; i++) {
            int fn = modes[i];
            boolean exp = (fn == expectedActiveMode);
            if (menu.diagIsModeUiActive(fn) != exp) return false;
            if (FeatureStateStore.getBool(fn, false) != exp) return false;
        }
        return true;
    }
}
