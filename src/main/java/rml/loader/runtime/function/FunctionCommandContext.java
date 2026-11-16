package rml.loader.runtime.function;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.advancements.FunctionManager;
import net.minecraft.nbt.NBTBase;
import rml.loader.runtime.function.FunctionDataPath;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Command sender carrying the entity, world and position selected by an execute chain. */
public final class FunctionCommandContext implements ICommandSender {
    private final ICommandSender delegate;
    private final Entity entity;
    private final World world;
    private final Vec3d position;
    private final NBTTagCompound arguments;
    private final float yaw;
    private final float pitch;
    private final FunctionReturnFrame returnFrame;

    public FunctionCommandContext(ICommandSender delegate, Entity entity, World world, BlockPos origin) {
        this(delegate, entity, world, origin == null ? delegate.getPositionVector() : new Vec3d(origin.getX(), origin.getY(), origin.getZ()),
                inheritedArguments(delegate), inheritedYaw(delegate, entity), inheritedPitch(delegate, entity), inheritedReturnFrame(delegate));
    }

    public FunctionCommandContext(ICommandSender delegate, Entity entity, World world, Vec3d position) {
        this(delegate, entity, world, position, inheritedArguments(delegate), inheritedYaw(delegate, entity), inheritedPitch(delegate, entity), inheritedReturnFrame(delegate));
    }

    private FunctionCommandContext(ICommandSender delegate, Entity entity, World world, Vec3d position, NBTTagCompound arguments,
                                   float yaw, float pitch, FunctionReturnFrame returnFrame) {
        this.delegate = delegate;
        this.entity = entity;
        this.world = world;
        this.position = position == null ? delegate.getPositionVector() : position;
        this.arguments = arguments == null ? null : (NBTTagCompound) arguments.copy();
        this.yaw = yaw;
        this.pitch = pitch;
        this.returnFrame = returnFrame;
    }

    public static ICommandSender withArguments(ICommandSender sender, NBTTagCompound arguments) {
        FunctionCommandContext context = sender instanceof FunctionCommandContext ? (FunctionCommandContext) sender : null;
        return new FunctionCommandContext(sender, sender.getCommandSenderEntity(), sender.getEntityWorld(), sender.getPositionVector(),
                arguments, context == null ? rotationYaw(sender.getCommandSenderEntity()) : context.yaw,
                context == null ? rotationPitch(sender.getCommandSenderEntity()) : context.pitch,
                context == null ? null : context.returnFrame);
    }

    public static ICommandSender withRotation(ICommandSender sender, float yaw, float pitch) {
        FunctionCommandContext context = sender instanceof FunctionCommandContext ? (FunctionCommandContext) sender : null;
        return new FunctionCommandContext(sender, sender.getCommandSenderEntity(), sender.getEntityWorld(), sender.getPositionVector(),
                inheritedArguments(sender), yaw, pitch, context == null ? null : context.returnFrame);
    }

    public static ICommandSender withReturnFrame(ICommandSender sender, FunctionReturnFrame frame) {
        FunctionCommandContext context = sender instanceof FunctionCommandContext ? (FunctionCommandContext) sender : null;
        return new FunctionCommandContext(sender, sender.getCommandSenderEntity(), sender.getEntityWorld(), sender.getPositionVector(),
                inheritedArguments(sender), context == null ? rotationYaw(sender.getCommandSenderEntity()) : context.yaw,
                context == null ? rotationPitch(sender.getCommandSenderEntity()) : context.pitch, frame);
    }

    public FunctionReturnFrame getReturnFrame() { return returnFrame; }

    public NBTBase getArgument(String path) { return arguments == null ? null : FunctionDataPath.get(arguments, path); }
    public boolean hasArguments() { return arguments != null; }
    private static FunctionReturnFrame inheritedReturnFrame(ICommandSender sender) { return sender instanceof FunctionCommandContext ? ((FunctionCommandContext) sender).returnFrame : null; }
    private static NBTTagCompound inheritedArguments(ICommandSender sender) { return sender instanceof FunctionCommandContext ? ((FunctionCommandContext) sender).arguments : null; }
    private static float inheritedYaw(ICommandSender sender, Entity entity) { return sender instanceof FunctionCommandContext ? ((FunctionCommandContext) sender).yaw : rotationYaw(entity); }
    private static float inheritedPitch(ICommandSender sender, Entity entity) { return sender instanceof FunctionCommandContext ? ((FunctionCommandContext) sender).pitch : rotationPitch(entity); }
    private static float rotationYaw(Entity entity) { return entity == null ? 0.0F : entity.rotationYaw; }
    private static float rotationPitch(Entity entity) { return entity == null ? 0.0F : entity.rotationPitch; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }


    public int executeCommand(FunctionManager manager, String command) {
        if (entity == null) return manager.getCommandManager().executeCommand(this, command);
        float oldYaw = entity.rotationYaw;
        float oldPitch = entity.rotationPitch;
        entity.rotationYaw = yaw;
        entity.rotationPitch = pitch;
        try { return manager.getCommandManager().executeCommand(this, command); }
        finally { entity.rotationYaw = oldYaw; entity.rotationPitch = oldPitch; }
    }

    public static int executeCommand(FunctionManager manager, ICommandSender sender, String command) {
        return sender instanceof FunctionCommandContext
                ? ((FunctionCommandContext) sender).executeCommand(manager, command)
                : manager.getCommandManager().executeCommand(sender, command);
    }

    @Override public String getName() { return entity == null ? delegate.getName() : entity.getName(); }
    @Override public boolean canUseCommand(int level, String command) { return delegate.canUseCommand(level, command); }
    @Override public World getEntityWorld() { return world == null ? delegate.getEntityWorld() : world; }
    @Override public MinecraftServer getServer() { return delegate.getServer(); }
    @Override public Entity getCommandSenderEntity() { return entity; }
    @Override public BlockPos getPosition() { return new BlockPos(position); }
    @Override public Vec3d getPositionVector() { return position; }
    @Override public boolean sendCommandFeedback() { return delegate.sendCommandFeedback(); }
    @Override public void sendMessage(net.minecraft.util.text.ITextComponent component) { delegate.sendMessage(component); }
    @Override public void setCommandStat(net.minecraft.command.CommandResultStats.Type type, int amount) { delegate.setCommandStat(type, amount); }
}
