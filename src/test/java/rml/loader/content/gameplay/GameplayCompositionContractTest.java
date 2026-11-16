package rml.loader.content.gameplay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.data.DataContext;
import rml.loader.deserialize.NativeTypeDecoders;
import java.util.Random;

/** Regression coverage for native stack preservation at machine and block boundaries. */
public final class GameplayCompositionContractTest {
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        DeserializerManager manager = new DeserializerManager("rml");
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "resource_location"), ResourceLocation.class, value -> new ResourceLocation(value.getAsString())));
        NativeTypeDecoders.register(manager, new CompiledTagModule());
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("test", "tool_result"), ItemStack.class, value -> {
            ItemStack result = new ItemStack(Items.IRON_PICKAXE);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("ResultFlag", true);
            result.setTagCompound(tag);
            return result;
        }));
        MachineRecipe smithing = MachineRecipe.decode(manager, new ResourceLocation("test", "smithing"), json("{\"type\":\"minecraft:smithing_transform\",\"template\":\"minecraft:diamond\",\"base\":\"minecraft:diamond_pickaxe\",\"addition\":\"minecraft:iron_ingot\",\"result\":{\"type\":\"test:tool_result\"}}"), null);
        ItemStack base = new ItemStack(Items.DIAMOND_PICKAXE);
        base.setStackDisplayName("Named tool");
        base.setItemDamage(17);
        base.getTagCompound().setBoolean("BaseFlag", true);
        DataContext context = new DataContext(null, null, null, new Random(3), 0);
        ItemStack result = smithing.resultFor(new ItemStack(Items.DIAMOND), base, new ItemStack(Items.IRON_INGOT), context);
        check(result.getItem() == Items.IRON_PICKAXE && result.getItemDamage() == 17, "Smithing must preserve damage before output-capacity checks");
        check(result.getDisplayName().equals("Named tool") && result.getTagCompound().getBoolean("BaseFlag") && result.getTagCompound().getBoolean("ResultFlag"), "Smithing must merge base data with the custom result decoder");
        result.getTagCompound().setBoolean("BaseFlag", false);
        check(base.getTagCompound().getBoolean("BaseFlag") && !smithing.result.getTagCompound().hasKey("BaseFlag"), "Crafted results must not mutate input stacks or compiled recipe templates");
        MachineRecipe transmute = MachineRecipe.decode(manager, new ResourceLocation("test", "transmute"), json("{\"type\":\"minecraft:crafting_transmute\",\"input\":\"minecraft:diamond_pickaxe\",\"material\":\"minecraft:iron_ingot\",\"result\":{\"type\":\"test:tool_result\"}}"), null);
        check(transmute.resultFor(base, new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, context).getDisplayName().equals("Named tool"), "Transmute must copy its first input rather than its material slot");

        NativeItemDefinition deferred = NativeItemDefinition.decodeForRegistration(manager, new ResourceLocation("test", "late_item"), json("{\"model\":\"minecraft:stick\",\"on_use\":{\"type\":\"test:late_ignite\"}}"));
        NativeDataItem registeredItem = new NativeDataItem(deferred);
        check(registeredItem.getMaxItemUseDuration(ItemStack.EMPTY) == 32 && registeredItem.getItemUseAction(ItemStack.EMPTY) == net.minecraft.item.EnumAction.DRINK, "Deferred effects must preserve the declared item's native use behavior");
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("test", "late_ignite"), rml.loader.content.data.DataRules.Effect.class, value -> (ctx, target) -> target.setFire(3)));
        deferred.resolveDependencies(manager);
        net.minecraft.entity.item.EntityItem victim = new net.minecraft.entity.item.EntityItem((net.minecraft.world.World) null);
        deferred.useEffect.apply(context, victim);
        check(victim.isBurning(), "A child decoder registered after native declarations must bind and execute its actual effect");

        NativeBlockDefinition blockDefinition = NativeBlockDefinition.decode(manager, new ResourceLocation("test", "variants"), json("{\"model\":\"minecraft:stone\",\"material\":\"rock\",\"hardness\":1,\"resistance\":3,\"variants\":4}"));
        NativeDataBlock block = new NativeDataBlock(blockDefinition);
        NativeBlockItem item = new NativeBlockItem(block);
        check(item.getMetadata(3) == 3 && block.damageDropped(block.getStateFromMeta(3)) == 3, "Block variants must survive placement and native drops");
        check(item.getMetadata(15) == 3, "Out-of-range metadata must stay inside the declared variants");
        try {
            NativeItemDefinition.decode(manager, new ResourceLocation("test", "unsupported"), json("{\"model\":\"minecraft:stick\",\"teleports_user\":true}"));
            throw new AssertionError("Unsupported item behavior must not be silently accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("GameplayCompositionContractTest passed");
    }
    private static JsonObject json(String value) { return new JsonParser().parse(value).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
