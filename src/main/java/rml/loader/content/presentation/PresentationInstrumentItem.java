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
    private final PresentationCodecs.SoundPlayback playback;

    PresentationInstrumentItem(SoundEvent sound, PresentationCodecs.SoundPlayback playback) {
        this.playback = playback;
        setMaxStackSize(1);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.MISC);
    }

    @Override public int getMaxItemUseDuration(ItemStack stack) { return playback.duration; }
    @Override public net.minecraft.item.EnumAction getItemUseAction(ItemStack stack) { return net.minecraft.item.EnumAction.BOW; }

    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        player.getCooldownTracker().setCooldown(this, playback.duration);
        if (!world.isRemote) PresentationPlayback.play(world, player.posX, player.posY, player.posZ, playback, player.getEntityId(), false);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
