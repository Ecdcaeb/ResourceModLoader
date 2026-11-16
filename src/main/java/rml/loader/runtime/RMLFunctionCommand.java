package rml.loader.runtime;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.ICommand;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import rml.runtime.ContentRuntime;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Operator commands for managed content and native Minecraft capability providers. */
public final class RMLFunctionCommand extends CommandBase {
    private final FunctionModule functions;
    private final Map<String, NativeCommand> commands = new TreeMap<>();

    public RMLFunctionCommand(LoaderRuntime runtime) {
        this.functions = runtime.getFunctions();
        RMLContentCommands content = new RMLContentCommands(runtime);
        register(content.getCommands(), content::execute);
        register(runtime.getFunctionEngine().getCommands(), runtime.getFunctionEngine()::execute);
        register(runtime.getGameplay().getCommands(), runtime.getGameplay()::execute);
        register(runtime.getWorldgen().getCommands(), runtime.getWorldgen()::execute);
        register(runtime.getPresentation().getCommands(), runtime.getPresentation()::execute);
        register(runtime.getTimeTests().getCommands(), runtime.getTimeTests()::execute);
        register(Collections.singleton("capabilities"), (server, sender, args) -> capabilities(runtime, sender, args));
    }

    @Override public String getName() { return "rml"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) {
        return "/rml <functions|" + String.join("|", commands.keySet()) + "> ...";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw new WrongUsageException(getUsage(sender));
        try {
            if (!"functions".equals(args[0])) {
                NativeCommand command = commands.get(args[0]);
                if (command == null) throw new WrongUsageException(getUsage(sender));
                command.execute(server, sender, args);
                return;
            }
            if (args.length < 2) throw new WrongUsageException(getUsage(sender));
            switch (args[1]) {
                case "status":
                    if (args.length != 2) throw new WrongUsageException(getUsage(sender));
                    List<ContentRuntime.PackSnapshot> active = functions.snapshot();
                    sender.sendMessage(new TextComponentString("RML functions: " + active.size() + " active packs"));
                    for (ContentRuntime.PackSnapshot pack : active) {
                        sender.sendMessage(new TextComponentString(pack.getId() + " " + pack.getVersion()
                                + " generation=" + pack.getGeneration() + " units=" + pack.getUnitIds().size()
                                + " resources=" + pack.getResourceCount()));
                    }
                    if (functions.getLastError() != null) {
                        sender.sendMessage(new TextComponentString("Last rejected change: " + functions.getLastError()));
                    }
                    break;
                case "reload":
                    if (args.length > 3) throw new WrongUsageException(getUsage(sender));
                    functions.reload(args.length == 3 ? args[2] : null);
                    sender.sendMessage(new TextComponentString("RML function candidate committed"));
                    break;
                case "unload":
                    if (args.length != 3) throw new WrongUsageException(getUsage(sender));
                    functions.deactivate(args[2]);
                    sender.sendMessage(new TextComponentString("RML functions deactivated: " + args[2]));
                    break;
                default:
                    throw new WrongUsageException(getUsage(sender));
            }
        } catch (RuntimeException e) {
            throw new CommandException("RML %s: %s", args[0], e.getMessage());
        }
    }

    private static void capabilities(LoaderRuntime runtime, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1) {
            java.util.SortedSet<String> types = new java.util.TreeSet<>(runtime.getContentTypes());
            types.add("function");
            types.add("function_tag");
            types.add("tag/<registry>");
            sender.sendMessage(new TextComponentString("RML content: " + String.join(", ", types)));
            return;
        }
        if (!"decoders".equals(args[1]) || args.length > 3) throw new WrongUsageException("/rml capabilities [decoders [Java-type]]");
        rml.deserializer.DeserializerManager manager = rml.loader.deserialize.Deserializer.MANAGER;
        Map<String, String> rows = new TreeMap<>();
        for (Class<?> type : manager.registry.keySet()) {
            if (args.length == 3 && !type.getName().equals(args[2])) continue;
            java.util.SortedSet<String> names = new java.util.TreeSet<>();
            for (net.minecraft.util.ResourceLocation name : manager.registry.get(type).keySet()) names.add(name.toString());
            rows.put(type.getName(), String.join(", ", names));
        }
        if (rows.isEmpty()) throw new CommandException("No registered decoder matches this Java type");
        for (Map.Entry<String, String> entry : rows.entrySet()) sender.sendMessage(new TextComponentString(entry.getKey() + ": " + entry.getValue()));
    }

    private void register(Set<String> names, NativeCommand handler) {
        for (String name : names) {
            if ("functions".equals(name) || commands.putIfAbsent(name, handler) != null) {
                throw new IllegalArgumentException("Duplicate RML command: " + name);
            }
        }
    }

    public List<ICommand> getNativeAliases(MinecraftServer server) {
        List<ICommand> aliases = new ArrayList<>();
        for (String name : commands.keySet()) {
            if (!server.getCommandManager().getCommands().containsKey(name)) aliases.add(new Alias(name));
        }
        return aliases;
    }

    @FunctionalInterface
    private interface NativeCommand {
        void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException;
    }

    private final class Alias extends CommandBase {
        private final String name;
        private Alias(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public int getRequiredPermissionLevel() { return 2; }
        @Override public String getUsage(ICommandSender sender) { return "/" + name + " ..."; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            String[] routed = new String[args.length + 1];
            routed[0] = name;
            System.arraycopy(args, 0, routed, 1, args.length);
            RMLFunctionCommand.this.execute(server, sender, routed);
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                         String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>(commands.keySet());
            names.add("functions");
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (!"functions".equals(args[0])) return Collections.emptyList();
        if (args.length == 2) return getListOfStringsMatchingLastWord(args, "status", "reload", "unload");
        if (args.length == 3 && ("reload".equals(args[1]) || "unload".equals(args[1]))) {
            List<String> ids = new ArrayList<>();
            for (ContentRuntime.PackSnapshot pack : functions.snapshot()) ids.add(pack.getId());
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return Collections.emptyList();
    }
}
