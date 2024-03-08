package com.Hileb.custom_village_hileb.vanilla;

import com.Hileb.custom_village_hileb.CustomVillageModMain;
import com.Hileb.custom_village_hileb.json.load.event.CustomVillageLoaderRegisterEvent;
import com.Hileb.custom_village_hileb.reflection.ReflectionHandler;
import com.Hileb.custom_village_hileb.vanilla.other.VillageCancer;
import com.Hileb.custom_village_hileb.vanilla.other.VillageProfession;
import com.Hileb.custom_village_hileb.vanilla.sounds.SoundsJsonLoader;
import com.Hileb.custom_village_hileb.vanilla.trades.EmeraldForItems;
import com.Hileb.custom_village_hileb.vanilla.trades.ItemAndEmeraldToItem;
import com.Hileb.custom_village_hileb.vanilla.trades.ListItemForEmeralds;
import com.Hileb.custom_village_hileb.vanilla.trades.itrades.SlotRecipe;
import com.Hileb.custom_village_hileb.vanilla.trades.ranges.PriceRange;
import com.Hileb.custom_village_hileb.vanilla.trades.ranges.RangeConstant;
import com.Hileb.custom_village_hileb.vanilla.trades.ranges.RangePoisson;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:36
 **/
@Mod.EventBusSubscriber(modid= CustomVillageModMain.MODID)
public class SelfEventReader {
    @SubscribeEvent
    public static void onRegister(CustomVillageLoaderRegisterEvent event){
        ReflectionHandler.init();

        new SoundsJsonLoader(new ResourceLocation("forge","sounds"));
        //range factory
        new PriceRange.Factory(new ResourceLocation("minecraft","price"));
        new RangeConstant.Factory(new ResourceLocation("cvh","constant"));
        new RangePoisson.Factory(new ResourceLocation("cvh","poisson_distribution"));

        //trade loader
        new EmeraldForItems.Loader(new ResourceLocation("minecraft","emerald_for_items"));
        new ItemAndEmeraldToItem.Loader(new ResourceLocation("minecraft","item_and_emerald_to_item"));
        new ListItemForEmeralds.Loader(new ResourceLocation("minecraft","list_item_for_emeralds"));
        new SlotRecipe.Loader(new ResourceLocation("cvh","slots"));

        //base loader
        new VillageProfession.Loader(new ResourceLocation("minecraft","profession"));
        new VillageCancer.Loader(new ResourceLocation("minecraft","cancer"));
    }
}
