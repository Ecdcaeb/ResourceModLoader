package mods.Hileb.rml.core;

import mods.Hileb.rml.RMLModContainer;
import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.api.PrivateAPI;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.MetadataCollection;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
    @PrivateAPI
    public static void inject(List<ModContainer> modContainers){
        RMLFMLLoadingPlugin.Container.LOGGER.info("rml inject ModContainer(s)");

        File modRoots=new File((File)ReflectionHelper.getPrivateValue(Loader.class,null,"minecraftDir"),"mods");

        for (File modFile : modRoots.listFiles()) {
            try(ZipFile zipFile = new ZipFile(modFile)) {
                ZipEntry info = zipFile.getEntry("rml.info");
                if (info!=null){//fix: https://mclo.gs/4yyaEH5
                    InputStream inputStream = zipFile.getInputStream(info);
                    MetadataCollection metadataCollection=MetadataCollection.from(inputStream,modFile.getName());
                    for(ModMetadata metadata:(ModMetadata[])ReflectionHelper.getPrivateValue(MetadataCollection.class,metadataCollection,"modList")){
                        modContainers.add(ResourceModLoader.enableRML(new RMLModContainer(metadata, modFile)));
                    }
                }
            } catch (IOException e) {
                RMLFMLLoadingPlugin.Container.LOGGER.error("could not read "+modFile.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }
}
