package mods.rml.api.world.villagers.villagers;

import mods.rml.api.announces.BeDiscovered;
import mods.rml.api.world.villagers.IVillager;
import mods.rml.api.world.villagers.VillageReader;
import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.Deserializer;
import rml.deserializer.Record;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 11:39
 **/
@BeDiscovered
public class VillageCancer implements IVillager{
    public static final AbstractDeserializer<IVillager> DESERIALIZER = Deserializer.named(IVillager.class, new ResourceLocation("minecraft","cancer"))
            .record(VillageCancer.class).build();
    public ResourceLocation profession;
    public String name;
    @Record(parameters = {@Record.Parameter(type = ResourceLocation.class, name = "profession"), @Record.Parameter(type = String.class, name = "career")})
    public VillageCancer(ResourceLocation professionIn, String nameIn){
        profession=professionIn;
        name=nameIn;
    }

    @Override
    public void run(IForgeRegistry<VillagerRegistry.VillagerProfession> r) {
        VillagerRegistry.VillagerProfession profession_v = r.getValue(this.profession);
        new VillagerRegistry.VillagerCareer(profession_v, name);
    }
}
