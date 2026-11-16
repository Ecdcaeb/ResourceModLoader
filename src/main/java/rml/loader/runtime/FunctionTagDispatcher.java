package rml.loader.runtime;

import net.minecraft.advancements.FunctionManager;
import net.minecraft.command.FunctionObject;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import rml.loader.ResourceModLoader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import rml.runtime.ResourceScope;

import java.util.function.Supplier;

/** Load runs once per committed generation, before ordinary tick tags and scheduled jobs. */
public final class FunctionTagDispatcher implements AutoCloseable {
    private final ResourceScope scope;
    private final FunctionManager manager;
    private final Supplier<WorldServer> world;
    private final ResourceLocation tag;
    private final ResourceLocation wrapper;
    private boolean pending = true;
    private WorldServer contextWorld;
    private ICommandSender sender;

    FunctionTagDispatcher(ResourceScope scope, FunctionManager manager, Supplier<WorldServer> world,
                          ResourceLocation tag, ResourceLocation wrapper) {
        this.scope = scope;
        this.manager = manager;
        this.world = world;
        this.tag = tag;
        this.wrapper = wrapper;
    }

    void register() {
        Loader loader = Loader.instance();
        ModContainer previous = loader.activeModContainer();
        loader.setActiveModContainer(loader.getIndexedModList().get(ResourceModLoader.MODID));
        try {
            MinecraftForge.EVENT_BUS.register(this);
        } finally {
            loader.setActiveModContainer(previous);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLoad(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && pending && "minecraft:load".equals(tag.toString())) {
            if (execute()) pending = false;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && "minecraft:tick".equals(tag.toString())) execute();
    }

    private boolean execute() {
        if (!scope.isActive()) return false;
        WorldServer currentWorld = world.get();
        if (currentWorld == null) return false;
        if (contextWorld != currentWorld) {
            contextWorld = currentWorld;
            sender = new FunctionCommandSender(currentWorld, tag.toString());
        }
        FunctionObject function = manager.getFunction(wrapper);
        if (function == null) throw new IllegalStateException("Missing compiled function tag wrapper: " + wrapper);
        manager.execute(function, sender);
        return true;
    }

    @Override public void close() {
        MinecraftForge.EVENT_BUS.unregister(this);
        contextWorld = null;
        sender = null;
    }
}
