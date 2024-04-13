package mods.rml.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mods.rml.RMLModContainer;
import mods.rml.ResourceModLoader;
import mods.rml.api.PrivateAPI;
import mods.rml.api.RMLBus;
import mods.rml.api.config.ConfigTransformer;
import mods.rml.api.event.RMLAfterInjectEvent;
import mods.rml.api.mods.ContainerHolder;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.MetadataCollection;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.VersionParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 9:53
 *
 **/
@PrivateAPI
@SuppressWarnings("unused")
public class RMLModDiscover {
    public static final Gson GSON = new GsonBuilder().registerTypeAdapter(ArtifactVersion.class, new MetadataCollection.ArtifactVersionAdapter()).create();
    @PrivateAPI
    public static void inject(List<ModContainer> modContainers){
        RMLFMLLoadingPlugin.Container.LOGGER.info("rml inject ModContainer(s)");

        File modRoots=new File(Launch.minecraftHome,"mods");

        for (File modFile : Objects.requireNonNull(modRoots.listFiles(), "Directory `mods/` is not exist")) {
            if(modFile.isFile()){
                try(ZipFile zipFile = new ZipFile(modFile)) {
                    ZipEntry info = zipFile.getEntry("rml.info");
                    if (info!=null){
                        InputStream inputStream = zipFile.getInputStream(info);
                        JsonArray jsonArray = GSON.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonArray.class);

                        for(int i = 0, size = jsonArray.size(); i<size ; i++){
                            modContainers.add(ResourceModLoader.enableRML(makeContainer(jsonArray.get(i).getAsJsonObject(), modFile)));
                        }
                    }
                } catch (IOException e) {
                    RMLFMLLoadingPlugin.Container.LOGGER.error("could not read "+modFile.getAbsolutePath());
                    e.printStackTrace();
                }
            }else if (modFile.isDirectory()){
                File[] files = modFile.listFiles(pathname -> pathname.isFile() && "rml.info".equals(pathname.getName()));
                if (files!=null && files.length==1){
                    try {
                        InputStream inputStream = Files.newInputStream(files[0].toPath());
                        JsonArray jsonArray = GSON.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonArray.class);

                        for(int i = 0, size = jsonArray.size(); i<size ; i++){
                            modContainers.add(ResourceModLoader.enableRML(makeContainer(jsonArray.get(i).getAsJsonObject(), modFile)));
                        }
                    } catch (IOException e) {
                        RMLFMLLoadingPlugin.Container.LOGGER.error("could not read "+modFile.getAbsolutePath());
                        e.printStackTrace();
                    }
                }

            }
        }
        afterInject();
    }
    @PrivateAPI public static void afterInject(){
        ConfigTransformer.searchRedefault();
        ConfigTransformer.searchOverride();
        RMLBus.BUS.post(new RMLAfterInjectEvent());
    }

    @PrivateAPI public static ContainerHolder makeContainer(JsonObject jsonObject, File modFile){
        ModMetadata metadata = decodeMetaData(jsonObject);
        ContainerHolder.Module[] modules;
        if (jsonObject.has("modules")){
            JsonArray array = jsonObject.getAsJsonArray("modules");
            int size = array.size();
            modules = new ContainerHolder.Module[size];
            try{
                for(int i=0; i < size; i++) {
                    modules[i] = ContainerHolder.Module.decode(array.get(i));
                }
            }catch (NullPointerException e){
                throw new RuntimeException("illegal modules opinion for " + jsonObject.get("modid").getAsString(), e);
            }
        }else {
            modules = ContainerHolder.ModuleType.getAllForDefault();
        }

        return new ContainerHolder(new RMLModContainer(metadata, modFile), modules);
    }

    @SuppressWarnings("all")
    public static ModMetadata decodeMetaData(JsonObject json){
        ModMetadata metadata = new ModMetadata();

        //basic message
        metadata.modId = json.get("modid").getAsString();
        metadata.name = json.get("name").getAsString();

        //optional message
        if (json.has("description")) metadata.description = json.get("description").getAsString();
        if (json.has("credits")) metadata.credits = json.get("credits").getAsString();
        if (json.has("url")) metadata.url = json.get("url").getAsString();
        if (json.has("updateJSON")) metadata.updateJSON = json.get("updateJSON").getAsString();
        if (json.has("logoFile")) metadata.logoFile = json.get("logoFile").getAsString();
        if (json.has("version")) metadata.version = json.get("version").getAsString();
        if (json.has("parent")) metadata.parent = json.get("parent").getAsString();
        if (json.has("useDependencyInformation")) metadata.useDependencyInformation = json.get("useDependencyInformation").getAsBoolean();
        if (metadata.useDependencyInformation){
            if (json.has("requiredMods")){
                for(JsonElement element : json.getAsJsonArray("requiredMods")){
                    metadata.requiredMods.add(VersionParser.parseVersionReference(element.getAsString()));
                }
            }
            if (json.has("dependencies")){
                for(JsonElement element : json.getAsJsonArray("dependencies")){
                    metadata.dependencies.add(VersionParser.parseVersionReference(element.getAsString()));
                }
            }
            if (json.has("dependants")){
                for(JsonElement element : json.getAsJsonArray("dependants")){
                    metadata.dependants.add(VersionParser.parseVersionReference(element.getAsString()));
                }
            }
        }
        if (json.has("authorList")){
            for(JsonElement element : json.getAsJsonArray("authorList")){
                metadata.authorList.add(element.getAsString());
            }
        }
        if (json.has("screenshots")){ // this field was never used
            JsonArray array = json.getAsJsonArray("screenshots");
            int size = array.size();
            String[] screenshots = new String[size];
            for (int i = 0; i < size; i++){
                screenshots[i] = array.get(i).getAsString();
            }
            metadata.screenshots = screenshots;
        }else metadata.screenshots = new String[0];
        if (json.has("updateUrl")){ // this field is out of date
            metadata.updateUrl = json.get("updateUrl").getAsString();
            FMLLog.log.warn("{} is using a deprecated field 'updateUrl' in mcmod.info. Never really used for anything and format is undefined. See updateJSON for replacement.", metadata.modId);
        }

        return metadata;
    }
}
