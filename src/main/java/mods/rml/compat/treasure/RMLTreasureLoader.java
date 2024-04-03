package mods.rml.compat.treasure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import ins.treasure.Treasure_Main;
import ins.treasure.config.TreasureLoot;
import ins.treasure.config.TreasureLoots;
import mods.rml.ResourceModLoader;
import mods.rml.api.file.FileHelper;
import mods.rml.api.mods.ContainerHolder;
import mods.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/3 18:14
 **/
public class RMLTreasureLoader {
    public static final Gson GSON = new GsonBuilder().create();

    @SubscribeEvent
    public static void onTreasureLoad(ConfigChangedEvent event){
        if (event.getModID().equals("treasure")) {
            for(ContainerHolder containerHolder : ResourceModLoader.getCurrentRMLContainerHolders(ContainerHolder.Modules.MOD_TREASURES)){
                final ModContainer modContainer = containerHolder.container;
                Loader.instance().setActiveModContainer(modContainer);
                FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/mods/treasure",
                        (root, file) ->
                        {
                            String relative = root.relativize(file).toString();
                            if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                                return;

                            String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                            ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);

                            BufferedReader reader = null;
                            try
                            {
                                reader = Files.newBufferedReader(file);
                                JsonObject json = JsonUtils.fromJson(FileHelper.GSON, reader, JsonObject.class);
                                loadTreasures(json);
                            }
                            catch (JsonParseException e)
                            {
                                RMLFMLLoadingPlugin.Container.LOGGER.error("Parsing error loading config redefault {}", key, e);
                            }
                            catch (IOException e)
                            {
                                RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read config redefault {} from {}", key, file, e);
                            }
                            finally
                            {
                                IOUtils.closeQuietly(reader);
                            }
                        });
                Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
            }
        }
    }

    public static void loadTreasures(JsonObject jsonObject){
        try {
            Treasure_Main.LOGGER.info(s);
            TreasureLoot loot = GSON.fromJson(jsonObject, TreasureLoot.class);
            TreasureLoots.LOOTS.put(loot.id, loot);
        } catch (JsonSyntaxException var8) {
            Treasure_Main.LOGGER.error("Error during loading treasure loots! Skipping load loots! {}", var8.getLocalizedMessage());
        }
    }
}
