package local.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Map;

/** Full-screen "join the Wi-Fi" page: QR code plus the network name and password. */
public class WifiActivity extends Activity {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA3AEBD;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Map<String, String> conf = readConf();
        Bitmap qr = readQr();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(64), dp(40), dp(64), dp(40));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A0D12, 0xFF151B24}));

        if (qr != null) {
            FrameLayout card = new FrameLayout(this);
            GradientDrawable white = new GradientDrawable();
            white.setColor(0xFFFFFFFF);
            white.setCornerRadius(dp(24));
            card.setBackground(white);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            ImageView img = new ImageView(this);
            BitmapDrawable d = new BitmapDrawable(getResources(), qr);
            d.setFilterBitmap(false);  // keep the modules sharp when scaled
            img.setImageDrawable(d);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(img, new FrameLayout.LayoutParams(dp(380), dp(380)));
            root.addView(card);
        }

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(56), 0, 0, 0);
        info.addView(text("Join the Wi-Fi", 40, TEXT, Typeface.DEFAULT_BOLD));
        info.addView(text("Point your phone’s camera at the code", 18, MUTED, Typeface.DEFAULT));
        String ssid = conf.get("SSID");
        String password = conf.get("PASSWORD");
        if (TextUtils.isEmpty(ssid)) {
            info.addView(gap(32));
            info.addView(text("No Wi-Fi details set up (tvhome/wifi.conf)", 20, MUTED, Typeface.DEFAULT));
        } else {
            info.addView(gap(36));
            info.addView(label("Network"));
            info.addView(text(ssid, 36, TEXT, Typeface.DEFAULT_BOLD));
            if (!TextUtils.isEmpty(password) && !"nopass".equals(conf.get("SECURITY"))) {
                info.addView(gap(22));
                info.addView(label("Password"));
                info.addView(text(password, 36, TEXT, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)));
            }
        }
        info.addView(gap(40));
        info.addView(text("Press Back to return", 15, MUTED, Typeface.DEFAULT));
        root.addView(info);
        setContentView(root);
    }

    private Map<String, String> readConf() {
        return Conf.read(this, "wifi.conf");
    }

    private Bitmap readQr() {
        try (InputStream in = getAssets().open("wifi_qr.png")) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private TextView label(String s) {
        TextView t = text(s.toUpperCase(), 13, MUTED, Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private TextView text(String s, int sp, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        return t;
    }

    private View gap(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
