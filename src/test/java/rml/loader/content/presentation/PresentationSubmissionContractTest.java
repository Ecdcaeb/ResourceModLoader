package rml.loader.content.presentation;

import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Real codec/packet/validation consumers; does not require a fake server or claim to simulate a client. */
public final class PresentationSubmissionContractTest {
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        DeserializerManager manager = new DeserializerManager("rml");
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "resource_location"), ResourceLocation.class,
                value -> new ResourceLocation(manager.decode(String.class, value))));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "textcomponent"), ITextComponent.class,
                value -> ITextComponent.Serializer.jsonToComponent(value.toString())));
        PresentationCodecs.register(manager);
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("probe", "text"), PresentationDialogs.Text.class,
                value -> new PresentationDialogs.Text(new TextComponentTranslation("gui.done"))));
        PresentationCodecs.DialogDefinition definition = manager.decode(PresentationCodecs.DialogDefinition.class, new JsonParser().parse(
                "{\"title\":{\"type\":\"probe:text\"},\"body\":{\"translate\":\"chat.type.text\",\"with\":[\"Guide\",{\"text\":\"Choose\",\"bold\":true}]},"
                + "\"inputs\":[{\"type\":\"rml:text\",\"key\":\"name\",\"label\":\"Name\",\"max_length\":8},"
                + "{\"type\":\"rml:boolean\",\"key\":\"enabled\",\"label\":\"Enabled\"},"
                + "{\"type\":\"rml:choice\",\"key\":\"route\",\"label\":\"Route\",\"choices\":[\"forest\",\"river\"]},"
                + "{\"type\":\"rml:number\",\"key\":\"amount\",\"label\":\"Amount\",\"min\":1,\"max\":5,\"step\":0.5}],"
                + "\"actions\":[{\"type\":\"rml:confirmation\",\"label\":\"Submit\",\"title\":\"Confirm\","
                + "\"yes\":{\"type\":\"rml:function\",\"label\":{\"translate\":\"gui.yes\"},\"id\":\"probe:submit\"}}]}"));
        UUID token = UUID.randomUUID(); ResourceLocation id = new ResourceLocation("probe", "form");
        ByteBuf buffer = Unpooled.buffer();
        try {
            new PresentationNetwork.OpenMessage(id, token, definition).toBytes(buffer);
            PresentationNetwork.OpenMessage opened = new PresentationNetwork.OpenMessage(); opened.fromBytes(buffer);
            check(opened.token.equals(token) && opened.id.equals(id), "open packet must retain session identity");
            check(opened.title instanceof TextComponentTranslation, "custom nested text must retain translation semantics over the wire");
            check(ITextComponent.Serializer.componentToJson(opened.body).contains("bold"), "nested styles must survive transport");
            check(opened.inputs.length == 4 && opened.inputs[3].step == 0.5, "input constraints must reach native form controls");
        } finally { buffer.release(); }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "Alex"); values.put("enabled", "true"); values.put("route", "river"); values.put("amount", "2.5");
        buffer = Unpooled.buffer();
        try {
            new PresentationNetwork.ChooseMessage(id, token, 0, values).toBytes(buffer);
            PresentationNetwork.ChooseMessage chosen = new PresentationNetwork.ChooseMessage(); chosen.fromBytes(buffer);
            check(chosen.token.equals(token) && chosen.index == 0, "submission must retain exact action and session");
            NBTTagCompound validated = PresentationDialogs.validateInputs(definition.inputs, chosen.inputs);
            check(validated.hasKey("name", 8) && validated.hasKey("enabled", 1) && validated.hasKey("amount", 6), "server values must have authoritative NBT types");
            check(validated.getDouble("amount") == 2.5 && validated.getBoolean("enabled") && "river".equals(validated.getString("route")), "submitted values must reach binding unchanged");
        } finally { buffer.release(); }
        reject(definition, values, "amount", "2.25"); reject(definition, values, "amount", "NaN");
        reject(definition, values, "amount", "100"); reject(definition, values, "enabled", "1");
        reject(definition, values, "route", "op @s"); reject(definition, values, "name", "too-long-name");
        reject(definition, values, "name", "A\n/op @s");
        Map<String, String> extra = new LinkedHashMap<>(values); extra.put("command", "op @s");
        expectInvalid(() -> PresentationDialogs.validateInputs(definition.inputs, extra));
        PresentationDialogs.Confirmation confirmation = (PresentationDialogs.Confirmation) definition.actions[0];
        check(confirmation.dialog.actions.length == 2 && confirmation.dialog.inputs.length == 0, "confirmation must expose only its own server-owned choices");
        expectInvalid(() -> definition.actions[0].validate(location -> false, location -> false));
        definition.actions[0].validate(location -> false, location -> location.equals(new ResourceLocation("probe", "submit")));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("probe", "increment"), PresentationCodecs.DialogAction.class,
                json -> new PresentationCodecs.DialogAction() {
                    @Override public ITextComponent label() { return new TextComponentString("Increment"); }
                    @Override public void execute(PresentationDialogs.ActionContext context) { context.inputs.setInteger("count", context.inputs.getInteger("count") + 1); }
                }));
        PresentationCodecs.DialogAction sequence = manager.decode(PresentationCodecs.DialogAction.class, new JsonParser().parse(
                "{\"type\":\"rml:sequence\",\"label\":\"Twice\",\"actions\":[{\"type\":\"probe:increment\"},{\"type\":\"probe:increment\"}]}"));
        NBTTagCompound state = new NBTTagCompound(); sequence.execute(new PresentationDialogs.ActionContext(null, state, null, id));
        check(state.getInteger("count") == 2, "registered custom children must execute through the composed action consumer");
        PresentationDialogs.Text nativeText = manager.decode(PresentationDialogs.Text.class, new JsonParser().parse(
                "{\"type\":\"rml:component\",\"value\":{\"text\":\"Hover\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"translate\":\"gui.done\"}}}}"));
        check(nativeText.component.getStyle().getHoverEvent() != null, "native component bridge must preserve component events");
        PresentationCodecs.SoundPlayback playback = manager.decode(PresentationCodecs.SoundPlayback.class, new JsonParser().parse(
                "{\"sound\":\"minecraft:record.13\",\"duration\":100,\"range\":12,\"comparator\":11}"));
        PresentationInstrumentItem instrument = new PresentationInstrumentItem(new net.minecraft.util.SoundEvent(playback.sound), playback);
        check(instrument.getMaxItemUseDuration(new net.minecraft.item.ItemStack(instrument)) == 100, "native item use must consume authored duration");
        PresentationPlayback.Play play = new PresentationPlayback.Play(1, 2, 3, playback, -1, true);
        buffer = Unpooled.buffer();
        try {
            play.toBytes(buffer); PresentationPlayback.Play received = new PresentationPlayback.Play(); received.fromBytes(buffer);
            check(received.range == 12 && received.duration == 100 && received.jukebox, "playback lifetime and audible radius must reach the client sound consumer");
        } finally { buffer.release(); }
        System.out.println("PresentationSubmissionContractTest passed");
    }
    private static void reject(PresentationCodecs.DialogDefinition definition, Map<String, String> values, String key, String value) {
        Map<String, String> invalid = new LinkedHashMap<>(values); invalid.put(key, value);
        expectInvalid(() -> PresentationDialogs.validateInputs(definition.inputs, invalid));
    }
    private static void expectInvalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
