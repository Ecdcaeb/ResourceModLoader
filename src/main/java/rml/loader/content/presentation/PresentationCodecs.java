package rml.loader.content.presentation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.loader.deserialize.NativeTypeDecoders;

/** Reusable, recursively composable value codecs used by presentation resources and other modules. */
public final class PresentationCodecs {
    private PresentationCodecs() { }

    public static void register(DeserializerManager manager) {
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), ChatStyle.class,
                json -> decodeChatStyle(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), SoundPlayback.class,
                json -> decodeSoundPlayback(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), DialogDefinition.class,
                json -> decodeDialog(manager, json)));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "message"), DialogAction.class,
                json -> decodeMessageAction(manager, json)));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "function"), DialogAction.class,
                json -> decodeFunctionAction(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), SurfaceDefinition.class,
                json -> decodeSurface(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), PaintingVariant.class,
                json -> decodePaintingVariant(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), EntitySounds.class,
                json -> decodeEntitySounds(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), EntityVariant.class,
                json -> decodeEntityVariant(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), TrimMaterial.class,
                json -> decodeTrimMaterial(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), TrimPattern.class,
                json -> decodeTrimPattern(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), TrimDescription.class,
                json -> decodeTrimDescription(manager, json)));
    }

    public static ChatStyle decodeChatStyle(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "chat style");
        return new ChatStyle(optionalString(manager, object, "prefix", ""), optionalString(manager, object, "suffix", ""),
                optionalString(manager, object, "color", "white"));
    }

    public static SoundPlayback decodeSoundPlayback(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "sound playback");
        String id = requiredString(manager, object, "sound");
        float volume = optionalNumber(manager, object, "volume", 1.0F);
        float pitch = optionalNumber(manager, object, "pitch", 1.0F);
        if (!Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0)
            throw new JsonDeserializeException(json, "sound volume must be finite and nonnegative and pitch must be finite and positive");
        return new SoundPlayback(new ResourceLocation(id), volume, pitch);
    }

    public static DialogDefinition decodeDialog(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "dialog");
        String title = requiredString(manager, object, "title");
        String body = requiredString(manager, object, "body");
        if (title.length() > 256 || body.length() > 2048)
            throw new JsonDeserializeException(json, "dialog title or body exceeds the network limit");
        JsonElement actions = object.get("actions");
        if (actions == null) throw new JsonDeserializeException(json, "dialog requires actions");
        DialogAction[] decoded = manager.decode(DialogAction[].class, actions);
        if (decoded.length < 1 || decoded.length > 16) throw new JsonDeserializeException(actions, "dialog requires between 1 and 16 actions");
        for (DialogAction action : decoded) if (action.label().length() > 128)
            throw new JsonDeserializeException(actions, "dialog action labels are limited to 128 characters");
        return new DialogDefinition(title, body, decoded);
    }

    public static DialogAction decodeMessageAction(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "message action");
        String label = requiredString(manager, object, "label"), text = requiredString(manager, object, "text");
        if (label.length() > 128 || text.length() > 2048) throw new JsonDeserializeException(json, "message action text exceeds network limits");
        return new MessageAction(label, text);
    }

    public static DialogAction decodeFunctionAction(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "function action");
        String label = requiredString(manager, object, "label");
        if (label.length() > 128) throw new JsonDeserializeException(json, "function action label exceeds the network limit");
        return new FunctionAction(label, new ResourceLocation(requiredString(manager, object, "id")));
    }


    public static SurfaceDefinition decodeSurface(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "presentation surface");
        ResourceLocation texture = new ResourceLocation(requiredString(manager, object, "texture"));
        String colorText = optionalString(manager, object, "color", "ffffff");
        if (colorText.startsWith("#")) colorText = colorText.substring(1);
        if (!colorText.matches("[0-9a-fA-F]{6}")) throw new JsonDeserializeException(json, "surface color must be six hexadecimal digits");
        return new SurfaceDefinition(texture, Integer.parseInt(colorText, 16));
    }

    public static PaintingVariant decodePaintingVariant(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "painting variant");
        ResourceLocation texture = new ResourceLocation(requiredString(manager, object, "texture"));
        int width = object.has("width") ? manager.decode(Integer.class, object.get("width")) : 1;
        int height = object.has("height") ? manager.decode(Integer.class, object.get("height")) : 1;
        if (width < 1 || height < 1 || width > 16 || height > 16)
            throw new JsonDeserializeException(json, "painting width and height must be between 1 and 16 blocks");
        return new PaintingVariant(texture, width, height);
    }

    public static TrimMaterial decodeTrimMaterial(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "trim material");
        ResourceLocation palette = manager.decode(ResourceLocation.class, required(object, "palette_id"));
        TrimDescription description = object.has("description")
                ? manager.decode(TrimDescription.class, object.get("description")) : new TrimDescription(0xFFFFFF, "");
        NativeTypeDecoders.ItemMatcher ingredient = object.has("ingredient")
                ? manager.decode(NativeTypeDecoders.ItemMatcher.class, object.get("ingredient")) : null;
        return new TrimMaterial(palette, description, ingredient);
    }

    public static TrimPattern decodeTrimPattern(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "trim pattern");
        ResourceLocation asset = manager.decode(ResourceLocation.class, required(object, "asset_id"));
        boolean decal = object.has("decal") && manager.decode(Boolean.class, object.get("decal"));
        ResourceLocation templateItem = object.has("template_item")
                ? manager.decode(ResourceLocation.class, object.get("template_item")) : null;
        TrimDescription description = object.has("description")
                ? manager.decode(TrimDescription.class, object.get("description")) : new TrimDescription(0xFFFFFF, "");
        return new TrimPattern(asset, decal, templateItem, description);
    }

    public static TrimDescription decodeTrimDescription(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "trim description");
        String color = optionalString(manager, object, "color", "ffffff");
        if (color.startsWith("#")) color = color.substring(1);
        if (!color.matches("[0-9a-fA-F]{6}")) throw new JsonDeserializeException(json, "trim description color must be six hexadecimal digits");
        return new TrimDescription(Integer.parseInt(color, 16), optionalString(manager, object, "translate", ""));
    }

    public static EntitySounds decodeEntitySounds(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "entity sounds");
        SoundPlayback ambient = object.has("ambient") ? manager.decode(SoundPlayback.class, object.get("ambient")) : null;
        SoundPlayback hurt = object.has("hurt") ? manager.decode(SoundPlayback.class, object.get("hurt")) : null;
        SoundPlayback death = object.has("death") ? manager.decode(SoundPlayback.class, object.get("death")) : null;
        if (ambient == null && hurt == null && death == null) throw new JsonDeserializeException(json, "entity sounds require ambient, hurt, or death");
        return new EntitySounds(ambient, hurt, death);
    }

    public static EntityVariant decodeEntityVariant(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "entity variant");
        ResourceLocation entity = new ResourceLocation(requiredString(manager, object, "entity"));
        ResourceLocation texture = object.has("texture") ? new ResourceLocation(requiredString(manager, object, "texture")) : null;
        EntitySounds sounds = object.has("sounds") ? manager.decode(EntitySounds.class, object.get("sounds")) : null;
        if (texture == null && sounds == null) throw new JsonDeserializeException(json, "entity variant requires a texture or sound set");
        return new EntityVariant(entity, texture, sounds);
    }

    private static JsonObject requireObject(JsonElement json, String name) throws JsonDeserializeException {
        if (json == null || !json.isJsonObject()) throw new JsonDeserializeException(json, name + " must be an object");
        return json.getAsJsonObject();
    }

    private static String requiredString(DeserializerManager manager, JsonObject object, String field) throws JsonDeserializeException {
        JsonElement value = object.get(field);
        if (value == null) throw new JsonDeserializeException(object, "missing required field '" + field + "'");
        return manager.decode(String.class, value);
    }

    private static String optionalString(DeserializerManager manager, JsonObject object, String field, String fallback) throws JsonDeserializeException {
        JsonElement value = object.get(field);
        return value == null ? fallback : manager.decode(String.class, value);
    }

    private static float optionalNumber(DeserializerManager manager, JsonObject object, String field, float fallback) throws JsonDeserializeException {
        JsonElement value = object.get(field);
        return value == null ? fallback : manager.decode(Float.class, value);
    }

    private static JsonElement required(JsonObject object, String field) throws JsonDeserializeException {
        JsonElement value = object.get(field);
        if (value == null) throw new JsonDeserializeException(object, "missing required field '" + field + "'");
        return value;
    }

    public static final class ChatStyle {
        public final String prefix, suffix, color;
        public ChatStyle(String prefix, String suffix, String color) { this.prefix = prefix; this.suffix = suffix; this.color = color; }
    }

    public static final class SoundPlayback {
        public final ResourceLocation sound;
        public final float volume, pitch;
        public SoundPlayback(ResourceLocation sound, float volume, float pitch) { this.sound = sound; this.volume = volume; this.pitch = pitch; }
    }

    public interface DialogAction { String label(); }

    public static final class MessageAction implements DialogAction {
        public final String label, text;
        public MessageAction(String label, String text) { this.label = label; this.text = text; }
        @Override public String label() { return label; }
    }

    public static final class FunctionAction implements DialogAction {
        public final String label;
        public final ResourceLocation function;
        public FunctionAction(String label, ResourceLocation function) { this.label = label; this.function = function; }
        @Override public String label() { return label; }
    }

    public static final class DialogDefinition {
        public final String title, body;
        public final DialogAction[] actions;
        public DialogDefinition(String title, String body, DialogAction[] actions) { this.title = title; this.body = body; this.actions = actions; }
    }


    public static final class SurfaceDefinition {
        public final ResourceLocation texture;
        public final int color;
        public SurfaceDefinition(ResourceLocation texture, int color) { this.texture = texture; this.color = color; }
    }

    public static final class PaintingVariant {
        public final ResourceLocation texture;
        public final int width, height;
        public PaintingVariant(ResourceLocation texture, int width, int height) {
            this.texture = texture; this.width = width; this.height = height;
        }
    }

    public static final class TrimMaterial {
        public final ResourceLocation paletteId;
        public final TrimDescription description;
        public final NativeTypeDecoders.ItemMatcher ingredient;
        public TrimMaterial(ResourceLocation paletteId, TrimDescription description, NativeTypeDecoders.ItemMatcher ingredient) {
            this.paletteId = paletteId; this.description = description; this.ingredient = ingredient;
        }
    }

    public static final class TrimPattern {
        public final ResourceLocation assetId, templateItem;
        public final boolean decal;
        public final TrimDescription description;
        public TrimPattern(ResourceLocation assetId, boolean decal, ResourceLocation templateItem, TrimDescription description) {
            this.assetId = assetId; this.decal = decal; this.templateItem = templateItem; this.description = description;
        }
    }

    public static final class TrimDescription {
        public final int color;
        public final String translate;
        public TrimDescription(int color, String translate) { this.color = color; this.translate = translate; }
    }

    public static final class EntitySounds {
        public final SoundPlayback ambient, hurt, death;
        public EntitySounds(SoundPlayback ambient, SoundPlayback hurt, SoundPlayback death) {
            this.ambient = ambient; this.hurt = hurt; this.death = death;
        }
    }

    public static final class EntityVariant {
        public final ResourceLocation entity, texture;
        public final EntitySounds sounds;
        public EntityVariant(ResourceLocation entity, ResourceLocation texture, EntitySounds sounds) {
            this.entity = entity; this.texture = texture; this.sounds = sounds;
        }
    }
}
