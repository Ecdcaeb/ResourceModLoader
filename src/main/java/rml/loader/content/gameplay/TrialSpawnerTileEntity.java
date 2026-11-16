package rml.loader.content.gameplay;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrialSpawnerTileEntity extends TileEntity implements net.minecraft.util.ITickable {
    private static volatile Map<ResourceLocation, TrialSpawnerDefinition> definitions = new LinkedHashMap<>();
    private final Set<UUID> liveMobs = new HashSet<>();
    private ResourceLocation definitionId;
    private int spawned;
    private int delay;
    private int cooldown;
    private boolean active;

    static void setDefinitions(Map<ResourceLocation, TrialSpawnerDefinition> values) {
        definitions = new LinkedHashMap<>(values);
    }

    public void setDefinition(ResourceLocation id) {
        if (!definitions.containsKey(id)) throw new IllegalArgumentException("Unknown trial spawner definition: " + id);
        definitionId = id;
        spawned = 0;
        delay = 0;
        cooldown = 0;
        active = false;
        liveMobs.clear();
        markDirty();
    }

    public void activate(EntityPlayer player) {
        if (definitionId == null && definitions.size() == 1) definitionId = definitions.keySet().iterator().next();
        TrialSpawnerDefinition definition = definitionId == null ? null : definitions.get(definitionId);
        if (definition == null || cooldown > 0 || active) return;
        if (player.getDistanceSq(pos) <= (double) definition.activationRange * definition.activationRange) {
            active = true;
            spawned = 0;
            delay = 0;
            markDirty();
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote || !(world instanceof WorldServer)) return;
        TrialSpawnerDefinition definition = definitionId == null ? null : definitions.get(definitionId);
        if (definition == null) return;
        removeDeadMobs();
        if (cooldown > 0) {
            cooldown--;
            if (cooldown == 0) { active = false; spawned = 0; }
            if (cooldown % 20 == 0) markDirty();
            return;
        }
        if (!active) return;
        if (spawned >= definition.spawnCount && liveMobs.isEmpty()) {
            if (!definition.reward.isEmpty()) world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, definition.reward.copy()));
            cooldown = definition.cooldown;
            markDirty();
            return;
        }
        if (spawned >= definition.spawnCount || liveMobs.size() >= definition.maxActive) return;
        if (delay > 0) { delay--; return; }
        if (spawn(definition)) {
            spawned++;
            delay = definition.interval;
            markDirty();
        } else {
            delay = 20;
        }
    }

    private boolean spawn(TrialSpawnerDefinition definition) {
        Entity entity = EntityList.createEntityByIDFromName(definition.entity, world);
        if (!(entity instanceof EntityLiving)) return false;
        int range = definition.spawnRange;
        for (int attempt = 0; attempt < 8; attempt++) {
            double x = pos.getX() + 0.5 + (world.rand.nextDouble() * 2 - 1) * range;
            double z = pos.getZ() + 0.5 + (world.rand.nextDouble() * 2 - 1) * range;
            BlockPos spawnPos = new BlockPos(x, pos.getY() + 1, z);
            BlockPos floor = spawnPos.down();
            if (!world.getBlockState(floor).isSideSolid(world, floor, net.minecraft.util.EnumFacing.UP)
                    || !world.isAirBlock(spawnPos) || !world.isAirBlock(spawnPos.up())) continue;
            entity.setLocationAndAngles(x, spawnPos.getY(), z, world.rand.nextFloat() * 360.0F, 0);
            if (!world.checkNoEntityCollision(entity.getEntityBoundingBox()) || !world.getCollisionBoxes(entity, entity.getEntityBoundingBox()).isEmpty()) continue;
            if (world.spawnEntity(entity)) {
                liveMobs.add(entity.getUniqueID());
                return true;
            }
        }
        entity.setDead();
        return false;
    }

    private void removeDeadMobs() {
        List<UUID> dead = new ArrayList<>();
        for (UUID id : liveMobs) {
            Entity entity = ((WorldServer) world).getEntityFromUuid(id);
            if (entity == null || entity.isDead) dead.add(id);
        }
        liveMobs.removeAll(dead);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (definitionId != null) tag.setString("Definition", definitionId.toString());
        tag.setInteger("Spawned", spawned); tag.setInteger("Delay", delay); tag.setInteger("Cooldown", cooldown); tag.setBoolean("Active", active);
        NBTTagList mobs = new NBTTagList();
        for (UUID id : liveMobs) { NBTTagCompound mob = new NBTTagCompound(); mob.setUniqueId("Id", id); mobs.appendTag(mob); }
        tag.setTag("LiveMobs", mobs);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        definitionId = tag.hasKey("Definition") ? new ResourceLocation(tag.getString("Definition")) : null;
        spawned = tag.getInteger("Spawned"); delay = tag.getInteger("Delay"); cooldown = tag.getInteger("Cooldown"); active = tag.getBoolean("Active");
        liveMobs.clear();
        NBTTagList mobs = tag.getTagList("LiveMobs", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < mobs.tagCount(); i++) liveMobs.add(mobs.getCompoundTagAt(i).getUniqueId("Id"));
    }
}