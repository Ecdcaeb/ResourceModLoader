package mods.rml.api.world.villagers;

import com.google.gson.JsonObject;
import mods.rml.api.announces.PublicAPI;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:23
 **/
@PublicAPI
public abstract class VillageReader extends IForgeRegistryEntry.Impl<VillageReader> {
    public VillageReader(){
    }
    public abstract LoadedVillage load(JsonObject json);
}