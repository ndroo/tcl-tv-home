package local.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The TV apps TV Home lists, in its order, and which are hidden (Settings -> Apps on the home screen). */
final class Apps {
    private Apps() {}

    static final List<String> ORDER = Arrays.asList(
            "fm.triplec.tv",  // our own station, first
            "com.plexapp.android",
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
            "com.google.android.youtube.tvmusic", "com.android.vending", "com.tcl.ui_mediaCenter");
    // not apps to list: this home screen, live TV (the inputs row), TCL's old-launcher add-on, Android settings
    private static final Set<String> NEVER = new HashSet<>(Arrays.asList(
            "local.tvhome", "com.tcl.tv", "com.tcl.partnercustomizer", "com.android.tv.settings"));
    // hidden until shown in Settings (asked for 2026-11-26): Netflix, Prime Video, TCL's USB Media Center
    private static final Set<String> FIRST_HIDDEN = new HashSet<>(Arrays.asList(
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.tcl.ui_mediaCenter"));
    // v2: the first test of the toggles flipped Netflix and Prime back on; start again from FIRST_HIDDEN
    private static final String PREFS = "tvhome", KEY = "hidden_apps_v2";

    /** Every launchable TV app (one entry per app), in TV Home's order. */
    static List<ResolveInfo> all(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        List<ResolveInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo r : found) {
            String p = r.activityInfo.packageName;
            if (!NEVER.contains(p) && seen.add(p)) apps.add(r);
        }
        Collections.sort(apps, (a, b) -> {
            int ia = ORDER.indexOf(a.activityInfo.packageName), ib = ORDER.indexOf(b.activityInfo.packageName);
            if (ia < 0) ia = 99;
            if (ib < 0) ib = 99;
            if (ia != ib) return Integer.compare(ia, ib);
            return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
        });
        return apps;
    }

    static Set<String> hidden(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, FIRST_HIDDEN));
    }

    static void setHidden(Context ctx, String pkg, boolean hide) {
        Set<String> s = hidden(ctx);
        if (hide) s.add(pkg); else s.remove(pkg);
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putStringSet(KEY, s).apply();
    }
}
