package rml.loader.agent;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import rml.loader.runtime.LoaderRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** Child-JVM lifecycle for an isolated preview instance. */
public final class PreviewBootstrap {
    private static final Object LOCK = new Object();
    private static volatile AgentBridge bridge;
    private static volatile MinecraftServer server;
    private static volatile String previewId;
    private static volatile Path readyFile;
    private static volatile boolean clientHookStarted;

    private PreviewBootstrap() { }

    public static boolean isActive() {
        return System.getenv("RML_PREVIEW_ID") != null;
    }

    /** Called after RML postInit; client code is loaded reflectively only in a client process. */
    public static void initialize() {
        if (!isActive()) return;
        synchronized (LOCK) {
            if (clientHookStarted) return;
            String id = System.getenv("RML_PREVIEW_ID");
            if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid preview identity");
            Long.parseLong(System.getenv("RML_PREVIEW_SEED"));
            if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
                try {
                    Class.forName("rml.loader.agent.PreviewClient", true, PreviewBootstrap.class.getClassLoader())
                            .getMethod("initialize").invoke(null);
                    clientHookStarted = true;
                } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot initialize preview lifecycle", error); }
            }
        }
    }

    /** Called after the native server and world have started. */
    public static void serverStarted(LoaderRuntime runtime, MinecraftServer minecraftServer) {
        if (!isActive()) return;
        if (runtime == null || minecraftServer == null) throw new IllegalArgumentException("Preview runtime and server are required");
        synchronized (LOCK) {
            if (bridge != null) return;
            try {
                previewId = UUID.fromString(System.getenv("RML_PREVIEW_ID")).toString();
                String token = System.getenv("RML_PREVIEW_TOKEN");
                if (token == null || token.isEmpty()) throw new IllegalArgumentException("Missing preview token");
                Path cwd = Paths.get("").toAbsolutePath().normalize();
                String readyEnv = System.getenv("RML_PREVIEW_READY");
                if (readyEnv == null || readyEnv.isEmpty()) throw new IllegalArgumentException("Missing preview readiness path");
                Path ready = Paths.get(readyEnv).toAbsolutePath().normalize();
                if (!ready.startsWith(cwd) || ready.equals(cwd)) throw new IllegalArgumentException("Preview readiness path must be inside the child directory");
                long seed = Long.parseLong(System.getenv("RML_PREVIEW_SEED"));
                Path workspaces = cwd.resolve("native-workspaces");
                Path mods = cwd.resolve("mods");
                AgentBridge next = new AgentBridge(runtime, minecraftServer, workspaces, mods, token);
                JsonObject data = new JsonObject();
                data.addProperty("version", 1);
                data.addProperty("preview_id", previewId);
                data.addProperty("mode", minecraftServer.isDedicatedServer() ? "server" : "client");
                data.addProperty("endpoint", next.getEndpoint());
                data.addProperty("world", minecraftServer.getEntityWorld().getSaveHandler().getWorldDirectory().getAbsolutePath());
                if (minecraftServer.isDedicatedServer()) data.addProperty("port", serverPort(minecraftServer));
                try { AgentConfig.writePrivate(ready, new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(data)); }
                catch (IOException error) { next.close(); throw error; }
                bridge = next;
                server = minecraftServer;
                readyFile = ready;
            } catch (IOException error) { throw new IllegalStateException("Cannot publish preview readiness", error); }
        }
    }

    private static int serverPort(MinecraftServer minecraftServer) {
        return minecraftServer.getServerPort();
    }

    public static void serverStopping() {
        synchronized (LOCK) {
            AgentBridge current = bridge; bridge = null; server = null;
            if (current != null) current.close();
            Path ready = readyFile; readyFile = null;
            if (ready != null) try { Files.deleteIfExists(ready); } catch (IOException ignored) { }
        }
        if (clientHookStarted) PreviewClient.requestStop();
    }

    public static void requestStop() {
        MinecraftServer current = server;
        if (current != null) {
            if (current.isDedicatedServer()) current.initiateShutdown();
            else if (clientHookStarted) PreviewClient.requestStop();
        } else if (clientHookStarted) PreviewClient.requestStop();
    }
}
