package local.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Line icons drawn in code (a 24-unit grid, 2-unit round strokes), so the app needs no image files. */
final class Icons extends Drawable {
    static final int WIFI = 0, CAMERA = 1, SETTINGS = 2;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Icons(int kind, int color) {
        this.kind = kind;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2, b.top + (b.height() - 24 * s) / 2);
        c.scale(s, s);
        switch (kind) {
            case WIFI:     // three arcs fanning up from a dot
                for (int i = 0; i < 3; i++) {
                    float r = 4 + i * 4.5f;
                    c.drawArc(new RectF(12 - r, 19 - r, 12 + r, 19 + r), 225, 90, false, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 19, 1.6f, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case CAMERA:   // the camera site's own mark: a body and a lens cone
                Path body = new Path();
                body.addRoundRect(new RectF(2.5f, 7, 15.5f, 17.5f), 2, 2, Path.Direction.CW);
                c.drawPath(body, paint);
                Path cone = new Path();
                cone.moveTo(15.5f, 10.5f); cone.lineTo(21.5f, 7.5f); cone.lineTo(21.5f, 17f); cone.lineTo(15.5f, 14f);
                c.drawPath(cone, paint);
                break;
            case SETTINGS: // an eight-tooth gear around a hub
                Path gear = new Path();
                int teeth = 8;
                for (int i = 0; i < teeth; i++) {
                    double a = 2 * Math.PI * i / teeth, w = Math.PI / teeth;
                    point(gear, i == 0, a - w * 0.95, 7.2);
                    point(gear, false, a - w * 0.5, 10);
                    point(gear, false, a + w * 0.5, 10);
                    point(gear, false, a + w * 0.95, 7.2);
                }
                gear.close();
                c.drawPath(gear, paint);
                c.drawCircle(12, 12, 3, paint);
                break;
            default:
                break;
        }
        c.restore();
    }

    private static void point(Path p, boolean first, double angle, double r) {
        float x = (float) (12 + r * Math.cos(angle)), y = (float) (12 + r * Math.sin(angle));
        if (first) p.moveTo(x, y); else p.lineTo(x, y);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
