package mods.rml.api;

import mods.rml.api.villagers.VillageReader;
import mods.rml.api.villagers.trades.ranges.RangeFactory;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/5 14:57
 **/
public class RMLRegistries {
    public static final IForgeRegistry<RangeFactory> RANGE_FACTORIES = new RegistryBuilder<RangeFactory>()
            .setName(Names.RANGE_FACTORIES)
            .setType(RangeFactory.class)
            .setMaxID(4095).create();

    public static final IForgeRegistry<VillageReader> VILLAGE_READERS= new RegistryBuilder<VillageReader>()
            .setName(Names.VILLAGE_READERS)
            .setType(VillageReader.class)
            .setMaxID(4095).create();

    public static class Names{

        public static final ResourceLocation RANGE_FACTORIES = new ResourceLocation("rml", "range_factory");
        public static final ResourceLocation VILLAGE_READERS = new ResourceLocation("rml", "village_reader");
    }
}
