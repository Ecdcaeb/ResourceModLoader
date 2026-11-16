package rml.loader.content.gameplay;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import rml.loader.content.data.DataContext;
import java.util.Map;
import java.util.WeakHashMap;

/** Native consumers shared by Forge handlers and the ItemStack durability hook. */
final class NativeEnchantmentRuntime {
    private final Iterable<Enchantment> enchantments;
    private final Map<EntityLivingBase, LocationState> locations = new WeakHashMap<>();
    private static final EntityEquipmentSlot[] EQUIPMENT = EntityEquipmentSlot.values();
    private NativeEnchantment[] locationEnchantments = new NativeEnchantment[0];
    private AttributeBinding[] attributeBindings = new AttributeBinding[0];
    private static final class AttributeBinding {
        final NativeEnchantment enchantment;
        final EnchantmentAttribute effect;
        final EntityEquipmentSlot slot;
        final java.util.UUID uuid;
        AttributeBinding(NativeEnchantment enchantment, EnchantmentAttribute effect, EntityEquipmentSlot slot) {
            this.enchantment = enchantment; this.effect = effect; this.slot = slot; this.uuid = effect.uuid(enchantment.getRegistryName(), slot);
        }
    }
    private static final class LocationState {
        long position;
        final int[] levels;
        LocationState(int slots) { levels = new int[slots]; }
    }
    private final ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);
    NativeEnchantmentRuntime(Iterable<Enchantment> enchantments) { this.enchantments = enchantments; }
    void resolveComponents() {
        java.util.List<NativeEnchantment> locations = new java.util.ArrayList<>();
        for (Enchantment entry : enchantments) if (entry instanceof NativeEnchantment && ((NativeEnchantment) entry).hasEntity("location_changed")) locations.add((NativeEnchantment) entry);
        locationEnchantments = locations.toArray(new NativeEnchantment[0]);
        java.util.List<AttributeBinding> attributes = new java.util.ArrayList<>();
        for (Enchantment entry : enchantments) if (entry instanceof NativeEnchantment) {
            NativeEnchantment enchantment = (NativeEnchantment) entry;
            for (EnchantmentAttribute effect : enchantment.attributes()) for (EntityEquipmentSlot slot : enchantment.activeSlots) attributes.add(new AttributeBinding(enchantment, effect, slot));
        }
        attributeBindings = attributes.toArray(new AttributeBinding[0]);
    }
    void updateAttributes(EntityLivingBase owner, boolean remove) {
        for (AttributeBinding binding : attributeBindings) {
            ItemStack stack = owner.getItemStackFromSlot(binding.slot);
            int level = remove ? 0 : EnchantmentHelper.getEnchantmentLevel(binding.enchantment, stack);
            binding.effect.reconcile(owner.getAttributeMap(), binding.uuid, level == 0 ? null : context(owner, level).withTool(stack), level > 0);
        }
        if (owner.getHealth() > owner.getMaxHealth()) owner.setHealth(owner.getMaxHealth());
    }
    boolean hasEnchantment(ItemStack stack) {
        for (Enchantment entry : enchantments) if (EnchantmentHelper.getEnchantmentLevel(entry, stack) > 0) return true;
        return false;
    }
    float modifyStack(String component, float amount, EntityLivingBase owner, ItemStack stack, EntityEquipmentSlot slot) {
        for (Enchantment entry : enchantments) if (entry instanceof NativeEnchantment) {
            NativeEnchantment enchantment = (NativeEnchantment) entry;
            if (!enchantment.hasValue(component)) continue;
            int level = EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
            if (level <= 0) continue;
            for (EntityEquipmentSlot active : enchantment.activeSlots) if (active == slot) { amount = enchantment.modify(component, amount, context(owner, level).withTool(stack)); break; }
        }
        return amount;
    }
    void projectileSpawned(EntityLivingBase owner, ItemStack weapon, EntityEquipmentSlot slot, Entity projectile) {
        if (applying.get()) return;
        applying.set(true);
        try {
            for (Enchantment entry : enchantments) if (entry instanceof NativeEnchantment) {
                NativeEnchantment enchantment = (NativeEnchantment) entry;
                int level = EnchantmentHelper.getEnchantmentLevel(enchantment, weapon);
                if (level <= 0 || !enchantment.hasEntity("projectile_spawned")) continue;
                for (EntityEquipmentSlot active : enchantment.activeSlots) if (active == slot) {
                    enchantment.execute("projectile_spawned", context(owner, level).withTool(owner.getItemStackFromSlot(slot)), projectile, owner, projectile);
                    break;
                }
            }
        } finally { applying.set(false); }
    }
    private static DataContext context(EntityLivingBase owner, int level) {
        return new DataContext(owner.world instanceof WorldServer ? (WorldServer) owner.world : null, owner, owner.getPosition(), owner.getRNG(), level);
    }
    private static int level(NativeEnchantment enchantment, EntityLivingBase owner, EntityEquipmentSlot slot) {
        ItemStack stack = owner.getItemStackFromSlot(slot);
        return stack.isEmpty() ? 0 : EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
    }
    float modify(String component, float amount, EntityLivingBase owner, net.minecraft.util.DamageSource source) {
        if (applying.get()) return amount;
        EnchantmentProjectiles.Snapshot shot = "damage".equals(component) ? EnchantmentProjectiles.snapshot(source) : null;
        for (Enchantment entry : enchantments) if (entry instanceof NativeEnchantment) {
            NativeEnchantment enchantment = (NativeEnchantment) entry;
            if (!enchantment.hasValue(component)) continue;
            for (EntityEquipmentSlot slot : enchantment.activeSlots) {
                ItemStack stack = shot != null && shot.slot == slot ? shot.weapon : owner.getItemStackFromSlot(slot);
                int level = EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
                if (level > 0) amount = enchantment.modify(component, amount, context(owner, level).withTool(stack).withDamageSource(source));
            }
        }
        return amount;
    }
    void execute(String component, EntityLivingBase owner, Entity attacker, Entity victim, net.minecraft.util.DamageSource source) {
        if (applying.get()) return;
        EnchantmentProjectiles.Snapshot shot = "post_attack".equals(component) ? EnchantmentProjectiles.snapshot(source) : null;
        applying.set(true);
        try {
            for (Enchantment entry : enchantments) if (entry instanceof NativeEnchantment) {
                NativeEnchantment enchantment = (NativeEnchantment) entry;
                if (!enchantment.hasEntity(component)) continue;
                for (EntityEquipmentSlot slot : enchantment.activeSlots) {
                    ItemStack stack = shot != null && shot.slot == slot ? shot.weapon : owner.getItemStackFromSlot(slot);
                    int level = EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
                    ItemStack tool = shot != null && shot.slot == slot ? shot.liveTool : stack;
                    if (level > 0) enchantment.execute(component, context(owner, level).withTool(tool).withDamageSource(source), owner, attacker, victim);
                }
            }
        } finally { applying.set(false); }
    }
    void tick(EntityLivingBase entity) {
        updateAttributes(entity, !entity.isEntityAlive());
        execute("tick", entity, entity, entity, null);
        if (locationEnchantments.length == 0) return;
        LocationState previous = locations.get(entity);
        boolean active = false, changed = false;
        for (int index = 0; index < locationEnchantments.length; index++) {
            NativeEnchantment enchantment = locationEnchantments[index];
            for (EntityEquipmentSlot slot : enchantment.activeSlots) {
                int level = level(enchantment, entity, slot);
                if (level > 0) {
                    active = true;
                    if (previous == null) {
                        previous = new LocationState(locationEnchantments.length * EQUIPMENT.length);
                        locations.put(entity, previous);
                        changed = true;
                    }
                }
                int position = index * EQUIPMENT.length + slot.ordinal();
                if (previous != null && previous.levels[position] != level) { previous.levels[position] = level; changed = true; }
            }
        }
        if (!active) { locations.remove(entity); return; }
        long position = entity.getPosition().toLong();
        if (changed || previous.position != position) {
            previous.position = position;
            execute("location_changed", entity, entity, entity, null);
        }
    }
    int itemDamage(ItemStack stack, int amount, EntityLivingBase entity) {
        if (amount <= 0 || stack.isEmpty() || entity == null || entity.world.isRemote) return amount;
        float result = amount;
        for (Enchantment entry : enchantments) if (entry instanceof NativeEnchantment) {
            NativeEnchantment enchantment = (NativeEnchantment) entry;
            if (!enchantment.hasValue("item_damage")) continue;
            int level = EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
            if (level <= 0) continue;
            for (EntityEquipmentSlot slot : enchantment.activeSlots) if (entity.getItemStackFromSlot(slot) == stack) {
                result = enchantment.modify("item_damage", result, context(entity, level).withTool(stack));
                break;
            }
        }
        return Math.max(0, Math.round(result));
    }
    void pickup(EntityPlayer player, EntityXPOrb orb) {
        if (player.world.isRemote || orb.xpValue <= 0) return;
        orb.xpValue = Math.max(0, (int) modify("experience", orb.xpValue, player, null));
        // Reservoir selection gives each eligible equipment stack one equal chance, even with several repair enchantments.
        ItemStack chosen = ItemStack.EMPTY;
        int candidates = 0;
        float ratio = 0;
        for (EntityEquipmentSlot slot : EQUIPMENT) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (!stack.isItemDamaged()) continue;
            float candidateRatio = repairRatio(player, stack, slot);
            if (candidateRatio > 0 && player.getRNG().nextInt(++candidates) == 0) { chosen = stack; ratio = candidateRatio; }
        }
        if (chosen.isEmpty()) return;
        orb.xpValue = repairWithExperience(chosen, orb.xpValue, ratio);
        player.inventory.markDirty();
    }
    static int repairWithExperience(ItemStack stack, int experience, float ratio) {
        if (!Float.isFinite(ratio) || ratio <= 0) throw new IllegalArgumentException("Repair ratio must be finite and positive");
        if (experience <= 0 || !stack.isItemDamaged()) return experience;
        int repaired = Math.min(stack.getItemDamage(), (int) Math.min(Integer.MAX_VALUE, experience * (double) ratio));
        if (repaired <= 0) return experience;
        stack.setItemDamage(stack.getItemDamage() - repaired);
        return Math.max(0, experience - (int) Math.ceil(repaired / (double) ratio));
    }
    private float repairRatio(EntityPlayer player, ItemStack stack, EntityEquipmentSlot slot) {
        boolean found = false;
        float ratio = 2;
        for (Enchantment entry : enchantments) if (entry instanceof NativeEnchantment) {
            NativeEnchantment enchantment = (NativeEnchantment) entry;
            if (!enchantment.hasValue("repair_with_xp")) continue;
            int level = EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
            if (level <= 0) continue;
            for (EntityEquipmentSlot active : enchantment.activeSlots) if (active == slot) {
                float modified = enchantment.repairRatio(ratio, context(player, level).withTool(stack));
                if (!Float.isNaN(modified)) { ratio = modified; found = true; }
                break;
            }
        }
        return found ? Math.max(0, ratio) : 0;
    }
}
