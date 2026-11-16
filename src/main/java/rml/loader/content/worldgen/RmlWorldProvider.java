package rml.loader.content.worldgen;
import com.google.gson.JsonObject;

import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.DimensionManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Provider used by dimensions declared in content; definition lookup is by native dimension id. */
public final class RmlWorldProvider extends WorldProvider {
    private static final Map<Integer, WorldgenModule.Definition> DIMENSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<String, WorldgenModule.Definition>> DEFINITIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, WorldgenCodecs.DensityFunction> DENSITIES = new ConcurrentHashMap<>();
    private static final Map<Integer, WorldgenCodecs.MaterialRule> MATERIAL_RULES = new ConcurrentHashMap<>();
    private static final Map<Integer, rml.deserializer.DeserializerManager> DECODERS = new ConcurrentHashMap<>();
    private static final Map<Integer, rml.loader.content.CompiledTagModule> TAGS = new ConcurrentHashMap<>();
    private static final Map<Integer, rml.loader.content.CompiledStructureModule> TEMPLATES = new ConcurrentHashMap<>();
    static void bind(int id, WorldgenModule.Definition definition, Map<String, WorldgenModule.Definition> all, WorldgenCodecs.DensityFunction density, WorldgenCodecs.MaterialRule materialRule, rml.deserializer.DeserializerManager decoder, rml.loader.content.CompiledTagModule tags, rml.loader.content.CompiledStructureModule structures) {
        DIMENSIONS.put(id, definition);
        DEFINITIONS.put(id, all);
        DECODERS.put(id, decoder);
        TAGS.put(id, tags);
        TEMPLATES.put(id, structures);
        if (density != null) DENSITIES.put(id, density); else DENSITIES.remove(id);
        if (materialRule != null) MATERIAL_RULES.put(id, materialRule); else MATERIAL_RULES.remove(id);
    }
    static WorldgenModule.Definition definition(int id) { return DIMENSIONS.get(id); }
    static Map<String, WorldgenModule.Definition> definitions(int id) { return DEFINITIONS.get(id); }

    @Override protected void init() {
        WorldgenModule.Definition definition = DIMENSIONS.get(getDimension());
        if (definition == null) throw new IllegalStateException("No RML worldgen definition for dimension " + getDimension());
        WorldgenModule.Definition environment = definition;
        if (definition.json.has("dimension_type")) {
            ResourceLocation typeId = new ResourceLocation(definition.json.get("dimension_type").getAsString());
            WorldgenModule.Definition type = WorldgenModule.find(DEFINITIONS.get(getDimension()),"dimension_type",typeId);
            if (type != null) environment = type;
        }
        this.hasSkyLight = !environment.json.has("has_skylight") || environment.json.get("has_skylight").getAsBoolean();
        this.nether = environment.json.has("ultrawarm") && environment.json.get("ultrawarm").getAsBoolean();
        if (definition.json.has("biome_source")) {
            ResourceLocation sourceId = new ResourceLocation(definition.json.get("biome_source").getAsString());
            WorldgenModule.Definition source = WorldgenModule.find(DEFINITIONS.get(getDimension()),"worldgen/multi_noise_biome_source_parameter_list",sourceId);
            if (source == null || !"worldgen/multi_noise_biome_source_parameter_list".equals(source.type)) throw new IllegalStateException("Dimension " + definition.id + " refers to invalid multi-noise source " + sourceId);
            this.biomeProvider = new RmlBiomeProvider(source, world.getSeed(), TAGS.get(getDimension()));
        } else {
            JsonObject biomeDefinition=definition.json;
            if(!biomeDefinition.has("biome")&&biomeDefinition.has("flat_preset")) {
                ResourceLocation flatId=new ResourceLocation(biomeDefinition.get("flat_preset").getAsString());
                WorldgenModule.Definition flat=WorldgenModule.find(DEFINITIONS.get(getDimension()),"worldgen/flat_level_generator_preset",flatId);
                if(flat==null) throw new IllegalStateException("Unknown flat preset " + flatId);
                biomeDefinition=flat.json.has("settings")?flat.json.getAsJsonObject("settings"):flat.json;
            }
            ResourceLocation biomeId = reference(biomeDefinition, "biome");
            Biome biome = Biome.REGISTRY.getObject(biomeId);
            if (biome == null) throw new IllegalStateException("Dimension " + definition.id + " refers to unavailable biome " + biomeId);
            this.biomeProvider = new RmlBiomeProvider(biome, world.getSeed());
        }
    }
    @Override public IChunkGenerator createChunkGenerator() {
        WorldgenModule.Definition definition = DIMENSIONS.get(getDimension());
        if (definition == null) throw new IllegalStateException("No RML worldgen definition for dimension " + getDimension());
        return new RmlChunkGenerator(world, definition, DEFINITIONS.get(getDimension()), biomeProvider, DENSITIES.get(getDimension()), MATERIAL_RULES.get(getDimension()), DECODERS.get(getDimension()), TEMPLATES.get(getDimension()));
    }
    @Override public DimensionType getDimensionType() { return DimensionManager.getProviderType(getDimension()); }
    @Override public boolean isSurfaceWorld() { return !nether && !(getDimensionType() == DimensionType.THE_END); }
    private static ResourceLocation reference(com.google.gson.JsonObject json, String key) {
        if (!json.has(key)) throw new IllegalArgumentException("Missing worldgen reference '" + key + "'");
        return new ResourceLocation(json.get(key).getAsString());
    }
}
