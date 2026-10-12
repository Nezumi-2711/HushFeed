import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Corrupt actual helper inputs and call the same artifact verifier used by the build. */
public final class ArtifactContracts {
    private static int checks;
    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("<APK> <payload DEX> <shared DEX> <vendor DEX> <new test directory>");
        Path apk = Path.of(args[0]), payload = Path.of(args[1]), shared = Path.of(args[2]), slice = Path.of(args[3]), output = Path.of(args[4]);
        Files.createDirectory(output);
        VendorSlice.main(new String[]{"verify-apk", apk.toString(), payload.toString(), shared.toString(), slice.toString()}); checks++;
        for (String entry : Arrays.asList("classes.dex", "classes2.dex", "classes3.dex", "classes4.dex")) {
            Path missing = rewrite(apk, output.resolve("missing-" + entry + ".apk"), entry, null, false);
            reject(() -> VendorSlice.main(new String[]{"verify-apk", missing.toString(), payload.toString(), shared.toString(), slice.toString()}));
        }
        Path wrongPayload = rewrite(apk, output.resolve("wrong-payload.apk"), "classes2.dex", Files.readAllBytes(slice), false);
        reject(() -> VendorSlice.main(new String[]{"verify-apk", wrongPayload.toString(), payload.toString(), shared.toString(), slice.toString()}));
        Path wrongShared = rewrite(apk, output.resolve("wrong-shared.apk"), "classes3.dex", Files.readAllBytes(payload), false);
        reject(() -> VendorSlice.main(new String[]{"verify-apk", wrongShared.toString(), payload.toString(), shared.toString(), slice.toString()}));
        Path wrongSlice = rewrite(apk, output.resolve("wrong-vendor.apk"), "classes4.dex", Files.readAllBytes(payload), false);
        reject(() -> VendorSlice.main(new String[]{"verify-apk", wrongSlice.toString(), payload.toString(), shared.toString(), slice.toString()}));
        Path helperSubstitute = rewrite(apk, output.resolve("helper-substitute.apk"), "classes.dex", Files.readAllBytes(payload), false);
        reject(() -> VendorSlice.main(new String[]{"verify-apk", helperSubstitute.toString(), payload.toString(), shared.toString(), slice.toString()}));
        Path extra = rewrite(apk, output.resolve("extra-dex.apk"), "classes5.dex", Files.readAllBytes(slice), true);
        reject(() -> VendorSlice.main(new String[]{"verify-apk", extra.toString(), payload.toString(), shared.toString(), slice.toString()}));
        Path shortPayload = output.resolve("truncated.dex"); Files.write(shortPayload, Arrays.copyOf(Files.readAllBytes(payload), 80));
        reject(() -> VendorSlice.main(new String[]{"payload", shortPayload.toString(), shared.toString(), output.resolve("short.json").toString()}));
        reject(() -> VendorSlice.main(new String[]{"payload", slice.toString(), shared.toString(), output.resolve("vendor-as-payload.json").toString()}));
        Path existing = output.resolve("existing.json"); Files.writeString(existing, "preserve this receipt");
        reject(() -> VendorSlice.main(new String[]{"payload", payload.toString(), shared.toString(), existing.toString()}));
        if (!Files.readString(existing).equals("preserve this receipt")) throw new AssertionError("pre-existing output changed"); checks++;
        System.out.println("[api23-art] " + checks + " artifact contracts passed");
    }
    private static Path rewrite(Path source, Path output, String replace, byte[] bytes, boolean append) throws Exception {
        try (ZipFile zip = new ZipFile(source.toFile()); ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(output))) {
            for (java.util.Enumeration<? extends ZipEntry> all = zip.entries(); all.hasMoreElements();) {
                ZipEntry entry = all.nextElement();
                if (entry.getName().equals(replace)) {
                    if (bytes != null) add(target, replace, bytes);
                    continue;
                }
                if (entry.getSize() > 16 * 1024 * 1024) throw new IllegalArgumentException("test input entry too large");
                try (InputStream input = zip.getInputStream(entry)) { add(target, entry.getName(), input.readNBytes(16 * 1024 * 1024)); }
            }
            if (append) add(target, replace, bytes);
        }
        return output;
    }
    private static void add(ZipOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new ZipEntry(name)); output.write(bytes); output.closeEntry();
    }
    private interface Work { void run() throws Exception; }
    private static void reject(Work work) throws Exception {
        try { work.run(); throw new AssertionError("invalid actual artifact was accepted"); }
        catch (Exception expected) { checks++; }
    }
}
