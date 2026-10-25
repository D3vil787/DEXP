package com.anton.dexphome;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * After power-on (boot or wake from standby) waits until the stock Konka launcher has stayed
 * on screen for a while (it starts the TV middleware and audio meanwhile) and then opens HDMI
 * full screen, so there is no need to press OK on the small preview window every time.
 *
 * Runs in the foreground and remembers an unfinished wait in prefs: right after boot the TV is
 * short on memory and may kill the process; a restarted service picks the wait up again.
 */
public class AutoHdmiService extends Service {

    static final String PREFS = "home";
    static final String KEY_ENABLED = "auto_hdmi";
    static final String KEY_PORT = "hdmi_port";
    static final String KEY_DELAY = "hdmi_delay_s";
    static final String KEY_STATUS = "auto_status";
    /** elapsedRealtime() until which a power-on wait is still in progress. */
    static final String KEY_PENDING_UNTIL = "auto_pending_until";
    static final int[] DELAYS = {5, 10, 15, 20, 30};
    static final int DEFAULT_DELAY = 10;
    static final String ACTION_POWER_ON = "com.anton.dexphome.POWER_ON";

    private static final String STOCK_LAUNCHER = "com.konka.avenger";
    private static final long WAIT_LIMIT_MS = 120000;
    private static final long POLL_MS = 1000;
    private static final int NOTIFICATION_ID = 1;

    private final Handler handler = new Handler();
    private boolean waiting;
    /** When the stock launcher was first seen on screen in this wait; 0 = not yet. */
    private long launcherSince;

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            begin("пробуждение", false);
        }
    };

    // ---- settings ----

    static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    static int port(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_PORT, 1);
    }

    /** Seconds the stock launcher must stay on screen before HDMI opens. */
    static int delay(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_DELAY, DEFAULT_DELAY);
    }

    static int nextDelay(Context c) {
        int cur = delay(c), next = DELAYS[0];
        for (int i = 0; i < DELAYS.length; i++) {
            if (DELAYS[i] == cur) {
                next = DELAYS[(i + 1) % DELAYS.length];
                break;
            }
        }
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_DELAY, next).apply();
        return next;
    }

    /** Last action with its time, shown on the diagnostics screen; survives process restarts. */
    static String status(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STATUS, "ещё не срабатывал");
    }

    static void sync(Context c) {
        Intent svc = new Intent(c, AutoHdmiService.class);
        if (isEnabled(c)) c.startService(svc);
        else c.stopService(svc);
    }

    private void setStatus(String s) {
        String t = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_STATUS, t + " " + s).apply();
    }

    private void setPendingUntil(long elapsed) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_PENDING_UNTIL, elapsed).apply();
    }

    private long pendingUntil() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_PENDING_UNTIL, 0);
    }

    // ---- lifecycle ----

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        goForeground();
    }

    @SuppressWarnings("deprecation")
    private void goForeground() {
        try {
            PendingIntent open = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class), 0);
            Notification n = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("Помощник ТВ")
                    .setContentText("Автозапуск HDMI включён")
                    .setContentIntent(open)
                    .setOngoing(true)
                    .getNotification();
            startForeground(NOTIFICATION_ID, n);
        } catch (Exception ignored) {
            // Not critical: the pending marker still lets a restarted service resume.
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_POWER_ON.equals(intent.getAction())) {
            begin("включение", false);
        } else if (!waiting && pendingUntil() > SystemClock.elapsedRealtime()) {
            // The process was killed while waiting after power-on: carry on.
            begin("продолжение после перезапуска помощника", true);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screenOn);
        } catch (Exception ignored) {
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- the wait ----

    private void begin(String why, boolean resume) {
        if (!isEnabled(this)) return;
        handler.removeCallbacksAndMessages(null);
        // A fresh power-on gets a fresh time limit; a resumed wait keeps the original one.
        if (!resume || pendingUntil() <= SystemClock.elapsedRealtime()) {
            setPendingUntil(SystemClock.elapsedRealtime() + WAIT_LIMIT_MS);
        }
        waiting = true;
        launcherSince = 0;
        setStatus(why + ": жду штатный экран");
        handler.post(poll);
    }

    private void done(String result) {
        waiting = false;
        setPendingUntil(0);
        setStatus(result);
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!waiting) return;
            String top = topActivity();
            if (isFullScreenTv(top)) {
                done("полноэкранный TV-режим уже открыт, ничего не делаю");
                return;
            }
            if (isLauncherScreen(top)) {
                long now = SystemClock.uptimeMillis();
                if (launcherSince == 0) launcherSince = now;
                if (now - launcherSince >= delay(AutoHdmiService.this) * 1000L) {
                    waiting = false;
                    handler.post(open);
                    return;
                }
                handler.postDelayed(this, POLL_MS);
                return;
            }
            // Something else is on screen: restart the launcher timer when it comes back.
            launcherSince = 0;
            if (SystemClock.elapsedRealtime() > pendingUntil()) {
                done("штатный экран не продержался нужное время за 120 с (сверху: " + top + ")");
                return;
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable open = new Runnable() {
        @Override
        public void run() {
            String top = topActivity();
            if (!isLauncherScreen(top)) {
                done("пропущено: на экране уже " + top);
                return;
            }
            int p = port(AutoHdmiService.this);
            if (Apps.startQuiet(AutoHdmiService.this, Tv.switchToHdmi(p))) {
                done("штатный экран простоял " + delay(AutoHdmiService.this) + " с, HDMI " + p + " открыт");
            } else {
                done("не удалось открыть TV-приложение");
            }
        }
    };

    /** HDMI / TV picture is already full screen. */
    private static boolean isFullScreenTv(String topActivity) {
        return topActivity != null && topActivity.equals(Tv.TV_PKG + "/" + Tv.TV_ROOT);
    }

    /**
     * The stock home screen, possibly with a TV-app popup over it (e.g. the source info banner
     * shown after power-on): both count as "launcher on screen".
     */
    private static boolean isLauncherScreen(String topActivity) {
        if (topActivity == null) return false;
        if (topActivity.startsWith(STOCK_LAUNCHER + "/")) return true;
        return topActivity.startsWith(Tv.TV_PKG + "/") && !isFullScreenTv(topActivity);
    }

    /** "package/full.class.Name" of the top activity, or "?". */
    @SuppressWarnings("deprecation")
    private String topActivity() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> t = am.getRunningTasks(1);
            if (t != null && !t.isEmpty() && t.get(0).topActivity != null) {
                return t.get(0).topActivity.getPackageName() + "/" + t.get(0).topActivity.getClassName();
            }
        } catch (Exception ignored) {
        }
        return "?";
    }
}
