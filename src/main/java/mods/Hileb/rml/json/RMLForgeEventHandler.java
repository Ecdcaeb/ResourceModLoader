package mods.Hileb.rml.json;

import mods.Hileb.rml.api.event.CraftingHelperInitEvent;
import mods.Hileb.rml.json.craft.recipe.SimpleAnvilRecipe;
import mods.Hileb.rml.json.craft.recipe.SimpleBrewRecipe;
import mods.Hileb.rml.json.craft.recipe.SmeltRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/5 23:08
 **/
public class RMLForgeEventHandler {
    @SubscribeEvent
    public static void onRegisterRecipeFactory(CraftingHelperInitEvent event){
        event.register(new ResourceLocation("rml","smelt"),new SmeltRecipe.Factory());
        event.register(new ResourceLocation("rml","brew"),new SimpleBrewRecipe.Factory());
        event.register(new ResourceLocation("rml","anvil"),new SimpleAnvilRecipe.Factory());
    }
}
