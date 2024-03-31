package mods.Hileb.rml.api.config;


import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.api.file.JsonHelper;
import mods.Hileb.rml.api.java.reflection.ReflectionHelper;
import mods.Hileb.rml.api.mods.ContainerHolder;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/6 12:58
 **/
@PrivateAPI
@SuppressWarnings("all")
public class ConfigTransformer {
    private static Map<String, Configuration> CONFIGS = ReflectionHelper.getPrivateValue(ConfigManager.class,  null,"CONFIGS");
    private static Map<String, Set<Class<?>>> MOD_CONFIG_CLASSES = ReflectionHelper.getPrivateValue(ConfigManager.class,  null,"MOD_CONFIG_CLASSES");
    public static void transformTheConfiguration(Configuration config, JsonObject json){
        Multimap<String,String> multimap = HashMultimap.create();
        for(String s:JsonHelper.walkAndGetAllEntryPath(json)){
            String[] s1=s.split("\\.");
            String s2=s1[s1.length-1];
            multimap.put(s.substring(0,s.length()-s2.length()-1),s2);
        }
        for(String cateName:multimap.keys()){
            ConfigElement configElement=new ConfigElement(config.getCategory(cateName));
            for(IConfigElement iConfigElement:configElement.getChildElements()){
                for(String childName:multimap.get(cateName)){
                    if (childName.equals(iConfigElement.getName())){
                        JsonElement jsonElement=JsonHelper.walkAndGetTheElement(json,cateName+"."+childName);
                        if (JsonHelper.isList(jsonElement)){
                            iConfigElement.set(JsonHelper.getAsRawList(jsonElement));
                        }else iConfigElement.set(JsonHelper.getRawValue(jsonElement));
                    }
                }
            }
        }
        config.save();//not save auto. Since we break the loading rules.
    }
    //INVOKEINTERFACE java/util/Map.put (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; (itf)
    //registerCfg(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)V
    @SuppressWarnings("unused")
    public static Object registerCfg(Map<Object, Object> map, Object string, Object configuration){
        RMLFMLLoadingPlugin.Container.LOGGER.info("register a config {}",string );
        transformConfigRedefalut((Configuration) configuration,FilenameUtils.getName((String)string));
        return map.put(string,configuration);
    }
    @SuppressWarnings("unchecked")
    public static void handleOverride(){
        RMLFMLLoadingPlugin.Container.LOGGER.info("config con {}",((Map<String, Configuration>)ReflectionHelper.getPrivateValue(ConfigManager.class, null,"CONFIGS")).values().size());
        for(Configuration configuration:((Map<String, Configuration>)ReflectionHelper.getPrivateValue(ConfigManager.class, null,"CONFIGS")).values()){
            transformConfigOverride(configuration,FilenameUtils.getName(configuration.getConfigFile().getName()));
        }
    }
    @PrivateAPI public static void transformConfigRedefalut(Configuration configuration, String name){
        RMLFMLLoadingPlugin.Container.LOGGER.debug("try apply config Redefalut for {}",name);
        for (JsonObject json : cachedRedefault.get(name)) {
            transformTheConfiguration(configuration,json);
            RMLFMLLoadingPlugin.Container.LOGGER.debug("apply config redefault for {}",name);
        }
        cachedRedefault.removeAll(name);
    }
    @PrivateAPI public static void transformConfigOverride(Configuration configuration, String name){
        RMLFMLLoadingPlugin.Container.LOGGER.info("try apply config override for {}",name);
        for(JsonObject json:cachedOverrides.get(name)){
            transformTheConfiguration(configuration,json);
            RMLFMLLoadingPlugin.Container.LOGGER.info("apply config override for {} ",name);
        }
        cachedOverrides.removeAll(name);
    }
    @PrivateAPI private static final Multimap<String,JsonObject> cachedRedefault=HashMultimap.create();
    @PrivateAPI public static void searchRedefault(){
        for(ContainerHolder containerHolder:ResourceModLoader.getCurrentRMLContainerHolders()){
            if (containerHolder.modules.contains(ContainerHolder.Modules.CONFIG_REDEFAULT)){
                final ModContainer modContainer = containerHolder.container;
                Loader.instance().setActiveModContainer(modContainer);
                FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/config/redefault",
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
                                cachedRedefault.put(name,json);
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
        RMLFMLLoadingPlugin.Container.LOGGER.info("Search {} config redefault",cachedRedefault.size());
    }


    @PrivateAPI  private static final Multimap<String,JsonObject> cachedOverrides=HashMultimap.create();
    @PrivateAPI public static void searchOverride(){
        for(ContainerHolder containerHolder : ResourceModLoader.getCurrentRMLContainerHolders()){
            if (containerHolder.modules.contains(ContainerHolder.Modules.CONFIG_OVERRIDE)){
                final ModContainer modContainer = containerHolder.container;
                Loader.instance().setActiveModContainer(modContainer);
                FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/config/override",
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
                                cachedOverrides.put(name,json);
                                RMLFMLLoadingPlugin.Container.LOGGER.info("find {} {}",name,json);
                            }
                            catch (JsonParseException e)
                            {
                                RMLFMLLoadingPlugin.Container.LOGGER.error("Parsing error loading config override {}", key, e);
                            }
                            catch (IOException e)
                            {
                                RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read config override {} from {}", key, file, e);
                            }
                            finally
                            {
                                IOUtils.closeQuietly(reader);
                            }
                        });
                Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
            }

        }
        RMLFMLLoadingPlugin.Container.LOGGER.info("Search {} config overrides",cachedOverrides.size());
    }
}
