package com.anton.dexphome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds launchable apps on the TV. */
final class Apps {
    private Apps() {}

    static final class Entry {
        String label;
        String pkg;
        Drawable icon;
        Intent intent;
    }

    private static final String LEANBACK = "android.intent.category.LEANBACK_LAUNCHER";

    static List<Entry> launchables(Context c) {
        PackageManager pm = c.getPackageManager();
        List<Entry> out = new ArrayList<Entry>();
        Set<String> seen = new HashSet<String>();
        String self = c.getPackageName();
        for (String cat : new String[]{Intent.CATEGORY_LAUNCHER, LEANBACK}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(self) || !seen.add(pkg)) continue;
                Entry e = new Entry();
                e.pkg = pkg;
                CharSequence l = ri.loadLabel(pm);
                e.label = l == null ? pkg : l.toString();
                e.icon = ri.loadIcon(pm);
                e.intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                out.add(e);
            }
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    /** First match by package (in the given order), then by a label keyword. */
    static Entry find(List<Entry> apps, String[] pkgs, String[] labelKeywords, boolean exactLabel) {
        if (pkgs != null) {
            for (String p : pkgs) {
                for (Entry e : apps) if (e.pkg.equals(p)) return e;
            }
        }
        if (labelKeywords != null) {
            for (String k : labelKeywords) {
                String kk = k.toLowerCase(Locale.getDefault());
                for (Entry e : apps) {
                    String l = e.label.toLowerCase(Locale.getDefault()).trim();
                    if (exactLabel ? l.equals(kk) : l.contains(kk)) return e;
                }
            }
        }
        return null;
    }

    static void start(Context c, Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(c, "Не удалось открыть", Toast.LENGTH_SHORT).show();
        } catch (SecurityException ex) {
            Toast.makeText(c, "Нет доступа", Toast.LENGTH_SHORT).show();
        }
    }
}
