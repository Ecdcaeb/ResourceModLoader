package rml.compiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import rml.io.PackFile;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Detached source resource; a PackFile-backed resource avoids copying its snapshot again. */
public final class SourceResource {
    private final String type;
    private final String id;
    private final String path;
    private final byte[] bytes;
    private final PackFile snapshot;

    public SourceResource(String type, String id, String path, byte[] bytes) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = Ids.parse(id);
        this.path = Objects.requireNonNull(path, "path");
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
        this.snapshot = null;
    }

    private SourceResource(String type, String id, PackFile snapshot) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = Ids.parse(id);
        this.path = snapshot.getPath();
        this.bytes = null;
        this.snapshot = snapshot;
    }

    public static SourceResource fromSnapshot(String type, String id, PackFile snapshot) {
        return new SourceResource(type, id, Objects.requireNonNull(snapshot, "snapshot"));
    }

    public String getType() { return type; }
    public String getId() { return id; }
    public String getPath() { return path; }
    public String getText() { return snapshot == null ? new String(bytes, StandardCharsets.UTF_8) : snapshot.getText(StandardCharsets.UTF_8); }
    public byte[] getBytes() { return snapshot == null ? bytes.clone() : snapshot.getBytes(); }

    public JsonElement getJson() {
        try {
            return new JsonParser().parse(getText());
        } catch (JsonParseException e) {
            throw new CompileException("invalid_json", path, 0, "Invalid JSON: " + e.getMessage(), e);
        }
    }
}
