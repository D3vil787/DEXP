package com.anton.dexphome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Emergency exits from this launcher: open the TV's own home screen, drop the
 * "Always" default, or uninstall this launcher. Also reachable directly via EXTRA_ACTION.
 */
public class RescueActivity extends Activity {

    static final String EXTRA_ACTION = "action";
    static final String ACTION_STOCK_HOME = "stock_home";
    static final String ACTION_RESET_HOME = "reset_home";

    static Intent intent(Context c, String action) {
        return new Intent(c, RescueActivity.class).putExtra(EXTRA_ACTION, action);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = getIntent().getStringExtra(EXTRA_ACTION);
        if (ACTION_STOCK_HOME.equals(action)) {
            Apps.openStockHome(this);
            finish();
            return;
        }
        if (ACTION_RESET_HOME.equals(action)) {
            Apps.resetHomeChoice(this);
            finish();
            return;
        }
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background(this));
        root.addView(panel(this, null), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    /** The rescue buttons as a view; also used by MainActivity if building the home screen fails. */
    static View panel(final Activity a, Throwable error) {
        LinearLayout p = new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);

        TextView title = new TextView(a);
        title.setText(error == null ? "Аварийный выход" : "Главный экран не смог открыться");
        title.setTextColor(0xFFFFFFFF);
        Ui.textSize(title, 40);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        p.addView(title);

        if (error != null) {
            TextView msg = new TextView(a);
            msg.setText(String.valueOf(error));
            msg.setTextColor(0xB3FFFFFF);
            Ui.textSize(msg, 20);
            msg.setGravity(Gravity.CENTER);
            msg.setPadding(Ui.px(200), Ui.px(16), Ui.px(200), 0);
            p.addView(msg);
        }

        View first = button(a, p, "Штатный экран DEXP", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.openStockHome(a);
            }
        });
        button(a, p, "Сбросить выбор главного экрана", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.resetHomeChoice(a);
            }
        });
        button(a, p, "Удалить этот экран", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Apps.start(a, new Intent(Intent.ACTION_DELETE,
                        Uri.parse("package:" + a.getPackageName())));
            }
        });
        first.requestFocus();
        return p;
    }

    private static View button(Activity a, LinearLayout parent, String text, View.OnClickListener l) {
        TextView b = new TextView(a);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setGravity(Gravity.CENTER);
        Ui.textSize(b, 26);
        Ui.makeFocusable(b, 36, 1.05f);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(620), Ui.px(84));
        lp.topMargin = Ui.px(28);
        parent.addView(b, lp);
        return b;
    }
}
