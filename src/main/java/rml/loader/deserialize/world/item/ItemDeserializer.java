package rml.loader.deserialize.world.item;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.utils.RandomHolder;
import rml.loader.api.utils.values.RandomIntSupplier;
import rml.loader.deserialize.Deserializer;

public class ItemDeserializer {
    public static class EnchantmentDataDeserializer {
        public static final AbstractDeserializer<EnchantmentData> ENCHANTMENT_DATA = Deserializer.named(EnchantmentData.class, new ResourceLocation("minecraft", "enchantment_data"))
                .require(Enchantment.class, "name")
                .require(RandomIntSupplier.class, "level")
                .decode(context -> new EnchantmentData(context.get(Enchantment.class, "name"), context.get(RandomIntSupplier.class, "level").get(RandomHolder.RANDOM)))
                .markDefault().build();
    }

    public static class ItemStackDeserializer {
        public static final AbstractDeserializer<ItemStack> ITEM_STACK_DEFAULT = Deserializer.named(ItemStack.class, new ResourceLocation("minecraft", "item"))
                .require(Item.class, "item")
                .optionalWhen(Integer.class, "data", context -> context.get(Item.class, "item").getHasSubtypes())
                .check((context -> {if (!context.ifPresent("data")) context.put("data", 0); return null;}))
                .optional(NBTTagCompound.class, "nbt")
                .optionalDefault(Integer.class, "count", 1)
                .decode((context -> {
                    Item item = context.get(Item.class, "item");
                    int data = context.get(Integer.class, "data");
                    int count = context.get(Integer.class, "count");
                    if (context.ifPresent(NBTTagCompound.class, "nbt")){
                        NBTTagCompound nbt = context.get(NBTTagCompound.class, "nbt");
                        NBTTagCompound tmp = new NBTTagCompound();
                        if (nbt.hasKey("ForgeCaps"))
                        {
                            tmp.setTag("ForgeCaps", nbt.getTag("ForgeCaps"));
                            nbt.removeTag("ForgeCaps");
                        }

                        tmp.setTag("tag", nbt);
                        tmp.setString("id", item.getRegistryName().toString());
                        tmp.setInteger("Count", count);
                        tmp.setInteger("Damage", data);

                        return new ItemStack(tmp);
                    }else {
                        return new ItemStack(item, count, data);
                    }
                })).markDefault().build();


        public static final AbstractDeserializer<ItemStack> ENCHANTED_ITEM = Deserializer.named(ItemStack.class, new ResourceLocation("rml", "enchantmented_item"))
                .require(ItemStack.class, "item")
                .optional(EnchantmentData[].class, "enchantment")
                .decode(context -> {
                    ItemStack stack = context.get(ItemStack.class, "item");
                    if (context.ifPresent(EnchantmentData[].class, "enchantment")){
                        for(EnchantmentData enchantmentData : context.get(EnchantmentData[].class, "enchantment")){
                            stack.addEnchantment(enchantmentData.enchantment, enchantmentData.enchantmentLevel);
                        }
                    }
                    return stack;
                }).build();
    }

    public static class NBTTagCompoundDeserializer {
        public static final AbstractDeserializer<NBTTagCompound> NBT_TAG_COMPOUND = Deserializer.MANAGER.addDefaultEntry(
                new AbstractDeserializer<>(new ResourceLocation("minecraft", "json_to_nbt"), NBTTagCompound.class,
                        element -> {
                            try {
                                if (element.isJsonObject())
                                    return JsonToNBT.getTagFromJson(element.toString());
                                else
                                    return JsonToNBT.getTagFromJson(element.getAsString());
                            }catch (NBTException e){
                                throw new JsonDeserializeException(element, e);
                            }
                        })
        );
    }

    public static class ToolMaterialDeserializer {
        public static final AbstractDeserializer<Item.ToolMaterial> TOOL_MATERIAL = Deserializer.named(Item.ToolMaterial.class, new ResourceLocation("minecraft","tool_material"))
                .require(String.class, "name")
                .decode((context -> {
                    Item.ToolMaterial material = null;
                    try{
                        material = Item.ToolMaterial.valueOf(context.get(String.class, "name"));
                    }catch (IllegalArgumentException e){
                        //material = EnumHelper.addToolMaterial()
                    }
                    return material;
                })).markDefault().build();

        public static final AbstractDeserializer<Item.ToolMaterial> TOOL_MATERIAL_SIMPLE = Deserializer.named(Item.ToolMaterial.class, new ResourceLocation("rml","create_tool_material"))
                .require(String.class, "name")
                .require(Integer.class, "harvestLevel")
                .require(Integer.class, "maxUses")
                .require(Float.class, "efficiency")
                .require(Float.class, "damage")
                .require(Integer.class, "enchantability")
                .decode((context -> EnumHelper.addToolMaterial(context.get(String.class, "name"), context.get(Integer.class, "harvestLevel"), context.get(Integer.class, "maxUses"), context.get(Float.class, "efficiency"), context.get(Float.class, "damage"), context.get(Integer.class, "enchantability")))).build();
    }
}
