package com.anton.dexphome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Small persistent event log (last N lines) plus power-on detection that does not depend on
 * the firmware sending BOOT_COMPLETED or SCREEN_ON:
 *  - a new boot is a new "boot id" (wall clock minus time since boot);
 *  - a wake from standby shows up as growth of (elapsedRealtime - uptimeMillis), because
 *    uptimeMillis stops while the CPU is in deep sleep.
 */
final class Events {
    private Events() {}

    private static final String PREFS = AutoHdmiService.PREFS;
    private static final String KEY_LOG = "event_log";
    private static final String KEY_BOOT_ID = "handled_boot_id";
    private static final String KEY_SLEEP = "sleep_delta_ms";
    private static final int MAX_LINES = 14;
    /** A boot counts as "fresh" (worth opening HDMI for) only within this time after it. */
    private static final long FRESH_BOOT_MS = 10 * 60 * 1000L;
    /** Deep sleep longer than this means the TV was in standby. */
    private static final long SLEEP_THRESHOLD_MS = 20 * 1000L;

    static synchronized void log(Context c, String what) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String t = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = t + " " + what;
        String old = p.getString(KEY_LOG, "");
        String[] lines = old.length() == 0 ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < lines.length && i < MAX_LINES - 1; i++) sb.append('\n').append(lines[i]);
        p.edit().putString(KEY_LOG, sb.toString()).apply();
    }

    /** Newest first. */
    static String read(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOG, "(пусто)");
    }

    /**
     * Returns a reason string if the TV has just been powered on (new boot or wake from standby)
     * and this power-on has not been handled yet; otherwise null. Marks it handled.
     */
    static synchronized String checkPowerOn(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long elapsed = SystemClock.elapsedRealtime();
        long sleep = elapsed - SystemClock.uptimeMillis();
        long bootId = (System.currentTimeMillis() - elapsed) / 60000L; // minute resolution

        String reason = null;
        if (p.getLong(KEY_BOOT_ID, -1) != bootId) {
            p.edit().putLong(KEY_BOOT_ID, bootId).putLong(KEY_SLEEP, sleep).apply();
            if (elapsed < FRESH_BOOT_MS) reason = "загрузка телевизора";
            return reason;
        }
        long lastSleep = p.getLong(KEY_SLEEP, sleep);
        if (sleep - lastSleep > SLEEP_THRESHOLD_MS) {
            reason = "пробуждение (сон " + (sleep - lastSleep) / 1000 + " с)";
        }
        p.edit().putLong(KEY_SLEEP, sleep).apply();
        return reason;
    }
}
