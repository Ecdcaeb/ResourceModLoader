package rml.loader.content.data;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;

/** Native loot adapter carrying the position supplied by an actual command or world hook. */
public final class PositionedLootContext extends LootContext implements DataContext.LootOrigin {
    private final BlockPos origin;

    public PositionedLootContext(WorldServer world, Entity entity, BlockPos origin) {
        super(entity instanceof EntityPlayer ? ((EntityPlayer) entity).getLuck() : 0.0F, world, world.getLootTableManager(),
                entity, entity instanceof EntityPlayer ? (EntityPlayer) entity : null, null);
        this.origin = origin.toImmutable();
    }

    @Override public BlockPos getLootOrigin() { return origin; }
}
