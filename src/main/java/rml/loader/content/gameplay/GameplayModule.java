package rml.loader.content.gameplay;

import net.minecraft.util.DamageSource;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import rml.loader.api.world.villagers.IVillager;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;
import rml.loader.content.CompiledResource;
import rml.loader.content.ContentCatalog;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;
import java.util.ArrayList;
import java.util.List;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Native gameplay definitions and their Forge event integrations. */
public final class GameplayModule {
    private static final Set<String> TYPES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "damage_type", "enchantment", "enchantment_provider", "villager_trade", "trade_set",
            "block_transformer", "slot_source", "trial_spawner", "sulfur_cube_archetype", "machine_recipe", "item", "block")));
    private static final Set<String> COMMANDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "damage", "enchant", "trade", "transform", "slot", "spawner", "station")));

    private final ContentCatalog catalog;
    private final DataRules rules;
    private final CompiledTagModule tags;
    private final Map<String, CompiledResource> definitions = new LinkedHashMap<>();
    private final Map<ResourceLocation, Enchantment> enchantments = new LinkedHashMap<>();
    private final Map<ResourceLocation, DamageDefinition> damageTypes = new LinkedHashMap<>();
    private final Map<ResourceLocation, SlotDefinition> slotSources = new LinkedHashMap<>();
    private final List<BlockTransformer> blockTransformers = new ArrayList<>();
    private final Map<ResourceLocation, EnchantmentProvider> enchantmentProviders = new LinkedHashMap<>();
    private final Map<ResourceLocation, TrialSpawnerDefinition> trialSpawners = new LinkedHashMap<>();
    private final Map<ResourceLocation, SulfurCubeArchetype> sulfurCubes = new LinkedHashMap<>();
    private final Map<ResourceLocation, MachineRecipe> machineRecipes = new LinkedHashMap<>();
    private boolean initialized;
    private boolean registryPrepared;
    private boolean declaresTrials, declaresMachines, declaresSulfur;
    private List<CompiledResource> declaredDefinitions = Collections.emptyList();
    private final Map<ResourceLocation, NativeItemDefinition> customItems = new LinkedHashMap<>();
    private final Map<ResourceLocation, NativeBlockDefinition> customBlocks = new LinkedHashMap<>();
    private DeserializerManager decoderManager;
    private final Map<ResourceLocation, NativeDataItem> itemInstances = new LinkedHashMap<>();
    private final Map<ResourceLocation, NativeDataBlock> blockInstances = new LinkedHashMap<>();
    private MachineRecipe.TrimLookup trimLookup;
    private final NativeEnchantmentRuntime enchantmentRuntime = new NativeEnchantmentRuntime(enchantments.values());
    private final EnchantmentProjectiles projectileRuntime = new EnchantmentProjectiles(enchantmentRuntime);

    public GameplayModule(ContentCatalog catalog, DataRules rules, CompiledTagModule tags) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.tags = Objects.requireNonNull(tags, "tags");
    }

    public Set<String> getTypes() { return TYPES; }
    public Set<String> getCommands() { return COMMANDS; }
    public void registerDecoders(DeserializerManager manager) {
        Objects.requireNonNull(manager, "manager");
        if (decoderManager != null && decoderManager != manager) throw new IllegalStateException("Gameplay decoders already bound to another manager");
        decoderManager = manager;
        EnchantmentEffectDecoders.register(manager);
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "attribute"), EnchantmentAttribute.class, json -> EnchantmentAttribute.decode(manager, json.getAsJsonObject())));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "trial_settings"), TrialSpawnerDefinition.Settings.class, json -> TrialSpawnerDefinition.Settings.decode(manager, json.getAsJsonObject())));
        GameplayTradeDecoders.register(manager);
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "slot_source"), SlotDefinition.class, json -> SlotDefinition.decode(json.getAsJsonObject())));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "equipment"), SlotDefinition.class, json -> SlotDefinition.equipment(json.getAsJsonObject())));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "inventory"), SlotDefinition.class, json -> SlotDefinition.inventory(json.getAsJsonObject())));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "first_match"), SlotDefinition.class, json -> SlotDefinition.firstMatch(manager, json.getAsJsonObject())));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "block_transformer"), BlockTransformer.class,
                json -> BlockTransformer.decode(manager, json.getAsJsonObject())));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "sequence"), BlockTransformer.class,
                json -> BlockTransformer.decode(manager, json.getAsJsonObject())));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "enchantment_provider"), EnchantmentProvider.class,
                json -> EnchantmentProvider.decode(manager, json.getAsJsonObject())));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "enchantment_choice"), EnchantmentProvider.Choice.class,
                json -> EnchantmentProvider.Choice.decode(manager, json.getAsJsonObject())));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "damage_type"), DamageDefinition.class,
                json -> DamageDefinition.decode(manager, json.getAsJsonObject())));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "machine_recipe"), MachineRecipe.class,
                json -> MachineRecipe.decode(manager, new ResourceLocation("rml", "anonymous_recipe"), json.getAsJsonObject(), trimLookup)));
        for (String operation : new String[]{"blasting", "smoking", "campfire_cooking", "stonecutting", "smithing_transform", "smithing_trim", "crafting_transmute"}) {
            manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", operation), MachineRecipe.class,
                    json -> MachineRecipe.decode(manager, new ResourceLocation("rml", "anonymous_recipe"), json.getAsJsonObject(), trimLookup)));
        }
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "item_definition"), NativeItemDefinition.class,
                json -> NativeItemDefinition.decode(manager, manager.decode(ResourceLocation.class, json.getAsJsonObject().get("id")), json.getAsJsonObject())));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "block_definition"), NativeBlockDefinition.class,
                json -> NativeBlockDefinition.decode(manager, manager.decode(ResourceLocation.class, json.getAsJsonObject().get("id")), json.getAsJsonObject())));
    }
    public synchronized void setTrimLookup(MachineRecipe.TrimLookup trimLookup) {
        if (initialized) throw new IllegalStateException("Trim resolver must be supplied before GameplayModule.initializeAfterRegistries");
        this.trimLookup = Objects.requireNonNull(trimLookup, "trimLookup");
    }

    /** Declare registry identities first; item/block/entity-dependent children bind after registry events. */
    public synchronized void initialize() {
        if (registryPrepared) return;
        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
        List<CompiledResource> declarations = new ArrayList<>();
        for (String type : TYPES) declarations.addAll(catalog.snapshot().resources(type).values());
        boolean trials = !catalog.snapshot().resources("trial_spawner").isEmpty();
        boolean machines = !catalog.snapshot().resources("machine_recipe").isEmpty();
        boolean sulfur = !catalog.snapshot().resources("sulfur_cube_archetype").isEmpty();
        Map<ResourceLocation, NativeItemDefinition> items = new LinkedHashMap<>();
        Map<ResourceLocation, NativeBlockDefinition> blocks = new LinkedHashMap<>();
        Map<ResourceLocation, Enchantment> enchantmentDeclarations = new LinkedHashMap<>();
        for (CompiledResource resource : declarations) {
            try {
                switch (resource.getType()) {
                    case "item": items.put(resource.getId(), NativeItemDefinition.decodeForRegistration(decoderManager, resource.getId(), object(resource))); break;
                    case "block": blocks.put(resource.getId(), NativeBlockDefinition.decodeForRegistration(decoderManager, resource.getId(), object(resource))); break;
                    case "enchantment": enchantmentDeclarations.put(resource.getId(), new NativeEnchantment(resource.getId(), object(resource), decoderManager, true)); break;
                    default: break;
                }
            } catch (Exception cause) { throw failure(resource, "invalid registry declaration: " + cause.getMessage()); }
        }
        for (ResourceLocation id : items.keySet()) {
            if (blocks.containsKey(id)) throw failure(find(declarations, "item", id), "item and block definitions cannot share a registry ID");
            if (isBuiltInBlock(id, trials, machines)) throw failure(find(declarations, "item", id), "item ID conflicts with a gameplay machine block");
        }
        for (ResourceLocation id : blocks.keySet()) if (isBuiltInBlock(id, trials, machines)) throw failure(find(declarations, "block", id), "block ID conflicts with a gameplay machine block");
        customItems.putAll(items);
        customBlocks.putAll(blocks);
        enchantments.putAll(enchantmentDeclarations);
        declaredDefinitions = Collections.unmodifiableList(declarations);
        declaresTrials = trials; declaresMachines = machines; declaresSulfur = sulfur;
        if (trials) GameRegistry.registerTileEntity(TrialSpawnerTileEntity.class, new ResourceLocation("rml", "trial_spawner"));
        if (machines) GameRegistry.registerTileEntity(StationTileEntity.class, new ResourceLocation("rml", "machine_station"));
        if (sulfur) FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        registryPrepared = true;
    }

    /** Called once from FMLInitializationEvent, after every Forge registry event. */
    public synchronized void initializeAfterRegistries() {
        if (initialized) return;
        if (!registryPrepared) throw new IllegalStateException("GameplayModule.initialize must declare native registries first");
        List<CompiledResource> staged = declaredDefinitions;
        Map<ResourceLocation, Enchantment> stagedEnchantments = new LinkedHashMap<>();
        Map<ResourceLocation, SlotDefinition> stagedSlots = new LinkedHashMap<>();
        Map<ResourceLocation, DamageDefinition> stagedDamage = new LinkedHashMap<>();
        List<BlockTransformer> stagedTransformers = new ArrayList<>();
        Map<ResourceLocation, EnchantmentProvider> stagedProviders = new LinkedHashMap<>();
        Map<ResourceLocation, TrialSpawnerDefinition> stagedSpawners = new LinkedHashMap<>();
        Map<ResourceLocation, SulfurCubeArchetype> stagedSulfurCubes = new LinkedHashMap<>();
        Map<ResourceLocation, MachineRecipe> stagedRecipes = new LinkedHashMap<>();
        Map<ResourceLocation, NativeItemDefinition> stagedItems = new LinkedHashMap<>();
        Map<ResourceLocation, NativeBlockDefinition> stagedBlocks = new LinkedHashMap<>();
        for (CompiledResource resource : staged) {
            JsonObject json = object(resource);
            switch (resource.getType()) {
                case "enchantment":
                    try {
                        NativeEnchantment enchantment = (NativeEnchantment) enchantments.get(resource.getId());
                        enchantment.resolveDependencies(decoderManager);
                        stagedEnchantments.put(resource.getId(), enchantment);
                    } catch (Exception cause) {
                        throw failure(resource, "invalid enchantment: " + cause.getMessage());
                    }
                    break;
                case "enchantment_provider":
                    try {
                        stagedProviders.put(resource.getId(), decoderManager.decode(EnchantmentProvider.class, resource.getValue()));
                    } catch (Exception cause) {
                        throw failure(resource, "invalid enchantment provider: " + cause.getMessage());
                    }
                    break;
                case "damage_type":
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        stagedDamage.put(resource.getId(), decoderManager.decode(DamageDefinition.class, resource.getValue()));
                    } catch (Exception cause) {
                        throw failure(resource, "invalid damage type: " + cause.getMessage());
                    }
                    break;
                case "slot_source":
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        stagedSlots.put(resource.getId(), decoderManager.decode(SlotDefinition.class, resource.getValue()));
                    } catch (Exception cause) {
                        throw failure(resource, "invalid slot source: " + cause.getMessage());
                    }
                    break;
                case "villager_trade":
                case "trade_set":
                    break;
                case "block_transformer":
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        stagedTransformers.add(decoderManager.decode(BlockTransformer.class, resource.getValue()));
                    } catch (Exception cause) {
                        throw failure(resource, "invalid block transformer: " + cause.getMessage());
                    }
                    break;
                case "trial_spawner":
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        stagedSpawners.put(resource.getId(), TrialSpawnerDefinition.decode(decoderManager, resource.getId(), json));
                    } catch (Exception cause) {
                        throw failure(resource, "invalid trial spawner: " + cause.getMessage());
                    }
                    break;
                case "sulfur_cube_archetype":
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        stagedSulfurCubes.put(resource.getId(), SulfurCubeArchetype.decode(decoderManager, resource.getId(), json));
                    } catch (Exception cause) {
                        throw failure(resource, "invalid sulfur cube archetype: " + cause.getMessage());
                    }
                    break;
                case "machine_recipe":
                    try {
                        if ("smithing_trim".equals(decoderManager.decode(ResourceLocation.class, json.get("type")).getPath()) && trimLookup == null) {
                            throw new IllegalStateException("Presentation trim resolver was not supplied");
                        }
                        stagedRecipes.put(resource.getId(), MachineRecipe.decode(decoderManager, resource.getId(), json, trimLookup));
                    } catch (Exception cause) {
                        throw failure(resource, "invalid machine recipe: " + cause.getMessage());
                    }
                    break;
                case "item":
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        NativeItemDefinition item = customItems.get(resource.getId());
                        item.resolveDependencies(decoderManager);
                        stagedItems.put(resource.getId(), item);
                    } catch (Exception cause) {
                        throw failure(resource, "invalid native item: " + cause.getMessage());
                    }
                    break;
                case "block":
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        NativeBlockDefinition block = customBlocks.get(resource.getId());
                        block.resolveDependencies(decoderManager);
                        stagedBlocks.put(resource.getId(), block);
                    } catch (Exception cause) {
                        throw failure(resource, "invalid native block: " + cause.getMessage());
                    }
                    break;
                default:
                    throw failure(resource, "Unsupported gameplay type");
            }
        }
        for (ResourceLocation id : stagedItems.keySet()) {
            if (stagedBlocks.containsKey(id)) throw failure(find(staged, "item", id), "item and block definitions cannot share a registry ID");
            if (isBuiltInBlock(id, !stagedSpawners.isEmpty(), !stagedRecipes.isEmpty())) throw failure(find(staged, "item", id), "item ID conflicts with a gameplay machine block");
        }
        for (ResourceLocation id : stagedBlocks.keySet()) {
            if (isBuiltInBlock(id, !stagedSpawners.isEmpty(), !stagedRecipes.isEmpty())) throw failure(find(staged, "block", id), "block ID conflicts with a gameplay machine block");
        }
        customItems.putAll(stagedItems);
        customBlocks.putAll(stagedBlocks);
        sulfurCubes.putAll(stagedSulfurCubes);
        SulfurCubeEntity.setArchetypes(sulfurCubes);
        for (CompiledResource resource : staged) definitions.put(definitionKey(resource.getType(), resource.getId()), resource);
        enchantments.putAll(stagedEnchantments);
        damageTypes.putAll(stagedDamage);
        slotSources.putAll(stagedSlots);
        blockTransformers.addAll(stagedTransformers);
        enchantmentProviders.putAll(stagedProviders);
        trialSpawners.putAll(stagedSpawners);
        machineRecipes.putAll(stagedRecipes);
        StationTileEntity.setRecipes(machineRecipes);
        TrialSpawnerTileEntity.setDefinitions(trialSpawners);
        enchantmentRuntime.resolveComponents();
        initialized = true;
    }
    public void initializeClient() {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(GameplayClient.class);
            if (declaresSulfur) SulfurCubeClient.register();
        }
    }
    public DamageSource createDamageSource(ResourceLocation type, Entity attacker) {
        DamageDefinition definition = damageTypes.get(type);
        if (definition == null) throw new IllegalArgumentException("Unknown damage type: " + type);
        return definition.source(type.toString(), attacker);
    }
    public ItemStack applyEnchantmentProvider(ResourceLocation id, ItemStack stack, java.util.Random random) {
        EnchantmentProvider provider = enchantmentProviders.get(id);
        if (provider == null) throw new IllegalArgumentException("Unknown enchantment provider: " + id);
        return provider.apply(stack, random);
    }
    /** Resolves a compiled equipment or player inventory slot against a live entity. */
    public ItemStack resolveSlot(Entity entity, ResourceLocation source) {
        SlotDefinition definition = slotSources.get(source);
        if (definition == null) throw new IllegalArgumentException("Unknown slot source: " + source);
        return definition.resolve(entity);
    }

    @SubscribeEvent
    public void registerEnchantments(RegistryEvent.Register<Enchantment> event) {
        IForgeRegistry<Enchantment> registry = event.getRegistry();
        for (Map.Entry<ResourceLocation, Enchantment> entry : enchantments.entrySet()) {
            if (registry.containsKey(entry.getKey())) throw new IllegalArgumentException("Enchantment already registered: " + entry.getKey());
            registry.register(entry.getValue());
        }
    }

    @SubscribeEvent
    public void registerGameplayBlocks(RegistryEvent.Register<Block> event) {
        if (declaresTrials) {
            if (event.getRegistry().containsKey(TrialSpawnerBlock.INSTANCE.getRegistryName())) throw new IllegalArgumentException("Block ID is already registered: rml:trial_spawner");
            event.getRegistry().register(TrialSpawnerBlock.INSTANCE);
        }
        if (declaresMachines) {
            for (StationBlock block : StationBlock.ALL) {
                if (event.getRegistry().containsKey(block.getRegistryName())) throw new IllegalArgumentException("Block ID is already registered: " + block.getRegistryName());
                event.getRegistry().register(block);
            }
        }
        for (Map.Entry<ResourceLocation, NativeBlockDefinition> entry : customBlocks.entrySet()) {
            if (event.getRegistry().containsKey(entry.getKey())) throw new IllegalArgumentException("Block ID is already registered: " + entry.getKey());
            NativeDataBlock block = new NativeDataBlock(entry.getValue());
            blockInstances.put(entry.getKey(), block);
            event.getRegistry().register(block);
        }
    }

    @SubscribeEvent
    public void registerGameplayItems(RegistryEvent.Register<Item> event) {
        if (declaresTrials) {
            ItemBlock item = new ItemBlock(TrialSpawnerBlock.INSTANCE);
            item.setRegistryName(TrialSpawnerBlock.INSTANCE.getRegistryName());
            event.getRegistry().register(item);
        }
        if (declaresMachines) {
            for (StationBlock block : StationBlock.ALL) {
                ItemBlock item = new ItemBlock(block);
                item.setRegistryName(block.getRegistryName());
                event.getRegistry().register(item);
            }
        }
        for (Map.Entry<ResourceLocation, NativeItemDefinition> entry : customItems.entrySet()) {
            if (event.getRegistry().containsKey(entry.getKey())) throw new IllegalArgumentException("Item ID is already registered: " + entry.getKey());
            NativeDataItem item = new NativeDataItem(entry.getValue());
            itemInstances.put(entry.getKey(), item);
            event.getRegistry().register(item);
        }
        for (NativeDataBlock block : blockInstances.values()) {
            ItemBlock item = new NativeBlockItem(block);
            item.setRegistryName(block.getRegistryName());
            event.getRegistry().register(item);
        }
    }
    @SubscribeEvent
    public void registerSulfurEntity(RegistryEvent.Register<net.minecraftforge.fml.common.registry.EntityEntry> event) {
        if (!declaresSulfur) return;
        net.minecraftforge.fml.common.registry.EntityEntry entry = EntityEntryBuilder.create()
                .entity(SulfurCubeEntity.class)
                .id(new ResourceLocation("rml", "sulfur_cube"), 1)
                .name("sulfur_cube")
                .tracker(64, 3, true)
                .build();
        event.getRegistry().register(entry);
    }
    @SubscribeEvent
    public void registerVillagerProfessions(RegistryEvent.Register<VillagerRegistry.VillagerProfession> event) {
        // Forge fires block and item registry events before the remaining native registries.
        // Offers can now resolve same-catalog item stacks, while profession installers can still register identities.
        List<IVillager> staged = new ArrayList<>();
        for (CompiledResource resource : declaredDefinitions) {
            if (!"villager_trade".equals(resource.getType()) && !"trade_set".equals(resource.getType())) continue;
            try {
                JsonObject trade = object(resource);
                if ("trade_set".equals(resource.getType()) && !trade.has("type")) {
                    JsonObject tagged = new JsonObject();
                    for (Map.Entry<String, JsonElement> field : trade.entrySet()) tagged.add(field.getKey(), field.getValue());
                    tagged.addProperty("type", "rml:trade_set");
                    trade = tagged;
                }
                staged.add(decoderManager.decode(IVillager.class, trade));
            } catch (Exception cause) { throw failure(resource, "invalid native villager trade: " + cause.getMessage()); }
        }
        for (IVillager trade : staged) trade.run(event.getRegistry());
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.PlaceEvent event) {
        if (!event.getWorld().isRemote) transformBlock((net.minecraft.world.WorldServer) event.getWorld(), event.getPos(), event.getPlayer());
    }

    public boolean transformBlock(net.minecraft.world.WorldServer world, net.minecraft.util.math.BlockPos pos, Entity actor) {
        DataContext context = new DataContext(world, actor, pos, world.rand, 0);
        for (BlockTransformer transformer : blockTransformers) if (transformer.apply(context)) return true;
        return false;
    }

    public ItemStack applyEnchantmentProvider(ResourceLocation id, ItemStack stack, DataContext context) {
        EnchantmentProvider provider = enchantmentProviders.get(id);
        if (provider == null) throw new IllegalArgumentException("Unknown enchantment provider: " + id);
        return provider.apply(stack, context);
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote) return;
        DamageDefinition definition = damageTypes.get(event.getSource().getDamageType() == null
                ? null : new ResourceLocation(event.getSource().getDamageType()));
        if (definition == null) return;
        if (definition.attackEffect != null) {
            Entity attacker = event.getSource().getTrueSource();
            definition.attackEffect.apply(context(victim, attacker), victim);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote) return;
        DamageSource source = event.getSource();
        String damageName = source.getDamageType();
        DamageDefinition definition = damageName == null ? null : damageTypes.get(new ResourceLocation(damageName));
        float amount = event.getAmount();
        Entity attacker = source.getTrueSource();
        if (attacker instanceof EntityLivingBase) amount = enchantmentRuntime.modify("damage", amount, (EntityLivingBase) attacker, source);
        if (definition == null) {
            event.setAmount(Math.max(0, amount));
            return;
        }
        if (definition.damageMultiplier != null) amount *= definition.damageMultiplier.evaluate(context(victim, attacker));
        if (definition.hurtEffect != null) definition.hurtEffect.apply(context(victim, attacker), victim);
        event.setAmount(Math.max(0, amount));
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void onLivingDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote || event.getAmount() <= 0) return;
        float protection = enchantmentRuntime.modify("protection", 0, victim, event.getSource());
        protection = enchantmentRuntime.modify("damage_protection", protection, victim, event.getSource());
        event.setAmount(event.getAmount() * (1 - Math.min(20, Math.max(0, protection)) / 25));
        Entity attacker = event.getSource().getTrueSource();
        if (attacker instanceof EntityLivingBase) enchantmentRuntime.execute("post_attack", (EntityLivingBase) attacker, attacker, victim, event.getSource());
        enchantmentRuntime.execute("post_defense", victim, attacker, victim, event.getSource());
    }

    @SubscribeEvent
    public void onLivingUpdate(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        if (!event.getEntityLiving().world.isRemote) enchantmentRuntime.tick(event.getEntityLiving());
    }

    @SubscribeEvent
    public void onEquipmentChanged(net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent event) {
        if (!event.getEntityLiving().world.isRemote) enchantmentRuntime.updateAttributes(event.getEntityLiving(), false);
    }

    @SubscribeEvent
    public void onPickupExperience(net.minecraftforge.event.entity.player.PlayerPickupXpEvent event) {
        enchantmentRuntime.pickup(event.getEntityPlayer(), event.getOrb());
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void onTrialMobDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        TrialSpawnerTileEntity.onMobDeath(event.getEntityLiving());
        if (!event.getEntityLiving().world.isRemote) enchantmentRuntime.updateAttributes(event.getEntityLiving(), true);
    }

    /** Called at ItemStack.damageItem entry, before vanilla Unbreaking. */
    public int modifyItemDamage(ItemStack stack, int amount, EntityLivingBase entity) {
        return enchantmentRuntime.itemDamage(stack, amount, entity);
    }

    public boolean hasNativeEnchantment(ItemStack stack) { return enchantmentRuntime.hasEnchantment(stack); }

    public boolean spawnEnchantedProjectile(net.minecraft.world.World world, Entity projectile, EntityLivingBase shooter, ItemStack weapon, ItemStack ammunition) {
        return projectileRuntime.spawn(world, projectile, shooter, weapon, ammunition);
    }

    public void consumeEnchantedAmmunition(ItemStack ammunition, int amount, ItemStack weapon, EntityLivingBase shooter) {
        projectileRuntime.consume(ammunition, amount, weapon, shooter);
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw new CommandException("Gameplay command requires a namespace");
        Entity actor = sender.getCommandSenderEntity();
        switch (args[0]) {
            case "damage":
                if (args.length != 3) throw new CommandException("Usage: damage <type> <amount>");
                if (actor == null) throw new CommandException("This damage command requires an entity sender");
                try {
                    float amount = Float.parseFloat(args[2]);
                    if (amount <= 0 || Float.isInfinite(amount) || Float.isNaN(amount)) throw new NumberFormatException();
                    actor.attackEntityFrom(createDamageSource(new ResourceLocation(args[1]), actor), amount);
                    return;
                } catch (IllegalArgumentException cause) {
                    throw new CommandException("Unknown damage type or invalid amount: %s", args[1]);
                }
            case "enchant":
                if (args.length != 2) throw new CommandException("Usage: enchant <enchantment_provider>");
                if (!(actor instanceof EntityPlayer)) throw new CommandException("This enchant command requires a player sender");
                EntityPlayer player = (EntityPlayer) actor;
                ResourceLocation providerId = new ResourceLocation(args[1]);
                ItemStack enchanted = applyEnchantmentProvider(providerId, player.getHeldItemMainhand(), context(player, player));
                player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, enchanted);
                return;
            case "slot":
                if (args.length != 2 || actor == null) throw new CommandException("Usage: slot <slot_source> (entity sender required)");
                ItemStack selected = resolveSlot(actor, new ResourceLocation(args[1]));
                sender.sendMessage(new net.minecraft.util.text.TextComponentString(selected.toString()));
                return;
            case "transform":
                if (actor == null) throw new CommandException("This transform command requires an entity sender");
                if (!(actor.world instanceof net.minecraft.world.WorldServer) || !transformBlock((net.minecraft.world.WorldServer) actor.world, sender.getPosition(), actor)) throw new CommandException("No transformer matches the block at your position");
                return;
            case "spawner":
                if (args.length != 2 || actor == null) throw new CommandException("Usage: spawner <definition> (entity sender required)");
                ResourceLocation spawnerId = new ResourceLocation(args[1]);
                if (!trialSpawners.containsKey(spawnerId)) throw new CommandException("Unknown trial spawner: %s", args[1]);
                net.minecraft.tileentity.TileEntity tile = actor.world.getTileEntity(sender.getPosition());
                if (!(tile instanceof TrialSpawnerTileEntity)) tile = actor.world.getTileEntity(sender.getPosition().down());
                if (!(tile instanceof TrialSpawnerTileEntity)) throw new CommandException("Stand on or target a trial spawner block");
                ((TrialSpawnerTileEntity) tile).setDefinition(spawnerId);
                return;
            case "station":
                if (args.length != 2 || actor == null) throw new CommandException("Usage: station <recipe_id> (entity sender required)");
                ResourceLocation recipeId = new ResourceLocation(args[1]);
                MachineRecipe recipe = machineRecipes.get(recipeId);
                if (recipe == null) throw new CommandException("Unknown machine recipe: %s", args[1]);
                net.minecraft.tileentity.TileEntity station = actor.world.getTileEntity(sender.getPosition());
                if (!(station instanceof StationTileEntity)) station = actor.world.getTileEntity(sender.getPosition().down());
                if (!(station instanceof StationTileEntity)) throw new CommandException("Stand on or target a machine station");
                ((StationTileEntity) station).setInventorySlotContents(4, recipe.result.copy());
                return;
            case "trade":
                ResourceLocation tradeId = args.length > 1 ? new ResourceLocation(args[1]) : null;
                if (tradeId == null || (!definitions.containsKey(definitionKey("villager_trade", tradeId)) && !definitions.containsKey(definitionKey("trade_set", tradeId)))) throw new CommandException("Unknown trade definition: %s", args.length > 1 ? args[1] : "");
                sender.sendMessage(new net.minecraft.util.text.TextComponentString("Native villager trade registered: " + args[1]));
                return;
            default:
                throw new CommandException("Unknown gameplay command: %s", args[0]);
        }
    }

    private static DataContext context(Entity target, Entity source) {
        return new DataContext(target.world instanceof net.minecraft.world.WorldServer
                ? (net.minecraft.world.WorldServer) target.world : null, source, target.getPosition(), target.world.rand, 0);
    }

    private static JsonObject object(CompiledResource resource) {
        JsonElement value = resource.getValue();
        if (!value.isJsonObject()) throw failure(resource, "Expected an object");
        return value.getAsJsonObject();
    }

    private static IllegalArgumentException failure(CompiledResource resource, String message) {
        return new IllegalArgumentException(resource.getSource() + ": " + resource.getType() + " " + resource.getId() + ": " + message);
    }
    private static CompiledResource find(List<CompiledResource> resources, String type, ResourceLocation id) {
        for (CompiledResource resource : resources) if (resource.getType().equals(type) && resource.getId().equals(id)) return resource;
        throw new IllegalStateException("Missing staged resource " + type + " " + id);
    }
    private static boolean isBuiltInBlock(ResourceLocation id, boolean hasTrialSpawners, boolean hasMachines) {
        if (!"rml".equals(id.getNamespace())) return false;
        if (hasTrialSpawners && "trial_spawner".equals(id.getPath())) return true;
        return hasMachines && Arrays.asList("blast_furnace", "smoker", "campfire", "stonecutter", "smithing_table").contains(id.getPath());
    }

    private static int integer(JsonObject json, String key, int fallback) { return json.has(key) ? json.get(key).getAsInt() : fallback; }
    private static String string(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }

    public static final class EnchantmentProvider {
        private final List<Choice> choices;
        private final rml.deserializer.value.IntProvider count, cost;
        private final rml.deserializer.value.LevelBasedValue difficultyCost;
        private EnchantmentProvider(List<Choice> choices, rml.deserializer.value.IntProvider count, rml.deserializer.value.IntProvider cost, rml.deserializer.value.LevelBasedValue difficultyCost) {
            this.choices = Collections.unmodifiableList(choices); this.count = count; this.cost = cost; this.difficultyCost = difficultyCost;
        }
        public static EnchantmentProvider decode(DeserializerManager manager, JsonObject json) throws rml.deserializer.JsonDeserializeException {
            GameplayFields.only(json, "entries", "count", "cost", "difficulty_cost");
            if (!json.has("entries") || !json.get("entries").isJsonArray()) throw new IllegalArgumentException("enchantment provider requires entries array");
            List<Choice> choices = new ArrayList<>();
            long total = 0;
            for (JsonElement entry : json.getAsJsonArray("entries")) {
                Choice choice = manager.decode(Choice.class, entry);
                total += choice.weight;
                if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("enchantment provider total weight exceeds integer range");
                choices.add(choice);
            }
            if (choices.isEmpty()) throw new IllegalArgumentException("enchantment provider entries must not be empty");
            rml.deserializer.value.IntProvider count = json.has("count") ? manager.decode(rml.deserializer.value.IntProvider.class, json.get("count")) : new rml.deserializer.value.IntProvider(1, 1, random -> 1);
            rml.deserializer.value.IntProvider cost = json.has("cost") ? manager.decode(rml.deserializer.value.IntProvider.class, json.get("cost")) : null;
            if (count.getMinValue() < 0 || cost != null && cost.getMinValue() < 0) throw new IllegalArgumentException("Provider count and cost must be nonnegative");
            rml.deserializer.value.LevelBasedValue difficulty = json.has("difficulty_cost") ? manager.decode(rml.deserializer.value.LevelBasedValue.class, json.get("difficulty_cost")) : level -> 0;
            return new EnchantmentProvider(choices, count, cost, difficulty);
        }
        public ItemStack apply(ItemStack original, java.util.Random random) {
            return apply(original, new DataContext(null, null, null, random, 0));
        }
        public ItemStack apply(ItemStack original, DataContext context) {
            Objects.requireNonNull(original, "stack"); Objects.requireNonNull(context, "context");
            ItemStack result = original.copy();
            if (result.isEmpty()) return result;
            boolean book = result.getItem() == net.minecraft.init.Items.BOOK || result.getItem() == net.minecraft.init.Items.ENCHANTED_BOOK;
            int difficulty = context.getWorld() == null || context.getOrigin() == null ? 0 : (int) Math.ceil(context.getWorld().getDifficultyForLocation(context.getOrigin()).getAdditionalDifficulty());
            float extra = difficultyCost.calculate(difficulty);
            if (!Float.isFinite(extra)) throw new IllegalStateException("Nonfinite provider difficulty cost");
            int budget = cost == null ? -1 : Math.max(0, (int) Math.min(Integer.MAX_VALUE, cost.sample(context.getRandom()) + (double) extra));
            int selections = Math.min(choices.size(), count.sample(context.getRandom()));
            List<Choice> remaining = new ArrayList<>(choices);
            Map<Enchantment, Integer> installed = EnchantmentHelper.getEnchantments(result);
            for (int pick = 0; pick < selections; pick++) {
                List<Choice> eligible = new ArrayList<>();
                List<Integer> levels = new ArrayList<>();
                int weight = 0;
                for (Choice choice : remaining) {
                    Enchantment enchantment = net.minecraftforge.fml.common.registry.ForgeRegistries.ENCHANTMENTS.getValue(choice.enchantment);
                    if (enchantment == null) throw new IllegalStateException("Unknown enchantment in provider: " + choice.enchantment);
                    if (!choice.condition.test(context) || (book ? !enchantment.isAllowedOnBooks() : !enchantment.canApply(result)) || installed.containsKey(enchantment)) continue;
                    boolean compatible = true;
                    for (Enchantment present : installed.keySet()) if (!enchantment.isCompatibleWith(present)) { compatible = false; break; }
                    if (!compatible) continue;
                    int level = budget >= 0 && !choice.explicitLevel ? enchantment.getMaxLevel() : Math.min(enchantment.getMaxLevel(), choice.levels.sample(context.getRandom()));
                    if (budget >= 0) while (level >= enchantment.getMinLevel() && (budget < enchantment.getMinEnchantability(level) || budget > enchantment.getMaxEnchantability(level))) level--;
                    if (level < enchantment.getMinLevel()) continue;
                    eligible.add(choice); levels.add(level); weight += choice.weight;
                }
                if (eligible.isEmpty()) break;
                int selected = context.getRandom().nextInt(weight), index = 0;
                for (; index < eligible.size() - 1; index++) { selected -= eligible.get(index).weight; if (selected < 0) break; }
                Choice choice = eligible.get(index);
                installed.put(net.minecraftforge.fml.common.registry.ForgeRegistries.ENCHANTMENTS.getValue(choice.enchantment), levels.get(index));
                remaining.remove(choice);
            }
            if (result.getItem() == net.minecraft.init.Items.BOOK && !installed.isEmpty()) {
                ItemStack enchantedBook = new ItemStack(net.minecraft.init.Items.ENCHANTED_BOOK, result.getCount());
                if (result.hasTagCompound()) enchantedBook.setTagCompound(result.getTagCompound().copy());
                result = enchantedBook;
            }
            EnchantmentHelper.setEnchantments(installed, result);
            return result;
        }
        public static final class Choice {
            public final ResourceLocation enchantment;
            public final int level;
            public final int weight;
            private final rml.deserializer.value.IntProvider levels;
            private final DataRules.Test condition;
            private final boolean explicitLevel;
            private Choice(ResourceLocation enchantment, rml.deserializer.value.IntProvider levels, int weight, DataRules.Test condition, boolean explicitLevel) {
                this.enchantment = enchantment; this.level = levels.getMinValue(); this.levels = levels; this.weight = weight; this.condition = condition; this.explicitLevel = explicitLevel;
            }
            public static Choice decode(DeserializerManager manager, JsonObject json) throws rml.deserializer.JsonDeserializeException {
                GameplayFields.only(json, "enchantment", "level", "weight", "conditions");
                ResourceLocation enchantment = manager.decode(ResourceLocation.class, json.get("enchantment"));
                if (!net.minecraftforge.fml.common.registry.ForgeRegistries.ENCHANTMENTS.containsKey(enchantment)) throw new IllegalArgumentException("Unknown enchantment in provider: " + enchantment);
                rml.deserializer.value.IntProvider level = json.has("level") ? manager.decode(rml.deserializer.value.IntProvider.class, json.get("level")) : new rml.deserializer.value.IntProvider(1, 1, random -> 1);
                int weight = json.has("weight") ? manager.decode(Integer.class, json.get("weight")) : 1;
                if (enchantment == null || level.getMinValue() < 1 || weight < 1) throw new IllegalArgumentException("enchantment choice requires an id and positive level/weight");
                DataRules.Test condition = json.has("conditions") ? manager.decode(DataRules.Test.class, json.get("conditions")) : context -> true;
                return new Choice(enchantment, level, weight, condition, json.has("level"));
            }
        }
    }

    public static final class BlockTransformer {
        public final ResourceLocation from;
        public final ResourceLocation to;
        private final rml.loader.deserialize.NativeTypeDecoders.BlockMatcher predicate;
        private final net.minecraft.block.state.IBlockState state;
        private final DataRules.Test condition;
        private final DataRules.Effect effect;
        private final BlockTransformer[] sequence;
        private BlockTransformer(ResourceLocation from, net.minecraft.block.state.IBlockState state, rml.loader.deserialize.NativeTypeDecoders.BlockMatcher predicate,
                                 DataRules.Test condition, DataRules.Effect effect, BlockTransformer[] sequence) {
            this.from = from; this.to = state == null ? null : state.getBlock().getRegistryName(); this.state = state;
            this.predicate = predicate; this.condition = condition; this.effect = effect; this.sequence = sequence;
        }
        public static BlockTransformer decode(DeserializerManager manager, JsonObject json) throws rml.deserializer.JsonDeserializeException {
            GameplayFields.only(json, "from", "to", "predicate", "conditions", "effect", "steps");
            DataRules.Test condition = json.has("conditions") ? manager.decode(DataRules.Test.class, json.get("conditions")) : context -> true;
            DataRules.Effect effect = json.has("effect") ? manager.decode(DataRules.Effect.class, json.get("effect")) : null;
            if (json.has("steps")) {
                if (json.has("from") || json.has("to") || json.has("predicate")) throw new IllegalArgumentException("Sequence transformer cannot also declare from/to/predicate");
                return new BlockTransformer(null, null, null, condition, effect, manager.decode(BlockTransformer[].class, json.get("steps")));
            }
            ResourceLocation from = json.has("from") ? manager.decode(ResourceLocation.class, json.get("from")) : null;
            if (from != null && !Block.REGISTRY.containsKey(from)) throw new IllegalArgumentException("Unknown transformer block: " + from);
            rml.loader.deserialize.NativeTypeDecoders.BlockMatcher predicate = json.has("predicate") ? manager.decode(rml.loader.deserialize.NativeTypeDecoders.BlockMatcher.class, json.get("predicate")) : (world, pos, old) -> true;
            if (from == null && !json.has("predicate")) throw new IllegalArgumentException("Transformer requires from or predicate");
            net.minecraft.block.state.IBlockState state = manager.decode(net.minecraft.block.state.IBlockState.class, json.get("to"));
            return new BlockTransformer(from, state, predicate, condition, effect, null);
        }
        public boolean apply(DataContext context) {
            net.minecraft.world.WorldServer world = context.getWorld();
            net.minecraft.util.math.BlockPos pos = context.getOrigin();
            if (world == null || pos == null || pos.getY() < 0 || pos.getY() > 255 || !world.isBlockLoaded(pos) || !condition.test(context)) return false;
            boolean changed = false;
            if (sequence != null) { for (BlockTransformer step : sequence) changed |= step.apply(context); }
            else {
                net.minecraft.block.state.IBlockState old = world.getBlockState(pos);
                if ((from == null || from.equals(old.getBlock().getRegistryName())) && predicate.test(world, pos, old)) changed = world.setBlockState(pos, state, 3);
            }
            if (changed && effect != null && context.getEntity() != null) effect.apply(context, context.getEntity());
            return changed;
        }
    }

    public static final class SlotDefinition {
        public final String type;
        public final String slot;
        public final int index;
        private final SlotDefinition[] alternatives;
        private final rml.loader.deserialize.NativeTypeDecoders.ItemMatcher matcher;
        private final DataRules.Test condition;
        private SlotDefinition(String type, String slot, int index) { this(type, slot, index, null, null, null); }
        private SlotDefinition(String type, String slot, int index, SlotDefinition[] alternatives, rml.loader.deserialize.NativeTypeDecoders.ItemMatcher matcher, DataRules.Test condition) {
            this.type = type; this.slot = slot; this.index = index; this.alternatives = alternatives; this.matcher = matcher; this.condition = condition;
        }
        public static SlotDefinition firstMatch(DeserializerManager manager, JsonObject json) throws rml.deserializer.JsonDeserializeException {
            GameplayFields.only(json, "sources", "predicate", "conditions");
            SlotDefinition[] sources = manager.decode(SlotDefinition[].class, json.get("sources"));
            if (sources.length == 0) throw new IllegalArgumentException("first_match requires at least one source");
            rml.loader.deserialize.NativeTypeDecoders.ItemMatcher matcher = json.has("predicate") ? manager.decode(rml.loader.deserialize.NativeTypeDecoders.ItemMatcher.class, json.get("predicate")) : (stack, context) -> !stack.isEmpty();
            DataRules.Test condition = json.has("conditions") ? manager.decode(DataRules.Test.class, json.get("conditions")) : context -> true;
            return new SlotDefinition("first_match", "", -1, sources, matcher, condition);
        }
        private SlotDefinition select(Entity entity) {
            DataContext context = context(entity, entity);
            if (!condition.test(context)) return null;
            for (SlotDefinition source : alternatives) {
                SlotDefinition selected = source.alternatives == null ? source : source.select(entity);
                if (selected != null && matcher.test(selected.resolve(entity), context)) return selected;
            }
            return null;
        }
        public static SlotDefinition decode(JsonObject json) {
            String type = string(json, "type", "");
            if ("equipment".equals(type)) return equipment(json);
            if ("inventory".equals(type)) return inventory(json);
            throw new IllegalArgumentException("slot source type must be equipment or inventory");
        }
        public static SlotDefinition equipment(JsonObject json) {
            GameplayFields.only(json, "slot");
            String slot = string(json, "slot", "");
            if (!Arrays.asList("mainhand", "offhand", "head", "chest", "legs", "feet").contains(slot)) throw new IllegalArgumentException("equipment slot must be mainhand, offhand, head, chest, legs, or feet");
            return new SlotDefinition("equipment", slot, -1);
        }
        public static SlotDefinition inventory(JsonObject json) {
            GameplayFields.only(json, "index");
            int index = integer(json, "index", -1);
            if (index < 0 || index > 35) throw new IllegalArgumentException("inventory index must be between 0 and 35");
            return new SlotDefinition("inventory", "", index);
        }
        public static SlotDefinition inventory(int index) {
            if (index < 0 || index > 35) throw new IllegalArgumentException("inventory index must be between 0 and 35");
            return new SlotDefinition("inventory", "", index);
        }
        public static SlotDefinition command(String nativeSlotName) {
            if (nativeSlotName == null) throw new IllegalArgumentException("slot name is required");
            switch (nativeSlotName) {
                case "weapon.mainhand": return new SlotDefinition("equipment", "mainhand", -1);
                case "weapon.offhand": return new SlotDefinition("equipment", "offhand", -1);
                case "armor.head": return new SlotDefinition("equipment", "head", -1);
                case "armor.chest": return new SlotDefinition("equipment", "chest", -1);
                case "armor.legs": return new SlotDefinition("equipment", "legs", -1);
                case "armor.feet": return new SlotDefinition("equipment", "feet", -1);
                default:
                    if (nativeSlotName.startsWith("hotbar.")) return inventory(parseIndex(nativeSlotName, "hotbar.", 0, 8));
                    if (nativeSlotName.startsWith("inventory.")) return inventory(parseIndex(nativeSlotName, "inventory.", 0, 26) + 9);
                    throw new IllegalArgumentException("unsupported native slot: " + nativeSlotName);
            }
        }
        private static int parseIndex(String slot, String prefix, int min, int max) {
            try {
                int index = Integer.parseInt(slot.substring(prefix.length()));
                if (index < min || index > max) throw new NumberFormatException();
                return index;
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("slot index out of range: " + slot, invalid);
            }
        }
        public static SlotDefinition decode(CompiledResource resource, JsonObject json) {
            try { return decode(json); } catch (RuntimeException cause) { throw failure(resource, cause.getMessage()); }
        }
        public ItemStack resolve(Entity entity) {
            if (alternatives != null) { SlotDefinition source = select(entity); return source == null ? ItemStack.EMPTY : source.resolve(entity); }
            if (entity instanceof EntityPlayer && "inventory".equals(type)) return ((EntityPlayer) entity).inventory.getStackInSlot(index);
            if (!(entity instanceof EntityLivingBase)) return ItemStack.EMPTY;
            EntityLivingBase living = (EntityLivingBase) entity;
            if ("inventory".equals(type)) return ItemStack.EMPTY;
            switch (slot) {
                case "mainhand": return living.getHeldItemMainhand();
                case "offhand": return living.getHeldItemOffhand();
                case "head": return living.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD);
                case "chest": return living.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST);
                case "legs": return living.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.LEGS);
                case "feet": return living.getItemStackFromSlot(net.minecraft.inventory.EntityEquipmentSlot.FEET);
                default: throw new IllegalStateException("Invalid compiled equipment slot: " + slot);
            }
        }
        public void replace(Entity entity, ItemStack stack) {
            Objects.requireNonNull(stack, "stack");
            if (alternatives != null) {
                SlotDefinition source = select(entity);
                if (source == null) throw new IllegalArgumentException("No slot source matched the entity");
                source.replace(entity, stack);
                return;
            }
            if ("inventory".equals(type)) {
                if (!(entity instanceof EntityPlayer)) throw new IllegalArgumentException("inventory slots require a player entity");
                ((EntityPlayer) entity).inventory.setInventorySlotContents(index, stack);
                ((EntityPlayer) entity).inventory.markDirty();
                return;
            }
            if (!(entity instanceof EntityLivingBase)) throw new IllegalArgumentException("equipment slots require a living entity");
            EntityLivingBase living = (EntityLivingBase) entity;
            switch (slot) {
                case "mainhand": living.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.MAINHAND, stack); return;
                case "offhand": living.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.OFFHAND, stack); return;
                case "head": living.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.HEAD, stack); return;
                case "chest": living.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.CHEST, stack); return;
                case "legs": living.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.LEGS, stack); return;
                case "feet": living.setItemStackToSlot(net.minecraft.inventory.EntityEquipmentSlot.FEET, stack); return;
                default: throw new IllegalStateException("Invalid compiled equipment slot: " + slot);
            }
        }
    }

    private static String definitionKey(String type, ResourceLocation id) { return type + "|" + id; }

    public static final class DamageDefinition {
        public final float exhaustion;
        public final boolean bypassesArmor;
        public final boolean fire;
        public final boolean projectile;
        public final boolean magic;
        public final boolean explosion;
        public final DataRules.NumberValue damageMultiplier;
        public final DataRules.Effect attackEffect;
        public final DataRules.Effect hurtEffect;
        private final DifficultyScaling difficultyScaling;
        private DamageDefinition(float exhaustion, JsonObject json, DifficultyScaling difficultyScaling, DataRules.NumberValue damageMultiplier, DataRules.Effect attackEffect, DataRules.Effect hurtEffect) {
            this.exhaustion = exhaustion;
            this.bypassesArmor = flag(json, "bypasses_armor");
            this.fire = flag(json, "fire"); this.projectile = flag(json, "projectile");
            this.magic = flag(json, "magic"); this.explosion = flag(json, "explosion");
            this.difficultyScaling = difficultyScaling;
            this.damageMultiplier = damageMultiplier; this.attackEffect = attackEffect; this.hurtEffect = hurtEffect;
        }
        public DamageSource source(String name, Entity attacker) {
            final boolean scaled = difficultyScaling == DifficultyScaling.ALWAYS
                    || difficultyScaling == DifficultyScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER
                    && attacker instanceof EntityLivingBase && !(attacker instanceof EntityPlayer);
            DamageSource source = attacker == null ? new DamageSource(name) {
                @Override public boolean isDifficultyScaled() { return scaled; }
                @Override public float getHungerDamage() { return exhaustion; }
            } : new EntityDamageSource(name, attacker) {
                @Override public boolean isDifficultyScaled() { return scaled; }
                @Override public float getHungerDamage() { return exhaustion; }
            };
            if (fire) source.setFireDamage();
            if (projectile) source.setProjectile();
            if (magic) source.setMagicDamage();
            if (explosion) source.setExplosion();
            if (bypassesArmor) source.setDamageBypassesArmor();
            return source;
        }
        public static DamageDefinition decode(DeserializerManager manager, JsonObject json) throws rml.deserializer.JsonDeserializeException {
            float exhaustion = json.has("exhaustion") ? json.get("exhaustion").getAsFloat() : 0;
            if (exhaustion < 0) throw new IllegalArgumentException("exhaustion must be nonnegative");
            DifficultyScaling difficultyScaling = json.has("scaling")
                    ? manager.decode(DifficultyScaling.class, json.get("scaling")) : DifficultyScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER;
            DataRules.NumberValue damageMultiplier = json.has("damage_multiplier") ? manager.decode(DataRules.NumberValue.class, json.get("damage_multiplier")) : null;
            DataRules.Effect attack = json.has("on_attack") ? manager.decode(DataRules.Effect.class, json.get("on_attack")) : null;
            DataRules.Effect hurt = json.has("on_hurt") ? manager.decode(DataRules.Effect.class, json.get("on_hurt")) : null;
            return new DamageDefinition(exhaustion, json, difficultyScaling, damageMultiplier, attack, hurt);
        }
        private static boolean flag(JsonObject json, String key) { return json.has(key) && json.get(key).getAsBoolean(); }
        private enum DifficultyScaling { NEVER, ALWAYS, WHEN_CAUSED_BY_LIVING_NON_PLAYER }
    }
}