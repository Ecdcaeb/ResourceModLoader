package rml.loader.content.gameplay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Map;

/** Rejects supplied options which a native consumer would otherwise silently discard. */
final class GameplayFields {
    private GameplayFields() { }
    static void only(JsonObject json, String... fields) {
        for (Map.Entry<String, JsonElement> field : json.entrySet()) {
            if (!"type".equals(field.getKey()) && !Arrays.asList(fields).contains(field.getKey())) throw new IllegalArgumentException("Unsupported gameplay field: " + field.getKey());
        }
    }
}
