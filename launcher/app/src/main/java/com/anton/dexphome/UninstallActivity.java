package com.anton.dexphome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Lists launchable apps and removes the chosen one through the stock Android
 * uninstall dialog (the package installer works even though the vendor app
 * manager crashes). Preinstalled system apps are marked and cannot be removed.
 */
public class UninstallActivity extends Activity {

    private LinearLayout list;
    private int focusIndex = 0;

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
        list.setPadding(Ui.px(200), Ui.px(60), Ui.px(200), Ui.px(80));
        list.setClipChildren(false);
        scroll.addView(list);
        root.addView(scroll);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(); // refresh after returning from the uninstall dialog
    }

    private void build() {
        list.removeAllViews();

        TextView title = new TextView(this);
        title.setText("Удаление приложений");
        title.setTextColor(0xFFFFFFFF);
        Ui.textSize(title, 40);
        title.getPaint().setFakeBoldText(true);
        list.addView(title);

        TextView hint = new TextView(this);
        hint.setText("«системное» — встроено в прошивку, удалить нельзя. «откатить обновление» — вернётся заводская версия. «удалить» — удаляется полностью.");
        hint.setTextColor(0xB3FFFFFF);
        Ui.textSize(hint, 22);
        hint.setPadding(0, Ui.px(8), 0, Ui.px(24));
        list.addView(hint);

        PackageManager pm = getPackageManager();
        List<Apps.Entry> apps = Apps.launchables(this);
        View toFocus = null;
        int i = 0;
        for (final Apps.Entry e : apps) {
            boolean system = false, updated = false;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(e.pkg, 0);
                updated = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !updated;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            View row = row(e, system, updated);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(84));
            lp.setMargins(0, Ui.px(6), 0, Ui.px(6));
            list.addView(row, lp);
            if (i == focusIndex || toFocus == null) toFocus = row;
            i++;
        }
        if (toFocus != null) {
            final View f = toFocus;
            f.post(new Runnable() {
                @Override
                public void run() {
                    f.requestFocus();
                }
            });
        }
    }

    private View row(final Apps.Entry e, final boolean system, final boolean updated) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        Ui.makeFocusable(r, 22, 1.02f);

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(e.icon);
        r.addView(iv, new LinearLayout.LayoutParams(Ui.px(56), Ui.px(56)));

        TextView name = new TextView(this);
        name.setText(e.label);
        name.setTextColor(0xFFFFFFFF);
        Ui.textSize(name, 26);
        name.setSingleLine(true);
        name.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        r.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tag = new TextView(this);
        // An updated firmware app can only be rolled back to its factory version, not removed.
        tag.setText(system ? "системное" : updated ? "откатить обновление" : "удалить");
        tag.setTextColor(system ? 0x80FFFFFF : updated ? 0xFFFFD180 : 0xFFFF8A80);
        Ui.textSize(tag, 22);
        r.addView(tag);

        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                focusIndex = list.indexOfChild(v) - 2;
                if (!system) {
                    Apps.start(UninstallActivity.this,
                            new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.pkg)));
                } else if (!Apps.startQuiet(UninstallActivity.this,
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + e.pkg)))) {
                    Toast.makeText(UninstallActivity.this,
                            e.label + ": встроено в прошивку, удалить нельзя",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        return r;
    }
}
