package rml.layer.compat.justenoughdimensions;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import fi.dy.masa.justenoughdimensions.config.DimensionConfig;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.reflection.jvm.MethodAccessor;
import rml.loader.api.reflection.jvm.ReflectionHelper;
import rml.loader.ResourceModLoader;
import rml.loader.api.mods.module.ModuleType;

import java.io.IOException;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/8/13 20:27
 **/
public class JEDLoader {
    public static final MethodAccessor<Void, fi.dy.masa.justenoughdimensions.config.DimensionConfig> parseDimensionConfig = ReflectionHelper.getMethodAccessor(DimensionConfig.class, "parseDimensionConfig", "parseDimensionConfig", JsonElement.class);

    public static void load(){
        ResourceModLoader.loadModule(ModuleType.valueOf(new ResourceLocation("rml", "justenoughdimensions")), (context) -> {
            try{
                if (context.isExtension("json")) {
                    fromJson(context.toJson());
                }
            } catch (IOException e) {
                context.error(e, "Could not read {}", context.getResourceLocation());
            } catch (JsonSyntaxException | JsonDeserializeException e){
                context.error(e, "Could not read as Json {}", context.getResourceLocation());
            }
        });
    }

    public static void fromJson(JsonElement jsonElement){
        parseDimensionConfig.invoke(DimensionConfig.instance(), jsonElement);
    }
}
