package mods.Hileb.rml.api.villagers;

import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:19
 **/
public abstract class LoadedVillage {
    public abstract void run(IForgeRegistry<VillagerRegistry.VillagerProfession> r);
}
