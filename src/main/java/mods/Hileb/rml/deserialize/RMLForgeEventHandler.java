package mods.Hileb.rml.deserialize;

import mods.Hileb.rml.ResourceModLoader;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.event.CraftingHelperInitEvent;
import mods.Hileb.rml.api.event.FunctionLoadEvent;
import mods.Hileb.rml.api.event.LootTableRegistryEvent;
import mods.Hileb.rml.api.event.client.gui.ModMenuInfoEvent;
import mods.Hileb.rml.api.event.villagers.CustomVillageLoaderRegisterEvent;
import mods.Hileb.rml.api.java.progress.ProgressBar;
import mods.Hileb.rml.api.java.utils.EnumUtils;
import mods.Hileb.rml.api.mods.ContainerHolder;
import mods.Hileb.rml.api.text.ChangeMod;
import mods.Hileb.rml.api.villagers.LoadedVillage;
import mods.Hileb.rml.api.villagers.trades.itrades.SlotRecipe;
import mods.Hileb.rml.api.villagers.trades.ranges.PriceRange;
import mods.Hileb.rml.api.villagers.trades.ranges.RangeConstant;
import mods.Hileb.rml.api.villagers.trades.ranges.RangePoisson;
import mods.Hileb.rml.api.villagers.trades.trades.EmeraldForItems;
import mods.Hileb.rml.api.villagers.trades.trades.ItemAndEmeraldToItem;
import mods.Hileb.rml.api.villagers.trades.trades.ListItemForEmeralds;
import mods.Hileb.rml.api.villagers.villagers.VillageCancer;
import mods.Hileb.rml.api.villagers.villagers.VillageProfession;
import mods.Hileb.rml.deserialize.craft.recipe.NamedEmptyRecipeImpl;
import mods.Hileb.rml.deserialize.craft.recipe.SimpleAnvilRecipe;
import mods.Hileb.rml.deserialize.craft.recipe.SimpleBrewRecipe;
import mods.Hileb.rml.deserialize.craft.recipe.SmeltRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.ModContainer;
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
        RMLDeserializeLoader.Function.load(event);
    }

    @SubscribeEvent
    public static void onRegisterLootTable(LootTableRegistryEvent event){
        RMLDeserializeLoader.LootTable.load(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegistryEvent.Register<VillagerRegistry.VillagerProfession> event){
        IForgeRegistry<VillagerRegistry.VillagerProfession> forgeRegistry = event.getRegistry();
        for(LoadedVillage village: RMLDeserializeLoader.CustomVillageLoader.load()){
            village.run(forgeRegistry);
        }
    }

    @SubscribeEvent
    public static void onRegister(CustomVillageLoaderRegisterEvent event){
        event.add(new ResourceLocation("minecraft","price"), new PriceRange.Factory());
        //range factory
        event.add(new ResourceLocation("minecraft","price"), new PriceRange.Factory());
        event.add(new ResourceLocation("cvh","constant"), new RangeConstant.Factory());
        event.add(new ResourceLocation("cvh","poisson_distribution"),new RangePoisson.Factory());

        //trade loader
        event.add(new ResourceLocation("minecraft","emerald_for_items"), new EmeraldForItems.Loader());
        event.add(new ResourceLocation("minecraft","item_and_emerald_to_item"), new ItemAndEmeraldToItem.Loader());
        event.add(new ResourceLocation("minecraft","list_item_for_emeralds"), new ListItemForEmeralds.Loader());
        event.add(new ResourceLocation("cvh","slots"), new SlotRecipe.Loader());

        //base loader
        event.add(new ResourceLocation("minecraft","profession"), new VillageProfession.Loader());
        event.add(new ResourceLocation("minecraft","cancer"), new VillageCancer.Loader());
    }

    public static void preInit(FMLPreInitializationEvent event){
        //RMLOreDicLoader.load();
    }
    public static void postInit(FMLPostInitializationEvent event){
        RMLDeserializeLoader.OreDic.load();
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
