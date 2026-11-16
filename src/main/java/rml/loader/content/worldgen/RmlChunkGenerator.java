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
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;

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
    private final rml.loader.content.CompiledStructureModule structureTemplates;
    private final List<StructureSet> structureSets;
    private final Map<net.minecraft.util.ResourceLocation,WorldgenCodecs.TemplateProcessor> processorLists;
    private final Set<Long> processedStructureChunks = new HashSet<>();
    private final Map<Long,List<StructureBounds>> structureBounds = new HashMap<>();
    private final long seed;

    RmlChunkGenerator(World world, WorldgenModule.Definition dimension,
                      Map<String, WorldgenModule.Definition> definitions, BiomeProvider biomes,
                      WorldgenCodecs.DensityFunction density, WorldgenCodecs.MaterialRule materialRule,
                      rml.deserializer.DeserializerManager decoder, rml.loader.content.CompiledStructureModule structureTemplates) {
        this.world = world; this.dimension = dimension; this.definitions = definitions; this.biomes = biomes; this.density = density; this.materialRule = materialRule; this.structureTemplates = structureTemplates;
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
        this.processorLists = compileProcessorLists(definitions,decoder);
        this.seed = world.getSeed();
        LIVE.put(world, this);
    }

    static void release(World world) { LIVE.remove(world); }

    @Override public Chunk generateChunk(int chunkX, int chunkZ) {
        ChunkPrimer primer = new ChunkPrimer();
        MaterialContext context = new MaterialContext(seed);
        int originX = chunkX << 4, originZ = chunkZ << 4;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = originX + x, wz = originZ + z;
            int height;
            if(flatLayers==null) {
                double terrainValue = density == null ? terrain(wx, wz) : density.sample(seed, wx, (int)baseHeight, wz);
                height = Math.max(2, Math.min(250, (int)Math.floor(baseHeight + terrainValue * amplitude)));
                int caveX = wx >> 2, caveZ = wz >> 2;
                for (int y = 0; y <= height; y++) {
                    if (caveThreshold < 1.0D && y > 3 && y < height - 4 && caveValue(caveX, y >> 2, caveZ) > caveThreshold) continue;
                    IBlockState state = y == 0 ? Blocks.BEDROCK.getDefaultState() : solid;
                    if (materialRule != null && y != 0) { context.set(wx,y,wz,state); IBlockState replacement=materialRule.apply(context); if(replacement!=null) state=replacement; }
                    primer.setBlockState(x, y, z, state);
                }
                for (int y = height + 1; y <= seaLevel && y < 256; y++) { context.set(wx,y,wz,fluid); IBlockState replacement=materialRule==null?null:materialRule.apply(context); primer.setBlockState(x,y,z,replacement==null?fluid:replacement); }
            } else {
                int y=0;
                for(FlatLayer layer:flatLayers) for(int n=0;n<layer.height;n++,y++) {
                    IBlockState state=layer.state;
                    if(materialRule!=null) { context.set(wx,y,wz,state); IBlockState replacement=materialRule.apply(context); if(replacement!=null) state=replacement; }
                    primer.setBlockState(x,y,z,state);
                }
                height=y-1;
            }
        }
        Chunk chunk = new Chunk(world, primer, chunkX, chunkZ);
        Biome[] assigned = biomes.getBiomes(null, originX, originZ, 16, 16, false);
        byte[] ids = chunk.getBiomeArray();
        for (int i = 0; i < 256; i++) ids[i] = (byte)Biome.getIdForBiome(assigned[i]);
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override public void populate(int chunkX, int chunkZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long chunkSeed = seed ^ (long)chunkX * 341873128712L ^ (long)chunkZ * 132897987541L;
        Random random = new Random(chunkSeed);
        for (Feature feature : features) {
            int count = feature.count;
            for (int n = 0; n < count; n++) {
                int x = (chunkX << 4) + random.nextInt(16);
                int z = (chunkZ << 4) + random.nextInt(16);
                int y = feature.minY + random.nextInt(Math.max(1, feature.maxY - feature.minY + 1));
                if (feature.kind == Kind.ORE) {
                    for (int i = 0; i < feature.size; i++) {
                        int ox = x + random.nextInt(5) - random.nextInt(5);
                        int oy = y + random.nextInt(3) - random.nextInt(3);
                        int oz = z + random.nextInt(5) - random.nextInt(5);
                        if (oy > 0 && oy < 256 && world.getBlockState(pos.setPos(ox, oy, oz)).getBlock() == Blocks.STONE) world.setBlockState(pos, feature.provider.provide(random,pos), 2);
                    }
                } else if (feature.kind == Kind.DISK) {
                    int radius = feature.size;
                    for (int ox = -radius; ox <= radius; ox++) for (int oz = -radius; oz <= radius; oz++)
                        if (ox * ox + oz * oz <= radius * radius && world.isAirBlock(pos.setPos(x + ox, y, z + oz))) world.setBlockState(pos, feature.provider.provide(random,pos), 2);
                } else {
                    for (int attempt=0; attempt<feature.size; attempt++) {
                        int px=x+random.nextInt(8)-random.nextInt(8), py=y+random.nextInt(4)-random.nextInt(4), pz=z+random.nextInt(8)-random.nextInt(8);
                        if(py>0 && py<256 && world.isAirBlock(pos.setPos(px,py,pz)) && world.isSideSolid(pos.down(), net.minecraft.util.EnumFacing.UP)) world.setBlockState(pos,feature.provider.provide(random,pos),2);
                    }
                }
            }
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
        int startX=position.getX()>>4,startZ=position.getZ()>>4;
        for(int radius=0;radius<=64;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            int chunkX=startX+dx,chunkZ=startZ+dz;
            for(StructureSet set:structureSets) {
                int regionX=Math.floorDiv(chunkX,set.spacing),regionZ=Math.floorDiv(chunkZ,set.spacing);
                Random random=new Random(seed+regionX*341873128712L+regionZ*132897987541L+set.salt);
                int candidateX=regionX*set.spacing+random.nextInt(set.spacing-set.separation),candidateZ=regionZ*set.spacing+random.nextInt(set.spacing-set.separation);
                if(candidateX!=chunkX||candidateZ!=chunkZ) continue;
                if(set.choose(random).structure.equals(requested)) return new BlockPos((chunkX<<4)+8,0,(chunkZ<<4)+8);
            }
        }
        return null;
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
    private double caveValue(int x, int y, int z) { long h = mix64(seed + x * 73428767L + y * 912931L + z * 42317861L); return (h >>> 11) * 0x1.0p-53; }
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
    private static List<FlatLayer> compileFlatLayers(JsonObject settings,rml.deserializer.DeserializerManager decoder) {
        if(!settings.has("layers")||!settings.get("layers").isJsonArray()||settings.getAsJsonArray("layers").size()==0) throw new IllegalArgumentException("Flat preset requires nonempty layers");
        List<FlatLayer> result=new ArrayList<>(); int total=0;
        for(JsonElement element:settings.getAsJsonArray("layers")) {
            JsonObject layer=element.getAsJsonObject(); JsonElement state=layer.has("block_state")?layer.get("block_state"):layer.get("block");
            if(state==null) throw new IllegalArgumentException("Flat layer requires block or block_state");
            int height=layer.get("height").getAsInt(); if(height<=0||(total+=height)>256) throw new IllegalArgumentException("Flat layers must have positive heights totaling at most 256");
            try { result.add(new FlatLayer(decoder.decode(IBlockState.class,state),height)); }
            catch(rml.deserializer.JsonDeserializeException error) { throw new IllegalArgumentException("Invalid flat layer state: "+error.getMessage(),error); }
        }
        return Collections.unmodifiableList(result);
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
            WorldgenModule.Definition feature = placedJson.has("feature") ? resolve(placedJson, "feature", definitions) : placed;
            String type = feature.json.has("type") ? feature.json.get("type").getAsString() : "";
            JsonObject config = feature.json.has("config") ? feature.json.getAsJsonObject("config") : feature.json;
            Kind kind = "minecraft:ore".equals(type) ? Kind.ORE : "minecraft:disk".equals(type) ? Kind.DISK : Kind.SINGLE;
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
            int size = config.has("size") ? config.get("size").getAsInt() : 8;
            int count = config.has("count") ? config.get("count").getAsInt() : placedJson.has("count") ? placedJson.get("count").getAsInt() : 1;
            int minY = config.has("min_y") ? config.get("min_y").getAsInt() : 4;
            int maxY = config.has("max_y") ? config.get("max_y").getAsInt() : 64;
            if (config.has("height_range")) {
                try { rml.deserializer.value.IntRange range = decoder.decode(rml.deserializer.value.IntRange.class, config.get("height_range")); minY=range.getMin(); maxY=range.getMax(); }
                catch (rml.deserializer.JsonDeserializeException error) { throw new IllegalArgumentException("Invalid height_range for " + feature.id + ": " + error.getMessage(), error); }
            }
            if (minY < 0 || maxY > 255 || minY > maxY) throw new IllegalArgumentException("Invalid feature height range " + minY + ".." + maxY + " for " + feature.id);
            result.add(new Feature(kind, provider, Math.max(1,count), Math.max(1,size), minY, maxY));
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
        compileProcessorLists(definitions,decoder);
        compileCarvers(dimension,definitions);
    }
    private static Map<net.minecraft.util.ResourceLocation,WorldgenCodecs.TemplateProcessor> compileProcessorLists(Map<String,WorldgenModule.Definition> definitions,rml.deserializer.DeserializerManager decoder) {
        Map<net.minecraft.util.ResourceLocation,WorldgenCodecs.TemplateProcessor> compiled=new HashMap<>();
        for(WorldgenModule.Definition definition:definitions.values()) if("worldgen/processor_list".equals(definition.type)) {
            try { compiled.put(definition.id,WorldgenCodecs.decodeProcessorList(decoder,definition.json)); }
            catch(rml.deserializer.JsonDeserializeException error) { throw new IllegalArgumentException("Invalid processor_list " + definition.id + ": " + error.getMessage(),error); }
        }
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
            long placementKey=chunkKey(chunkX,chunkZ)^((long)index*0x9E3779B97F4A7C15L);
            if(!processedStructureChunks.add(placementKey)) continue;
            StructureChoice choice=set.choose(random);
            WorldgenModule.Definition structure=WorldgenModule.find(definitions,"worldgen/structure",choice.structure);
            if(structure==null) throw new IllegalStateException("Missing worldgen structure " + choice.structure);
            net.minecraft.util.ResourceLocation templateId=resolveTemplateId(structure,random);
            Template template=structureTemplates.findTemplate(templateId);
            if(template==null) throw new IllegalStateException("Structure " + structure.id + " refers to unavailable native template " + templateId);
            int x=(chunkX<<4)+8,z=(chunkZ<<4)+8;
            BlockPos origin=world.getHeight(new BlockPos(x,0,z));
            WorldgenCodecs.TemplateProcessor processor=null;
            if(structure.json.has("processor_list")) {
                net.minecraft.util.ResourceLocation processorId=new net.minecraft.util.ResourceLocation(structure.json.get("processor_list").getAsString());
                processor=processorLists.get(processorId);
                if(processor==null) throw new IllegalStateException("Unknown processor_list " + processorId + " for " + structure.id);
            }
            PlacementSettings placement=new PlacementSettings();
            if(processor!=null) { origin=processor.transformOrigin(origin); placement.setRotation(processor.rotation()); }
            template.addBlocksToWorld(world,origin,processor,placement,2);
            BlockPos size=template.getSize();
            StructureBounds bounds=new StructureBounds(structure.id,origin.getX(),origin.getY(),origin.getZ(),origin.getX()+size.getX()-1,origin.getY()+size.getY()-1,origin.getZ()+size.getZ()-1);
            for(int bx=Math.floorDiv(bounds.minX,16);bx<=Math.floorDiv(bounds.maxX,16);bx++) for(int bz=Math.floorDiv(bounds.minZ,16);bz<=Math.floorDiv(bounds.maxZ,16);bz++) structureBounds.computeIfAbsent(chunkKey(bx,bz),key->new ArrayList<>()).add(bounds);
            generated=true;
        }
        return generated;
    }

    private net.minecraft.util.ResourceLocation resolveTemplateId(WorldgenModule.Definition structure,Random random) {
        if(structure.json.has("template")) return new net.minecraft.util.ResourceLocation(structure.json.get("template").getAsString());
        if(!structure.json.has("template_pool")) throw new IllegalStateException("Structure " + structure.id + " requires template or template_pool");
        net.minecraft.util.ResourceLocation poolId=new net.minecraft.util.ResourceLocation(structure.json.get("template_pool").getAsString());
        WorldgenModule.Definition pool=WorldgenModule.find(definitions,"worldgen/template_pool",poolId);
        if(pool==null||!"worldgen/template_pool".equals(pool.type)) throw new IllegalStateException("Unknown template_pool " + poolId);
        JsonArray elements=pool.json.getAsJsonArray("elements"); int total=0;
        for(JsonElement element:elements) total+=element.getAsJsonObject().has("weight")?element.getAsJsonObject().get("weight").getAsInt():1;
        if(total<=0) throw new IllegalStateException("Template pool " + poolId + " has no positive weights");
        int selected=random.nextInt(total);
        for(JsonElement element:elements) { JsonObject value=element.getAsJsonObject(); int weight=value.has("weight")?value.get("weight").getAsInt():1; if(selected<weight) return new net.minecraft.util.ResourceLocation(value.get("template").getAsString()); selected-=weight; }
        throw new IllegalStateException("Template pool selection failed for " + poolId);
    }

    private static List<StructureSet> compileStructureSets(JsonObject dimension,Map<String,WorldgenModule.Definition> definitions) {
        if(!dimension.has("structure_sets")) return Collections.emptyList();
        JsonArray references=dimension.getAsJsonArray("structure_sets"); List<StructureSet> compiled=new ArrayList<>(references.size());
        for(JsonElement reference:references) {
            net.minecraft.util.ResourceLocation id=new net.minecraft.util.ResourceLocation(reference.getAsString());
            WorldgenModule.Definition definition=WorldgenModule.find(definitions,"worldgen/structure_set",id);
            if(definition==null||!"worldgen/structure_set".equals(definition.type)) throw new IllegalArgumentException("Unknown structure_set " + id);
            JsonObject placement=definition.json.getAsJsonObject("placement");
            int spacing=placement.get("spacing").getAsInt(),separation=placement.get("separation").getAsInt(),salt=placement.get("salt").getAsInt();
            if(spacing<2||separation<1||separation>=spacing) throw new IllegalArgumentException("Invalid spacing/separation for structure_set " + id);
            JsonArray structures=definition.json.getAsJsonArray("structures"); List<StructureChoice> choices=new ArrayList<>(structures.size()); long total=0;
            for(JsonElement item:structures) {
                JsonObject value=item.getAsJsonObject(); net.minecraft.util.ResourceLocation structureId=new net.minecraft.util.ResourceLocation(value.get("structure").getAsString());
                int weight=value.has("weight")?value.get("weight").getAsInt():1;
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
    private static final class FlatLayer { final IBlockState state; final int height; FlatLayer(IBlockState state,int height){this.state=state;this.height=height;} }
    private static final class StructureBounds {
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
    private static final class MaterialContext implements WorldgenCodecs.MaterialContext {
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
    private enum Kind { ORE, DISK, SINGLE }
    private static final class Feature { final Kind kind; final WorldgenCodecs.BlockStateProvider provider; final int count,size,minY,maxY; Feature(Kind kind,WorldgenCodecs.BlockStateProvider provider,int count,int size,int minY,int maxY) { this.kind=kind;this.provider=provider;this.count=count;this.size=size;this.minY=minY;this.maxY=maxY; } }
    interface WorldServerAccess { World world(); }
}
