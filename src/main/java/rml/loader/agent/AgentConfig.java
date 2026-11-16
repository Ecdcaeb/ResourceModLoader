package rml.loader.agent;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Process configuration; credentials remain in the child process environment. */
final class AgentConfig {
    final Path file;
    final boolean enabled;
    final List<String> python;

    private AgentConfig(Path file, JsonObject json) {
        this.file = file;
        enabled = json.has("enabled") && json.get("enabled").getAsBoolean();
        List<String> command = new ArrayList<>();
        if (json.has("python")) {
            for (JsonElement value : json.getAsJsonArray("python")) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isEmpty())
                    throw new IllegalArgumentException("python must be a nonempty array of command arguments");
                command.add(value.getAsString());
            }
        } else command.add("python");
        if (command.isEmpty()) throw new IllegalArgumentException("python command must not be empty");
        python = Collections.unmodifiableList(command);
    }

    static AgentConfig load(Path gameDirectory) throws IOException {
        Path file = gameDirectory.resolve("config/rml-agent.json");
        if (!Files.exists(file)) {
            JsonObject json = new JsonObject();
            json.addProperty("enabled", false);
            JsonArray python = new JsonArray(); python.add("python"); json.add("python", python);
            json.addProperty("compiler", "rml-compiler.jar");
            json.addProperty("service_host", "127.0.0.1");
            json.addProperty("service_port", 0);
            JsonObject model = new JsonObject();
            model.addProperty("provider", "gemini"); model.addProperty("name", ""); model.addProperty("api_key_env", "GOOGLE_API_KEY");
            json.add("model", model);
            writePrivate(file, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new AgentConfig(file, new JsonParser().parse(reader).getAsJsonObject());
        }
    }

    static void writePrivate(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".rml-agent-", ".tmp");
        try {
            if (Files.getFileStore(temporary).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(temporary, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
