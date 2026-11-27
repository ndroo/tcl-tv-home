package local.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A deliberately tiny home screen: inputs, the apps you cast to, settings, a clock.
 * No ads, no recommendation rows, no background work.
 */
public class HomeActivity extends Activity {
    private static final String LIVE_TV = "com.tcl.tv";
    private static final String CAST = "com.google.android.apps.mediashell";
    // app tiles are 16:9 and share the row's width when they fit (fewer apps, bigger tiles); else 132 dp and it scrolls
    private static final int APP_TILE_MIN = 132, APP_TILE_MAX = 200;

    private static final int ACCENT = 0xFF3B82F6;
    private static final int TILE = 0xFF1C232C;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA3AEBD;

    private TvInputManager tvm;
    private LinearLayout inputsRow;
    private View inputsTitle;
    private LinearLayout bottomRow;
    private LinearLayout appsRow;
    private HorizontalScrollView appsScroll;
    private TextView castLine;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override public void onInputStateChanged(String id, int state) { refreshInputs(); }
        @Override public void onInputAdded(String id) { refreshInputs(); }
        @Override public void onInputRemoved(String id) { refreshInputs(); }
        @Override public void onInputUpdated(String id) { refreshInputs(); }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        tvm = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        setContentView(buildLayout());
        if (tvm != null) tvm.registerCallback(inputCallback, main);
    }

    @Override protected void onDestroy() {
        if (tvm != null) tvm.unregisterCallback(inputCallback);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshCastLine();
        refreshInputs();
        refreshApps();
        refreshBottom();
        focusFirst();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        focusFirst();  // Home pressed while already home: back to the top left
    }

    @Override public void onBackPressed() {
        // This is the home screen; there is nothing behind it.
    }

    // ---------------------------------------------------------------- layout

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(36), dp(48), dp(28));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A0D12, 0xFF151B24}));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(deviceName(), 30, TEXT, true));
        castLine = text("", 15, MUTED, false);
        titles.addView(castLine);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(TEXT);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        header.addView(clock);
        root.addView(header);

        root.addView(section("Apps"));
        // A scrolling row of 16:9 tiles: as many apps as there are, banners uncropped; the remote scrolls it.
        appsRow = row();
        HorizontalScrollView apps = new HorizontalScrollView(this) {
            // Scroll a focused tile clear of the side margins, not just onto the screen (which left the last one
            // touching the edge).
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
                android.graphics.Rect r = new android.graphics.Rect(rect);
                r.left -= getPaddingLeft() + dp(8);   // + the tile's own margin, so the ends sit where they started
                r.right += getPaddingRight() + dp(8);
                return super.computeScrollDeltaToGetChildRectOnScreen(r);
            }
        };
        apps.setHorizontalScrollBarEnabled(false);
        apps.setClipChildren(false);
        apps.setClipToPadding(false);
        apps.setOverScrollMode(View.OVER_SCROLL_NEVER);
        apps.addView(appsRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // Full-bleed: the row runs to the screen's edges (tiles slide off them when scrolled) but starts in line
        // with everything else.
        apps.setPadding(dp(48), 0, dp(48), 0);
        appsRow.setPadding(0, 0, dp(48), 0);  // room to scroll the last tile in line with the right margin
        LinearLayout.LayoutParams appsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        appsLp.setMargins(-dp(48), 0, -dp(48), 0);
        root.addView(apps, appsLp);
        appsScroll = apps;

        inputsTitle = section("Inputs");
        root.addView(inputsTitle);
        inputsRow = row();
        root.addView(inputsRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));

        root.addView(new View(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottomRow = row();
        root.addView(bottomRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));
        return root;
    }

    /** Wi-Fi and Cameras when they're set up and on (Settings), then Settings, which is always there. Each a third
     *  of the row, from the left, so fewer don't stretch. */
    private void refreshBottom() {
        if (bottomRow == null) return;
        bottomRow.removeAllViews();
        int room = Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 96;
        int w = room / 3 - 16;
        if (Tiles.shown(this, Tiles.WIFI)) {
            View wifi = iconTile(Icons.WIFI, "Get the Wi-Fi", null);
            wifi.setOnClickListener(v -> startActivity(new Intent(this, WifiActivity.class)));
            bottomRow.addView(wifi, tileParams(w));
        }
        if (Tiles.shown(this, Tiles.CAMERAS)) {
            View cams = iconTile(Icons.CAMERA, "Cameras", null);
            cams.setOnClickListener(v -> startActivity(new Intent(this, CamerasActivity.class)));
            bottomRow.addView(cams, tileParams(w));
        }
        View settings = iconTile(Icons.SETTINGS, "Settings", null);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bottomRow.addView(settings, tileParams(w));
    }

    // ---------------------------------------------------------------- inputs

    private void refreshInputs() {
        if (inputsRow == null) return;
        View focused = getCurrentFocus();
        Object focusedTag = focused != null ? focused.getTag() : null;
        inputsRow.removeAllViews();
        Set<String> hidden = Inputs.hidden(this);   // Settings -> Inputs on the home screen
        List<Inputs.Input> shown = new ArrayList<>();
        for (Inputs.Input in : Inputs.all(this)) if (!hidden.contains(in.info.getId())) shown.add(in);
        // Tiles keep the size five would have, so two shown don't stretch across the screen
        int room = Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 96;
        int w = room / Math.max(5, shown.size()) - 16;
        inputsTitle.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        inputsRow.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        for (Inputs.Input in : shown) {
            boolean live = in.state != TvInputManager.INPUT_STATE_DISCONNECTED;
            View t = labelTile(in.label, in.detail, in.port, live);
            t.setTag(in.info.getId());
            t.setOnClickListener(v -> openInput(in.info));
            inputsRow.addView(t, tileParams(w));
            if (in.info.getId().equals(focusedTag)) t.requestFocus();
        }
    }

    private void openInput(TvInputInfo i) {
        PackageManager pm = getPackageManager();
        Intent liveTv = pm.getLeanbackLaunchIntentForPackage(LIVE_TV);
        if (i.isPassthroughInput()) {
            Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(i.getId()));
            if (launch(view)) return;
        } else {
            // The tuner: just opening the TV app shows the last source (AV, say). "Show this input's channels" makes it
            // switch to the tuner (and with none scanned, TCL's TV app offers a scan).
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelsUriForInput(i.getId()), TvContract.Channels.CONTENT_TYPE)
                    .setPackage(LIVE_TV);
            if (view.resolveActivity(pm) != null && launch(view)) return;
        }
        launch(liveTv);
    }

    /** The top-left tile: the first app (the row scrolled back to its start), or the first input if no apps show. */
    private void focusFirst() {
        main.post(() -> {
            View target = appsRow != null && appsRow.getChildCount() > 0 ? appsRow.getChildAt(0)
                    : inputsRow != null && inputsRow.getChildCount() > 0 ? inputsRow.getChildAt(0) : null;
            if (appsScroll != null) appsScroll.scrollTo(0, 0);
            if (target != null) target.requestFocus();
        });
    }

    // ---------------------------------------------------------------- apps + cast

    private void refreshApps() {
        if (appsRow == null) return;
        appsRow.removeAllViews();
        PackageManager pm = getPackageManager();
        Set<String> hidden = Apps.hidden(this);   // Settings -> Apps on the home screen
        List<ResolveInfo> shown = new ArrayList<>();
        for (ResolveInfo r : Apps.all(this)) if (!hidden.contains(r.activityInfo.packageName)) shown.add(r);
        int room = Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) - 96;
        int w = shown.isEmpty() ? APP_TILE_MAX : Math.min(APP_TILE_MAX, room / shown.size() - 16);
        if (w < APP_TILE_MIN) w = APP_TILE_MIN;
        appsScroll.getLayoutParams().height = dp(Math.round(w * 9f / 16f) + 12);
        appsScroll.requestLayout();
        for (ResolveInfo r : shown) {
            ActivityInfo ai = r.activityInfo;
            CharSequence label = r.loadLabel(pm);
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            View t = banner != null ? bannerTile(banner, label) : labelTile(label.toString(), "", null, true);
            Intent open = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(ai.packageName, ai.name);
            t.setOnClickListener(v -> launch(open));
            appsRow.addView(t, tileParams(w));
        }
    }

    private void refreshCastLine() {
        boolean on;
        try {
            on = getPackageManager().getApplicationInfo(CAST, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            on = false;
        }
        castLine.setText(on ? "●  Ready to cast" : "●  Chromecast is turned off");
        castLine.setTextColor(on ? 0xFF4ADE80 : 0xFFFBBF24);
    }

    private String deviceName() {
        String n = Settings.Global.getString(getContentResolver(), "device_name");
        return TextUtils.isEmpty(n) ? "TV" : n;
    }

    private boolean launch(Intent i) {
        if (i == null) return false;
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Couldn't open that", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    // ---------------------------------------------------------------- views

    private View labelTile(String title, String detail, String corner, boolean live) {
        FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackground(states(rounded(ACCENT, 14), rounded(TILE, 14)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(20), dp(10), dp(14), dp(10));
        // A long name ("Fireplace TV") shrinks to fit the tile rather than being cut off
        TextView name = text(title, 22, TEXT, true);
        name.setSingleLine(false);
        name.setMaxLines(1);
        name.setAutoSizeTextTypeUniformWithConfiguration(15, 22, 1, TypedValue.COMPLEX_UNIT_SP);
        t.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        if (!TextUtils.isEmpty(detail)) t.addView(text(detail, 13, 0xFFC9D1DC, false));
        tile.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!TextUtils.isEmpty(corner)) {   // the port, small, top right ("HDMI 2")
            TextView c = text(corner.toUpperCase(java.util.Locale.ROOT), 10, 0xB3C9D1DC, true);
            c.setLetterSpacing(0.1f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
            lp.setMargins(0, dp(9), dp(12), 0);
            tile.addView(c, lp);
        }
        grow(tile, live ? 1f : 0.45f);
        return tile;
    }

    private View iconTile(int icon, String title, String detail) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(18), dp(10), dp(12), dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(states(rounded(ACCENT, 14), rounded(TILE, 14)));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Icons(icon, TEXT));
        t.addView(iv, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(14), 0, 0, 0);
        words.addView(text(title, 20, TEXT, true));
        if (!TextUtils.isEmpty(detail)) words.addView(text(detail, 13, 0xFFC9D1DC, false));
        t.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        grow(t, 1f);
        return t;
    }

    private View bannerTile(Drawable banner, CharSequence label) {
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(dp(3), dp(3), dp(3), dp(3));
        frame.setFocusable(true);
        frame.setClickable(true);
        frame.setContentDescription(label);
        frame.setBackground(states(rounded(ACCENT, 15), new ColorDrawable(0)));
        ImageView img = new ImageView(this);
        img.setImageDrawable(banner);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackground(rounded(TILE, 12));
        img.setClipToOutline(true);
        frame.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        grow(frame, 1f);
        return frame;
    }

    private void grow(View v, float restingAlpha) {
        v.setAlpha(restingAlpha);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f)
                    .alpha(has ? 1f : restingAlpha).setDuration(120).start();
            view.setElevation(has ? dp(8) : 0);
        });
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }

    private LinearLayout.LayoutParams tileParams(int widthDp) {
        LinearLayout.LayoutParams lp = widthDp > 0
                ? new LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.MATCH_PARENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(dp(8), dp(6), dp(8), dp(6));
        return lp;
    }

    private TextView section(String s) {
        TextView t = text(s.toUpperCase(), 13, MUTED, true);
        t.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(8), dp(26), 0, dp(6));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private static StateListDrawable states(Drawable focused, Drawable normal) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[0], normal);
        return s;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
