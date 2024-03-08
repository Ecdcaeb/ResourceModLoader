package com.Hileb.custom_colorful_enchantment.internal.instances.colors;

import com.Hileb.custom_colorful_enchantment.api.colors.IColorFactory;
import com.Hileb.custom_colorful_enchantment.api.colors.IColorInstance;
import com.google.gson.JsonObject;
import com.mojang.realmsclient.util.JsonUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 20:19
 **/
public class ColorColorEnchantment {
    public ColorColorEnchantment() {
    }

    public static class Instance implements IColorInstance {
        public final Enchantment enchantment;
        public boolean isAll;
        public final int color;

        public Instance(JsonObject oin, int colorIn) {
            if (oin.has("accept_all")) {
                this.isAll = true;
                this.enchantment = null;
            } else {
                this.isAll = false;
                this.enchantment = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(Objects.requireNonNull(com.mojang.realmsclient.util.JsonUtils.getStringOr("enchantment", oin, null))));
            }

            this.color = colorIn;
        }

        public int getRGBColor(ItemStack stack) {
            return this.color;
        }

        public boolean hasColor(ItemStack stack) {
            return EnchantmentHelper.getEnchantments(stack).containsKey(this.enchantment) || this.isAll && stack.isEnchanted();
        }
    }

    public static class Factory implements IColorFactory {
        public Factory() {
        }

        public IColorInstance getInstance(JsonObject object) {
            return new Instance(object, JsonUtils.getIntOr("color",object, 0));
        }
    }
}

