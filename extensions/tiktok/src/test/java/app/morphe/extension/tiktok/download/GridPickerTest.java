/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.ss.android.ugc.aweme.feed.model.Aweme;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.PausedProcess;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/**
 * Picking posts on a profile grid (#27): a recycled cell answers for the post it shows now and
 * never for one it showed before, picks are kept by post and survive scrolling, a story or an ad
 * gets no circle, the run takes at most {@link GridPicker#MAX_PICKED}, Save hands the picks over
 * in the order picked, and off or paused the cell is TikTok's as it was.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "en")
public class GridPickerTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    private ActivityController<SaveNoticeTest.HostActivity> owner;
    private Activity activity;
    private final List<List<Object>> runs = new ArrayList<>();

    /** A grid cell's post as the holder's bind hands it over. */
    public static final class Post extends Aweme {
        final String aid;
        boolean story;
        boolean ad;

        Post(String aid) {
            this.aid = aid;
        }

        @Override public String getAid() { return aid; }
        @Override public boolean getIsTikTokStory() { return story; }
        @Override public int getAwemeType() { return story ? 40 : 0; }
        @Override public boolean isAd() { return ad; }
    }

    @Before public void setUp() {
        owner = Robolectric.buildActivity(SaveNoticeTest.HostActivity.class).setup().visible();
        activity = owner.get();
        Utils.setActivity(activity);
        SettingsStatus.advancedDownloadsEnabled = true;
        Settings.PICK_GRID_VIDEOS.save(true);
        GridPicker.resetForTests();
        GridPicker.runsForTests = runs;
        ShadowToast.reset();
    }

    @After public void tearDown() {
        GridPicker.resetForTests();
        PausedProcess.set(false);
        Settings.PICK_GRID_VIDEOS.save(false);
        SettingsStatus.advancedDownloadsEnabled = false;
        Utils.setActivity(null);
        owner.pause().stop().destroy();
    }

    /** A cell the way the grid lays it out: a FrameLayout on the screen with TikTok's cover in it. */
    private FrameLayout cell() {
        FrameLayout cell = new FrameLayout(activity);
        View cover = new View(activity);
        cover.setOnClickListener(view -> { });
        cell.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        ViewGroup content = activity.findViewById(android.R.id.content);
        content.addView(cell, new FrameLayout.LayoutParams(300, 400));
        return cell;
    }

    private static TextView circle(View cell) {
        return cell.findViewWithTag(GridPicker.CIRCLE_TAG);
    }

    private static View cover(View cell) {
        return cell.findViewWithTag(GridPicker.COVER_TAG);
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private TextView barText(String text) {
        View bar = GridPicker.barForTests();
        if (!(bar instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) bar;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView && text.contentEquals(((TextView) child).getText())) return (TextView) child;
        }
        return null;
    }

    @Test public void theSwitchStartsOff() {
        assertFalse(Settings.PICK_GRID_VIDEOS.defaultValue);
    }

    @Test public void aCellGetsACircleOverTikToksViewsAndNoCoverUntilSomethingIsPicked() {
        FrameLayout cell = cell();
        GridPicker.bind(cell, new Post("1"));
        TextView circle = circle(cell);
        assertNotNull("no circle on the cell", circle);
        assertSame("the circle isn't on top", circle, cell.getChildAt(cell.getChildCount() - 1));
        assertEquals(View.GONE, cover(cell).getVisibility());
        assertFalse(circle.isSelected());
        // Bound again, the cell keeps one circle rather than gaining another.
        GridPicker.bind(cell, new Post("1"));
        int circles = 0;
        for (int i = 0; i < cell.getChildCount(); i++) {
            if (GridPicker.CIRCLE_TAG.equals(cell.getChildAt(i).getTag())) circles++;
        }
        assertEquals(1, circles);
    }

    @Test public void aRecycledCellAnswersForThePostItShowsNow() {
        FrameLayout cell = cell();
        Post first = new Post("1");
        Post second = new Post("2");
        GridPicker.bind(cell, first);
        circle(cell).performClick();
        assertTrue(GridPicker.isPicked("1"));
        assertEquals(1, GridPicker.pickedCount());
        assertNotNull("the bar doesn't count one pick", barText("One picked"));

        // Scrolled: the same view now shows another post, which isn't picked.
        GridPicker.bind(cell, second);
        assertFalse("a recycled cell kept the tick of the post it showed before", circle(cell).isSelected());
        assertEquals("with a pick made, the whole cell picks", View.VISIBLE, cover(cell).getVisibility());
        cover(cell).performClick();
        assertTrue("the tap picked another post than the one on screen", GridPicker.isPicked("2"));
        assertTrue(GridPicker.isPicked("1"));
        assertNotNull(barText("2 picked"));

        // Scrolled back: the first post's tick returns with it.
        GridPicker.bind(cell, first);
        assertTrue(circle(cell).isSelected());
        cover(cell).performClick();
        assertFalse("the tap didn't unpick the post on screen", GridPicker.isPicked("1"));
        assertTrue(GridPicker.isPicked("2"));
    }

    @Test public void aTapReadsTheLatestBindNotTheFirst() {
        FrameLayout cell = cell();
        GridPicker.bind(cell, new Post("old"));
        GridPicker.bind(cell, new Post("new"));
        circle(cell).performClick();
        assertTrue(GridPicker.isPicked("new"));
        assertFalse(GridPicker.isPicked("old"));
    }

    @Test public void twoCellsOfOnePostShareItsTick() {
        FrameLayout one = cell();
        FrameLayout two = cell();
        GridPicker.bind(one, new Post("7"));
        GridPicker.bind(two, new Post("7"));
        circle(one).performClick();
        assertTrue("the other cell of the same post wasn't redrawn", circle(two).isSelected());
    }

    @Test public void storiesAndAdsAreTikToksToTap() {
        FrameLayout cell = cell();
        Post story = new Post("s");
        story.story = true;
        GridPicker.bind(cell, story);
        assertNull("a story cell got a circle", circle(cell));
        Post ad = new Post("a");
        ad.ad = true;
        GridPicker.bind(cell, ad);
        assertNull("an ad got a circle", circle(cell));
        GridPicker.bind(cell, new Post(null));
        assertNull("a post without an id got a circle", circle(cell));
        GridPicker.bind(cell, "not a post");
        assertNull(circle(cell));

        // A cell that was a video and is recycled into a story loses its circle and cover.
        GridPicker.bind(cell, new Post("v"));
        circle(cell).performClick();
        GridPicker.bind(cell, story);
        assertNull(circle(cell));
        assertNull("the story cell's tap is still taken", cover(cell));
        assertTrue("the pick made before stays", GridPicker.isPicked("v"));
    }

    @Test public void aRunTakesAtMostTheCap() {
        FrameLayout cell = cell();
        for (int index = 0; index < GridPicker.MAX_PICKED; index++) {
            GridPicker.bind(cell, new Post("p" + index));
            circle(cell).performClick();
        }
        assertEquals(GridPicker.MAX_PICKED, GridPicker.pickedCount());
        GridPicker.bind(cell, new Post("one too many"));
        cover(cell).performClick();
        assertFalse(GridPicker.isPicked("one too many"));
        assertEquals(GridPicker.MAX_PICKED, GridPicker.pickedCount());
        assertEquals("You can pick up to 50 at a time", ShadowToast.getTextOfLatestToast());
    }

    @Test public void saveHandsThePicksOverInTheOrderPickedAndGivesTheGridBack() {
        FrameLayout cell = cell();
        Post a = new Post("a");
        Post b = new Post("b");
        Post c = new Post("c");
        for (Post post : new Post[]{a, c, b}) {
            GridPicker.bind(cell, post);
            circle(cell).performClick();
        }
        TextView save = barText("Save");
        assertNotNull("the bar has no Save", save);
        save.performClick();
        assertEquals(1, runs.size());
        assertEquals(List.of(a, c, b), runs.get(0));
        assertEquals(0, GridPicker.pickedCount());
        assertNull("the bar stayed up", GridPicker.barForTests());
        assertEquals("the cells still take the tap", View.GONE, cover(cell).getVisibility());
        assertFalse(circle(cell).isSelected());
    }

    @Test public void cancelUnpicksEverythingAndSavesNothing() {
        FrameLayout cell = cell();
        GridPicker.bind(cell, new Post("1"));
        circle(cell).performClick();
        barText("Cancel").performClick();
        assertEquals(0, GridPicker.pickedCount());
        assertNull(GridPicker.barForTests());
        assertTrue(runs.isEmpty());
        assertEquals(View.GONE, cover(cell).getVisibility());
    }

    @Test public void offOrPausedTheCellIsTikToksAsItWas() {
        FrameLayout cell = cell();
        int before = cell.getChildCount();
        GridPicker.bind(cell, new Post("1"));
        circle(cell).performClick();
        assertNotNull(GridPicker.barForTests());

        Settings.PICK_GRID_VIDEOS.save(false);
        GridPicker.bind(cell, new Post("1"));
        assertEquals("our views stayed on the cell", before, cell.getChildCount());
        assertEquals("the picks outlived the switch", 0, GridPicker.pickedCount());
        assertNull(GridPicker.barForTests());

        Settings.PICK_GRID_VIDEOS.save(true);
        PausedProcess.set(true);
        GridPicker.bind(cell, new Post("1"));
        assertEquals("paused, the cell still got a circle", before, cell.getChildCount());
    }

    @Test public void aCircleLeftFromBeforeTheSwitchWentOffGivesTheGridBack() {
        FrameLayout cell = cell();
        GridPicker.bind(cell, new Post("1"));
        TextView circle = circle(cell);
        Settings.PICK_GRID_VIDEOS.save(false);
        circle.performClick();
        assertEquals(0, GridPicker.pickedCount());
        assertNull("the circle stayed after a tap with the switch off", circle(cell));
    }

    @Test public void closingTheScreenDropsItsPicks() {
        FrameLayout cell = cell();
        GridPicker.bind(cell, new Post("1"));
        circle(cell).performClick();
        assertEquals(1, GridPicker.pickedCount());
        activity.finish();
        owner.pause().stop().destroy();
        idle();
        assertEquals(0, GridPicker.pickedCount());
        // A fresh host for tearDown.
        owner = Robolectric.buildActivity(SaveNoticeTest.HostActivity.class).setup().visible();
        activity = owner.get();
    }

    @Test public void theCircleReadsAsACheckBoxThatSaysWhetherItIsPicked() {
        FrameLayout cell = cell();
        GridPicker.bind(cell, new Post("1"));
        TextView circle = circle(cell);
        assertEquals("Pick for saving", circle.getContentDescription().toString());
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        circle.onInitializeAccessibilityNodeInfo(info);
        assertTrue(info.isCheckable());
        assertFalse(info.isChecked());
        assertEquals("android.widget.CheckBox", info.getClassName().toString());
        circle.performClick();
        AccessibilityNodeInfo after = AccessibilityNodeInfo.obtain();
        circle(cell).onInitializeAccessibilityNodeInfo(after);
        assertTrue(after.isChecked());
        assertEquals("the cover doubles the cell for a screen reader",
                View.IMPORTANT_FOR_ACCESSIBILITY_NO, cover(cell).getImportantForAccessibility());
    }

    @Test public void aViewThatIsNotACellIsLeftAlone() {
        TextView view = new TextView(activity);
        GridPicker.bind(view, new Post("1"));
        GridPicker.bind(null, new Post("1"));
        assertNull(GridPicker.boundTo(view));
    }
}
