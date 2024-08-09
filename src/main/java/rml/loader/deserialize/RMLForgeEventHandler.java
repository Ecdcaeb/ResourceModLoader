package rml.loader.deserialize;

import com.cleanroommc.groovyscript.GroovyScript;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import rml.jrx.announces.BeDiscovered;
import rml.layer.compat.groovyscripts.RMLGrsLoader;
import rml.loader.ResourceModLoader;
import rml.jrx.announces.PrivateAPI;
import rml.loader.api.event.CraftingHelperInitEvent;
import rml.loader.api.event.FunctionLoadEvent;
import rml.loader.api.event.LootTableRegistryEvent;
import rml.loader.api.event.client.gui.ModMenuInfoEvent;
import rml.jrx.utils.ObjectHelper;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.api.world.text.RMLTextEffects;
import rml.loader.api.world.villagers.IVillager;
import rml.loader.core.RMLModDiscover;
import rml.loader.deserialize.craft.recipe.NamedEmptyRecipeImpl;
import rml.loader.deserialize.craft.recipe.SimpleAnvilRecipe;
import rml.loader.deserialize.craft.recipe.SimpleBrewRecipe;
import rml.loader.deserialize.craft.recipe.SmeltRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
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
        RMLLoaders.Function.load(event);
    }

    @SubscribeEvent
    public static void registerLootTable(LootTableRegistryEvent event){
        RMLLoaders.LootTable.load(event);
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
        for(IVillager village : RMLLoaders.CustomVillageLoader.load()){
            village.run(forgeRegistry);
        }
    }

    public static void construct(FMLConstructionEvent event){
        RMLLoaders.ConfigLoader.load();
        if (Loader.isModLoaded(GroovyScript.ID)){
            RMLGrsLoader.load();
        }
        RMLModDiscover.discover(event.getASMHarvestedData(), BeDiscovered.MOD_LOADING);
    }

    public static void preInit(FMLPreInitializationEvent event){
        RMLModDiscover.discover(event.getAsmData(), BeDiscovered.PRE_INIT);
    }

    public static void postInit(FMLPostInitializationEvent event){
        RMLLoaders.OreDic.load();
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onPrintModList(ModMenuInfoEvent event){
        if (event.getMod() != null){
            if ("rml".equals(event.getMod().getModId())) {
                event.getTextComponents().add(new TextComponentString("Official Documents")
                        .setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://ecdcaeb.github.io/ResourceModLoader/")))
                );
                event.getTextComponents().add(
                        new TextComponentTranslation("rml.gui.modmenu.rmlchilds")
                                .setStyle(new Style().setColor(TextFormatting.BLUE)
                                        .setHoverEvent(
                                                new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                        new TextComponentString(ObjectHelper.allToString("\n", (Object[]) ModuleType.values()))
                                                                .setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://ecdcaeb.github.io/ResourceModLoader/")))
                                                )
                                        )
                                )
                );
                for (ModContainer container : ResourceModLoader.getCurrentRMLContainers()) {
                    event.getTextComponents().add(
                            new TextComponentString(container.getName())
                                    .setStyle(new Style().setColor(TextFormatting.BLUE).setUnderlined(true)
                                            .setClickEvent(RMLTextEffects.ChangeModClickAction.CHANGE_MOD.makeEvent(container.getModId()))
                                            .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(ObjectHelper.allToString("\n", ResourceModLoader.of(container).getModules()))))
                                    )
                    );
                }
            }else {
                ContainerHolder containerHolder = ResourceModLoader.of(event.getMod());
                if (containerHolder != null) {
                    event.getTextComponents().add(new TextComponentTranslation("rml.gui.modmenu.enablerml").setStyle(new Style().setColor(TextFormatting.BLUE).setUnderlined(true).setClickEvent(RMLTextEffects.ChangeModClickAction.CHANGE_MOD.makeEvent("rml"))));
                    if (containerHolder.packVersion < ResourceModLoader.PACK_VERSION) {
                        event.getTextComponents().add(new TextComponentTranslation("rml.legacy.pack.warn").setStyle(new Style().setColor(TextFormatting.RED)));
                    }
                }
            }
        }
    }

}
