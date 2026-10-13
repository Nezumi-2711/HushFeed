/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.settings;

import android.content.Context;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.wellbeing.BudgetChanges;
import app.morphe.extension.tiktok.wellbeing.SessionBudget;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The three one-tap choices at the top of the settings: Quiet, Privacy and Back to defaults.
 *
 * <p>Every switch a choice may touch is listed here and nowhere else. Quiet is the Calm feed
 * preset, applied through it, so the Restore on the Calm feed card still puts back the setup
 * from before. Privacy turns on the switches that keep what you do to yourself and cost nothing
 * you'd notice; anything with a price, like Watch history, location, motion sensors or a VPN,
 * stays a choice for the Privacy page. Back to defaults puts every switch on Hushfeed's pages
 * back as it shipped. Text, numbers and lists are left alone, and a locked day's budget holds
 * its switches still, the way a restore does.
 */
public final class QuickSetups {
    public enum Choice {
        QUIET,
        PRIVACY,
        DEFAULTS
    }

    /** Views and typing (Ghost mode), tracking, the AI engine, the advertising ID, the clipboard and link tags. */
    private static final BooleanSetting[] PRIVACY = {
            Settings.GHOST_MODE,
            Settings.DISABLE_ANALYTICS,
            Settings.STOP_AI_PROFILING,
            Settings.BLOCK_ADVERTISING_ID,
            Settings.BLOCK_CLIPBOARD_READS,
            BaseSettings.SANITIZE_SHARING_LINKS,
    };

    /** What a choice changed, for the restart row, the banner and Undo. */
    public static final class Result {
        public final Map<Setting<?>, Object> previousValues;
        public final int changedCount;
        public final int restartChangedCount;
        /** Whether Undo goes through the Calm feed preset's own restore. */
        final boolean calmFeedSnapshot;

        Result(Map<Setting<?>, Object> previousValues, boolean calmFeedSnapshot) {
            this.previousValues = Collections.unmodifiableMap(new LinkedHashMap<>(previousValues));
            int restart = 0;
            for (Setting<?> setting : previousValues.keySet()) {
                if (setting.rebootApp) restart++;
            }
            this.changedCount = previousValues.size();
            this.restartChangedCount = restart;
            this.calmFeedSnapshot = calmFeedSnapshot;
        }
    }

    private QuickSetups() {
    }

    /** The switches Quiet and Privacy turn on. Back to defaults has no list: it is every switch. */
    public static List<BooleanSetting> switchesFor(Choice choice) {
        switch (choice) {
            case QUIET:
                return Arrays.asList(CalmFeedPreset.settings());
            case PRIVACY:
                return Arrays.asList(PRIVACY.clone());
            default:
                return Collections.emptyList();
        }
    }

    /**
     * What a choice writes, before the budget has its say. Privacy and Back to defaults keep to
     * the switches on a page this bundle shows, so a switch from a patch left out is never changed
     * out of sight. Quiet writes the preset's six, as the Calm feed card does.
     */
    public static Map<Setting<?>, Object> targets(Choice choice, Collection<Setting<?>> onPages) {
        Map<Setting<?>, Object> targets = new LinkedHashMap<>();
        if (choice == Choice.DEFAULTS) {
            for (Setting<?> setting : onPages) {
                if (setting instanceof BooleanSetting) targets.put(setting, setting.defaultValue);
            }
            return targets;
        }
        for (BooleanSetting setting : switchesFor(choice)) {
            if (choice == Choice.QUIET || onPages.contains(setting)) targets.put(setting, Boolean.TRUE);
        }
        return targets;
    }

    /** The part of {@link #targets} that differs from what's saved now. */
    static Map<Setting<?>, Object> changes(Choice choice, Collection<Setting<?>> onPages) {
        return differing(targets(choice, onPages));
    }

    /** Applies a choice and answers what it changed. Runs off the main thread: it writes to disk. */
    public static synchronized Result apply(Context context, Choice choice,
            Collection<Setting<?>> onPages) throws IOException {
        Map<Setting<?>, Object> changes = changes(choice, onPages);
        if (changes.isEmpty()) return new Result(Collections.emptyMap(), false);
        if (choice == Choice.QUIET && !CalmFeedPreset.hasSnapshot(context)) {
            CalmFeedPreset.Result calm = CalmFeedPreset.apply(context);
            return new Result(changedSince(calm.previousValues), true);
        }
        return new Result(save(changes, false), false);
    }

    /** Puts back what {@code result} changed. */
    public static synchronized Result undo(Context context, Result result) throws IOException {
        if (result.calmFeedSnapshot && CalmFeedPreset.hasSnapshot(context)) {
            return new Result(changedSince(CalmFeedPreset.restore(context).previousValues), false);
        }
        return new Result(save(differing(result.previousValues), true), false);
    }

    /**
     * Saves {@code changes} in one transaction, less what the budget keeps: a locked day holds its
     * switches, and with Wait a day to loosen a looser one waits for the next day. Answers the
     * values the written settings had before.
     */
    private static Map<Setting<?>, Object> save(Map<Setting<?>, Object> changes, boolean deviceState)
            throws IOException {
        if (changes.isEmpty()) return Collections.emptyMap();
        try {
            BudgetChanges.Split split = BudgetChanges.forRestore(changes, SessionBudget.now());
            Map<Setting<?>, Object> before = new LinkedHashMap<>();
            for (Setting<?> setting : split.apply.keySet()) before.put(setting, setting.savedValue());
            Setting.saveAll(split.withWaiting(), deviceState);
            return changedSince(before);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Could not apply the quick setup", error);
        }
    }

    private static Map<Setting<?>, Object> differing(Map<Setting<?>, Object> targets) {
        Map<Setting<?>, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<Setting<?>, Object> target : targets.entrySet()) {
            if (!Objects.equals(target.getKey().savedValue(), target.getValue())) {
                changes.put(target.getKey(), target.getValue());
            }
        }
        return changes;
    }

    /** The entries of {@code before} whose setting now holds something else. */
    private static Map<Setting<?>, Object> changedSince(Map<Setting<?>, Object> before) {
        Map<Setting<?>, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<Setting<?>, Object> entry : before.entrySet()) {
            if (!Objects.equals(entry.getKey().savedValue(), entry.getValue())) {
                changed.put(entry.getKey(), entry.getValue());
            }
        }
        return changed;
    }

    /** Every switch Quiet and Privacy name, for the test that holds the lists to real settings. */
    static List<BooleanSetting> listed() {
        List<BooleanSetting> listed = new ArrayList<>(switchesFor(Choice.QUIET));
        listed.addAll(switchesFor(Choice.PRIVACY));
        return listed;
    }
}
