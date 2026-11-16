package rml.loader.content.presentation;

import com.google.gson.JsonParser;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;
import rml.loader.deserialize.NativeTypeDecoders;

/** Exercises child registration after shell decoding, then the resolved runtime consumers. */
public final class PresentationRegistryPhaseContractTest {
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        DeserializerManager manager = new DeserializerManager("rml");
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "resource_location"), ResourceLocation.class,
                value -> new ResourceLocation(manager.decode(String.class, value))));
        PresentationDeferredChildren children = new PresentationDeferredChildren();
        PresentationCodecs.register(manager, children);
        JsonParser parser = new JsonParser();
        PresentationCodecs.EntityVariant variant = manager.decode(PresentationCodecs.EntityVariant.class, parser.parse(
                "{\"entity\":\"minecraft:cow\",\"texture\":\"minecraft:entity/cow/cow\",\"condition\":{\"type\":\"probe:late_condition\"}}"));
        PresentationCodecs.TrimMaterial material = manager.decode(PresentationCodecs.TrimMaterial.class, parser.parse(
                "{\"palette_id\":\"probe:palette\",\"ingredient\":{\"type\":\"probe:late_ingredient\"}}"));
        DataContext context = new DataContext(null, null, null, new java.util.Random(1), 3);
        try {
            variant.condition.test(context);
            throw new AssertionError("An unresolved condition must not silently return a default result");
        } catch (IllegalStateException expected) { }
        int[] decodes = {0};
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("probe", "late_condition"), DataRules.Test.class, value -> {
            decodes[0]++;
            return candidate -> candidate.getLevel() == 3;
        }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("probe", "late_ingredient"), NativeTypeDecoders.ItemMatcher.class, value -> {
            decodes[0]++;
            return (stack, candidate) -> !stack.isEmpty() && stack.getItem() == Items.DIAMOND;
        }));
        children.resolve();
        children.resolve();
        check(decodes[0] == 2, "Post-registry resolution must decode each child exactly once");
        check(variant.condition.test(context), "Resolved variant condition must reach its runtime consumer");
        check(!variant.condition.test(context.withLevel(2)), "Resolved conditions must retain their authored behavior");
        check(material.ingredient.test(new ItemStack(Items.DIAMOND), context), "Resolved ingredient must match the native item");
        check(!material.ingredient.test(new ItemStack(Items.IRON_INGOT), context), "Resolved ingredient must reject a different native item");
        check(decodes[0] == 2, "Runtime consumers must not parse child definitions again");
        System.out.println("PresentationRegistryPhaseContractTest passed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
