package rml.loader.content.gameplay;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import java.util.Map;
import java.util.WeakHashMap;

/** Paired native bow launch/ammunition consumers. A shot samples its ammunition cost exactly once. */
final class EnchantmentProjectiles {
    private static final String WEAPON = "rml_projectile_weapon", SLOT = "rml_projectile_slot";
    private static final Map<Entity, Snapshot> SNAPSHOTS = new WeakHashMap<>();
    private final Map<ItemStack, Pending> pending = new WeakHashMap<>();
    private final NativeEnchantmentRuntime runtime;
    EnchantmentProjectiles(NativeEnchantmentRuntime runtime) { this.runtime = runtime; }
    static final class Snapshot {
        final ItemStack weapon, liveTool;
        final EntityEquipmentSlot slot;
        Snapshot(ItemStack weapon, EntityEquipmentSlot slot, ItemStack liveTool) { this.weapon = weapon; this.slot = slot; this.liveTool = liveTool; }
    }
    private static final class Pending {
        final ItemStack ammunition;
        final int consumption;
        boolean launched;
        Pending(ItemStack ammunition, int consumption) { this.ammunition = ammunition; this.consumption = consumption; }
    }
    static Snapshot snapshot(DamageSource source) {
        if (source == null || source.getImmediateSource() == null || source.getImmediateSource() == source.getTrueSource()) return null;
        Entity projectile = source.getImmediateSource();
        Snapshot snapshot = SNAPSHOTS.get(projectile);
        if (snapshot == null && projectile.getEntityData().hasKey(WEAPON, 10)) {
            snapshot = new Snapshot(new ItemStack(projectile.getEntityData().getCompoundTag(WEAPON)), EntityEquipmentSlot.fromString(projectile.getEntityData().getString(SLOT)), ItemStack.EMPTY);
            SNAPSHOTS.put(projectile, snapshot);
        }
        return snapshot;
    }
    boolean spawn(World world, Entity projectile, EntityLivingBase shooter, ItemStack weapon, ItemStack ammunition) {
        if (world.isRemote || !runtime.hasEnchantment(weapon)) return world.spawnEntity(projectile);
        EntityEquipmentSlot slot = shooter.getActiveHand() == EnumHand.OFF_HAND ? EntityEquipmentSlot.OFFHAND : EntityEquipmentSlot.MAINHAND;
        boolean free = shooter instanceof EntityPlayer && ((EntityPlayer) shooter).capabilities.isCreativeMode
                || projectile instanceof EntityArrow && ((EntityArrow) projectile).pickupStatus == EntityArrow.PickupStatus.CREATIVE_ONLY;
        int cost = free ? 0 : Math.max(0, Math.round(runtime.modifyStack("ammunition_use", 1, shooter, weapon, slot)));
        Pending shot = new Pending(ammunition, cost);
        if (!free) pending.put(weapon, shot);
        if (!free && cost > ammunition.getCount()) return false;
        int count = Math.max(0, Math.round(runtime.modifyStack("projectile_count", 1, shooter, weapon, slot)));
        if (count == 0) return false;
        float spread = runtime.modifyStack("projectile_spread", 0, shooter, weapon, slot);
        double x = projectile.motionX, y = projectile.motionY, z = projectile.motionZ;
        double originalSpeed = Math.sqrt(x * x + y * y + z * z);
        double speed = Math.max(0, runtime.modifyStack("projectile_speed", (float) originalSpeed, shooter, weapon, slot));
        if (originalSpeed == 0) { net.minecraft.util.math.Vec3d look = shooter.getLookVec(); x = look.x; y = look.y; z = look.z; originalSpeed = 1; }
        double scale = speed / originalSpeed;
        Entity[] projectiles = new Entity[count];
        projectiles[0] = projectile;
        if (count > 1) {
            ResourceLocation type = EntityList.getKey(projectile);
            if (type == null) throw new IllegalArgumentException("Cannot duplicate an unregistered projectile class: " + projectile.getClass().getName());
            NBTTagCompound data = projectile.writeToNBT(new NBTTagCompound());
            data.setString("id", type.toString());
            data.removeTag("UUIDMost"); data.removeTag("UUIDLeast");
            for (int index = 1; index < count; index++) {
                Entity child = EntityList.createEntityFromNBT(data.copy(), world);
                if (child == null) throw new IllegalStateException("Projectile factory failed: " + type);
                if (child instanceof EntityArrow) ((EntityArrow) child).shootingEntity = shooter;
                projectiles[index] = child;
            }
        }
        // All factories succeed before any entities are added to the world.
        NBTTagCompound savedWeapon = weapon.writeToNBT(new NBTTagCompound());
        Snapshot snapshot = new Snapshot(weapon, slot, shooter.getItemStackFromSlot(slot));
        for (int index = 0; index < count; index++) {
            Entity child = projectiles[index];
            double angle = Math.toRadians(spread * (index - (count - 1) / 2.0));
            child.motionX = (x * Math.cos(angle) - z * Math.sin(angle)) * scale;
            child.motionY = y * scale;
            child.motionZ = (x * Math.sin(angle) + z * Math.cos(angle)) * scale;
            child.rotationYaw = child.prevRotationYaw = (float) (Math.atan2(child.motionX, child.motionZ) * 180 / Math.PI);
            child.rotationPitch = child.prevRotationPitch = (float) (Math.atan2(child.motionY, Math.sqrt(child.motionX * child.motionX + child.motionZ * child.motionZ)) * 180 / Math.PI);
            if (child instanceof EntityArrow && (free || cost == 0 || index != 0)) ((EntityArrow) child).pickupStatus = EntityArrow.PickupStatus.CREATIVE_ONLY;
            child.getEntityData().setTag(WEAPON, savedWeapon.copy());
            child.getEntityData().setString(SLOT, slot.getName());
            if (world.spawnEntity(child)) {
                shot.launched = true;
                SNAPSHOTS.put(child, snapshot);
                runtime.projectileSpawned(shooter, weapon, slot, child);
            }
        }
        return shot.launched;
    }
    void consume(ItemStack ammunition, int amount, ItemStack weapon, EntityLivingBase shooter) {
        if (shooter.world.isRemote) { ammunition.shrink(amount); return; }
        Pending shot = pending.remove(weapon);
        if (shot == null) {
            if (!runtime.hasEnchantment(weapon)) { ammunition.shrink(amount); return; }
            throw new IllegalStateException("Enchanted ammunition consumption requires its paired native launch");
        }
        if (shot.ammunition != ammunition) throw new IllegalStateException("Native launch/consumption ammunition mismatch");
        if (shot.launched) ammunition.shrink(shot.consumption);
    }
}
