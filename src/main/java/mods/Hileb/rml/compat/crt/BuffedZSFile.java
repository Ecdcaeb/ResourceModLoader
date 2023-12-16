package mods.Hileb.rml.compat.crt;

import crafttweaker.runtime.IScriptIterator;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/16 18:47
 **/
public class BuffedZSFile implements IScriptIterator {
    public final ResourceLocation name;
    public final byte[] file;
    private boolean first = true;
    public BuffedZSFile(ResourceLocation name,byte[] file){
        this.file=file;
        this.name=name;
    }
    @Override
    public String getGroupName() {
        return name.getResourceDomain();
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
        return name.toString().replace(':','$');
    }

    @Override
    public InputStream open() throws IOException {
        return new ByteArrayInputStream(file);
    }

    @Override
    public IScriptIterator copyCurrent() {
        return new BuffedZSFile(name,file);
    }

    @SuppressWarnings("unused")
    public static void buildInLoader(List<IScriptIterator> scripts){
        RMLFMLLoadingPlugin.Container.LOGGER.info("Inject into Crt");
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
                            BuffedZSFile zsFile=new BuffedZSFile(key,FileHelper.getByteSource(file).read());
                            scripts.add(zsFile);
                            RMLFMLLoadingPlugin.Container.LOGGER.debug(key.toString());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return true;
                    },true, true);

            Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
        }
    }
}
