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
 * @Date 2023/8/16 11:45
 **/
@BeDiscovered
public class VillageProfession implements IVillager{
    public static final AbstractDeserializer<IVillager> DESERIALIZER = Deserializer.named(IVillager.class, new ResourceLocation("minecraft","profession"))
            .record(VillageProfession.class).build();
    public ResourceLocation professionName;
    public ResourceLocation villageTexture;
    public ResourceLocation zombieTexture;

    @Record(parameters = {@Record.Parameter(type = ResourceLocation.class, name = "professionName"), @Record.Parameter(type = ResourceLocation.class, name = "villageTexture"), @Record.Parameter(type = ResourceLocation.class, name = "zombieTexture")})
    public VillageProfession(ResourceLocation professionIn, ResourceLocation villageTextureIn, ResourceLocation  zombieTextureIn){
        professionName =professionIn;
        villageTexture=villageTextureIn;
        zombieTexture=zombieTextureIn;
    }

    @Override
    public void run(IForgeRegistry<VillagerRegistry.VillagerProfession> r) {
        r.register(new VillagerRegistry.VillagerProfession(professionName.toString(),villageTexture.toString(),zombieTexture.toString()));
    }
}
