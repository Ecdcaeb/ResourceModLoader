package rml.loader.core.mixin.core;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.client.GuiModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.List;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/8/2 13:06
 **/
@Mixin(targets = "net.minecraftforge.fml.client.GuiModList$Info")
public abstract class MixinGuiModListInfo {

    @Shadow(remap = false)
    private List<ITextComponent> lines = null;

    @Inject(method = "<init>", remap = false,
    at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/fml/client/GuiModList$Info;setHeaderInfo(ZI)V",
            remap = false
    ))
    public void _init(GuiModList width, int lines, List logoPath, ResourceLocation logoDims, Dimension par5, CallbackInfo ci){}
}
