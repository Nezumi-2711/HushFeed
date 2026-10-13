/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Report a problem fills in the bug form from fixed facts, and never grows past its cap. */
public class ProblemReportTest {
    private static ProblemReport.Facts facts(List<String> switchesOn) {
        return new ProblemReport.Facts("0.72.0", "47.1.4", 2024701040L, "samsung", "SM-S901U",
                "15", 35, switchesOn);
    }

    @Test public void theBodyCarriesVersionsPhoneSwitchesThenTheTwoLinesToFillIn() {
        String body = ProblemReport.body(facts(Arrays.asList("Hide ads", "Hide Shop posts")));

        assertEquals("Hushfeed and TikTok: Version 0.72.0 for TikTok 47.1.4 (version code 2024701040)\n"
                + "Phone: samsung SM-S901U, Android 15 (SDK 35)\n\n"
                + "Hushfeed switches that are on (2):\n- Hide ads\n- Hide Shop posts\n\n"
                + "What happened:\n\nWhat I expected:\n", body);
    }

    @Test public void theLinkOpensTheBugFormWithItsFieldsFilledAndEncoded() throws Exception {
        String url = ProblemReport.url(facts(Arrays.asList("Hide ads", "Comments: Hide likes & replies")));

        assertTrue(url, url.startsWith("https://github.com/SysAdminDoc/HushFeed/issues/new?template=bug_report.yml&title=bug%3A%20&"));
        assertTrue(url, url.contains("&versions=Version%200.72.0%20for%20TikTok%2047.1.4%20%28version%20code%202024701040%29&"));
        assertTrue(url, url.contains("&device=samsung%20SM-S901U%2C%20Android%2015%20%28SDK%2035%29&"));
        assertTrue(url, url.contains("&bug_description=What%20happened%3A%0A%0AWhat%20I%20expected%3A%0A&"));
        assertFalse("a raw space, newline or plus slipped into the link",
                url.contains(" ") || url.contains("\n") || url.contains("+"));
        String context = url.substring(url.indexOf("&additional_context=") + "&additional_context=".length());
        assertEquals("Hushfeed switches that are on (2):\n- Hide ads\n- Comments: Hide likes & replies",
                URLDecoder.decode(context, "UTF-8"));
    }

    @Test public void aLongSwitchListIsCutToTheCapAndSaysHowManyMore() throws Exception {
        List<String> many = new ArrayList<>();
        for (int index = 1; index <= 1_500; index++) many.add("A fairly long switch title, number " + index);

        String body = ProblemReport.body(facts(many));

        assertTrue("the body is " + body.length() + " characters", body.length() <= ProblemReport.BODY_LIMIT);
        assertTrue("the lines the reporter fills in were cut", body.endsWith("What happened:\n\nWhat I expected:\n"));
        assertTrue(body.contains("Hushfeed switches that are on (1500):\n- A fairly long switch title, number 1\n"));
        int shown = body.split("\n- A fairly long switch title, number ", -1).length - 1;
        assertTrue("nothing was cut, so this proves nothing", shown < many.size());
        assertTrue("the cut doesn't say how many it left out",
                body.contains("\n- and " + (many.size() - shown) + " more\n"));

        String url = ProblemReport.url(facts(many));
        String context = URLDecoder.decode(url.substring(url.indexOf("&additional_context=")
                + "&additional_context=".length()), "UTF-8");
        assertTrue("the link carried more switches than the body", body.contains(context));
    }

    @Test public void noSwitchesOnSaysSoAndMissingFactsReadUnknown() {
        String body = ProblemReport.body(new ProblemReport.Facts("", null, -1, "Google", "Pixel 8",
                "16", 36, null));

        assertTrue(body, body.startsWith("Hushfeed and TikTok: Version unknown for TikTok unknown\n"
                + "Phone: Google Pixel 8, Android 16 (SDK 36)\n\n"
                + "Hushfeed switches that are on: none\n\n"));
    }
}
