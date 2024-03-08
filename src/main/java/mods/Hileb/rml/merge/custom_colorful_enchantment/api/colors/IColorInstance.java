package com.Hileb.custom_colorful_enchantment.api.colors;

import net.minecraft.world.item.ItemStack;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 19:56
 **/
public interface IColorInstance {
    int getRGBColor(ItemStack var1);

    boolean hasColor(ItemStack var1);
}
