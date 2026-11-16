package rml.io;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * An immutable, in-memory file snapshot. Streams and readers remain usable after
 * the source closes or its underlying files change. The URI identifies the
 * original file; opening that URI again is not part of the snapshot contract.
 */
public final class PackFile {
    private final String path;
    private final String relativePath;
    private final URI uri;
    private final byte[] bytes;

    // Only PackSource supplies newly allocated arrays, transferring ownership.
    PackFile(String path, String relativePath, URI uri, byte[] bytes) {
        this.path = path;
        this.relativePath = relativePath;
        this.uri = uri;
        this.bytes = bytes;
    }

    /** Returns the normalized, slash-separated path relative to the pack. */
    public String getPath() {
        return path;
    }

    /**
     * Returns the slash-separated path relative to the requested directory, or
     * the filename when the source was read as a single-file module.
     */
    public String getRelativePath() {
        return relativePath;
    }

    public URI getUri() {
        return uri;
    }

    public InputStream openStream() {
        return new ByteArrayInputStream(bytes);
    }

    /** Decoding uses the requested charset without altering the stored bytes. */
    public BufferedReader openReader(Charset charset) {
        return new BufferedReader(new InputStreamReader(openStream(),
                Objects.requireNonNull(charset, "charset")));
    }

    public String getText(Charset charset) {
        return new String(bytes, Objects.requireNonNull(charset, "charset"));
    }

    /** Returns an independent copy; callers cannot mutate the snapshot. */
    public byte[] getBytes() {
        return bytes.clone();
    }

    public long getSize() {
        return bytes.length;
    }
}
