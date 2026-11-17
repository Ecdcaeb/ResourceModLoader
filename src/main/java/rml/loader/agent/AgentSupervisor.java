package rml.loader.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.Logger;
import rml.loader.runtime.LoaderRuntime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Owns the service lifetime. Network, process and filesystem waits never run in a Minecraft tick. */
public final class AgentSupervisor {
    private final LoaderRuntime runtime;
    private final Logger logger;
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32), task -> {
                Thread thread = new Thread(task, "RML Agent supervisor"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private volatile Binding binding;
    private final java.util.Set<Service> children = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private volatile boolean shuttingDown;

    public AgentSupervisor(LoaderRuntime runtime, Logger logger) {
        this.runtime = runtime;
        this.logger = logger;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            List<Service> remaining;
            synchronized (children) {
                shuttingDown = true;
                remaining = new ArrayList<>(children);
            }
            for (Service service : remaining) close(service);
        }, "RML Agent exit cleanup"));
    }

    /** Called once the world and runtime session exist, for both integrated and dedicated servers. */
    public void serverStarted(MinecraftServer server) {
        if (PreviewBootstrap.isActive()) {
            PreviewBootstrap.serverStarted(runtime, server);
            return;
        }
        Binding previous = binding;
        if (previous != null) { previous.closed = true; stop(previous); }
        Binding current = new Binding(server);
        binding = current;
        long generation = current.generation;
        worker.execute(() -> {
            try {
                AgentConfig config = AgentConfig.load(current.gameDirectory);
                if (config.enabled && !current.closed) start(current, "console", generation);
            } catch (Exception error) { fail(current, null, error, generation); }
        });
    }

    public void serverStopping() {
        if (PreviewBootstrap.isActive()) {
            PreviewBootstrap.serverStopping();
            return;
        }
        Binding current = binding;
        if (current != null) {
            current.closed = true;
            stop(current);
            binding = null;
        }
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (PreviewBootstrap.isActive()) throw new CommandException("Preview instances are controlled by their owning Agent service");
        if (args.length != 2) throw new WrongUsageException("/rml agent start|status|stop");
        String principal = principal(server, sender);
        Binding current = binding;
        if (current == null || current.server != server || current.closed) throw new CommandException("RML Agent world is not ready");
        switch (args[1]) {
            case "start":
                try {
                    long generation = current.generation;
                    worker.execute(() -> {
                        try { start(current, principal, generation); }
                        catch (Exception error) { fail(current, principal, error, generation); }
                    });
                } catch (java.util.concurrent.RejectedExecutionException error) {
                    throw new CommandException("RML Agent supervisor is busy");
                }
                sender.sendMessage(new TextComponentString("RML Agent start requested; the connection command will follow when ready."));
                break;
            case "status":
                sender.sendMessage(new TextComponentString(status(current, principal).toString()));
                break;
            case "stop":
                stop(current);
                sender.sendMessage(new TextComponentString("RML Agent service stopping."));
                break;
            default: throw new WrongUsageException("/rml agent start|status|stop");
        }
    }

    private void start(Binding current, String principal, long generation) throws Exception {
        if (shuttingDown || current.closed || current.generation != generation) return;
        Service service = current.service;
        boolean launched = false;
        if (service == null || service.closed || service.process == null || !service.process.isAlive()) {
            launched = true;
            List<Service> pending;
            synchronized (children) { pending = new ArrayList<>(children); }
            for (Service previous : pending) close(previous);
            if (service != null) close(service);
            AgentConfig config = AgentConfig.load(current.gameDirectory);
            Files.createDirectories(current.stateDirectory);
            Path identity = current.stateDirectory.resolve("instance-id");
            if (!Files.exists(identity)) AgentConfig.writePrivate(identity, UUID.randomUUID().toString());
            String instance = UUID.fromString(new String(Files.readAllBytes(identity), StandardCharsets.UTF_8).trim()).toString();
            service = new Service();
            synchronized (current) {
                if (current.closed || current.generation != generation) return;
                current.service = service;
                current.status = "starting";
                current.error = null;
            }
            try {
                service.bridge = new AgentBridge(runtime, current.server, current.stateDirectory.resolve("workspaces"),
                        current.gameDirectory.resolve("mods"), service.bridgeToken);
                Path ready = current.stateDirectory.resolve("ready-" + UUID.randomUUID() + ".json");
                service.readyFile = ready;
                Path previewProfile = current.stateDirectory.resolve("preview-profile.json");
                PreviewLaunchProfile.write(current.server, current.gameDirectory, previewProfile);
                List<String> command = new ArrayList<>(config.python);
                java.util.Collections.addAll(command, "-m", "rml_agent", "serve", "--config", config.file.toString(),
                        "--state-dir", current.stateDirectory.toString(), "--bridge", service.bridge.getEndpoint(),
                        "--instance", instance, "--ready-file", ready.toString());
                ProcessBuilder process = new ProcessBuilder(command).directory(current.gameDirectory.toFile());
                process.environment().put("RML_BRIDGE_TOKEN", service.bridgeToken);
                process.environment().put("RML_SERVICE_TOKEN", service.serviceToken);
                process.environment().put("RML_PREVIEW_PROFILE", previewProfile.toString());
                process.environment().put("PYTHONUNBUFFERED", "1");
                process.environment().put("RML_JAVA", current.gameDirectory.getFileSystem().getPath(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString());
                process.redirectErrorStream(true);
                process.redirectOutput(ProcessBuilder.Redirect.appendTo(current.stateDirectory.resolve("service.log").toFile()));
                synchronized (children) {
                    if (shuttingDown || current.closed || service.closed) return;
                    service.process = process.start();
                    children.add(service);
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
                while (!current.closed && !service.closed && System.nanoTime() < deadline) {
                    if (!service.process.isAlive()) throw new IOException("Agent process exited with code " + service.process.exitValue()
                            + "; see " + current.stateDirectory.resolve("service.log"));
                    if (Files.exists(ready)) {
                        JsonObject announcement = new JsonParser().parse(new String(Files.readAllBytes(ready), StandardCharsets.UTF_8)).getAsJsonObject();
                        if (!instance.equals(announcement.get("instance").getAsString())) throw new IOException("Agent ready identity does not match this world");
                        String endpoint = announcement.get("endpoint").getAsString();
                        JsonObject health = request(endpoint + "/health", null, null);
                        if (!instance.equals(health.get("instance").getAsString())) throw new IOException("Agent health identity does not match this world");
                        synchronized (current) {
                            if (current.closed || service.closed || current.generation != generation) return;
                            service.endpoint = endpoint;
                            current.status = "ready";
                        }
                        watch(current, service);
                        logger.info("RML Agent service ready at {}", endpoint);
                        break;
                    }
                    Thread.sleep(100);
                }
                if (current.closed || service.closed) { close(service); return; }
                if (service.endpoint == null) throw new IOException("Agent service did not become ready within 60 seconds; see " + current.stateDirectory.resolve("service.log"));
            } catch (Exception error) {
                close(service);
                throw error;
            }
        }
        if (current.closed || service.closed || current.generation != generation) return;
        if (launched && Files.isDirectory(current.stateDirectory.resolve("connections"))) {
            try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(current.stateDirectory.resolve("connections"), "*.json")) {
                for (Path file : files) {
                    String owner = file.getFileName().toString();
                    owner = owner.substring(0, owner.length() - 5);
                    if (owner.equals(principal)) continue;
                    if (!"console".equals(owner)) {
                        try { owner = UUID.fromString(owner).toString(); }
                        catch (IllegalArgumentException invalid) { continue; }
                    }
                    connect(current, service, owner, generation, false);
                }
            }
        }
        connect(current, service, principal, generation, true);
    }

    private void connect(Binding current, Service service, String principal, long generation, boolean notify) throws IOException {
        if (current.closed || service.closed || current.generation != generation) return;
        JsonObject body = new JsonObject(); body.addProperty("principal", principal);
        JsonObject session = request(service.endpoint + "/v1/sessions", service.serviceToken, body);
        if (current.closed || service.closed || current.generation != generation) return;
        JsonObject connection = new JsonObject();
        connection.addProperty("connect", service.endpoint);
        connection.addProperty("session", session.get("id").getAsString());
        connection.addProperty("token", session.get("token").getAsString());
        Path connectionFile = connectionFile(current, principal);
        AgentConfig.writePrivate(connectionFile, connection.toString());
        if (!notify) return;
        tell(current, principal, "RML Agent ready. Run: rml-agent tui --connection \""
                + connectionFile.toString().replace('\\', '/') + "\"");
        if (session.has("model_configured") && !session.get("model_configured").getAsBoolean())
            tell(current, principal, "Model is not configured. Set model.name and its credential environment in config/rml-agent.json, then restart the Agent service.");
    }

    private void watch(Binding current, Service service) {
        Thread monitor = new Thread(() -> {
            try {
                int code = service.process.waitFor();
                if (!service.closed && !current.closed && current.service == service) {
                    current.error = "Agent process exited with code " + code + "; see " + current.stateDirectory.resolve("service.log");
                    current.status = "failed";
                    close(service);
                    logger.warn(current.error);
                }
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }, "RML Agent process monitor");
        monitor.setDaemon(true); monitor.start();
    }

    private void stop(Binding current) {
        Service service;
        long generation;
        synchronized (current) {
            generation = ++current.generation;
            service = current.service;
            current.service = null;
            if (service == null) {
                if (!"stopping".equals(current.status)) current.status = "stopped";
                return;
            }
            current.status = "stopping";
        }
        service.closed = true;
        if (service.bridge != null) service.bridge.close();
        Runnable cleanup = () -> {
            close(service);
            synchronized (current) {
                if (current.service == null && current.generation == generation) current.status = "stopped";
            }
        };
        try { worker.execute(cleanup); }
        catch (java.util.concurrent.RejectedExecutionException busy) {
            Thread shutdown = new Thread(cleanup, "RML Agent shutdown");
            shutdown.setDaemon(true); shutdown.start();
        }
    }

    private void close(Service service) {
        service.closed = true;
        if (service.bridge != null) service.bridge.close();
        Process process = service.process;
        if (process != null && process.isAlive()) {
            if (service.endpoint != null) {
                try { request(service.endpoint + "/v1/shutdown", service.serviceToken, new JsonObject()); }
                catch (IOException ignored) { /* An exited/unresponsive child is reclaimed below. */ }
            } else process.destroy();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroy();
                    if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException error) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
        }
        if (process == null || !process.isAlive()) children.remove(service);
        if (service.readyFile != null) {
            try { Files.deleteIfExists(service.readyFile); }
            catch (IOException ignored) { /* Ready paths are unique to a launch and never reused. */ }
        }
    }

    private void fail(Binding current, String principal, Exception error, long generation) {
        if (current.closed || current.generation != generation) return;
        current.status = "failed";
        current.error = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        logger.error("RML Agent: {}", current.error);
        if (principal != null) tell(current, principal, "RML Agent: " + current.error);
    }

    private static void tell(Binding current, String principal, String message) {
        if (current.closed) return;
        current.server.addScheduledTask(() -> {
            if (current.closed) return;
            if ("console".equals(principal)) current.server.sendMessage(new TextComponentString(message));
            else {
                EntityPlayerMP player = current.server.getPlayerList().getPlayerByUUID(UUID.fromString(principal));
                if (player != null && player.canUseCommand(2, "rml")) player.sendMessage(new TextComponentString(message));
            }
        });
    }

    private static String principal(MinecraftServer server, ICommandSender sender) throws CommandException {
        if (!sender.canUseCommand(2, "rml")) throw new CommandException("Operator permission is required");
        if (sender instanceof EntityPlayerMP) return ((EntityPlayerMP) sender).getUniqueID().toString();
        if (sender == server) return "console";
        throw new CommandException("Agent sessions require a player or the server console");
    }

    private static Path connectionFile(Binding binding, String principal) {
        return binding.stateDirectory.resolve("connections").resolve(principal + ".json");
    }

    private static JsonObject status(Binding current, String principal) {
        JsonObject result = new JsonObject();
        result.addProperty("status", current.status);
        result.addProperty("error", current.error);
        Service service = current.service;
        result.addProperty("endpoint", service == null ? null : service.endpoint);
        result.addProperty("connection_file", connectionFile(current, principal).toString());
        return result;
    }

    private static JsonObject request(String endpoint, String token, JsonObject body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(2000); connection.setReadTimeout(5000);
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Agent service HTTP " + status + " at " + connection.getURL().getPath());
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048]; int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (bytes.size() + count > 65536) throw new IOException("Agent service response is too large");
                    bytes.write(buffer, 0, count);
                }
                return new JsonParser().parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } finally { connection.disconnect(); }
    }

    private static String token() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class Binding {
        final MinecraftServer server;
        final Path gameDirectory, stateDirectory;
        volatile Service service;
        volatile boolean closed;
        volatile long generation;
        volatile String status = "stopped", error;
        Binding(MinecraftServer server) {
            this.server = server;
            gameDirectory = server.getFile(".").toPath().toAbsolutePath().normalize();
            stateDirectory = server.getWorld(0).getSaveHandler().getWorldDirectory().toPath().toAbsolutePath().normalize().resolve("rml-agent");
        }
    }

    private static final class Service {
        final String bridgeToken = token(), serviceToken = token();
        volatile AgentBridge bridge;
        volatile Process process;
        volatile Path readyFile;
        volatile String endpoint;
        volatile boolean closed;
    }
}
