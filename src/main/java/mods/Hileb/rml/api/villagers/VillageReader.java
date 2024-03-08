package mods.Hileb.rml.api.villagers;

import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:23
 **/
public abstract class VillageReader {
    public VillageReader(){
    }
    public abstract LoadedVillage load(JsonObject json);
}
