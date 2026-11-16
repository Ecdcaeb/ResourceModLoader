package rml.loader.content.presentation;

import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIFollowParent;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMate;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.entity.passive.EntityAnimal;

/** A living, breedable frog equivalent for the species absent from 1.12.2. */
public final class EntityPresentationFrog extends EntityAnimal {
    public EntityPresentationFrog(World world) {
        super(world);
        setSize(0.55F, 0.45F);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIMate(this, 1.0D));
        tasks.addTask(2, new EntityAITempt(this, 1.1D, Items.SLIME_BALL, false));
        tasks.addTask(3, new EntityAIFollowParent(this, 1.1D));
        tasks.addTask(4, new EntityAIWander(this, 0.8D));
        tasks.addTask(5, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
        tasks.addTask(6, new EntityAILookIdle(this));
    }

    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(10.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.24D);
    }

    @Override public boolean isBreedingItem(ItemStack stack) { return stack.getItem() == Items.SLIME_BALL; }
    @Override public EntityPresentationFrog createChild(EntityAgeable mate) { return new EntityPresentationFrog(world); }
    @Override protected net.minecraft.util.SoundEvent getAmbientSound() { return PresentationSoundResolver.sound(this, "ambient"); }
    @Override protected net.minecraft.util.SoundEvent getHurtSound(net.minecraft.util.DamageSource source) { return PresentationSoundResolver.sound(this, "hurt"); }
    @Override protected net.minecraft.util.SoundEvent getDeathSound() { return PresentationSoundResolver.sound(this, "death"); }
}
