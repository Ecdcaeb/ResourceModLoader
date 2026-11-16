package rml.loader.content.presentation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import rml.loader.content.CompiledResource;
import rml.loader.content.ContentCatalog;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import net.minecraft.util.math.BlockPos;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Native services for authored presentation definitions. */
public final class PresentationModule {
    private static final Set<String> TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "banner_pattern", "decorated_pot_pattern", "painting_variant", "trim_material", "trim_pattern",
            "chat_type", "dialog", "instrument", "jukebox_song", "cat_sound_variant", "chicken_sound_variant",
            "cow_sound_variant", "pig_sound_variant", "wolf_sound_variant", "cat_variant", "chicken_variant",
            "cow_variant", "pig_variant", "frog_variant", "wolf_variant", "zombie_nautilus_variant")));
    private static final Set<String> COMMANDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "dialog", "variant", "instrument", "jukebox", "trim", "painting", "pot", "chat")));

    private final ContentCatalog catalog;
    private final DataRules rules;
    private final CompiledTagModule tags;
    private DeserializerManager decoders;
    private final Map<ResourceLocation, Definition> definitions = new LinkedHashMap<>();
    private final Map<ResourceLocation, SoundEvent> soundEvents = new HashMap<>();
    private final Map<ResourceLocation, PresentationCodecs.SurfaceDefinition> surfaces = new LinkedHashMap<>();
    private final Map<ResourceLocation, PresentationCodecs.PaintingVariant> paintings = new LinkedHashMap<>();
    private final Map<ResourceLocation, PresentationCodecs.TrimMaterial> trimMaterials = new LinkedHashMap<>();
    private final Map<ResourceLocation, PresentationCodecs.TrimPattern> trimPatterns = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> defaultEntityTextures = new LinkedHashMap<>();
    private final Map<ResourceLocation, PresentationCodecs.EntitySounds> entitySoundVariants = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> defaultSoundVariants = new LinkedHashMap<>();
    private final Map<ResourceLocation, String> defaultSoundVariantTypes = new LinkedHashMap<>();
    private BlockPresentationPot potBlock;
    private boolean initialized;

    public PresentationModule(ContentCatalog catalog, DataRules rules, CompiledTagModule tags) {
        if (catalog == null || rules == null || tags == null) throw new NullPointerException("presentation dependency");
        this.catalog = catalog;
        this.rules = rules;
        this.tags = tags;
    }

    public Set<String> getTypes() { return TYPES; }
    public Set<String> getCommands() { return COMMANDS; }

    public void registerDecoders(DeserializerManager manager) {
        if (manager == null) throw new NullPointerException("manager");
        if (decoders != null && decoders != manager) throw new IllegalStateException("Presentation decoders already registered");
        if (decoders == null) {
            PresentationCodecs.register(manager);
            decoders = manager;
        }
    }

    /** Decode and validate the entire module before creating native registry entries. */
    public synchronized void initialize() {
        if (initialized) return;
        if (decoders == null) throw new IllegalStateException("registerDecoders must run before initialize");
        Map<ResourceLocation, Definition> parsed = new LinkedHashMap<>();
        for (String type : TYPES) {
            for (CompiledResource resource : catalog.snapshot().resources(type).values()) {
                try {
                    Definition definition = parse(resource);
                    definition.decoded = decodeTyped(resource);
                    validateTyped(resource, definition);
                    Definition previous = parsed.putIfAbsent(PresentationIds.key(type, resource.getId()), definition);
                    if (previous != null) throw new IllegalArgumentException("duplicate " + type + " presentation id");
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Invalid " + resource.getType() + " at " + resource.getSource() + ": " + ex.getMessage(), ex);
                }
            }
        }
        Map<ResourceLocation, PresentationCodecs.SurfaceDefinition> parsedSurfaces = new LinkedHashMap<>();
        Map<ResourceLocation, PresentationCodecs.TrimMaterial> parsedTrimMaterials = new LinkedHashMap<>();
        Map<ResourceLocation, PresentationCodecs.TrimPattern> parsedTrimPatterns = new LinkedHashMap<>();
        Map<ResourceLocation, PresentationCodecs.PaintingVariant> parsedPaintings = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> parsedTextures = new LinkedHashMap<>();
        Map<ResourceLocation, PresentationCodecs.EntitySounds> parsedSounds = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> parsedDefaultSounds = new LinkedHashMap<>();
        Map<ResourceLocation, String> parsedDefaultSoundTypes = new LinkedHashMap<>();
        for (Definition definition : parsed.values()) {
            if (definition.decoded instanceof PresentationCodecs.SurfaceDefinition)
                parsedSurfaces.put(PresentationIds.key(definition.type, definition.id), (PresentationCodecs.SurfaceDefinition) definition.decoded);
            if (definition.decoded instanceof PresentationCodecs.TrimMaterial)
                parsedTrimMaterials.put(PresentationIds.key(definition.type, definition.id), (PresentationCodecs.TrimMaterial) definition.decoded);
            if (definition.decoded instanceof PresentationCodecs.TrimPattern)
                parsedTrimPatterns.put(PresentationIds.key(definition.type, definition.id), (PresentationCodecs.TrimPattern) definition.decoded);
            if (definition.decoded instanceof PresentationCodecs.PaintingVariant)
                parsedPaintings.put(definition.id, (PresentationCodecs.PaintingVariant) definition.decoded);
            if (definition.decoded instanceof PresentationCodecs.SoundPlayback)
                ensureSoundEvent(((PresentationCodecs.SoundPlayback) definition.decoded).sound);
            if (definition.decoded instanceof PresentationCodecs.EntitySounds) {
                PresentationCodecs.EntitySounds entitySounds = (PresentationCodecs.EntitySounds) definition.decoded;
                parsedSounds.put(PresentationIds.key(definition.type, definition.id), entitySounds);
                ResourceLocation entityType = soundEntityType(definition.type);
                if (entityType != null) {
                    parsedDefaultSounds.putIfAbsent(entityType, definition.id);
                    parsedDefaultSoundTypes.putIfAbsent(entityType, definition.type);
                }
                registerEntitySounds(entitySounds);
            }
            if (definition.decoded instanceof PresentationCodecs.EntityVariant) {
                PresentationCodecs.EntityVariant variant = (PresentationCodecs.EntityVariant) definition.decoded;
                parsedTextures.putIfAbsent(variant.entity, variant.texture);
                if (variant.sounds != null) {
                    parsedSounds.put(PresentationIds.key(definition.type, definition.id), variant.sounds);
                    parsedDefaultSounds.putIfAbsent(variant.entity, definition.id);
                    parsedDefaultSoundTypes.putIfAbsent(variant.entity, definition.type);
                    registerEntitySounds(variant.sounds);
                }
            }
        }
        for (Definition definition : parsed.values()) {
            if (definition.decoded instanceof PresentationCodecs.EntityVariant) {
                PresentationCodecs.EntityVariant variant = (PresentationCodecs.EntityVariant) definition.decoded;
                if (variant.sounds != null) {
                    parsedDefaultSounds.put(variant.entity, definition.id);
                    parsedDefaultSoundTypes.put(variant.entity, definition.type);
                }
            }
        }
        definitions.putAll(parsed);
        paintings.putAll(parsedPaintings);
        surfaces.putAll(parsedSurfaces);
        trimMaterials.putAll(parsedTrimMaterials);
        trimPatterns.putAll(parsedTrimPatterns);
        defaultEntityTextures.putAll(parsedTextures);
        entitySoundVariants.putAll(parsedSounds);
        defaultSoundVariantTypes.putAll(parsedDefaultSoundTypes);
        defaultSoundVariants.putAll(parsedDefaultSounds);
        PresentationSoundResolver.configure(entitySoundVariants, soundEvents, defaultSoundVariants, defaultSoundVariantTypes);
        boolean hasPotPatterns = hasType(parsed, "decorated_pot_pattern");
        if (hasPotPatterns) potBlock = new BlockPresentationPot(firstIdOfType(parsed, "decorated_pot_pattern"));
        registerNativeEntities(parsed);
        if (hasPotPatterns) GameRegistry.registerTileEntity(TileEntityPresentationPot.class, new ResourceLocation("rml", "decorated_pot"));
        MinecraftForge.EVENT_BUS.register(this);
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
            PresentationClient.configure(surfaces, paintings, trimMaterials, trimPatterns, defaultEntityTextures);
            PresentationClient.installNetworkHandler();
            PresentationClient.registerModels();
        }
        PresentationNetwork.register(this);
        initialized = true;
    }

    private void validateTyped(CompiledResource resource, Definition definition) {
        if (definition.decoded instanceof PresentationCodecs.EntityVariant) {
            PresentationCodecs.EntityVariant variant = (PresentationCodecs.EntityVariant) definition.decoded;
            ResourceLocation expected = entityType(resource.getType());
            if (expected == null || !expected.equals(variant.entity))
                throw new IllegalArgumentException("variant entity must be " + expected);
            if (variant.texture == null) throw new IllegalArgumentException("entity variants require a texture");
        }
    }

    private void registerNativeEntities(Map<ResourceLocation, Definition> parsed) {
        if (hasType(parsed, "frog_variant"))
            net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new ResourceLocation("rml", "frog"),
                    EntityPresentationFrog.class, "presentation_frog", 111, "rml", 80, 3, true);
        if (hasType(parsed, "frog_variant"))
            net.minecraftforge.fml.common.registry.EntityRegistry.addSpawn(EntityPresentationFrog.class, 6, 2, 4,
                    net.minecraft.entity.EnumCreatureType.CREATURE, net.minecraft.init.Biomes.SWAMPLAND,
                    net.minecraft.init.Biomes.JUNGLE, net.minecraft.init.Biomes.JUNGLE_HILLS);
        if (hasType(parsed, "zombie_nautilus_variant"))
            net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new ResourceLocation("rml", "zombie_nautilus"),
                    EntityPresentationZombieNautilus.class, "presentation_zombie_nautilus", 112, "rml", 96, 3, true);
        if (hasType(parsed, "zombie_nautilus_variant"))
            net.minecraftforge.fml.common.registry.EntityRegistry.addSpawn(EntityPresentationZombieNautilus.class, 4, 1, 2,
                    net.minecraft.entity.EnumCreatureType.MONSTER, net.minecraft.init.Biomes.OCEAN,
                    net.minecraft.init.Biomes.DEEP_OCEAN, net.minecraft.init.Biomes.RIVER);
        if (hasType(parsed, "painting_variant"))
            net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(new ResourceLocation("rml", "presentation_painting"),
                    EntityPresentationPainting.class, "presentation_painting", 113, "rml", 80, 10, true);
    }

    private static ResourceLocation firstIdOfType(Map<ResourceLocation, Definition> resources, String type) {
        for (Definition definition : resources.values()) if (type.equals(definition.type)) return definition.id;
        return null;
    }

    private static boolean hasType(Map<ResourceLocation, Definition> resources, String type) {
        for (Definition definition : resources.values()) if (type.equals(definition.type)) return true;
        return false;
    }

    private static ResourceLocation entityType(String type) {
        switch (type) {
            case "cat_variant": return new ResourceLocation("minecraft", "ocelot");
            case "chicken_variant": return new ResourceLocation("minecraft", "chicken");
            case "cow_variant": return new ResourceLocation("minecraft", "cow");
            case "pig_variant": return new ResourceLocation("minecraft", "pig");
            case "wolf_variant": return new ResourceLocation("minecraft", "wolf");
            case "frog_variant": return new ResourceLocation("rml", "frog");
            case "zombie_nautilus_variant": return new ResourceLocation("rml", "zombie_nautilus");
            default: return null;
        }
    }

    private static ResourceLocation soundEntityType(String type) {
        switch (type) {
            case "cat_sound_variant": return new ResourceLocation("minecraft", "ocelot");
            case "chicken_sound_variant": return new ResourceLocation("minecraft", "chicken");
            case "cow_sound_variant": return new ResourceLocation("minecraft", "cow");
            case "pig_sound_variant": return new ResourceLocation("minecraft", "pig");
            case "wolf_sound_variant": return new ResourceLocation("minecraft", "wolf");
            default: return null;
        }
    }
    private Object decodeTyped(CompiledResource resource) throws JsonDeserializeException {
        switch (resource.getType()) {
            case "chat_type": return decoders.decode(PresentationCodecs.ChatStyle.class, resource.getValue());
            case "dialog": return decoders.decode(PresentationCodecs.DialogDefinition.class, resource.getValue());
            case "instrument":
            case "jukebox_song": return decoders.decode(PresentationCodecs.SoundPlayback.class, resource.getValue());
            case "painting_variant": return decoders.decode(PresentationCodecs.PaintingVariant.class, resource.getValue());
            case "banner_pattern":
            case "decorated_pot_pattern": return decoders.decode(PresentationCodecs.SurfaceDefinition.class, resource.getValue());
            case "trim_material": return decoders.decode(PresentationCodecs.TrimMaterial.class, resource.getValue());
            case "trim_pattern": return decoders.decode(PresentationCodecs.TrimPattern.class, resource.getValue());
            case "cat_sound_variant":
            case "chicken_sound_variant":
            case "cow_sound_variant":
            case "pig_sound_variant":
            case "wolf_sound_variant": return decoders.decode(PresentationCodecs.EntitySounds.class, resource.getValue());
            case "cat_variant":
            case "chicken_variant":
            case "cow_variant":
            case "pig_variant":
            case "frog_variant":
            case "wolf_variant":
            case "zombie_nautilus_variant": return decoders.decode(PresentationCodecs.EntityVariant.class, resource.getValue());
            default: return null;
        }
    }

    private void ensureSoundEvent(ResourceLocation id) {
        if (soundEvents.containsKey(id)) return;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (sound == null) {
            sound = new SoundEvent(id);
            sound.setRegistryName(id);
        }
        soundEvents.put(id, sound);
    }

    private void registerEntitySounds(PresentationCodecs.EntitySounds entitySounds) {
        if (entitySounds == null) return;
        if (entitySounds.ambient != null) ensureSoundEvent(entitySounds.ambient.sound);
        if (entitySounds.hurt != null) ensureSoundEvent(entitySounds.hurt.sound);
        if (entitySounds.death != null) ensureSoundEvent(entitySounds.death.sound);
    }


    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<net.minecraft.block.Block> event) {
        if (potBlock != null) event.getRegistry().register(potBlock);
    }
    @SubscribeEvent
    public void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        for (Map.Entry<ResourceLocation, SoundEvent> entry : soundEvents.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (event.getRegistry().containsKey(id)) entry.setValue(event.getRegistry().getValue(id));
            else event.getRegistry().register(entry.getValue());
        }
        PresentationSoundResolver.configure(entitySoundVariants, soundEvents, defaultSoundVariants, defaultSoundVariantTypes);
    }

    @SubscribeEvent
    public void routeEntitySound(net.minecraftforge.event.entity.PlaySoundAtEntityEvent event) {
        net.minecraft.entity.Entity entity = event.getEntity();
        if (entity != null && !entity.world.isRemote) PresentationSoundResolver.replace(event);
    }

    @SubscribeEvent
    public void assignDefaultEntitySounds(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) return;
        net.minecraft.entity.Entity entity = event.getEntity();
        String current = entity.getEntityData().getString("rml_presentation_sound_variant");
        ResourceLocation type = net.minecraft.entity.EntityList.getKey(entity);
        ResourceLocation defaultVariant = defaultSoundVariants.get(type);
        String defaultVariantType = defaultSoundVariantTypes.get(type);
        if (current.isEmpty() && defaultVariant != null && defaultVariantType != null) {
            entity.getEntityData().setString("rml_presentation_sound_variant", defaultVariant.toString());
            entity.getEntityData().setString("rml_presentation_sound_variant_type", defaultVariantType);
    }
    }

    @SubscribeEvent
    public void registerPlaybackItems(RegistryEvent.Register<net.minecraft.item.Item> event) {
        if (potBlock != null) {
            net.minecraft.item.ItemBlock item = new net.minecraft.item.ItemBlock(potBlock);
            item.setRegistryName(potBlock.getRegistryName());
            event.getRegistry().register(item);
        }
        for (Definition definition : definitions.values()) {
            if ("banner_pattern".equals(definition.type)) {
                ResourceLocation itemId = itemId("banner_pattern", definition.id);
                PresentationBannerPatternItem item = new PresentationBannerPatternItem(definition.id);
                item.setRegistryName(itemId);
                item.setTranslationKey(itemId.getNamespace() + "." + itemId.getPath().replace('/', '.'));
                event.getRegistry().register(item);
            }
            if (!"jukebox_song".equals(definition.type) && !"instrument".equals(definition.type)) continue;
            PresentationCodecs.SoundPlayback playback = (PresentationCodecs.SoundPlayback) definition.decoded;
            SoundEvent sound = soundEvents.get(playback.sound);
            if (sound == null) throw new IllegalStateException("Missing registered sound " + playback.sound + " for " + definition.id);
            ResourceLocation itemId = itemId("jukebox_song".equals(definition.type) ? "record" : "instrument", definition.id);
            if (event.getRegistry().containsKey(itemId)) throw new IllegalStateException("Playback item id is already registered: " + itemId);
            net.minecraft.item.Item item = "jukebox_song".equals(definition.type)
                    ? new PresentationRecordItem(itemId.getPath(), sound)
                    : new PresentationInstrumentItem(sound, playback.volume, playback.pitch);
            item.setRegistryName(itemId);
            item.setTranslationKey(itemId.getNamespace() + "." + itemId.getPath().replace('/', '.'));
            item.setCreativeTab(net.minecraft.creativetab.CreativeTabs.MISC);
            event.getRegistry().register(item);
        }
    }

    private static ResourceLocation itemId(String family, ResourceLocation id) {
        return new ResourceLocation("rml", family + "/" + id.getNamespace() + "/" + id.getPath());
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw usage();
        switch (args[0]) {
            case "chat": sendChat(sender, args); return;
            case "instrument": play(sender, args, "instrument"); return;
            case "jukebox": play(sender, args, "jukebox_song"); return;
            case "dialog": openDialog(sender, args); return;
            case "variant": setVariant(server, sender, args); return;
            case "trim": applyTrim(sender, args); return;
            case "painting": placePainting(sender, args); return;
            case "pot": placePot(sender, args); return;
            default: throw usage();
        }
    }

    private void sendChat(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) throw usage();
        Definition style = require(args[1], "chat_type");
        StringBuilder message = new StringBuilder();
        for (int i = 2; i < args.length; i++) { if (i > 2) message.append(' '); message.append(args[i]); }
        PresentationCodecs.ChatStyle chatStyle = (PresentationCodecs.ChatStyle) style.decoded;
        TextComponentString component = new TextComponentString(chatStyle.prefix + message + chatStyle.suffix);
        String color = chatStyle.color;
        TextFormatting formatting = TextFormatting.getValueByName(color.toUpperCase(java.util.Locale.ROOT));
        if (formatting == null || !formatting.isColor()) throw new CommandException("Unknown chat color '%s' in %s", color, style.id);
        component.getStyle().setColor(formatting);
        sender.getServer().getPlayerList().sendMessage(component);
    }

    private void play(ICommandSender sender, String[] args, String type) throws CommandException {
        if (args.length < 2 || sender.getPosition() == null || sender.getEntityWorld() == null) throw usage();
        Definition song = require(args[1], type);
        PresentationCodecs.SoundPlayback playback = (PresentationCodecs.SoundPlayback) song.decoded;
        SoundEvent sound = soundEvents.get(playback.sound);
        if (sound == null) throw new CommandException("Sound '%s' is not registered yet", playback.sound);
        sender.getEntityWorld().playSound(null, sender.getPosition(), sound, SoundCategory.RECORDS, playback.volume, playback.pitch);
    }

    private void openDialog(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 2 || !(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) throw usage();
        Definition dialog = require(args[1], "dialog");
        openDialog((EntityPlayerMP) sender.getCommandSenderEntity(), dialog.id);
    }

    /** Opens the same validated, network-backed dialog used by the command for a player. */
    public void openDialog(EntityPlayerMP player, ResourceLocation id) {
        if (player == null || id == null) throw new NullPointerException("player and dialog id are required");
        if (!initialized) throw new IllegalStateException("PresentationModule must be initialized before opening dialogs");
        Definition dialog = definitions.get(PresentationIds.key("dialog", id));
        if (dialog == null || !(dialog.decoded instanceof PresentationCodecs.DialogDefinition))
            throw new IllegalArgumentException("Unknown dialog " + id);
        MinecraftServer server = player.getServer();
        if (server == null) throw new IllegalStateException("Dialog preview player is not attached to a server");
        if (!server.isCallingFromMinecraftThread()) {
            server.addScheduledTask(() -> openDialog(player, id));
            return;
        }
        if (player.isDead || player.connection == null || player.world.isRemote)
            throw new IllegalStateException("Dialog preview requires a connected server player");
        PresentationNetwork.open(player, id, dialog.json);
    }

    private void setVariant(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 4) throw usage();
        String type = args[1];
        if (!type.endsWith("_variant")) throw usage();
        Definition variant = require(args[2], type);
        net.minecraft.entity.Entity entity = net.minecraft.command.CommandBase.getEntity(server, sender, args[3]);
        if (variant.decoded instanceof PresentationCodecs.EntitySounds) {
            ResourceLocation expected = soundEntityType(variant.type);
            if (expected == null || !expected.equals(net.minecraft.entity.EntityList.getKey(entity)))
                throw new CommandException("Sound variant '%s' does not match entity %s", variant.id, entity.getName());
            PresentationCodecs.EntitySounds soundSet = (PresentationCodecs.EntitySounds) variant.decoded;
            PresentationCodecs.SoundPlayback sound = soundSet.ambient != null ? soundSet.ambient : (soundSet.hurt != null ? soundSet.hurt : soundSet.death);
            entity.getEntityData().setString("rml_presentation_sound_variant", variant.id.toString());
            entity.getEntityData().setString("rml_presentation_sound_variant_type", variant.type);
            PresentationNetwork.syncSoundVariant(entity, variant.id, variant.type);
            SoundEvent event = soundEvents.get(sound.sound);
            if (event != null) entity.world.playSound(null, entity.posX, entity.posY, entity.posZ, event, SoundCategory.NEUTRAL, sound.volume, sound.pitch);
            return;
        }
        if (!(variant.decoded instanceof PresentationCodecs.EntityVariant))
            throw new CommandException("Unknown entity presentation variant '%s'", variant.id);
        PresentationCodecs.EntityVariant value = (PresentationCodecs.EntityVariant) variant.decoded;
        if (!value.entity.equals(net.minecraft.entity.EntityList.getKey(entity)))
            throw new CommandException("Variant '%s' targets %s, not %s", variant.id, value.entity, entity.getName());
        entity.getEntityData().setString("rml_presentation_variant", variant.id.toString());
        entity.getEntityData().setString("rml_presentation_variant_type", variant.type);
        entity.getEntityData().setString("rml_presentation_variant_entity", value.entity.toString());
        entity.getEntityData().setString("rml_presentation_variant_texture", value.texture.toString());
        entity.getEntityData().setString("rml_presentation_sound_variant", "");
        entity.getEntityData().setInteger("rml_presentation_variant_revision", entity.getEntityData().getInteger("rml_presentation_variant_revision") + 1);
        PresentationNetwork.syncVariant(entity, variant.id, variant.type, value);
    }

    public ResourceLocation resolveTrimMaterial(net.minecraft.item.ItemStack addition, DataContext context) {
        if (addition == null || addition.isEmpty()) return null;
        net.minecraft.nbt.NBTTagCompound tag = addition.getTagCompound();
        if (tag != null && tag.hasKey("rml_trim_material")) {
            if (!tag.hasKey("rml_trim_material", 8)) throw new IllegalArgumentException("rml_trim_material must be a resource identifier string");
            return new ResourceLocation(tag.getString("rml_trim_material"));
        }
        ResourceLocation itemId = net.minecraft.item.Item.REGISTRY.getNameForObject(addition.getItem());
        ResourceLocation vanillaMaterial = vanillaTrimMaterial(itemId);
        if (vanillaMaterial != null) return vanillaMaterial;
        ResourceLocation match = null;
        for (Definition definition : definitions.values()) {
            if (!(definition.decoded instanceof PresentationCodecs.TrimMaterial)) continue;
            PresentationCodecs.TrimMaterial material = (PresentationCodecs.TrimMaterial) definition.decoded;
            if (material.ingredient != null && material.ingredient.test(addition, context)) {
                if (match != null) throw new IllegalStateException("Multiple trim materials match item " + itemId);
                match = definition.id;
            }
        }
        return match;
    }

    public ResourceLocation resolveTrimPattern(ResourceLocation explicitPattern, net.minecraft.item.ItemStack template) {
        if (explicitPattern != null) {
            Definition definition = definitions.get(PresentationIds.key("trim_pattern", explicitPattern));
            if (definition == null || !(definition.decoded instanceof PresentationCodecs.TrimPattern))
                throw new IllegalArgumentException("Unknown trim pattern " + explicitPattern);
            return definition.id;
        }
        if (template == null || template.isEmpty()) return null;
        ResourceLocation templateId = net.minecraft.item.Item.REGISTRY.getNameForObject(template.getItem());
        ResourceLocation match = null;
        for (Definition definition : definitions.values()) {
            if (!(definition.decoded instanceof PresentationCodecs.TrimPattern)) continue;
            ResourceLocation requiredTemplate = ((PresentationCodecs.TrimPattern) definition.decoded).templateItem;
            if (requiredTemplate != null && requiredTemplate.equals(templateId)) {
                if (match != null) throw new IllegalStateException("Multiple trim patterns use template item " + templateId);
                match = definition.id;
            }
        }
        return match;
    }

    private static ResourceLocation vanillaTrimMaterial(ResourceLocation item) {
        if (item == null || !"minecraft".equals(item.getNamespace())) return null;
        switch (item.getPath()) {
            case "amethyst_shard": return new ResourceLocation("minecraft", "amethyst");
            case "copper_ingot": return new ResourceLocation("minecraft", "copper");
            case "diamond": return new ResourceLocation("minecraft", "diamond");
            case "emerald": return new ResourceLocation("minecraft", "emerald");
            case "gold_ingot": return new ResourceLocation("minecraft", "gold");
            case "iron_ingot": return new ResourceLocation("minecraft", "iron");
            case "lapis_lazuli": return new ResourceLocation("minecraft", "lapis");
            case "netherite_ingot": return new ResourceLocation("minecraft", "netherite");
            case "quartz": return new ResourceLocation("minecraft", "quartz");
            case "redstone": return new ResourceLocation("minecraft", "redstone");
            case "resin_brick": return new ResourceLocation("minecraft", "resin");
            default: return null;
        }
    }

    private void applyTrim(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3 || !(sender.getCommandSenderEntity() instanceof net.minecraft.entity.player.EntityPlayer)) throw usage();
        Definition material = require(args[1], "trim_material");
        Definition pattern = require(args[2], "trim_pattern");
        net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) sender.getCommandSenderEntity();
        net.minecraft.item.ItemStack armor = player.getHeldItemMainhand();
        if (armor.isEmpty() || !(armor.getItem() instanceof net.minecraft.item.ItemArmor))
            throw new CommandException("Hold an armor piece to apply the trim");
        net.minecraft.nbt.NBTTagCompound tag = armor.getTagCompound();
        if (tag == null) { tag = new net.minecraft.nbt.NBTTagCompound(); armor.setTagCompound(tag); }
        net.minecraft.nbt.NBTTagCompound trim = new net.minecraft.nbt.NBTTagCompound();
        trim.setString("material", material.id.toString());
        trim.setString("pattern", pattern.id.toString());
        tag.setTag("rml_trim", trim);
        player.inventoryContainer.detectAndSendChanges();
    }

    private void placePainting(ICommandSender sender, String[] args) throws CommandException {
        if ((args.length != 2 && args.length != 3) || !(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) throw usage();
        Definition definition = require(args[1], "painting_variant");
        PresentationCodecs.PaintingVariant paintingVariant = (PresentationCodecs.PaintingVariant) definition.decoded;
        EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
        net.minecraft.util.EnumFacing facing = args.length == 3
                ? net.minecraft.util.EnumFacing.byName(args[2].toLowerCase(java.util.Locale.ROOT))
                : player.getHorizontalFacing().getOpposite();
        if (facing == null || !facing.getAxis().isHorizontal()) throw new CommandException("Painting direction must be north, south, east, or west");
        net.minecraft.util.math.BlockPos anchor = new net.minecraft.util.math.BlockPos(player.posX, player.posY + 1.0D, player.posZ);
        EntityPresentationPainting painting = new EntityPresentationPainting(player.world, anchor, facing);
        painting.configure(definition.id, paintingVariant.width * 16, paintingVariant.height * 16);
        if (!painting.onValidSurface()) throw new CommandException("There is no clear wall surface for painting '%s'", definition.id);
        painting.getEntityData().setString("rml_presentation_variant", definition.id.toString());
        painting.getEntityData().setString("rml_presentation_variant_type", "painting_variant");
        painting.getEntityData().setString("rml_presentation_variant_texture", paintingVariant.texture.toString());
        if (!player.world.spawnEntity(painting)) throw new CommandException("Could not place painting '%s'", definition.id);
        painting.playPlaceSound();
        PresentationNetwork.syncSurfaceVariant(painting, definition.id, "painting_variant", paintingVariant.texture);
    }

    private void placePot(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 2 || potBlock == null || sender.getPosition() == null || sender.getEntityWorld() == null) throw usage();
        Definition pattern = require(args[1], "decorated_pot_pattern");
        net.minecraft.util.math.BlockPos target = sender.getPosition().offset(sender.getCommandSenderEntity() == null
                ? net.minecraft.util.EnumFacing.NORTH : sender.getCommandSenderEntity().getHorizontalFacing());
        if (!sender.getEntityWorld().isBlockLoaded(target) || !sender.getEntityWorld().getBlockState(target).getBlock().isReplaceable(sender.getEntityWorld(), target))
            throw new CommandException("The block in front of you is not clear");
        if (!sender.getEntityWorld().setBlockState(target, potBlock.getDefaultState(), 3)) throw new CommandException("Could not place decorated pot");
        net.minecraft.tileentity.TileEntity tile = sender.getEntityWorld().getTileEntity(target);
        if (!(tile instanceof TileEntityPresentationPot)) throw new CommandException("Decorated pot tile entity did not initialize");
        ((TileEntityPresentationPot) tile).setPattern(pattern.id);
    }

    void executeDialogAction(EntityPlayerMP player, ResourceLocation id, int index) {
        Definition dialog = definitions.get(PresentationIds.key("dialog", id));
        if (dialog == null) return;
        PresentationCodecs.DialogDefinition decoded = (PresentationCodecs.DialogDefinition) dialog.decoded;
        PresentationCodecs.DialogAction action = decoded.actions[index];
        if (action instanceof PresentationCodecs.MessageAction) {
            player.getServer().getPlayerList().sendMessage(new TextComponentString(((PresentationCodecs.MessageAction) action).text));
        } else if (action instanceof PresentationCodecs.FunctionAction) {
            ResourceLocation function = ((PresentationCodecs.FunctionAction) action).function;
            DataContext context = new DataContext(player.getServerWorld(), player, player.getPosition(), player.getRNG(), 0);
            rules.invoke(function, context);
        }
    }

    private Definition require(String id, String type) throws CommandException {
        ResourceLocation location = new ResourceLocation(id);
        Definition value = definitions.get(PresentationIds.key(type, location));
        if (value == null) throw new CommandException("Unknown %s '%s'", type, id);
        return value;
    }

    private static Definition parse(CompiledResource resource) {
        JsonElement element = resource.getValue();
        if (!element.isJsonObject()) throw new IllegalArgumentException("definition must be an object");
        JsonObject json = element.getAsJsonObject();
        Definition definition = new Definition(resource.getId(), resource.getType(), json);
        if ("chat_type".equals(resource.getType())) {
            definition.text("color", "white");
            String color = definition.text("color", "white");
            TextFormatting formatting = TextFormatting.getValueByName(color.toUpperCase(java.util.Locale.ROOT));
            if (formatting == null || !formatting.isColor()) throw new IllegalArgumentException("color must be a Minecraft text color");
        }
        if ("instrument".equals(resource.getType()) || "jukebox_song".equals(resource.getType())) {
            JsonElement sound = json.get("sound");
            if (sound == null || !sound.isJsonPrimitive()) throw new IllegalArgumentException("sound must be a resource identifier");
            new ResourceLocation(sound.getAsString());
            float volume = definition.number("volume", 1.0F);
            float pitch = definition.number("pitch", 1.0F);
            if (volume < 0 || pitch <= 0) throw new IllegalArgumentException("volume must be nonnegative and pitch must be positive");
        }
        return definition;
    }

    private static CommandException usage() {
        return new CommandException("Usage: rml chat <chat_type> <message...> | dialog <id> | instrument <id> | jukebox <id> | variant <id>");
    }

    private static final class Definition {
        final ResourceLocation id;
        final String type;
        final JsonObject json;
        Definition(ResourceLocation id, String type, JsonObject json) { this.id = id; this.type = type; this.json = json; }
        Object decoded;
        String text(String key, String fallback) { JsonElement value = json.get(key); return value == null || !value.isJsonPrimitive() ? fallback : value.getAsString(); }
        float number(String key, float fallback) { JsonElement value = json.get(key); return value == null ? fallback : value.getAsFloat(); }
    }
}
