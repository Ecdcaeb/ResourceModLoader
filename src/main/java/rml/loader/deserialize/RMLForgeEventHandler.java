package rml.loader.deserialize;

import com.cleanroommc.groovyscript.GroovyScript;
import crafttweaker.mc1120.CraftTweaker;
import dev.latvian.kubejs.KubeJS;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import rml.loader.api.annotations.BeDiscovered;
import rml.loader.api.annotations.OptionalAnnotation;
import rml.loader.api.utils.ClassHelper;
import rml.layer.compat.crt.RMLCrTLoader;
import rml.layer.compat.groovyscripts.RMLGrsLoader;
import rml.layer.compat.justenoughdimensions.JEDLoader;
import rml.layer.compat.kubejs.RMKKubeJs;
import rml.loader.ResourceModLoader;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.event.CraftingHelperInitEvent;
import rml.loader.api.event.FunctionLoadEvent;
import rml.loader.api.event.LootTableRegistryEvent;
import rml.loader.api.event.client.gui.ModMenuInfoEvent;
import rml.loader.api.utils.ObjectHelper;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.api.world.text.RMLTextEffects;
import rml.loader.core.RMLFMLLoadingPlugin;
import rml.loader.content.CompiledLootModule;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import rml.loader.api.world.villagers.IVillager;
import rml.loader.core.RMLModDiscover;
import rml.loader.core.RMLTransformer;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void loadFunction(FunctionLoadEvent event){
        ResourceModLoader.runtime().getFunctions().onLoad(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void runScheduledFunctions(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) ResourceModLoader.runtime().getFunctions().tick();
    }

    @SubscribeEvent
    public static void registerLootTable(LootTableRegistryEvent event){
        ResourceModLoader.runtime().getLootTables().load(event);
    }

    @SubscribeEvent
    public static void registerRecipeFactory(CraftingHelperInitEvent event){
        event.register(new ResourceLocation("rml","smelt"), new SmeltRecipe.Factory());
        event.register(new ResourceLocation("rml","brew"), new SimpleBrewRecipe.Factory());
        event.register(new ResourceLocation("rml","anvil"), new SimpleAnvilRecipe.Factory());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterRecipeRegister(RegistryEvent.Register<IRecipe> event){
        ResourceModLoader.runtime().getCompiledRecipes().register(event.getRegistry());
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
        for (IVillager village : ResourceModLoader.runtime().getVillagers().read()) {
            village.run(forgeRegistry);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerDataBiomes(RegistryEvent.Register<net.minecraft.world.biome.Biome> event) {
        ResourceModLoader.runtime().getWorldgen().initialize();
    }

    public static void construct(FMLConstructionEvent event){
        ResourceModLoader.runtime().getConfigs().loadDefinitions();
        ResourceModLoader.runtime().getConfigs().loadNodes();
        if (Loader.isModLoaded(GroovyScript.ID)){
            RMLGrsLoader.load();
        }
        ASMDataTable asmDataTable = event.getASMHarvestedData();
        asmDataTable.getAll(OptionalAnnotation.class.getCanonicalName())
                        .stream().map(data -> data.getClassName().replace('/', '.'))
                        .forEach(name -> RMLTransformer.register(name, OptionalAnnotation.Handler::handle));
        RMLModDiscover.discover(asmDataTable, BeDiscovered.MOD_LOADING);
        ClassHelper.forceInit(RMLDeserializer.class);
        ClassHelper.forceInit(MCDeserializers.class);
        ResourceModLoader.runtime().getContent().reload();

    }

    public static void preInit(FMLPreInitializationEvent event){
        MinecraftForge.EVENT_BUS.register(RMLForgeEventHandler.class);
        MinecraftForge.EVENT_BUS.register(SimpleAnvilRecipe.class);
        CompiledLootModule.registerConditions();
        ResourceModLoader.runtime().initializeNative();
        if (FMLLaunchHandler.side() == Side.CLIENT){
            ResourceModLoader.runtime().getGameplay().initializeClient();
            ClassHelper.forceInit(RMLTextEffects.ChangeModClickAction.class);
        }
        if (Loader.isModLoaded(KubeJS.MOD_ID)){
            MinecraftForge.EVENT_BUS.register(RMKKubeJs.class);
        }
        if (Loader.isModLoaded(CraftTweaker.MODID)){
            MinecraftForge.EVENT_BUS.register(RMLCrTLoader.class);
        }
        RMLModDiscover.discover(event.getAsmData(), BeDiscovered.PRE_INIT);
        ResourceModLoader.runtime().getRegistryRemaps().load();
    }

    public static void onInit(FMLInitializationEvent event){
        ResourceModLoader.runtime().getGameplay().initializeAfterRegistries();
        ResourceModLoader.runtime().getPresentation().initializeAfterRegistries();
        ResourceModLoader.runtime().getTimeTests().initialize();
        if (Loader.isModLoaded("justenoughdimensions")){
            JEDLoader.load();
        }
    }

    public static void postInit(FMLPostInitializationEvent event){
        ResourceModLoader.runtime().getOreDictionary().load();
        RMLFMLLoadingPlugin.Container.INSTANCE.getMetadata().childMods = ResourceModLoader.runtime().getPacks().all()
                .stream().map(ContainerHolder::getContainer).collect(java.util.stream.Collectors.toList());
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
                for (ContainerHolder holder : ResourceModLoader.runtime().getPacks().all()) {
                    ModContainer container = holder.getContainer();
                    event.getTextComponents().add(
                            new TextComponentString(container.getName())
                                    .setStyle(new Style().setColor(TextFormatting.BLUE).setUnderlined(true)
                                            .setClickEvent(RMLTextEffects.ChangeModClickAction.CHANGE_MOD.makeEvent(container.getModId()))
                                            .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(ObjectHelper.allToString("\n", holder.getModules()))))
                                    )
                    );
                }
            }else {
                ContainerHolder containerHolder = ResourceModLoader.runtime().getPacks().find(event.getMod().getModId());
                if (containerHolder != null) {
                    event.getTextComponents().add(new TextComponentTranslation("rml.gui.modmenu.enablerml").setStyle(new Style().setColor(TextFormatting.BLUE).setUnderlined(true).setClickEvent(RMLTextEffects.ChangeModClickAction.CHANGE_MOD.makeEvent("rml"))));
                    if (containerHolder.getPackVersion() < ResourceModLoader.PACK_VERSION) {
                        event.getTextComponents().add(new TextComponentTranslation("rml.legacy.pack.warn").setStyle(new Style().setColor(TextFormatting.RED)));
                    }
                }
            }
        }
    }

}
