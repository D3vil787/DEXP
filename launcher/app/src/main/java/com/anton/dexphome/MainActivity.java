package com.anton.dexphome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Apple TV-style home screen: big glass tiles + clock + a small bottom row. */
public class MainActivity extends Activity {

    /** One big tile. The first installed package (or label match) wins. */
    static final class TileDef {
        final String label, letter;
        final int color;
        final String[] pkgs, keywords;

        TileDef(String label, String letter, int color, String[] pkgs, String[] keywords) {
            this.label = label;
            this.letter = letter;
            this.color = color;
            this.pkgs = pkgs;
            this.keywords = keywords;
        }
    }

    // Kinopoisk is not listed: its Android app needs Android 7+, this TV has 4.2.
    // Lampa is not listed: Android 4.2 lacks the Let's Encrypt root, so its sources fail.
    static final TileDef[] TILES = {
            new TileDef("YouTube", "Y", 0xFFE01818,
                    new String[]{"com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta",
                            "com.liskovsoft.smarttubetv", "com.liskovsoft.videomanager"},
                    new String[]{"smarttube"}),
    };

    private static final String PREFS = "home";
    private static final String KEY_HDMI_PORT = "hdmi_port";

    private FrameLayout root;
    private TextView clock;
    private final Handler handler = new Handler();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final List<View> focusables = new ArrayList<View>();
    private int focusIndex = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            clock.setText(timeFormat.format(new Date()));
            handler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        root = new FrameLayout(this);
        root.setBackground(Ui.background(this));
        setContentView(root);
        AutoHdmiService.sync(this); // also covers the first launch after install / update
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            build(); // re-resolve apps every time: something may have been installed meanwhile
        } catch (Throwable t) {
            // Never leave the TV stuck on a broken home screen: show the emergency exits instead.
            root.removeAllViews();
            focusables.clear();
            root.addView(RescueActivity.panel(this, t), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            return;
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        View f = getCurrentFocus();
        int i = focusables.indexOf(f);
        if (i >= 0) focusIndex = i;
    }

    @Override
    public void onBackPressed() {
        finish(); // a helper app now, not the home screen
    }

    private void build() {
        root.removeAllViews();
        focusables.clear();
        List<Apps.Entry> apps = Apps.launchables(this);

        // Clock, top right.
        clock = new TextView(this);
        clock.setTextColor(0xFFFFFFFF);
        Ui.textSize(clock, 38);
        clock.getPaint().setFakeBoldText(true);
        clock.setPadding(Ui.px(30), Ui.px(12), Ui.px(30), Ui.px(12));
        clock.setBackground(Ui.glass(false, 40));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        clp.topMargin = Ui.px(44);
        clp.rightMargin = Ui.px(60);
        root.addView(clock, clp);

        // Big tiles.
        int tileW = Ui.px(420), tileH = Ui.px(280), gap = Ui.px(68);
        List<View> tiles = new ArrayList<View>();
        tiles.add(hdmiTile());
        for (TileDef d : TILES) tiles.add(bigTile(d, Apps.find(apps, d.pkgs, d.keywords, false)));
        int n = tiles.size();
        int x = (Ui.screenW - (n * tileW + (n - 1) * gap)) / 2;
        for (View t : tiles) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(tileW, tileH);
            lp.leftMargin = x;
            lp.topMargin = Ui.px(390);
            root.addView(t, lp);
            focusables.add(t);
            x += tileW + gap;
        }

        // Small bottom row: TV, all apps, settings.
        List<View> pills = new ArrayList<View>();
        final boolean auto = AutoHdmiService.isEnabled(this);
        pills.add(pill(auto ? "Автозапуск HDMI: вкл" : "Автозапуск HDMI: выкл", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !AutoHdmiService.isEnabled(MainActivity.this);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(AutoHdmiService.KEY_ENABLED, on).apply();
                AutoHdmiService.sync(MainActivity.this);
                ((TextView) v).setText(on ? "Автозапуск HDMI: вкл" : "Автозапуск HDMI: выкл");
                Toast.makeText(MainActivity.this, on
                        ? "После включения телевизора HDMI " + hdmiPort() + " откроется сам"
                        : "Автозапуск HDMI выключен", Toast.LENGTH_LONG).show();
            }
        }));
        pills.add(pill("ТВ", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.start(MainActivity.this, Tv.openTvApp());
            }
        }));
        pills.add(pill("Задержка HDMI: " + AutoHdmiService.delay(this) + " с", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int d = AutoHdmiService.nextDelay(MainActivity.this);
                ((TextView) v).setText("Задержка HDMI: " + d + " с");
            }
        }));
        pills.add(pill("Все приложения", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AllAppsActivity.class));
            }
        }));
        final Apps.Entry vendorSettings = Apps.find(apps, null,
                new String[]{"системные настр", "настройки тв"}, false);
        pills.add(pill("Настройки", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.start(MainActivity.this, vendorSettings != null
                        ? vendorSettings.intent : new Intent(Settings.ACTION_SETTINGS));
            }
        }));
        int pillW = Ui.px(330), pillH = Ui.px(72), pillGap = Ui.px(22);
        int px = (Ui.screenW - (pills.size() * pillW + (pills.size() - 1) * pillGap)) / 2;
        for (View p : pills) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pillW, pillH);
            lp.leftMargin = px;
            lp.topMargin = Ui.px(790);
            root.addView(p, lp);
            focusables.add(p);
            px += pillW + pillGap;
        }

        final View first = focusables.get(Math.min(focusIndex, focusables.size() - 1));
        first.post(new Runnable() {
            @Override
            public void run() {
                first.requestFocus();
            }
        });
    }

    private int hdmiPort() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_HDMI_PORT, 1);
    }

    /** Switches the TV to HDMI like the remote's Source key. Long press OK changes the port. */
    private View hdmiTile() {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        Ui.makeFocusable(t, 48, 1.07f);

        int iconSize = Ui.px(120);
        TextView g = new TextView(this);
        g.setText("HDMI");
        g.setGravity(Gravity.CENTER);
        g.setTextColor(0xFFFFFFFF);
        Ui.textSize(g, 32);
        g.getPaint().setFakeBoldText(true);
        g.setBackground(Ui.rounded(0xFF2F86FF, 30));
        t.addView(g, new LinearLayout.LayoutParams(Ui.px(150), iconSize));

        final TextView label = new TextView(this);
        label.setText("Приставка · HDMI " + hdmiPort());
        label.setTextColor(0xFFFFFFFF);
        Ui.textSize(label, 32);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.px(22);
        t.addView(label, llp);

        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.start(MainActivity.this, Tv.switchToHdmi(hdmiPort()));
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int next = hdmiPort() % 4 + 1;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_HDMI_PORT, next).apply();
                label.setText("Приставка · HDMI " + next);
                Toast.makeText(MainActivity.this, "Порт: HDMI " + next, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        return t;
    }

    private View bigTile(final TileDef d, final Apps.Entry app) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        Ui.makeFocusable(t, 48, 1.07f);

        int iconSize = Ui.px(120);
        if (app != null && app.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            t.addView(iv, new LinearLayout.LayoutParams(iconSize, iconSize));
        } else {
            TextView g = new TextView(this);
            g.setText(d.letter);
            g.setGravity(Gravity.CENTER);
            g.setTextColor(0xFFFFFFFF);
            Ui.textSize(g, 52);
            g.getPaint().setFakeBoldText(true);
            g.setBackground(Ui.rounded(d.color, 30));
            t.addView(g, new LinearLayout.LayoutParams(iconSize, iconSize));
        }

        TextView label = new TextView(this);
        label.setText(app != null ? d.label : d.label + " — не установлено");
        label.setTextColor(app != null ? 0xFFFFFFFF : 0x99FFFFFF);
        Ui.textSize(label, app != null ? 32 : 24);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.px(22);
        t.addView(label, llp);

        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (app != null) {
                    Apps.start(MainActivity.this, app.intent);
                } else {
                    Toast.makeText(MainActivity.this, d.label + " не установлено",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        return t;
    }

    private View pill(String text, View.OnClickListener click) {
        TextView p = new TextView(this);
        p.setText(text);
        p.setGravity(Gravity.CENTER);
        p.setTextColor(0xD9FFFFFF);
        Ui.textSize(p, 24);
        Ui.makeFocusable(p, 36, 1.06f);
        p.setOnClickListener(click);
        return p;
    }
}
