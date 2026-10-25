package com.anton.dexphome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lists every exported screen of the stock Settings app, so hidden pages
 * (developer options, about device, app management) can be opened directly.
 */
public class SettingsListActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background(this));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.px(160), Ui.px(60), Ui.px(160), Ui.px(80));
        list.setClipChildren(false);
        scroll.addView(list);
        root.addView(scroll);

        TextView title = new TextView(this);
        title.setText("Разделы настроек Android");
        title.setTextColor(0xFFFFFFFF);
        Ui.textSize(title, 40);
        title.getPaint().setFakeBoldText(true);
        title.setPadding(0, 0, 0, Ui.px(30));
        list.addView(title);

        final PackageManager pm = getPackageManager();
        List<ActivityInfo> screens = new ArrayList<ActivityInfo>();
        try {
            PackageInfo pi = pm.getPackageInfo(Apps.SETTINGS_PKG, PackageManager.GET_ACTIVITIES);
            if (pi.activities != null) {
                for (ActivityInfo ai : pi.activities) {
                    if (ai.exported && ai.enabled) screens.add(ai);
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        Collections.sort(screens, new Comparator<ActivityInfo>() {
            @Override
            public int compare(ActivityInfo a, ActivityInfo b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        View first = null;
        if (screens.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("Приложение настроек Android не найдено");
            none.setTextColor(0xB3FFFFFF);
            Ui.textSize(none, 26);
            list.addView(none);
        }
        for (final ActivityInfo ai : screens) {
            TextView row = new TextView(this);
            CharSequence label = ai.loadLabel(pm);
            String shortName = ai.name.startsWith(Apps.SETTINGS_PKG)
                    ? ai.name.substring(Apps.SETTINGS_PKG.length()) : ai.name;
            row.setText(label + "    " + shortName);
            row.setTextColor(0xFFFFFFFF);
            Ui.textSize(row, 24);
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
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(72));
            lp.setMargins(0, Ui.px(8), 0, Ui.px(8));
            list.addView(row, lp);
            if (first == null) first = row;
        }

        setContentView(root);
        if (first != null) first.requestFocus();
    }
}
