package rml.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads regular files from a directory or ZIP/JAR into independent snapshots.
 * Directory reads recurse and sort by normalized pack-relative path using
 * String ordering. The empty path (or ".") denotes the pack root. Separators
 * are normalized to '/', while absolute paths, drive/URI prefixes, NULs and
 * parent traversal are rejected with IOException.
 *
 * <p>A missing path returns an empty list, or null from readFile. An existing
 * path of the wrong kind raises IOException. Directory symlinks are not
 * followed: enumeration excludes links, and directly addressing a link or a
 * path through one is rejected. A symlink supplied as the source itself is
 * resolved once when opening. ZIP entries are opaque payloads; link metadata
 * is never interpreted or followed. Conflicting or unsafe ZIP paths make the
 * archive invalid. Filesystem and archive errors are propagated.</p>
 *
 * <p>Close is idempotent and prevents further reads. Returned files retain no
 * archive handles and remain usable after close. Calls on a source are
 * serialized so close cannot interrupt an in-progress read.</p>
 */
public final class PackSource implements AutoCloseable {
    private final Path root;
    private final ZipFile archive;
    private final SortedMap<String, ZipEntry> entries;
    private final Set<String> directories;
    private boolean closed;

    private PackSource(Path root) {
        this.root = root;
        this.archive = null;
        this.entries = Collections.emptySortedMap();
        this.directories = Collections.emptySet();
    }

    private PackSource(Path root, ZipFile archive) throws IOException {
        this.root = root;
        this.archive = archive;
        this.entries = new TreeMap<>();
        this.directories = new TreeSet<>();
        directories.add("");
        Enumeration<? extends ZipEntry> contents = archive.entries();
        while (contents.hasMoreElements()) {
            ZipEntry entry = contents.nextElement();
            String path = normalize(entry.getName());
            if (entry.isDirectory()) {
                directories.add(path);
            } else {
                if (path.isEmpty() || entries.put(path, entry) != null) {
                    throw new IOException("Duplicate or empty ZIP file path: " + entry.getName());
                }
            }
            for (int slash = path.lastIndexOf('/'); slash >= 0;
                    slash = path.lastIndexOf('/', slash - 1)) {
                directories.add(path.substring(0, slash));
            }
        }
        for (String path : entries.keySet()) {
            if (directories.contains(path)) {
                throw new IOException("ZIP path is both a file and directory: " + path);
            }
        }
    }

    /** Opens an existing directory or a regular ZIP/JAR file. */
    public static PackSource open(Path source) throws IOException {
        Path root = Objects.requireNonNull(source, "source").toRealPath();
        BasicFileAttributes attributes = attributes(root);
        if (attributes.isDirectory()) {
            return new PackSource(root);
        }
        if (!attributes.isRegularFile()) {
            throw notRegular(root.toString());
        }
        ZipFile archive = new ZipFile(root.toFile(), StandardCharsets.UTF_8);
        try {
            return new PackSource(root, archive);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                archive.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * Reads all regular files beneath a directory, or exactly one regular file.
     * Returned lists and their snapshots are immutable.
     */
    public synchronized List<PackFile> read(String relativePath, boolean directory)
            throws IOException {
        ensureOpen();
        String path = normalize(relativePath);
        if (archive != null) {
            return readArchive(path, directory);
        }
        Path requested = resolve(path);
        if (requested == null) {
            return Collections.emptyList();
        }
        BasicFileAttributes attributes = attributes(requested);
        if (!directory) {
            requireRegular(requested, attributes);
            return Collections.singletonList(snapshot(requested, path, filename(path)));
        }
        if (!attributes.isDirectory()) {
            throw new NotDirectoryException(requested.toString());
        }
        final Map<String, Path> files = new TreeMap<>();
        Files.walkFileTree(requested, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                requireInsideRoot(directory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                if (attributes.isRegularFile()) {
                    requireInsideRoot(file);
                    files.put(packPath(file), file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        List<PackFile> result = new ArrayList<>(files.size());
        String prefix = path.isEmpty() ? "" : path + "/";
        for (Map.Entry<String, Path> file : files.entrySet()) {
            // Recheck paths after traversal before taking each snapshot.
            Path resolved = resolve(file.getKey());
            if (resolved == null) {
                throw new NoSuchFileException(file.getValue().toString());
            }
            requireRegular(resolved, attributes(resolved));
            result.add(snapshot(resolved, file.getKey(), file.getKey().substring(prefix.length())));
        }
        return Collections.unmodifiableList(result);
    }

    /** Returns exact bytes, or null only when the requested path is missing. */
    public synchronized byte[] readFile(String relativePath) throws IOException {
        ensureOpen();
        String path = normalize(relativePath);
        if (archive != null) {
            ZipEntry entry = entries.get(path);
            if (entry != null) {
                return readEntry(entry);
            }
            if (directories.contains(path)) {
                throw notRegular(path);
            }
            requireArchiveParents(path);
            return null;
        }
        Path file = resolve(path);
        if (file == null) {
            return null;
        }
        requireRegular(file, attributes(file));
        return Files.readAllBytes(file);
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            if (archive != null) {
                archive.close();
            }
        }
    }

    private List<PackFile> readArchive(String path, boolean directory) throws IOException {
        ZipEntry entry = entries.get(path);
        if (!directory) {
            if (entry != null) {
                return Collections.singletonList(snapshot(path, filename(path), entry));
            }
            if (directories.contains(path)) {
                throw notRegular(path);
            }
            requireArchiveParents(path);
            return Collections.emptyList();
        }
        if (entry != null) {
            throw new NotDirectoryException(path);
        }
        if (!directories.contains(path)) {
            requireArchiveParents(path);
            return Collections.emptyList();
        }
        String prefix = path.isEmpty() ? "" : path + "/";
        List<PackFile> result = new ArrayList<>();
        for (Map.Entry<String, ZipEntry> file : entries.tailMap(prefix).entrySet()) {
            if (!file.getKey().startsWith(prefix)) {
                break;
            }
            result.add(snapshot(file.getKey(), file.getKey().substring(prefix.length()), file.getValue()));
        }
        return Collections.unmodifiableList(result);
    }

    private void requireArchiveParents(String path) throws IOException {
        for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
            String parent = path.substring(0, slash);
            if (entries.containsKey(parent)) {
                throw new NotDirectoryException(parent);
            }
        }
    }

    private PackFile snapshot(Path file, String path, String relativePath) throws IOException {
        return new PackFile(path, relativePath, file.toUri(), Files.readAllBytes(file));
    }

    private PackFile snapshot(String path, String relativePath, ZipEntry entry) throws IOException {
        try {
            String entryPath = new URI(null, null, "/" + entry.getName(), null).toASCIIString();
            URI uri = URI.create("jar:" + root.toUri().toASCIIString().replace("!", "%21")
                    + "!" + entryPath);
            return new PackFile(path, relativePath, uri, readEntry(entry));
        } catch (URISyntaxException failure) {
            throw new IOException("Invalid ZIP entry URI: " + entry.getName(), failure);
        }
    }

    private byte[] readEntry(ZipEntry entry) throws IOException {
        long size = entry.getSize();
        if (size < 0 || size > Integer.MAX_VALUE - 8) {
            throw new IOException("Unsupported ZIP entry size for " + entry.getName() + ": " + size);
        }
        byte[] bytes = new byte[(int) size];
        try (InputStream input = archive.getInputStream(entry)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    throw new EOFException("Truncated ZIP entry: " + entry.getName());
                }
                if (count == 0) {
                    int value = input.read();
                    if (value < 0) {
                        throw new EOFException("Truncated ZIP entry: " + entry.getName());
                    }
                    bytes[offset++] = (byte) value;
                } else {
                    offset += count;
                }
            }
            if (input.read() != -1) {
                throw new IOException("ZIP entry exceeds its declared size: " + entry.getName());
            }
        }
        return bytes;
    }

    /** Resolves without following descendant links; only missing paths become null. */
    private Path resolve(String path) throws IOException {
        Path current = root;
        String[] components = path.isEmpty() ? new String[0] : path.split("/");
        try {
            BasicFileAttributes attributes = attributes(current);
            for (String component : components) {
                if (!attributes.isDirectory()) {
                    throw new NotDirectoryException(current.toString());
                }
                current = current.resolve(component);
                attributes = attributes(current);
                if (attributes.isSymbolicLink()) {
                    throw new IOException("Symbolic links cannot be read from a pack: " + current);
                }
                requireInsideRoot(current);
            }
            return current;
        } catch (NoSuchFileException missing) {
            return null;
        } catch (InvalidPathException invalid) {
            throw new IOException("Invalid pack path: " + path, invalid);
        }
    }

    private void requireInsideRoot(Path path) throws IOException {
        if (!path.toRealPath().startsWith(root)) {
            throw new IOException("Pack path resolves outside its source: " + path);
        }
    }

    private String packPath(Path file) throws IOException {
        StringBuilder result = new StringBuilder();
        for (Path component : root.relativize(file)) {
            String name = component.toString();
            if (name.indexOf('\\') >= 0) {
                throw new IOException("Ambiguous separator in pack filename: " + file);
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(name);
        }
        String path = result.toString();
        if (!normalize(path).equals(path)) {
            throw new IOException("Non-normalized pack filename: " + file);
        }
        return path;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Pack source is closed: " + root);
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static void requireRegular(Path path, BasicFileAttributes attributes) throws IOException {
        if (!attributes.isRegularFile()) {
            throw notRegular(path.toString());
        }
    }

    private static FileSystemException notRegular(String path) {
        return new FileSystemException(path, null, "Not a regular pack file");
    }

    private static String filename(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String normalize(String relativePath) throws IOException {
        Objects.requireNonNull(relativePath, "relativePath");
        String path = relativePath.replace('\\', '/');
        if (path.startsWith("/") || path.indexOf(':') >= 0 || path.indexOf('\0') >= 0) {
            throw new IOException("Unsafe pack path: " + relativePath);
        }
        StringBuilder normalized = new StringBuilder(path.length());
        for (String component : path.split("/")) {
            if (component.equals("..")) {
                throw new IOException("Parent traversal in pack path: " + relativePath);
            }
            if (component.isEmpty() || component.equals(".")) {
                continue;
            }
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(component);
        }
        return normalized.toString();
    }
}
