package mods.rml.api.tag.gamein.stack;

import net.minecraftforge.oredict.OreIngredient;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:16
 **/
public class OreItemStackTag extends ItemStackTag{

    public OreItemStackTag(String ore) {
        super(new OreIngredient(ore));
    }
}
