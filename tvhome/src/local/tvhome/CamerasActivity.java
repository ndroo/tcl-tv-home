package local.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.Map;

/**
 * The house's cameras, full screen (the camera site's /wall?house=..&tv=1, URL in cameras.conf), signed in with
 * this TV's device key.
 * Back on the wall offers the full camera site (every house) or leaving; on the site's other pages Back steps
 * back through them. Leaving stops every stream.
 */
public class CamerasActivity extends Activity {
    private static final String TAG = "tvhome-cams";
    private static final int ACCENT = 0xFF3B82F6;

    private WebView web;
    private FrameLayout root;
    private View menu;
    private String base, house, key;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Map<String, String> conf = Conf.read(this, "cameras.conf");
        base = conf.containsKey("URL") ? conf.get("URL").replaceAll("/+$", "") : "";
        house = conf.containsKey("HOUSE") ? conf.get("HOUSE") : "cottage";
        key = conf.get("KEY");

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(base)) {
            TextView t = new TextView(this);
            t.setText("Cameras aren't set up on this TV yet.\nPut the camera site's URL and this TV's device key in tvhome/cameras.conf\n(see cameras.conf.example) and reinstall TV Home.\n\nPress Back to return.");
            t.setTextColor(0xFFC9D1DC);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            t.setGravity(Gravity.CENTER);
            root.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }

        CookieManager.getInstance().setAcceptCookie(true);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " TVHome-Cameras/1.0");
        final String host = Uri.parse(base).getHost();
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !host.equals(r.getUrl().getHost());   // the camera site only
            }

            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) main.postDelayed(CamerasActivity.this::openWall, 10_000);   // offline: try again
            }

            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                // Android may end the page's process for memory on a 2 GB TV: start over rather than crash
                if (v == web) web = null;
                v.destroy();
                if (!isFinishing()) recreate();
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            // Android draws a big grey play icon over every video until it starts; show nothing instead
            @Override public Bitmap getDefaultVideoPoster() {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }

            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d(TAG, m.messageLevel() + " " + m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        openWall();
    }

    /** Every camera at the house, full screen. */
    private void openWall() {
        hideMenu();
        signIn("/wall?house=" + Uri.encode(house) + "&tv=1");
    }

    /** The camera site as usual, every house in its filter. */
    private void openSite() {
        hideMenu();
        signIn("/?house=all");
    }

    // The key rides in a header, never the URL; already signed in, the site just goes on to next.
    private void signIn(String next) {
        if (web == null) return;
        web.loadUrl(base + "/device/login?next=" + Uri.encode(next), Collections.singletonMap("Authorization", "Bearer " + key));
    }

    private boolean onWall() {
        String u = web == null ? null : web.getUrl();
        return u != null && u.contains("/wall") && u.contains("tv=1");
    }

    @Override public void onBackPressed() {
        if (menu != null) { hideMenu(); return; }
        if (web == null) { finish(); return; }
        if (onWall()) {   // the wall first: a camera filling the screen goes back to the grid
            web.evaluateJavascript("window.tvBack ? window.tvBack() : false", r -> { if (!"true".equals(r)) showMenu(); });
            return;
        }
        if (web.canGoBack()) { web.goBack(); return; }
        showMenu();
    }

    /** A modal over the dimmed picture: where to go from here. */
    private void showMenu() {
        boolean wall = onWall();
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setClickable(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(40), dp(30), dp(40), dp(32));
        GradientDrawable bg = rounded(0xF2151B24);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x26FFFFFF);
        card.setBackground(bg);
        card.setElevation(dp(16));

        TextView title = new TextView(this);
        title.setText("Cameras");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        TextView hint = new TextView(this);
        hint.setText(wall ? "Camera menu has every house, events and recordings" : "Back to the full-screen " + label(house) + " view");
        hint.setTextColor(0xFFA3AEBD);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, dp(24));
        card.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        Button first = button(wall ? "Camera menu" : label(house) + " cameras", wall ? v -> openSite() : v -> openWall());
        row.addView(first, buttonParams());
        row.addView(button("Exit", v -> finish()), buttonParams());
        card.addView(row);

        scrim.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        if (web != null) {   // the page behind keeps playing but can't take the remote's focus
            web.setFocusable(false);
            web.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        menu = scrim;
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        first.requestFocus();
    }

    private void hideMenu() {
        if (menu == null) return;
        root.removeView(menu);
        menu = null;
        if (web != null) {
            web.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            web.setFocusable(true);
            web.requestFocus();
        }
    }

    private Button button(String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(28), dp(14), dp(28), dp(14));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rounded(ACCENT));
        bg.addState(new int[0], rounded(0xFF2A323D));
        b.setBackground(bg);
        b.setStateListAnimator(null);
        b.setOnClickListener(click);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(210), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(8), 0, dp(8), 0);
        return lp;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        return g;
    }

    /** "cottage" -> "Cottage", "lake-house" -> "Lake House". */
    static String label(String slug) {
        StringBuilder out = new StringBuilder();
        for (String w : (slug == null ? "" : slug).split("[-_ ]+")) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    @Override protected void onStop() {
        super.onStop();
        CookieManager.getInstance().flush();
        finish();   // leaving (Home, an input, the screensaver) stops every stream
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
