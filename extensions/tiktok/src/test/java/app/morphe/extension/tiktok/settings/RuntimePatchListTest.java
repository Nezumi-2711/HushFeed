package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The switch list Build details shows is read back from the patcher's registrations, nothing else. */
public class RuntimePatchListTest {
    private static final String PATCH_ROOT = "patches/src/main/kotlin/app/morphe/patches/tiktok";
    private static final Pattern REGISTRATION = Pattern.compile("registerForBuildDetails\\(\"(enable[A-Za-z0-9]+)\"\\)");
    /** What follows a registration: execute's closing brace, then the patch's. Nothing runs after it. */
    private static final String END_OF_EXECUTE = "\n    }\n}\n";
    private static final Pattern PATCH_START = Pattern.compile("(?m)^val \\w+ = (bytecodePatch|resourcePatch|rawResourcePatch)\\(");
    private static final Pattern PATCH_NAME = Pattern.compile("\\bname = \"([^\"]+)\"");
    private static final Pattern ANY_REGISTRATION =
            Pattern.compile("SettingsStatus;->|SETTINGS_STATUS->enable|registerForBuildDetails\\(\"");

    @Test
    public void registeredNamesEveryRaisedSwitchAndNothingElse() throws Exception {
        Map<Field, Boolean> saved = new LinkedHashMap<>();
        for (Field field : SettingsStatus.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.getType() == boolean.class && Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)) {
                saved.put(field, field.getBoolean(null));
            }
        }
        try {
            for (Field field : saved.keySet()) field.setBoolean(null, false);
            assertEquals(Collections.emptyList(), SettingsStatus.registered());

            SettingsStatus.enableNetworkRequestReport();
            SettingsStatus.enableCommentSendFix();
            SettingsStatus.enableGhostMode();
            assertEquals(Arrays.asList("commentSendFix", "ghostMode", "networkRequestReport"), sorted(SettingsStatus.registered()));

            for (Field field : saved.keySet()) field.setBoolean(null, true);
            List<String> all = SettingsStatus.registered();
            assertEquals("every public switch ends in Enabled and is listed", saved.size(), all.size());
            assertTrue(all.contains("featureGateRecorder"));
        } finally {
            for (Map.Entry<Field, Boolean> entry : saved.entrySet()) entry.getKey().setBoolean(null, entry.getValue());
        }
    }

    @Test
    public void everyPatchThatChangesTikToksCodeRegisters() throws Exception {
        Set<String> silent = new TreeSet<>();
        for (Path file : patchSources()) {
            String source = read(file);
            Matcher start = PATCH_START.matcher(source);
            List<Integer> starts = new ArrayList<>();
            List<String> kinds = new ArrayList<>();
            while (start.find()) {
                starts.add(start.start());
                kinds.add(start.group(1));
            }
            for (int index = 0; index < starts.size(); index++) {
                if (!kinds.get(index).equals("bytecodePatch")) continue;
                int end = index + 1 < starts.size() ? starts.get(index + 1) : source.length();
                String block = source.substring(starts.get(index), end);
                Matcher name = PATCH_NAME.matcher(block.substring(0, Math.min(400, block.length())));
                // An unnamed patch is a dependency another one pulls in, never a Manager row.
                if (name.find() && !ANY_REGISTRATION.matcher(block).find()) silent.add(name.group(1));
            }
        }
        assertEquals("a named code patch that never registers is missing from Build details",
                Collections.emptySet(), silent);
    }

    @Test
    public void everyRowlessRegistrationNamesARealSwitchAndRunsLast() throws Exception {
        Set<String> registered = new TreeSet<>();
        for (Path file : patchSources()) {
            String source = read(file);
            Matcher call = REGISTRATION.matcher(source);
            while (call.find()) {
                String method = call.group(1);
                SettingsStatus.class.getMethod(method);
                String flag = Character.toLowerCase(method.charAt(6)) + method.substring(7) + "Enabled";
                assertEquals(file + " registers a switch SettingsStatus has", boolean.class,
                        SettingsStatus.class.getField(flag).getType());
                assertTrue(file + " already registers " + method, registered.add(method));
                assertTrue(file + " registers " + method + " as the last statement of execute, at its indent",
                        source.startsWith(END_OF_EXECUTE, call.end())
                                && source.substring(source.lastIndexOf('\n', call.start()) + 1, call.start())
                                        .equals("        "));
            }
        }
        assertEquals(new TreeSet<>(Arrays.asList("enableCommentPublishDiagnostics", "enableCommentSendFix",
                "enableFeatureGateLab", "enableFollowDiagnostics", "enableGoogleLoginFix",
                "enableNetworkRequestReport", "enableNoLoginRequirement", "enableNoScreenCaptureDetection",
                "enableRepostDiagnostics", "enableRiskControlCaptcha", "enableRunBesideStoreApp",
                "enableSettings")), registered);
    }

    private static List<Path> patchSources() throws Exception {
        Path repository = Paths.get("").toAbsolutePath();
        while (repository != null && !Files.isDirectory(repository.resolve(PATCH_ROOT))) {
            repository = repository.getParent();
        }
        assertNotNull("The patch sources must be available", repository);
        try (Stream<Path> files = Files.walk(repository.resolve(PATCH_ROOT))) {
            return files.filter(path -> path.toString().endsWith(".kt")).sorted().collect(Collectors.toList());
        }
    }

    private static String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static List<String> sorted(List<String> names) {
        List<String> copy = new ArrayList<>(names);
        Collections.sort(copy);
        return copy;
    }
}
