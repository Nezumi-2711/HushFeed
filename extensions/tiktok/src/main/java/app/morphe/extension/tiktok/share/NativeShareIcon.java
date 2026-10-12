/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.share;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

import androidx.annotation.Nullable;

/**
 * Draws an owned share action's glyph the way TikTok draws its own action icons.
 *
 * <p>Both native binders build a LayerDrawable whose last layer is the action's TuxIconDrawable:
 * the share grid puts its grey circle under it (layer 0), the feed's long-press panel list uses
 * it alone. Swapping only that last layer keeps the circle TikTok themed for light, dark and
 * AMOLED, and the glyph takes the native icon's size and ink colour.
 */
final class NativeShareIcon {
    /** Builds a glyph in the native icon's colour with a stroke weight for its box. */
    interface Glyph {
        Drawable make(int color, float strokeWidthPx);
    }

    /**
     * Hushfeed's glyphs fill 60% of their box, a quarter larger than TikTok's 24dp icon box puts
     * them at the 18dp its icon artwork takes.
     */
    static final float BOX_SCALE = 1.25f;
    /** TikTok's line icons are drawn about 2dp wide in a 24dp box. */
    static final float STROKE_FRACTION = 1f / 12f;

    private NativeShareIcon() {}

    /** The native icon with [glyph] in its icon layer, or null when it isn't TikTok's layered icon. */
    @Nullable
    static Drawable restyle(@Nullable Drawable current, Glyph glyph) {
        if (!(current instanceof LayerDrawable)) return null;
        LayerDrawable layers = (LayerDrawable) current;
        int count = layers.getNumberOfLayers();
        if (count < 1) return null;
        Drawable icon = layers.getDrawable(count - 1);
        if (icon == null) return null;
        int size = Math.min(icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        if (size <= 0) return null;
        Integer ink = inkOf(icon, size);
        if (ink == null) return null;

        Drawable[] parts = new Drawable[count];
        for (int i = 0; i < count - 1; i++) parts[i] = layers.getDrawable(i);
        parts[count - 1] = glyph.make(ink, size * STROKE_FRACTION);
        LayerDrawable styled = new LayerDrawable(parts);
        int box = Math.round(size * BOX_SCALE);
        styled.setLayerSize(count - 1, box, box);
        styled.setLayerGravity(count - 1, Gravity.CENTER);
        return styled;
    }

    /** The most opaque pixel the native icon draws, as an opaque colour, or null if it draws nothing. */
    @Nullable
    static Integer inkOf(Drawable icon, int size) {
        Bitmap bitmap = null;
        Rect bounds = icon.copyBounds();
        try {
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, size, size);
            icon.draw(new Canvas(bitmap));
            int[] pixels = new int[size * size];
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
            int ink = 0;
            int alpha = 0;
            for (int pixel : pixels) {
                int a = pixel >>> 24;
                if (a > alpha) {
                    alpha = a;
                    ink = pixel;
                }
            }
            return alpha < 0x80 ? null : ink | 0xFF000000;
        } catch (Throwable ignored) {
            return null;
        } finally {
            icon.setBounds(bounds);
            if (bitmap != null) bitmap.recycle();
        }
    }
}
