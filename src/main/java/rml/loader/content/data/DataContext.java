package rml.loader.content.data;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;
import java.util.Random;

/** Explicit native data evaluation context. Missing origin remains missing, never a fabricated position. */
public final class DataContext {
    public interface LootOrigin { BlockPos getLootOrigin(); }
    public interface LootTool { net.minecraft.item.ItemStack getLootTool(); }
    public interface LootData { DataContext getDataContext(); }
    private static final java.lang.reflect.Field LOOT_DAMAGE = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
            LootContext.class, new String[]{"damageSource", "field_186503_f"});
    private final WorldServer world;
    private final Entity entity;
    private final BlockPos origin;
    private final Random random;
    private final int level;
    private final LootContext lootContext;
    private final NBTTagCompound inputs;
    private final net.minecraft.util.DamageSource damageSource;
    private final net.minecraft.item.ItemStack tool;
    private LootContext nativeView;

    public DataContext(WorldServer world, Entity entity, BlockPos origin, Random random, int level) {
        this(world, entity, origin, random, level, null, null, null, net.minecraft.item.ItemStack.EMPTY);
    }

    private DataContext(WorldServer world, Entity entity, BlockPos origin, Random random, int level, LootContext lootContext,
                        NBTTagCompound inputs, net.minecraft.util.DamageSource damageSource, net.minecraft.item.ItemStack tool) {
        this.world = world;
        this.entity = entity;
        this.origin = origin == null ? null : origin.toImmutable();
        this.random = Objects.requireNonNull(random, "random");
        this.level = level;
        this.lootContext = lootContext;
        this.inputs = inputs;
        this.damageSource = damageSource;
        this.tool = tool;
    }

    public WorldServer getWorld() { return world; }
    public Entity getEntity() { return entity; }
    public BlockPos getOrigin() { return origin; }
    public Random getRandom() { return random; }
    public int getLevel() { return level; }
    public LootContext getLootContext() { return lootContext; }
    public LootContext asLootContext() {
        if (nativeView == null) nativeView = new PositionedLootContext(this);
        return nativeView;
    }
    public net.minecraft.util.DamageSource getDamageSource() { return damageSource; }
    public net.minecraft.item.ItemStack getTool() { return tool; }

    public DataContext withDamageSource(net.minecraft.util.DamageSource damageSource) {
        return new DataContext(world, entity, origin, random, level, lootContext, inputs, damageSource, tool);
    }

    public DataContext withTool(net.minecraft.item.ItemStack tool) {
        return new DataContext(world, entity, origin, random, level, lootContext, inputs, damageSource, Objects.requireNonNull(tool, "tool"));
    }

    public NBTTagCompound getInputs() { return inputs == null ? null : inputs.copy(); }

    /** Read-only view for compiled expressions; callers must copy before modifying a returned tag. */
    public net.minecraft.nbt.NBTBase getInput(rml.loader.runtime.function.FunctionDataPath.Compiled path) {
        return inputs == null ? null : path.get(inputs);
    }

    public DataContext withInputs(NBTTagCompound inputs) {
        return new DataContext(world, entity, origin, random, level, lootContext,
                Objects.requireNonNull(inputs, "inputs").copy(), damageSource, tool);
    }

    public DataContext withEntity(Entity entity) {
        return new DataContext(world, entity, entity == null ? origin : entity.getPosition(), random, level, lootContext, inputs, damageSource, tool);
    }

    public DataContext withLevel(int level) { return new DataContext(world, entity, origin, random, level, lootContext, inputs, damageSource, tool); }

    public static DataContext fromLoot(LootContext context, Random random) {
        DataContext original = context instanceof LootData ? ((LootData) context).getDataContext() : null;
        if (original != null) {
            DataContext result = new DataContext(original.world, original.entity, original.origin, random, original.level,
                    context, original.inputs, original.damageSource, original.tool);
            result.nativeView = context;
            return result;
        }
        Entity entity = context.getLootedEntity();
        BlockPos origin = context instanceof LootOrigin ? ((LootOrigin) context).getLootOrigin() : entity == null ? null : entity.getPosition();
        Entity killer = context.getKiller();
        net.minecraft.item.ItemStack tool = context instanceof LootTool ? ((LootTool) context).getLootTool()
                : killer instanceof net.minecraft.entity.EntityLivingBase
                ? ((net.minecraft.entity.EntityLivingBase) killer).getHeldItemMainhand() : net.minecraft.item.ItemStack.EMPTY;
        try {
            return new DataContext(context.getWorld(), entity, origin, random, 0, context, null,
                    (net.minecraft.util.DamageSource) LOOT_DAMAGE.get(context), tool);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot read native loot damage source", failure);
        }
    }
}
