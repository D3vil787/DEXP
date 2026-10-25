package com.anton.dexphome;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;

import java.util.List;

/**
 * After power-on (boot or wake from standby) waits until the stock Konka launcher is on screen
 * (it starts the TV middleware, so HDMI then has sound) and opens HDMI full screen, so there is
 * no need to press OK on the small preview window every time.
 */
public class AutoHdmiService extends Service {

    static final String PREFS = "home";
    static final String KEY_ENABLED = "auto_hdmi";
    static final String KEY_PORT = "hdmi_port";
    static final String KEY_DELAY = "hdmi_delay_s";
    static final int[] DELAYS = {5, 10, 15, 20, 30};
    static final int DEFAULT_DELAY = 10;
    static final String ACTION_POWER_ON = "com.anton.dexphome.POWER_ON";

    private static final String STOCK_LAUNCHER = "com.konka.avenger";
    private static final long WAIT_LIMIT_MS = 120000;
    private static final long POLL_MS = 1000;


    /** Last action, shown on the diagnostics screen. */
    static volatile String status = "не запускался";

    private final Handler handler = new Handler();
    private long deadline;
    private boolean waiting;
    /** When the stock launcher was first seen on top in this wait; 0 = not yet. */
    private long launcherSince;

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            begin("пробуждение");
        }
    };

    static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    static int port(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_PORT, 1);
    }

    /** Seconds the stock launcher must stay on screen before HDMI opens (it finishes audio setup meanwhile). */
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

    static void sync(Context c) {
        Intent svc = new Intent(c, AutoHdmiService.class);
        if (isEnabled(c)) c.startService(svc);
        else c.stopService(svc);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_POWER_ON.equals(intent.getAction())) begin("включение");
        else if (status.equals("не запускался")) status = "ждёт включения телевизора";
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screenOn);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void begin(String why) {
        if (!isEnabled(this)) return;
        handler.removeCallbacksAndMessages(null);
        deadline = SystemClock.uptimeMillis() + WAIT_LIMIT_MS;
        waiting = true;
        launcherSince = 0;
        status = why + ": жду штатный экран";
        handler.post(poll);
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!waiting) return;
            String top = topPackage();
            if (Tv.TV_PKG.equals(top)) {
                waiting = false;
                status = "TV-приложение уже на экране, ничего не делаю";
                return;
            }
            if (STOCK_LAUNCHER.equals(top)) {
                long now = SystemClock.uptimeMillis();
                if (launcherSince == 0) launcherSince = now;
                long need = delay(AutoHdmiService.this) * 1000L;
                if (now - launcherSince >= need) {
                    waiting = false;
                    status = "штатный экран простоял " + delay(AutoHdmiService.this) + " с, открываю HDMI "
                            + port(AutoHdmiService.this);
                    handler.post(open);
                    return;
                }
                status = "штатный экран на месте, жду " + ((need - (now - launcherSince)) / 1000 + 1) + " с";
                handler.postDelayed(this, POLL_MS);
                return;
            }
            // Something else is on top: restart the stock-launcher timer once it comes back.
            launcherSince = 0;
            if (SystemClock.uptimeMillis() > deadline) {
                waiting = false;
                status = "штатный экран не продержался нужное время за 90 с (сверху: " + top + ")";
                return;
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable open = new Runnable() {
        @Override
        public void run() {
            // The user may have started something else in the meantime: only act over the launcher.
            if (STOCK_LAUNCHER.equals(topPackage())) {
                if (Apps.startQuiet(AutoHdmiService.this, Tv.switchToHdmi(port(AutoHdmiService.this)))) {
                    status = "HDMI " + port(AutoHdmiService.this) + " открыт";
                } else {
                    status = "не удалось открыть TV-приложение";
                }
            } else {
                status = "пропущено: на экране уже другое приложение";
            }
        }
    };

    @SuppressWarnings("deprecation")
    private String topPackage() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> t = am.getRunningTasks(1);
            if (t != null && !t.isEmpty() && t.get(0).topActivity != null) {
                return t.get(0).topActivity.getPackageName();
            }
        } catch (Exception ignored) {
        }
        return "?";
    }
}
