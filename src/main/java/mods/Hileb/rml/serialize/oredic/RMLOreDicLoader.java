package mods.Hileb.rml.serialize.oredic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/15 12:54
 **/
public class RMLOreDicLoader {
    private static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static void load(){//pre-init
        for(ModContainer modContainer: Loader.instance().getActiveModList()){
            Loader.instance().setActiveModContainer(modContainer);

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
    public static void load(ResourceLocation name, JsonObject json){
        try{
            TagOre tagOre=GSON.fromJson(json,TagOre.class);
            ItemStack stack= CraftingHelper.getItemStack(tagOre.item,new JsonContext(name.getResourceDomain()));
            OreDictionary.registerOre(tagOre.ore,stack);
        }catch (Exception ignored){
            RMLFMLLoadingPlugin.Container.LOGGER.error(ignored);
        }
    }
    public static class TagOre{
        public String ore;
        public JsonObject item;
    }

}
