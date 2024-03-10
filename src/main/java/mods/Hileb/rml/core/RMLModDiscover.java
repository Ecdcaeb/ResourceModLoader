package mods.Hileb.rml.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mods.Hileb.rml.RMLModContainer;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.RMLBus;
import mods.Hileb.rml.api.config.ConfigTransformer;
import mods.Hileb.rml.api.event.RMLAfterInjectEvent;
import mods.Hileb.rml.api.file.JsonHelper;
import mods.Hileb.rml.api.mods.ContainerHolder;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.MetadataCollection;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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

        File modRoots=new File((File)ReflectionHelper.getPrivateValue(Loader.class,null,"minecraftDir"),"mods");

        for (File modFile : Objects.requireNonNull(modRoots.listFiles(), "Directory `mods/` is not exist")) {
            if(modFile.isDirectory()){
                try(ZipFile zipFile = new ZipFile(modFile)) {
                    ZipEntry info = zipFile.getEntry("rml.info");
                    if (info!=null){//fix: https://mclo.gs/4yyaEH5
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
            }else if (modFile.isFile()){
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
        ArrayList<ContainerHolder.Modules> modules = new ArrayList<>();
        if (jsonObject.has("modules")){
            JsonArray array = jsonObject.getAsJsonArray("modules");
            try{
                for(String name : JsonHelper.getStringArray(array)) {
                    modules.add(ContainerHolder.Modules.valueOf(name));
                }
            }catch (NullPointerException e){
                throw new RuntimeException("illegal modules opinion for "+jsonObject.get("modid").getAsString(), e);
            }
            jsonObject.remove("modules");
        }
        ModMetadata metadata = GSON.fromJson(jsonObject, ModMetadata.class);
        return new ContainerHolder(new RMLModContainer(metadata, modFile), modules.toArray(new ContainerHolder.Modules[0]));
    }
}
