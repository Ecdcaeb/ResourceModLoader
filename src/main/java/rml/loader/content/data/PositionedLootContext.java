package rml.loader.content.data;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;

/** Native loot adapter carrying the position supplied by an actual command or world hook. */
public final class PositionedLootContext extends LootContext implements DataContext.LootOrigin, DataContext.LootTool, DataContext.LootData {
    private final BlockPos origin;
    private final DataContext dataContext;

    public PositionedLootContext(WorldServer world, Entity entity, BlockPos origin) {
        super(entity instanceof EntityPlayer ? ((EntityPlayer) entity).getLuck() : 0.0F, world, world.getLootTableManager(),
                entity, entity instanceof EntityPlayer ? (EntityPlayer) entity : null, null);
        this.origin = origin == null ? null : origin.toImmutable();
        this.dataContext = null;
    }

    public PositionedLootContext(DataContext context) {
        super(luck(context), context.getWorld(), context.getWorld() == null ? null : context.getWorld().getLootTableManager(),
                context.getEntity(), killerPlayer(context), context.getDamageSource());
        origin = context.getOrigin();
        dataContext = context;
    }

    private static float luck(DataContext context) {
        if (context.getLootContext() != null) return context.getLootContext().getLuck();
        return context.getEntity() instanceof EntityPlayer ? ((EntityPlayer) context.getEntity()).getLuck() : 0.0F;
    }

    private static EntityPlayer killerPlayer(DataContext context) {
        Entity killer = context.getLootContext() != null ? context.getLootContext().getKillerPlayer()
                : context.getDamageSource() == null ? null : context.getDamageSource().getTrueSource();
        return killer instanceof EntityPlayer ? (EntityPlayer) killer : null;
    }

    @Override public DataContext getDataContext() { return dataContext; }

    @Override public BlockPos getLootOrigin() { return origin; }
    @Override public net.minecraft.item.ItemStack getLootTool() {
        if (dataContext != null) return dataContext.getTool();
        Entity entity = getLootedEntity();
        return entity instanceof net.minecraft.entity.EntityLivingBase
                ? ((net.minecraft.entity.EntityLivingBase) entity).getHeldItemMainhand() : net.minecraft.item.ItemStack.EMPTY;
    }
}
