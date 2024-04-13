package mods.rml.deserialize;

import com.google.common.collect.Lists;
import com.google.common.io.LineProcessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import mods.rml.ResourceModLoader;
import mods.rml.api.PrivateAPI;
import mods.rml.api.RMLRegistries;
import mods.rml.api.event.FunctionLoadEvent;
import mods.rml.api.event.LootTableRegistryEvent;
import mods.rml.api.file.FileHelper;
import mods.rml.api.file.JsonHelper;
import mods.rml.api.mods.ContainerHolder;
import mods.rml.api.registry.remap.RemapCollection;
import mods.rml.api.villagers.LoadedVillage;
import mods.rml.api.villagers.VillageReader;
import mods.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.command.FunctionObject;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.GameData;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

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
public class RMLDeserializeLoader {
    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2023/12/15 12:54
     **/
    public static class OreDic {
        private static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        public static void load(){
            ResourceModLoader.loadModuleFindAssets(ContainerHolder.ModuleType.ORE_DIC, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                    return;

                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);
                BufferedReader reader = null;
                try
                {
                    reader = Files.newBufferedReader(file);
                    JsonObject json = JsonHelper.getJson(reader);
                    load(key,json);
                }
                catch (JsonParseException e)
                {
                    FMLLog.log.error("Parsing error loading recipe {}", key, e);
                }
                catch (IOException e)
                {
                    FMLLog.log.error("Couldn't read recipe {} from {}", key, file, e);
                }
                finally
                {
                    IOUtils.closeQuietly(reader);
                }
            });
        }
        public static void load(ResourceLocation name, JsonObject json){
            try{
                TagOre tagOre = GSON.fromJson(json, TagOre.class);
                ItemStack stack = CraftingHelper.getItemStack(tagOre.item,new JsonContext(name.getResourceDomain()));
                OreDictionary.registerOre(tagOre.ore,stack);
                RMLFMLLoadingPlugin.Container.LOGGER.debug("ore: "+tagOre.ore+" ; "+ stack);
            }catch (Exception ignored){
                RMLFMLLoadingPlugin.Container.LOGGER.error(ignored);
            }
        }
        public static class TagOre{
            public String ore;
            public JsonObject item;
        }

    }

    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2023/12/15 12:52
     **/
    public static class LootTable {
        public static void load(LootTableRegistryEvent event) {
            ResourceModLoader.loadModuleFindAssets(ContainerHolder.ModuleType.LOOT_TABLES, (containerHolder, root, file) -> {
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
            ResourceModLoader.loadModuleFindAssets(ContainerHolder.ModuleType.FUNCTIONS, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                if (!"mcfunction".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                    return;
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
            });
        }
    }
    public static class MissingRemap {
        public static void load() {
            ResourceModLoader.loadModuleFindAssets(ContainerHolder.ModuleType.REGISTRY_REMAP, (containerHolder, root, file) -> {
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
        public static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        public static void initVillageRegistry() {
            GameData.fireRegistryEvents(resourceLocation -> RMLRegistries.Names.RANGE_FACTORIES.equals(resourceLocation) || RMLRegistries.Names.VILLAGE_READERS.equals(resourceLocation));
        }

        public static List<LoadedVillage> load() {
            initVillageRegistry();

            final List<LoadedVillage> list = new ArrayList<>();
            ResourceModLoader.loadModuleFindAssets(ContainerHolder.ModuleType.CUSTOM_VILLAGERS, (containerHolder, root, file) -> {
                String relative = root.relativize(file).toString();
                if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                    return;

                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                ResourceLocation key = new ResourceLocation(containerHolder.getContainer().getModId(), name);

                BufferedReader reader = null;
                try {
                    reader = Files.newBufferedReader(file);
                    JsonObject json = JsonHelper.getJson(reader);
                    String s = JsonUtils.getString(json, "type");
                    ResourceLocation resourceLocation = new ResourceLocation(s);
                    if (RMLRegistries.VILLAGE_READERS.containsKey(resourceLocation)) {
                        VillageReader villageReader = RMLRegistries.VILLAGE_READERS.getValue(resourceLocation);
                        try {
                            LoadedVillage loadedVillage = villageReader.load(json);
                            RMLFMLLoadingPlugin.Container.LOGGER.info("load village :" + file.getFileName());
                            list.add(loadedVillage);
                        } catch (Exception e) {
                            RMLFMLLoadingPlugin.Container.LOGGER.error("Error load village at " + file.getFileName());
                            RMLFMLLoadingPlugin.Container.LOGGER.error(e);
                        }

                    } else {
                        RMLFMLLoadingPlugin.Container.LOGGER.error("type: " + s + " not found!");
                    }
                } catch (JsonParseException e) {
                    RMLFMLLoadingPlugin.Container.LOGGER.error("Parsing error loading replacement {}", key, e);
                } catch (IOException e) {
                    RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read replacement {} from {}", key, file, e);
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
            ResourceModLoader.loadModuleFindAssets(ContainerHolder.ModuleType.SPLASH_TEXT, (containerHolder, root, file) -> {
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
}






