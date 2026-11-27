package local.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * TV Home's settings, laid out for a 960x540 dp screen: the title and "TV settings" on one line, then every
 * app as a banner card, four across, and every input, five across; OK shows or hides one on the home screen.
 */
public class SettingsActivity extends Activity {
    private static final int ACCENT = 0xFF3B82F6, TILE = 0xFF1C232C, TEXT = 0xFFFFFFFF, MUTED = 0xFFA3AEBD;
    private static final int COLS = 4, GAP = 18, SIDE = 56;
    private LinearLayout grid;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(SIDE), dp(34), dp(SIDE), dp(16));
        root.setClipChildren(false);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0A0D12, 0xFF151B24}));

        // title ............................................. [gear TV settings]
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Settings", 30, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout tv = new LinearLayout(this);
        tv.setOrientation(LinearLayout.HORIZONTAL);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(16), dp(8), dp(20), dp(8));
        focusable(tv, 14);
        ImageView gear = new ImageView(this);
        gear.setImageDrawable(new Icons(Icons.SETTINGS, TEXT));
        tv.addView(gear, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(14), 0, 0, 0);
        words.addView(text("TV settings", 18, TEXT, true));
        words.addView(text("Picture, sound, network, inputs", 13, 0xFFC9D1DC, false));
        tv.addView(words);
        tv.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (ActivityNotFoundException ignored) {}
        });
        head.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(60)));
        root.addView(head);

        ScrollView scroll = new ScrollView(this);   // scrolls down to the inputs
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        fill();
    }

    private void fill() {
        grid.removeAllViews();
        grid.addView(sectionHead("APPS ON THE HOME SCREEN", "OK shows or hides one. Hidden apps stay installed and keep working, casting included."));
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = Apps.all(this);
        int width = getResources().getDisplayMetrics().widthPixels;
        int cardW = (width - dp(SIDE) * 2 - dp(GAP) * (COLS - 1)) / COLS;
        int bannerH = cardW * 9 / 16;
        LinearLayout row = null;
        View first = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rl.topMargin = dp(GAP);
                grid.addView(row, rl);
            }
            View card = card(pm, apps.get(i), bannerH);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % COLS > 0) cl.leftMargin = dp(GAP);
            row.addView(card, cl);
            if (first == null) first = card;
        }

        List<Inputs.Input> inputs = Inputs.all(this);
        if (!inputs.isEmpty()) {
            grid.addView(sectionHead("INPUTS ON THE HOME SCREEN", "OK shows or hides one."));
            int cols = 5, inW = (width - dp(SIDE) * 2 - dp(GAP) * (cols - 1)) / cols;
            for (int i = 0; i < inputs.size(); i++) {
                if (i % cols == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setClipChildren(false);
                    LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 0) rl.topMargin = dp(GAP);
                    rl.bottomMargin = dp(12);
                    grid.addView(row, rl);
                }
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(inW, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i % cols > 0) cl.leftMargin = dp(GAP);
                row.addView(inputCard(inputs.get(i)), cl);
            }
        }
        if (first != null && getCurrentFocus() == null) first.requestFocus();
    }

    private View sectionHead(String title, String hintText) {
        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.HORIZONTAL);
        sec.setGravity(Gravity.BOTTOM);
        sec.setPadding(0, dp(24), 0, dp(12));
        TextView label = text(title, 13, MUTED, true);
        label.setLetterSpacing(0.12f);
        sec.addView(label);
        TextView hint = text(hintText, 13, 0xFF7C8796, false);
        hint.setPadding(dp(16), 0, 0, 0);
        sec.addView(hint);
        return sec;
    }

    /** An input: its name, its port (and note) under it, and a Shown / Hidden badge; OK flips it. */
    private View inputCard(Inputs.Input in) {
        String id = in.info.getId();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(12), dp(12));
        focusable(card, 14);
        TextView name = text(in.label, 18, TEXT, true);
        card.addView(name);
        String sub = in.port != null ? in.port + (TextUtils.isEmpty(in.detail) ? "" : " \u00b7 " + in.detail)
                : (TextUtils.isEmpty(in.detail) ? "Input" : in.detail);
        TextView subText = text(sub, 13, 0xFFC9D1DC, false);
        card.addView(subText);
        TextView badge = text("", 13, TEXT, true);
        badge.setPadding(dp(10), dp(3), dp(10), dp(3));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(10);
        card.addView(badge, bl);
        Runnable draw = () -> {
            boolean h = Inputs.hidden(this).contains(id);
            badge.setText(h ? "Hidden" : "\u25cf Shown");
            badge.setTextColor(h ? MUTED : 0xFF4ADE80);
            badge.setBackground(rounded(h ? 0x26FFFFFF : 0x2E4ADE80, 99));
            name.setAlpha(h ? 0.5f : 1f);
            subText.setAlpha(h ? 0.5f : 1f);
        };
        draw.run();
        card.setOnClickListener(v -> { Inputs.setHidden(this, id, !Inputs.hidden(this).contains(id)); draw.run(); });
        card.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(110).start());
        return card;
    }

    /** A banner with the app's name and a Shown / Hidden badge under it; OK flips it. */
    private View card(PackageManager pm, ResolveInfo r, int bannerH) {
        ActivityInfo ai = r.activityInfo;
        String pkg = ai.packageName;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(8));
        focusable(card, 16);

        FrameLayout pic = new FrameLayout(this);
        ImageView banner = new ImageView(this);
        Drawable d = ai.loadBanner(pm);
        if (d == null) d = ai.applicationInfo.loadBanner(pm);
        banner.setImageDrawable(d != null ? d : ai.loadIcon(pm));
        banner.setScaleType(d != null ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
        banner.setBackground(rounded(0xFF0E1218, 10));
        banner.setClipToOutline(true);
        pic.addView(banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bannerH));
        card.addView(pic);

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(dp(4), dp(8), 0, 0);
        TextView name = text(r.loadLabel(pm).toString(), 16, TEXT, true);
        foot.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView badge = text("", 13, TEXT, true);
        badge.setPadding(dp(10), dp(3), dp(10), dp(3));
        foot.addView(badge);
        card.addView(foot);

        Runnable draw = () -> {
            boolean h = Apps.hidden(this).contains(pkg);
            badge.setText(h ? "Hidden" : "● Shown");
            badge.setTextColor(h ? MUTED : 0xFF4ADE80);
            badge.setBackground(rounded(h ? 0x26FFFFFF : 0x2E4ADE80, 99));
            banner.setAlpha(h ? 0.3f : 1f);
            name.setAlpha(h ? 0.5f : 1f);
        };
        draw.run();
        card.setOnClickListener(v -> { Apps.setHidden(this, pkg, !Apps.hidden(this).contains(pkg)); draw.run(); });
        card.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(110).start());
        return card;
    }

    private void focusable(View v, int radius) {
        v.setFocusable(true);
        v.setClickable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rounded(ACCENT, radius));
        bg.addState(new int[0], rounded(TILE, radius));
        v.setBackground(bg);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
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
