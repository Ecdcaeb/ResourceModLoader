package rml.compiler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Complete immutable build result. No destination file is touched during compilation. */
public final class CompiledPack {
    private final Map<String, byte[]> entries;
    private final int resourceCount;
    private final int functionCount;

    CompiledPack(Map<String, byte[]> entries, int resourceCount, int functionCount) {
        this.entries = Collections.unmodifiableMap(new TreeMap<>(entries));
        this.resourceCount = resourceCount;
        this.functionCount = functionCount;
    }

    public int getResourceCount() { return resourceCount; }
    public int getFunctionCount() { return functionCount; }

    public Map<String, byte[]> getEntries() {
        Map<String, byte[]> copy = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) copy.put(entry.getKey(), entry.getValue().clone());
        return Collections.unmodifiableMap(copy);
    }

    public void write(Path output) throws IOException {
        Path destination = output.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".rml-compile-", ".zip");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary); ZipOutputStream zip = new ZipOutputStream(stream)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    ZipEntry member = new ZipEntry(entry.getKey());
                    member.setTime(0L);
                    zip.putNextEntry(member);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
