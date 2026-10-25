package com.anton.dexphome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Grid of every installed app, plus shortcuts into the hidden stock Android settings. */
public class AllAppsActivity extends Activity {

    private static final int COLUMNS = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.px(110), Ui.px(60), Ui.px(110), Ui.px(80));
        list.setClipChildren(false);
        scroll.addView(list);
        root.addView(scroll);

        TextView title = new TextView(this);
        title.setText("Все приложения");
        title.setTextColor(0xFFFFFFFF);
        Ui.textSize(title, 40);
        title.getPaint().setFakeBoldText(true);
        title.setPadding(Ui.px(12), 0, 0, Ui.px(30));
        list.addView(title);

        Drawable gear = null;
        try {
            gear = getPackageManager().getApplicationIcon("com.android.settings");
        } catch (Exception ignored) {
        }

        LinearLayout row = null;
        int count = 0;
        View first = null;

        // Stock Android settings that the DEXP menu hides.
        // Each entry lists fallbacks, tried in order.
        Object[][] extras = {
                {"Настройки Android", new Intent[]{
                        new Intent(Settings.ACTION_SETTINGS),
                        Apps.settingsComponent(".Settings")}},
                {"Для разработчиков", new Intent[]{
                        new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                        Apps.settingsComponent(".Settings$DevelopmentSettingsActivity"),
                        Apps.settingsComponent(".DevelopmentSettings"),
                        Apps.settingsFragment("com.android.settings.DevelopmentSettings")}},
                {"Об устройстве", new Intent[]{
                        new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
                        Apps.settingsComponent(".Settings$DeviceInfoSettingsActivity"),
                        Apps.settingsFragment("com.android.settings.DeviceInfoSettings")}},
                {"Управление приложениями", new Intent[]{
                        new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),
                        new Intent(Settings.ACTION_APPLICATION_SETTINGS),
                        Apps.settingsFragment("com.android.settings.applications.ManageApplications")}},
                {"Все разделы настроек", new Intent[]{
                        new Intent(this, SettingsListActivity.class)}},
        };

        List<Apps.Entry> apps = Apps.launchables(this);
        int total = apps.size() + extras.length;
        for (int i = 0; i < total; i++) {
            if (count % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                list.addView(row);
            }
            final String label;
            final Drawable icon;
            final Intent[] intent;
            if (i < apps.size()) {
                Apps.Entry e = apps.get(i);
                label = e.label;
                icon = e.icon;
                intent = new Intent[]{e.intent};
            } else {
                Object[] x = extras[i - apps.size()];
                label = (String) x[0];
                icon = gear;
                intent = (Intent[]) x[1];
            }
            View cell = cell(label, icon, intent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(260), Ui.px(220));
            lp.setMargins(Ui.px(12), Ui.px(12), Ui.px(12), Ui.px(12));
            row.addView(cell, lp);
            if (first == null) first = cell;
            count++;
        }

        setContentView(root);
        if (first != null) first.requestFocus();
    }

    private View cell(String label, Drawable icon, final Intent[] intent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        Ui.makeFocusable(c, 32, 1.08f);

        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            c.addView(iv, new LinearLayout.LayoutParams(Ui.px(96), Ui.px(96)));
        }
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFFFFFFFF);
        Ui.textSize(t, 22);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Ui.px(10), Ui.px(14), Ui.px(10), 0);
        c.addView(t, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.startFirst(AllAppsActivity.this, intent);
            }
        });
        return c;
    }
}
