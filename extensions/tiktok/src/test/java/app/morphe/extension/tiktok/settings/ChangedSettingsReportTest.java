package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.diagnostics.DiagnosticCategory;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.preference.LogBufferManager;
import app.morphe.extension.tiktok.SettingsContextRule;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The export says which settings the reader changed. #149's reporter's export couldn't: a search
 * bar and captions were missing, and nothing in it said whether a switch was on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ChangedSettingsReportTest {
    private static final String BLOCKED_NAME = "a sound somebody blocked";

    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();

    @Before public void setUp() {
        BaseSettings.DEBUG_LOG_FILTERS.resetToDefault();
    }

    @After public void tearDown() {
        for (Setting<?> setting : some()) setting.resetToDefault();
    }

    private static List<Setting<?>> some() {
        return Arrays.<Setting<?>>asList(Settings.HIDE_BOTTOM_SEARCH_BAR, Settings.SHOW_AUTHOR_REGION,
                Settings.HIDE_BLOCKED_SOUNDS, Settings.FADE_CONTROLS_OPACITY,
                Settings.BLOCKED_SOUND_NAMES, Settings.FEED_MUTED);
    }

    @Test public void changedSettingsAreListedByKindAndATextSettingKeepsItsValue() {
        Settings.HIDE_BOTTOM_SEARCH_BAR.save(true);
        Settings.SHOW_AUTHOR_REGION.save(true);
        Settings.HIDE_BLOCKED_SOUNDS.save(false);
        Settings.FADE_CONTROLS_OPACITY.save(50);
        Settings.BLOCKED_SOUND_NAMES.save(BLOCKED_NAME);

        assertEquals(List.of(
                "5 of 5 settings differ from their defaults; text settings show no value",
                "on: hide_bottom_search_bar, show_author_region",
                "off: hide_blocked_sounds",
                "set: fade_controls_opacity 50",
                "text changed: blocked_sound_names"),
                ChangedSettingsReport.lines(some()));
    }

    @Test public void untouchedSettingsSaySo() {
        assertEquals(List.of(ChangedSettingsReport.NONE), ChangedSettingsReport.lines(some()));
    }

    @Test public void whatABackupLeavesOutIsNotAChoice() {
        Settings.FEED_MUTED.save(true);
        assertEquals(List.of(ChangedSettingsReport.NONE), ChangedSettingsReport.lines(some()));
    }

    @Test public void aLongListWraps() {
        Setting<?>[] many = new Setting<?>[ChangedSettingsReport.PER_LINE + 1];
        Arrays.fill(many, Settings.HIDE_BOTTOM_SEARCH_BAR);
        Settings.HIDE_BOTTOM_SEARCH_BAR.save(true);

        List<String> lines = ChangedSettingsReport.lines(Arrays.asList(many));

        assertEquals(3, lines.size());
        assertEquals("on: hide_bottom_search_bar", lines.get(2));
    }

    /**
     * The redactor blanks the value after a name holding "auth", so a switch written as
     * {@code show_author_region: on} would reach the tracker as "[omitted]".
     */
    @Test public void theExportCarriesTheKeysTheRedactorWouldBlankAndNoTextValue() {
        ChangedSettingsReport.install();
        Settings.HIDE_BOTTOM_SEARCH_BAR.save(true);
        Settings.SHOW_AUTHOR_REGION.save(true);
        Settings.BLOCKED_SOUND_NAMES.save(BLOCKED_NAME);

        LogBufferManager.appendEvent(DiagnosticCategory.FEED_AND_NAVIGATION, "Test", "INFO", "something worth reporting");
        String report = LogBufferManager.buildExportText();

        assertTrue(report, report.contains("[" + ChangedSettingsReport.TITLE + "]\n"));
        assertTrue(report, report.contains("hide_bottom_search_bar"));
        assertTrue(report, report.contains("show_author_region"));
        assertTrue(report, report.contains("blocked_sound_names"));
        assertFalse(report, report.contains(BLOCKED_NAME));
    }
}
