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
    private TextDecoders() { }

    static AbstractDeserializer<ITextComponent> register(DeserializerManager manager) {
        return manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "textcomponent"),
                ITextComponent.class, value -> text(manager, value)));
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
