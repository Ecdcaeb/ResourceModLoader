package rml.loader.content.presentation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

/** Hand-playable authored instrument; the server broadcasts the authored sound to nearby players. */
final class PresentationInstrumentItem extends Item {
    private final SoundEvent sound;
    private final float volume;
    private final float pitch;

    PresentationInstrumentItem(SoundEvent sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.MISC);
    }

    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) world.playSound(null, player.posX, player.posY, player.posZ, sound, SoundCategory.RECORDS, volume, pitch);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
