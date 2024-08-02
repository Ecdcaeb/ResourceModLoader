package rml.loader.core.mixin.core;

import net.minecraftforge.common.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import rml.loader.api.config.ConfigPatcher;

import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/8/2 12:54
 **/
@Mixin(ConfigManager.class)
public class MixinConfigManager {

    @Redirect(method = "sync(Ljava/lang/String;Lnet/minecraftforge/common/config/Config$Type;)V", remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    remap = false
    ))
    private static Object _sync(Map<Object, Object> instance, Object k, Object v){
        return ConfigPatcher.registerCfg(instance, k, v);
    }
}
