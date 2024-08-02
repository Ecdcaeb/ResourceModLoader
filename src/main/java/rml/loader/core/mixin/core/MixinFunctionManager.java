package rml.loader.core.mixin.core;

import net.minecraft.advancements.FunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/8/2 18:02
 **/
@Mixin(FunctionManager.class)
public class MixinFunctionManager {

    @Inject(method = "loadFunctions", remap = true, at = @At())
    public void _loadFunctions(){

    }
}
