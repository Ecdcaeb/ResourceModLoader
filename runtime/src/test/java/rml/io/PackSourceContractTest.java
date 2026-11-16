package rml.io;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Dependency-free executable contract checks; run with assertions enabled or disabled. */
public final class PackSourceContractTest {
    private static final String MODULE = "assets/demo/module";
    private static final String TEXT = MODULE + "/a-first.txt";
    private static final String BINARY = MODULE + "/nested/\u4e2d\u6587.bin";

    private PackSourceContractTest() {
    }

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("rml-pack-source-");
        try {
            checkContract(temporary);
            checkInvalidSources(temporary);
            checkSymbolicLinks(temporary);
        } finally {
            deleteTree(temporary);
        }
        System.out.println("PackSourceContractTest passed");
    }

    private static void checkContract(Path temporary) throws Exception {
        Map<String, byte[]> fixtures = fixtures();
        Path directory = Files.createDirectory(temporary.resolve("directory pack \u00fc"));
        for (Map.Entry<String, byte[]> fixture : fixtures.entrySet()) {
            Path file = directory.resolve(fixture.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, fixture.getValue());
        }
        Files.createDirectories(directory.resolve("assets/demo/empty"));
        Path archiveDirectory = Files.createDirectory(temporary.resolve("archives !"));
        Path archive = archiveDirectory.resolve("pack space \u00fc.jar");
        writeArchive(archive, fixtures);

        List<PackFile> directoryFiles = checkSource(directory, fixtures, "file");
        List<PackFile> archiveFiles = checkSource(archive, fixtures, "jar");
        equal(directoryFiles.size(), archiveFiles.size(), "directory/archive file count");
        for (int index = 0; index < directoryFiles.size(); index++) {
            PackFile left = directoryFiles.get(index);
            PackFile right = archiveFiles.get(index);
            equal(left.getPath(), right.getPath(), "directory/archive path");
            equal(left.getRelativePath(), right.getRelativePath(), "directory/archive relative path");
            bytes(left.getBytes(), right.getBytes(), "directory/archive bytes");
        }

        // On Windows these operations fail if a ZipFile or entry stream leaked.
        Path moved = archive.resolveSibling("moved archive.jar");
        Files.move(archive, moved);
        Files.delete(moved);
        Files.write(directory.resolve(TEXT), new byte[] {42});
        deleteTree(directory);
        checkSnapshots(directoryFiles, fixtures, "file");
        checkSnapshots(archiveFiles, fixtures, "jar");
    }

    private static List<PackFile> checkSource(Path path, Map<String, byte[]> fixtures,
                                               String scheme) throws Exception {
        PackSource source = PackSource.open(path);
        try {
            List<String> expected = new ArrayList<>();
            for (String name : fixtures.keySet()) {
                if (name.startsWith(MODULE + "/")) {
                    expected.add(name);
                }
            }
            Collections.sort(expected);
            List<PackFile> files = source.read(MODULE, true);
            equal(expected, paths(files), "deterministic recursive file order");
            equal(expected, paths(source.read("assets\\demo//module/.", true)), "normalized separators");
            equal(expected, paths(source.read(MODULE + "/", true)), "trailing separator");
            checkSnapshots(files, fixtures, scheme);
            for (PackFile file : files) {
                equal(file.getPath().substring(MODULE.length() + 1), file.getRelativePath(),
                        "module-relative path");
                // Verify original URIs are properly escaped, including Unicode, #, %, spaces and !.
                URLConnection connection = file.getUri().toURL().openConnection();
                connection.setUseCaches(false);
                try (InputStream input = connection.getInputStream()) {
                    bytes(fixtures.get(file.getPath()), readAll(input), "original URI bytes");
                }
            }
            expectUnsupported(() -> files.clear());

            List<String> allExpected = new ArrayList<>(fixtures.keySet());
            Collections.sort(allExpected);
            equal(allExpected, paths(source.read("", true)), "root enumeration excludes directories");
            equal(allExpected, paths(source.read(".", true)), "dot root enumeration");
            checkSingleFile(source, TEXT, "a-first.txt", fixtures.get(TEXT));
            checkSingleFile(source, "standalone.json", "standalone.json", fixtures.get("standalone.json"));
            try (BufferedReader reader = source.read(TEXT, false).get(0).openReader(StandardCharsets.UTF_8)) {
                equal("\u7b2c\u4e00\u884c", reader.readLine(), "UTF-8 first line");
                equal("Gr\u00fc\u00dfe \ud83c\udf0d", reader.readLine(), "UTF-8 supplementary character");
                equal(null, reader.readLine(), "UTF-8 reader EOF");
            }
            byte[] binary = source.readFile(BINARY);
            bytes(fixtures.get(BINARY), binary, "readFile binary fidelity");
            binary[0] ^= 127;
            bytes(fixtures.get(BINARY), source.readFile(BINARY), "readFile transfers independent bytes");

            for (String missing : Arrays.asList("missing", "assets/demo/missing", MODULE + "/missing.txt")) {
                equal(0, source.read(missing, true).size(), "missing directory");
                equal(0, source.read(missing, false).size(), "missing single file");
                equal(null, source.readFile(missing), "missing readFile");
            }
            equal(0, source.read("assets/demo/empty", true).size(), "existing empty directory");
            expectIO(() -> source.read("assets/demo/empty", false));
            expectIO(() -> source.readFile("assets/demo/empty"));
            expectIO(() -> source.read(MODULE, false));
            expectIO(() -> source.readFile(MODULE));
            expectIO(() -> source.read(TEXT, true));
            expectIO(() -> source.read(TEXT + "/child", true));
            expectIO(() -> source.readFile(TEXT + "/child"));
            expectIO(() -> source.readFile(""));
            for (String unsafe : Arrays.asList("../outside", "../../", "assets/../outside", "/absolute",
                    "\\absolute", "C:\\absolute", "C:relative", "\\\\server\\pack", "file:/outside", "nul\0path")) {
                expectIO(() -> source.read(unsafe, true));
                expectIO(() -> source.read(unsafe, false));
                expectIO(() -> source.readFile(unsafe));
            }
            source.close();
            source.close();
            expectIO(() -> source.read(MODULE, true));
            expectIO(() -> source.read(TEXT, false));
            expectIO(() -> source.readFile(TEXT));
            checkSnapshots(files, fixtures, scheme);
            return files;
        } finally {
            source.close();
        }
    }

    private static void checkSingleFile(PackSource source, String path, String relative,
                                        byte[] expected) throws IOException {
        List<PackFile> files = source.read(path, false);
        equal(1, files.size(), "single-file count");
        PackFile file = files.get(0);
        equal(path, file.getPath(), "single-file pack path");
        equal(relative, file.getRelativePath(), "single-file relative filename");
        bytes(expected, file.getBytes(), "single-file bytes");
    }

    private static void checkSnapshots(List<PackFile> files, Map<String, byte[]> fixtures,
                                       String scheme) throws IOException {
        for (PackFile file : files) {
            byte[] expected = fixtures.get(file.getPath());
            check(expected != null, "unexpected snapshot path: " + file.getPath());
            equal((long) expected.length, file.getSize(), "snapshot size");
            equal(scheme, file.getUri().getScheme(), "original URI scheme");
            bytes(expected, file.getBytes(), "snapshot bytes");
            byte[] copy = file.getBytes();
            if (copy.length != 0) {
                copy[0] ^= 127;
                bytes(expected, file.getBytes(), "snapshot defensive copy");
            }
            try (InputStream input = file.openStream()) {
                bytes(expected, readAll(input), "independent snapshot stream");
            }
            // A second stream starts at the beginning even after the first closes.
            try (InputStream input = file.openStream()) {
                equal(expected.length == 0 ? -1 : expected[0] & 255, input.read(), "fresh stream position");
            }
            if (file.getPath().equals(TEXT)) {
                try (BufferedReader reader = file.openReader(StandardCharsets.UTF_8)) {
                    equal("\u7b2c\u4e00\u884c", reader.readLine(), "snapshot reader after close");
                }
            }
        }
    }

    private static void checkInvalidSources(Path temporary) throws Exception {
        expectIO(() -> PackSource.open(temporary.resolve("missing-source")));
        Path invalid = temporary.resolve("not-a-zip.jar");
        Files.write(invalid, new byte[] {1, 2, 3, 4});
        expectIO(() -> PackSource.open(invalid));
        Files.delete(invalid);

        List<List<String>> invalidNames = Arrays.asList(
                Collections.singletonList("../escape.txt"),
                Collections.singletonList("/absolute.txt"),
                Collections.singletonList("C:/absolute.txt"),
                Arrays.asList("same.txt", "./same.txt"),
                Arrays.asList("file", "file/child.txt"),
                Arrays.asList("directory", "directory/"));
        for (int index = 0; index < invalidNames.size(); index++) {
            Path archive = temporary.resolve("invalid-" + index + ".zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
                for (String name : invalidNames.get(index)) {
                    output.putNextEntry(new ZipEntry(name));
                    output.closeEntry();
                }
            }
            expectIO(() -> PackSource.open(archive));
            Path moved = archive.resolveSibling("invalid-moved-" + index + ".zip");
            Files.move(archive, moved);
            Files.delete(moved);
        }
    }

    private static void checkSymbolicLinks(Path temporary) throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.write(outside.resolve("secret.txt"), new byte[] {9, 8, 7});
        Path directory = Files.createDirectory(temporary.resolve("links"));
        Files.write(directory.resolve("ordinary.txt"), new byte[] {1});
        try {
            Files.createSymbolicLink(directory.resolve("file-link.txt"), outside.resolve("secret.txt"));
            Files.createSymbolicLink(directory.resolve("directory-link"), outside);
            Files.createSymbolicLink(directory.resolve("missing-link"), outside.resolve("missing"));
            Files.createSymbolicLink(temporary.resolve("root-link"), directory);
        } catch (UnsupportedOperationException | FileSystemException | SecurityException unavailable) {
            // Windows may require a privilege not held by the test process.
            return;
        }
        try (PackSource source = PackSource.open(directory)) {
            equal(Collections.singletonList("ordinary.txt"), paths(source.read("", true)),
                    "enumeration excludes links and their targets");
            expectIO(() -> source.readFile("file-link.txt"));
            expectIO(() -> source.read("file-link.txt", false));
            expectIO(() -> source.read("directory-link", true));
            expectIO(() -> source.readFile("directory-link/secret.txt"));
            expectIO(() -> source.readFile("missing-link"));
        }
        try (PackSource source = PackSource.open(temporary.resolve("root-link"))) {
            equal(Collections.singletonList("ordinary.txt"), paths(source.read("", true)),
                    "source symlink establishes the resolved pack root");
        }
    }

    private static Map<String, byte[]> fixtures() {
        Map<String, byte[]> fixtures = new LinkedHashMap<>();
        fixtures.put(MODULE + "/z-last.txt", "last\n".getBytes(StandardCharsets.UTF_8));
        byte[] binary = new byte[1024];
        for (int index = 0; index < binary.length; index++) {
            binary[index] = (byte) index;
        }
        fixtures.put(BINARY, binary);
        fixtures.put("standalone.json", "{\"enabled\":true}\n".getBytes(StandardCharsets.UTF_8));
        fixtures.put(MODULE + "/space #%.txt", "escaped URI\n".getBytes(StandardCharsets.UTF_8));
        fixtures.put(TEXT, "\u7b2c\u4e00\u884c\nGr\u00fc\u00dfe \ud83c\udf0d\n".getBytes(StandardCharsets.UTF_8));
        fixtures.put("assets/demo/module-sibling/other.txt", new byte[] {5});
        fixtures.put(MODULE + "/nested/empty.bin", new byte[0]);
        return fixtures;
    }

    private static void writeArchive(Path archive, Map<String, byte[]> fixtures) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("assets/demo/empty/"));
            output.closeEntry();
            // Parents are intentionally implicit and files intentionally unsorted.
            for (Map.Entry<String, byte[]> fixture : fixtures.entrySet()) {
                output.putNextEntry(new ZipEntry(fixture.getKey()));
                output.write(fixture.getValue());
                output.closeEntry();
            }
        }
    }

    private static List<String> paths(List<PackFile> files) {
        List<String> paths = new ArrayList<>(files.size());
        for (PackFile file : files) {
            paths.add(file.getPath());
        }
        return paths;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void expectIO(IOAction action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Expected IOException");
    }

    private static void expectUnsupported(IOAction action) throws Exception {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected UnsupportedOperationException");
    }

    private static void bytes(byte[] expected, byte[] actual, String message) {
        check(Arrays.equals(expected, actual), message);
    }

    private static void equal(Object expected, Object actual, String message) {
        check(expected == null ? actual == null : expected.equals(actual),
                message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface IOAction {
        void run() throws Exception;
    }
}
