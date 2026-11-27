package local.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
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
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.Map;

/**
 * The house's cameras, full screen (the camera site's /wall?house=..&tv=1, URL in cameras.conf), signed in with
 * this TV's device key. The column on the left of the page switches property.
 * Back: a camera filling the screen goes back to the grid (the page handles that); on the grid it leaves, no
 * questions asked. Leaving stops every stream.
 */
public class CamerasActivity extends Activity {
    private static final String TAG = "tvhome-cams";

    private WebView web;
    private String base, house, key;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Map<String, String> conf = Conf.read(this, "cameras.conf");
        base = conf.containsKey("URL") ? conf.get("URL").replaceAll("/+$", "") : "";
        house = conf.containsKey("HOUSE") ? conf.get("HOUSE") : "cottage";
        key = conf.get("KEY");

        FrameLayout root = new FrameLayout(this);
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

    /** Every camera at the house, full screen. The key rides in a header, never the URL; already signed in, the
     *  site just goes on to the wall. */
    private void openWall() {
        if (web == null) return;
        String next = "/wall?house=" + Uri.encode(house) + "&tv=1";
        web.loadUrl(base + "/device/login?next=" + Uri.encode(next), Collections.singletonMap("Authorization", "Bearer " + key));
    }

    @Override public void onBackPressed() {
        if (web == null) { finish(); return; }
        // The page first: a camera filling the screen goes back to the grid. Anything else leaves.
        web.evaluateJavascript("window.tvBack ? window.tvBack() : false", r -> { if (!"true".equals(r)) finish(); });
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
}
