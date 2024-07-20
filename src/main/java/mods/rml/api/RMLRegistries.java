package mods.rml.api;

import mods.rml.api.world.function.FunctionExecutorFactory;
import mods.rml.api.world.villagers.VillageReader;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/5 14:57
 **/
public class RMLRegistries {
    public static final IForgeRegistry<VillageReader> VILLAGE_READERS = new RegistryBuilder<VillageReader>()
            .setName(Names.VILLAGE_READERS)
            .setType(VillageReader.class)
            .setMaxID(4095).create();

    public static final IForgeRegistry<FunctionExecutorFactory> FUNCTION_EXECUTORS = new RegistryBuilder<FunctionExecutorFactory>()
            .setName(Names.FUNCTION_EXECUTOR_FACTORY)
            .setType(FunctionExecutorFactory.class)
            .setMaxID(4095).create();

    public static void call(){}

    public static class Names extends ResourceLocation{

        public static final Names RANGE_FACTORIES = new Names("rml", "range_factory");
        public static final Names VILLAGE_READERS = new Names("rml", "village_reader");
        public static final Names FUNCTION_EXECUTOR_FACTORY = new Names("rml", "function_executor_factory");

        public Names(String resourceDomainIn, String resourcePathIn) {
            super(resourceDomainIn, resourcePathIn);
        }

        public void fire(){
            GameData.fireRegistryEvents(this::equals);
        }

        static {
            RMLRegistries.call();
        }
    }
}
