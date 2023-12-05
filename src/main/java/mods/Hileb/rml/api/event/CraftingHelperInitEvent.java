package mods.Hileb.rml.api.event;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.IIngredientFactory;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/5 22:57
 **/
public class CraftingHelperInitEvent extends Event {
    public CraftingHelperInitEvent(){}
    public static void post(){
        MinecraftForge.EVENT_BUS.post(new CraftingHelperInitEvent());
    }
    public void register(ResourceLocation name, IConditionFactory fac) {
        CraftingHelper.register(name,fac);
    }
    public void register(ResourceLocation name, IRecipeFactory fac) {
        CraftingHelper.register(name,fac);
    }
    public void register(ResourceLocation name, IIngredientFactory fac) {
        CraftingHelper.register(name,fac);
    }
}
