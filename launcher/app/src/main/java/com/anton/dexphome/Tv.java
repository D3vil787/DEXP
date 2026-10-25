package com.anton.dexphome;

import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Method;

import dalvik.system.DexClassLoader;

/**
 * Konka / MStar TV input-source control, as found in the stock Konka launcher:
 *  - HDMI: ask the TV app to switch source like the remote's Source key does
 *    (action com.konka.SwitchSource, extra SwitchSourceByTVHotKey = EnumInputSource ordinal);
 *    the TV app then switches video and audio together.
 *  - Back on the home screen: switch the TV back to the Android source (E_INPUT_SOURCE_STORAGE)
 *    via com.mstar.android.tvapi.common.TvManager, as the stock launcher does,
 *    so Android apps get the audio path again.
 */
final class Tv {
    private Tv() {}

    static final String TV_PKG = "com.konka.tvsettings";
    static final String TV_ROOT = "com.konka.tvsettings.RootActivity";

    /** EnumInputSource ordinals in this firmware's com.mstar.android.jar. */
    static final int SRC_HDMI1 = 23;   // HDMI2 = 24, HDMI3 = 25, HDMI4 = 26

    /** Last result of a TvManager call, shown on the diagnostics screen. */
    static volatile String status = "ещё не вызывался";

    static Intent switchToHdmi(int port) {
        int p = Math.max(1, Math.min(4, port));
        return new Intent("com.konka.SwitchSource")
                .setClassName(TV_PKG, TV_ROOT)
                .putExtra("SwitchSourceByTVHotKey", SRC_HDMI1 + p - 1)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static Intent openTvApp() {
        return new Intent(Intent.ACTION_MAIN)
                .setClassName(TV_PKG, TV_ROOT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Switch the TV to the Android ("storage") source if it is on another one. Runs off the UI thread. */
    static void toAndroidSourceAsync(final Context c) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                toAndroidSource(c.getApplicationContext());
            }
        }, "tv-source").start();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void toAndroidSource(Context c) {
        try {
            Class<?> tmCls = load(c, "com.mstar.android.tvapi.common.TvManager");
            Class enumCls = load(c, "com.mstar.android.tvapi.common.vo.TvOsType$EnumInputSource");
            Object tm = tmCls.getMethod("getInstance").invoke(null);
            if (tm == null) {
                status = "TvManager.getInstance() = null";
                return;
            }
            Object storage = Enum.valueOf(enumCls, "E_INPUT_SOURCE_STORAGE");
            Object cur = tmCls.getMethod("getCurrentInputSource").invoke(tm);
            String curName = cur == null ? "null" : ((Enum) cur).name();
            if ("E_INPUT_SOURCE_STORAGE".equals(curName)) {
                status = "источник уже Android (" + curName + ")";
                return;
            }
            Method set = tmCls.getMethod("setInputSource", enumCls, boolean.class, boolean.class, boolean.class);
            Object ok = set.invoke(tm, storage, false, false, false);
            status = "переключено " + curName + " → STORAGE, результат " + ok;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            status = "ошибка: " + cause;
        }
    }

    private static ClassLoader mstarLoader;

    /** The MStar API lives in /system/framework/com.mstar.android.jar; load it if not on our classpath. */
    private static Class<?> load(Context c, String name) throws ClassNotFoundException {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            if (mstarLoader == null) {
                mstarLoader = new DexClassLoader("/system/framework/com.mstar.android.jar",
                        c.getDir("mstar", Context.MODE_PRIVATE).getAbsolutePath(),
                        "/system/lib", Tv.class.getClassLoader());
            }
            return mstarLoader.loadClass(name);
        }
    }
}
