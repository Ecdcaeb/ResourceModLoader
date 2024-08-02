package rml.loader.core.mixin.core;

import net.minecraftforge.common.crafting.CraftingHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rml.loader.api.event.CraftingHelperInitEvent;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/8/2 12:45
 **/
@Mixin(CraftingHelper.class)
public class MixinCraftingHelper {
    @Inject(method = "init", remap = false, at = @At("RETURN"))
    private static void _init(CallbackInfo ci){
        CraftingHelperInitEvent.post();
    }
}
