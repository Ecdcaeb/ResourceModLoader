package mods.rml.api.tag.gamein.stack;

import mods.rml.api.tag.Tag;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:14
 **/
public class ItemStackTag implements Tag<ItemStack> {
    private final Ingredient ingredient;
    public ItemStackTag(Ingredient ingredient){
        this.ingredient = ingredient;
    }
    @Override
    public boolean match(ItemStack value) {
        return ingredient.apply(value);
    }
}
