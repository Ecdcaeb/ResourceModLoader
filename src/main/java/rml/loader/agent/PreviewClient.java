package rml.loader.agent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

import java.io.File;

/** Client-only launch and window lifecycle for the preview's real integrated world. */
public final class PreviewClient {
    private static volatile boolean launched;
    private static volatile boolean stopRequested;
    private static volatile boolean previouslyHadServer;

    private PreviewClient() { }

    public static String assetsDirectory() {
        File assets = net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(
                Minecraft.class, Minecraft.getMinecraft(), "field_110446_Y");
        if (assets == null || !assets.isDirectory()) throw new IllegalStateException("Minecraft assets directory is unavailable");
        return assets.getAbsolutePath();
    }

    public static void initialize() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new PreviewClient());
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.gameSettings.pauseOnLostFocus = false;
        Display.setTitle("RML Preview");
    }

    public static void requestStop() {
        stopRequested = true;
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(minecraft::shutdown);
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (stopRequested) { minecraft.shutdown(); return; }
        if (!launched && minecraft.currentScreen instanceof GuiMainMenu) {
            launched = true;
            long seed;
            try { seed = Long.parseLong(System.getenv("RML_PREVIEW_SEED")); }
            catch (RuntimeException invalid) { throw new IllegalStateException("Invalid preview seed", invalid); }
            WorldSettings settings = new WorldSettings(seed, GameType.CREATIVE, true, false, WorldType.FLAT);
            settings.enableCommands();
            minecraft.launchIntegratedServer("preview", "preview", settings);
            Display.setTitle("RML Preview");
            return;
        }
        if (minecraft.getIntegratedServer() != null) previouslyHadServer = true;
        else if (previouslyHadServer) minecraft.shutdown();
    }
}
