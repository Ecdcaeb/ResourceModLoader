package rml.loader.agent;

import rml.loader.content.DetachedCompiledPackContractTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

public final class BridgeArchivesContractTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("rml-bridge-contract-");
        try {
            String session = UUID.randomUUID().toString();
            String relative = session + "/builds/" + UUID.randomUUID() + ".zip";
            Path workspaces = root.resolve("workspaces");
            Path mods = root.resolve("mods");
            BridgeArchives archives = new BridgeArchives(workspaces, mods);
            byte[] bytes = DetachedCompiledPackContractTest.archive(BridgeArchives.packId(session), "tag/item", "fixture:blocks", "[]");
            Path source = workspaces.resolve(relative);
            Files.createDirectories(source.getParent());
            Files.write(source, bytes);
            BridgeArchives.Build build = archives.read(session, relative, BridgeArchives.sha256(bytes));
            Files.write(source, new byte[]{1, 2, 3});
            String installed = archives.install(session, build);
            check(Arrays.equals(bytes, Files.readAllBytes(mods.resolve(installed))), "Install uses checked bytes despite source replacement");
            Files.write(source, bytes);
            rejects(() -> archives.read(session, relative, String.join("", java.util.Collections.nCopies(64, "0"))));
            rejects(() -> archives.read(session, "../" + relative, BridgeArchives.sha256(bytes)));
            rejects(() -> archives.read(UUID.randomUUID().toString(), relative, BridgeArchives.sha256(bytes)));
            rejects(() -> archives.read(session, session + "/builds/../escape.zip", BridgeArchives.sha256(bytes)));
            Path linked = root.resolve("linked");
            boolean canLink = false;
            try { Files.createSymbolicLink(linked, workspaces); canLink = true; }
            catch (UnsupportedOperationException | IOException | SecurityException unavailable) { }
            if (canLink) rejects(() -> new BridgeArchives(linked, mods));
            System.out.println("BridgeArchivesContractTest passed" + (canLink ? " (including symlinks)" : " (symlinks unavailable)"));
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }

    private static void rejects(Checked operation) throws Exception {
        try { operation.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected unsafe archive rejection");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private interface Checked { void run() throws Exception; }
}
