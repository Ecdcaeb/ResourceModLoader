package rml.loader.agent;

import rml.loader.content.DetachedCompiledPack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Filesystem operations run on bridge workers, never on the server tick thread. */
final class BridgeArchives {
    private final Path workspaceRoot;
    private final Path modsDirectory;

    BridgeArchives(Path workspaceRoot, Path modsDirectory) throws IOException {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.modsDirectory = modsDirectory.toAbsolutePath().normalize();
        safeDirectories(this.workspaceRoot);
        safeDirectories(this.modsDirectory);
    }

    Build read(String session, String relative, String digest) throws IOException {
        String prefix = session + "/builds/";
        if (!relative.startsWith(prefix) || !relative.endsWith(".zip")) throw new IOException("Archive must be a session build");
        String build = relative.substring(prefix.length(), relative.length() - 4);
        if (!uuid(build).equals(build) || !digest.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid build id or SHA-256");
        Path path = workspaceRoot.resolve(relative).normalize();
        if (!path.startsWith(workspaceRoot)) throw new IOException("Archive escapes workspace");
        requireSafe(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Archive must be a regular file");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = bounded(input, DetachedCompiledPack.MAX_ARCHIVE_BYTES);
        }
        requireSafe(path);
        String actual = sha256(bytes);
        if (!actual.equalsIgnoreCase(digest)) throw new IOException("Archive SHA-256 mismatch");
        return new Build(bytes, actual, DetachedCompiledPack.read(bytes, packId(session)));
    }

    String install(String session, Build build) throws IOException {
        requireSafe(modsDirectory);
        Path destination = modsDirectory.resolve("rml-agent-" + session + ".zip");
        requireSafe(destination);
        Path temporary = Files.createTempFile(modsDirectory, ".rml-agent-", ".tmp");
        try {
            Files.write(temporary, build.bytes);
            requireSafe(modsDirectory);
            requireSafe(destination);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return destination.getFileName().toString();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String packId(String session) { return "rml_agent_" + session.replace("-", ""); }

    static String uuid(String value) {
        String parsed = UUID.fromString(value).toString();
        if (!parsed.equals(value)) throw new IllegalArgumentException("Expected canonical UUID");
        return parsed;
    }

    static byte[] bounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maximum) throw new IOException("Input exceeds size limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(64);
            for (byte b : digest) value.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void safeDirectories(Path path) throws IOException {
        requireSafe(path);
        Files.createDirectories(path);
        requireSafe(path);
    }

    static void requireSafe(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path segment : absolute) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) continue;
            BasicFileAttributes attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther() || !current.toRealPath().equals(current))
                throw new IOException("Symbolic links and reparse paths are not allowed");
        }
    }

    static final class Build {
        final byte[] bytes;
        final String digest;
        final DetachedCompiledPack pack;
        Build(byte[] bytes, String digest, DetachedCompiledPack pack) {
            this.bytes = bytes;
            this.digest = digest;
            this.pack = pack;
        }
    }
}
