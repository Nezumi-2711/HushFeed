/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.interaction;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.View;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Shows a photo slide whole on a screen that is nearly square, as Fit the video to the screen
 * does for videos.
 *
 * <p>TikTok sizes a slide's picture to cover its cell whenever the slide is taller than about
 * 9:16. On a 9:16 or 9:20 phone that costs a sliver of the picture. On a 1:1 or 4:5 screen the
 * cell is far wider than the slide, so the cover scale cuts a third or more off the top and the
 * bottom, enough to lose the text on a slide. Two of TikTok's questions are answered here: the
 * scale a slide gets, and whether the full screen cell crops one. When this says yes the slide
 * gets the fit scale, which shows all of it centred with the leftover space black.
 *
 * <p>Only squarish screens are touched. A tall slide on an ordinary phone keeps TikTok's crop,
 * which is the same as before this existed.
 */
public final class PhotoSlideFit {
    private PhotoSlideFit() {}

    /**
     * The narrowest a cell has to be, as width over height, before a crop counts. A 9:16 phone is
     * 0.56 and a 9:21 one 0.43, a tablet held upright 0.75, and the square QWERTY phones this was
     * asked for sit near 0.9. 0.65 leaves every tall phone alone and takes in the rest.
     */
    static final float SQUARISH = 0.65f;

    /**
     * The share of a slide's height a cover crop can cut before fit is worth it. Below this the
     * crop is the sliver TikTok always cut and there is nothing to read in it.
     */
    static final float WORTH_FITTING = 0.10f;

    /** Whether Fit is the switch that is on; Fill and off leave every slide to TikTok. */
    private static boolean on() {
        return Settings.FIT_VIDEO_TO_SCREEN.get();
    }

    /**
     * How much of a slide's height covering a cell cuts off: nothing for a slide as wide as the
     * cell or wider, and the missing share of the height otherwise. Zero for sizes it can't use.
     *
     * @param aspect the slide's width over its height
     */
    static float croppedShare(float aspect, float cellWidth, float cellHeight) {
        if (!(aspect > 0f) || !(cellWidth > 0f) || !(cellHeight > 0f)) return 0f;
        float cell = cellWidth / cellHeight;
        if (aspect >= cell) return 0f;
        return 1f - aspect / cell;
    }

    /** Whether a slide in a cell of this size should be shown whole instead of cropped to cover. */
    static boolean shouldFit(float aspect, float cellWidth, float cellHeight) {
        if (!(cellWidth > 0f) || !(cellHeight > 0f)) return false;
        if (cellWidth / cellHeight < SQUARISH) return false;
        return croppedShare(aspect, cellWidth, cellHeight) >= WORTH_FITTING;
    }

    /**
     * The scale question for a slide in the feed, which knows the slide and not the cell, so the
     * screen stands in for it. Non-zero means use the fit scale.
     */
    public static int fitOnScreen(float aspect) {
        try {
            if (!on()) return 0;
            Context context = Utils.getContext();
            if (context == null) return 0;
            Resources resources = context.getResources();
            DisplayMetrics metrics = resources == null ? null : resources.getDisplayMetrics();
            if (metrics == null) return 0;
            return shouldFit(aspect, metrics.widthPixels, metrics.heightPixels) ? 1 : 0;
        } catch (Exception exception) {
            Logger.printException(() -> "Could not decide how to fit a photo slide", exception);
            return 0;
        }
    }

    /**
     * The full screen cell's question of whether a slide this tall should be cropped to its
     * container. Non-zero means do not crop it.
     */
    public static int fitInContainer(View container, float aspect) {
        try {
            if (!on() || container == null) return 0;
            return shouldFit(aspect, container.getWidth(), container.getHeight()) ? 1 : 0;
        } catch (Exception exception) {
            Logger.printException(() -> "Could not decide how to fit a photo slide in its cell", exception);
            return 0;
        }
    }
}
