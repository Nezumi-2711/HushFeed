package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.SettingsContextRule;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class QuickSetupsTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();

    private final Map<Setting<?>, Object> original = new LinkedHashMap<>();
    private Context context;

    @Before public void rememberSettings() {
        context = RuntimeEnvironment.getApplication();
        List<Setting<?>> touched = new ArrayList<>(QuickSetups.listed());
        touched.add(Settings.HIDE_STORY);
        touched.add(Settings.FEED_DESCRIPTION_TEXT_SIZE);
        touched.add(Settings.SESSION_BUDGET_PENDING);
        for (Setting<?> setting : touched) original.put(setting, setting.get());
        CalmFeedPreset.clearForTests(context);
    }

    @After public void restoreSettings() throws Exception {
        Setting.saveAll(original);
        CalmFeedPreset.clearForTests(context);
    }

    @Test public void everyListedSwitchIsARealSettingAndNoListIsEmpty() throws Exception {
        assertFalse("Quiet names no switches", QuickSetups.switchesFor(QuickSetups.Choice.QUIET).isEmpty());
        assertFalse("Privacy names no switches", QuickSetups.switchesFor(QuickSetups.Choice.PRIVACY).isEmpty());
        Set<Object> declared = new HashSet<>();
        for (Class<?> owner : Arrays.asList(Settings.class, BaseSettings.class)) {
            for (Field field : owner.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) declared.add(field.get(null));
            }
        }
        Set<String> keys = new HashSet<>();
        for (BooleanSetting setting : QuickSetups.listed()) {
            assertNotNull("a quick setup lists a switch that isn't there", setting);
            assertTrue(setting.key + " is not declared in Settings or BaseSettings", declared.contains(setting));
            assertSame(setting.key + " doesn't resolve to its own setting",
                    setting, Setting.getSettingFromPath(setting.key));
            assertTrue(setting.key + " is listed twice", keys.add(setting.key));
        }
    }

    @Test public void privacyTurnsOnOnlyTheSwitchesOnThePagesAndUndoPutsThemBack() throws Exception {
        for (BooleanSetting setting : QuickSetups.switchesFor(QuickSetups.Choice.PRIVACY)) setting.save(false);
        BaseSettings.SANITIZE_SHARING_LINKS.save(true);
        // A switch whose patch the bundle left out has no row, so Privacy leaves it alone.
        Collection<Setting<?>> onPages = new ArrayList<>(QuickSetups.switchesFor(QuickSetups.Choice.PRIVACY));
        onPages.remove(Settings.BLOCK_CLIPBOARD_READS);

        QuickSetups.Result applied = QuickSetups.apply(context, QuickSetups.Choice.PRIVACY, onPages);

        List<BooleanSetting> turnedOn = Arrays.asList(Settings.GHOST_MODE, Settings.DISABLE_ANALYTICS,
                Settings.STOP_AI_PROFILING, Settings.BLOCK_ADVERTISING_ID);
        int restart = 0;
        for (BooleanSetting setting : turnedOn) {
            assertTrue(setting.key + " stayed off", setting.get());
            if (setting.rebootApp) restart++;
        }
        assertEquals("the count counts only what changed", turnedOn.size(), applied.changedCount);
        assertEquals(restart, applied.restartChangedCount);
        assertTrue("Stop on-device AI profiling needs a restart and the count missed it", restart > 0);
        assertFalse("a switch with no row was changed out of sight", Settings.BLOCK_CLIPBOARD_READS.get());

        assertEquals("a second tap found something left to change", 0,
                QuickSetups.apply(context, QuickSetups.Choice.PRIVACY, onPages).changedCount);

        QuickSetups.Result undone = QuickSetups.undo(context, applied);
        assertEquals(turnedOn.size(), undone.changedCount);
        for (BooleanSetting setting : turnedOn) assertFalse(setting.key + " didn't come back", setting.get());
        assertTrue("Undo turned off a switch Privacy never changed", BaseSettings.SANITIZE_SHARING_LINKS.get());
    }

    @Test public void backToDefaultsResetsTheSwitchesOnThePagesAndNothingElse() throws Exception {
        Settings.HIDE_STORY.save(!Settings.HIDE_STORY.defaultValue);
        Settings.GHOST_MODE.save(!Settings.GHOST_MODE.defaultValue);
        Settings.DISABLE_ANALYTICS.save(!Settings.DISABLE_ANALYTICS.defaultValue);
        Integer textSize = Settings.FEED_DESCRIPTION_TEXT_SIZE.get();
        Collection<Setting<?>> onPages = Arrays.asList(Settings.HIDE_STORY, Settings.GHOST_MODE,
                Settings.FEED_DESCRIPTION_TEXT_SIZE);

        Map<Setting<?>, Object> targets = QuickSetups.targets(QuickSetups.Choice.DEFAULTS, onPages);
        assertEquals("Back to defaults reaches past the switches",
                Arrays.asList(Settings.HIDE_STORY, Settings.GHOST_MODE), new ArrayList<>(targets.keySet()));

        QuickSetups.Result applied = QuickSetups.apply(context, QuickSetups.Choice.DEFAULTS, onPages);
        assertEquals(2, applied.changedCount);
        assertEquals("Hide stories needs a restart", 1 + (Settings.GHOST_MODE.rebootApp ? 1 : 0),
                applied.restartChangedCount);
        assertEquals(Settings.HIDE_STORY.defaultValue, Settings.HIDE_STORY.get());
        assertEquals(Settings.GHOST_MODE.defaultValue, Settings.GHOST_MODE.get());
        assertEquals("a number was touched", textSize, Settings.FEED_DESCRIPTION_TEXT_SIZE.get());
        assertEquals("a switch with no row was reset", !Settings.DISABLE_ANALYTICS.defaultValue,
                Settings.DISABLE_ANALYTICS.get());

        QuickSetups.undo(context, applied);
        assertEquals(!Settings.HIDE_STORY.defaultValue, Settings.HIDE_STORY.get());
        assertEquals(!Settings.GHOST_MODE.defaultValue, Settings.GHOST_MODE.get());
    }

    @Test public void quietIsTheCalmFeedPresetSoTheCardCanStillRestore() throws Exception {
        for (BooleanSetting setting : QuickSetups.switchesFor(QuickSetups.Choice.QUIET)) setting.save(false);

        QuickSetups.Result applied = QuickSetups.apply(context, QuickSetups.Choice.QUIET,
                Collections.emptyList());

        assertEquals(QuickSetups.switchesFor(QuickSetups.Choice.QUIET).size(), applied.changedCount);
        assertEquals(CalmFeedPreset.State.ACTIVE, CalmFeedPreset.state(context));
        assertTrue(CalmFeedPreset.isApplied());

        QuickSetups.undo(context, applied);
        for (BooleanSetting setting : QuickSetups.switchesFor(QuickSetups.Choice.QUIET)) {
            assertFalse(setting.key + " didn't come back", setting.get());
        }
        assertFalse("Undo left the Calm feed card offering a restore", CalmFeedPreset.hasSnapshot(context));
    }
}
