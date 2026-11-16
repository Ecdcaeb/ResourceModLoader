package rml.loader.content.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import rml.loader.content.CompiledResource;
import rml.loader.content.CompiledStructureModule;
import rml.loader.content.ContentCatalog;
import rml.loader.content.data.DataRules;
import rml.loader.content.CompiledTagModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bridges RML world-generation definitions to Forge 1.12 registries and chunk execution. */
public final class WorldgenModule {
    private static final Set<String> TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "dimension", "dimension_type", "worldgen/biome", "worldgen/block_state_provider", "worldgen/carver",
            "worldgen/density_function", "worldgen/feature", "worldgen/flat_level_generator_preset", "worldgen/material_condition",
            "worldgen/material_rule", "worldgen/multi_noise_biome_source_parameter_list", "worldgen/noise", "worldgen/noise_settings",
            "worldgen/placed_feature", "worldgen/processor_list", "worldgen/structure", "worldgen/structure_set", "worldgen/template_pool", "worldgen/world_preset")));
    private final ContentCatalog catalog;
    @SuppressWarnings("unused") private final DataRules rules;
    @SuppressWarnings("unused") private final CompiledStructureModule structures;
    @SuppressWarnings("unused") private final CompiledTagModule tags;
    private final Map<String, Definition> definitions = new HashMap<>();
    private final Map<Integer, Definition> dimensions = new HashMap<>();
    private final Map<Integer, DimensionType> dimensionTypes = new HashMap<>();
    private final Map<ResourceLocation, Integer> dimensionIds = new HashMap<>();
    private final Map<ResourceLocation, DimensionType> namedDimensionTypes = new HashMap<>();
    private final Map<ResourceLocation, WorldgenCodecs.Noise> noises = new HashMap<>();
    private rml.deserializer.DeserializerManager decoderManager;
    private boolean initialized;

    public WorldgenModule(ContentCatalog catalog, DataRules rules, CompiledStructureModule structures, CompiledTagModule tags) {
        this.catalog = catalog; this.rules = rules; this.structures = structures; this.tags = tags;
    }

    public Set<String> getTypes() { return TYPES; }
    public void registerDecoders(rml.deserializer.DeserializerManager manager) { this.decoderManager = manager; WorldgenCodecs.registerDecoders(manager, noises::get); }
    public Integer getDimensionId(ResourceLocation id) { return dimensionIds.get(id); }
    public Set<String> getCommands() { return Collections.singleton("worldgen"); }

    /** Parses every source before touching native registries. Call during preInit. */
    public synchronized void initialize() {
        if (initialized) return;
        Map<String, Definition> parsed = new HashMap<>();
        Map<Integer, Definition> parsedDimensions = new HashMap<>();
        Map<ResourceLocation, Integer> parsedDimensionIds = new HashMap<>();
        for (String type : TYPES) for (Map.Entry<ResourceLocation, CompiledResource> entry : catalog.snapshot().resources(type).entrySet()) {
            CompiledResource resource = entry.getValue();
            try {
                if (!resource.getValue().isJsonObject()) throw new IllegalArgumentException("definition must be an object");
                JsonObject json = resource.getValue().getAsJsonObject();
                Definition definition = Definition.parse(type, entry.getKey(), String.valueOf(resource.getSource()), json);
                parsed.put(definitionKey(type, entry.getKey()), definition);
                if (type.equals("dimension")) {
                    int id = requiredInt(json, "id");
                    if (parsedDimensions.put(id, definition) != null) throw new IllegalArgumentException("duplicate dimension id " + id);
                    parsedDimensionIds.put(entry.getKey(), id);
                }
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Invalid worldgen resource " + resource.getId() + " from " + resource.getSource() + ": " + error.getMessage(), error);
            }
        }
        for (Definition definition : parsed.values()) {
            try { validateReferences(definition, parsed); }
            catch (RuntimeException error) { throw new IllegalArgumentException("Invalid worldgen resource " + definition.id + " from " + definition.source + ": " + error.getMessage(), error); }
        }
        for (Definition definition : parsed.values()) if ("worldgen/world_preset".equals(definition.type)) {
            for (Map.Entry<String,JsonElement> entry : definition.json.getAsJsonObject("dimensions").entrySet()) {
                JsonElement selected=entry.getValue(); if(selected.isJsonObject()) selected=selected.getAsJsonObject().get("dimension");
                if(selected==null||!selected.isJsonPrimitive()) throw new IllegalArgumentException("Invalid dimension selection " + entry.getKey() + " in world preset " + definition.id);
                ResourceLocation dimensionId=new ResourceLocation(selected.getAsString());
                if(find(parsed,"dimension",dimensionId)==null) throw new IllegalArgumentException("World preset " + definition.id + " refers to unknown dimension " + dimensionId);
            }
        }
        for(Definition definition:parsed.values()) if("worldgen/flat_level_generator_preset".equals(definition.type)) {
            JsonObject settings=definition.json.has("settings")?definition.json.getAsJsonObject("settings"):definition.json;
            if(settings.has("biome")) {
                ResourceLocation biomeId=new ResourceLocation(settings.get("biome").getAsString());
                if(find(parsed,"worldgen/biome",biomeId)==null&&!ForgeRegistries.BIOMES.containsKey(biomeId)) throw new IllegalArgumentException("Flat preset " + definition.id + " refers to unknown biome " + biomeId);
            }
        }
        for (Definition definition : parsed.values()) if ("worldgen/noise".equals(definition.type)) {
            try { noises.put(definition.id, decoderManager.decode(WorldgenCodecs.Noise.class, definition.json)); }
            catch (Exception error) { throw new IllegalArgumentException("Invalid noise " + definition.id + " from " + definition.source + ": " + error.getMessage(), error); }
        }
        Map<ResourceLocation, WorldgenCodecs.DensityFunction> densityFunctions = new HashMap<>();
        for (Definition definition : parsed.values()) if ("worldgen/density_function".equals(definition.type)) {
            try { densityFunctions.put(definition.id, WorldgenCodecs.decodeDensityFunction(decoderManager, definition.json)); }
            catch (Exception error) { throw new IllegalArgumentException("Invalid density function " + definition.id + " from " + definition.source + ": " + error.getMessage(), error); }
        }
        Map<ResourceLocation, WorldgenCodecs.DensityFunction> decodedDensities = new HashMap<>();
        for (Definition definition : parsed.values()) if ("worldgen/noise_settings".equals(definition.type) && definition.json.has("density_function")) {
            JsonElement expression=definition.json.get("density_function");
            try {
                if(expression.isJsonPrimitive()&&expression.getAsJsonPrimitive().isString()) {
                    ResourceLocation id=new ResourceLocation(expression.getAsString()); WorldgenCodecs.DensityFunction function=densityFunctions.get(id);
                    if(function==null) throw new IllegalArgumentException("Unknown density_function " + id);
                    decodedDensities.put(definition.id,function);
                } else decodedDensities.put(definition.id,WorldgenCodecs.decodeDensityFunction(decoderManager,expression));
            } catch(Exception error) { throw new IllegalArgumentException("Invalid density function in " + definition.id + " from " + definition.source + ": " + error.getMessage(),error); }
        }
        Map<ResourceLocation, WorldgenCodecs.MaterialRule> materialRules = new HashMap<>();
        for (Definition definition : parsed.values()) if ("worldgen/material_rule".equals(definition.type)) {
            try { materialRules.put(definition.id, WorldgenCodecs.decodeMaterialRule(decoderManager, definition.json)); }
            catch (Exception error) { throw new IllegalArgumentException("Invalid material rule " + definition.id + " from " + definition.source + ": " + error.getMessage(), error); }
        }
        Map<ResourceLocation, WorldgenCodecs.MaterialRule> settingsRules = new HashMap<>();
        for (Definition definition : parsed.values()) if ("worldgen/noise_settings".equals(definition.type) && definition.json.has("material_rule")) {
            JsonElement expression = definition.json.get("material_rule");
            try {
                if (expression.isJsonPrimitive() && expression.getAsJsonPrimitive().isString()) {
                    ResourceLocation ruleId = new ResourceLocation(expression.getAsString());
                    WorldgenCodecs.MaterialRule rule = materialRules.get(ruleId);
                    if (rule == null) throw new IllegalArgumentException("Unknown material_rule " + ruleId);
                    settingsRules.put(definition.id, rule);
                } else settingsRules.put(definition.id, WorldgenCodecs.decodeMaterialRule(decoderManager, expression));
            } catch (Exception error) { throw new IllegalArgumentException("Invalid material_rule in " + definition.id + " from " + definition.source + ": " + error.getMessage(), error); }
        }
        for (Definition definition : parsedDimensions.values()) {
            try { RmlChunkGenerator.validateExecutableSettings(definition.json, parsed, decoderManager); }
            catch (RuntimeException error) { throw new IllegalArgumentException("Invalid dimension " + definition.id + " from " + definition.source + ": " + error.getMessage(), error); }
        }
        definitions.putAll(parsed);
        dimensions.putAll(parsedDimensions);
        dimensionIds.putAll(parsedDimensionIds);
        for (Definition definition : parsed.values()) if (definition.type.equals("worldgen/biome")) {
            try { registerBiome(definition); }
            catch (RuntimeException error) { throw new IllegalArgumentException("Cannot register biome " + definition.id + " from " + definition.source + ": " + error.getMessage(), error); }
        }
        for (Definition definition : parsed.values()) if ("dimension_type".equals(definition.type)) {
            try { registerDimensionType(definition); }
            catch (RuntimeException error) { throw new IllegalArgumentException("Cannot register dimension type " + definition.id + " from " + definition.source + ": " + error.getMessage(), error); }
        }
        for (Map.Entry<Integer, Definition> entry : dimensions.entrySet()) {
            Definition definition = entry.getValue();
            try {
                ResourceLocation settingsId = new ResourceLocation(definition.json.get("noise_settings").getAsString());
                RmlWorldProvider.bind(entry.getKey(), definition, definitions, decodedDensities.get(settingsId), settingsRules.get(settingsId), decoderManager, tags, structures);
                registerDimension(entry.getKey(), definition);
            } catch (RuntimeException error) { throw new IllegalArgumentException("Cannot register dimension " + definition.id + " from " + definition.source + ": " + error.getMessage(), error); }
        }
        MinecraftForge.EVENT_BUS.register(this);
        initialized = true;
    }
    static String definitionKey(String type,ResourceLocation id) { return type + "|" + id; }
    static Definition find(Map<String,Definition> definitions,String type,ResourceLocation id) { return definitions.get(definitionKey(type,id)); }
    static Definition findAny(Map<String,Definition> definitions,ResourceLocation id) { for(Definition definition:definitions.values()) if(definition.id.equals(id)) return definition; return null; }
    private static void validateReferences(Definition d, Map<String, Definition> all) {
        for (String field : new String[]{"biome", "biome_source", "noise_settings", "flat_preset", "noise", "feature", "placed_feature", "structure", "template_pool", "processor_list", "material_rule", "material_condition", "block_state_provider", "density_function", "carver", "structure_set", "dimension_type"}) {
            JsonElement value = d.json.get(field);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                ResourceLocation id = new ResourceLocation(value.getAsString());
                if (findAny(all,id) == null && !ForgeRegistries.BIOMES.containsKey(id) && !Block.REGISTRY.containsKey(id))
                    throw new IllegalArgumentException("unknown " + field + " reference " + id);
            }
        }
    }

    private void registerBiome(Definition d) {
        if (ForgeRegistries.BIOMES.containsKey(d.id)) throw new IllegalArgumentException("biome id already registered: " + d.id);
        JsonObject json = d.json;
        JsonObject desc = json.getAsJsonObject("description");
        String name = desc.has("translate") ? desc.get("translate").getAsString() : d.id.toString();
        Biome.BiomeProperties properties = new Biome.BiomeProperties(name);
        if (json.has("temperature")) properties.setTemperature(json.get("temperature").getAsFloat());
        if (json.has("downfall")) properties.setRainfall(json.get("downfall").getAsFloat());
        if (json.has("has_precipitation") && !json.get("has_precipitation").getAsBoolean()) properties.setRainDisabled();
        if (json.has("depth")) properties.setBaseHeight(json.get("depth").getAsFloat());
        if (json.has("scale")) properties.setHeightVariation(json.get("scale").getAsFloat());
        final boolean hasSkyColor = json.has("effects") && json.getAsJsonObject("effects").has("sky_color");
        final int skyColor = hasSkyColor ? json.getAsJsonObject("effects").get("sky_color").getAsInt() : 0;
        Biome biome = new Biome(properties) {
            @Override public int getSkyColorByTemp(float temperature) {
                return hasSkyColor ? skyColor : super.getSkyColorByTemp(temperature);
            }
        };
        int numericId = 40;
        while (numericId < 256 && Biome.REGISTRY.getObjectById(numericId) != null) numericId++;
        if (numericId == 256) throw new IllegalStateException("No available 1.12 biome ids remain for " + d.id);
        Biome.REGISTRY.register(numericId, d.id, biome);
    }

    private void registerDimensionType(Definition definition) {
        JsonObject json = definition.json;
        String name = json.has("name") ? json.get("name").getAsString() : definition.id.getPath();
        String suffix = json.has("suffix") ? json.get("suffix").getAsString() : "_" + definition.id.getPath();
        int typeId = json.has("type_id") ? json.get("type_id").getAsInt() : nextDimensionTypeId();
        boolean keepLoaded = json.has("keep_loaded") && json.get("keep_loaded").getAsBoolean();
        DimensionType type = DimensionType.register(name, suffix, typeId, RmlWorldProvider.class, keepLoaded);
        namedDimensionTypes.put(definition.id, type);
    }

    private void registerDimension(int id, Definition d) {
        JsonObject data = d.json;
        DimensionType type;
        if (data.has("dimension_type")) {
            ResourceLocation typeId = new ResourceLocation(data.get("dimension_type").getAsString());
            type = namedDimensionTypes.get(typeId);
            if (type == null) throw new IllegalArgumentException("Unknown dimension_type " + typeId + " for " + d.id);
        } else {
            String name = data.has("type") ? data.get("type").getAsString() : d.id.getPath();
            int typeId = data.has("type_id") ? data.get("type_id").getAsInt() : nextDimensionTypeId();
            type = DimensionType.register(name, "_" + d.id.getPath(), typeId, RmlWorldProvider.class, false);
        }
        if (DimensionManager.isDimensionRegistered(id)) throw new IllegalArgumentException("dimension id already registered: " + id);
        dimensionTypes.put(id, type);
        DimensionManager.registerDimension(id, type);
    }

    private static int nextDimensionTypeId() {
        Set<Integer> used = new HashSet<>();
        for (DimensionType type : DimensionType.values()) used.add(type.getId());
        int id = 2;
        while (used.contains(id)) id++;
        return id;
    }

    @SubscribeEvent public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof WorldServer) RmlChunkGenerator.release((WorldServer) event.getWorld());
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2 || !"worldgen".equals(args[0])) throw new CommandException("commands.rml.worldgen.usage");
        if ("list".equals(args[1])) {
            sender.sendMessage(new net.minecraft.util.text.TextComponentString("Worldgen definitions: " + definitions.size()));
            return;
        }
        if ("inspect".equals(args[1]) && args.length == 3) {
            ResourceLocation id = new ResourceLocation(args[2]);
            Definition definition = findAny(definitions,id);
            if (definition == null) throw new CommandException("Unknown worldgen definition " + id);
            sender.sendMessage(new net.minecraft.util.text.TextComponentString(definition.type + " " + id + " from " + definition.source));
            return;
        }
        if ("chunk".equals(args[1]) && args.length == 4) {
            if (!(sender.getEntityWorld() instanceof WorldServer)) throw new CommandException("Worldgen chunk command requires a server world");
            WorldServer world = (WorldServer) sender.getEntityWorld();
            int x = parseInt(args[2]), z = parseInt(args[3]);
            world.getChunkProvider().provideChunk(x, z);
            sender.sendMessage(new net.minecraft.util.text.TextComponentString("Generated chunk " + x + "," + z + " in dimension " + world.provider.getDimension()));
            return;
        }
        if ("preset".equals(args[1]) && (args.length == 4 || args.length == 6)) {
            ResourceLocation presetId = new ResourceLocation(args[2]);
            Definition preset = find(definitions,"worldgen/world_preset",presetId);
            if (preset == null || !preset.json.has("dimensions")) throw new CommandException("Unknown world preset " + presetId);
            JsonObject dimensions = preset.json.getAsJsonObject("dimensions");
            String key=args[3]; JsonElement selected=dimensions.get(key);
            if(selected==null) { ResourceLocation keyId=new ResourceLocation(key); selected=dimensions.get(keyId.toString()); }
            if(selected==null) throw new CommandException("Preset " + presetId + " has no dimension " + key);
            ResourceLocation dimensionId=new ResourceLocation(selected.isJsonPrimitive()?selected.getAsString():selected.getAsJsonObject().get("dimension").getAsString());
            Definition dimension=find(definitions,"dimension",dimensionId);
            if(dimension==null) throw new CommandException("Preset refers to missing dimension " + dimensionId);
            int nativeId=requiredInt(dimension.json,"id");
            if(!DimensionManager.isDimensionRegistered(nativeId)) throw new CommandException("Dimension " + dimensionId + " is not registered");
            WorldServer target=DimensionManager.getWorld(nativeId);
            if(target==null) { DimensionManager.initDimension(nativeId); target=DimensionManager.getWorld(nativeId); }
            if(target==null) throw new CommandException("Dimension " + dimensionId + " could not be loaded");
            int x=args.length==6?parseInt(args[4]):sender.getPosition().getX()>>4;
            int z=args.length==6?parseInt(args[5]):sender.getPosition().getZ()>>4;
            target.getChunkProvider().provideChunk(x,z);
            sender.sendMessage(new net.minecraft.util.text.TextComponentString("Preset " + presetId + " selected dimension " + dimensionId + " and generated chunk " + x + "," + z));
            return;
        }
        throw new CommandException("commands.rml.worldgen.usage");
    }

    private static int parseInt(String text) throws CommandException { try { return Integer.parseInt(text); } catch (NumberFormatException e) { throw new CommandException("Expected integer: " + text); } }
    private static String requiredString(JsonObject json, String key) { if (!json.has(key) || !json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("missing string '" + key + "'"); return json.get(key).getAsString(); }
    private static int requiredInt(JsonObject json, String key) { if (!json.has(key) || !json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("missing integer '" + key + "'"); return json.get(key).getAsInt(); }

    static final class Definition {
        final String type; final ResourceLocation id; final String source; final JsonObject json;
        private Definition(String type, ResourceLocation id, String source, JsonObject json) { this.type=type; this.id=id; this.source=source; this.json=json; }
        static Definition parse(String type, ResourceLocation id, String source, JsonObject json) {
            if (source == null || source.isEmpty()) source = id.toString();
            if (type.equals("worldgen/noise_settings")) {
                if (!json.has("sea_level") || !json.has("default_block") || !json.has("default_fluid")) throw new IllegalArgumentException("noise_settings requires sea_level, default_block, default_fluid");
                resolveBlockState(json.get("default_block")); resolveBlockState(json.get("default_fluid"));
            } else if (type.equals("worldgen/biome")) {
                if (!json.has("description") || !json.get("description").isJsonObject()) throw new IllegalArgumentException("biome requires description object");
            } else if (type.equals("dimension")) {
                requiredInt(json, "id");
                if (!json.has("noise_settings") || (!json.has("biome") && !json.has("biome_source") && !json.has("flat_preset"))) throw new IllegalArgumentException("dimension requires noise_settings and biome, biome_source, or flat_preset");
            } else if (type.equals("dimension_type")) {
                if (json.has("type_id")) requiredInt(json, "type_id");
            } else if (type.equals("worldgen/multi_noise_biome_source_parameter_list")) {
                if (!json.has("biomes") || json.getAsJsonArray("biomes").size() == 0) throw new IllegalArgumentException("multi-noise parameter list requires non-empty biomes array");
                for (JsonElement entry : json.getAsJsonArray("biomes")) if (!entry.isJsonObject() || (!entry.getAsJsonObject().has("biome") && !entry.getAsJsonObject().has("tag"))) throw new IllegalArgumentException("multi-noise entries require biome or tag");
            } else if (type.equals("worldgen/feature")) {
                String featureType = requiredString(json, "type");
                if (!Arrays.asList("minecraft:ore", "minecraft:disk", "minecraft:random_patch").contains(featureType)) throw new IllegalArgumentException("unsupported feature type " + featureType);
            } else if (type.equals("worldgen/carver")) {
                String carverType = requiredString(json, "type");
                if (!"minecraft:cave".equals(carverType)) throw new IllegalArgumentException("unsupported carver type " + carverType);
                if (!json.has("config") || !json.getAsJsonObject("config").has("probability")) throw new IllegalArgumentException("cave carver requires config.probability");
                double probability=json.getAsJsonObject("config").get("probability").getAsDouble();
                if (probability<0||probability>1) throw new IllegalArgumentException("carver probability must be between zero and one");
            } else if (type.equals("worldgen/structure")) {
                if (!json.has("template") && !json.has("template_pool")) throw new IllegalArgumentException("structure requires template or template_pool");
            } else if (type.equals("worldgen/template_pool")) {
                if (!json.has("elements") || !json.get("elements").isJsonArray() || json.getAsJsonArray("elements").size() == 0) throw new IllegalArgumentException("template_pool requires non-empty elements array");
                for (JsonElement element : json.getAsJsonArray("elements")) if (!element.isJsonObject() || !element.getAsJsonObject().has("template")) throw new IllegalArgumentException("template_pool elements require template");
            } else if (type.equals("worldgen/structure_set")) {
                if (!json.has("structures") || !json.get("structures").isJsonArray() || !json.has("placement")) throw new IllegalArgumentException("structure_set requires structures and placement");
                JsonObject placement=json.getAsJsonObject("placement"); requiredInt(placement,"spacing"); requiredInt(placement,"separation"); requiredInt(placement,"salt");
            } else if (type.equals("worldgen/flat_level_generator_preset")) {
                JsonObject settings=json.has("settings")?json.getAsJsonObject("settings"):json;
                if(!settings.has("layers")||!settings.get("layers").isJsonArray()||settings.getAsJsonArray("layers").size()==0) throw new IllegalArgumentException("flat preset requires nonempty layers");
                for(JsonElement layer:settings.getAsJsonArray("layers")) if(!layer.isJsonObject()||(!layer.getAsJsonObject().has("block")&&!layer.getAsJsonObject().has("block_state"))||!layer.getAsJsonObject().has("height")) throw new IllegalArgumentException("flat layers require block/block_state and height");
            } else if (type.equals("worldgen/world_preset")) {
                if(!json.has("dimensions")||!json.get("dimensions").isJsonObject()||json.getAsJsonObject("dimensions").size()==0) throw new IllegalArgumentException("world_preset requires a nonempty dimensions object");
            } else if (type.equals("worldgen/processor_list")) {
                if(!json.has("processors")||!json.get("processors").isJsonArray()) throw new IllegalArgumentException("processor_list requires processors array");
                for(JsonElement processor:json.getAsJsonArray("processors")) if(!processor.isJsonObject()||!processor.getAsJsonObject().has("type")) throw new IllegalArgumentException("processor entries require a type");
            }
            return new Definition(type, id, source, json);
        }
    }

    static IBlockState resolveBlockState(JsonElement e) {
        if (!e.isJsonObject()) throw new IllegalArgumentException("block state must be an object");
        JsonObject state = e.getAsJsonObject();
        ResourceLocation id = new ResourceLocation(requiredString(state, "Name"));
        Block block = Block.REGISTRY.getObject(id);
        if (block == null) throw new IllegalArgumentException("unknown block " + id);
        IBlockState result = block.getDefaultState();
        if (state.has("Properties")) for (Map.Entry<String, JsonElement> property : state.getAsJsonObject("Properties").entrySet()) {
            net.minecraft.block.properties.IProperty<?> nativeProperty = block.getBlockState().getProperty(property.getKey());
            if (nativeProperty == null) throw new IllegalArgumentException("unknown property " + property.getKey() + " on " + id);
            result = applyProperty(result, nativeProperty, property.getValue().getAsString());
        }
        return result;
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static IBlockState applyProperty(IBlockState state, net.minecraft.block.properties.IProperty property, String value) {
        com.google.common.base.Optional<?> parsed = property.parseValue(value);
        if (!parsed.isPresent()) throw new IllegalArgumentException("invalid property value " + value);
        Object parsedValue = parsed.get();
        return state.withProperty(property, (Comparable) parsedValue);
    }
}
