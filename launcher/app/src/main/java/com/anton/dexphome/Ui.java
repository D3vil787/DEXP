package com.anton.dexphome;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.InputStream;

/** Shared look: sizes scaled from a 1920x1080 design, "glass" drawables, focus animation. */
final class Ui {
    private Ui() {}

    /** Screen width / 1920. */
    static float scale = 1f;
    static int screenW = 1920;
    static int screenH = 1080;

    static void init(Activity a) {
        a.requestWindowFeature(Window.FEATURE_NO_TITLE);
        a.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        scale = screenW / 1920f;
    }

    static int px(float designPx) {
        return Math.round(designPx * scale);
    }

    static void textSize(TextView t, float designPx) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, designPx * scale);
    }

    static Drawable glass(boolean focused, float designRadius) {
        float r = px(designRadius);
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR, focused
                ? new int[]{0x57FFFFFF, 0x24FFFFFF, 0x14FFFFFF}
                : new int[]{0x33FFFFFF, 0x12FFFFFF, 0x0AFFFFFF});
        base.setCornerRadius(r);
        base.setStroke(focused ? px(3) : Math.max(1, px(2)), focused ? 0xE6FFFFFF : 0x38FFFFFF);

        // Soft top highlight fading out towards the middle.
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{focused ? 0x33FFFFFF : 0x24FFFFFF, 0x08FFFFFF, 0x00FFFFFF});
        shine.setCornerRadius(r);

        return new LayerDrawable(new Drawable[]{base, shine});
    }

    static GradientDrawable rounded(int color, float designRadius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(designRadius));
        return d;
    }

    /** Glass background + gentle zoom on focus. Only transform is animated (cheap on old GPUs). */
    static void makeFocusable(final View v, final float designRadius, final float zoom) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(glass(false, designRadius));
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                view.setBackground(glass(hasFocus, designRadius));
                float s = hasFocus ? zoom : 1f;
                view.animate().scaleX(s).scaleY(s).setDuration(120).start();
            }
        });
    }

    /** Pre-rendered background picture; RGB_565 halves its memory on a 1 GB TV. */
    static Drawable background(Activity a) {
        try {
            AssetManager am = a.getAssets();
            InputStream in = am.open("bg.jpg");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            if (b != null) return new BitmapDrawable(a.getResources(), b);
        } catch (Exception ignored) {
        }
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF090B13);
        return d;
    }
}
