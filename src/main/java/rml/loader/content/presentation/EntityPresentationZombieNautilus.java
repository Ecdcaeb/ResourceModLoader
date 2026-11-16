package rml.loader.content.presentation;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraft.block.material.Material;

/** Hostile aquatic nautilus equivalent with swimmer navigation and player targeting. */
public final class EntityPresentationZombieNautilus extends EntityMob {
    public EntityPresentationZombieNautilus(World world) {
        super(world);
        setSize(1.15F, 0.9F);
        ((PathNavigateGround) getNavigator()).setCanSwim(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackMelee(this, 1.15D, true));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true));
    }

    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(24.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.28D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(4.0D);
        getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(24.0D);
    }

    @Override public boolean getCanSpawnHere() {
        return world.getBlockState(getPosition()).getMaterial() == Material.WATER && super.getCanSpawnHere();
    }

    @Override protected SoundEvent getAmbientSound() { return PresentationSoundResolver.sound(this, "ambient"); }
    @Override protected SoundEvent getHurtSound(net.minecraft.util.DamageSource source) { return PresentationSoundResolver.sound(this, "hurt"); }
    @Override protected SoundEvent getDeathSound() { return PresentationSoundResolver.sound(this, "death"); }
}
