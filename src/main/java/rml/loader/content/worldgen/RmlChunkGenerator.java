package rml.loader.content.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;

import java.util.HashMap;
import java.util.HashSet;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/** Seed stable chunk terrain and native feature placement for RML dimensions. */
final class RmlChunkGenerator implements IChunkGenerator {
    private static final Map<World, RmlChunkGenerator> LIVE = new ConcurrentHashMap<>();
    private final World world;
    private final WorldgenModule.Definition dimension;
    private final Map<String, WorldgenModule.Definition> definitions;
    private final BiomeProvider biomes;
    private final IBlockState solid;
    private final IBlockState fluid;
    private final int seaLevel;
    private final double baseHeight;
    private final double amplitude;
    private final List<FlatLayer> flatLayers;
    private final List<Feature> features;
    private final WorldgenCodecs.DensityFunction density;
    private final WorldgenCodecs.MaterialRule materialRule;
    private final double caveThreshold;
    private final List<StructureSet> structureSets;
    private final Map<net.minecraft.util.ResourceLocation,WorldgenStructure> compiledStructures;
    private final WorldgenStructureData structureData;
    private final Map<net.minecraft.util.ResourceLocation,Set<Long>> placingStructures = new HashMap<>();
    private final Map<Long,List<StructureBounds>> structureBounds = new HashMap<>();
    private final long seed;

    RmlChunkGenerator(World world, WorldgenModule.Definition dimension,
                      Map<String, WorldgenModule.Definition> definitions, BiomeProvider biomes,
                      WorldgenCodecs.DensityFunction density, WorldgenCodecs.MaterialRule materialRule,
                      rml.deserializer.DeserializerManager decoder, rml.loader.content.CompiledStructureModule structureTemplates) {
        this.world = world; this.dimension = dimension; this.definitions = definitions; this.biomes = biomes; this.density = density;
        this.materialRule = dimension.json.has("flat_preset") ? null : materialRule;
        if (definitions == null) throw new IllegalStateException("Missing compiled worldgen definitions for " + dimension.id);
        JsonObject settings = resolve(dimension.json, "noise_settings", definitions).json;
        this.solid = WorldgenModule.resolveBlockState(settings.get("default_block"));
        this.fluid = WorldgenModule.resolveBlockState(settings.get("default_fluid"));
        this.seaLevel = settings.get("sea_level").getAsInt();
        this.baseHeight = settings.has("base_height") ? settings.get("base_height").getAsDouble() : 64.0D;
        this.amplitude = settings.has("amplitude") ? settings.get("amplitude").getAsDouble() : 32.0D;
        JsonObject flatSettings = flatSettings(dimension.json,definitions);
        JsonObject executable = executionSettings(dimension.json,flatSettings);
        this.flatLayers = flatSettings==null?null:compileFlatLayers(flatSettings,decoder);
        this.features = compileFeatures(executable, definitions, decoder);
        this.structureSets = compileStructureSets(executable, definitions);
        this.caveThreshold = compileCarvers(dimension.json, definitions);
        this.compiledStructures = compileStructures(definitions,decoder);
        for (WorldgenStructure structure : compiledStructures.values()) structure.validateTemplates(structureTemplates);
        this.seed = world.getSeed();
        this.structureData = WorldgenStructureData.get(world);
        for (StructureBounds bounds : structureData.bounds) indexBounds(bounds);
        LIVE.put(world, this);
    }

    static void release(World world) { LIVE.remove(world); }

    @Override public Chunk generateChunk(int chunkX, int chunkZ) {
        ChunkPrimer primer = new ChunkPrimer();
        MaterialContext context = new MaterialContext(seed);
        int originX = chunkX << 4, originZ = chunkZ << 4;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = originX + x, wz = originZ + z;
            if(flatLayers==null) {
                double surface = 0;
                if (density == null) {
                    Biome biome = biomes.getBiome(context.position.setPos(wx, 0, wz));
                    surface = baseHeight + biome.getBaseHeight() * 16 + terrain(wx, wz) * (amplitude + biome.getHeightVariation() * 16);
                }
                fillDensityColumn(primer, x, z, wx, wz, seed, density, materialRule, context, solid, fluid, seaLevel, surface, caveThreshold);
            } else {
                fillFlatColumn(primer, x, z, wx, wz, flatLayers, materialRule, context);
            }
        }
        Chunk chunk = new Chunk(world, primer, chunkX, chunkZ);
        Biome[] assigned = biomes.getBiomes(null, originX, originZ, 16, 16, false);
        byte[] ids = chunk.getBiomeArray();
        for (int i = 0; i < 256; i++) ids[i] = (byte)Biome.getIdForBiome(assigned[i]);
        chunk.generateSkylightMap();
        return chunk;
    }
    static void fillDensityColumn(ChunkPrimer primer, int x, int z, int wx, int wz, long seed,
                                  WorldgenCodecs.DensityFunction density, WorldgenCodecs.MaterialRule material,
                                  MaterialContext context, IBlockState solid, IBlockState fluid,
                                  int seaLevel, double fallbackSurface, double caveThreshold) {
        for (int y = 0; y < 256; y++) {
            double value = density == null ? fallbackSurface - y : density.sample(seed, wx, y, wz);
            if (!Double.isFinite(value)) throw new IllegalStateException("Nonfinite density at " + wx + "," + y + "," + wz);
            boolean occupied = value > 0;
            if (occupied && caveThreshold < 1 && y > 3) {
                long hash = mix64(seed + (wx >> 2) * 73428767L + (y >> 2) * 912931L + (wz >> 2) * 42317861L);
                occupied = (hash >>> 11) * 0x1.0p-53 <= caveThreshold;
            }
            IBlockState state = y == 0 ? Blocks.BEDROCK.getDefaultState() : occupied ? solid : y <= seaLevel ? fluid : Blocks.AIR.getDefaultState();
            if (material != null && y != 0 && state.getBlock() != Blocks.AIR) {
                context.set(wx, y, wz, state);
                IBlockState replacement = material.apply(context);
                if (replacement != null) state = replacement;
            }
            primer.setBlockState(x, y, z, state);
        }
    }

    @Override public void populate(int chunkX, int chunkZ) {
        long chunkSeed = seed ^ (long)chunkX * 341873128712L ^ (long)chunkZ * 132897987541L;
        Random random = new Random(chunkSeed);
        for (Feature feature : features) {
            WorldgenPlacement.Context context = new WorldgenPlacement.Context(world, random, feature::place);
            feature.placement.place(context, chunkX << 4, 0, chunkZ << 4);
        }
        Biome biome = biomes.getBiome(new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8));
        biome.decorate(world, random, new BlockPos(chunkX << 4, 0, chunkZ << 4));
        placeStructures(chunkX,chunkZ);
    }
    @Override public boolean generateStructures(Chunk chunkIn, int x, int z) { return placeStructures(x,z); }
    @Override public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) { return biomes.getBiome(pos).getSpawnableList(creatureType); }
    @Nullable @Override public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        net.minecraft.util.ResourceLocation requested;
        try { requested=new net.minecraft.util.ResourceLocation(structureName); } catch(IllegalArgumentException error) { return null; }
        BlockPos known = null;
        if (!findUnexplored) for (StructureBounds bounds : structureData.bounds) if (bounds.id.equals(requested)) {
            BlockPos candidate = new BlockPos(bounds.minX, bounds.minY, bounds.minZ);
            if (known == null || candidate.distanceSq(position) < known.distanceSq(position)) known = candidate;
        }
        int startX=position.getX()>>4,startZ=position.getZ()>>4;
        for(int radius=0;radius<=64;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            int chunkX=startX+dx,chunkZ=startZ+dz;
            for(StructureSet set:structureSets) {
                int regionX=Math.floorDiv(chunkX,set.spacing),regionZ=Math.floorDiv(chunkZ,set.spacing);
                Random random=new Random(seed+regionX*341873128712L+regionZ*132897987541L+set.salt);
                int candidateX=regionX*set.spacing+random.nextInt(set.spacing-set.separation),candidateZ=regionZ*set.spacing+random.nextInt(set.spacing-set.separation);
                if(candidateX!=chunkX||candidateZ!=chunkZ) continue;
                if (findUnexplored && worldIn.isChunkGeneratedAt(chunkX, chunkZ)) continue;
                if (!set.choose(random).structure.equals(requested)) continue;
                Set<Long> processed = structureData.processed.get(set.id);
                if (processed != null && processed.contains(chunkKey(chunkX, chunkZ))) continue;
                net.minecraft.world.gen.structure.StructureBoundingBox candidate = compiledStructures.get(requested).locate(worldIn, random, (chunkX<<4)+8, (chunkZ<<4)+8);
                if (candidate != null) {
                    BlockPos found = new BlockPos(candidate.minX, candidate.minY, candidate.minZ);
                    return known != null && known.distanceSq(position) < found.distanceSq(position) ? known : found;
                }
            }
        }
        return known;
    }
    @Override public void recreateStructures(Chunk chunkIn, int x, int z) { placeStructures(x,z); }
    @Override public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        List<StructureBounds> bounds=structureBounds.get(chunkKey(pos.getX()>>4,pos.getZ()>>4));
        if(bounds==null) return false;
        for(StructureBounds bound:bounds) if((bound.id.toString().equals(structureName)||bound.id.getPath().equals(structureName))&&bound.contains(pos)) return true;
        return false;
    }

    private double terrain(int x, int z) {
        double scale = 0.0125D;
        double x0 = x * scale, z0 = z * scale;
        int ix = floor(x0), iz = floor(z0);
        double fx = fade(x0 - ix), fz = fade(z0 - iz);
        double a = mix(noise(ix, iz), noise(ix + 1, iz), fx);
        double b = mix(noise(ix, iz + 1), noise(ix + 1, iz + 1), fx);
        return mix(a, b, fz);
    }
    private double noise(int x, int z) { long h = mix64(seed ^ ((long)x * 0x9E3779B97F4A7C15L) ^ ((long)z * 0xC2B2AE3D27D4EB4FL)); return ((h >>> 11) * 0x1.0p-52) - 1.0D; }
    private static long mix64(long value) { value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L; value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL; return value ^ (value >>> 31); }
    private static int floor(double d) { int i=(int)d; return d < i ? i-1 : i; }
    private static double fade(double t) { return t*t*(3.0D-2.0D*t); }
    private static double mix(double a,double b,double t) { return a+(b-a)*t; }

    private static JsonObject flatSettings(JsonObject dimension,Map<String,WorldgenModule.Definition> definitions) {
        if(!dimension.has("flat_preset")) return null;
        net.minecraft.util.ResourceLocation id=new net.minecraft.util.ResourceLocation(dimension.get("flat_preset").getAsString());
        WorldgenModule.Definition preset=WorldgenModule.find(definitions,"worldgen/flat_level_generator_preset",id);
        if(preset==null) throw new IllegalArgumentException("Unknown flat_level_generator_preset " + id);
        return preset.json.has("settings")?preset.json.getAsJsonObject("settings"):preset.json;
    }
    private static JsonObject executionSettings(JsonObject dimension,JsonObject flat) {
        JsonObject result=new JsonObject(); for(Map.Entry<String,JsonElement> entry:dimension.entrySet()) result.add(entry.getKey(),entry.getValue());
        if(flat==null) return result;
        for(String key:new String[]{"features","structure_sets"}) if(flat.has(key)) {
            JsonArray merged=new JsonArray(); if(result.has(key)) for(JsonElement value:result.getAsJsonArray(key)) merged.add(value);
            for(JsonElement value:flat.getAsJsonArray(key)) merged.add(value); result.add(key,merged);
        }
        return result;
    }
    static List<FlatLayer> compileFlatLayers(JsonObject settings,rml.deserializer.DeserializerManager decoder) {
        if(!settings.has("layers")||!settings.get("layers").isJsonArray()||settings.getAsJsonArray("layers").size()==0) throw new IllegalArgumentException("Flat preset requires nonempty layers");
        List<FlatLayer> result=new ArrayList<>(); int total=0;
        for(JsonElement element:settings.getAsJsonArray("layers")) {
            JsonObject layer=element.getAsJsonObject(); JsonElement state=layer.has("block_state")?layer.get("block_state"):layer.get("block");
            WorldgenNodes.fields(layer, "block", "block_state", "height");
            if (layer.has("block") && layer.has("block_state")) throw new IllegalArgumentException("Flat layer must select block or block_state");
            if(state==null) throw new IllegalArgumentException("Flat layer requires block or block_state");
            int height=layer.get("height").getAsBigDecimal().intValueExact(); if(height<=0||height>256-total) throw new IllegalArgumentException("Flat layers must have positive heights totaling at most 256"); total+=height;
            try { result.add(new FlatLayer(decoder.decode(IBlockState.class,state),height)); }
            catch(rml.deserializer.JsonDeserializeException error) { throw new IllegalArgumentException("Invalid flat layer state: "+error.getMessage(),error); }
        }
        return Collections.unmodifiableList(result);
    }
    static void fillFlatColumn(ChunkPrimer primer, int x, int z, int wx, int wz, List<FlatLayer> layers, WorldgenCodecs.MaterialRule material, MaterialContext context) {
        int y = 0;
        for (int i = 0; i < layers.size(); i++) {
            FlatLayer layer = layers.get(i);
            for (int n = 0; n < layer.height; n++, y++) {
                IBlockState state = layer.state;
                if (material != null) { context.set(wx, y, wz, state); IBlockState replacement = material.apply(context); if (replacement != null) state = replacement; }
                primer.setBlockState(x, y, z, state);
            }
        }
    }
    private static List<Feature> compileFeatures(JsonObject dimension, Map<String, WorldgenModule.Definition> definitions, rml.deserializer.DeserializerManager decoder) {
        if (!dimension.has("features")) return Collections.emptyList();
        JsonArray entries = dimension.getAsJsonArray("features");
        List<Feature> result = new ArrayList<>(entries.size());
        for (JsonElement entry : entries) {
            net.minecraft.util.ResourceLocation id = new net.minecraft.util.ResourceLocation(entry.getAsString());
            WorldgenModule.Definition placed = WorldgenModule.find(definitions,"worldgen/placed_feature",id);
            if (placed == null) throw new IllegalArgumentException("Unknown placed feature " + id);
            JsonObject placedJson = placed.json;
            if (placedJson.has("feature")) WorldgenNodes.fields(placedJson, "feature", "placement", "count");
            WorldgenModule.Definition feature = placedJson.has("feature") ? resolve(placedJson, "feature", definitions) : placed;
            String type = feature.json.has("type") ? feature.json.get("type").getAsString() : "";
            JsonObject config = feature.json.has("config") ? feature.json.getAsJsonObject("config") : feature.json;
            WorldgenNodes.fields(config, "type", "state", "to_place", "size", "count", "min_y", "max_y", "height_range");
            if (!java.util.Arrays.asList("minecraft:ore", "minecraft:disk", "minecraft:random_patch", "rml:simple_block").contains(type)) throw new IllegalArgumentException("Unsupported feature type " + type);
            if (config.has("state") && config.has("to_place")) throw new IllegalArgumentException("Feature must select state or to_place");
            Kind kind = "minecraft:ore".equals(type) ? Kind.ORE : "minecraft:disk".equals(type) ? Kind.DISK : "rml:simple_block".equals(type) ? Kind.SIMPLE : Kind.SINGLE;
            JsonElement stateJson = config.has("state") ? config.get("state") : config.get("to_place");
            if (stateJson == null) throw new IllegalArgumentException("Feature " + feature.id + " lacks a state provider");
            if (stateJson.isJsonPrimitive() && stateJson.getAsJsonPrimitive().isString()) {
                net.minecraft.util.ResourceLocation providerId = new net.minecraft.util.ResourceLocation(stateJson.getAsString());
                WorldgenModule.Definition providerDefinition = WorldgenModule.find(definitions,"worldgen/block_state_provider",providerId);
                if (providerDefinition == null || !"worldgen/block_state_provider".equals(providerDefinition.type)) throw new IllegalArgumentException("Unknown block state provider " + providerId);
                stateJson = providerDefinition.json;
            }
            WorldgenCodecs.BlockStateProvider provider;
            try { provider = WorldgenCodecs.decodeBlockStateProvider(decoder, stateJson); }
            catch (rml.deserializer.JsonDeserializeException error) { throw new IllegalArgumentException("Invalid block state provider for feature " + feature.id + ": " + error.getMessage(), error); }
            if (kind == Kind.SIMPLE && config.has("size")) throw new IllegalArgumentException("simple_block has no size setting");
            if (config.has("count") && placedJson.has("count") && config != placedJson) throw new IllegalArgumentException("Count is specified in both configured and placed feature");
            if (config.has("height_range") && (config.has("min_y") || config.has("max_y"))) throw new IllegalArgumentException("height_range cannot be mixed with min_y/max_y");
            int size = config.has("size") ? config.get("size").getAsBigDecimal().intValueExact() : kind == Kind.SIMPLE ? 1 : 8;
            if (size < 1 || size > 256) throw new IllegalArgumentException("Feature size must be 1..256");
            JsonArray modifiers;
            if (placedJson.has("placement")) {
                if (placedJson.has("count") || config.has("count") || config.has("min_y") || config.has("max_y") || config.has("height_range"))
                    throw new IllegalArgumentException("Explicit placement cannot be mixed with legacy count/height fields in " + id);
                modifiers = placedJson.getAsJsonArray("placement");
            } else {
                int minY = config.has("min_y") ? config.get("min_y").getAsBigDecimal().intValueExact() : 4;
                int maxY = config.has("max_y") ? config.get("max_y").getAsBigDecimal().intValueExact() : 64;
                if (config.has("height_range")) {
                    try { rml.deserializer.value.IntRange range = decoder.decode(rml.deserializer.value.IntRange.class, config.get("height_range")); minY = range.getMin(); maxY = range.getMax(); }
                    catch (rml.deserializer.JsonDeserializeException error) { throw new IllegalArgumentException("Invalid feature height_range", error); }
                }
                if (minY < 0 || maxY > 255 || minY > maxY) throw new IllegalArgumentException("Invalid feature height range " + minY + ".." + maxY);
                modifiers = new JsonArray();
                JsonObject count = new JsonObject(); count.addProperty("type", "rml:count");
                count.add("count", config.has("count") ? config.get("count") : placedJson.has("count") ? placedJson.get("count") : new com.google.gson.JsonPrimitive(1)); modifiers.add(count);
                JsonObject square = new JsonObject(); square.addProperty("type", "rml:in_square"); modifiers.add(square);
                JsonObject height = new JsonObject(); height.addProperty("type", "rml:height");
                JsonObject uniform = new JsonObject(); uniform.addProperty("type", "rml:uniform"); uniform.addProperty("min_inclusive", minY); uniform.addProperty("max_inclusive", maxY);
                height.add("height", uniform); modifiers.add(height);
            }
            try { result.add(new Feature(kind, provider, size, WorldgenPlacement.compile(decoder, modifiers))); }
            catch (rml.deserializer.JsonDeserializeException error) { throw new IllegalArgumentException("Invalid placement for " + id + ": " + error.getMessage(), error); }
        }
        return Collections.unmodifiableList(result);
    }
    private static WorldgenModule.Definition resolve(JsonObject json, String field, Map<String, WorldgenModule.Definition> definitions) {
        net.minecraft.util.ResourceLocation id = new net.minecraft.util.ResourceLocation(json.get(field).getAsString());
        String registry = "worldgen/" + field;
        WorldgenModule.Definition result = WorldgenModule.find(definitions,registry,id);
        if (result == null) throw new IllegalArgumentException("Missing worldgen definition " + id);
        return result;
    }
    static void validateExecutableSettings(JsonObject dimension,Map<String,WorldgenModule.Definition> definitions,rml.deserializer.DeserializerManager decoder) {
        JsonObject flat=flatSettings(dimension,definitions);
        if(flat!=null) compileFlatLayers(flat,decoder);
        JsonObject executable=executionSettings(dimension,flat);
        compileFeatures(executable,definitions,decoder);
        compileStructureSets(executable,definitions);
        compileStructures(definitions,decoder);
        compileCarvers(dimension,definitions);
    }
    private static Map<net.minecraft.util.ResourceLocation,WorldgenStructure> compileStructures(Map<String,WorldgenModule.Definition> definitions,rml.deserializer.DeserializerManager decoder) {
        Map<net.minecraft.util.ResourceLocation,WorldgenStructure> compiled = new HashMap<>();
        for (WorldgenModule.Definition definition : definitions.values()) if ("worldgen/structure".equals(definition.type))
            compiled.put(definition.id, WorldgenStructure.compile(definition, definitions, decoder));
        return compiled;
    }
    private static double compileCarvers(JsonObject dimension,Map<String,WorldgenModule.Definition> definitions) {
        if(!dimension.has("carvers")) return 1.1D;
        double threshold=1.0D;
        for(JsonElement entry:dimension.getAsJsonArray("carvers")) {
            net.minecraft.util.ResourceLocation id=new net.minecraft.util.ResourceLocation(entry.getAsString());
            WorldgenModule.Definition carver=WorldgenModule.find(definitions,"worldgen/carver",id);
            if(carver==null||!"worldgen/carver".equals(carver.type)) throw new IllegalArgumentException("Unknown carver " + id);
            double probability=carver.json.getAsJsonObject("config").get("probability").getAsDouble();
            if(probability<0||probability>1) throw new IllegalArgumentException("Invalid carver probability for " + id);
            threshold=Math.min(threshold,1.0D-probability);
        }
        return threshold;
    }
    private boolean placeStructures(int chunkX,int chunkZ) {
        boolean generated=false;
        for(int index=0;index<structureSets.size();index++) {
            StructureSet set=structureSets.get(index);
            int regionX=Math.floorDiv(chunkX,set.spacing),regionZ=Math.floorDiv(chunkZ,set.spacing);
            Random random=new Random(seed+regionX*341873128712L+regionZ*132897987541L+set.salt);
            int candidateX=regionX*set.spacing+random.nextInt(set.spacing-set.separation);
            int candidateZ=regionZ*set.spacing+random.nextInt(set.spacing-set.separation);
            if(candidateX!=chunkX||candidateZ!=chunkZ) continue;
            long placementKey = chunkKey(chunkX,chunkZ);
            Set<Long> processed = structureData.processed.computeIfAbsent(set.id, key -> new HashSet<>());
            if (processed.contains(placementKey)) continue;
            Set<Long> inProgress = placingStructures.computeIfAbsent(set.id, key -> new HashSet<>());
            if (!inProgress.add(placementKey)) continue;
            try {
                StructureChoice choice = set.choose(random);
                WorldgenStructure structure = compiledStructures.get(choice.structure);
                if (structure == null) throw new IllegalStateException("Missing worldgen structure " + choice.structure);
                net.minecraft.world.gen.structure.StructureBoundingBox placed = structure.place(world, random, (chunkX << 4) + 8, (chunkZ << 4) + 8);
                if (placed != null) {
                    StructureBounds bounds = new StructureBounds(structure.id, placed.minX, placed.minY, placed.minZ, placed.maxX, placed.maxY, placed.maxZ);
                    structureData.bounds.add(bounds);
                    indexBounds(bounds);
                    generated = true;
                }
                processed.add(placementKey);
                structureData.markDirty();
            } finally { inProgress.remove(placementKey); }
        }
        return generated;
    }
    private void indexBounds(StructureBounds bounds) {
        for (int bx = Math.floorDiv(bounds.minX,16); bx <= Math.floorDiv(bounds.maxX,16); bx++) for (int bz = Math.floorDiv(bounds.minZ,16); bz <= Math.floorDiv(bounds.maxZ,16); bz++)
            structureBounds.computeIfAbsent(chunkKey(bx,bz), key -> new ArrayList<>()).add(bounds);
    }


    private static List<StructureSet> compileStructureSets(JsonObject dimension,Map<String,WorldgenModule.Definition> definitions) {
        if(!dimension.has("structure_sets")) return Collections.emptyList();
        JsonArray references=dimension.getAsJsonArray("structure_sets"); List<StructureSet> compiled=new ArrayList<>(references.size());
        for(JsonElement reference:references) {
            net.minecraft.util.ResourceLocation id=new net.minecraft.util.ResourceLocation(reference.getAsString());
            WorldgenModule.Definition definition=WorldgenModule.find(definitions,"worldgen/structure_set",id);
            if(definition==null||!"worldgen/structure_set".equals(definition.type)) throw new IllegalArgumentException("Unknown structure_set " + id);
            JsonObject placement=definition.json.getAsJsonObject("placement");
            int spacing=placement.get("spacing").getAsBigDecimal().intValueExact(),separation=placement.get("separation").getAsBigDecimal().intValueExact(),salt=placement.get("salt").getAsBigDecimal().intValueExact();
            WorldgenNodes.fields(placement, "spacing", "separation", "salt");
            if(spacing<1||separation<0||separation>=spacing) throw new IllegalArgumentException("Invalid spacing/separation for structure_set " + id);
            JsonArray structures=definition.json.getAsJsonArray("structures"); List<StructureChoice> choices=new ArrayList<>(structures.size()); long total=0;
            for(JsonElement item:structures) {
                JsonObject value=item.getAsJsonObject(); net.minecraft.util.ResourceLocation structureId=new net.minecraft.util.ResourceLocation(value.get("structure").getAsString());
                WorldgenNodes.fields(value, "structure", "weight");
                int weight=value.has("weight")?value.get("weight").getAsBigDecimal().intValueExact():1;
                if(weight<=0) throw new IllegalArgumentException("Structure weights must be positive in " + id);
                WorldgenModule.Definition structure=WorldgenModule.find(definitions,"worldgen/structure",structureId);
                if(structure==null||!"worldgen/structure".equals(structure.type)) throw new IllegalArgumentException("Unknown worldgen structure " + structureId);
                total+=weight; choices.add(new StructureChoice(structureId,weight));
            }
            if(choices.isEmpty()||total>Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid structure choices in " + id);
            compiled.add(new StructureSet(id,spacing,separation,salt,choices,(int)total));
        }
        return Collections.unmodifiableList(compiled);
    }

    private static long chunkKey(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
    static final class FlatLayer { final IBlockState state; final int height; FlatLayer(IBlockState state,int height){this.state=state;this.height=height;} }
    static final class StructureBounds {
        final net.minecraft.util.ResourceLocation id; final int minX,minY,minZ,maxX,maxY,maxZ;
        StructureBounds(net.minecraft.util.ResourceLocation id,int minX,int minY,int minZ,int maxX,int maxY,int maxZ){this.id=id;this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;}
        boolean contains(BlockPos pos){return pos.getX()>=minX&&pos.getX()<=maxX&&pos.getY()>=minY&&pos.getY()<=maxY&&pos.getZ()>=minZ&&pos.getZ()<=maxZ;}
    }
    private static final class StructureChoice { final net.minecraft.util.ResourceLocation structure; final int weight; StructureChoice(net.minecraft.util.ResourceLocation structure,int weight){this.structure=structure;this.weight=weight;} }
    private static final class StructureSet {
        final net.minecraft.util.ResourceLocation id; final int spacing,separation,salt,total; final List<StructureChoice> choices;
        StructureSet(net.minecraft.util.ResourceLocation id,int spacing,int separation,int salt,List<StructureChoice> choices,int total){this.id=id;this.spacing=spacing;this.separation=separation;this.salt=salt;this.choices=choices;this.total=total;}
        StructureChoice choose(Random random){int value=random.nextInt(total);for(StructureChoice choice:choices){if(value<choice.weight)return choice;value-=choice.weight;}throw new IllegalStateException("No structure selected from "+id);}
    }
    static final class MaterialContext implements WorldgenCodecs.MaterialContext {
        private final long seed;
        private final Random random = new Random();
        private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        private int x,y,z;
        private IBlockState current;
        MaterialContext(long seed) { this.seed=seed; }
        void set(int x,int y,int z,IBlockState current) { this.x=x;this.y=y;this.z=z;this.current=current;position.setPos(x,y,z);random.setSeed(seed^((long)x*341873128712L)^((long)y*132897987541L)^z); }
        @Override public long seed() { return seed; }
        @Override public int x() { return x; }
        @Override public int y() { return y; }
        @Override public int z() { return z; }
        @Override public IBlockState current() { return current; }
        @Override public Random random() { return random; }
        @Override public BlockPos position() { return position; }
    }
    private enum Kind { ORE, DISK, SINGLE, SIMPLE }
    private static final class Feature {
        final Kind kind;
        final WorldgenCodecs.BlockStateProvider provider;
        final int size;
        final WorldgenPlacement.Next placement;
        Feature(Kind kind, WorldgenCodecs.BlockStateProvider provider, int size, WorldgenPlacement.Next placement) {
            this.kind = kind; this.provider = provider; this.size = size; this.placement = placement;
        }
        void place(WorldgenPlacement.Context context, int x, int y, int z) {
            World world = context.world; Random random = context.random; BlockPos.MutableBlockPos pos = context.position;
            if (kind == Kind.ORE) {
                for (int i = 0; i < size; i++) {
                    int ox = x + random.nextInt(5) - random.nextInt(5), oy = y + random.nextInt(3) - random.nextInt(3), oz = z + random.nextInt(5) - random.nextInt(5);
                    if (oy >= 0 && oy <= 255 && world.getBlockState(pos.setPos(ox, oy, oz)).getBlock() == Blocks.STONE)
                        world.setBlockState(pos, provider.provide(random, pos), 2);
                }
            } else if (kind == Kind.DISK) {
                for (int ox = -size; ox <= size; ox++) for (int oz = -size; oz <= size; oz++)
                    if (ox * ox + oz * oz <= size * size && world.isAirBlock(pos.setPos(x + ox, y, z + oz))) world.setBlockState(pos, provider.provide(random, pos), 2);
            } else if (kind == Kind.SIMPLE) {
                pos.setPos(x, y, z); world.setBlockState(pos, provider.provide(random, pos), 2);
            } else {
                for (int i = 0; i < size; i++) {
                    int px = x + random.nextInt(8) - random.nextInt(8), py = y + random.nextInt(4) - random.nextInt(4), pz = z + random.nextInt(8) - random.nextInt(8);
                    if (py > 0 && py <= 255 && world.isAirBlock(pos.setPos(px, py, pz)) && world.isSideSolid(pos.down(), net.minecraft.util.EnumFacing.UP))
                        world.setBlockState(pos, provider.provide(random, pos), 2);
                }
            }
        }
    }
    interface WorldServerAccess { World world(); }
}
