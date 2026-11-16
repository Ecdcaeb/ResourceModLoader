package rml.loader.content.presentation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.loader.deserialize.NativeTypeDecoders;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import rml.loader.content.data.DataRules;

/** Reusable, recursively composable value codecs used by presentation resources and other modules. */
public final class PresentationCodecs {
    private PresentationCodecs() { }

    public static void register(DeserializerManager manager) {
        register(manager, null);
    }

    static void register(DeserializerManager manager, PresentationDeferredChildren children) {
        PresentationDialogs.register(manager);
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
                json -> decodeEntityVariant(manager, json, children)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), TrimMaterial.class,
                json -> decodeTrimMaterial(manager, json, children)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), TrimPattern.class,
                json -> decodeTrimPattern(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "default"), TrimDescription.class,
                json -> decodeTrimDescription(manager, json)));
    }

    public static ChatStyle decodeChatStyle(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "chat style", "prefix", "suffix", "color");
        return new ChatStyle(text(manager, object, "prefix", ""), text(manager, object, "suffix", ""),
                optionalString(manager, object, "color", "white"));
    }

    public static SoundPlayback decodeSoundPlayback(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "sound playback", "sound", "volume", "pitch", "duration", "range", "comparator");
        float volume = optionalNumber(manager, object, "volume", 1.0F);
        float pitch = optionalNumber(manager, object, "pitch", 1.0F);
        if (!Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0)
            throw new JsonDeserializeException(json, "sound volume must be finite and nonnegative and pitch must be finite and positive");
        int duration = object.has("duration") ? manager.decode(Integer.class, object.get("duration")) : 140;
        float range = optionalNumber(manager, object, "range", 64);
        int comparator = object.has("comparator") ? manager.decode(Integer.class, object.get("comparator")) : 1;
        if (duration < 1 || duration > 72000 || !Float.isFinite(range) || range <= 0 || range > 1024 || comparator < 0 || comparator > 15)
            throw new JsonDeserializeException(json, "duration must be 1..72000 ticks, range (0,1024], comparator 0..15");
        return new SoundPlayback(manager.decode(ResourceLocation.class, required(object, "sound")), volume, pitch, duration, range, comparator);
    }

    public static DialogDefinition decodeDialog(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "dialog", "title", "body", "actions", "inputs");
        ITextComponent title = text(manager, object, "title", null), body = text(manager, object, "body", "");
        DialogAction[] decoded = manager.decode(DialogAction[].class, required(object, "actions"));
        if (decoded.length < 1 || decoded.length > 64) throw new JsonDeserializeException(json, "dialog requires 1..64 actions");
        PresentationDialogs.Input[] inputs = object.has("inputs") ? manager.decode(PresentationDialogs.Input[].class, object.get("inputs")) : new PresentationDialogs.Input[0];
        if (inputs.length > 16) throw new JsonDeserializeException(json, "dialog supports at most 16 inputs");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (PresentationDialogs.Input input : inputs) if (!names.add(input.key)) throw new JsonDeserializeException(json, "duplicate input " + input.key);
        return new DialogDefinition(title, body, decoded, inputs);
    }

    public static DialogAction decodeMessageAction(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "message action", "label", "text");
        return new MessageAction(text(manager, object, "label", null), text(manager, object, "text", null));
    }

    public static DialogAction decodeFunctionAction(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "function action", "label", "id");
        return new FunctionAction(text(manager, object, "label", null), manager.decode(ResourceLocation.class, required(object, "id")));
    }


    public static SurfaceDefinition decodeSurface(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "presentation surface", "texture", "color");
        ResourceLocation texture = texture(manager, required(object, "texture"));
        String colorText = optionalString(manager, object, "color", "ffffff");
        if (colorText.startsWith("#")) colorText = colorText.substring(1);
        if (!colorText.matches("[0-9a-fA-F]{6}")) throw new JsonDeserializeException(json, "surface color must be six hexadecimal digits");
        return new SurfaceDefinition(texture, Integer.parseInt(colorText, 16));
    }

    public static PaintingVariant decodePaintingVariant(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "painting variant", "texture", "width", "height");
        ResourceLocation texture = texture(manager, required(object, "texture"));
        int width = object.has("width") ? manager.decode(Integer.class, object.get("width")) : 1;
        int height = object.has("height") ? manager.decode(Integer.class, object.get("height")) : 1;
        if (width < 1 || height < 1 || width > 16 || height > 16)
            throw new JsonDeserializeException(json, "painting width and height must be between 1 and 16 blocks");
        return new PaintingVariant(texture, width, height);
    }

    public static TrimMaterial decodeTrimMaterial(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        return decodeTrimMaterial(manager, json, null);
    }

    private static TrimMaterial decodeTrimMaterial(DeserializerManager manager, JsonElement json, PresentationDeferredChildren children) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "trim material", "palette_id", "texture", "description", "ingredient");
        ResourceLocation palette = manager.decode(ResourceLocation.class, required(object, "palette_id"));
        TrimDescription description = object.has("description")
                ? manager.decode(TrimDescription.class, object.get("description")) : new TrimDescription(0xFFFFFF, "");
        NativeTypeDecoders.ItemMatcher ingredient = object.has("ingredient")
                ? children == null ? manager.decode(NativeTypeDecoders.ItemMatcher.class, object.get("ingredient"))
                    : children.ingredient(manager, object.get("ingredient")) : null;
        ResourceLocation texture = object.has("texture") ? texture(manager, object.get("texture"))
                : new ResourceLocation(palette.getNamespace(), "textures/trims/color_palettes/" + palette.getPath() + ".png");
        return new TrimMaterial(palette, texture, description, ingredient);
    }

    public static TrimPattern decodeTrimPattern(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "trim pattern", "asset_id", "texture", "leggings_texture", "decal", "template_item", "description");
        ResourceLocation asset = manager.decode(ResourceLocation.class, required(object, "asset_id"));
        boolean decal = object.has("decal") && manager.decode(Boolean.class, object.get("decal"));
        ResourceLocation templateItem = object.has("template_item")
                ? manager.decode(ResourceLocation.class, object.get("template_item")) : null;
        TrimDescription description = object.has("description")
                ? manager.decode(TrimDescription.class, object.get("description")) : new TrimDescription(0xFFFFFF, "");
        ResourceLocation texture = object.has("texture") ? texture(manager, object.get("texture"))
                : new ResourceLocation(asset.getNamespace(), "textures/trims/models/armor/" + asset.getPath() + ".png");
        ResourceLocation leggings = object.has("leggings_texture") ? texture(manager, object.get("leggings_texture"))
                : new ResourceLocation(asset.getNamespace(), "textures/trims/models/armor/" + asset.getPath() + "_leggings.png");
        return new TrimPattern(asset, texture, leggings, decal, templateItem, description);
    }

    public static TrimDescription decodeTrimDescription(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "trim description", "color", "translate");
        String color = optionalString(manager, object, "color", "ffffff");
        if (color.startsWith("#")) color = color.substring(1);
        if (!color.matches("[0-9a-fA-F]{6}")) throw new JsonDeserializeException(json, "trim description color must be six hexadecimal digits");
        return new TrimDescription(Integer.parseInt(color, 16), optionalString(manager, object, "translate", ""));
    }

    public static EntitySounds decodeEntitySounds(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "entity sounds", "ambient", "hurt", "death");
        SoundPlayback ambient = object.has("ambient") ? manager.decode(SoundPlayback.class, object.get("ambient")) : null;
        SoundPlayback hurt = object.has("hurt") ? manager.decode(SoundPlayback.class, object.get("hurt")) : null;
        SoundPlayback death = object.has("death") ? manager.decode(SoundPlayback.class, object.get("death")) : null;
        if (ambient == null && hurt == null && death == null) throw new JsonDeserializeException(json, "entity sounds require ambient, hurt, or death");
        return new EntitySounds(ambient, hurt, death);
    }

    public static EntityVariant decodeEntityVariant(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        return decodeEntityVariant(manager, json, null);
    }

    private static EntityVariant decodeEntityVariant(DeserializerManager manager, JsonElement json, PresentationDeferredChildren children) throws JsonDeserializeException {
        JsonObject object = requireObject(json, "entity variant", "entity", "texture", "sounds", "condition", "weight");
        ResourceLocation entity = manager.decode(ResourceLocation.class, required(object, "entity"));
        ResourceLocation texture = object.has("texture") ? texture(manager, object.get("texture")) : null;
        EntitySounds sounds = object.has("sounds") ? manager.decode(EntitySounds.class, object.get("sounds")) : null;
        DataRules.Test condition = object.has("condition")
                ? children == null ? manager.decode(DataRules.Test.class, object.get("condition"))
                    : children.condition(manager, object.get("condition")) : context -> true;
        int weight = object.has("weight") ? manager.decode(Integer.class, object.get("weight")) : 1;
        if (weight < 1 || weight > 1000000) throw new JsonDeserializeException(json, "variant weight must be 1..1000000");
        if (texture == null) throw new JsonDeserializeException(json, "entity variant requires a texture");
        return new EntityVariant(entity, texture, sounds, condition, weight);
    }

    private static JsonObject requireObject(JsonElement json, String name, String... fields) throws JsonDeserializeException {
        if (json == null || !json.isJsonObject()) throw new JsonDeserializeException(json, name + " must be an object");
        JsonObject object = json.getAsJsonObject();
        for (java.util.Map.Entry<String, JsonElement> field : object.entrySet())
            if (!"type".equals(field.getKey()) && !java.util.Arrays.asList(fields).contains(field.getKey()))
                throw new JsonDeserializeException(json, "unsupported " + name + " field " + field.getKey());
        return object;
    }

    private static ResourceLocation texture(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        ResourceLocation texture = manager.decode(ResourceLocation.class, value);
        String path = texture.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        if (!path.endsWith(".png")) path += ".png";
        return new ResourceLocation(texture.getNamespace(), path);
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

    static ITextComponent text(DeserializerManager manager, JsonObject object, String field, String fallback) throws JsonDeserializeException {
        if (!object.has(field)) {
            if (fallback == null) throw new JsonDeserializeException(object, "missing " + field);
            return new TextComponentString(fallback);
        }
        return manager.decode(PresentationDialogs.Text.class, object.get(field)).component;
    }

    public static final class ChatStyle {
        public final ITextComponent prefix, suffix;
        public final String color;
        public ChatStyle(ITextComponent prefix, ITextComponent suffix, String color) { this.prefix = prefix; this.suffix = suffix; this.color = color; }
    }

    public static final class SoundPlayback {
        public final ResourceLocation sound;
        public final float volume, pitch, range;
        public final int duration, comparator;
        public SoundPlayback(ResourceLocation sound, float volume, float pitch, int duration, float range, int comparator) {
            this.sound = sound; this.volume = volume; this.pitch = pitch; this.duration = duration; this.range = range; this.comparator = comparator;
        }
    }

    public interface DialogAction {
        ITextComponent label();
        void execute(PresentationDialogs.ActionContext context);
        default void validate(java.util.function.Predicate<ResourceLocation> dialogs, java.util.function.Predicate<ResourceLocation> functions) { }
    }

    public static final class MessageAction implements DialogAction {
        public final ITextComponent label, text;
        public MessageAction(ITextComponent label, ITextComponent text) { this.label = label; this.text = text; }
        @Override public ITextComponent label() { return label; }
        @Override public void execute(PresentationDialogs.ActionContext context) { context.player.sendMessage(text.createCopy()); }
    }

    public static final class FunctionAction implements DialogAction {
        public final ITextComponent label;
        public final ResourceLocation function;
        public FunctionAction(ITextComponent label, ResourceLocation function) { this.label = label; this.function = function; }
        @Override public ITextComponent label() { return label; }
        @Override public void execute(PresentationDialogs.ActionContext context) { context.invoke(function); }
        @Override public void validate(java.util.function.Predicate<ResourceLocation> dialogs, java.util.function.Predicate<ResourceLocation> functions) {
            if (!functions.test(function)) throw new IllegalArgumentException("Unknown dialog function " + function);
        }
    }

    public static final class DialogDefinition {
        public final ITextComponent title, body;
        public final DialogAction[] actions;
        public final PresentationDialogs.Input[] inputs;
        public DialogDefinition(ITextComponent title, ITextComponent body, DialogAction[] actions, PresentationDialogs.Input[] inputs) {
            this.title = title; this.body = body; this.actions = actions; this.inputs = inputs;
        }
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
        public final ResourceLocation paletteId, paletteTexture;
        public final TrimDescription description;
        public final NativeTypeDecoders.ItemMatcher ingredient;
        public TrimMaterial(ResourceLocation paletteId, ResourceLocation paletteTexture, TrimDescription description, NativeTypeDecoders.ItemMatcher ingredient) {
            this.paletteId = paletteId; this.paletteTexture = paletteTexture; this.description = description; this.ingredient = ingredient;
        }
    }

    public static final class TrimPattern {
        public final ResourceLocation assetId, templateItem, texture, leggingsTexture;
        public final boolean decal;
        public final TrimDescription description;
        public TrimPattern(ResourceLocation assetId, ResourceLocation texture, ResourceLocation leggingsTexture, boolean decal, ResourceLocation templateItem, TrimDescription description) {
            this.assetId = assetId; this.texture = texture; this.leggingsTexture = leggingsTexture; this.decal = decal; this.templateItem = templateItem; this.description = description;
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
        public final DataRules.Test condition;
        public final int weight;
        public EntityVariant(ResourceLocation entity, ResourceLocation texture, EntitySounds sounds, DataRules.Test condition, int weight) {
            this.entity = entity; this.texture = texture; this.sounds = sounds; this.condition = condition; this.weight = weight;
        }
    }
}
