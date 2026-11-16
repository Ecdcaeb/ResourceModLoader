package rml.loader.content.gameplay;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SulfurCubeEntity extends EntitySlime {
    private static volatile Map<ResourceLocation, SulfurCubeArchetype> archetypes = new LinkedHashMap<>();
    private ResourceLocation archetypeId;
    private long lastContactTick;

    public SulfurCubeEntity(World world) { super(world); }

    static void setArchetypes(Map<ResourceLocation, SulfurCubeArchetype> values) {
        archetypes = new LinkedHashMap<>(values);
    }

    public void setArchetype(ResourceLocation id) {
        SulfurCubeArchetype archetype = archetypes.get(id);
        if (archetype == null) throw new IllegalArgumentException("Unknown sulfur cube archetype: " + id);
        archetypeId = id;
        setSlimeSize(archetype.size, true);
    }

    private SulfurCubeArchetype archetype() {
        if (archetypeId != null) return archetypes.get(archetypeId);
        if (archetypes.size() == 1) {
            archetypeId = archetypes.keySet().iterator().next();
            return archetypes.get(archetypeId);
        }
        return null;
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        SulfurCubeArchetype archetype = archetype();
        if (archetype == null || !(target instanceof EntityLivingBase)) return super.attackEntityAsMob(target);
        long now = world.getTotalWorldTime();
        if (now - lastContactTick < 20) return false;
        lastContactTick = now;
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), archetype.contactDamage);
        if (hit && archetype.contactEffect != null) ((EntityLivingBase) target).addPotionEffect(new net.minecraft.potion.PotionEffect(archetype.contactEffect));
        return hit;
    }

    @Override
    protected ResourceLocation getLootTable() {
        SulfurCubeArchetype archetype = archetype();
        return archetype == null ? super.getLootTable() : archetype.lootTable;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (archetypeId != null) tag.setString("Archetype", archetypeId.toString());
        tag.setLong("LastContact", lastContactTick);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        archetypeId = tag.hasKey("Archetype") ? new ResourceLocation(tag.getString("Archetype")) : null;
        lastContactTick = tag.getLong("LastContact");
        SulfurCubeArchetype archetype = archetype();
        if (archetype != null) setSlimeSize(archetype.size, true);
    }
}