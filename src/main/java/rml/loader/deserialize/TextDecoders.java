package rml.loader.deserialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;

import java.util.Map;

/** Text components with recursively decoded siblings and hover contents. */
final class TextDecoders {
    private static final java.util.Set<String> FORMATTED_FIELDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "text", "color", "bold", "italic", "underline", "strikethrough", "obfuscated", "insertion"));
    private static final java.util.Set<String> TRANSLATED_FIELDS = new java.util.HashSet<>(java.util.Arrays.asList("key", "args"));

    private TextDecoders() { }

    static AbstractDeserializer<ITextComponent> register(DeserializerManager manager) {
        AbstractDeserializer<ITextComponent> defaultEntry = new AbstractDeserializer<>(new ResourceLocation("minecraft", "textcomponent"),
                ITextComponent.class, value -> text(manager, value));
        manager.addDefaultEntry(defaultEntry);
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "formatted"),
                ITextComponent.class, value -> formatted(manager, value)));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "translated"),
                ITextComponent.class, value -> translated(manager, value)));
        return defaultEntry;
    }

    private static ITextComponent formatted(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        JsonObject object = NativeTypeDecoders.object(value);
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if ("type".equals(field.getKey())) continue;
            if (!FORMATTED_FIELDS.contains(field.getKey())) throw NativeTypeDecoders.error(value, "Unsupported text field: " + field.getKey());
        }
        JsonObject vanilla = new JsonObject();
        vanilla.add("text", object.has("text") ? object.get("text") : new com.google.gson.JsonPrimitive(""));
        ITextComponent component = manager.decode(ITextComponent.class, vanilla);
        net.minecraft.util.text.Style style = component.getStyle();
        if (object.has("color")) {
            net.minecraft.util.text.TextFormatting color = net.minecraft.util.text.TextFormatting.getValueByName(
                    manager.decode(String.class, object.get("color")).toLowerCase(java.util.Locale.ROOT));
            if (color == null) throw NativeTypeDecoders.error(value, "Unknown text color");
            style.setColor(color);
        }
        if (object.has("bold")) style.setBold(manager.decode(Boolean.class, object.get("bold")));
        if (object.has("italic")) style.setItalic(manager.decode(Boolean.class, object.get("italic")));
        if (object.has("underline")) style.setUnderlined(manager.decode(Boolean.class, object.get("underline")));
        if (object.has("strikethrough")) style.setStrikethrough(manager.decode(Boolean.class, object.get("strikethrough")));
        if (object.has("obfuscated")) style.setObfuscated(manager.decode(Boolean.class, object.get("obfuscated")));
        if (object.has("insertion")) style.setInsertion(manager.decode(String.class, object.get("insertion")));
        return component;
    }

    private static ITextComponent translated(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        JsonObject object = NativeTypeDecoders.object(value);
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if ("type".equals(field.getKey())) continue;
            if (!TRANSLATED_FIELDS.contains(field.getKey())) throw NativeTypeDecoders.error(value, "Unsupported translation field: " + field.getKey());
        }
        if (!object.has("key")) throw NativeTypeDecoders.error(value, "Translation requires a key");
        JsonObject vanilla = new JsonObject();
        vanilla.addProperty("translate", manager.decode(String.class, required(object, "key")));
        if (object.has("args")) vanilla.add("with", object.get("args"));
        return manager.decode(ITextComponent.class, vanilla);
    }

    private static JsonElement required(JsonObject object, String field) throws JsonDeserializeException {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) throw NativeTypeDecoders.error(object, "Missing text field: " + field);
        return value;
    }

    private static ITextComponent text(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonArray()) {
            ITextComponent result = null;
            for (JsonElement child : value.getAsJsonArray()) {
                ITextComponent component = manager.decode(ITextComponent.class, child);
                if (result == null) result = component;
                else result.appendSibling(component);
            }
            if (result == null) throw NativeTypeDecoders.error(value, "Text component array must not be empty");
            return result;
        }
        JsonElement normalized = value;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject(), result = new JsonObject();
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                JsonElement child = field.getValue();
                if ("extra".equals(field.getKey()) || "with".equals(field.getKey())) {
                    if (!child.isJsonArray()) throw NativeTypeDecoders.error(value, "Text children must be an array");
                    JsonArray children = new JsonArray();
                    for (JsonElement entry : child.getAsJsonArray()) children.add(textJson(manager, entry));
                    child = children;
                } else if ("hoverEvent".equals(field.getKey()) && child.isJsonObject()) {
                    JsonObject hover = new JsonObject();
                    for (Map.Entry<String, JsonElement> entry : child.getAsJsonObject().entrySet())
                        hover.add(entry.getKey(), "value".equals(entry.getKey()) ? textJson(manager, entry.getValue()) : entry.getValue());
                    child = hover;
                }
                result.add(field.getKey(), child);
            }
            normalized = result;
        }
        ITextComponent result = ITextComponent.Serializer.jsonToComponent(normalized.toString());
        if (result == null) throw NativeTypeDecoders.error(value, "Text component must not be null");
        return result;
    }

    private static JsonElement textJson(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        return new com.google.gson.JsonParser().parse(ITextComponent.Serializer.componentToJson(
                manager.decode(ITextComponent.class, value)));
    }
}
