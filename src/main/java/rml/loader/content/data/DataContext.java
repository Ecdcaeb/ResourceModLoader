package rml.loader.content.data;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;

import java.util.Objects;
import java.util.Random;

/** Explicit native data evaluation context. Missing origin remains missing, never a fabricated position. */
public final class DataContext {
    public interface LootOrigin { BlockPos getLootOrigin(); }
    private final WorldServer world;
    private final Entity entity;
    private final BlockPos origin;
    private final Random random;
    private final int level;
    private final LootContext lootContext;

    public DataContext(WorldServer world, Entity entity, BlockPos origin, Random random, int level) {
        this(world, entity, origin, random, level, null);
    }

    private DataContext(WorldServer world, Entity entity, BlockPos origin, Random random, int level, LootContext lootContext) {
        this.world = world;
        this.entity = entity;
        this.origin = origin == null ? null : origin.toImmutable();
        this.random = Objects.requireNonNull(random, "random");
        this.level = level;
        this.lootContext = lootContext;
    }

    public WorldServer getWorld() { return world; }
    public Entity getEntity() { return entity; }
    public BlockPos getOrigin() { return origin; }
    public Random getRandom() { return random; }
    public int getLevel() { return level; }
    public LootContext getLootContext() { return lootContext; }

    public DataContext withEntity(Entity entity) {
        return new DataContext(world, entity, entity == null ? origin : entity.getPosition(), random, level, lootContext);
    }

    public DataContext withLevel(int level) { return new DataContext(world, entity, origin, random, level, lootContext); }

    public static DataContext fromLoot(LootContext context, Random random) {
        Entity entity = context.getLootedEntity();
        BlockPos origin = context instanceof LootOrigin ? ((LootOrigin) context).getLootOrigin() : entity == null ? null : entity.getPosition();
        return new DataContext(context.getWorld(), entity, origin, random, 0, context);
    }
}
