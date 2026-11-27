package fm.triplec.tv;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Triple C on the TV: triplec.fm's own Chromecast receiver page, full screen, playing the station itself.
 * The page is loaded unchanged; Google's cast framework script is swapped for assets/caf_shim.js, which plays
 * the stream the way a phone's cast would. Leaving the app stops the radio.
 */
public class PlayerActivity extends Activity {
    private static final String PAGE = "https://triplec.fm/cast-receiver.html";
    // What the site's own sender asks a Chromecast to play (web/public/cast.js: burst 30, hold = CUSHION_S 900).
    private static final String STREAM = "https://stream.triplec.fm/stream.mp3?cast=1&burst=30&hold=900";
    private static final String CAF = "https://www.gstatic.com/cast/sdk/libs/caf_receiver/";
    private static final String[] LANGS = {"en", "fr", "es", "zh", "pa"};

    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0A0A0D);
        web.setFocusable(false);  // the page has nothing to click; the remote's keys come to the activity
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " TripleC-AndroidTV/1.0");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (r.getUrl().toString().startsWith(CAF)) {
                    return new WebResourceResponse("application/javascript", "utf-8", shim());
                }
                return null;
            }

            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true;  // stay on the receiver page
            }

            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) main.postDelayed(PlayerActivity.this::loadPage, 10_000);  // offline: try again
            }

            // On a 2 GB TV Android may kill the page's process for memory. By default that takes the app down too;
            // instead start over with a fresh page (unless we're on the way out anyway).
            @Override public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                if (v == web) web = null;
                v.destroy();
                if (!isFinishing()) recreate();
                return true;
            }
        });
        setContentView(web);
        loadPage();
    }

    private void loadPage() {
        if (web == null) return;
        String lang = Locale.getDefault().getLanguage();
        boolean known = false;
        for (String l : LANGS) known |= l.equals(lang);
        web.loadUrl(Uri.parse(PAGE).buildUpon().appendQueryParameter("lang", known ? lang : "en").build().toString());
    }

    private InputStream shim() {
        try (InputStream in = getAssets().open("caf_shim.js")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            String js = new String(out.toByteArray(), StandardCharsets.UTF_8).replace("__STREAM_URL__", STREAM);
            return new ByteArrayInputStream(js.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private void js(String call) {
        if (web != null) web.evaluateJavascript("window.__tcTv && window.__tcTv." + call, null);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean toggle = k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_DPAD_CENTER
                || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_HEADSETHOOK;
        boolean media = toggle || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE
                || k == KeyEvent.KEYCODE_MEDIA_STOP;
        // Left/right and the volume buttons: the stream's own volume (over optical the TV's volume does nothing). Held, it
        // keeps going.
        int vol = k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_VOLUME_UP ? 1
                : k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_VOLUME_DOWN ? -1 : 0;
        if (vol != 0 || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) js(vol != 0 ? "volume(" + vol + ")" : (e.getRepeatCount() == 0 ? "mute()" : "state()"));
            return true;
        }
        if (!media) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            if (toggle) js("toggle()");
            else if (k == KeyEvent.KEYCODE_MEDIA_PLAY) js("play()");
            else if (k == KeyEvent.KEYCODE_MEDIA_PAUSE) js("pause()");
            else finish();
        }
        return true;
    }

    @Override protected void onStop() {
        super.onStop();
        js("stop()");
        finish();  // a radio on a TV: switching to an input or going Home stops it
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
