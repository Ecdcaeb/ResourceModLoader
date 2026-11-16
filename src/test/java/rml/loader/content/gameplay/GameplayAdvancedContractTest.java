package rml.loader.content.gameplay;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Live native proof. Install the four rml_regression resources before starting Forge. */
public final class GameplayAdvancedContractTest {
    private GameplayAdvancedContractTest() { }
    public static void verifyInWorld(WorldServer world, EntityPlayerMP player) throws Exception {
        check(player.world == world && !player.isHandActive() && !player.isRiding(), "Proof requires an idle, unmounted player in the supplied world");
        Enchantment stride = enchantment("attribute_stride"), multishot = enchantment("multishot"), mending = enchantment("mending");
        BlockPos center = new BlockPos(player.posX + 32, 240, player.posZ + 32);
        AxisAlignedBB area = new AxisAlignedBB(center).grow(12);
        for (BlockPos position : BlockPos.getAllInBox(center.add(-4, 0, -4), center.add(4, 4, 4))) check(world.isAirBlock(position), "Proof platform must be empty air: " + position);
        for (net.minecraft.entity.player.EntityPlayer other : world.playerEntities) if (other != player) check(other.getDistanceSq(center) > 144, "Another player is inside the proof area");
        WorldServer otherWorld = world.getMinecraftServer().getWorld(world.provider.getDimension() == -1 ? 0 : -1);
        check(otherWorld != null && otherWorld != world, "Cross-dimension proof requires a second native server dimension");
        BlockPos otherCenter = new BlockPos(center.getX(), 240, center.getZ());
        AxisAlignedBB otherArea = new AxisAlignedBB(otherCenter).grow(12);
        Set<UUID> originalEntities = entityIds(world, area), otherEntities = entityIds(otherWorld, otherArea);
        NBTTagCompound savedPlayer = player.writeToNBT(new NBTTagCompound());
        float yaw = player.rotationYaw, pitch = player.rotationPitch;
        boolean sneaking = player.isSneaking(), sprinting = player.isSprinting();
        int xpCooldown = player.xpCooldown;
        StatBase bowUses = StatList.getObjectUseStats(Items.BOW), bowBreaks = StatList.getObjectBreakStats(Items.BOW);
        int uses = player.getStatFile().readStat(bowUses), breaks = player.getStatFile().readStat(bowBreaks);
        List<BlockPos> placed = new ArrayList<>();
        try {
            for (BlockPos position : BlockPos.getAllInBox(center.add(-4, 0, -4), center.add(4, 0, 4))) {
                BlockPos copy = position.toImmutable(); placed.add(copy); world.setBlockState(copy, Blocks.STONE.getDefaultState(), 3);
            }
            player.inventory.clear(); player.inventory.currentItem = 0;
            player.clearActivePotions(); player.extinguish(); player.setSneaking(false); player.setSprinting(false);
            player.capabilities.isCreativeMode = false; player.capabilities.disableDamage = true;
            player.motionX = player.motionY = player.motionZ = 0;
            player.connection.setPlayerLocation(center.getX() + 3.5, center.getY() + 1, center.getZ() + 0.5, 0, 0);
            player.onUpdateEntity();
            IAttributeInstance speed = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED);
            double baseline = speed.getAttributeValue();
            player.setItemStackToSlot(EntityEquipmentSlot.FEET, enchanted(Items.DIAMOND_BOOTS, stride, 1));
            player.onUpdateEntity();
            check(Math.abs(speed.getAttributeValue() - baseline) < 0.00001, "False attribute requirements must not apply modifiers");
            player.setItemStackToSlot(EntityEquipmentSlot.FEET, enchanted(Items.DIAMOND_BOOTS, stride, 2));
            player.onUpdateEntity();
            check(Math.abs(speed.getAttributeValue() - baseline - 0.2) < 0.00001, "Equipped conditional enchantment must change the actual native movement attribute");
            player.setItemStackToSlot(EntityEquipmentSlot.FEET, ItemStack.EMPTY);
            player.onUpdateEntity();
            check(Math.abs(speed.getAttributeValue() - baseline) < 0.00001, "Unequipping must remove the native attribute modifier");

            ItemStack bow = enchanted(Items.BOW, multishot, 1);
            bow.setItemDamage(bow.getMaxDamage()); // 1.12 breaks a stack only when damage exceeds its maximum.
            player.inventory.setInventorySlotContents(0, bow);
            ItemStack ammunition = new ItemStack(Items.ARROW, 5);
            player.inventory.setInventorySlotContents(1, ammunition);
            shoot(player, world, bow);
            List<EntityArrow> arrows = arrows(world, area, player);
            check(arrows.size() == 3, "Native last-durability bow shot must launch three arrows; got " + arrows.size());
            check(ammunition.getCount() == 3, "Native last-durability bow shot must consume the sampled two-arrow cost; remaining " + ammunition.getCount());
            check(bow.isEmpty(), "The final native bow shot must break the bow");
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            int recoverable = 0;
            for (EntityArrow arrow : arrows) {
                minX = Math.min(minX, arrow.motionX); maxX = Math.max(maxX, arrow.motionX);
                check(arrow.isBurning(), "projectile_spawned effects must mutate the actual native arrow");
                check(Math.sqrt(arrow.motionX * arrow.motionX + arrow.motionY * arrow.motionY + arrow.motionZ * arrow.motionZ) > 4, "Projectile speed effects must reach native velocity");
                if (arrow.pickupStatus == EntityArrow.PickupStatus.ALLOWED) recoverable++;
                world.removeEntityDangerously(arrow);
            }
            check(maxX - minX > 1 && recoverable == 1, "Spread must create distinct trajectories without duplicating recoverable ammunition");
            bow = enchanted(Items.BOW, multishot, 1);
            player.inventory.setInventorySlotContents(0, bow);
            ammunition = new ItemStack(Items.ARROW, 1); player.inventory.setInventorySlotContents(1, ammunition);
            shoot(player, world, bow);
            check(arrows(world, area, player).isEmpty() && ammunition.getCount() == 1, "Insufficient authored ammunition must launch nothing and consume nothing");
            bow.addEnchantment(Enchantments.INFINITY, 1);
            shoot(player, world, bow);
            arrows = arrows(world, area, player);
            check(arrows.size() == 3 && ammunition.getCount() == 1, "Native Infinity gating must remain effective with authored multishot");
            for (EntityArrow arrow : arrows) { check(arrow.pickupStatus == EntityArrow.PickupStatus.CREATIVE_ONLY, "Free arrows must not become recoverable ammunition"); world.removeEntityDangerously(arrow); }

            player.inventory.clear();
            ItemStack tool = enchanted(Items.DIAMOND_PICKAXE, mending, 1); tool.setItemDamage(7);
            player.inventory.setInventorySlotContents(0, tool);
            EntityXPOrb orb = new EntityXPOrb(world, player.posX, player.posY, player.posZ, 2);
            check(world.spawnEntity(orb), "XP orb must enter the native world");
            player.xpCooldown = 0; orb.delayBeforeCanPickup = 0;
            orb.onCollideWithPlayer(player); // Native pickup invokes Forge and vanilla repair/XP consumers itself.
            check(tool.getItemDamage() == 3 && orb.isDead && orb.xpValue == 0, "Native XP pickup must repair four durability and spend two XP");
            world.removeEntityDangerously(orb);

            world.setBlockState(center, TrialSpawnerBlock.INSTANCE.getDefaultState(), 3);
            TrialSpawnerTileEntity trial = (TrialSpawnerTileEntity) world.getTileEntity(center);
            check(trial != null, "Native trial block must create its registered tile");
            trial.setDefinition(new ResourceLocation("rml_regression", "trial_ominous"));
            trial.update();
            check(trial.getParticipantCount() == 1 && trial.getTargetCount() == 3 && !trial.isOminous(), "Actual player detection must apply normal participant scaling");
            List<EntityLiving> mobs = trialMobs(world, area);
            check(mobs.size() == 1, "Normal wave must create its configured living mob");
            EntityLiving moving = mobs.get(0);
            UUID movingId = moving.getUniqueID();
            Entity moved = moving.changeDimension(otherWorld.provider.getDimension(), new net.minecraft.world.Teleporter(otherWorld) {
                @Override public void placeInPortal(Entity entity, float rotationYaw) { entity.setLocationAndAngles(otherCenter.getX() + 0.5, otherCenter.getY() + 1, otherCenter.getZ() + 0.5, rotationYaw, 0); }
            });
            check(moved instanceof EntityLiving && movingId.equals(moved.getUniqueID()), "Native dimension transfer must preserve trial mob identity");
            trial.update();
            check(trial.getActiveMobCount() == 1 && trial.getCooldown() == 0, "The dead source-dimension instance must not complete a wave while its live copy exists elsewhere");
            player.setFire(5);
            trial.update();
            check(trial.isOminous() && trial.getTargetCount() == 5, "Authored ominous condition must upgrade the real active wave and its participant-scaled target");
            NBTTagCompound savedTrial = trial.writeToNBT(new NBTTagCompound());
            UUID wave = savedTrial.getUniqueId("Wave");
            TrialSpawnerTileEntity reloaded = new TrialSpawnerTileEntity();
            reloaded.readFromNBT(savedTrial); world.setTileEntity(center, reloaded); trial = reloaded;
            check(trial.isOminous() && trial.getParticipantCount() == 1 && trial.getTargetCount() == 5, "Ominous state and participant-scaled limits must survive native tile NBT reload");
            moved.attackEntityFrom(DamageSource.OUT_OF_WORLD, 10000);
            TrialWaveData restored = new TrialWaveData();
            restored.readFromNBT(TrialWaveData.get(world.getMinecraftServer()).writeToNBT(new NBTTagCompound()));
            Set<UUID> waiting = new HashSet<>(); waiting.add(movingId);
            check(restored.drain(wave, waiting) && waiting.isEmpty(), "Cross-dimension death must persist independently of the owner's loaded tile");
            for (int tick = 0; tick < 100 && trial.getCooldown() == 0; tick++) {
                trial.update();
                for (EntityLiving mob : trialMobs(world, area)) {
                    check(mob instanceof net.minecraft.entity.monster.EntitySkeleton && mob.isPotionActive(MobEffects.STRENGTH), "Ominous entity selection and composed spawn effects must execute");
                    mob.attackEntityFrom(DamageSource.OUT_OF_WORLD, 10000);
                }
            }
            check(trial.getCooldown() == 80, "Completed ominous wave must use its configured cooldown");
            int diamonds = 0, emeralds = 0;
            for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, area)) {
                if (item.getItem().getItem() == Items.DIAMOND) diamonds += item.getItem().getCount();
                if (item.getItem().getItem() == Items.EMERALD) emeralds += item.getItem().getCount();
            }
            check(diamonds == 2 && emeralds == 0, "Ominous per-participant rewards must replace normal rewards");
            for (int tick = 0; tick < 5; tick++) trial.update();
            check(trial.getCooldown() == 75, "Persisted cooldown must count down without starting or rewarding another wave");
            System.out.println("GameplayAdvancedContractTest live native proof passed");
        } finally {
            try {
                net.minecraft.tileentity.TileEntity tile = world.getTileEntity(center);
                if (tile instanceof TrialSpawnerTileEntity) ((TrialSpawnerTileEntity) tile).abort();
                removeNewEntities(world, area, originalEntities, player);
                removeNewEntities(otherWorld, otherArea, otherEntities, player);
                for (BlockPos position : placed) world.setBlockToAir(position);
            } finally {
                player.resetActiveHand(); player.inventory.clear(); player.extinguish();
                try { player.onUpdateEntity(); }
                finally {
                    player.readFromNBT(savedPlayer);
                    player.setSneaking(sneaking); player.setSprinting(sprinting); player.xpCooldown = xpCooldown;
                    player.connection.setPlayerLocation(player.posX, player.posY, player.posZ, yaw, pitch);
                    player.getStatFile().unlockAchievement(player, bowUses, uses); player.getStatFile().unlockAchievement(player, bowBreaks, breaks);
                    player.onUpdateEntity(); player.sendPlayerAbilities(); player.inventoryContainer.detectAndSendChanges();
                }
            }
        }
    }
    private static void shoot(EntityPlayerMP player, WorldServer world, ItemStack bow) {
        player.setActiveHand(EnumHand.MAIN_HAND);
        Items.BOW.onPlayerStoppedUsing(bow, world, player, 72000 - 20);
        player.resetActiveHand();
    }
    private static Enchantment enchantment(String path) {
        Enchantment value = net.minecraftforge.fml.common.registry.ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("rml_regression", path));
        check(value instanceof NativeEnchantment, "Missing pre-registered gameplay proof enchantment rml_regression:" + path);
        return value;
    }
    private static ItemStack enchanted(net.minecraft.item.Item item, Enchantment enchantment, int level) { ItemStack stack = new ItemStack(item); stack.addEnchantment(enchantment, level); return stack; }
    private static List<EntityArrow> arrows(WorldServer world, AxisAlignedBB area, EntityPlayerMP owner) {
        List<EntityArrow> result = new ArrayList<>();
        for (EntityArrow arrow : world.getEntitiesWithinAABB(EntityArrow.class, area)) if (!arrow.isDead && arrow.shootingEntity == owner) result.add(arrow);
        return result;
    }
    private static List<EntityLiving> trialMobs(WorldServer world, AxisAlignedBB area) {
        List<EntityLiving> result = new ArrayList<>();
        for (EntityLiving entity : world.getEntitiesWithinAABB(EntityLiving.class, area)) if (entity.isEntityAlive() && entity.getEntityData().hasUniqueId("rml_trial_wave")) result.add(entity);
        return result;
    }
    private static Set<UUID> entityIds(WorldServer world, AxisAlignedBB area) {
        Set<UUID> ids = new HashSet<>();
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, area)) ids.add(entity.getUniqueID());
        return ids;
    }
    private static void removeNewEntities(WorldServer world, AxisAlignedBB area, Set<UUID> before, EntityPlayerMP player) {
        for (Entity entity : new ArrayList<>(world.getEntitiesWithinAABB(Entity.class, area))) if (entity != player && !before.contains(entity.getUniqueID())) world.removeEntityDangerously(entity);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
