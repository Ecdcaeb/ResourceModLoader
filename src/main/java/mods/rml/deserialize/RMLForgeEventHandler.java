package mods.rml.deserialize;

import mods.rml.ResourceModLoader;
import mods.rml.api.RMLRegistries;
import mods.rml.api.announces.PrivateAPI;
import mods.rml.api.event.CraftingHelperInitEvent;
import mods.rml.api.event.FunctionLoadEvent;
import mods.rml.api.event.LootTableRegistryEvent;
import mods.rml.api.event.client.gui.ModMenuInfoEvent;
import mods.rml.api.world.function.FunctionExecutorFactory;
import mods.rml.api.world.function.impl.FunctionExecutorGameLoop;
import mods.rml.api.world.function.impl.FunctionExecutorLoad;
import mods.rml.api.mods.ContainerHolder;
import mods.rml.api.world.text.ChangeMod;
import mods.rml.api.world.villagers.LoadedVillage;
import mods.rml.api.world.villagers.VillageReader;
import mods.rml.api.world.villagers.trades.itrades.SlotRecipe;
import mods.rml.api.world.villagers.trades.ranges.PriceRange;
import mods.rml.api.world.villagers.trades.ranges.RangeConstant;
import mods.rml.api.world.villagers.trades.ranges.RangeFactory;
import mods.rml.api.world.villagers.trades.ranges.RangePoisson;
import mods.rml.api.world.villagers.trades.trades.EmeraldForItems;
import mods.rml.api.world.villagers.trades.trades.ItemAndEmeraldToItem;
import mods.rml.api.world.villagers.trades.trades.ListItemForEmeralds;
import mods.rml.api.world.villagers.villagers.VillageCancer;
import mods.rml.api.world.villagers.villagers.VillageProfession;
import mods.rml.deserialize.craft.recipe.NamedEmptyRecipeImpl;
import mods.rml.deserialize.craft.recipe.SimpleAnvilRecipe;
import mods.rml.deserialize.craft.recipe.SimpleBrewRecipe;
import mods.rml.deserialize.craft.recipe.SmeltRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/5 23:08
 **/
@PrivateAPI
public class RMLForgeEventHandler {

    @SubscribeEvent
    public static void loadFunction(FunctionLoadEvent event){
        RMLDeserializeLoader.Function.load(event);
    }

    @SubscribeEvent
    public static void registerLootTable(LootTableRegistryEvent event){
        RMLDeserializeLoader.LootTable.load(event);
    }

    @SubscribeEvent
    public static void registerRecipeFactory(CraftingHelperInitEvent event){
        event.register(new ResourceLocation("rml","smelt"),new SmeltRecipe.Factory());
        event.register(new ResourceLocation("rml","brew"),new SimpleBrewRecipe.Factory());
        event.register(new ResourceLocation("rml","anvil"),new SimpleAnvilRecipe.Factory());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterRecipeRegister(RegistryEvent.Register<IRecipe> event){
        if (NamedEmptyRecipeImpl.removeCaches != null){
            ForgeRegistry<IRecipe> registry=(ForgeRegistry<IRecipe>) event.getRegistry();
            for(IRecipe recipe: NamedEmptyRecipeImpl.removeCaches){
                registry.remove(recipe.getRegistryName());
            }
            NamedEmptyRecipeImpl.removeCaches = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerVillagerProfession(RegistryEvent.Register<VillagerRegistry.VillagerProfession> event){
        IForgeRegistry<VillagerRegistry.VillagerProfession> forgeRegistry = event.getRegistry();
        for(LoadedVillage village : RMLDeserializeLoader.CustomVillageLoader.load()){
            village.run(forgeRegistry);
        }
    }

    @SubscribeEvent
    public static void registerVillageReaders(RegistryEvent.Register<VillageReader> event){
        IForgeRegistry<VillageReader> registry = event.getRegistry();
        //trade loader
        registry.register(new EmeraldForItems.Loader().setRegistryName(new ResourceLocation("minecraft","emerald_for_items")));
        registry.register(new ItemAndEmeraldToItem.Loader().setRegistryName(new ResourceLocation("minecraft","item_and_emerald_to_item")));
        registry.register(new ListItemForEmeralds.Loader().setRegistryName(new ResourceLocation("minecraft","list_item_for_emeralds")));
        registry.register(new SlotRecipe.Loader().setRegistryName(new ResourceLocation("cvh","slots")));

        //base loader
        registry.register(new VillageProfession.Loader().setRegistryName(new ResourceLocation("minecraft","profession")));
        registry.register(new VillageCancer.Loader().setRegistryName(new ResourceLocation("minecraft","cancer")));
    }

    @SubscribeEvent
    public static void registerRanges(RegistryEvent.Register<RangeFactory> event){
        IForgeRegistry<RangeFactory> registry = event.getRegistry();
        registry.register(new PriceRange.Factory().setRegistryName(new ResourceLocation("minecraft","price")));
        registry.register(new RangeConstant.Factory().setRegistryName(new ResourceLocation("cvh","constant")));
        registry.register(new RangePoisson.Factory().setRegistryName(new ResourceLocation("cvh","poisson_distribution")));
    }

    @SubscribeEvent
    public static void registerFunctionExecutorFactories(RegistryEvent.Register<FunctionExecutorFactory> event){
        IForgeRegistry<FunctionExecutorFactory> registry = event.getRegistry();
        registry.register(new FunctionExecutorGameLoop().setRegistryName(new ResourceLocation("rml", "tick")));
        registry.register(new FunctionExecutorLoad().setRegistryName(new ResourceLocation("rml", "load")));
    }

    public static void construct(FMLConstructionEvent event){
        RMLDeserializeLoader.ConfigLoader.load();
    }

    public static void preInit(FMLPreInitializationEvent event){

    }
    public static void postInit(FMLPostInitializationEvent event){
        RMLDeserializeLoader.OreDic.load();
        RMLRegistries.Names.FUNCTION_EXECUTOR_FACTORY.fire();
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onPrintModList(ModMenuInfoEvent event){
        if (event.getMod() != null){
            if ("rml".equals(event.getMod().getModId())) {
                event.getTextComponents().add(new TextComponentTranslation("rml.gui.modmenu.rmlchilds").setStyle(new Style().setColor(TextFormatting.BLUE)));
                for (ModContainer container : ResourceModLoader.getCurrentRMLContainers()) {
                    event.getTextComponents().add(new TextComponentString(container.getName()).setStyle(new Style().setColor(TextFormatting.BLUE).setUnderlined(true).setClickEvent(ChangeMod.ChangeModAction.makeEvent(container.getModId()))));
                }
            }else if (ResourceModLoader.enabledModContainers.stream().map(ContainerHolder::getContainer).anyMatch((container)->container == event.getMod())){
                event.getTextComponents().add(new TextComponentTranslation("rml.gui.modmenu.enablerml").setStyle(new Style().setColor(TextFormatting.BLUE).setUnderlined(true).setClickEvent(ChangeMod.ChangeModAction.makeEvent("rml"))));
            }
        }
    }

}
