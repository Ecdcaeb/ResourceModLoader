package rml.loader.core.mixin.core;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rml.loader.core.RMLModDiscover;

import java.util.List;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/8/2 12:52
 **/

@Mixin(Loader.class)
public class MixinLoader {

    @Inject(method = "identifyDuplicates", remap = false, at = @At("HEAD"))
    public void _identifyDuplicates(List<ModContainer> mods, CallbackInfo ci){
        RMLModDiscover.inject(mods);
    }
}
