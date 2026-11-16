package rml.loader.content.gameplay;

import net.minecraft.client.renderer.entity.RenderSlime;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class SulfurCubeClient {
    private SulfurCubeClient() { }
    static void register() {
        RenderingRegistry.registerEntityRenderingHandler(SulfurCubeEntity.class, RenderSlime::new);
    }
}