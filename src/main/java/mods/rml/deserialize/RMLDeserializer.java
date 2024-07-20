package mods.rml.deserialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mods.rml.api.announces.BeDiscovered;
import mods.rml.api.world.villagers.trades.ranges.PriceRange;
import mods.rml.api.world.villagers.trades.ranges.RangeBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
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

    public static final AbstractDeserializer<RMLLoaders.OreDic.TagOre> TAG_ORES = Deserializer.named(RMLLoaders.OreDic.TagOre.class, new ResourceLocation("rml", "ore_tag"))
            .require(ItemStack.class, "item")
            .require(String.class, "ore")
            .decode((context -> {
                RMLLoaders.OreDic.TagOre tagOre = new RMLLoaders.OreDic.TagOre();
                tagOre.ore = context.get(String.class, "ore");
                tagOre.item = context.get(ItemStack.class, "item");
                return tagOre;
            })).markDefault().build();


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

//    public static final AbstractDeserializer<RangeBase> PRICE_RANGE = Deserializer.named(RangeBase.class, new ResourceLocation("minecraft", "price"))
//            .require(Integer.class, "min")
//            .require(Integer.class, "max")
//            .decode((context -> {
//                return new
//            }))


}
