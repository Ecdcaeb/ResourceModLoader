package mods.rml.api.event.villagers;

import mods.rml.api.PublicAPI;
import mods.rml.api.villagers.VillageReader;
import mods.rml.api.villagers.registry.CustomVillagerRegistry;
import mods.rml.api.villagers.trades.ranges.RangeFactory;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:22
 **/
@PublicAPI
public class CustomVillageLoaderRegisterEvent extends Event {
    public CustomVillageLoaderRegisterEvent(){}
    @Override
    public boolean isCancelable() {
        return false;
    }

    @Override
    public boolean hasResult() {
        return false;
    }

    public void add(ResourceLocation name, VillageReader reader){
        CustomVillagerRegistry.VILLAGE_READERS.put(name, reader);
    }

    public void add(ResourceLocation name, RangeFactory factory){
        CustomVillagerRegistry.RANGES.put(name, factory);
    }
}
