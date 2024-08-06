package rml.layer.compat.groovyscripts;

import com.google.common.collect.HashBiMap;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.Argument;
import rml.jrx.announces.RewriteWhenCleanroom;
import rml.jrx.utils.ClassHelper;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.deserialize.Deserializer;
import rml.loader.deserialize.RMLLoaders;
import rml.loader.ResourceModLoader;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/30 10:26
 **/

@RewriteWhenCleanroom
public class RMLGrsLoader {
    public static void load(){
        ClassHelper.forceInit(RunConfig.class);
        ResourceModLoader.loadModuleFindAssets(ModuleType.valueOf(new ResourceLocation("rml", "mod_groovy_script")), (containerHolder, module, root, file) -> {
            BufferedReader bufferedreader = null;
            try {
                RunConfig config = Deserializer.decode(RunConfig.class, RMLLoaders.JSON_PARSER.parse(Files.newBufferedReader(file)));
                MOD.put(containerHolder, config);
            } catch (Exception ignored) {
            } finally {
                IOUtils.closeQuietly(bufferedreader);
            }
        });
    }
    public static HashBiMap<ContainerHolder, RunConfig> MOD = HashBiMap.create();

    public static class RunConfig{
        public static final Argument<Map<String, String[]>> ARG_CLASSES = Argument.map("classes", String[].class);
        public static final AbstractDeserializer<RunConfig> DESERIALIZER = Deserializer.named(RunConfig.class, new ResourceLocation("rml", "groovy"))
                .require(ARG_CLASSES)
                .decode(context -> new RunConfig(ARG_CLASSES.cast(context)))
                .markDefault().build();
        private final Map<String, String[]> classes;

        public RunConfig(Map<String, String[]> classes){
            this.classes = classes;
        }

        public String[] getClasses(String loader){
            String[] a1 = classes.get("all");
            String[] a2 = classes.get(loader);
            if (a1 == null && a2 == null){
                return new String[0];
            }else if (a1 == null) return a2.clone();
            else if (a2 == null) return a1.clone();
            else {
                String[] clone = new String[a1.length + a2.length];
                System.arraycopy(a1, 0, clone, 0, a1.length);
                System.arraycopy(a2, 0, clone, a1.length, a2.length);
                return clone;
            }
        }

    }

}
