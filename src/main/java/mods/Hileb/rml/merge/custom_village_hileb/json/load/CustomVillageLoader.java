package com.Hileb.custom_village_hileb.json.load;

import com.Hileb.custom_village_hileb.CustomVillageModMain;
import com.Hileb.custom_village_hileb.json.load.event.CustomVillageLoaderRegisterEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:18
 **/
public class CustomVillageLoader {
    public static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static List<LoadedVillage> load(){
        MinecraftForge.EVENT_BUS.post(new CustomVillageLoaderRegisterEvent());
        List<LoadedVillage> list=new ArrayList<>();
        for(ModContainer mod: Loader.instance().getActiveModList()){
            CraftingHelper.findFiles(mod, "assets/" + mod.getModId() + "/villages", root ->true,
                    (root,file)->{
                        String relative = root.relativize(file).toString();
                        if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                            return true;

                        String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                        ResourceLocation key = new ResourceLocation(mod.getModId(), name);

                        BufferedReader reader = null;
                        try
                        {
                            reader = Files.newBufferedReader(file);
                            JsonObject json = JsonUtils.fromJson(GSON, reader, JsonObject.class);
                            String s=JsonUtils.getString(json,"type");
                            ResourceLocation resourceLocation=new ResourceLocation(s);
                            if (VillageReader.REGISTERS.keySet().contains(resourceLocation)){
                                VillageReader villageReader=VillageReader.REGISTERS.get(resourceLocation);
                                try{
                                    LoadedVillage loadedVillage=villageReader.load(json);
                                    CustomVillageModMain.LOGGER.info("load village :"+file.getFileName());
                                    list.add(loadedVillage);
                                }catch (Exception e){
                                    CustomVillageModMain.LOGGER.error("Error load village at "+file.getFileName());
                                    CustomVillageModMain.LOGGER.error(e);
                                }

                            }else {
                                CustomVillageModMain.LOGGER.error("type: "+s+" not found!");
                            }
                        }
                        catch (JsonParseException e)
                        {
                            CustomVillageModMain.LOGGER.error("Parsing error loading replacement {}", key, e);
                            return false;
                        }
                        catch (IOException e)
                        {
                            CustomVillageModMain.LOGGER.error("Couldn't read replacement {} from {}", key, file, e);
                            return false;
                        }
                        finally
                        {
                            IOUtils.closeQuietly(reader);
                        }
                        return true;
                    },
                    true, true
            );
        }
        return list;
    }
}
