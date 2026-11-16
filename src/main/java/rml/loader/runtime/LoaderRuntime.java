package rml.loader.runtime;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Logger;
import rml.loader.module.ConfigModule;
import rml.loader.module.LootTableModule;
import rml.loader.module.OreDictionaryModule;
import rml.loader.module.RegistryRemapModule;
import rml.loader.module.SplashTextModule;
import rml.loader.module.VillagerModule;
import rml.loader.content.CompiledAdvancementModule;
import rml.loader.content.CompiledLootModule;
import rml.loader.content.CompiledRecipeModule;
import rml.loader.content.CompiledStructureModule;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.ContentCatalog;
import rml.loader.content.ContentProviders;
import rml.loader.content.data.DataRules;
import rml.loader.content.data.DataContext;
import rml.loader.content.gameplay.GameplayModule;
import rml.loader.content.gameplay.MachineRecipe;
import rml.loader.content.presentation.PresentationModule;
import rml.loader.content.time.TimeTestModule;
import rml.loader.content.worldgen.WorldgenModule;

/** Process-level Forge services; world-scoped content has a separate lifetime. */
public final class LoaderRuntime {
    private final PackRegistry packs;
    private final ModuleLoader modules;
    private final FunctionModule functions;
    private final ConfigModule configs;
    private final LootTableModule lootTables;
    private final OreDictionaryModule oreDictionary;
    private final RegistryRemapModule registryRemaps;
    private final SplashTextModule splashTexts;
    private final VillagerModule villagers;
    private final ContentCatalog content;
    private final CompiledLootModule compiledLoot;
    private final CompiledRecipeModule compiledRecipes;
    private final CompiledAdvancementModule compiledAdvancements;
    private final CompiledStructureModule compiledStructures;
    private final CompiledTagModule compiledTags;
    private final DataRules dataRules;
    private final CompiledFunctionEngine functionEngine;
    private final GameplayModule gameplay;
    private final WorldgenModule worldgen;
    private final PresentationModule presentation;
    private final TimeTestModule timeTests;
    private final ContentProviders providers;

    public LoaderRuntime(Logger logger) {
        packs = new PackRegistry();
        modules = new ModuleLoader(packs, logger);
        content = new ContentCatalog(modules);
        compiledRecipes = new CompiledRecipeModule(content);
        compiledAdvancements = new CompiledAdvancementModule(content);
        compiledStructures = new CompiledStructureModule();
        compiledTags = new CompiledTagModule();
        dataRules = new DataRules(content, compiledTags, (id, context) -> getFunctions().invoke(id, context));
        compiledLoot = new CompiledLootModule(dataRules);
        functionEngine = new CompiledFunctionEngine(dataRules, id -> getWorldgen().getDimensionId(id));
        gameplay = new GameplayModule(content, dataRules, compiledTags);
        worldgen = new WorldgenModule(content, dataRules, compiledStructures, compiledTags);
        presentation = new PresentationModule(content, dataRules, compiledTags);
        timeTests = new TimeTestModule(content, dataRules, compiledTags, compiledStructures);
        gameplay.setTrimLookup(new MachineRecipe.TrimLookup() {
            @Override public ResourceLocation resolveTrimMaterial(ItemStack addition, DataContext context) {
                return presentation.resolveTrimMaterial(addition, context);
            }
            @Override public ResourceLocation resolveTrimPattern(ResourceLocation explicitPattern, ItemStack template) {
                return presentation.resolveTrimPattern(explicitPattern, template);
            }
        });
        providers = new ContentProviders();
        providers.registerStartup("recipe");
        providers.registerStartup("advancement");
        for (String type : gameplay.getTypes()) providers.registerStartup(type);
        for (String type : worldgen.getTypes()) providers.registerStartup(type);
        for (String type : presentation.getTypes()) providers.registerStartup(type);
        for (String type : timeTests.getTypes()) providers.registerStartup(type);
        for (String type : dataRules.getRuntimeTypes()) providers.registerRuntime(type, dataRules::prepare);
        providers.registerRuntime("loot_table", compiledLoot::prepare);
        providers.registerRuntime("predicate", compiledLoot::prepare);
        providers.registerRuntime("item_modifier", compiledLoot::prepare);
        providers.registerRuntime("structure", compiledStructures::prepare);
        functions = new FunctionModule(packs, modules, content, providers, compiledTags, functionEngine, logger);
        configs = new ConfigModule(modules);
        lootTables = new LootTableModule(modules);
        oreDictionary = new OreDictionaryModule(modules);
        registryRemaps = new RegistryRemapModule(modules);
        splashTexts = new SplashTextModule(modules);
        villagers = new VillagerModule(modules);
    }

    public PackRegistry getPacks() {
        return packs;
    }

    public ModuleLoader getModules() {
        return modules;
    }

    public FunctionModule getFunctions() {
        return functions;
    }

    public ConfigModule getConfigs() { return configs; }
    public LootTableModule getLootTables() { return lootTables; }
    public OreDictionaryModule getOreDictionary() { return oreDictionary; }
    public RegistryRemapModule getRegistryRemaps() { return registryRemaps; }
    public SplashTextModule getSplashTexts() { return splashTexts; }
    public VillagerModule getVillagers() { return villagers; }

    public ContentCatalog getContent() { return content; }
    public CompiledLootModule getCompiledLoot() { return compiledLoot; }
    public CompiledRecipeModule getCompiledRecipes() { return compiledRecipes; }
    public CompiledAdvancementModule getCompiledAdvancements() { return compiledAdvancements; }
    public CompiledStructureModule getCompiledStructures() { return compiledStructures; }
    public CompiledTagModule getCompiledTags() { return compiledTags; }
    public DataRules getDataRules() { return dataRules; }
    public CompiledFunctionEngine getFunctionEngine() { return functionEngine; }
    public GameplayModule getGameplay() { return gameplay; }
    public WorldgenModule getWorldgen() { return worldgen; }
    public PresentationModule getPresentation() { return presentation; }
    public TimeTestModule getTimeTests() { return timeTests; }
    public java.util.Set<String> getContentTypes() { return providers.getTypes(); }

    public void initializeNative() {
        rml.deserializer.DeserializerManager decoders = rml.loader.deserialize.Deserializer.MANAGER;
        rml.loader.deserialize.NativeTypeDecoders.register(decoders, compiledTags);
        dataRules.registerDecoders(decoders);
        gameplay.registerDecoders(decoders);
        worldgen.registerDecoders(decoders);
        presentation.registerDecoders(decoders);
        timeTests.registerDecoders(decoders);
        dataRules.registerNative();
        worldgen.initialize();
        presentation.initialize();
        gameplay.initialize();
        timeTests.initialize();
    }
}
