package rml.loader.content.gameplay;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import rml.loader.content.data.DataContext;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrialSpawnerTileEntity extends TileEntity implements net.minecraft.util.ITickable {
    private static volatile Map<ResourceLocation, TrialSpawnerDefinition> definitions = new LinkedHashMap<>();
    private final Set<UUID> liveMobs = new LinkedHashSet<>();
    private final Set<UUID> participants = new LinkedHashSet<>();
    private ResourceLocation definitionId;
    private UUID waveId;
    private int spawned, delay, cooldown, baseCount, baseActive, targetCount, activeLimit;
    private boolean active, ominous;

    static void setDefinitions(Map<ResourceLocation, TrialSpawnerDefinition> values) { definitions = new LinkedHashMap<>(values); }
    public void setDefinition(ResourceLocation id) {
        if (!definitions.containsKey(id)) throw new IllegalArgumentException("Unknown trial spawner definition: " + id);
        if (active || cooldown > 0 || !liveMobs.isEmpty()) throw new IllegalStateException("Cannot reconfigure an active or cooling trial spawner");
        definitionId = id;
        spawned = delay = targetCount = activeLimit = 0;
        participants.clear();
        ominous = false;
        markDirty();
    }
    private DataContext context(Entity entity) { return new DataContext((WorldServer) world, entity, pos, world.rand, participants.size()); }
    private TrialSpawnerDefinition.Settings settings(TrialSpawnerDefinition definition) { return ominous ? definition.ominous : definition.normal; }
    private TrialWaveData ledger() { return TrialWaveData.get(world.getMinecraftServer()); }

    public void activate(EntityPlayer player) {
        if (!(world instanceof WorldServer) || player.isSpectator() || !player.isEntityAlive() || cooldown > 0) return;
        if (definitionId == null && definitions.size() == 1) definitionId = definitions.keySet().iterator().next();
        TrialSpawnerDefinition definition = definitions.get(definitionId);
        if (definition == null || player.getDistanceSq(pos) > (double) definition.activationRange * definition.activationRange) return;
        boolean start = !active;
        if (start) {
            participants.clear(); liveMobs.clear(); spawned = delay = targetCount = activeLimit = 0;
            active = true; ominous = false; waveId = UUID.randomUUID(); ledger().open(waveId);
        }
        boolean joined = participants.add(player.getUniqueID());
        boolean upgrade = !ominous && definition.ominous != null && definition.ominousCondition.test(context(player));
        if (upgrade) ominous = true;
        TrialSpawnerDefinition.Settings settings = settings(definition);
        if (start || upgrade) {
            baseCount = settings.spawnCount.sample(world.rand);
            baseActive = settings.maxActive.sample(world.rand);
            delay = 0;
            activeLimit = settings.activeLimit(baseActive, participants.size());
            if (settings.onStart != null) settings.onStart.apply(context(player), player);
        }
        if (start || upgrade || joined) {
            targetCount = Math.max(targetCount, settings.target(baseCount, participants.size()));
            activeLimit = Math.max(activeLimit, settings.activeLimit(baseActive, participants.size()));
            markDirty();
        }
    }

    @Override public void update() {
        if (!(world instanceof WorldServer)) return;
        if (definitionId == null && definitions.size() == 1) { definitionId = definitions.keySet().iterator().next(); markDirty(); }
        TrialSpawnerDefinition definition = definitions.get(definitionId);
        if (definition == null) return;
        if (cooldown > 0) {
            cooldown--;
            if (cooldown == 0) { active = false; ominous = false; spawned = 0; participants.clear(); waveId = null; }
            markDirty();
            return;
        }
        for (int index = world.playerEntities.size() - 1; index >= 0; index--) if (index < world.playerEntities.size()) activate(world.playerEntities.get(index));
        if (!active) return;
        if (waveId == null) { waveId = UUID.randomUUID(); markDirty(); }
        ledger().open(waveId);
        reconcileMobs();
        TrialSpawnerDefinition.Settings settings = settings(definition);
        if (settings == null) throw new IllegalStateException("Persisted ominous wave has no ominous settings: " + definitionId);
        if (spawned >= targetCount && liveMobs.isEmpty()) { complete(settings); return; }
        if (spawned >= targetCount || liveMobs.size() >= activeLimit) return;
        if (delay > 0) { delay--; markDirty(); return; }
        if (spawn(definition, settings)) { spawned++; delay = settings.interval.sample(world.rand); }
        else delay = 20;
        markDirty();
    }

    private boolean spawn(TrialSpawnerDefinition definition, TrialSpawnerDefinition.Settings settings) {
        Entity entity = EntityList.createEntityByIDFromName(settings.entity, world);
        if (!(entity instanceof EntityLiving)) throw new IllegalStateException("Trial entity factory did not create a living mob: " + settings.entity);
        int range = definition.spawnRange;
        for (int attempt = 0; attempt < 8; attempt++) {
            double x = pos.getX() + 0.5 + (world.rand.nextDouble() * 2 - 1) * range;
            double z = pos.getZ() + 0.5 + (world.rand.nextDouble() * 2 - 1) * range;
            BlockPos spawnPos = new BlockPos(x, pos.getY() + 1, z);
            BlockPos floor = spawnPos.down();
            if (spawnPos.getY() < 1 || spawnPos.getY() > 254 || !world.isBlockLoaded(spawnPos)
                    || !world.getBlockState(floor).isSideSolid(world, floor, net.minecraft.util.EnumFacing.UP)
                    || !world.isAirBlock(spawnPos) || !world.isAirBlock(spawnPos.up())) continue;
            entity.setLocationAndAngles(x, spawnPos.getY(), z, world.rand.nextFloat() * 360, 0);
            if (!world.checkNoEntityCollision(entity.getEntityBoundingBox()) || !world.getCollisionBoxes(entity, entity.getEntityBoundingBox()).isEmpty()) continue;
            ((EntityLiving) entity).onInitialSpawn(world.getDifficultyForLocation(spawnPos), null);
            ((EntityLiving) entity).enablePersistence();
            stamp(entity);
            if (world.spawnEntity(entity)) {
                liveMobs.add(entity.getUniqueID());
                if (settings.onSpawn != null) settings.onSpawn.apply(context(entity), entity);
                return true;
            }
            break;
        }
        entity.setDead();
        return false;
    }
    private void stamp(Entity entity) {
        entity.getEntityData().setLong("rml_trial_spawner", pos.toLong());
        entity.getEntityData().setInteger("rml_trial_dimension", world.provider.getDimension());
        entity.getEntityData().setUniqueId("rml_trial_wave", waveId);
    }
    private void reconcileMobs() {
        boolean changed = ledger().drain(waveId, liveMobs);
        WorldServer[] worlds = net.minecraftforge.common.DimensionManager.getWorlds();
        java.util.Iterator<UUID> iterator = liveMobs.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Entity alive = null;
            boolean observedDead = false;
            for (WorldServer dimension : worlds) {
                Entity entity = dimension.getEntityFromUuid(id);
                if (entity != null) {
                    if (!entity.isDead) { alive = entity; break; }
                    observedDead = true;
                }
            }
            // Dimension transfer kills the old instance. Prefer the live copy in any dimension.
            if (alive != null) stamp(alive);
            else if (observedDead) { iterator.remove(); changed = true; }
            // Absence means an unloaded chunk; the saved death ledger resolves deaths while this tile is unloaded.
        }
        if (changed) markDirty();
    }
    public static void onMobDeath(EntityLivingBase entity) {
        if (entity.world.isRemote || !entity.getEntityData().hasUniqueId("rml_trial_wave")) return;
        TrialWaveData.get(entity.world.getMinecraftServer()).record(entity.getEntityData().getUniqueId("rml_trial_wave"), entity.getUniqueID());
    }
    private void complete(TrialSpawnerDefinition.Settings settings) {
        cooldown = settings.cooldown.sample(world.rand);
        active = false;
        ledger().close(waveId);
        markDirty();
        EntityPlayer first = null;
        for (UUID id : participants) {
            EntityPlayer player = world.getMinecraftServer().getPlayerList().getPlayerByUUID(id);
            if (first == null && player != null) first = player;
            if (settings.rewardPerPlayer) reward(settings, player);
            if (player != null && settings.onComplete != null) settings.onComplete.apply(context(player), player);
        }
        if (!settings.rewardPerPlayer) reward(settings, first);
    }
    private void reward(TrialSpawnerDefinition.Settings settings, EntityPlayer player) {
        if (settings.reward.isEmpty()) return;
        ItemStack reward = settings.rewardModifier.apply(settings.reward.copy(), context(player));
        if (!reward.isEmpty()) world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, reward));
    }
    public void abort() {
        if (world instanceof WorldServer) ledger().close(waveId);
        active = false; liveMobs.clear(); participants.clear(); waveId = null;
        markDirty();
    }
    public boolean isOminous() { return ominous; }
    public int getParticipantCount() { return participants.size(); }
    public int getTargetCount() { return targetCount; }
    public int getActiveMobCount() { return liveMobs.size(); }
    public int getCooldown() { return cooldown; }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (definitionId != null) tag.setString("Definition", definitionId.toString());
        if (waveId != null) tag.setUniqueId("Wave", waveId);
        tag.setInteger("Spawned", spawned); tag.setInteger("Delay", delay); tag.setInteger("Cooldown", cooldown);
        tag.setBoolean("Active", active); tag.setBoolean("Ominous", ominous);
        tag.setInteger("BaseCount", baseCount); tag.setInteger("BaseActive", baseActive); tag.setInteger("Target", targetCount); tag.setInteger("ActiveLimit", activeLimit);
        tag.setTag("LiveMobs", uuids(liveMobs)); tag.setTag("Participants", uuids(participants));
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        definitionId = tag.hasKey("Definition") ? new ResourceLocation(tag.getString("Definition")) : null;
        waveId = tag.hasUniqueId("Wave") ? tag.getUniqueId("Wave") : null;
        spawned = tag.getInteger("Spawned"); delay = tag.getInteger("Delay"); cooldown = tag.getInteger("Cooldown");
        active = tag.getBoolean("Active"); ominous = tag.getBoolean("Ominous");
        baseCount = tag.getInteger("BaseCount"); baseActive = tag.getInteger("BaseActive"); targetCount = tag.getInteger("Target"); activeLimit = tag.getInteger("ActiveLimit");
        readUuids(tag.getTagList("LiveMobs", 10), liveMobs); readUuids(tag.getTagList("Participants", 10), participants);
    }
    private static NBTTagList uuids(Set<UUID> values) {
        NBTTagList list = new NBTTagList();
        for (UUID id : values) { NBTTagCompound value = new NBTTagCompound(); value.setUniqueId("Id", id); list.appendTag(value); }
        return list;
    }
    private static void readUuids(NBTTagList list, Set<UUID> values) {
        values.clear();
        for (int i = 0; i < list.tagCount(); i++) values.add(list.getCompoundTagAt(i).getUniqueId("Id"));
    }
}