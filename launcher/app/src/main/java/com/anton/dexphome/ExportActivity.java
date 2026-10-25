package com.anton.dexphome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Copies the stock launcher, Konka/MStar TV apps and MStar framework jars to a USB stick,
 * so they can be examined on a PC (e.g. to see how the stock launcher switches to HDMI).
 */
public class ExportActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background(this));
        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        Ui.textSize(status, 26);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.px(160), 0, Ui.px(160), 0);
        status.setText("Копирую на флешку…");
        root.addView(status, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String result = export();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status.setText(result);
                    }
                });
            }
        }).start();
    }

    private String export() {
        File usb = findUsb();
        boolean onUsb = usb != null;
        // USB sticks may not be writable for ordinary apps on 4.2; fall back to internal storage.
        File base = onUsb ? usb : Environment.getExternalStorageDirectory();
        File out = new File(base, "dexp-export");
        out.mkdirs();
        if (!out.canWrite()) return "Не удалось записать ни на флешку, ни во внутреннюю память: " + out;

        PackageManager pm = getPackageManager();
        Set<String> pkgs = new LinkedHashSet<String>();
        // Stock launcher(s).
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            if (!ri.activityInfo.packageName.equals(getPackageName())) pkgs.add(ri.activityInfo.packageName);
        }
        for (PackageInfo p : pm.getInstalledPackages(0)) {
            String n = p.packageName;
            if (n.startsWith("com.konka") || n.startsWith("com.mstar") || n.contains("tv")) pkgs.add(n);
        }

        StringBuilder report = new StringBuilder();
        int ok = 0;
        for (String pkg : pkgs) {
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
                ApplicationInfo ai = pi.applicationInfo;
                report.append(pkg).append("  v").append(pi.versionName)
                        .append("  ").append(ai.sourceDir).append('\n');
                if (pi.activities != null) {
                    for (ActivityInfo a : pi.activities) {
                        report.append("    ").append(a.exported ? "+ " : "  ").append(a.name).append('\n');
                    }
                }
                if (copy(new File(ai.sourceDir), new File(out, pkg + ".apk"))) ok++;
                File odex = new File(ai.sourceDir.replaceAll("\\.apk$", ".odex"));
                if (odex.exists()) copy(odex, new File(out, pkg + ".odex"));
            } catch (Exception e) {
                report.append(pkg).append("  ERROR ").append(e).append('\n');
            }
        }

        // MStar / Konka framework libraries: the TV source-switching API lives there.
        File fw = new File("/system/framework");
        File[] jars = fw.listFiles();
        List<String> fwCopied = new ArrayList<String>();
        if (jars != null) {
            for (File j : jars) {
                String n = j.getName().toLowerCase();
                if ((n.contains("mstar") || n.contains("konka") || n.contains("tv"))
                        && (n.endsWith(".jar") || n.endsWith(".odex"))) {
                    if (copy(j, new File(out, "framework_" + j.getName()))) fwCopied.add(j.getName());
                }
            }
        }
        report.append("\nframework: ").append(fwCopied).append('\n');

        try {
            FileWriter w = new FileWriter(new File(out, "packages.txt"));
            w.write(report.toString());
            w.close();
        } catch (Exception ignored) {
        }
        String where = "Готово: " + ok + " приложений и " + fwCopied.size() + " библиотек\nв папке "
                + out.getAbsolutePath();
        return onUsb ? where + "\n\nМожно вынимать флешку."
                : where + "\n\nФлешка недоступна для записи, поэтому файлы во внутренней памяти.\n"
                + "Скопируйте папку dexp-export на флешку через «Менеджер файлов».";
    }

    /** First writable USB mount; MStar TVs usually mount sticks under /mnt/usb. */
    private File findUsb() {
        List<File> candidates = new ArrayList<File>();
        for (String base : new String[]{"/mnt/usb", "/mnt/usbdrive", "/storage", "/mnt"}) {
            File[] kids = new File(base).listFiles();
            if (kids == null) continue;
            for (File k : kids) {
                String n = k.getName().toLowerCase();
                if (n.startsWith("sd") || n.contains("usb") || n.contains("udisk")) candidates.add(k);
            }
        }
        for (File c : candidates) {
            if (c.isDirectory() && c.canWrite()
                    && !c.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                return c;
            }
        }
        return null;
    }

    private static boolean copy(File from, File to) {
        try {
            InputStream in = new FileInputStream(from);
            OutputStream os = new FileOutputStream(to);
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) os.write(b, 0, n);
            in.close();
            os.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
