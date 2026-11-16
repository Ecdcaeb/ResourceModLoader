package rml.loader.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import rml.loader.runtime.FunctionModule;
import rml.loader.runtime.LoaderRuntime;
import rml.runtime.ContentException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Authenticated loopback adapter. Native operations are authorized again on the server thread. */
public final class AgentBridge implements AutoCloseable {
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private final LoaderRuntime runtime;
    private final MinecraftServer server;
    private final BridgeArchives archives;
    private final byte[] authorization;
    private final HttpServer listener;
    private final ThreadPoolExecutor httpWorkers = pool("rml-bridge-http", 8, 32);
    private final ThreadPoolExecutor ioWorkers = pool("rml-bridge-io", 4, 16);
    private final Semaphore waiting = new Semaphore(4);
    private final BridgeJournal journal;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile JsonElement lastState = initialState();
    private String candidate;
    private String candidatePrincipal;
    private String candidateSession;

    public AgentBridge(LoaderRuntime runtime, MinecraftServer server, Path workspaceRoot, Path modsDirectory, String token) throws IOException {
        if (runtime == null || server == null || token == null || token.isEmpty()) throw new IllegalArgumentException("Bridge requires runtime, server and token");
        this.runtime = runtime;
        this.server = server;
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        this.archives = new BridgeArchives(workspaceRoot, modsDirectory);
        this.journal = new BridgeJournal(256, workspaceRoot.resolve(".bridge-journal"));
        this.listener = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 16);
        listener.createContext("/invoke", this::invoke);
        listener.createContext("/cancel", this::cancel);
        listener.setExecutor(httpWorkers);
        listener.start();
    }

    public String getEndpoint() { return "http://127.0.0.1:" + listener.getAddress().getPort(); }

    private void invoke(HttpExchange exchange) throws IOException {
        boolean acquired = false;
        try {
            if (!authenticate(exchange, "/invoke")) return;
            if (!(acquired = waiting.tryAcquire())) {
                send(exchange, 503, failure("bridge_busy", "Bridge request capacity reached"));
                return;
            }
            JsonObject payload = body(exchange);
            String id = BridgeArchives.uuid(string(payload, "request_id"));
            String principal = principal(payload);
            String session = BridgeArchives.uuid(string(payload, "session_id"));
            String operation = string(payload, "operation");
            JsonObject arguments = payload.getAsJsonObject("arguments");
            if (arguments == null) throw new IllegalArgumentException("Missing arguments object");
            validate(operation, arguments);
            BridgeJournal.Entry entry = journal.submit(id, principal, session, payload);
            if (entry.dispatch()) {
                try {
                    ioWorkers.execute(() -> prepare(entry, operation, arguments));
                } catch (RuntimeException rejected) {
                    entry.complete(bound(failure("bridge_busy", "Bridge worker queue is unavailable")));
                }
            }
            try {
                send(exchange, 200, entry.response.get(30, TimeUnit.SECONDS));
            } catch (TimeoutException timeout) {
                JsonObject response = failure("outcome_pending", "Request is still " + entry.status() + "; do not issue a new request id for this mutation");
                JsonObject result = new JsonObject();
                result.addProperty("request_id", id);
                result.addProperty("status", entry.status());
                response.add("result", result);
                send(exchange, 202, response);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            send(exchange, 503, failure("bridge_closed", "Bridge worker stopped; a running operation may already have committed"));
        } catch (ExecutionException error) {
            send(exchange, 500, failure("operation_failed", "Bridge operation could not complete"));
        } catch (RuntimeException | IOException error) {
            send(exchange, 400, failure(errorCode(error), error.getMessage()));
        } finally {
            if (acquired) waiting.release();
            exchange.close();
        }
    }

    private void cancel(HttpExchange exchange) throws IOException {
        try {
            if (!authenticate(exchange, "/cancel")) return;
            JsonObject payload = body(exchange);
            String id = BridgeArchives.uuid(string(payload, "request_id"));
            BridgeJournal.Entry entry = journal.find(id, principal(payload), BridgeArchives.uuid(string(payload, "session_id")));
            String status = entry.cancel(bound(failure("cancelled", "Queued request cancelled before execution")));
            JsonObject result = new JsonObject();
            result.addProperty("request_id", id);
            result.addProperty("status", status);
            result.addProperty("cancelled", "cancelled".equals(status));
            if (entry.response.isDone()) result.add("response", entry.response.getNow(null));
            send(exchange, 200, success(result));
        } catch (RuntimeException | IOException error) {
            send(exchange, 400, failure(errorCode(error), error.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private boolean authenticate(HttpExchange exchange, String route) throws IOException {
        List<String> headers = exchange.getRequestHeaders().get("Authorization");
        if (headers == null || headers.size() != 1 || !MessageDigest.isEqual(authorization, headers.get(0).getBytes(StandardCharsets.UTF_8))) {
            send(exchange, 401, failure("unauthorized", "Bridge authentication required"));
            return false;
        }
        if (!"POST".equals(exchange.getRequestMethod()) || !route.equals(exchange.getRequestURI().getPath())
                || exchange.getRequestURI().getRawQuery() != null) {
            send(exchange, 400, failure("invalid_request", "Expected POST " + route));
            return false;
        }
        return true;
    }

    private static JsonObject body(HttpExchange exchange) throws IOException {
        byte[] bytes = BridgeArchives.bounded(exchange.getRequestBody(), 64 * 1024);
        JsonElement parsed = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Request must be an object");
        return parsed.getAsJsonObject();
    }

    private void prepare(BridgeJournal.Entry entry, String operation, JsonObject arguments) {
        try {
            if (!"queued".equals(entry.status())) return;
            BridgeArchives.Build build = ("check".equals(operation) || "install".equals(operation))
                    ? archives.read(entry.session, string(arguments, "archive"), string(arguments, "sha256")) : null;
            if (!"queued".equals(entry.status())) return;
            server.addScheduledTask(() -> execute(entry, operation, arguments, build));
        } catch (Exception error) {
            entry.complete(bound(failure(errorCode(error), error.getMessage())));
        }
    }

    private void execute(BridgeJournal.Entry entry, String operation, JsonObject arguments, BridgeArchives.Build build) {
        if (!entry.begin()) return;
        try {
            ICommandSender sender = authorize(entry.principal);
            JsonObject result = new JsonObject();
            switch (operation) {
                case "inspect":
                    JsonObject inspected = runtime.getTools().execute(server, "inspect");
                    lastState = inspected.get("state");
                    entry.complete(bound(inspected));
                    return;
                case "check":
                    FunctionModule.CheckResult checked = runtime.getFunctions().checkCompiled(build.pack);
                    candidate = checked.getCandidateId();
                    candidatePrincipal = entry.principal;
                    candidateSession = entry.session;
                    result.addProperty("candidate", candidate);
                    result.addProperty("expected_revision", checked.getRevision());
                    JsonObject affected = new JsonObject();
                    for (Map.Entry<String, List<String>> pack : checked.getUnits().entrySet()) {
                        JsonArray units = new JsonArray();
                        for (String unit : pack.getValue()) units.add(unit);
                        affected.add(pack.getKey(), units);
                    }
                    result.add("affected", affected);
                    break;
                case "apply":
                    String wanted = string(arguments, "candidate");
                    if (!wanted.equals(candidate) || !entry.principal.equals(candidatePrincipal) || !entry.session.equals(candidateSession))
                        throw new ContentException("candidate_owner_mismatch", null, null, "Candidate was not checked by this principal and session");
                    runtime.getFunctions().apply(wanted, revision(arguments));
                    result.addProperty("candidate", wanted);
                    candidate = null;
                    break;
                case "deactivate":
                    runtime.getFunctions().deactivate(string(arguments, "session"), revision(arguments), Collections.singleton(BridgeArchives.packId(entry.session)));
                    result.addProperty("pack", BridgeArchives.packId(entry.session));
                    break;
                case "command":
                    try (CommandCapture capture = new CommandCapture(sender)) {
                        int affectedCount = server.getCommandManager().executeCommand(sender, string(arguments, "command"));
                        result.addProperty("result", affectedCount);
                        result.add("messages", capture.messages());
                        result.addProperty("messages_truncated", capture.truncated());
                    }
                    break;
                case "install":
                    refreshState();
                    ioWorkers.execute(() -> install(entry, build));
                    return;
                default:
                    throw new IllegalArgumentException("Unknown operation");
            }
            refreshState();
            entry.complete(bound(success(result)));
        } catch (Exception error) {
            refreshState();
            entry.complete(bound(failure(errorCode(error), error.getMessage())));
        }
    }

    private ICommandSender authorize(String principal) {
        if (!server.isCallingFromMinecraftThread()) throw new IllegalStateException("Native operations require the server thread");
        if (closed.get()) throw new IllegalStateException("bridge_closed");
        if ("console".equals(principal)) return server;
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(UUID.fromString(principal));
        if (player == null || !player.canUseCommand(2, "rml"))
            throw new ContentException("permission_denied", null, null, "Principal must be an online operator at execution time");
        return player;
    }

    private void install(BridgeJournal.Entry entry, BridgeArchives.Build build) {
        try {
            String filename = archives.install(entry.session, build);
            JsonObject result = new JsonObject();
            result.addProperty("archive", filename);
            result.addProperty("sha256", build.digest);
            result.addProperty("requires_restart", true);
            result.addProperty("status", "requires_restart");
            JsonObject response = success(result);
            JsonArray diagnostics = new JsonArray();
            diagnostics.add(diagnostic("requires_restart", "Compiled archive installed for the next JVM launch"));
            response.add("diagnostics", diagnostics);
            entry.complete(bound(response));
        } catch (Exception error) {
            entry.complete(bound(failure("install_failed", error.getMessage())));
        }
    }

    private void refreshState() {
        lastState = runtime.getTools().execute(server, "inspect").get("state");
    }

    private JsonObject success(JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", true);
        response.add("result", result);
        response.add("diagnostics", new JsonArray());
        response.add("state", lastState);
        return response;
    }

    private JsonObject failure(String code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.add("result", JsonNull.INSTANCE);
        JsonArray diagnostics = new JsonArray();
        diagnostics.add(diagnostic(code, message));
        response.add("diagnostics", diagnostics);
        response.add("state", lastState);
        return response;
    }

    private JsonObject bound(JsonObject response) {
        if (response.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_RESPONSE_BYTES) return response;
        JsonObject bounded = failure("response_too_large", "Operation completed, but its detailed response exceeded the bridge limit; inspect current state");
        JsonObject result = new JsonObject();
        result.addProperty("operation_ok", response.get("ok").getAsBoolean());
        bounded.add("result", result);
        JsonObject state = new JsonObject();
        if (lastState.isJsonObject()) {
            state.add("session", lastState.getAsJsonObject().get("session"));
            state.add("revision", lastState.getAsJsonObject().get("revision"));
        }
        bounded.add("state", state);
        return bounded;
    }

    private static JsonObject initialState() {
        JsonObject state = new JsonObject();
        state.addProperty("ready", false);
        state.add("session", JsonNull.INSTANCE);
        state.add("revision", JsonNull.INSTANCE);
        return state;
    }

    private static JsonObject diagnostic(String code, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("code", code);
        String text = message == null ? code : message;
        result.addProperty("message", text.length() > 4096 ? text.substring(0, 4096) : text);
        return result;
    }

    private static String errorCode(Exception error) {
        if (error instanceof ContentException) return ((ContentException) error).getCode();
        if (error instanceof BridgeJournal.JournalException) return "journal_io_error";
        String message = error.getMessage();
        if (message != null && message.matches("[a-z_]+")) return message;
        return error instanceof IOException ? "invalid_archive" : "invalid_request";
    }

    private static void validate(String operation, JsonObject arguments) {
        switch (operation) {
            case "inspect": break;
            case "check": case "install":
                string(arguments, "archive");
                string(arguments, "sha256");
                break;
            case "apply":
                BridgeArchives.uuid(string(arguments, "candidate"));
                revision(arguments);
                break;
            case "deactivate":
                BridgeArchives.uuid(string(arguments, "session"));
                revision(arguments);
                break;
            case "command":
                String command = string(arguments, "command");
                if (command.trim().isEmpty() || command.length() > 8192 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0)
                    throw new IllegalArgumentException("Invalid command");
                break;
            default: throw new IllegalArgumentException("Unknown operation");
        }
    }

    private static String principal(JsonObject object) {
        String principal = string(object, "principal");
        return "console".equals(principal) ? principal : BridgeArchives.uuid(principal);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string: " + key);
        return value.getAsString();
    }

    private static long revision(JsonObject arguments) {
        JsonElement value = arguments.get("revision");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Expected nonnegative revision");
        return Long.parseLong(value.getAsString());
    }

    private void send(HttpExchange exchange, int status, JsonObject response) throws IOException {
        byte[] bytes = bound(response).toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static ThreadPoolExecutor pool(String name, int count, int queued) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, name + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(count, count, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queued), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        journal.close(bound(failure("bridge_closed", "Queued request cancelled because the server bridge closed")));
        listener.stop(0);
        httpWorkers.shutdownNow();
        ioWorkers.shutdown();
    }

}
