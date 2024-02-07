package mods.Hileb.rml.api.config;


import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.clazz.ClassValueTransformer;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/6 12:58
 **/
@PrivateAPI
public class ConfigTransformer {
    @PrivateAPI private static final Method m_sync=ReflectionHelper.findMethod(ConfigManager.class,"sync","sync", Configuration.class, Class.class, String.class, String.class, boolean.class, Object.class);
    static {
        m_sync.setAccessible(true);

        searchRedefault();
        searchOverride();
    }
    @PrivateAPI public static void sync(Configuration cfg, Class<?> cls, String modid, String category, boolean loading, Object instance)  {
        transformConfigRedefalut(cls,cfg.getConfigFile().getName());
        try {
            m_sync.invoke(null,cfg,cls,modid,category,loading,instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        transformConfigOverride(cls,cfg.getConfigFile().getName());
    }
    @PrivateAPI public static void transformConfigRedefalut(Class<?> clazz, String name){
        for (JsonObject json : cachedRedefault.get(name)) {
            try {
                ClassValueTransformer.transform(clazz, json, null);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not load redefault config for " + name, e);
            }
        }
        cachedRedefault.removeAll(name);
    }
    @PrivateAPI public static void transformConfigOverride(Class<?> clazz, String name){
        for(JsonObject json:cachedOverrides.get(name)){
            try {
                ClassValueTransformer.transform(clazz,json,null);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not load override config for "+ name ,e);
            }
        }
        cachedOverrides.removeAll(name);
    }
    @PrivateAPI private static Multimap<String,JsonObject> cachedRedefault=HashMultimap.create();
    @PrivateAPI public static void searchRedefault(){
        for(ModContainer modContainer:ResourceModLoader.getCurrentRMLContainers()){
            Loader.instance().setActiveModContainer(modContainer);
            FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/config/redefault",null,
                    (root, file) ->
                    {
                        String relative = root.relativize(file).toString();
                        if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                            return true;

                        String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                        ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);

                        BufferedReader reader = null;
                        try
                        {
                            reader = Files.newBufferedReader(file);
                            JsonObject json = JsonUtils.fromJson(FileHelper.GSON, reader, JsonObject.class);
                            cachedRedefault.put(name,json);
                        }
                        catch (JsonParseException e)
                        {
                            RMLFMLLoadingPlugin.Container.LOGGER.error("Parsing error loading config redefault {}", key, e);
                            return false;
                        }
                        catch (IOException e)
                        {
                            RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read config redefault {} from {}", key, file, e);
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


    @PrivateAPI  private static Multimap<String,JsonObject> cachedOverrides=HashMultimap.create();
    @PrivateAPI public static void searchOverride(){
        for(ModContainer modContainer:ResourceModLoader.getCurrentRMLContainers()){
            Loader.instance().setActiveModContainer(modContainer);
            FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/config/override",null,
                    (root, file) ->
                    {
                        String relative = root.relativize(file).toString();
                        if (!"json".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                            return true;

                        String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                        ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);

                        BufferedReader reader = null;
                        try
                        {
                            reader = Files.newBufferedReader(file);
                            JsonObject json = JsonUtils.fromJson(FileHelper.GSON, reader, JsonObject.class);
                            cachedOverrides.put(name,json);
                        }
                        catch (JsonParseException e)
                        {
                            RMLFMLLoadingPlugin.Container.LOGGER.error("Parsing error loading config override {}", key, e);
                            return false;
                        }
                        catch (IOException e)
                        {
                            RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read config override {} from {}", key, file, e);
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
