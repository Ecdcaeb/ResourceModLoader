package rml.loader.agent;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import rml.loader.ResourceModLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

/** Builds the private, credential-free launcher description used by preview.py. */
public final class PreviewLaunchProfile {
    private PreviewLaunchProfile() { }

    public static void write(MinecraftServer server, Path gameDirectory, Path destination) throws IOException {
        if (server == null || gameDirectory == null || destination == null) throw new IllegalArgumentException("Preview profile arguments are required");
        Path game = gameDirectory.toAbsolutePath().normalize();
        ModContainer container = Loader.instance().getIndexedModList().get(ResourceModLoader.MODID);
        if (container == null || container.getSource() == null) throw new IOException("RML mod source is unavailable");
        Path own = container.getSource().toPath().toAbsolutePath().normalize();
        boolean client = !server.isDedicatedServer();
        String launcher = System.getProperty("sun.java.command", "").split(" ", 2)[0];
        String main = "GradleStart".equals(launcher) || "GradleStartServer".equals(launcher)
                ? launcher : "net.minecraft.launchwrapper.Launch";
        Set<String> classpath = new LinkedHashSet<>();
        addClasspath(classpath, System.getProperty("java.class.path", ""), game.resolve("mods"), own);
        try {
            Object urls = Launch.classLoader.getClass().getMethod("getURLs").invoke(Launch.classLoader);
            for (java.net.URL url : (java.net.URL[]) urls) {
                if ("file".equalsIgnoreCase(url.getProtocol())) addPath(classpath, Paths.get(url.toURI()), game.resolve("mods"), own);
            }
        } catch (Exception error) { throw new IOException("Cannot enumerate Launch classloader classpath", error); }
        classpath.add(own.toString());

        JsonObject profile = new JsonObject();
        profile.addProperty("version", 1);
        profile.addProperty("mode", client ? "client" : "server");
        profile.addProperty("game_directory", game.toString());
        profile.addProperty("java", Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toAbsolutePath().toString());
        JsonArray cp = new JsonArray(); for (String path : classpath) cp.add(path); profile.add("classpath", cp);
        JsonArray shared = new JsonArray(); shared.add(own.toString()); profile.add("shared_mods", shared);
        JsonArray jvm = new JsonArray();
        jvm.add("-Dfile.encoding=UTF-8");
        for (String name : new String[]{"java.library.path", "org.lwjgl.librarypath", "net.java.games.input.librarypath"}) {
            String value = System.getProperty(name);
            if (value != null && !value.isEmpty()) jvm.add("-D" + name + "=" + value);
        }
        Set<String> plugins = new LinkedHashSet<>();
        for (String plugin : System.getProperty("fml.coreMods.load", "").split(",")) if (!plugin.isEmpty()) plugins.add(plugin);
        plugins.add("rml.loader.core.RMLFMLLoadingPlugin");
        jvm.add("-Dfml.coreMods.load=" + String.join(",", plugins));
        profile.add("jvm_args", jvm);
        profile.addProperty("main_class", main);
        JsonArray args = new JsonArray();
        if (client) {
            add(args, "--username"); add(args, "RMLPreview"); add(args, "--accessToken"); add(args, "0");
            add(args, "--version"); add(args, "1.12.2"); add(args, "--assetsDir");
            add(args, assetsDirectory()); add(args, "--assetIndex"); add(args, "1.12");
            add(args, "--gameDir"); add(args, "{preview}");
            if ("net.minecraft.launchwrapper.Launch".equals(main)) { add(args, "--tweakClass"); add(args, "net.minecraftforge.fml.common.launcher.FMLTweaker"); }
        } else {
            add(args, "nogui");
            if ("net.minecraft.launchwrapper.Launch".equals(main)) { add(args, "--tweakClass"); add(args, "net.minecraftforge.fml.common.launcher.FMLServerTweaker"); }
        }
        profile.add("arguments", args);
        AgentConfig.writePrivate(destination.toAbsolutePath().normalize(), new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(profile));
    }

    private static String assetsDirectory() throws IOException {
        try {
            return (String) Class.forName("rml.loader.agent.PreviewClient", true, Launch.classLoader)
                    .getMethod("assetsDirectory").invoke(null);
        } catch (ReflectiveOperationException error) { throw new IOException("Cannot locate Minecraft assets directory", error); }
    }

    private static void addClasspath(Set<String> paths, String value, Path mods, Path own) {
        for (String item : value.split(java.util.regex.Pattern.quote(File.pathSeparator))) if (!item.isEmpty()) addPath(paths, Paths.get(item), mods, own);
    }
    private static void addPath(Set<String> paths, Path value, Path mods, Path own) {
        Path path = value.toAbsolutePath().normalize();
        if (!path.startsWith(mods.toAbsolutePath().normalize()) || path.equals(own)) paths.add(path.toString());
    }
    private static void add(JsonArray array, String value) { array.add(value); }
    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"); }
}
