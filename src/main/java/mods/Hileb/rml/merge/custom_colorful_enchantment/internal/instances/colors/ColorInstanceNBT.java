package com.Hileb.custom_colorful_enchantment.internal.instances.colors;

import com.Hileb.custom_colorful_enchantment.api.colors.IColorInstance;
import net.minecraft.world.item.ItemStack;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 20:26
 **/
public class ColorInstanceNBT implements IColorInstance {
    public static final String KEY = "enchantment_color";

    public ColorInstanceNBT() {
    }

    public int getRGBColor(ItemStack stack) {
        return stack.getTag().getInt("enchantment_color");
    }

    public boolean hasColor(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("enchantment_color",99);
    }
}
