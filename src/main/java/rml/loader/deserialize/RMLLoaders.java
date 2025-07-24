package rml.loader.deserialize;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteSource;
import com.google.common.io.LineProcessor;
import com.google.gson.JsonParseException;
import rml.deserializer.AbstractDeserializer;
import rml.loader.api.config.v2.config.elements.ConfigElement;
import rml.loader.api.config.v2.config.elements.ConfigGroup;
import rml.loader.ResourceModLoader;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.config.ConfigFactory;
import rml.loader.api.config.ConfigPatcher;
import rml.loader.api.event.FunctionLoadEvent;
import rml.loader.api.event.LootTableRegistryEvent;
import rml.loader.api.reflection.jvm.FieldAccessor;
import rml.loader.api.reflection.jvm.ReflectionHelper;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.api.world.function.FunctionExecutor;
import rml.loader.api.world.registry.remap.RemapCollection;
import rml.loader.api.world.villagers.IVillager;
import rml.loader.core.RMLFMLLoadingPlugin;
import net.minecraft.command.FunctionObject;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.oredict.OreDictionary;
import rml.deserializer.JsonDeserializeException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/19 12:56
 **/
@PrivateAPI
public class RMLLoaders {
    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2023/12/15 12:54
     **/
    public static class OreDic {
        public static void load(){
            ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "ore_dic")), (context) -> {
                if (context.isExtension("json")) {
                    ResourceLocation key = context.getResourceLocation();
                    try {
                        for(TagOre tagOre : context.deserialize(TagOre[].class)){
                            OreDictionary.registerOre(tagOre.ore, tagOre.item);
                        }
                    } catch (JsonParseException | JsonDeserializeException e) {
                        context.error(e, "Parsing error loading Ore dic {}", key);
                    }
                    catch (IOException e)
                    {
                        context.error(e, "Couldn't read ore dic {} from {}", key, context.getFile());
                    }
                }
            }, OreDic.class);
        }

        public static class TagOre{
            public static final AbstractDeserializer<TagOre> DESERIALIZER = Deserializer.named(RMLLoaders.OreDic.TagOre.class, new ResourceLocation("rml", "ore_tag"))
                    .require(ItemStack.class, "item")
                    .require(String.class, "ore")
                    .decode((context -> {
                        RMLLoaders.OreDic.TagOre tagOre = new RMLLoaders.OreDic.TagOre();
                        tagOre.ore = context.get(String.class, "ore");
                        tagOre.item = context.get(ItemStack.class, "item");
                        return tagOre;
                    })).markDefault().build();

            public String ore;
            public ItemStack item;
        }
    }

    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2023/12/15 12:52
     **/
    public static class LootTable {
        public static void load(final LootTableRegistryEvent event) {
                ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "loot_tables")), (context) -> {
                    try{
                        if (context.isExtension("json")) {
                            ResourceLocation key = context.getResourceLocation();
                            event.register(key);
                        }
                    }catch (Throwable throwable){
                        context.error(throwable, "LootTable register error.");
                    }
                }, LootTable.class);

        }
    }

    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2023/12/14 23:22
     **/

    @SuppressWarnings("all")
    public static class Function {
        public static LineProcessor<List<String>> processor(){ return new LineProcessor<List<String>>() {final List<String> result = Lists.newArrayList();@Override public boolean processLine(String line) {result.add(line);return true;}@Override public List<String> getResult() {return result;}};}

        public static void load(FunctionLoadEvent event) {
            ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "functions")), (context) -> {
                switch (context.getExtension()) {
                    case "mcfunction" :
                        ResourceLocation key = context.getResourceLocation();
                        try {
                            FunctionObject functionObject = FunctionObject.create(
                                    event.functionManager,
                                    ByteSource.wrap(context.getBytes(StandardCharsets.UTF_8))
                                            .asCharSource(StandardCharsets.UTF_8)
                                                    .readLines(processor())
                            );
                            event.register(key, functionObject);
                        } catch (IOException e) {
                            context.error(e,"Couldn't read function {} from {}", key, context.getFile());
                        }
                        break;
                    default:
                        break;
                }
            }, Function.class);
            ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "functions")), (context) -> {
                switch (context.getExtension()) {
                    case "executor" :
                        try {
                            context.deserialize(FunctionExecutor[].class);
                        } catch (IOException e) {
                            context.error(e,"Couldn't read function executor {}", context.getResourceLocation());
                        } catch (JsonDeserializeException e) {
                            context.error(e, "Couldn't read function executor {}", context.getResourceLocation());
                        }
                        break;
                    default:
                        break;
                }
            }, Function.class);
        }
    }
    public static class MissingRemap {
        public static void load() {
            ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "registry_remap")), (context) -> {
                if (context.isExtension("json")) {
                    try {
                        Arrays.stream(context.deserialize(RemapCollection[].class)).forEach(RemapCollection.Manager::merge);
                    } catch (IOException e) {
                        context.error(e, "Could not cache the file {} ", context.getResourceLocation());
                    } catch (JsonDeserializeException e) {
                        context.error(e, "Could not deserialize registry_remap {}", context.getResourceLocation());
                    }
                }
            }, MissingRemap.class);
        }
    }
    /**
     * @Project CustomVillage
     * @Author Hileb
     * @Date 2023/8/16 10:18
     **/
    public static class CustomVillageLoader {

        public static List<IVillager> load() {

            final List<IVillager> list = new ArrayList<>();
            ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "custom_villagers")), (context) -> {
                if (context.isExtension("json")) {
                    try {
                        list.add(context.deserialize(IVillager.class));
                    } catch (JsonParseException | JsonDeserializeException e) {
                        context.error(e,"Parsing error loading villager {}", context.getResourceLocation());
                    } catch (IOException e) {
                        context.error(e,"Couldn't read villager {} from file", context.getResourceLocation());
                    }
                }
            }, CustomVillageLoader.class);
            return list;
        }
    }

    @SuppressWarnings("unused")
    public static class MCMainScreenTextLoader {
        public static List<String> rawTexts;

        public static void load() {
            ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "splash_text")), (context) -> {
                try (BufferedReader bufferedreader = context.openBufferedReader()){
                    String s;
                    while ((s = bufferedreader.readLine()) != null) {
                        s = s.trim();

                        if (!s.isEmpty()) {
                            rawTexts.add(s);
                        }
                    }
                } catch (Throwable e) {
                    context.error(e, "MCMainScreenTextLoaderError");
                }
            }, MCMainScreenTextLoader.class);
        }

        public static ArrayList<String> inject(ArrayList<String> list) {
            rawTexts = list;
            load();
            RMLFMLLoadingPlugin.LOGGER.debug("RML has injected the splashText!");
            return list;
        }

        public static String processComponent(String raw) {
            try {
                return ITextComponent.Serializer.jsonToComponent(raw).getFormattedText();
            } catch (Throwable ignored) {
                return raw;
            }
        }
    }

    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2024/5/12 11:13
     **/
    public static class ConfigLoader {
        public static final FieldAccessor<Map<String, Multimap<Config.Type, ASMDataTable.ASMData>>, ConfigManager> asm_data = ReflectionHelper.getFieldAccessor(ConfigManager.class, "asm_data");

        public static void load(){
            ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "config_define")), (context) -> {
                try
                {
                    ResourceLocation key = context.getResourceLocation();
                    byte[] cfg = context.getBytes(StandardCharsets.UTF_8);
                    ConfigPatcher.OWNED_CONFIGS.put(ConfigFactory.addConfig(key.getPath(), key.getNamespace(), cfg), key.getNamespace());
                    asm_data.get(null).put(key.getNamespace(), HashMultimap.create());
                }
                catch (IOException e)
                {
                    context.error(e, "Couldn't read config define {}", context.getResourceLocation());
                }
            }, ConfigLoader.class);
        }
    }

    public static class ConfigNodeLoader {
        public static final FieldAccessor<Map<String, Multimap<Config.Type, ASMDataTable.ASMData>>, ConfigManager> asm_data = ReflectionHelper.getFieldAccessor(ConfigManager.class, "asm_data");

        public static void load(){
            ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "config_define")), (context) -> {
                if (context.isExtension("json")) {
                    ResourceLocation key = context.getResourceLocation();
                    try
                    {
                        ((ConfigGroup)context.deserialize(ConfigElement.class)).register();
                    }
                    catch (Throwable e)
                    {
                        context.error(e, "Couldn't read config nodes %s", key);
                    }
                }
            }, ConfigNodeLoader.class);
        }
    }
}






