package mods.rml.deserialize;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import rml.jrx.announces.BeDiscovered;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.Deserializer;
import rml.deserializer.MCDeserializers;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 15:11
 **/
@BeDiscovered
public class RMLDeserializer {

    public static final AbstractDeserializer<RMLLoaders.OreDic.TagOre> TAG_ORES = Deserializer.named(RMLLoaders.OreDic.TagOre.class, new ResourceLocation("rml", "ore_tag"))
            .require(ItemStack.class, "item")
            .require(String.class, "ore")
            .decode((context -> {
                RMLLoaders.OreDic.TagOre tagOre = new RMLLoaders.OreDic.TagOre();
                tagOre.ore = context.get(String.class, "ore");
                tagOre.item = context.get(ItemStack.class, "item");
                return tagOre;
            })).markDefault().build();

    public static final AbstractDeserializer<ItemStack> ENCHANTED_ITEM = Deserializer.named(ItemStack.class, new ResourceLocation("rml", "enchantmented_item"))
            .optional(EnchantmentData[].class, "enchantment")
            .decode(context -> {
                ItemStack stack = MCDeserializers.ITEM_STACK_DEFAULT.deserialize(context.getJsonObject());
                if (context.ifPresent(EnchantmentData[].class, "enchantment")){
                    for(EnchantmentData enchantmentData : context.get(EnchantmentData[].class, "enchantment")){
                        stack.addEnchantment(enchantmentData.enchantment, enchantmentData.enchantmentLevel);
                    }
                }
                return stack;
            }).build();

    public static final AbstractDeserializer<IntArrayList> INT_ARRAY_LIST = Deserializer.MANAGER.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("iu", "ints"), IntArrayList.class, jsonElement -> new IntArrayList(Lists.newArrayList(Deserializer.decode(Integer[].class, jsonElement)))));


//    public static final AbstractDeserializer<Item.ToolMaterial> TOOL_MATERIAL = Deserializer.named(Item.ToolMaterial.class, new ResourceLocation("minecraft","tool_material"))
//            .require(String.class, "name")
//            .decode((context -> {
//                Item.ToolMaterial material = null;
//                try{
//                    material = Item.ToolMaterial.valueOf(context.get(String.class, "name"));
//                }catch (IllegalArgumentException e){
//                    //material = EnumHelper.addToolMaterial()
//                }
//                return material;
//            })).markDefault().build();

//    public static final AbstractDeserializer<RandomIntSupplier> PRICE_RANGE = Deserializer.named(RandomIntSupplier.class, new ResourceLocation("minecraft", "price"))
//            .require(Integer.class, "min")
//            .require(Integer.class, "max")
//            .decode((context -> {
//                return new
//            }))


}
