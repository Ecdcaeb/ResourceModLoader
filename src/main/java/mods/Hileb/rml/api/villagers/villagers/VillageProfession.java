package mods.Hileb.rml.api.villagers.villagers;

import mods.Hileb.rml.api.villagers.LoadedVillage;
import mods.Hileb.rml.api.villagers.VillageReader;
import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 11:45
 **/
public class VillageProfession {
    public static class Loader extends VillageReader {
        public Loader() {
            super();
        }

        @Override
        public LoadedVillage load(JsonObject json) {
            return new Loaded(new ResourceLocation(JsonUtils.getString(json,"professionName")),
            new ResourceLocation(JsonUtils.getString(json,"villageTexture")),
            new ResourceLocation(JsonUtils.getString(json,"zombieTexture")));
        }
    }
    public static class Loaded extends LoadedVillage{
        public ResourceLocation professionName;
        public ResourceLocation villageTexture;
        public ResourceLocation zombieTexture;
        public Loaded(ResourceLocation professionIn, ResourceLocation villageTextureIn, ResourceLocation  zombietextureIn){
            professionName =professionIn;
            villageTexture=villageTextureIn;
            zombieTexture=zombietextureIn;
        }

        @Override
        public void run(IForgeRegistry<VillagerRegistry.VillagerProfession> r) {
            r.register(new VillagerRegistry.VillagerProfession(professionName.toString(),villageTexture.toString(),zombieTexture.toString()));
        }
    }
}
