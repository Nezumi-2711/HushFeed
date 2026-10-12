/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.speed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.settings.PausedProcess;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.settings.Settings;

import com.ss.android.ugc.aweme.feed.model.Aweme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** Double tap's speed toggle (#136): the hold speed on one video, and back to exactly what it had. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SpeedToggleTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();

    private static final class Player implements PlaybackSpeedPatch.NativePlayer {
        final Map<Object, Aweme> videos = new HashMap<>();
        final List<Float> speeds = new ArrayList<>();

        @Override public Aweme aweme(Object controller) { return videos.get(controller); }
        @Override public void setSpeed(Object controller, float speed) { speeds.add(speed); }
    }

    private Player player;

    private static Aweme video(String id) {
        return new Aweme() {
            @Override public String getAid() { return id; }
        };
    }

    @Before public void setUp() {
        Settings.DEFAULT_SPEED_ENABLED.save(true);
        Settings.DEFAULT_SPEED.save("1.3");
        PlaybackSpeedPatch.beginVideo(null);
        forget();
        player = new Player();
        PlaybackSpeedPatch.nativeForTests = player;
    }

    @After public void tearDown() {
        PlaybackSpeedPatch.nativeForTests = null;
        forget();
        PlaybackSpeedPatch.beginVideo(null);
        Settings.DEFAULT_SPEED_ENABLED.resetToDefault();
        Settings.DEFAULT_SPEED.resetToDefault();
        Settings.HOLD_SPEED.resetToDefault();
        PausedProcess.set(false);
    }

    private static void forget() {
        ReflectionHelpers.setStaticField(PlaybackSpeedPatch.class, "onScreen", null);
        ReflectionHelpers.setStaticField(PlaybackSpeedPatch.class, "liveVideoId", "");
        ReflectionHelpers.setStaticField(PlaybackSpeedPatch.class, "liveSpeed", Float.NaN);
        ReflectionHelpers.setStaticField(PlaybackSpeedPatch.class, "boostedVideoId", "");
        ReflectionHelpers.setStaticField(PlaybackSpeedPatch.class, "boostedTo", Float.NaN);
    }

    private void playing(String id) {
        Object controller = new Object();
        player.videos.put(controller, video(id));
        PlaybackSpeedPatch.onPlayerProgress(controller, id);
    }

    @Test public void theFirstToggleGoesToTheHoldSpeedAndTheSecondPutsBackTheExactSpeed() {
        playing("a");
        assertEquals(2f, PlaybackSpeedPatch.toggleBoost(), 0f);
        assertEquals(2f, PlaybackSpeedPatch.getPlaybackSpeedForVideo(video("a")), 0f);

        // 1.3 is off the quarter steps a drag lands on, and it comes back as 1.3, not 1.25.
        assertEquals(1.3f, PlaybackSpeedPatch.toggleBoost(), 0f);
        assertEquals(List.of(2f, 1.3f), player.speeds);
        assertEquals(1.3f, PlaybackSpeedPatch.getPlaybackSpeedForVideo(video("a")), 0f);
        assertEquals(1.3f, PlaybackSpeedPatch.getPlaybackSpeed(), 0f);
    }

    @Test public void theNextVideoStartsUntoggledAndAtItsUsualSpeed() {
        playing("a");
        PlaybackSpeedPatch.toggleBoost();
        assertEquals(1.3f, PlaybackSpeedPatch.getPlaybackSpeedForVideo(video("b")), 0f);

        playing("b");
        assertEquals("a new video's first toggle speeds it up", 2f, PlaybackSpeedPatch.toggleBoost(), 0f);
        assertEquals(1.3f, PlaybackSpeedPatch.getPlaybackSpeedForVideo(video("c")), 0f);
    }

    @Test public void theHoldSpeedPickedUnderSpeedIsTheBoost() {
        Settings.HOLD_SPEED.save("3");
        playing("a");
        assertEquals(3f, PlaybackSpeedPatch.toggleBoost(), 0f);
        Settings.HOLD_SPEED.save("not a speed");
        assertEquals("TikTok's own 2x when the pick can't be used", 2f, PlaybackSpeedPatch.boostSpeed(), 0f);
    }

    @Test public void aSpeedChangedSinceTheToggleIsKeptAndTheNextToggleSpeedsUpAgain() {
        playing("a");
        PlaybackSpeedPatch.toggleBoost();
        assertTrue(PlaybackSpeedPatch.setLiveSpeed(1.75f));
        assertEquals(2f, PlaybackSpeedPatch.toggleBoost(), 0f);
        assertEquals(1.75f, PlaybackSpeedPatch.toggleBoost(), 0f);
    }

    @Test public void aVideoAlreadyAtTheHoldSpeedGoesToNormalAndBack() {
        Settings.DEFAULT_SPEED.save("2");
        playing("a");
        assertEquals(1f, PlaybackSpeedPatch.toggleBoost(), 0f);
        assertEquals(2f, PlaybackSpeedPatch.toggleBoost(), 0f);
    }

    @Test public void withNoPlayerOnScreenNothingChanges() {
        assertTrue(Float.isNaN(PlaybackSpeedPatch.toggleBoost()));
        assertTrue(player.speeds.isEmpty());
    }
}
