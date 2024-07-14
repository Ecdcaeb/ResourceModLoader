package mods.rml.deserialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mods.rml.api.announces.BeDiscovered;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.Deserializer;
import rml.deserializer.JsonDeserializerException;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 15:11
 **/
@BeDiscovered
public class RMLDeserializer {
    public static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final AbstractDeserializer<ResourceLocation> RESOURCE_LOCATION = Deserializer.MANAGER.addDefaultEntry(
            new AbstractDeserializer<>(new ResourceLocation("minecraft", "resource_location"), ResourceLocation.class,
                    element -> {
                        try {
                            return new ResourceLocation(element.getAsString());
                        }catch (Throwable e){
                            throw new JsonDeserializerException(element, "Could not read as ResourceLocation", e);
                        }
                    })
    );

    public static final AbstractDeserializer<Item> ITEM = Deserializer.MANAGER.addDefaultEntry(
            new AbstractDeserializer<>(new ResourceLocation("forge", "item"), Item.class,
                    element -> {
                        Item item =  ForgeRegistries.ITEMS.getValue(new ResourceLocation(element.getAsString()));
                        if(item == null) throw new JsonDeserializerException(element, "Could not found such an item");
                        else return item;
                    })
    );

    public static final AbstractDeserializer<NBTTagCompound> NBT_TAG_COMPOUND = Deserializer.MANAGER.addDefaultEntry(
            new AbstractDeserializer<>(new ResourceLocation("minecraft", "json_to_nbt"), NBTTagCompound.class,
                    element -> {
                        try {
                           if (element.isJsonObject())
                             return JsonToNBT.getTagFromJson(GSON.toJson(element));
                           else
                             return JsonToNBT.getTagFromJson(element.getAsString());
                        }catch (NBTException e){
                            throw new JsonDeserializerException(element, e);
                        }
                    })
    );

    public static final AbstractDeserializer<ItemStack> ITEM_STACK_DEFAULT = Deserializer.named(ItemStack.class, new ResourceLocation("minecraft", "item"))
            .require(Item.class, "item")
            .optionalWhen(int.class, "data", context -> context.get(Item.class, "item").getHasSubtypes())
            .check((context -> {if (!context.ifPresent("data")) context.put("data", 0); return null;}))
            .optional(NBTTagCompound.class, "nbt")
            .optionalDefault(int.class, "count", 1)
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

    public static final AbstractDeserializer<RMLLoaders.OreDic.TagOre> TAG_ORES = Deserializer.named(RMLLoaders.OreDic.TagOre.class, new ResourceLocation("rml", "ore_tag"))
            .require(ItemStack.class, "item")
            .require(String.class, "ore")
            .decode((context -> {
                RMLLoaders.OreDic.TagOre tagOre = new RMLLoaders.OreDic.TagOre();
                tagOre.ore = context.get(String.class, "ore");
                tagOre.item = context.get(ItemStack.class, "ore");
                return tagOre;
            })).markDefault().build();

}
