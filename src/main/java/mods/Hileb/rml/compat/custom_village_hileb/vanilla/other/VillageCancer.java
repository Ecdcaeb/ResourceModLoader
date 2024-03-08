package com.Hileb.custom_village_hileb.vanilla.other;

import com.Hileb.custom_village_hileb.json.load.LoadedVillage;
import com.Hileb.custom_village_hileb.json.load.VillageReader;
import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 11:39
 **/
public class VillageCancer {
    public static class Loader extends VillageReader{
        public Loader(ResourceLocation resourceLocation) {
            super(resourceLocation);
        }

        @Override
        public LoadedVillage load(JsonObject json) {
            String name= JsonUtils.getString(json,"career");
            ResourceLocation resourceLocation=new ResourceLocation(JsonUtils.getString(json,"profession"));
            return new Loaded(resourceLocation,name);
        }
    }
    public static class Loaded extends LoadedVillage{
        public ResourceLocation profession;
        public String name;
        public Loaded(ResourceLocation professionIn,String nameIn){
            profession=professionIn;
            name=nameIn;
        }

        @Override
        public void run(IForgeRegistry<VillagerRegistry.VillagerProfession> r) {
            VillagerRegistry.VillagerProfession profession_v = r.getValue(this.profession);
            VillagerRegistry.VillagerCareer newCareer = new VillagerRegistry.VillagerCareer(profession_v, name);
        }
    }
}
