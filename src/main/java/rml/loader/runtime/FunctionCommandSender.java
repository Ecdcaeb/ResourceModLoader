package rml.loader.runtime;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Immutable execution context shared by scheduled and event-driven functions. */
final class FunctionCommandSender implements ICommandSender {
    private final WorldServer world;
    private final String name;

    FunctionCommandSender(WorldServer world, String name) {
        this.world = world;
        this.name = "rml:" + name;
    }

    @Override public String getName() { return name; }
    @Override public boolean canUseCommand(int level, String command) { return level <= 2; }
    @Override public World getEntityWorld() { return world; }
    @Override public MinecraftServer getServer() { return world.getMinecraftServer(); }
}
