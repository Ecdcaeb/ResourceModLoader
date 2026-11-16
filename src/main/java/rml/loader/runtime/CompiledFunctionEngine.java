package rml.loader.runtime;

import com.google.gson.JsonObject;
import java.util.function.Function;
import net.minecraft.advancements.FunctionManager;
import net.minecraft.command.CommandException;
import net.minecraft.command.FunctionObject;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import rml.loader.runtime.function.FunctionDataPath;
import rml.loader.runtime.function.FunctionStorage;
import rml.loader.runtime.function.FunctionCommandContext;
import rml.loader.runtime.function.FunctionReturnFrame;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;
import rml.runtime.ResourceScope;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Random;

/** Executes compiled enhanced commands through native FunctionObject entries. */
public final class CompiledFunctionEngine {
    private final DataRules rules;
    private final Set<String> commands;
    private final Function<ResourceLocation, Integer> dimensionResolver;


    public CompiledFunctionEngine(DataRules rules, Function<ResourceLocation, Integer> dimensionResolver) {
        this.rules = rules;
        this.dimensionResolver = dimensionResolver == null ? id -> null : dimensionResolver;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Collections.addAll(names, "execute", "data", "return", "function");
        this.commands = Collections.unmodifiableSet(names);
    }

    public FunctionObject compile(FunctionManager manager, ResourceLocation id, List<String> lines) {
        boolean hasReturn = false;
        for (String line : lines) { String trimmed = line.trim(); if ("return".equals(trimmed) || trimmed.startsWith("return ")) { hasReturn = true; break; } }
        Program program = new Program(this, null, hasReturn);
        List<FunctionObject.Entry> entries = new ArrayList<>(lines.size() + (hasReturn ? 1 : 0));
        for (String line : lines) {
            String command = line.trim();
            if (command.isEmpty() || command.startsWith("#")) continue;
            String enhanced = stripRml(command);
            if (command.startsWith("$")) entries.add(new MacroEntry(program, command.substring(1), null));
            else if (isEnhanced(enhanced)) entries.add(new CompiledEntry(program, enhanced, compilePredicate(enhanced)));
            else entries.add(new ScopedEntry(program, new FunctionObject.CommandEntry(command)));
        }
        if (program.end != null) entries.add(program.end);
        return new FunctionObject(entries.toArray(new FunctionObject.Entry[0]));
    }

    public void bind(FunctionObject function, ResourceScope scope) {
        if (function == null || scope == null) throw new NullPointerException();
        for (FunctionObject.Entry entry : function.getEntries()) {
            Program program = entry instanceof CompiledEntry ? ((CompiledEntry) entry).program
                    : entry instanceof FrameEndEntry ? ((FrameEndEntry) entry).program
                    : entry instanceof ScopedEntry ? ((ScopedEntry) entry).program
                    : entry instanceof MacroEntry ? ((MacroEntry) entry).program : null;
            if (program != null) program.scope = scope;
        }
    }

    public int invoke(FunctionManager manager, FunctionObject function, ICommandSender sender) {
        FunctionReturnFrame frame = new FunctionReturnFrame();
        int nativeResult = manager.execute(function, FunctionCommandContext.withReturnFrame(sender, frame));
        return frame.hasReturned() ? frame.getResult() : nativeResult;
    }

    public int execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw new CommandException("Missing function command");
        if ("function".equals(args[0])) {
            if (args.length < 2) throw new CommandException("Expected function <id>");
            FunctionObject function = server.getFunctionManager().getFunction(new ResourceLocation(args[1]));
            if (function == null) throw new CommandException("Unknown function " + args[1]);
            if (args.length == 4 && ("if".equals(args[2]) || "unless".equals(args[2]))) {
                boolean matches = !net.minecraft.command.EntitySelector.matchEntities(sender, args[3], Entity.class).isEmpty();
                if (matches == "unless".equals(args[2])) return 0;
            }
            ICommandSender executionSender = sender;
            if (args.length == 6 && "with".equals(args[2]) && "storage".equals(args[3])) {
                World world = sender.getEntityWorld();
                if (!(world instanceof WorldServer)) throw new CommandException("Runtime arguments require a server world");
                NBTBase value = FunctionStorage.get((WorldServer) world).get(new ResourceLocation(args[4]), args[5]);
                if (!(value instanceof NBTTagCompound)) throw new CommandException("Runtime arguments must resolve to a compound");
                executionSender = rml.loader.runtime.function.FunctionCommandContext.withArguments(sender, (NBTTagCompound) value);
            }
            return invoke(server.getFunctionManager(), function, executionSender);
        }
        if ("return".equals(args[0])) {
            if (args.length > 1 && "run".equals(args[1])) return server.getCommandManager().executeCommand(sender, join(args, 2));
            if (args.length > 1 && "fail".equals(args[1])) return 0;
            return args.length > 1 ? parseInt(args[1]) : 0;
        }
        if ("execute".equals(args[0])) {
            int run = indexOf(args, "run", 1);
            if (run < 0 || run + 1 >= args.length) throw new CommandException("Expected execute clauses run <command>");
            String nested = join(args, run + 1);
            int result = 0;
            for (ICommandSender context : executeContexts(sender, args, run, compilePredicate(join(args, 0, run)))) {
                int value = rml.loader.runtime.function.FunctionCommandContext.executeCommand(server.getFunctionManager(), context, nested);
                storeResult(context, args, run, value);
                result += value;
            }
            return result;
        }
        if ("data".equals(args[0])) return data(sender, args);
        throw new CommandException("Unknown function command " + args[0]);
    }


    public Set<String> getCommands() { return commands; }

    private int data(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) throw new CommandException("Expected data get|modify|remove <entity|block|storage> ...");
        String operation = args[1];
        int targetStart = 2;
        DataTarget[] targets = dataTargets(sender, args, targetStart);
        if ("merge".equals(operation)) {
            int valueIndex = targetStart + ("block".equals(args[targetStart]) ? 4 : 2);
            NBTBase incoming = parseNbt(args, valueIndex);
            if (!(incoming instanceof NBTTagCompound)) throw new CommandException("Data merge requires a compound");
            for (DataTarget target : targets) target.mergeRoot((NBTTagCompound) incoming);
            return targets.length;
        }
        int pathIndex = targetStart + ("entity".equals(args[targetStart]) ? 2 : "block".equals(args[targetStart]) ? 4 : 2);
        if (pathIndex >= args.length) throw new CommandException("Missing data path");
        String path = args[pathIndex];
        if ("get".equals(operation)) {
            NBTBase found = targets.length == 0 ? null : targets[0].get(path);
            if (found == null) throw new CommandException("No data at " + path);
            int result = found instanceof net.minecraft.nbt.NBTPrimitive ? ((net.minecraft.nbt.NBTPrimitive) found).getInt() : found.toString().length();
            sender.setCommandStat(net.minecraft.command.CommandResultStats.Type.QUERY_RESULT, result);
            return result;
        }
        if ("remove".equals(operation)) {
            int changed = 0;
            for (DataTarget target : targets) if (target.remove(path)) changed++;
            if (changed == 0) throw new CommandException("No data removed at " + path);
            return changed;
        }
        if (!("modify".equals(operation) || "merge".equals(operation))) throw new CommandException("Unknown data operation " + operation);
        int action = pathIndex + 1;
        if (action >= args.length) throw new CommandException("Missing data modification operation");
        if ("set".equals(args[action]) && action + 1 < args.length && "value".equals(args[action + 1])) {
            NBTBase value = parseNbt(args, action + 2);
            for (DataTarget target : targets) target.set(path, value);
        } else if ("set".equals(args[action]) && action + 1 < args.length && "from".equals(args[action + 1])) {
            int sourceTarget = action + 2;
            if (sourceTarget >= args.length) throw new CommandException("Expected source data target and path");
            int sourcePath = sourceTarget + ("entity".equals(args[sourceTarget]) ? 2 : "block".equals(args[sourceTarget]) ? 4 : 2);
            if (sourcePath >= args.length) throw new CommandException("Expected source data target and path");
            DataTarget[] sources = dataTargets(sender, args, sourceTarget);
            if (sources.length == 0 || sources[0].get(args[sourcePath]) == null) throw new CommandException("Source data path does not exist");
            NBTBase value = sources[0].get(args[sourcePath]);
            for (DataTarget target : targets) target.set(path, value);
        } else if (("append".equals(args[action]) || "prepend".equals(args[action])) && action + 1 < args.length && "value".equals(args[action + 1])) {
            NBTBase value = parseNbt(args, action + 2);
            for (DataTarget target : targets) {
                NBTBase old = target.get(path);
                if (!(old instanceof net.minecraft.nbt.NBTTagList)) throw new CommandException("Data path is not a list: " + path);
                net.minecraft.nbt.NBTTagList list = insertList((net.minecraft.nbt.NBTTagList) old, "append".equals(args[action]) ? ((net.minecraft.nbt.NBTTagList) old).tagCount() : 0, value);
                target.set(path, list);
            }
        } else if ("insert".equals(args[action]) && action + 2 < args.length && "value".equals(args[action + 2])) {
            int insert;
            try { insert = Integer.parseInt(args[action + 1]); }
            catch (NumberFormatException e) { throw new CommandException("Invalid list insert index"); }
            NBTBase value = parseNbt(args, action + 3);
            for (DataTarget target : targets) {
                NBTBase old = target.get(path);
                if (!(old instanceof net.minecraft.nbt.NBTTagList)) throw new CommandException("Data path is not a list: " + path);
                net.minecraft.nbt.NBTTagList list = insertList((net.minecraft.nbt.NBTTagList) old, insert, value);
                target.set(path, list);
            }
        } else if ("merge".equals(args[action])) {
            NBTBase value = parseNbt(args, action + 1);
            if (!(value instanceof NBTTagCompound)) throw new CommandException("Data merge requires a compound");
            for (DataTarget target : targets) {
                NBTBase old = target.get(path);
                NBTTagCompound merged = old instanceof NBTTagCompound ? (NBTTagCompound) old.copy() : new NBTTagCompound();
                merged.merge((NBTTagCompound) value.copy());
                target.set(path, merged);
            }
        } else throw new CommandException("Unsupported data modify operation");
        return targets.length;
    }
    private static net.minecraft.nbt.NBTTagList insertList(net.minecraft.nbt.NBTTagList source, int index, NBTBase value) throws CommandException {
        if (index < 0 || index > source.tagCount()) throw new CommandException("List insertion index is outside the list");
        if (source.tagCount() != 0 && source.getTagType() != value.getId()) throw new CommandException("List element type does not match existing values");
        net.minecraft.nbt.NBTTagList result = new net.minecraft.nbt.NBTTagList();
        for (int i = 0; i <= source.tagCount(); i++) {
            if (i == index) result.appendTag(value.copy());
            if (i < source.tagCount()) result.appendTag(source.get(i).copy());
        }
        return result;
    }

    private DataTarget[] dataTargets(ICommandSender sender, String[] args, int index) throws CommandException {
        String kind = args[index];
        World world = sender.getEntityWorld();
        if ("storage".equals(kind)) {
            if (index + 1 >= args.length) throw new CommandException("Expected storage <id>");
            if (!(world instanceof WorldServer)) throw new CommandException("Function storage requires a server world");
            ResourceLocation id = new ResourceLocation(args[index + 1]);
            return new DataTarget[]{new DataTarget(FunctionStorage.get((WorldServer) world), id)};
        }
        if ("entity".equals(kind)) {
            if (index + 1 >= args.length) throw new CommandException("Expected entity <selector>");
            java.util.List<Entity> entities = net.minecraft.command.EntitySelector.matchEntities(sender, args[index + 1], Entity.class);
            DataTarget[] result = new DataTarget[entities.size()];
            for (int i = 0; i < result.length; i++) result[i] = new DataTarget(entities.get(i));
            return result;
        }
        if ("block".equals(kind)) {
            if (index + 3 >= args.length) throw new CommandException("Expected block x y z");
            if (world == null) throw new CommandException("Block data requires a world");
            BlockPos pos = position(sender, args[index + 1], args[index + 2], args[index + 3]);
            TileEntity tile = world.getTileEntity(pos);
            if (tile == null) throw new CommandException("Block has no data at " + pos);
            return new DataTarget[]{new DataTarget(tile)};
        }
        throw new CommandException("Unknown data target " + kind);
    }

    static NBTBase parseNbt(String[] args, int index) throws CommandException {
        if (index >= args.length) throw new CommandException("Missing SNBT value");
        try {
            NBTTagCompound wrapper = JsonToNBT.getTagFromJson("{value:" + join(args, index) + "}");
            if (wrapper.getKeySet().size() != 1) throw new IllegalArgumentException("Expected one SNBT value");
            return wrapper.getTag("value");
        }
        catch (Exception e) { throw new CommandException("Invalid SNBT value: " + e.getMessage()); }
    }
    private void storeResult(ICommandSender sender, String[] args, int run, int result) throws CommandException {
        storeResult(sender, args, run, result, result != 0);
    }

    private void storeResult(ICommandSender sender, String[] args, int run, int result, boolean succeeded) throws CommandException {
        for (int index = 1; index < run;) {
            if (!"store".equals(args[index])) { index++; continue; }
            if (index + 2 >= run) throw new CommandException("Incomplete execute store clause");
            String kind = args[index + 1];
            String targetKind = args[index + 2];
            if (!("result".equals(kind) || "success".equals(kind))) throw new CommandException("Expected store result or success");
            if ("score".equals(targetKind)) {
                if (index + 4 >= run) throw new CommandException("Expected score <selector> <objective>");
                World world = sender.getEntityWorld();
                if (world == null) throw new CommandException("Score storage requires a world");
                net.minecraft.scoreboard.Scoreboard board = world.getScoreboard();
                net.minecraft.scoreboard.ScoreObjective objective = board.getObjective(args[index + 4]);
                if (objective == null) throw new CommandException("Unknown scoreboard objective " + args[index + 4]);
                int value = "success".equals(kind) ? (succeeded ? 1 : 0) : result;
                String holder = args[index + 3];
                if (net.minecraft.command.EntitySelector.isSelector(holder)) {
                    for (Entity entity : net.minecraft.command.EntitySelector.matchEntities(sender, holder, Entity.class)) {
                        String name = entity instanceof net.minecraft.entity.player.EntityPlayer ? entity.getName() : entity.getCachedUniqueIdString();
                        board.getOrCreateScore(name, objective).setScorePoints(value);
                    }
                } else {
                    board.getOrCreateScore(holder, objective).setScorePoints(value);
                }
                index += 5;
                continue;
            }
            int targetStart = index + 2;
            int pathIndex = targetStart + ("entity".equals(targetKind) ? 2 : "block".equals(targetKind) ? 4 : 2);
            if (pathIndex + 1 >= run) throw new CommandException("Incomplete execute store data target");
            String path = args[pathIndex];
            String numberType = args[pathIndex + 1];
            double scale = 1.0D;
            int next = pathIndex + 2;
            if (next < run && !"store".equals(args[next]) && !"if".equals(args[next]) && !"unless".equals(args[next])) {
                try { scale = Double.parseDouble(args[next++]); }
                catch (NumberFormatException ignored) { }
            }
            double value = ("success".equals(kind) ? (succeeded ? 1 : 0) : result) * scale;
            NBTBase tag;
            if ("byte".equals(numberType)) tag = new NBTTagByte((byte) value);
            else if ("short".equals(numberType)) tag = new NBTTagShort((short) value);
            else if ("int".equals(numberType)) tag = new NBTTagInt((int) value);
            else if ("long".equals(numberType)) tag = new NBTTagLong((long) value);
            else if ("float".equals(numberType)) tag = new NBTTagFloat((float) value);
            else if ("double".equals(numberType)) tag = new NBTTagDouble(value);
            else throw new CommandException("Unsupported data numeric type " + numberType);
            for (DataTarget target : dataTargets(sender, args, targetStart)) target.set(path, tag);
            index = next;
        }
    }


    private static final class DataTarget {
        final FunctionStorage storage;
        final ResourceLocation id;
        final Entity entity;
        final TileEntity tile;
        DataTarget(FunctionStorage storage, ResourceLocation id) { this.storage = storage; this.id = id; entity = null; tile = null; }
        DataTarget(Entity entity) { storage = null; id = null; this.entity = entity; tile = null; }
        DataTarget(TileEntity tile) { storage = null; id = null; entity = null; this.tile = tile; }
        NBTTagCompound root() {
            if (entity != null) return entity.writeToNBT(new NBTTagCompound());
            if (tile != null) return tile.writeToNBT(new NBTTagCompound());
            return null;
        }
        NBTBase get(String path) { return storage != null ? storage.get(id, path) : FunctionDataPath.get(root(), path); }
        void set(String path, NBTBase value) {
            if (storage != null) { storage.set(id, path, value); return; }
            NBTTagCompound root = root();
            FunctionDataPath.set(root, path, value.copy());
            if (entity != null) entity.readFromNBT(root);
            else { tile.readFromNBT(root); tile.markDirty(); }
        }
        boolean remove(String path) {
            if (storage != null) return storage.remove(id, path);
            NBTTagCompound root = root();
            if (!FunctionDataPath.remove(root, path)) return false;
            if (entity != null) entity.readFromNBT(root);
            else { tile.readFromNBT(root); tile.markDirty(); }
            return true;
        }
        void mergeRoot(NBTTagCompound incoming) {
            if (storage != null) { storage.mergeRoot(id, incoming); return; }
            NBTTagCompound root = root();
            root.merge((NBTTagCompound) incoming.copy());
            if (entity != null) entity.readFromNBT(root);
            else { tile.readFromNBT(root); tile.markDirty(); }
        }
    }

    private boolean test(ICommandSender sender, String[] args, int start, int end, DataRules.Test predicate) throws CommandException {
        boolean unless = "unless".equals(args[start]);
        if (!unless && !"if".equals(args[start])) throw new CommandException("Expected if or unless");
        if (start + 2 >= end) throw new CommandException("Incomplete execute condition");
        String kind = args[start + 1];
        boolean result;
        if ("entity".equals(kind)) {
            result = !net.minecraft.command.EntitySelector.matchEntities(sender, args[start + 2], Entity.class).isEmpty();
        } else if ("block".equals(kind)) {
            if (start + 5 >= end) throw new CommandException("Expected block x y z block");
            World world = sender.getEntityWorld();
            BlockPos pos = position(sender, args[start + 2], args[start + 3], args[start + 4]);
            net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromName(args[start + 5]);
            result = world != null && block != null && world.getBlockState(pos).getBlock() == block;
        } else if ("predicate".equals(kind)) {
            if (predicate == null) throw new CommandException("Missing compiled predicate reference");
            result = predicate.test(context(sender));
        } else if ("data".equals(kind)) {
            int targetStart = start + 2;
            int pathIndex = targetStart + ("entity".equals(args[targetStart]) ? 2 : "block".equals(args[targetStart]) ? 4 : 2);
            if (pathIndex >= end) throw new CommandException("Expected data target and path");
            result = false;
            for (DataTarget target : dataTargets(sender, args, targetStart)) if (target.get(args[pathIndex]) != null) { result = true; break; }
        } else if ("score".equals(kind)) {
            if (start + 4 >= end) throw new CommandException("Incomplete score condition");
            World world = sender.getEntityWorld();
            if (world == null) throw new CommandException("Score condition requires a world");
            net.minecraft.scoreboard.Scoreboard board = world.getScoreboard();
            net.minecraft.scoreboard.ScoreObjective leftObjective = board.getObjective(args[start + 3]);
            result = false;
            if (leftObjective != null) {
                String leftName = net.minecraft.command.CommandBase.getEntityName(sender.getServer(), sender, args[start + 2]);
                if ("matches".equals(args[start + 4])) {
                    if (start + 5 >= end) throw new CommandException("Expected score match range");
                    int[] range = scoreRange(args[start + 5]);
                    if (board.entityHasObjective(leftName, leftObjective)) {
                        int points = board.getOrCreateScore(leftName, leftObjective).getScorePoints();
                        result = points >= range[0] && points <= range[1];
                    }
                } else {
                    if (start + 6 >= end) throw new CommandException("Expected score <holder> <objective> <comparison> <holder> <objective>");
                    String comparison = args[start + 4];
                    if (!isScoreOperator(comparison)) throw new CommandException("Unsupported score comparison " + comparison);
                    net.minecraft.scoreboard.ScoreObjective rightObjective = board.getObjective(args[start + 6]);
                    if (rightObjective != null && board.entityHasObjective(leftName, leftObjective)) {
                        String rightName = net.minecraft.command.CommandBase.getEntityName(sender.getServer(), sender, args[start + 5]);
                        if (board.entityHasObjective(rightName, rightObjective)) {
                            result = compareScores(board.getOrCreateScore(leftName, leftObjective).getScorePoints(),
                                    board.getOrCreateScore(rightName, rightObjective).getScorePoints(), comparison);
                        }
                    }
                }
            }
        } else {
            throw new CommandException("Unsupported execute condition " + kind);
        }
        return unless ? !result : result;
    }

    private static boolean isScoreOperator(String value) {
        return "=".equals(value) || "<".equals(value) || ">".equals(value) || "<=".equals(value) || ">=".equals(value);
    }

    private static boolean compareScores(int left, int right, String operator) {
        switch (operator) {
            case "=": return left == right;
            case "<": return left < right;
            case ">": return left > right;
            case "<=": return left <= right;
            case ">=": return left >= right;
            default: return false;
        }
    }
    private DataRules.Test compilePredicate(String command) {
        String[] args = command.trim().split("\\s+");
        int run = indexOf(args, "run", 1);
        if (!"execute".equals(args[0]) || run < 0) return null;
        for (int i = 1; i + 1 < run; i++) if (("if".equals(args[i]) || "unless".equals(args[i])) && "predicate".equals(args[i + 1])) {
            if (i + 2 >= run) throw new IllegalArgumentException("Missing execute predicate id");
            JsonObject reference = new JsonObject();
            reference.addProperty("condition", "reference");
            reference.addProperty("name", args[i + 2]);
            return rules.predicate(reference);
        }
        return null;
    }
    private static int[] scoreRange(String value) throws CommandException {
        int separator = value.indexOf("..");
        try {
            if (separator < 0) { int exact = Integer.parseInt(value); return new int[]{exact, exact}; }
            int min = separator == 0 ? Integer.MIN_VALUE : Integer.parseInt(value.substring(0, separator));
            int max = separator + 2 == value.length() ? Integer.MAX_VALUE : Integer.parseInt(value.substring(separator + 2));
            if (min > max) throw new NumberFormatException();
            return new int[]{min, max};
        } catch (NumberFormatException e) { throw new CommandException("Invalid score range " + value); }
    }
    private List<ICommandSender> executeContexts(ICommandSender sender, String[] args, int run, DataRules.Test predicate) throws CommandException {
        List<ICommandSender> contexts = new ArrayList<>();
        contexts.add(sender);
        int index = 1;
        while (index < run) {
            String clause = args[index++];
            if ("as".equals(clause) || "at".equals(clause)) {
                if (index >= run) throw new CommandException("Missing execute selector");
                String selector = args[index++];
                List<ICommandSender> next = new ArrayList<>();
                for (ICommandSender current : contexts) {
                    List<Entity> selected = net.minecraft.command.EntitySelector.matchEntities(current, selector, Entity.class);
                    for (Entity entity : selected) {
                        Entity active = "as".equals(clause) ? entity : current.getCommandSenderEntity();
                        World world = "at".equals(clause) ? entity.world : current.getEntityWorld();
                        net.minecraft.util.math.Vec3d origin = "at".equals(clause) ? entity.getPositionVector() : current.getPositionVector();
                        ICommandSender selectedContext = new rml.loader.runtime.function.FunctionCommandContext(current, active, world, origin);
                        if ("at".equals(clause)) selectedContext = rml.loader.runtime.function.FunctionCommandContext.withRotation(selectedContext, entity.rotationYaw, entity.rotationPitch);
                        next.add(selectedContext);
                    }
                }
                contexts = next;
            } else if ("positioned".equals(clause)) {
                if (index < run && "as".equals(args[index])) {
                    index++;
                    if (index >= run) throw new CommandException("Missing positioned entity selector");
                    String selector = args[index++];
                    List<ICommandSender> next = new ArrayList<>();
                    for (ICommandSender current : contexts) for (Entity entity : net.minecraft.command.EntitySelector.matchEntities(current, selector, Entity.class)) {
                        next.add(new rml.loader.runtime.function.FunctionCommandContext(current, current.getCommandSenderEntity(), current.getEntityWorld(), entity.getPositionVector()));
                    }
                    contexts = next;
                } else {
                    if (index + 2 >= run) throw new CommandException("Expected positioned x y z");
                    List<ICommandSender> next = new ArrayList<>(contexts.size());
                    for (ICommandSender current : contexts) {
                        net.minecraft.util.math.Vec3d origin = positionVector(current, args[index], args[index + 1], args[index + 2]);
                        next.add(new rml.loader.runtime.function.FunctionCommandContext(current, current.getCommandSenderEntity(), current.getEntityWorld(), origin));
                    }
                    index += 3;
                    contexts = next;
                }
            } else if ("align".equals(clause)) {
                if (index >= run) throw new CommandException("Missing align axes");
                String axes = args[index++];
                List<ICommandSender> next = new ArrayList<>(contexts.size());
                for (ICommandSender current : contexts) {
                    net.minecraft.util.math.Vec3d pos = current.getPositionVector();
                    next.add(new rml.loader.runtime.function.FunctionCommandContext(current, current.getCommandSenderEntity(),
                            current.getEntityWorld(), new net.minecraft.util.math.Vec3d(axes.contains("x") ? Math.floor(pos.x) : pos.x,
                            axes.contains("y") ? Math.floor(pos.y) : pos.y, axes.contains("z") ? Math.floor(pos.z) : pos.z)));
                }
                contexts = next;
            } else if ("in".equals(clause)) {
                if (index >= run) throw new CommandException("Missing execute dimension");
                String dimensionName = args[index++];
                int dimension;
                try { dimension = Integer.parseInt(dimensionName); }
                catch (NumberFormatException numericFailure) {
                    ResourceLocation id;
                    try { id = new ResourceLocation(dimensionName); }
                    catch (RuntimeException invalidId) { throw new CommandException("Invalid execute dimension " + dimensionName); }
                    Integer resolved = "minecraft:overworld".equals(id.toString()) ? 0
                            : "minecraft:the_nether".equals(id.toString()) ? -1
                            : "minecraft:the_end".equals(id.toString()) ? 1 : dimensionResolver.apply(id);
                    if (resolved == null) throw new CommandException("Unknown execute dimension " + id);
                    dimension = resolved;
                }
                List<ICommandSender> next = new ArrayList<>(contexts.size());
                for (ICommandSender current : contexts) {
                    MinecraftServer server = current.getServer();
                    WorldServer world = server == null ? null : server.getWorld(dimension);
                    if (world == null) throw new CommandException("Unknown execute dimension " + dimension);
                    next.add(new rml.loader.runtime.function.FunctionCommandContext(current, current.getCommandSenderEntity(), world, current.getPositionVector()));
                }
                contexts = next;
            } else if ("store".equals(clause)) {
                if (index + 1 >= run) throw new CommandException("Incomplete execute store clause");
                index += 2;
                String target = args[index - 1];
                if ("score".equals(target)) index += 2;
                else {
                    int targetStart = index - 1;
                    int pathIndex = targetStart + ("entity".equals(target) ? 2 : "block".equals(target) ? 4 : 2);
                    index = pathIndex + 2;
                    if (index < run) {
                        try { Double.parseDouble(args[index]); index++; }
                        catch (NumberFormatException ignored) { }
                    }
                }
                if (index > run) throw new CommandException("Incomplete execute store target");
            } else if ("anchored".equals(clause)) {
                if (index >= run) throw new CommandException("Missing execute anchor");
                String anchor = args[index++];
                if (!("eyes".equals(anchor) || "feet".equals(anchor))) throw new CommandException("Invalid execute anchor " + anchor);
                if ("eyes".equals(anchor)) {
                    List<ICommandSender> next = new ArrayList<>(contexts.size());
                    for (ICommandSender current : contexts) {
                        Entity entity = current.getCommandSenderEntity();
                        net.minecraft.util.math.Vec3d pos = entity == null ? current.getPositionVector()
                                : new net.minecraft.util.math.Vec3d(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
                        next.add(new rml.loader.runtime.function.FunctionCommandContext(current, entity, current.getEntityWorld(), pos));
                    }
                    contexts = next;
                }
            } else if ("rotated".equals(clause)) {
                if (index + 1 >= run) throw new CommandException("Expected rotated yaw pitch");
                List<ICommandSender> next = new ArrayList<>(contexts.size());
                for (ICommandSender current : contexts) {
                    float yaw;
                    float pitch;
                    try { yaw = rotationCoordinate(rotationYaw(current), args[index]); pitch = rotationCoordinate(rotationPitch(current), args[index + 1]); }
                    catch (NumberFormatException e) { throw new CommandException("Invalid execute rotation"); }
                    next.add(rml.loader.runtime.function.FunctionCommandContext.withRotation(current, yaw, pitch));
                }
                index += 2;
                contexts = next;
            } else if ("facing".equals(clause)) {
                if (index >= run) throw new CommandException("Missing facing target");
                List<ICommandSender> next = new ArrayList<>();
                if ("entity".equals(args[index])) {
                    if (index + 2 >= run) throw new CommandException("Expected facing entity <selector> eyes|feet");
                    String selector = args[index + 1];
                    String anchor = args[index + 2];
                    if (!("eyes".equals(anchor) || "feet".equals(anchor))) throw new CommandException("Invalid facing anchor " + anchor);
                    for (ICommandSender current : contexts) for (Entity target : net.minecraft.command.EntitySelector.matchEntities(current, selector, Entity.class)) {
                        net.minecraft.util.math.Vec3d point = new net.minecraft.util.math.Vec3d(target.posX,
                                target.posY + ("eyes".equals(anchor) ? target.getEyeHeight() : 0.0D), target.posZ);
                        float[] rotation = lookAt(current.getPositionVector(), point);
                        next.add(rml.loader.runtime.function.FunctionCommandContext.withRotation(current, rotation[0], rotation[1]));
                    }
                    index += 3;
                } else {
                    if (index + 2 >= run) throw new CommandException("Expected facing x y z");
                    for (ICommandSender current : contexts) {
                        float[] rotation = lookAt(current.getPositionVector(), positionVector(current, args[index], args[index + 1], args[index + 2]));
                        next.add(rml.loader.runtime.function.FunctionCommandContext.withRotation(current, rotation[0], rotation[1]));
                    }
                    index += 3;
                }
                contexts = next;
            }
            else if ("on".equals(clause)) {
                if (index >= run) throw new CommandException("Missing execute entity relation");
                String relation = args[index++];
                List<ICommandSender> next = new ArrayList<>();
                for (ICommandSender current : contexts) {
                    Entity executor = current.getCommandSenderEntity();
                    if (executor == null) continue;
                    List<Entity> related = new ArrayList<>();
                    if ("vehicle".equals(relation)) {
                        if (executor.getRidingEntity() != null) related.add(executor.getRidingEntity());
                    } else if ("passengers".equals(relation)) related.addAll(executor.getPassengers());
                    else if ("owner".equals(relation) && executor instanceof net.minecraft.entity.IEntityOwnable) {
                        Entity owner = ((net.minecraft.entity.IEntityOwnable) executor).getOwner();
                        if (owner != null) related.add(owner);
                    } else if ("leasher".equals(relation) && executor instanceof net.minecraft.entity.EntityLiving) {
                        Entity leasher = ((net.minecraft.entity.EntityLiving) executor).getLeashHolder();
                        if (leasher != null) related.add(leasher);
                    } else throw new CommandException("Unsupported execute entity relation " + relation);
                    for (Entity entity : related) next.add(new rml.loader.runtime.function.FunctionCommandContext(current, entity, entity.world, entity.getPositionVector()));
                }
                contexts = next;
            }
            else if ("if".equals(clause) || "unless".equals(clause)) {
                int conditionStart = index - 1;
                int kind = index;
                String conditionKind = args[kind];
                int consumed = "block".equals(conditionKind) ? 5
                        : "score".equals(conditionKind) ? ("matches".equals(args[kind + 3]) ? 5 : 6)
                        : "predicate".equals(conditionKind) || "entity".equals(conditionKind) ? 2
                        : "data".equals(conditionKind) ? ("block".equals(args[kind + 1]) ? 6 : 4) : 0;
                if (consumed == 0) throw new CommandException("Unsupported execute condition " + conditionKind);
                List<ICommandSender> next = new ArrayList<>();
                for (ICommandSender current : contexts) if (test(current, args, conditionStart, run, predicate)) next.add(current);
                contexts = next;
                index += consumed;
            } else {
                throw new CommandException("Unsupported execute clause " + clause);
            }
        }
        return contexts;
    }


    private static String join(String[] args, int start, int end) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < end; i++) { if (out.length() != 0) out.append(' '); out.append(args[i]); }
        return out.toString();
    }


    private static BlockPos position(ICommandSender sender, String x, String y, String z) throws CommandException {
        return new BlockPos(positionVector(sender, x, y, z));
    }

    private static net.minecraft.util.math.Vec3d positionVector(ICommandSender sender, String x, String y, String z) throws CommandException {
        net.minecraft.util.math.Vec3d origin = sender.getPositionVector();
        try {
            return new net.minecraft.util.math.Vec3d(coordinate(origin.x, x), coordinate(origin.y, y), coordinate(origin.z, z));
        } catch (NumberFormatException e) {
            throw new CommandException("Invalid execute position");
        }
    }

    private static double coordinate(double base, String token) {
        return token.startsWith("~") ? base + (token.length() == 1 ? 0.0D : Double.parseDouble(token.substring(1))) : Double.parseDouble(token);
    }
    private static float rotationYaw(ICommandSender sender) {
        if (sender instanceof rml.loader.runtime.function.FunctionCommandContext) return ((rml.loader.runtime.function.FunctionCommandContext) sender).getYaw();
        Entity entity = sender.getCommandSenderEntity();
        return entity == null ? 0.0F : entity.rotationYaw;
    }

    private static float rotationPitch(ICommandSender sender) {
        if (sender instanceof rml.loader.runtime.function.FunctionCommandContext) return ((rml.loader.runtime.function.FunctionCommandContext) sender).getPitch();
        Entity entity = sender.getCommandSenderEntity();
        return entity == null ? 0.0F : entity.rotationPitch;
    }

    private static float rotationCoordinate(float base, String value) {
        return value.startsWith("~") ? base + (value.length() == 1 ? 0.0F : Float.parseFloat(value.substring(1))) : Float.parseFloat(value);
    }

    private static float[] lookAt(net.minecraft.util.math.Vec3d origin, net.minecraft.util.math.Vec3d target) {
        double dx = target.x - origin.x;
        double dy = target.y - origin.y;
        double dz = target.z - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return new float[]{(float) (Math.atan2(dz, dx) * 180.0D / Math.PI - 90.0D),
                (float) (-Math.atan2(dy, horizontal) * 180.0D / Math.PI)};
    }

    private static int parseInt(String value) throws CommandException {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { throw new CommandException("Invalid return value " + value); }
    }

    private static int indexOf(String[] args, String word, int start) {
        for (int i = start; i < args.length; i++) if (word.equals(args[i])) return i;
        return -1;
    }

    private static String first(String command) {
        int end = command.indexOf(' ');
        return end < 0 ? command : command.substring(0, end);
    }
    private static String stripRml(String command) {
        return command.startsWith("rml ") ? command.substring(4) : command;
    }

    private static boolean isEnhanced(String command) {
        String[] words = command.split("\\s+");
        if ("rml".equals(words[0])) return words.length > 1 && isEnhanced(command.substring(4));
        if ("execute".equals(words[0])) return words.length > 1 && ("if".equals(words[1]) || "unless".equals(words[1])
                || "as".equals(words[1]) || "at".equals(words[1]) || "positioned".equals(words[1])
                || "align".equals(words[1]) || "store".equals(words[1]) || "in".equals(words[1])
                || "anchored".equals(words[1]) || "facing".equals(words[1]) || "rotated".equals(words[1]) || "on".equals(words[1]));
        return "data".equals(words[0]) || "return".equals(words[0]) || "function".equals(words[0]);
    }


    private static String join(String[] args, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < args.length; i++) { if (out.length() != 0) out.append(' '); out.append(args[i]); }
        return out.toString();
    }

    private DataContext context(ICommandSender sender) {
        World world = sender.getEntityWorld();
        WorldServer serverWorld = world instanceof WorldServer ? (WorldServer) world : null;
        net.minecraft.entity.Entity entity = sender.getCommandSenderEntity();
        BlockPos origin = sender.getPosition();
        Random random = serverWorld == null ? new Random() : serverWorld.rand;
        return serverWorld == null ? new DataContext(null, entity, origin, random, 0)
                : DataContext.fromLoot(new rml.loader.content.data.PositionedLootContext(serverWorld, entity, origin), random);
    }

    private static final class CompiledEntry implements FunctionObject.Entry {
        private final Program program;
        private final String command;
        private final DataRules.Test predicate;
        CompiledEntry(Program program, String command, DataRules.Test predicate) {
            this.program = program;
            this.command = command;
            this.predicate = predicate;
        }
        @Override public void execute(FunctionManager manager, ICommandSender sender, ArrayDeque<FunctionManager.QueuedCommand> queue, int limit) {
            if (program.scope != null && !program.scope.isActive()) return;
            countFrame(sender);
            String[] args = command.split("\\s+");
            try {
                if ("function".equals(args[0])) {
                    if (args.length < 2) throw new IllegalArgumentException("Expected function <id>");
                    if (args.length == 4 && ("if".equals(args[2]) || "unless".equals(args[2]))) {
                        boolean matches = !net.minecraft.command.EntitySelector.matchEntities(sender, args[3], Entity.class).isEmpty();
                        if (matches == "unless".equals(args[2])) return;
                    }
                    FunctionObject target = manager.getFunction(new ResourceLocation(args[1]));
                    if (target == null) throw new IllegalArgumentException("Unknown function " + args[1]);
                    ICommandSender callSender = sender;
                    if (args.length == 6 && "with".equals(args[2]) && "storage".equals(args[3])) {
                        World world = sender.getEntityWorld();
                        if (!(world instanceof WorldServer)) throw new IllegalArgumentException("Runtime arguments require a server world");
                        NBTBase value = FunctionStorage.get((WorldServer) world).get(new ResourceLocation(args[4]), args[5]);
                        if (!(value instanceof NBTTagCompound)) throw new IllegalArgumentException("Runtime arguments must resolve to a compound");
                        callSender = rml.loader.runtime.function.FunctionCommandContext.withArguments(sender, (NBTTagCompound) value);
                    }
                    FunctionObject.Entry[] entries = target.getEntries();
                    int count = Math.min(entries.length, Math.max(0, limit - queue.size()));
                    for (int i = count - 1; i >= 0; i--) queue.addFirst(new FunctionManager.QueuedCommand(manager, callSender, contextualEntry(program, entries[i])));
                } else if ("return".equals(args[0])) {
                    int returnValue = 0;
                    boolean returnSucceeded = true;
                    if (args.length > 2 && "run".equals(args[1]) && "function".equals(args[2])) {
                        if (args.length < 4) throw new CommandException("Expected return run function <id>");
                        FunctionObject nested = manager.getFunction(new ResourceLocation(args[3]));
                        if (nested == null) throw new CommandException("Unknown function " + args[3]);
                        FunctionCommandContext parentContext = sender instanceof FunctionCommandContext ? (FunctionCommandContext) sender : null;
                        FunctionReturnFrame childFrame = new FunctionReturnFrame(parentContext == null ? null : parentContext.getReturnFrame());
                        ICommandSender childSender = FunctionCommandContext.withReturnFrame(sender, childFrame);
                        if (args.length == 8 && "with".equals(args[4]) && "storage".equals(args[5])) {
                            World world = sender.getEntityWorld();
                            if (!(world instanceof WorldServer)) throw new CommandException("Runtime arguments require a server world");
                            NBTBase value = FunctionStorage.get((WorldServer) world).get(new ResourceLocation(args[6]), args[7]);
                            if (!(value instanceof NBTTagCompound)) throw new CommandException("Runtime arguments must resolve to a compound");
                            childSender = FunctionCommandContext.withArguments(childSender, (NBTTagCompound) value);
                        }
                        FunctionObject.Entry[] nestedEntries = nested.getEntries();
                        int available = Math.max(0, limit - queue.size());
                        if (available > 0) {
                            int count = Math.min(nestedEntries.length, available - 1);
                            queue.addFirst(new FunctionManager.QueuedCommand(manager, sender,
                                    new ReturnFinishEntry(program, childFrame, parentContext == null ? null : parentContext.getReturnFrame())));
                            for (int i = count - 1; i >= 0; i--) queue.addFirst(new FunctionManager.QueuedCommand(manager, childSender, contextualEntry(program, nestedEntries[i])));
                            return;
                        }
                        sender.setCommandStat(net.minecraft.command.CommandResultStats.Type.QUERY_RESULT, 0);
                        stopFrame(program, queue);
                        return;
                    }
                    if (args.length > 1 && "run".equals(args[1])) {
                        returnValue = manager.getCommandManager().executeCommand(sender, join(args, 2));
                        returnSucceeded = returnValue != 0;
                    } else if (args.length > 1 && "fail".equals(args[1])) { returnValue = 0; returnSucceeded = false; }
                    else if (args.length > 1) returnValue = parseInt(args[1]);
                    if (sender instanceof FunctionCommandContext && ((FunctionCommandContext) sender).getReturnFrame() != null)
                        ((FunctionCommandContext) sender).getReturnFrame().complete(returnValue, returnSucceeded);
                    sender.setCommandStat(net.minecraft.command.CommandResultStats.Type.QUERY_RESULT, returnValue);
                    if (program.end != null) {
                        boolean markerPresent = false;
                        for (FunctionManager.QueuedCommand queued : queue) if (queued.toString().equals(program.end.toString())) { markerPresent = true; break; }
                        if (markerPresent) while (!queue.isEmpty()) if (queue.removeFirst().toString().equals(program.end.toString())) break;
                    }
                } else if ("data".equals(args[0])) {
                    program.engine.data(sender, args);
                } else if ("execute".equals(args[0])) {
                    int run = indexOf(args, "run", 1);
                    if (run < 0 || run + 1 >= args.length) throw new CommandException("Expected execute clauses run <command>");
                    List<ICommandSender> contexts = program.engine.executeContexts(sender, args, run, predicate);
                    String nested = join(args, run + 1);
                    String executable = stripRml(nested);
                    boolean stores = hasStore(args, run);
                    for (int i = contexts.size() - 1; i >= 0; i--) {
                        ICommandSender selected = contexts.get(i);
                        if (stores && isEnhanced(executable)) {
                            FunctionReturnFrame parent = selected instanceof FunctionCommandContext
                                    ? ((FunctionCommandContext) selected).getReturnFrame() : null;
                            FunctionReturnFrame result = new FunctionReturnFrame(parent);
                            ICommandSender frameSender = FunctionCommandContext.withReturnFrame(selected, result);
                            queue.addFirst(new FunctionManager.QueuedCommand(manager, frameSender,
                                    new StoreFinishEntry(program, args, run, result)));
                            queue.addFirst(new FunctionManager.QueuedCommand(manager, frameSender,
                                    new CompiledEntry(program, executable, program.engine.compilePredicate(executable))));
                        } else if (isEnhanced(executable)) {
                            queue.addFirst(new FunctionManager.QueuedCommand(manager, selected,
                                    new CompiledEntry(program, executable, program.engine.compilePredicate(executable))));
                        } else {
                            int result = FunctionCommandContext.executeCommand(manager, selected, nested);
                            if (stores) program.engine.storeResult(selected, args, run, result);
                        }
                    }
                }

            } catch (CommandException | RuntimeException e) {
                throw new IllegalStateException("Compiled function command failed: " + command, e);
            }
        }
        @Override public String toString() { return "/" + command; }
    }
    private static boolean hasStore(String[] args, int run) {
        for (int i = 1; i < run; i++) if ("store".equals(args[i])) return true;
        return false;
    }

    private static final class StoreFinishEntry implements FunctionObject.Entry {
        final Program program;
        final String[] command;
        final int run;
        final FunctionReturnFrame result;
        StoreFinishEntry(Program program, String[] command, int run, FunctionReturnFrame result) {
            this.program = program;
            this.command = java.util.Arrays.copyOf(command, command.length);
            this.run = run;
            this.result = result;
        }
        @Override public void execute(FunctionManager manager, ICommandSender sender, ArrayDeque<FunctionManager.QueuedCommand> queue, int limit) {
            if (program.scope != null && !program.scope.isActive()) return;
            int value = result.hasReturned() ? result.getResult() : result.getCommandCount();
            boolean succeeded = result.succeeded();
            try { program.engine.storeResult(sender, command, run, value, succeeded); }
            catch (CommandException e) { throw new IllegalStateException("Compiled execute store failed", e); }
            sender.setCommandStat(net.minecraft.command.CommandResultStats.Type.QUERY_RESULT, value);
        }
        @Override public String toString() { return "#rml_store_finish_" + program.frameId; }
    }
    private static FunctionObject.Entry contextualEntry(Program program, FunctionObject.Entry entry) {
        return entry instanceof FunctionObject.CommandEntry ? new ScopedEntry(program, entry) : entry;
    }
    private static void countFrame(ICommandSender sender) {
        if (sender instanceof FunctionCommandContext) {
            FunctionReturnFrame frame = ((FunctionCommandContext) sender).getReturnFrame();
            if (frame != null) frame.countCommand();
        }
    }

    private static void stopFrame(Program program, ArrayDeque<FunctionManager.QueuedCommand> queue) {
        if (program.end == null) return;
        boolean markerPresent = false;
        for (FunctionManager.QueuedCommand queued : queue) if (queued.toString().equals(program.end.toString())) { markerPresent = true; break; }
        if (markerPresent) while (!queue.isEmpty()) if (queue.removeFirst().toString().equals(program.end.toString())) break;
    }

    private static final class MacroEntry implements FunctionObject.Entry {
        final Program program;
        final String template;
        final NBTTagCompound arguments;
        MacroEntry(Program program, String template, NBTTagCompound arguments) {
            this.program = program;
            this.template = template;
            this.arguments = arguments == null ? null : (NBTTagCompound) arguments.copy();
        }
        @Override public void execute(FunctionManager manager, ICommandSender sender, ArrayDeque<FunctionManager.QueuedCommand> queue, int limit) {
            if (program.scope != null && !program.scope.isActive()) return;
            countFrame(sender);
            if (arguments == null && (!(sender instanceof rml.loader.runtime.function.FunctionCommandContext)
                    || !((rml.loader.runtime.function.FunctionCommandContext) sender).hasArguments())) {
                throw new IllegalStateException("Runtime macro command was invoked without function arguments: " + template);
            }
            StringBuilder command = new StringBuilder();
            int offset = 0;
            while (true) {
                int start = template.indexOf("$(", offset);
                if (start < 0) { command.append(template, offset, template.length()); break; }
                command.append(template, offset, start);
                int end = template.indexOf(')', start + 2);
                if (end < 0) throw new IllegalArgumentException("Unclosed runtime function macro in " + template);
                NBTBase value = arguments != null ? FunctionDataPath.get(arguments, template.substring(start + 2, end))
                        : ((rml.loader.runtime.function.FunctionCommandContext) sender).getArgument(template.substring(start + 2, end));
                if (value == null) throw new IllegalArgumentException("Missing runtime function argument " + template.substring(start + 2, end));
                command.append(value instanceof net.minecraft.nbt.NBTTagString ? ((net.minecraft.nbt.NBTTagString) value).getString() : value.toString());
                offset = end + 1;
            }
            String expanded = command.toString();
            String executable = stripRml(expanded);
            if (isEnhanced(executable)) queue.addFirst(new FunctionManager.QueuedCommand(manager, sender,
                    new CompiledEntry(program, executable, program.engine.compilePredicate(executable))));
            else rml.loader.runtime.function.FunctionCommandContext.executeCommand(manager, sender, expanded);
        }
        @Override public String toString() { return "/$" + template; }
    }

    private static final class Program {
        private static final java.util.concurrent.atomic.AtomicLong IDS = new java.util.concurrent.atomic.AtomicLong();
        final CompiledFunctionEngine engine;
        final String frameId;
        final FrameEndEntry end;
        volatile ResourceScope scope;
        Program(CompiledFunctionEngine engine, ResourceScope scope, boolean hasReturn) {
            this.engine = engine;
            this.scope = scope;
            this.frameId = hasReturn ? "#rml_function_frame_" + IDS.incrementAndGet() : null;
            this.end = hasReturn ? new FrameEndEntry(this) : null;
        }
    }

    private static final class FrameEndEntry implements FunctionObject.Entry {
        final Program program;
        FrameEndEntry(Program program) { this.program = program; }
        @Override public void execute(FunctionManager manager, ICommandSender sender, ArrayDeque<FunctionManager.QueuedCommand> queue, int limit) { }
        @Override public String toString() { return program.frameId; }
    }

    private static final class ReturnFinishEntry implements FunctionObject.Entry {
        final Program program;
        final FunctionReturnFrame child;
        final FunctionReturnFrame parent;
        ReturnFinishEntry(Program program, FunctionReturnFrame child, FunctionReturnFrame parent) {
            this.program = program;
            this.child = child;
            this.parent = parent;
        }
        @Override public void execute(FunctionManager manager, ICommandSender sender, ArrayDeque<FunctionManager.QueuedCommand> queue, int limit) {
            int result = child.hasReturned() ? child.getResult() : child.getCommandCount();
            if (parent != null) parent.complete(result, child.succeeded());
            sender.setCommandStat(net.minecraft.command.CommandResultStats.Type.QUERY_RESULT, result);
            stopFrame(program, queue);
        }
        @Override public String toString() { return "#rml_return_finish_" + program.frameId; }
    }

    private static final class ScopedEntry implements FunctionObject.Entry {
        final Program program;
        final FunctionObject.Entry delegate;
        ScopedEntry(Program program, FunctionObject.Entry delegate) { this.program = program; this.delegate = delegate; }
        @Override public void execute(FunctionManager manager, ICommandSender sender, ArrayDeque<FunctionManager.QueuedCommand> queue, int limit) {
            if (program.scope == null || program.scope.isActive()) {
                countFrame(sender);
                if (delegate instanceof FunctionObject.CommandEntry) {
                    String command = delegate.toString();
                    rml.loader.runtime.function.FunctionCommandContext.executeCommand(manager, sender, command.substring(1));
                } else delegate.execute(manager, sender, queue, limit);
            }
        }
        @Override public String toString() { return delegate.toString(); }
    }
}
