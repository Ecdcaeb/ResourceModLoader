package rml.loader.runtime;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import rml.loader.content.data.DataContext;

/** Function authority and location derived from one explicit data evaluation. */
final class DataCommandSender implements ICommandSender {
    private final DataContext context;

    DataCommandSender(DataContext context) {
        if (context.getWorld() == null) throw new IllegalArgumentException("A data function requires a server world");
        this.context = context;
    }

    @Override public String getName() {
        return context.getEntity() == null ? "rml:data" : context.getEntity().getName();
    }

    @Override public ITextComponent getDisplayName() {
        return context.getEntity() == null ? new TextComponentString(getName()) : context.getEntity().getDisplayName();
    }

    @Override public boolean canUseCommand(int level, String command) { return level <= 2; }
    @Override public World getEntityWorld() { return context.getWorld(); }
    @Override public MinecraftServer getServer() { return context.getWorld().getMinecraftServer(); }
    @Override public Entity getCommandSenderEntity() { return context.getEntity(); }

    @Override public BlockPos getPosition() {
        BlockPos position = context.getOrigin();
        if (position == null) throw new IllegalStateException("This data function has no position in its loot context");
        return position;
    }

    @Override public Vec3d getPositionVector() {
        BlockPos position = getPosition();
        return new Vec3d(position.getX(), position.getY(), position.getZ());
    }
}
