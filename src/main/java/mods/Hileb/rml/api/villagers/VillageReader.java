package mods.Hileb.rml.api.villagers;

import com.google.gson.JsonObject;
import mods.Hileb.rml.api.PublicAPI;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:23
 **/
@PublicAPI
public abstract class VillageReader {
    public VillageReader(){
    }
    public abstract LoadedVillage load(JsonObject json);
}
