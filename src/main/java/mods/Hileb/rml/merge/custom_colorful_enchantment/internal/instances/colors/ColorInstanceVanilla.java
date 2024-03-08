package com.Hileb.custom_colorful_enchantment.internal.instances.colors;

import com.Hileb.custom_colorful_enchantment.api.colors.IColorInstance;
import net.minecraft.world.item.ItemStack;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 20:29
 **/
public class ColorInstanceVanilla implements IColorInstance {
    public ColorInstanceVanilla() {
    }

    public int getRGBColor(ItemStack stack) {
        return 8405195;
    }

    public boolean hasColor(ItemStack stack) {
        return stack.hasFoil();
    }
}
