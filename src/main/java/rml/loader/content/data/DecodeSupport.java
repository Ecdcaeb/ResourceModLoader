package rml.loader.content.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rml.deserializer.JsonDeserializeException;

import java.util.Map;

/** Shared JSON field contract for the context decoder families. */
final class DecodeSupport {
    private DecodeSupport() { }

    static JsonElement required(JsonObject object, String name) throws JsonDeserializeException {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) throw error(object, "Missing field " + name);
        return value;
    }

    static void fields(JsonObject object, String... allowed) throws JsonDeserializeException {
        checkFields(object, false, allowed);
    }

    static void fieldsWithConditions(JsonObject object, String... allowed) throws JsonDeserializeException {
        checkFields(object, true, allowed);
    }

    private static void checkFields(JsonObject object, boolean conditions, String... allowed) throws JsonDeserializeException {
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if ("type".equals(field.getKey()) || conditions && "conditions".equals(field.getKey())) continue;
            boolean found = false;
            for (String name : allowed) {
                if (name.equals(field.getKey())) {
                    found = true;
                    break;
                }
            }
            if (!found) throw error(object, "Unsupported field " + field.getKey());
        }
    }

    static JsonObject object(JsonElement value) throws JsonDeserializeException {
        if (value == null || !value.isJsonObject()) throw error(value, "Expected an object");
        return value.getAsJsonObject();
    }

    static JsonDeserializeException error(JsonElement value, String message) {
        return new JsonDeserializeException(value, message);
    }
}
