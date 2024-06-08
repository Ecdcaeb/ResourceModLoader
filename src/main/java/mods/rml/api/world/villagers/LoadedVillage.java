package mods.rml.api.world.villagers;

import mods.rml.api.announces.PublicAPI;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:19
 **/
@PublicAPI
public abstract class LoadedVillage {
    public abstract void run(IForgeRegistry<VillagerRegistry.VillagerProfession> r);
}
