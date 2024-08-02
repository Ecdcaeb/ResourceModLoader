package rml.loader.core.mixin.core;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rml.loader.deserialize.RMLLoaders;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/8/2 13:02
 **/
@Mixin(ForgeHooksClient.class)
public class MixinForgeHooksClient {
    @Inject(method = "renderMainMenu", remap = false, at = @At("RETURN"))
    private static void _renderMainMenu(GuiMainMenu gui, FontRenderer font, int width, int height, String splashText, CallbackInfoReturnable<String> cir){
        RMLLoaders.MCMainScreenTextLoader.processComponent(splashText);
    }
}
