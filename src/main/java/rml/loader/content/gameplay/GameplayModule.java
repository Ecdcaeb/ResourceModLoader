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
    private final List<IVillager> villagerTrades = new ArrayList<>();
    private final Map<Block, Block> blockTransformers = new LinkedHashMap<>();
    private final Map<ResourceLocation, EnchantmentProvider> enchantmentProviders = new LinkedHashMap<>();
    private final Map<ResourceLocation, TrialSpawnerDefinition> trialSpawners = new LinkedHashMap<>();
    private final Map<ResourceLocation, SulfurCubeArchetype> sulfurCubes = new LinkedHashMap<>();
    private final Map<ResourceLocation, MachineRecipe> machineRecipes = new LinkedHashMap<>();
    private boolean initialized;
    private final Map<ResourceLocation, NativeItemDefinition> customItems = new LinkedHashMap<>();
    private final Map<ResourceLocation, NativeBlockDefinition> customBlocks = new LinkedHashMap<>();
    private DeserializerManager decoderManager;
    private final Map<ResourceLocation, NativeDataItem> itemInstances = new LinkedHashMap<>();
    private final Map<ResourceLocation, NativeDataBlock> blockInstances = new LinkedHashMap<>();
    private MachineRecipe.TrimLookup trimLookup;

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
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "slot_source"), SlotDefinition.class, json -> SlotDefinition.decode(json.getAsJsonObject())));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "equipment"), SlotDefinition.class, json -> SlotDefinition.equipment(json.getAsJsonObject())));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "inventory"), SlotDefinition.class, json -> SlotDefinition.inventory(json.getAsJsonObject())));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "block_transformer"), BlockTransformer.class,
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
        if (initialized) throw new IllegalStateException("Trim resolver must be supplied before GameplayModule.initialize");
        this.trimLookup = Objects.requireNonNull(trimLookup, "trimLookup");
    }

    /** Decode every gameplay definition before installing event handlers or registry callbacks. */
    public synchronized void initialize() {
        if (initialized) return;
        List<CompiledResource> staged = new ArrayList<>();
        for (String type : TYPES) staged.addAll(catalog.snapshot().resources(type).values());
        Map<ResourceLocation, Enchantment> stagedEnchantments = new LinkedHashMap<>();
        Map<ResourceLocation, SlotDefinition> stagedSlots = new LinkedHashMap<>();
        List<IVillager> stagedTrades = new ArrayList<>();
        Map<ResourceLocation, DamageDefinition> stagedDamage = new LinkedHashMap<>();
        Map<Block, Block> stagedTransformers = new LinkedHashMap<>();
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
                    stagedEnchantments.put(resource.getId(), new NativeEnchantment(resource.getId(), json, rules));
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
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        stagedTrades.add(decoderManager.decode(IVillager.class, resource.getValue()));
                    } catch (Exception cause) {
                        throw failure(resource, "invalid native villager trade: " + cause.getMessage());
                    }
                    break;
                case "block_transformer":
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        BlockTransformer transformer = decoderManager.decode(BlockTransformer.class, resource.getValue());
                        Block from = Block.getBlockFromName(transformer.from.toString());
                        Block to = Block.getBlockFromName(transformer.to.toString());
                        if (from == null || to == null) throw new IllegalArgumentException("unknown block in transformation " + transformer.from + " -> " + transformer.to);
                        if (stagedTransformers.put(from, to) != null) throw new IllegalArgumentException("Duplicate transformer for " + transformer.from);
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
                        stagedItems.put(resource.getId(), NativeItemDefinition.decode(decoderManager, resource.getId(), json));
                    } catch (Exception cause) {
                        throw failure(resource, "invalid native item: " + cause.getMessage());
                    }
                    break;
                case "block":
                    try {
                        if (decoderManager == null) throw new IllegalStateException("Gameplay decoders were not registered");
                        stagedBlocks.put(resource.getId(), NativeBlockDefinition.decode(decoderManager, resource.getId(), json));
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
        villagerTrades.addAll(stagedTrades);
        blockTransformers.putAll(stagedTransformers);
        enchantmentProviders.putAll(stagedProviders);
        trialSpawners.putAll(stagedSpawners);
        machineRecipes.putAll(stagedRecipes);
        StationTileEntity.setRecipes(machineRecipes);
        TrialSpawnerTileEntity.setDefinitions(trialSpawners);
        if (!trialSpawners.isEmpty()) GameRegistry.registerTileEntity(TrialSpawnerTileEntity.class, new ResourceLocation("rml", "trial_spawner"));
        if (!machineRecipes.isEmpty()) GameRegistry.registerTileEntity(StationTileEntity.class, new ResourceLocation("rml", "machine_station"));
        if (!sulfurCubes.isEmpty()) FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        initialized = true;
    }
    public void initializeClient() {
        if (!sulfurCubes.isEmpty() && FMLCommonHandler.instance().getSide().isClient()) SulfurCubeClient.register();
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
        if (!trialSpawners.isEmpty()) {
            if (event.getRegistry().containsKey(TrialSpawnerBlock.INSTANCE.getRegistryName())) throw new IllegalArgumentException("Block ID is already registered: rml:trial_spawner");
            event.getRegistry().register(TrialSpawnerBlock.INSTANCE);
        }
        if (!machineRecipes.isEmpty()) {
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
        if (!trialSpawners.isEmpty()) {
            ItemBlock item = new ItemBlock(TrialSpawnerBlock.INSTANCE);
            item.setRegistryName(TrialSpawnerBlock.INSTANCE.getRegistryName());
            event.getRegistry().register(item);
        }
        if (!machineRecipes.isEmpty()) {
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
            ItemBlock item = new ItemBlock(block);
            item.setRegistryName(block.getRegistryName());
            event.getRegistry().register(item);
        }
    }
    @SubscribeEvent
    public void registerSulfurEntity(RegistryEvent.Register<net.minecraftforge.fml.common.registry.EntityEntry> event) {
        if (sulfurCubes.isEmpty()) return;
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
        for (IVillager trade : villagerTrades) trade.run(event.getRegistry());
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.PlaceEvent event) {
        Block input = event.getPlacedBlock().getBlock();
        Block output = blockTransformers.get(input);
        if (output != null && !event.getWorld().isRemote) {
            event.getWorld().setBlockState(event.getPos(), output.getDefaultState(), 3);
        }
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
        if (attacker instanceof EntityLivingBase) {
            EntityLivingBase livingAttacker = (EntityLivingBase) attacker;
            ItemStack weapon = livingAttacker.getHeldItemMainhand();
            for (Map.Entry<ResourceLocation, Enchantment> entry : enchantments.entrySet()) {
                int level = EnchantmentHelper.getEnchantmentLevel(entry.getValue(), weapon);
                if (level <= 0 || !(entry.getValue() instanceof NativeEnchantment)) continue;
                NativeEnchantment enchantment = (NativeEnchantment) entry.getValue();
                amount += enchantment.damageBonus * level;
                if (enchantment.hitEffect != null) enchantment.hitEffect.apply(context(victim, attacker), victim);
            }
        }
        if (definition == null) {
            event.setAmount(Math.max(0, amount));
            return;
        }
        if (definition.damageMultiplier != null) amount *= definition.damageMultiplier.evaluate(context(victim, attacker));
        if (definition.hurtEffect != null) definition.hurtEffect.apply(context(victim, attacker), victim);
        event.setAmount(Math.max(0, amount));
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
                ItemStack enchanted = applyEnchantmentProvider(providerId, player.getHeldItemMainhand(), player.world.rand);
                player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, enchanted);
                return;
            case "slot":
                if (args.length != 2 || actor == null) throw new CommandException("Usage: slot <slot_source> (entity sender required)");
                ItemStack selected = resolveSlot(actor, new ResourceLocation(args[1]));
                sender.sendMessage(new net.minecraft.util.text.TextComponentString(selected.toString()));
                return;
            case "transform":
                if (actor == null) throw new CommandException("This transform command requires an entity sender");
                Block input = actor.world.getBlockState(sender.getPosition()).getBlock();
                Block output = blockTransformers.get(input);
                if (output == null) throw new CommandException("No transformer matches the block at your position");
                actor.world.setBlockState(sender.getPosition(), output.getDefaultState(), 3);
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

    private static final class NativeEnchantment extends Enchantment {
        private final int maxLevel;
        private final int minLevel;
        private final int minCost;
        private final int maxCost;
        private final float damageBonus;
        private final DataRules.Effect hitEffect;

        NativeEnchantment(ResourceLocation id, JsonObject json, DataRules rules) {
            super(rarity(json), slot(json), net.minecraft.inventory.EntityEquipmentSlot.values());
            setName(id.toString());
            this.minLevel = integer(json, "min_level", 1);
            this.maxLevel = integer(json, "max_level", 1);
            this.minCost = integer(json, "min_cost", 1);
            this.maxCost = integer(json, "max_cost", Math.max(minCost, minCost + 10));
            this.damageBonus = json.has("damage_bonus") ? json.get("damage_bonus").getAsFloat() : 0;
            this.hitEffect = json.has("on_hit") ? rules.effect(json.get("on_hit")) : null;
            if (minLevel < 1 || maxLevel < minLevel || minCost < 0 || maxCost < minCost) throw new IllegalArgumentException("Invalid enchantment bounds for " + id);
        }
        @Override public int getMinLevel() { return minLevel; }
        @Override public int getMaxLevel() { return maxLevel; }
        @Override public int getMinEnchantability(int level) { return minCost + (level - 1) * 10; }
        @Override public int getMaxEnchantability(int level) { return getMinEnchantability(level) + maxCost; }
        @Override public float calcDamageByCreature(int level, net.minecraft.entity.EnumCreatureAttribute creatureType) { return damageBonus * level; }
    }

    private static Enchantment.Rarity rarity(JsonObject json) {
        String value = string(json, "weight", "common").toUpperCase(Locale.ROOT);
        return Enchantment.Rarity.valueOf(value);
    }
    private static EnumEnchantmentType slot(JsonObject json) {
        String value = string(json, "slot", "all").toUpperCase(Locale.ROOT);
        if ("ALL".equals(value)) return EnumEnchantmentType.ALL;
        if ("ARMOR_FEET".equals(value) || "ARMOR_LEGS".equals(value) || "ARMOR_CHEST".equals(value) || "ARMOR_HEAD".equals(value)) return EnumEnchantmentType.ARMOR;
        return EnumEnchantmentType.valueOf(value);
    }
    private static int integer(JsonObject json, String key, int fallback) { return json.has(key) ? json.get(key).getAsInt() : fallback; }
    private static String string(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }

    public static final class EnchantmentProvider {
        private final List<Choice> choices;
        private final int totalWeight;
        private EnchantmentProvider(List<Choice> choices, int totalWeight) { this.choices = Collections.unmodifiableList(choices); this.totalWeight = totalWeight; }
        public static EnchantmentProvider decode(DeserializerManager manager, JsonObject json) throws rml.deserializer.JsonDeserializeException {
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
            return new EnchantmentProvider(choices, (int) total);
        }
        public ItemStack apply(ItemStack original, java.util.Random random) {
            Objects.requireNonNull(original, "stack"); Objects.requireNonNull(random, "random");
            int selected = random.nextInt(totalWeight);
            Choice choice = choices.get(choices.size() - 1);
            for (Choice candidate : choices) {
                selected -= candidate.weight;
                if (selected < 0) { choice = candidate; break; }
            }
            Enchantment enchantment = net.minecraftforge.fml.common.registry.ForgeRegistries.ENCHANTMENTS.getValue(choice.enchantment);
            if (enchantment == null) throw new IllegalStateException("Unknown enchantment in provider: " + choice.enchantment);
            ItemStack result = original.copy();
            result.addEnchantment(enchantment, choice.level);
            return result;
        }
        public static final class Choice {
            public final ResourceLocation enchantment;
            public final int level;
            public final int weight;
            private Choice(ResourceLocation enchantment, int level, int weight) { this.enchantment = enchantment; this.level = level; this.weight = weight; }
            public static Choice decode(DeserializerManager manager, JsonObject json) throws rml.deserializer.JsonDeserializeException {
                ResourceLocation enchantment = manager.decode(ResourceLocation.class, json.get("enchantment"));
                int level = json.has("level") ? json.get("level").getAsInt() : 1;
                int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
                if (enchantment == null || level < 1 || weight < 1) throw new IllegalArgumentException("enchantment choice requires an id and positive level/weight");
                return new Choice(enchantment, level, weight);
            }
        }
    }

    public static final class BlockTransformer {
        public final ResourceLocation from;
        public final ResourceLocation to;
        private BlockTransformer(ResourceLocation from, ResourceLocation to) { this.from = from; this.to = to; }
        public static BlockTransformer decode(DeserializerManager manager, JsonObject json) throws rml.deserializer.JsonDeserializeException {
            ResourceLocation from = manager.decode(ResourceLocation.class, json.get("from"));
            ResourceLocation to = manager.decode(ResourceLocation.class, json.get("to"));
            if (from == null || to == null) throw new IllegalArgumentException("transformer from and to are required");
            return new BlockTransformer(from, to);
        }
    }

    public static final class SlotDefinition {
        public final String type;
        public final String slot;
        public final int index;
        private SlotDefinition(String type, String slot, int index) { this.type = type; this.slot = slot; this.index = index; }
        public static SlotDefinition decode(JsonObject json) {
            String type = string(json, "type", "");
            if ("equipment".equals(type)) return equipment(json);
            if ("inventory".equals(type)) return inventory(json);
            throw new IllegalArgumentException("slot source type must be equipment or inventory");
        }
        public static SlotDefinition equipment(JsonObject json) {
            String slot = string(json, "slot", "");
            if (!Arrays.asList("mainhand", "offhand", "head", "chest", "legs", "feet").contains(slot)) throw new IllegalArgumentException("equipment slot must be mainhand, offhand, head, chest, legs, or feet");
            return new SlotDefinition("equipment", slot, -1);
        }
        public static SlotDefinition inventory(JsonObject json) {
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
            if ("inventory".equals(type)) {
                if (!(entity instanceof EntityPlayer)) throw new IllegalArgumentException("inventory slots require a player entity");
                ((EntityPlayer) entity).inventory.setInventorySlotContents(index, stack);
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