/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * The comment half of onlynazril-patches' TikTok handle patch (GPL-3.0),
 * https://github.com/na-x-ril/onlynazril-patches, hooked at the comment cell.
 */
package app.morphe.extension.tiktok.comment;

import android.graphics.Color;
import android.text.TextUtils;
import android.widget.TextView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.feed.AuthorRegion;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A commenter's @username in place of the display name they chose, the country their account
 * is set to after the comment's time, and a color of the account's own for the name.
 *
 * <p>Every call comes from the comment cell's own bind, with the comment it's drawing, so
 * nothing here asks which screen a name belongs to. TikTok writes the name, the reply label and
 * the time afresh on each bind, so a recycled cell never keeps another comment's text: the name
 * hooks hand back a replacement for the text TikTok is about to write, and the time hook runs
 * right after TikTok's write.
 */
public final class CommenterDetails {
    private static final String FAMILY = "commenter details";

    /** Enough for the replies of a long thread; only recent commenters are ever asked for. */
    private static final int MAX_HANDLES = 512;

    /**
     * The handle of every commenter drawn with the switch on, by uid. A reply to a reply carries
     * the uid of the person it answers but not their handle, and TikTok's stand-in for that
     * person reads its handle back as the display name, so the label is named from here.
     */
    private static final Map<String, String> HANDLE_BY_UID = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_HANDLES;
        }
    };

    /** Per time view: exactly what was written over it, and the time it was built from. */
    private static final Map<TextView, String[]> STAMPED = new WeakHashMap<>();

    /**
     * The name colors, one hue per row: the darker shade for TikTok's light comment sheet and the
     * lighter one for its dark sheet, so an account keeps its hue in either theme.
     */
    private static final int[][] NAME_COLORS = {
            {0xFFC62828, 0xFFEF9A9A}, // red
            {0xFFD84315, 0xFFFFAB91}, // deep orange
            {0xFF6A1B9A, 0xFFCE93D8}, // purple
            {0xFF2E7D32, 0xFFA5D6A7}, // green
            {0xFF00838F, 0xFF80DEEA}, // cyan
            {0xFF1565C0, 0xFF90CAF9}, // blue
            {0xFFAD1457, 0xFFF48FB1}, // pink
            {0xFF4527A0, 0xFFB39DDB}, // deep purple
    };

    /** Per name view: the color written over it, and TikTok's color from under it. */
    private static final Map<TextView, int[]> COLORED = new WeakHashMap<>();

    private CommenterDetails() {
    }

    /**
     * Called by the cell's name setter with the name TikTok picked. Returns what to write
     * instead: "@handle", or the name as it came when the switch is off, the comment is an ad's
     * (its name is the advertiser's, not the account's) or the account has no handle.
     */
    public static CharSequence commenterName(Object comment, CharSequence name) {
        HookStatus.bound(FAMILY, "name");
        try {
            if (!handlesOn() || comment == null
                    || Reflect.property(comment, "getCommerceInfo", "commerceInfo") != null) {
                return name;
            }
            Object user = Reflect.property(comment, "getUser", "user");
            String handle = handleOf(user);
            if (handle == null) {
                return name;
            }
            remember(Reflect.string(user, "getUid", "uid"), handle);
            return "@" + handle;
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not show the commenter's handle", ex);
            return name;
        }
    }

    /**
     * Called by a reply's "replying to" label with the name TikTok picked for the person it
     * answers. That person's handle comes from their own comment, which is usually above it in
     * the thread. Until it has been drawn, the label stays as TikTok wrote it rather than
     * guessing from a display name two people could share.
     */
    public static CharSequence replyTargetName(Object comment, CharSequence name) {
        HookStatus.bound(FAMILY, "reply");
        try {
            if (!handlesOn() || comment == null) {
                return name;
            }
            String uid = Reflect.string(comment, "getReplyToUserId", "replyToUserId");
            String handle;
            synchronized (HANDLE_BY_UID) {
                handle = uid == null ? null : HANDLE_BY_UID.get(uid);
            }
            return handle == null ? name : "@" + handle;
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not show the replied-to handle", ex);
            return name;
        }
    }

    /**
     * Called right after the cell writes the comment's time on {@code time}. Adds the
     * commenter's country after it, the way the feed adds a video's after the creator's name.
     * With the switch off, or no country on the account, TikTok's time stays as it is.
     */
    public static void onCommentTime(Object comment, TextView time) {
        HookStatus.bound(FAMILY, "time");
        if (time == null) {
            return;
        }
        try {
            CharSequence shown = time.getText();
            String current = shown == null ? "" : shown.toString();
            String[] stamp = STAMPED.get(time);
            // TikTok writes the time before every call, so the view only holds a stamp of ours
            // when nothing rewrote it in between. Only the exact text written here counts.
            CharSequence base = stamp != null && stamp[0].equals(current) ? stamp[1] : shown;
            if (base == null) {
                base = "";
            }

            String region = regionsOn() ? regionOf(comment) : null;
            if (region == null) {
                STAMPED.remove(time);
                if (base != shown) {
                    time.setText(base);
                }
                return;
            }

            CharSequence target = base.length() == 0
                    ? region
                    : TextUtils.concat(base, AuthorRegion.SEPARATOR + region);
            String written = target.toString();
            STAMPED.put(time, new String[]{written, base.toString()});
            if (!written.equals(current)) {
                time.setText(target);
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not show the commenter's region", ex);
        }
    }

    /**
     * Called right after the cell writes the commenter's name on {@code name}. Colors it by the
     * account, or puts TikTok's color back with the switch off. The color goes on the view rather
     * than in the name, because one of TikTok's paths casts the name to a String. That helper
     * sets the color on most binds but leaves the old one when its theme color is missing, so
     * TikTok's color is kept per view instead of read back from a view of ours.
     */
    public static void onCommenterName(Object comment, TextView name) {
        HookStatus.bound(FAMILY, "name color");
        if (name == null) {
            return;
        }
        try {
            int current = name.getCurrentTextColor();
            int[] colored = COLORED.get(name);
            // Ours still on the view means TikTok wrote no color of its own this time.
            int tikTok = colored != null && colored[0] == current ? colored[1] : current;
            Integer ours = colorsOn() ? nameColor(comment, tikTok) : null;
            if (ours == null) {
                COLORED.remove(name);
                if (tikTok != current) {
                    name.setTextColor(tikTok);
                }
                return;
            }
            COLORED.put(name, new int[]{ours, tikTok});
            if (ours != current) {
                name.setTextColor(ours);
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not color the commenter's name", ex);
        }
    }

    /**
     * The account's color, by its uid or, when the payload has none, its handle. TikTok's own
     * name color says which sheet it's on: light text means the dark one. Null without either key.
     */
    static Integer nameColor(Object comment, int tikTok) {
        Object user = comment == null ? null : Reflect.property(comment, "getUser", "user");
        if (user == null) {
            return null;
        }
        String key = Reflect.string(user, "getUid", "uid");
        if (key == null || key.isEmpty()) {
            key = handleOf(user);
        }
        if (key == null) {
            return null;
        }
        int[] hue = NAME_COLORS[Math.floorMod(key.hashCode(), NAME_COLORS.length)];
        double luminance = (0.299 * Color.red(tikTok) + 0.587 * Color.green(tikTok)
                + 0.114 * Color.blue(tikTok)) / 255;
        return hue[luminance > 0.5 ? 1 : 0];
    }

    private static boolean colorsOn() {
        return SettingsStatus.commenterDetailsEnabled && Settings.COLOR_COMMENTER_NAMES.get();
    }

    private static boolean handlesOn() {
        return SettingsStatus.commenterDetailsEnabled && Settings.SHOW_COMMENTER_HANDLE.get();
    }

    private static boolean regionsOn() {
        return SettingsStatus.commenterDetailsEnabled && Settings.SHOW_COMMENTER_REGION.get();
    }

    /** The account's @name without the at sign, or null when it carries none. */
    private static String handleOf(Object user) {
        String unique = Reflect.string(user, "getUniqueId", "uniqueId");
        if (unique == null) {
            return null;
        }
        // Some payloads keep the at sign on the handle, and the label adds its own.
        String handle = unique.startsWith("@") ? unique.substring(1).trim() : unique;
        return handle.isEmpty() ? null : handle;
    }

    /**
     * The country the commenter's account is set to. A comment carries no country of its own,
     * so the account's is the only one there is.
     */
    private static String regionOf(Object comment) {
        Object user = comment == null ? null : Reflect.property(comment, "getUser", "user");
        if (user == null) {
            return null;
        }
        String region = AuthorRegion.regionCode(Reflect.string(user, "getRegion", "region"));
        if (region == null) {
            region = AuthorRegion.regionCode(Reflect.string(user, "getAccountRegion", "accountRegion"));
        }
        boolean carried = region != null;
        // Some comment payloads leave the country off the account. The feed already read it
        // for this creator's video, so look it up by this commenter's own handle. Only the
        // exact handle matches, so a recycled cell never gets another commenter's country.
        if (region == null) {
            region = AuthorRegion.seenRegion(handleOf(user));
        }
        String shown = region;
        Logger.printDebug(() -> "Commenter region: the comment's account "
                + (carried ? "carried one" : "carried none") + ", showing "
                + (shown == null ? "nothing" : shown + (carried ? "" : " from the feed")));
        return region;
    }

    private static void remember(String uid, String handle) {
        if (uid == null) {
            return;
        }
        synchronized (HANDLE_BY_UID) {
            HANDLE_BY_UID.put(uid, handle);
        }
    }

    /** Lets a test start from no remembered commenters. */
    static void forget() {
        synchronized (HANDLE_BY_UID) {
            HANDLE_BY_UID.clear();
        }
        STAMPED.clear();
        COLORED.clear();
        AuthorRegion.forgetSeenRegions();
    }
}
