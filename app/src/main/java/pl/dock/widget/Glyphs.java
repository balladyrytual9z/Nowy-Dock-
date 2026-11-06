package pl.dock.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.LruCache;

/** PLACEHOLDER - will be replaced */
public final class Glyphs {
    private Glyphs() {}
    private static final Paint GP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PA = new Path();
    private static final RectF RC = new RectF();
    public static void draw(Canvas cv, String g, float cx, float cy, float r, int color) {
        // temporary stub
    }
    public static Bitmap bitmap(String g, int size, int color) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        draw(new Canvas(b), g, size/2f, size/2f, size*0.42f, color);
        return b;
    }
}
