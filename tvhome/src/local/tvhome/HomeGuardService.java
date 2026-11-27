package local.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayDeque;

/**
 * Puts TV Home on top whenever Google TV's own home screen comes to the front.
 *
 * On a Chromecast with Google TV the real home screen cannot be replaced (Google's launcher outranks every
 * ordinary app, and the setup wizard's recovery screen is the only fallback), so instead of replacing it this
 * service reacts to it: it is told only about window changes of the one package listed in
 * res/xml/home_guard_config.xml (it reads no screen content), and when that package's window appears while TV
 * Home is not already showing, it opens TV Home on top.
 *
 * Safe to switch off at any time: `settings put secure enabled_accessibility_services ""` (or uninstall).
 * If it ever starts looping it backs off for a minute.
 */
public class HomeGuardService extends AccessibilityService {
    private static final String TAG = "TVHomeGuard";
    private static final String STOCK_HOME = "com.google.android.apps.tv.launcherx";
    private static final long DEBOUNCE_MS = 1500;     // ignore the stock home's trailing events
    private static final long LOOP_WINDOW_MS = 60_000;
    private static final int LOOP_MAX = 8;            // more launches than this in a minute = something is wrong

    /** Set by HomeActivity: true while TV Home is on screen. */
    static volatile boolean homeVisible;
    /** Elapsed-realtime time before which the guard stays out of the way (an "open Google TV home" escape). */
    static volatile long pausedUntil;

    private long lastLaunch;
    private final ArrayDeque<Long> recent = new ArrayDeque<>();

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !STOCK_HOME.contentEquals(pkg)) return;

        long now = SystemClock.elapsedRealtime();
        if (homeVisible || now < pausedUntil || now - lastLaunch < DEBOUNCE_MS) return;

        while (!recent.isEmpty() && now - recent.peekFirst() > LOOP_WINDOW_MS) recent.pollFirst();
        if (recent.size() >= LOOP_MAX) {
            Log.w(TAG, "too many launches in a minute; standing down for a minute");
            pausedUntil = now + LOOP_WINDOW_MS;
            recent.clear();
            return;
        }
        lastLaunch = now;
        recent.addLast(now);
        try {
            startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.i(TAG, "stock home came to the front; opened TV Home");
        } catch (RuntimeException e) {
            Log.w(TAG, "could not open TV Home: " + e);
        }
    }

    @Override public void onInterrupt() {}

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "home guard connected");
    }
}
