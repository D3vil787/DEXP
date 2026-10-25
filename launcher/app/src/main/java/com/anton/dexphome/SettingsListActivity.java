package com.anton.dexphome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Diagnostics: ADB state, plus every exported screen of apps that look like
 * settings / factory / service menus, so a hidden ADB switch can be found and opened.
 */
public class SettingsListActivity extends Activity {

    private static final Pattern INTERESTING = Pattern.compile(
            "(?i).*(setting|factory|debug|develop|engineer|service|mtk|mstar|tvmenu|hidden).*");

    private LinearLayout list;
    private View first;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background(this));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.px(160), Ui.px(60), Ui.px(160), Ui.px(80));
        list.setClipChildren(false);
        scroll.addView(list);
        root.addView(scroll);

        heading("Диагностика", 40);
        info("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
                + Build.MANUFACTURER + " " + Build.MODEL);
        info("ADB включён: " + adbEnabled()
                + "   adbd: " + prop("init.svc.adbd")
                + "   порт: " + prop("service.adb.tcp.port") + " / " + prop("persist.adb.tcp.port"));
        info("Автозапуск HDMI: " + (AutoHdmiService.isEnabled(this) ? "вкл" : "выкл")
                + ", порт HDMI " + AutoHdmiService.port(this) + ", задержка " + AutoHdmiService.delay(this)
                + " с — " + AutoHdmiService.status);
        info("ro.debuggable=" + prop("ro.debuggable") + "   ro.secure=" + prop("ro.secure")
                + "   ro.adb.secure=" + prop("ro.adb.secure"));

        PackageManager pm = getPackageManager();
        List<PackageInfo> pkgs = pm.getInstalledPackages(0);
        Collections.sort(pkgs, new Comparator<PackageInfo>() {
            @Override
            public int compare(PackageInfo a, PackageInfo b) {
                return a.packageName.compareTo(b.packageName);
            }
        });
        int found = 0;
        for (PackageInfo p : pkgs) {
            if (!INTERESTING.matcher(p.packageName).matches()) continue;
            List<ActivityInfo> screens = new ArrayList<ActivityInfo>();
            try {
                PackageInfo full = pm.getPackageInfo(p.packageName, PackageManager.GET_ACTIVITIES);
                if (full.activities != null) {
                    for (ActivityInfo ai : full.activities) {
                        if (ai.exported) screens.add(ai);
                    }
                }
            } catch (Exception ignored) {
            }
            CharSequence appLabel = p.applicationInfo != null ? p.applicationInfo.loadLabel(pm) : "";
            heading(appLabel + "   " + p.packageName + "   (" + screens.size() + ")", 28);
            found++;
            for (ActivityInfo ai : screens) row(pm, ai);
        }
        if (found == 0) info("Подходящих приложений не найдено.");

        setContentView(root);
        if (first != null) first.requestFocus();
    }

    private void heading(CharSequence text, float size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        Ui.textSize(t, size);
        t.getPaint().setFakeBoldText(true);
        t.setPadding(0, Ui.px(28), 0, Ui.px(10));
        list.addView(t);
    }

    private void info(CharSequence text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xCCFFFFFF);
        Ui.textSize(t, 24);
        t.setPadding(0, Ui.px(4), 0, Ui.px(4));
        list.addView(t);
    }

    private void row(PackageManager pm, final ActivityInfo ai) {
        TextView row = new TextView(this);
        String shortName = ai.name.startsWith(ai.packageName)
                ? ai.name.substring(ai.packageName.length()) : ai.name;
        row.setText(ai.loadLabel(pm) + "    " + shortName);
        row.setTextColor(0xFFFFFFFF);
        Ui.textSize(row, 22);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setSingleLine(true);
        row.setPadding(Ui.px(30), 0, Ui.px(30), 0);
        Ui.makeFocusable(row, 20, 1.02f);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.start(SettingsListActivity.this,
                        new Intent(Intent.ACTION_MAIN).setClassName(ai.packageName, ai.name));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(64));
        lp.setMargins(0, Ui.px(6), 0, Ui.px(6));
        list.addView(row, lp);
        if (first == null) first = row;
    }

    private String adbEnabled() {
        try {
            return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1
                    ? "да" : "нет";
        } catch (Exception e) {
            return "?";
        }
    }

    /** Reads a system property via the hidden android.os.SystemProperties class. */
    private static String prop(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            String v = (String) get.invoke(null, key);
            return v == null || v.length() == 0 ? "—" : v;
        } catch (Exception e) {
            return "?";
        }
    }
}
