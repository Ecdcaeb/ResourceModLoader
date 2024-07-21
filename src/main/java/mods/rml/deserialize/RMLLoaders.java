package mods.rml.deserialize;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.LineProcessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import mods.rml.ResourceModLoader;
import mods.rml.api.announces.PrivateAPI;
import mods.rml.api.RMLRegistries;
import mods.rml.api.config.ConfigFactory;
import mods.rml.api.config.ConfigPatcher;
import mods.rml.api.event.FunctionLoadEvent;
import mods.rml.api.event.LootTableRegistryEvent;
import mods.rml.api.file.FileHelper;
import mods.rml.api.file.JsonHelper;
import mods.rml.api.java.reflection.jvm.FieldAccessor;
import mods.rml.api.java.reflection.jvm.ReflectionHelper;
import mods.rml.api.mods.module.ModuleType;
import mods.rml.api.world.function.FunctionExecutor;
import mods.rml.api.world.registry.remap.RemapCollection;
import mods.rml.api.world.villagers.IVillager;
import mods.rml.api.world.villagers.VillageReader;
import mods.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.command.FunctionObject;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import rml.deserializer.Deserializer;
import rml.deserializer.JsonDeserializerException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/19 12:56
 **/
@PrivateAPI
public class RMLLoaders {
    public static final JsonParser JSON_PARSER = new JsonParser();
    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2023/12/15 12:54
     **/
    public static class OreDic {
        private static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        public static void load(){
            ResourceModLoader.loadModuleFindAssets(ModuleType.ORE_DIC, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                    return;

                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);
                BufferedReader reader = null;
                try
                {
                    reader = Files.newBufferedReader(file);
                    for(TagOre tagOre : Deserializer.decode(TagOre[].class, JSON_PARSER.parse(reader))){
                        OreDictionary.registerOre(tagOre.ore, tagOre.item);
                        if (RMLFMLLoadingPlugin.isDebug) RMLFMLLoadingPlugin.LOGGER.info("Loading Ore Tag {}, {}", tagOre.ore, tagOre.item.toString());
                    }
                }
                catch (JsonParseException e)
                {
                    FMLLog.log.error("Parsing error loading Ore dic {}", key, e);
                }
                catch (IOException | JsonDeserializerException e)
                {
                    FMLLog.log.error("Couldn't read ore dic {} from {}", key, file, e);
                }finally
                {
                    IOUtils.closeQuietly(reader);
                }
            });
        }

        public static class TagOre{
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
        public static void load(LootTableRegistryEvent event) {
            ResourceModLoader.loadModuleFindAssets(ModuleType.LOOT_TABLES, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                    return;
                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);
                event.register(key);
            });
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
            ResourceModLoader.loadModuleFindAssets(ModuleType.FUNCTIONS, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                switch (FilenameUtils.getExtension(file.toString())) {
                    case "mcfunction" :
                        String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                        ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);
                        try {

                            FunctionObject functionObject = FunctionObject.create(
                                    event.functionManager,
                                    FileHelper.getByteSource(file).asCharSource(StandardCharsets.UTF_8)
                                            .readLines(processor())
                            );
                            event.register(key, functionObject);
                        } catch (IOException e) {
                            RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read function {} from {}", key, file, e);
                        }
                        break;
                    default:
                        break;
                }
            });
            loadExecutors();
        }

        public static void loadExecutors() {

            ResourceModLoader.loadModuleFindAssets(ModuleType.FUNCTIONS, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                switch (FilenameUtils.getExtension(file.toString())) {
                    case "executor" :
                        String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                        ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);
                        try {
                            JsonElement jsonElement = RMLLoaders.JSON_PARSER.parse(Files.newBufferedReader(file));
                            Deserializer.decode(FunctionExecutor[].class, jsonElement);
                        } catch (IOException e) {
                            RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read function executor {} from {}", key, file, e);
                        } catch (JsonDeserializerException e) {
                            RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't load function executor {} from {}", key, file, e);
                            e.printStackTrace();
                        }
                        break;
                    default:
                        break;
                }
            });
        }
    }
    public static class MissingRemap {
        public static void load() {
            ResourceModLoader.loadModuleFindAssets(ModuleType.REGISTRY_REMAP, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                    return;
                try {
                    JsonObject json = JsonHelper.getJson(FileHelper.getCachedFile(file));
                    if (json.has("registry")) {
                        ResourceLocation registry = new ResourceLocation(JsonHelper.getString(json.get("registry")));
                        if (json.has("mapping")) {
                            JsonObject mapping = json.get("mapping").getAsJsonObject();
                            RemapCollection collection = new RemapCollection(registry);
                            for (Map.Entry<String, JsonElement> entry : mapping.entrySet()) {
                                collection.map(new ResourceLocation(entry.getKey()), new ResourceLocation(entry.getValue().getAsString()));
                            }
                            RemapCollection.Manager.merge(collection);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Could not cache the file " + file, e);
                }
            });
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
            ResourceModLoader.loadModuleFindAssets(ModuleType.CUSTOM_VILLAGERS, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                    return;

                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);

                BufferedReader reader = null;
                try {
                    reader = Files.newBufferedReader(file);
                    JsonElement jsonElement = RMLLoaders.JSON_PARSER.parse(reader);
                    try {
                        IVillager IVillager = Deserializer.decode(mods.rml.api.world.villagers.IVillager.class, jsonElement)
                        RMLFMLLoadingPlugin.Container.LOGGER.info("load village :" + file.getFileName());
                        list.add(IVillager);
                    } catch (Exception e) {
                        RMLFMLLoadingPlugin.Container.LOGGER.error("Error load village at " + file.getFileName());
                        RMLFMLLoadingPlugin.Container.LOGGER.error(e);
                    }
                } catch (JsonParseException e) {
                    RMLFMLLoadingPlugin.Container.LOGGER.error("Parsing error loading villager {}", key, e);
                } catch (IOException e) {
                    RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read villager {} from {}", key, file, e);
                } finally {
                    IOUtils.closeQuietly(reader);
                }
            });
            return list;
        }
    }

    @SuppressWarnings("unused")
    public static class MCMainScreenTextLoader {
        public static List<String> rawTexts;

        public static void load() {
            ResourceModLoader.loadModuleFindAssets(ModuleType.SPLASH_TEXT, (containerHolder, root, file) -> {
                BufferedReader bufferedreader = null;
                try {
                    bufferedreader = Files.newBufferedReader(file);
                    String s;
                    while ((s = bufferedreader.readLine()) != null) {
                        s = s.trim();

                        if (!s.isEmpty()) {
                            rawTexts.add(s);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    IOUtils.closeQuietly(bufferedreader);
                }
            });
        }

        public static ArrayList<String> inject(ArrayList<String> list) {
            rawTexts = list;
            load();
            RMLFMLLoadingPlugin.LOGGER.info("RML has injected the splashText!");
            return list;
        }

        public static String processComponent(String raw) {
            try {
                return ITextComponent.Serializer.jsonToComponent(raw).getFormattedText();
            } catch (Exception ignored) {
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
            ResourceModLoader.loadModuleFindAssets(ModuleType.CONFIG_DEFINE, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                if (!"cfg".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                    return;
                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);
                try
                {
                    byte[] cfg = FileHelper.getByteSource(file).read();
                    ConfigPatcher.OWNED_CONFIGS.put(ConfigFactory.addConfig(name, key.getResourcePath(), cfg), key.getResourceDomain());
                    asm_data.get(null).put(key.getResourceDomain(), HashMultimap.create());
                }
                catch (IOException e)
                {
                    FMLLog.log.error("Couldn't read config define {} from {}", key, file, e);
                }
            });
        }
    }
}






