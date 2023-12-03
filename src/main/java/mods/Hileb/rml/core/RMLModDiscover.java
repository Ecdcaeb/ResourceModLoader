package mods.Hileb.rml.core;

import com.google.gson.*;
import mods.Hileb.rml.RMLModContainer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.MetadataCollection;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 9:53
 *
 **/
@SuppressWarnings("unused")
public class RMLModDiscover {

    public static void inject(List<ModContainer> modContainers){
        RMLFMLPlugin.Container.LOGGER.info("rml inject ModContainer(s)");

        File modRoots=new File((File)ReflectionHelper.getPrivateValue(Loader.class,null,"minecraftDir"),"mods");

        for (File modFile : modRoots.listFiles()) {
            if (modFile.getName().endsWith(".jar") || modFile.getName().endsWith(".zip")) {
                RMLFMLPlugin.Container.LOGGER.error("mod! " + modFile.getName());
                try {
                    ZipFile zipFile = new ZipFile(modFile);
                    ZipEntry info = zipFile.getEntry("rml.info");
                    InputStreamReader reader = new InputStreamReader(zipFile.getInputStream(info), StandardCharsets.UTF_8);
                    Gson gson = new GsonBuilder().registerTypeAdapter(ArtifactVersion.class, new MetadataCollection.ArtifactVersionAdapter()).create();
                    JsonParser parser = new JsonParser();
                    JsonElement rootElement = parser.parse(reader);
                    if (rootElement.isJsonArray()) {
                        JsonArray jsonList = rootElement.getAsJsonArray();
                        for (JsonElement mod : jsonList) {
                            ModMetadata metadata = gson.fromJson(mod, ModMetadata.class);
                            modContainers.add(new RMLModContainer(metadata, modFile));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
