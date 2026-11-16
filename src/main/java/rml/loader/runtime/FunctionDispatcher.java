package rml.loader.runtime;

import net.minecraft.advancements.FunctionManager;
import net.minecraft.command.FunctionObject;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;
import rml.loader.api.world.function.FunctionExecutor;
import rml.runtime.ResourceScope;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** One owned listener for a source file; no global sender or immortal executor instances. */
public final class FunctionDispatcher implements AutoCloseable {
    private final ResourceScope scope;
    private final FunctionExecutor[] executors;
    private final String source;
    private final Logger logger;
    private final Map<WorldServer, ICommandSender> senders = new IdentityHashMap<>();
    private final Set<ResourceLocation> missing = new HashSet<>();
    private boolean closed;

    FunctionDispatcher(ResourceScope scope, FunctionExecutor[] executors, String source, Logger logger) {
        this.scope = scope;
        this.executors = executors;
        this.source = source;
        this.logger = logger;
    }

    void register() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.world instanceof WorldServer) {
            dispatch((WorldServer) event.world, FunctionExecutor.Trigger.TICK);
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.getWorld() instanceof WorldServer) {
            dispatch((WorldServer) event.getWorld(), FunctionExecutor.Trigger.WORLD_LOAD);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        senders.remove(event.getWorld());
    }

    private void dispatch(WorldServer world, FunctionExecutor.Trigger trigger) {
        if (!scope.isActive() || closed) return;
        int dimension = world.provider.getDimension();
        long time = world.getTotalWorldTime();
        FunctionManager manager = world.getFunctionManager();
        for (FunctionExecutor executor : executors) {
            if (!scope.isActive() || closed) return;
            if (!executor.matches(trigger, dimension, time)) continue;
            ResourceLocation name = executor.getFunction();
            FunctionObject function = manager.getFunction(name);
            if (function == null) {
                if (missing.add(name)) logger.error("RML function {} referenced by {} is unavailable", name, source);
                continue;
            }
            missing.remove(name);
            ICommandSender sender = senders.get(world);
            if (sender == null) {
                sender = new FunctionCommandSender(world, source);
                senders.put(world, sender);
            }
            manager.execute(function, sender);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        MinecraftForge.EVENT_BUS.unregister(this);
        senders.clear();
        missing.clear();
    }

}
