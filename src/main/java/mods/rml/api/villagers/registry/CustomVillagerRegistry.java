package mods.rml.api.villagers.registry;

import mods.rml.api.PublicAPI;
import mods.rml.api.villagers.VillageReader;
import mods.rml.api.villagers.trades.ranges.RangeFactory;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;

/**
 * @Project setupDecomp.bat
 * @Author Hileb
 * @Date 2024/3/8 13:27
 **/
@PublicAPI
public class CustomVillagerRegistry {
    public static final HashMap<ResourceLocation, RangeFactory> RANGES = new HashMap<>();
    public static final HashMap<ResourceLocation, VillageReader> VILLAGE_READERS = new HashMap<>();
}
