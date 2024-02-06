package mods.Hileb.rml.serialize;

import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.event.CraftingHelperInitEvent;
import mods.Hileb.rml.api.event.FunctionLoadEvent;
import mods.Hileb.rml.api.event.LootTableRegistryEvent;
import mods.Hileb.rml.serialize.craft.recipe.NamedEmptyRecipeImpl;
import mods.Hileb.rml.serialize.craft.recipe.SimpleAnvilRecipe;
import mods.Hileb.rml.serialize.craft.recipe.SimpleBrewRecipe;
import mods.Hileb.rml.serialize.craft.recipe.SmeltRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/5 23:08
 **/
@PrivateAPI
public class RMLForgeEventHandler {
    @SubscribeEvent
    public static void onRegisterRecipeFactory(CraftingHelperInitEvent event){
        event.register(new ResourceLocation("rml","smelt"),new SmeltRecipe.Factory());
        event.register(new ResourceLocation("rml","brew"),new SimpleBrewRecipe.Factory());
        event.register(new ResourceLocation("rml","anvil"),new SimpleAnvilRecipe.Factory());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterRecipeRegister(RegistryEvent.Register<IRecipe> event){
        if (NamedEmptyRecipeImpl.removeCaches!=null){
            ForgeRegistry<IRecipe> registry=(ForgeRegistry<IRecipe>) event.getRegistry();
            for(IRecipe recipe: NamedEmptyRecipeImpl.removeCaches){
                registry.remove(recipe.getRegistryName());
            }
            NamedEmptyRecipeImpl.removeCaches=null;
        }
    }
    @SubscribeEvent
    public static void onLoad(FunctionLoadEvent event){
        RMLSerializeLoader.Function.load(event);
    }
    @SubscribeEvent
    public static void onRegisterLootTable(LootTableRegistryEvent event){
        RMLSerializeLoader.LootTable.load(event);
    }
    public static void preInit(FMLPreInitializationEvent event){
        //RMLOreDicLoader.load();
    }
    public static void postInit(FMLPostInitializationEvent event){
        RMLSerializeLoader.OreDic.load();
    }
}
