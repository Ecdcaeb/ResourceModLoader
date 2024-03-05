package mods.Hileb.rml.deserialize;

import com.google.common.collect.Lists;
import com.google.common.io.LineProcessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.event.FunctionLoadEvent;
import mods.Hileb.rml.api.event.LootTableRegistryEvent;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.api.file.JsonHelper;
import mods.Hileb.rml.api.mods.ContainerHolder;
import mods.Hileb.rml.api.registry.remap.RemapCollection;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.command.FunctionObject;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/19 12:56
 **/
@PrivateAPI
public class RMLSerializeLoader {
    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2023/12/15 12:54
     **/
    public static class OreDic {
        private static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        public static void load(){//pre-init
            ModContainer modContainer;
            for(ContainerHolder containerHolder:ResourceModLoader.getCurrentRMLContainerHolders()){
                if ((containerHolder.opinion & ContainerHolder.Opinion.ORE_DIC) != 0){
                    modContainer = containerHolder.container;
                    Loader.instance().setActiveModContainer(modContainer);
                    RMLFMLLoadingPlugin.Container.LOGGER.debug("Load oreDic");

                    FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/ore_dic",null,
                            (root, file) -> {

                                String relative = root.relativize(file).toString();
                                if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                                    return true;

                                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                                ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);
                                BufferedReader reader = null;
                                try
                                {
                                    reader = Files.newBufferedReader(file);
                                    JsonObject json = JsonUtils.fromJson(GSON, reader, JsonObject.class);
                                    load(key,json);
                                }
                                catch (JsonParseException e)
                                {
                                    FMLLog.log.error("Parsing error loading recipe {}", key, e);
                                    return false;
                                }
                                catch (IOException e)
                                {
                                    FMLLog.log.error("Couldn't read recipe {} from {}", key, file, e);
                                    return false;
                                }
                                finally
                                {
                                    IOUtils.closeQuietly(reader);
                                }
                                return true;
                            },true, true);
                    Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
                }

            }
        }
        public static void load(ResourceLocation name, JsonObject json){
            try{
                TagOre tagOre=GSON.fromJson(json, TagOre.class);
                ItemStack stack= CraftingHelper.getItemStack(tagOre.item,new JsonContext(name.getResourceDomain()));
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
        public static void load(LootTableRegistryEvent event){
            ModContainer modContainer;

            for(ContainerHolder containerHolder : ResourceModLoader.getCurrentRMLContainerHolders()){
                if ((containerHolder.opinion & ContainerHolder.Opinion.LOOT_TABLES) != 0) {
                    modContainer = containerHolder.container;
                    Loader.instance().setActiveModContainer(modContainer);

                    FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/loot_tables", null,
                            (root, file) -> {
                                String relative = root.relativize(file).toString();
                                if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                                    return true;
                                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                                ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);
                                event.register(key);
                                return true;
                            }, true, true);
                    Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
                }
            }
        }
    }

    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2023/12/14 23:22
     **/

    public static class Function {


        public static void load(FunctionLoadEvent event){
            ModContainer modContainer;
            for(ContainerHolder containerHolder : ResourceModLoader.getCurrentRMLContainerHolders()){
                if((containerHolder.opinion & ContainerHolder.Opinion.FUNCTIONS) != 0) {
                    modContainer = containerHolder.container;
                    Loader.instance().setActiveModContainer(modContainer);

                    FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/functions", null,
                            (root, file) -> {

                                String relative = root.relativize(file).toString();
                                if (!"mcfunction".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                                    return true;
                                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                                ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);
                                try {

                                    FunctionObject functionObject = FunctionObject.create(
                                            event.functionManager,
                                            FileHelper.getByteSource(file).asCharSource(StandardCharsets.UTF_8)
                                                    .readLines(new LineProcessor<List<String>>() {
                                                        final List<String> result = Lists.newArrayList();

                                                        @Override
                                                        public boolean processLine(String line) {
                                                            result.add(line);
                                                            return true;
                                                        }

                                                        @Override
                                                        public List<String> getResult() {
                                                            return result;
                                                        }
                                                    })
                                    );
                                    event.register(key, functionObject);
                                    return true;
                                } catch (IOException e) {
                                    RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read function {} from {}", key, file, e);
                                    return false;
                                }
                            }, true, true);
                    Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
                }
            }
        }
    }
    public static class MissingRemap{
        public static void load(){
            ModContainer modContainer;
            for(ContainerHolder containerHolder : ResourceModLoader.getCurrentRMLContainerHolders()){
                if ((containerHolder.opinion & ContainerHolder.Opinion.REGISTRY_REMAP) !=0) {
                    modContainer = containerHolder.container;
                    Loader.instance().setActiveModContainer(modContainer);

                    FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/registry/remap", null,
                            (root, file) -> {

                                String relative = root.relativize(file).toString();
                                if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                                    return true;
                                String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                                try {
                                    JsonObject json = FileHelper.GSON.fromJson(FileHelper.getCachedFile(file), JsonObject.class);
                                    if (json.has("registry")) {
                                        ResourceLocation registry = new ResourceLocation(JsonHelper.getString(json.get("registry")));
                                        if (json.has("mapping")) {
                                            JsonObject mapping = json.get("mapping").getAsJsonObject();
                                            RemapCollection collection = new RemapCollection(registry);
                                            for (Map.Entry<String, JsonElement> entry : mapping.entrySet()) {
                                                collection.map(new ResourceLocation(entry.getKey()), new ResourceLocation(entry.getValue().getAsString()));
                                            }
                                            RemapCollection.Manager.merge(collection);
                                            return true;
                                        }
                                        return false;
                                    }
                                    return false;
                                } catch (IOException e) {
                                    throw new RuntimeException("Could not cache the file " + file, e);
                                }
                            }, true, true);
                    Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
                }
            }
        }
    }
}
