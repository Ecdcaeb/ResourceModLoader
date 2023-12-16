package mods.Hileb.rml.serialize.function;

import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;
import mods.Hileb.rml.api.event.FunctionLoadEvent;
import mods.Hileb.rml.api.file.FileHelper;
import mods.Hileb.rml.core.RMLFMLLoadingPlugin;
import net.minecraft.command.FunctionObject;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.FilenameUtils;

import java.io.CharArrayReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/14 23:22
 **/

public class RMLFunctionLoader {


    public static void load(FunctionLoadEvent event){
        for(ModContainer modContainer: Loader.instance().getActiveModList()){
            Loader.instance().setActiveModContainer(modContainer);

            FileHelper.findFiles(modContainer, "assets/" + modContainer.getModId() + "/functions",null,
                    (root, file) -> {

                        String relative = root.relativize(file).toString();
                        if (!"mcfunction".equals(FilenameUtils.getExtension(file.toString())) || relative.startsWith("_"))
                            return true;
                        String name = FilenameUtils.removeExtension(relative).replaceAll("\\\\", "/");
                        ResourceLocation key = new ResourceLocation(modContainer.getModId(), name);
                        try {

                            FunctionObject functionObject=FunctionObject.create(
                                    event.functionManager,
                                    FileHelper.getByteSource(file).asCharSource(StandardCharsets.UTF_8)
                                            .readLines(new LineProcessor<List<String>>(){
                                                final List<String> result = Lists.newArrayList();
                                                @Override
                                                public boolean processLine(String line) {
                                                    result.add(line);
                                                    return true;
                                                }

                                                @Override
                                                public List<String> getResult() {
                                                    return result;
                                                }
                                            })
                            );
                            event.register(key,functionObject);
                            return true;
                        } catch (IOException e) {
                            RMLFMLLoadingPlugin.Container.LOGGER.error("Couldn't read function {} from {}", key, file, e);
                            return false;
                        }
                    },true, true);
            Loader.instance().setActiveModContainer(RMLFMLLoadingPlugin.Container.INSTANCE);
        }
    }
}
