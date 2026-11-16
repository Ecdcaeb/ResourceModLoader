package rml.loader.content.presentation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import java.util.Map;
import java.util.function.Predicate;

/** Shared authoring types; only validated values and server-owned actions reach execution. */
public final class PresentationDialogs {
    private PresentationDialogs() { }

    static void register(DeserializerManager manager) {
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "text"), Text.class, json -> decodeText(manager, json)));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "component"), Text.class, json -> {
            JsonObject object = object(json); fields(object, "value");
            return new Text(manager.decode(ITextComponent.class, required(object, "value")));
        }));
        for (Kind kind : Kind.values()) manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", kind.name().toLowerCase(java.util.Locale.ROOT)), Input.class,
                json -> decodeInput(manager, json, kind)));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "sequence"), PresentationCodecs.DialogAction.class, json -> {
            JsonObject object = object(json);
            fields(object, "label", "actions");
            PresentationCodecs.DialogAction[] actions = manager.decode(PresentationCodecs.DialogAction[].class, required(object, "actions"));
            if (actions.length < 1 || actions.length > 64) throw new JsonDeserializeException(json, "sequence requires 1..64 actions");
            return new Sequence(PresentationCodecs.text(manager, object, "label", null), actions);
        }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "open_dialog"), PresentationCodecs.DialogAction.class, json -> {
            JsonObject object = object(json);
            fields(object, "label", "id");
            return new Navigate(PresentationCodecs.text(manager, object, "label", null), manager.decode(ResourceLocation.class, required(object, "id")));
        }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "close"), PresentationCodecs.DialogAction.class,
                json -> { JsonObject object = object(json); fields(object, "label"); return new Close(object.has("label") ? PresentationCodecs.text(manager, object, "label", null) : new TextComponentTranslation("gui.cancel")); }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "confirmation"), PresentationCodecs.DialogAction.class, json -> {
            JsonObject object = object(json);
            fields(object, "label", "title", "body", "yes", "no");
            return new Confirmation(PresentationCodecs.text(manager, object, "label", null), PresentationCodecs.text(manager, object, "title", null),
                    PresentationCodecs.text(manager, object, "body", ""), manager.decode(PresentationCodecs.DialogAction.class, required(object, "yes")),
                    object.has("no") ? manager.decode(PresentationCodecs.DialogAction.class, object.get("no")) : new Close(new TextComponentTranslation("gui.cancel")));
        }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "dialog_list"), PresentationCodecs.DialogAction.class, json -> {
            JsonObject object = object(json);
            fields(object, "label", "title", "dialogs");
            PresentationCodecs.DialogAction[] entries = manager.decode(PresentationCodecs.DialogAction[].class, required(object, "dialogs"));
            if (entries.length < 1 || entries.length > 64) throw new JsonDeserializeException(json, "dialog list requires 1..64 entries");
            return new DialogList(PresentationCodecs.text(manager, object, "label", null), PresentationCodecs.text(manager, object, "title", null), entries);
        }));
    }

    public static final class Text {
        public final ITextComponent component;
        public Text(ITextComponent component) {
            if (component == null || ITextComponent.Serializer.componentToJson(component).length() > 16384) throw new IllegalArgumentException("Text component exceeds 16384 characters");
            this.component = component;
        }
    }

    private static Text decodeText(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        if (json.isJsonPrimitive()) return new Text(new TextComponentString(manager.decode(String.class, json)));
        if (json.isJsonArray()) {
            ITextComponent result = new TextComponentString("");
            for (Text child : manager.decode(Text[].class, json)) result.appendSibling(child.component.createCopy());
            return new Text(result);
        }
        JsonObject object = object(json);
        ITextComponent component;
        if (object.has("translate")) {
            Text[] children = object.has("with") ? manager.decode(Text[].class, object.get("with")) : new Text[0];
            Object[] args = new Object[children.length];
            for (int i = 0; i < args.length; i++) args[i] = children[i].component;
            component = new TextComponentTranslation(manager.decode(String.class, object.get("translate")), args);
        } else component = new TextComponentString(manager.decode(String.class, required(object, "text")));
        if (object.has("color")) {
            net.minecraft.util.text.TextFormatting color = net.minecraft.util.text.TextFormatting.getValueByName(manager.decode(String.class, object.get("color")));
            if (color == null || !color.isColor()) throw new JsonDeserializeException(json, "unknown text color");
            component.getStyle().setColor(color);
        }
        if (object.has("bold")) component.getStyle().setBold(manager.decode(Boolean.class, object.get("bold")));
        if (object.has("italic")) component.getStyle().setItalic(manager.decode(Boolean.class, object.get("italic")));
        if (object.has("underlined")) component.getStyle().setUnderlined(manager.decode(Boolean.class, object.get("underlined")));
        if (object.has("strikethrough")) component.getStyle().setStrikethrough(manager.decode(Boolean.class, object.get("strikethrough")));
        if (object.has("obfuscated")) component.getStyle().setObfuscated(manager.decode(Boolean.class, object.get("obfuscated")));
        if (object.has("extra")) for (Text child : manager.decode(Text[].class, object.get("extra"))) component.appendSibling(child.component.createCopy());
        for (Map.Entry<String, JsonElement> field : object.entrySet()) {
            if (!java.util.Arrays.asList("type", "text", "translate", "with", "color", "bold", "italic", "underlined", "strikethrough", "obfuscated", "extra").contains(field.getKey()))
                throw new JsonDeserializeException(json, "unsupported text field " + field.getKey());
        }
        return new Text(component);
    }

    public enum Kind { TEXT, BOOLEAN, CHOICE, NUMBER }

    public static class Input {
        public final String key, initial;
        public final ITextComponent label;
        public final Kind kind;
        public final int maxLength;
        public final double minimum, maximum, step;
        public final String[] choices;
        public Input(String key, ITextComponent label, Kind kind, String initial, int maxLength, double minimum, double maximum, double step, String[] choices) {
            this.key = key; this.label = label; this.kind = kind; this.initial = initial; this.maxLength = maxLength;
            this.minimum = minimum; this.maximum = maximum; this.step = step; this.choices = choices;
        }
        public void validate(String value, NBTTagCompound result) {
            if (value == null || value.length() > maxLength) throw new IllegalArgumentException("Invalid value for " + key);
            for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException("Control characters are not allowed in " + key);
            switch (kind) {
                case TEXT: result.setString(key, value); break;
                case BOOLEAN:
                    if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("Expected boolean for " + key);
                    result.setBoolean(key, Boolean.parseBoolean(value)); break;
                case CHOICE:
                    if (!java.util.Arrays.asList(choices).contains(value)) throw new IllegalArgumentException("Unknown choice for " + key);
                    result.setString(key, value); break;
                case NUMBER:
                    double number;
                    try { number = Double.parseDouble(value); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Expected number for " + key); }
                    double offset = (number - minimum) / step;
                    if (!Double.isFinite(number) || !Double.isFinite(offset) || number < minimum || number > maximum || Math.abs(offset - Math.rint(offset)) > 0.000001)
                        throw new IllegalArgumentException("Number out of range or step for " + key);
                    result.setDouble(key, number); break;
                default: throw new IllegalStateException("Unknown input kind");
            }
        }
        void write(PacketBuffer out) {
            out.writeString(key); out.writeTextComponent(label); out.writeVarInt(kind.ordinal()); out.writeString(initial);
            out.writeVarInt(maxLength); out.writeDouble(minimum); out.writeDouble(maximum); out.writeDouble(step);
            out.writeVarInt(choices.length); for (String choice : choices) out.writeString(choice);
        }
        static Input read(PacketBuffer in) {
            String key = in.readString(64); ITextComponent label = readText(in); int ordinal = in.readVarInt();
            if (ordinal < 0 || ordinal >= Kind.values().length) throw new IllegalArgumentException("Invalid input kind");
            String initial = in.readString(1024); int maxLength = in.readVarInt();
            double minimum = in.readDouble(), maximum = in.readDouble(), step = in.readDouble();
            int count = in.readVarInt(); if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid choice count");
            String[] choices = new String[count]; for (int i = 0; i < count; i++) choices[i] = in.readString(128);
            return new Input(key, label, Kind.values()[ordinal], initial, maxLength, minimum, maximum, step, choices);
        }
    }

    static ITextComponent readText(PacketBuffer in) {
        try { return in.readTextComponent(); } catch (java.io.IOException ex) { throw new IllegalArgumentException("Invalid component", ex); }
    }

    private static Input decodeInput(DeserializerManager manager, JsonElement json, Kind kind) throws JsonDeserializeException {
        JsonObject object = object(json);
        java.util.List<String> allowed = new java.util.ArrayList<>(java.util.Arrays.asList("key", "label", "initial"));
        if (kind == Kind.TEXT) allowed.add("max_length");
        if (kind == Kind.CHOICE) allowed.add("choices");
        if (kind == Kind.NUMBER) allowed.addAll(java.util.Arrays.asList("min", "max", "step"));
        fields(object, allowed.toArray(new String[0]));
        String key = manager.decode(String.class, required(object, "key"));
        if (!key.matches("[a-zA-Z_][a-zA-Z0-9_]{0,63}")) throw new JsonDeserializeException(json, "invalid input key");
        int length = object.has("max_length") ? manager.decode(Integer.class, object.get("max_length")) : 256;
        double minimum = object.has("min") ? manager.decode(Double.class, object.get("min")) : 0;
        double maximum = object.has("max") ? manager.decode(Double.class, object.get("max")) : 100;
        double step = object.has("step") ? manager.decode(Double.class, object.get("step")) : 1;
        String[] choices = object.has("choices") ? manager.decode(String[].class, object.get("choices")) : new String[0];
        if (length < 1 || length > 1024 || !Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(step) || minimum > maximum || step <= 0
                || choices.length > 64 || (kind == Kind.CHOICE && choices.length == 0)) throw new JsonDeserializeException(json, "invalid input constraints");
        java.util.Set<String> unique = new java.util.HashSet<>();
        for (String choice : choices) if (choice.length() > 128 || !unique.add(choice)) throw new JsonDeserializeException(json, "invalid or duplicate choice");
        String initial = object.has("initial") ? manager.decode(String.class, object.get("initial"))
                : kind == Kind.BOOLEAN ? "false" : kind == Kind.NUMBER ? Double.toString(minimum) : kind == Kind.CHOICE ? choices[0] : "";
        Input input = new Input(key, PresentationCodecs.text(manager, object, "label", null), kind, initial, length, minimum, maximum, step, choices);
        try { input.validate(initial, new NBTTagCompound()); } catch (IllegalArgumentException ex) { throw new JsonDeserializeException(json, ex.getMessage()); }
        return input;
    }

    static NBTTagCompound validateInputs(Input[] definitions, Map<String, String> values) {
        if (values.size() != definitions.length) throw new IllegalArgumentException("Input set does not match dialog");
        NBTTagCompound result = new NBTTagCompound();
        for (Input input : definitions) input.validate(values.get(input.key), result);
        return result;
    }

    public static final class ActionContext {
        public final EntityPlayerMP player;
        public final NBTTagCompound inputs;
        private final PresentationModule module;
        private final ResourceLocation dialog;
        ActionContext(EntityPlayerMP player, NBTTagCompound inputs, PresentationModule module, ResourceLocation dialog) {
            this.player = player; this.inputs = inputs; this.module = module; this.dialog = dialog;
        }
        public void invoke(ResourceLocation function) { module.invokeDialogFunction(player, function, inputs); }
        public void open(ResourceLocation target) { module.openDialog(player, target); }
        public void show(PresentationCodecs.DialogDefinition definition) { PresentationNetwork.open(player, dialog, definition, inputs); }
    }

    public static final class Sequence implements PresentationCodecs.DialogAction {
        private final ITextComponent label;
        public final PresentationCodecs.DialogAction[] actions;
        public Sequence(ITextComponent label, PresentationCodecs.DialogAction[] actions) { this.label = label; this.actions = actions; }
        @Override public ITextComponent label() { return label; }
        @Override public void execute(ActionContext context) { for (PresentationCodecs.DialogAction action : actions) action.execute(context); }
        @Override public void validate(Predicate<ResourceLocation> dialogs, Predicate<ResourceLocation> functions) { for (PresentationCodecs.DialogAction action : actions) action.validate(dialogs, functions); }
    }
    public static final class Navigate implements PresentationCodecs.DialogAction {
        private final ITextComponent label;
        public final ResourceLocation target;
        public Navigate(ITextComponent label, ResourceLocation target) { this.label = label; this.target = target; }
        @Override public ITextComponent label() { return label; }
        @Override public void execute(ActionContext context) { context.open(target); }
        @Override public void validate(Predicate<ResourceLocation> dialogs, Predicate<ResourceLocation> functions) { if (!dialogs.test(target)) throw new IllegalArgumentException("Unknown dialog " + target); }
    }
    public static final class Close implements PresentationCodecs.DialogAction {
        private final ITextComponent label;
        public Close(ITextComponent label) { this.label = label; }
        @Override public ITextComponent label() { return label; }
        @Override public void execute(ActionContext context) { /* Consuming the session closes the dialog. */ }
    }
    public static final class Confirmation implements PresentationCodecs.DialogAction {
        private final ITextComponent label;
        public final PresentationCodecs.DialogDefinition dialog;
        public Confirmation(ITextComponent label, ITextComponent title, ITextComponent body, PresentationCodecs.DialogAction yes, PresentationCodecs.DialogAction no) {
            this.label = label; this.dialog = new PresentationCodecs.DialogDefinition(title, body, new PresentationCodecs.DialogAction[]{yes, no}, new Input[0]);
        }
        @Override public ITextComponent label() { return label; }
        @Override public void execute(ActionContext context) { context.show(dialog); }
        @Override public void validate(Predicate<ResourceLocation> dialogs, Predicate<ResourceLocation> functions) { for (PresentationCodecs.DialogAction action : dialog.actions) action.validate(dialogs, functions); }
    }
    public static final class DialogList implements PresentationCodecs.DialogAction {
        private final ITextComponent label;
        public final PresentationCodecs.DialogDefinition dialog;
        public DialogList(ITextComponent label, ITextComponent title, PresentationCodecs.DialogAction[] entries) {
            this.label = label; this.dialog = new PresentationCodecs.DialogDefinition(title, new TextComponentString(""), entries, new Input[0]);
        }
        @Override public ITextComponent label() { return label; }
        @Override public void execute(ActionContext context) { context.show(dialog); }
        @Override public void validate(Predicate<ResourceLocation> dialogs, Predicate<ResourceLocation> functions) { for (PresentationCodecs.DialogAction action : dialog.actions) action.validate(dialogs, functions); }
    }
    private static JsonObject object(JsonElement json) throws JsonDeserializeException {
        if (json == null || !json.isJsonObject()) throw new JsonDeserializeException(json, "expected object");
        return json.getAsJsonObject();
    }
    private static void fields(JsonObject object, String... fields) throws JsonDeserializeException {
        for (Map.Entry<String, JsonElement> field : object.entrySet())
            if (!"type".equals(field.getKey()) && !java.util.Arrays.asList(fields).contains(field.getKey()))
                throw new JsonDeserializeException(object, "unsupported field " + field.getKey());
    }
    private static JsonElement required(JsonObject object, String field) throws JsonDeserializeException {
        if (!object.has(field)) throw new JsonDeserializeException(object, "missing " + field);
        return object.get(field);
    }
}
