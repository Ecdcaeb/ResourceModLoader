package rml.loader.content.gameplay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.value.LevelBasedValue;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.ContentCatalog;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;
import rml.loader.deserialize.NativeTypeDecoders;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/** Actual effect evaluation, native stacks and damage sources; run with the Forge test runtime. */
public final class NativeEnchantmentContractTest {
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        DeserializerManager manager = new DeserializerManager("rml");
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "resource_location"), ResourceLocation.class, json -> new ResourceLocation(json.getAsString())));
        CompiledTagModule tags = new CompiledTagModule();
        NativeTypeDecoders.register(manager, tags);
        DataRules rules = new DataRules(new ContentCatalog(null), tags, (id, context) -> { throw new AssertionError("Unexpected function invocation: " + id); });
        rules.registerDecoders(manager);
        EnchantmentEffectDecoders.register(manager);
        DataContext context = new DataContext(null, null, null, new Random(91), 2);

        NativeEnchantment fire = new NativeEnchantment(new ResourceLocation("test", "fire_aspect"), fixture("fire_aspect"), manager);
        check(fire.getMinEnchantability(2) == 30 && fire.getMaxEnchantability(2) == 80, "Costs must use composed level values as absolute bounds");
        check(fire.canApply(new ItemStack(Items.DIAMOND_SWORD)) && !fire.canApply(new ItemStack(Items.DIAMOND_PICKAXE)), "Supported item matcher must affect native applicability");
        net.minecraft.entity.item.EntityItem target = new net.minecraft.entity.item.EntityItem((net.minecraft.world.World) null);
        fire.execute("post_attack", context, target, target, target);
        check(target.isBurning(), "Data-defined post-attack ignite must mutate the native victim");

        NativeEnchantment protection = new NativeEnchantment(new ResourceLocation("test", "protection"), fixture("protection"), manager);
        check(protection.modify("damage_protection", 0, context.withDamageSource(DamageSource.GENERIC)) == 2, "Protection must read enchantment level and actual damage context");
        check(protection.modify("damage_protection", 0, context.withDamageSource(DamageSource.OUT_OF_WORLD)) == 0, "Void damage must fail conditional protection");
        check(protection.modify("damage_protection", 0, context.withDamageSource(new DamageSource("absolute").setDamageIsAbsolute())) == 0, "Native absolute damage must fail conditional protection");
        check(protection.modify("damage_protection", 0, context) == 0, "Missing damage context must not fabricate protection eligibility");

        NativeEnchantment mending = new NativeEnchantment(new ResourceLocation("test", "mending"), fixture("mending"), manager);
        check(mending.repairRatio(2, context) == 2, "Mending ratio must be available to native XP consumer");
        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setItemDamage(7);
        check(NativeEnchantmentRuntime.repairWithExperience(damaged, 2, mending.repairRatio(2, context)) == 0 && damaged.getItemDamage() == 3, "XP consumer must repair native damage and spend only available XP");
        check(NativeEnchantmentRuntime.repairWithExperience(damaged, 3, 2) == 1 && damaged.getItemDamage() == 0, "Partial repair must preserve remaining orb XP and never underflow damage");
        check(NativeEnchantmentRuntime.repairWithExperience(damaged, 3, 2) == 3, "Undamaged equipment must consume no XP");
        NativeEnchantment conditional = enchantment(manager, "{\"max_level\":2,\"effects\":{\"repair_with_xp\":[{\"requirements\":{\"type\":\"rml:level\",\"min\":2},\"effect\":{\"type\":\"rml:multiply\",\"amount\":2}}]}}");
        check(Float.isNaN(conditional.repairRatio(2, context.withLevel(1))), "A failed repair condition must make the stack ineligible, not silently keep the vanilla ratio");
        check(conditional.repairRatio(2, context) == 4, "An eligible repair condition must multiply the native ratio");

        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "enchantment_choice"), GameplayModule.EnchantmentProvider.Choice.class, value -> GameplayModule.EnchantmentProvider.Choice.decode(manager, value.getAsJsonObject())));
        ItemStack originalSword = new ItemStack(Items.DIAMOND_SWORD);
        GameplayModule.EnchantmentProvider pool = GameplayModule.EnchantmentProvider.decode(manager, fixture("cost_provider"));
        ItemStack selected = pool.apply(originalSword, new Random(17));
        check(net.minecraft.enchantment.EnchantmentHelper.getEnchantments(selected).size() == 1, "Incompatible sharpness and smite must not both be selected");
        check(!originalSword.isItemEnchanted(), "Provider application must not mutate the caller's original stack");
        GameplayModule.EnchantmentProvider cost = GameplayModule.EnchantmentProvider.decode(manager, json("{\"cost\":12,\"entries\":[{\"enchantment\":\"minecraft:sharpness\"}]}"));
        ItemStack book = cost.apply(new ItemStack(Items.BOOK), new Random(2));
        check(book.getItem() == Items.ENCHANTED_BOOK && net.minecraft.enchantment.EnchantmentHelper.getEnchantments(book).get(net.minecraft.init.Enchantments.SHARPNESS) == 2, "Cost-based providers must select the highest affordable level and write native stored book enchantments");

        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("test", "triple_level"), LevelBasedValue.class, json -> level -> level * 3));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("test", "subtract_level"), NativeEnchantment.ValueEffect.class, json -> (value, ctx) -> value - ctx.getLevel()));
        NativeEnchantment.ValueEffect composed = manager.decode(NativeEnchantment.ValueEffect.class, json("{\"type\":\"rml:all_of\",\"effects\":[{\"type\":\"rml:add\",\"amount\":{\"type\":\"test:triple_level\"}},{\"type\":\"rml:multiply\",\"amount\":2},{\"type\":\"test:subtract_level\"}]}"));
        check(composed.apply(1, context) == 12, "Custom children must participate in ordered native value composition");
        NativeEnchantment.ValueEffect remove = manager.decode(NativeEnchantment.ValueEffect.class, json("{\"type\":\"rml:remove_binomial\",\"amount\":1}"));
        check(remove.apply(9, context) == 0, "Probability one removes all durability trials");
        NativeEnchantment.ValueEffect square = manager.decode(NativeEnchantment.ValueEffect.class, json("{\"type\":\"rml:exponential\",\"amount\":2}"));
        check(square.apply(3, context) == 9, "Exponential value effects must execute");
        try {
            enchantment(manager, "{\"effects\":{\"unimplemented_component\":[{\"type\":\"rml:add\",\"amount\":1}]}}");
            throw new AssertionError("Unknown effects must fail compilation rather than disappear");
        } catch (IllegalArgumentException expected) { }
        System.out.println("NativeEnchantmentContractTest passed");
    }
    private static NativeEnchantment enchantment(DeserializerManager manager, String value) throws Exception {
        return new NativeEnchantment(new ResourceLocation("test", "conditional"), json(value), manager);
    }
    private static JsonObject fixture(String name) throws Exception {
        try (InputStream stream = NativeEnchantmentContractTest.class.getResourceAsStream("/rml/gameplay/" + name + ".json")) {
            if (stream == null) throw new AssertionError("Missing gameplay fixture " + name);
            return new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static JsonObject json(String value) { return new JsonParser().parse(value).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
