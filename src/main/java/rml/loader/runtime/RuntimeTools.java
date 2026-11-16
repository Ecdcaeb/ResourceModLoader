package rml.loader.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import rml.loader.api.mods.ContainerHolder;
import rml.runtime.ContentException;
import rml.runtime.ContentRuntime;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Structured operations shared by operator commands and in-process Agent integrations. */
public final class RuntimeTools {
    private final LoaderRuntime runtime;

    RuntimeTools(LoaderRuntime runtime) { this.runtime = runtime; }

    /** Call on the server thread. A failed response includes the actual post-operation state. */
    public JsonObject execute(MinecraftServer server, String... args) {
        String operation = args.length == 0 ? "" : args[0];
        JsonObject response = new JsonObject();
        response.addProperty("protocol", 1);
        response.addProperty("operation", operation);
        response.addProperty("scope", "runtime_content");
        JsonArray diagnostics = new JsonArray();
        boolean serverThread = server != null && server.isCallingFromMinecraftThread();
        try {
            if (!serverThread) throw failure("wrong_thread", "Runtime tools require the owning server thread");
            JsonObject result;
            switch (operation) {
                case "inspect":
                    require(args.length == 1, "inspect");
                    result = inspect();
                    break;
                case "check":
                    require(args.length == 1 || args.length == 2, "check [pack]");
                    result = checked(runtime.getFunctions().check(args.length == 2 ? args[1] : null));
                    break;
                case "apply":
                    require(args.length == 3, "apply <candidate> <revision>");
                    runtime.getFunctions().apply(args[1], revision(args[2]));
                    result = new JsonObject();
                    result.addProperty("candidate", args[1]);
                    break;
                case "deactivate":
                    require(args.length >= 4, "deactivate <session> <revision> <pack> [pack...]");
                    List<String> packs = Arrays.asList(Arrays.copyOfRange(args, 3, args.length));
                    runtime.getFunctions().deactivate(args[1], revision(args[2]), packs);
                    result = new JsonObject();
                    result.add("packs", strings(packs));
                    break;
                default:
                    throw failure("invalid_request", "Expected inspect, check, apply or deactivate");
            }
            response.addProperty("ok", true);
            response.add("result", result);
        } catch (RuntimeException error) {
            response.addProperty("ok", false);
            response.add("result", JsonNull.INSTANCE);
            JsonObject diagnostic = new JsonObject();
            String code = "check".equals(operation) ? "check_failed" : "operation_failed";
            if (error instanceof ContentException) {
                ContentException content = (ContentException) error;
                code = content.getCode();
                diagnostic.addProperty("pack", content.getPackId());
                diagnostic.addProperty("unit", content.getUnitId());
            }
            diagnostic.addProperty("code", code);
            diagnostic.addProperty("message", error.getMessage());
            JsonArray causes = new JsonArray();
            for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
                causes.add(cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage());
            }
            diagnostic.add("causes", causes);
            diagnostics.add(diagnostic);
        }
        response.add("diagnostics", diagnostics);
        response.add("state", serverThread ? state() : JsonNull.INSTANCE);
        return response;
    }

    private JsonObject inspect() {
        JsonObject result = new JsonObject();
        result.addProperty("minecraft", "1.12.2");
        result.addProperty("rml", rml.loader.ResourceModLoader.VERSION);
        JsonArray capabilities = new JsonArray();
        TreeSet<String> types = new TreeSet<>(runtime.getContentTypes());
        types.add("function");
        types.add("function_tag");
        types.add("tag/<registry>");
        for (String type : types) {
            JsonObject capability = new JsonObject();
            capability.addProperty("type", type);
            capability.addProperty("lifecycle", runtime.isStartupContent(type) ? "startup" : "reloadable");
            capabilities.add(capability);
        }
        result.add("capabilities", capabilities);
        JsonArray installed = new JsonArray();
        for (ContainerHolder pack : runtime.getPacks().all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", pack.getContainer().getModId());
            entry.addProperty("version", pack.getContainer().getVersion());
            installed.add(entry);
        }
        result.add("installed_packs", installed);
        TreeSet<String> scenarios = new TreeSet<>();
        for (net.minecraft.util.ResourceLocation id : runtime.getContent().snapshot().resources("test_instance").keySet()) scenarios.add(id.toString());
        result.add("scenarios", strings(scenarios));
        return result;
    }

    private JsonObject state() {
        FunctionModule functions = runtime.getFunctions();
        JsonObject state = new JsonObject();
        String session = functions.getSessionId();
        state.addProperty("ready", session != null);
        state.addProperty("session", session);
        if (session == null) state.add("revision", JsonNull.INSTANCE);
        else state.addProperty("revision", functions.getRevision());
        JsonArray packs = new JsonArray();
        for (ContentRuntime.PackSnapshot pack : functions.snapshot()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", pack.getId());
            entry.addProperty("version", pack.getVersion());
            entry.addProperty("generation", pack.getGeneration());
            entry.addProperty("resources", pack.getResourceCount());
            entry.add("units", strings(pack.getUnitIds()));
            packs.add(entry);
        }
        state.add("packs", packs);
        FunctionModule.CheckResult candidate = functions.getCheckedCandidate();
        state.addProperty("candidate", candidate == null ? null : candidate.getCandidateId());
        return state;
    }

    private static JsonObject checked(FunctionModule.CheckResult checked) {
        JsonObject result = new JsonObject();
        result.addProperty("candidate", checked.getCandidateId());
        result.addProperty("expected_revision", checked.getRevision());
        JsonObject affected = new JsonObject();
        for (Map.Entry<String, List<String>> pack : checked.getUnits().entrySet()) affected.add(pack.getKey(), strings(pack.getValue()));
        result.add("affected", affected);
        return result;
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }

    private static long revision(String text) {
        try {
            long value = Long.parseLong(text);
            if (value >= 0) return value;
        } catch (NumberFormatException ignored) { }
        throw failure("invalid_request", "Revision must be a nonnegative integer");
    }

    private static void require(boolean condition, String usage) {
        if (!condition) throw failure("invalid_request", "Usage: /rml tools " + usage);
    }

    private static ContentException failure(String code, String message) {
        return new ContentException(code, null, null, message);
    }
}
