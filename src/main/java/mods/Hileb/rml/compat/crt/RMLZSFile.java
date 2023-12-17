package mods.Hileb.rml.compat.crt;

import crafttweaker.runtime.CrTTweaker;
import crafttweaker.runtime.IScriptIterator;
import crafttweaker.runtime.ITweaker;
import crafttweaker.runtime.ScriptFile;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/17 13:21
 **/
public class RMLZSFile extends ScriptFile {
    public RMLZSFile(ITweaker tweaker, boolean isSyntaxCommand,ResourceLocation name,byte[] file) {
        super(tweaker, new RMLScriptIterator(name,file), isSyntaxCommand);
    }

    public static class RMLScriptIterator extends IForgeRegistryEntry.Impl<RMLScriptIterator> implements IScriptIterator{
        private boolean first = true;
        public final byte[] file;
        public RMLScriptIterator(ResourceLocation name,byte[] file){
            this.file=file;
            setRegistryName(name);
        }
        @Override
        public String getGroupName() {
            return getRegistryName().getResourceDomain();
        }

        @Override
        public boolean next() {
            if (this.first) {
                this.first = false;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public String getName() {
            return getRegistryName().toString().replace(':','$');
        }

        @Override
        public InputStream open() throws IOException {
            return new ByteArrayInputStream(file);
        }

        @Override
        public IScriptIterator copyCurrent() {
            return new RMLScriptIterator(getRegistryName(),file);
        }
    }
    public static List<ScriptFile> buildInLoad(List<ScriptFile> fileList, CrTTweaker tweaker,boolean z){
        for(ModContainer modContainer: Loader.instance().getActiveModList()){
            Loader.instance().setActiveModContainer(modContainer);
            FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/crt",null,
                    (root, file) ->
                    {
                        String relative = root.relativize(file).toString();
                        if (!"zs".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                            return true;

                        String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                        ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);
                        try{
                            RMLZSFile rmlzsFile=new RMLZSFile(tweaker,z,key,FileHelper.getByteSource(file).read());
                            fileList.add(rmlzsFile);
                            RMLFMLLoadingPlugin.Container.LOGGER.debug(key.toString());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return true;
                    },true, true);

            Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
        }
        return fileList;
    }
}
