package local.tvhome;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** KEY=value files bundled as assets (wifi.conf, cameras.conf); # starts a comment. Missing file = empty. */
final class Conf {
    private Conf() {}

    static Map<String, String> read(Context ctx, String asset) {
        Map<String, String> m = new HashMap<>();
        try (InputStream in = ctx.getAssets().open(asset);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || eq < 0) continue;
                m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (Exception ignored) {
            // not bundled: callers treat that as "not set up"
        }
        return m;
    }
}
