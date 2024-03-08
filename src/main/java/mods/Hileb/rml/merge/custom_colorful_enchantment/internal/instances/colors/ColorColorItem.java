package com.Hileb.custom_colorful_enchantment.internal.instances.colors;

import com.Hileb.custom_colorful_enchantment.api.colors.IColorFactory;
import com.Hileb.custom_colorful_enchantment.api.colors.IColorInstance;
import com.google.gson.JsonObject;
import com.mojang.realmsclient.util.JsonUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 20:23
 **/
public class ColorColorItem {
    //public static final JsonContext CONTEXT = new JsonContext("custom_colorful_enchantment");

    public ColorColorItem() {
    }

    public static class Instance implements IColorInstance {
        public final Ingredient ingredient;
        public boolean isAll;
        public final int color;

        public Instance(JsonObject in, int colorIn) {
            if (in.has("accept_all")) {
                this.isAll = true;
                this.ingredient = null;
            } else {
                this.isAll = false;
                this.ingredient = CraftingHelper.getIngredient(in.get("ingredient"), true);
            }

            this.color = colorIn;
        }

        public int getRGBColor(ItemStack stack) {
            return this.color;
        }

        public boolean hasColor(ItemStack stack) {
            return this.ingredient == null || this.ingredient.test(stack);
        }
    }

    public static class Factory implements IColorFactory {
        public Factory() {
        }

        public IColorInstance getInstance(JsonObject object) {
            return new Instance(object, JsonUtils.getIntOr("color",object,  0));
        }
    }
}
