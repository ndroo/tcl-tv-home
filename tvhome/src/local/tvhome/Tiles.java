package local.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The optional tiles at the foot of the home screen (Settings is always there). Each shows only when it's set up
 * (its .conf was bundled at build time) and not turned off in Settings -> Also on the home screen.
 */
final class Tiles {
    private Tiles() {}

    static final String WIFI = "wifi", CAMERAS = "cameras";
    private static final String PREFS = "tvhome", KEY = "hidden_tiles";

    /** wifi.conf names a network. */
    static boolean wifiReady(Context ctx) {
        return !TextUtils.isEmpty(Conf.read(ctx, "wifi.conf").get("SSID"));
    }

    /** cameras.conf has the camera site and this TV's key. */
    static boolean camerasReady(Context ctx) {
        Map<String, String> c = Conf.read(ctx, "cameras.conf");
        return !TextUtils.isEmpty(c.get("URL")) && !TextUtils.isEmpty(c.get("KEY"));
    }

    static boolean ready(Context ctx, String tile) {
        return WIFI.equals(tile) ? wifiReady(ctx) : CAMERAS.equals(tile) && camerasReady(ctx);
    }

    static boolean shown(Context ctx, String tile) {
        return ready(ctx, tile) && !hidden(ctx).contains(tile);
    }

    static Set<String> hidden(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, Collections.emptySet()));
    }

    static void setHidden(Context ctx, String tile, boolean hide) {
        Set<String> s = hidden(ctx);
        if (hide) s.add(tile); else s.remove(tile);
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putStringSet(KEY, s).apply();
    }
}
