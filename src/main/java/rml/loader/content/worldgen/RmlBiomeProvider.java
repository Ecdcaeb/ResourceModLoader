package rml.loader.content.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import rml.loader.content.CompiledTagModule;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Seeded fixed or multi-noise biome source used by RML dimension providers. */
final class RmlBiomeProvider extends BiomeProvider {
    private final List<Entry> entries;
    private final List<Biome> spawnBiomes;
    private final long seed;

    RmlBiomeProvider(Biome fixed, long seed) {
        this.entries=Collections.singletonList(new Entry(fixed, -1,1,-1,1,-1,1,-1,1));
        this.spawnBiomes=Collections.singletonList(fixed);
        this.seed=seed;
    }

    RmlBiomeProvider(WorldgenModule.Definition parameterList, long seed, CompiledTagModule tags) {
        this.seed=seed;
        JsonArray source=parameterList.json.getAsJsonArray("biomes");
        List<Entry> parsed=new ArrayList<>();
        for(JsonElement element:source) {
            JsonObject value=element.getAsJsonObject();
            WorldgenNodes.fields(value, "biome", "tag", "parameters", "temperature", "humidity", "altitude", "weirdness");
            if (value.has("biome") == value.has("tag")) throw new IllegalArgumentException("Multi-noise entry requires exactly one biome or tag");
            if (value.has("parameters")) {
                WorldgenNodes.fields(value, "biome", "tag", "parameters");
                WorldgenNodes.fields(value.getAsJsonObject("parameters"), "temperature", "humidity", "altitude", "weirdness");
            }
            JsonObject parameters=value.has("parameters")?value.getAsJsonObject("parameters"):value;
            double[] temperature=range(parameters,"temperature"), humidity=range(parameters,"humidity"), altitude=range(parameters,"altitude"), weirdness=range(parameters,"weirdness");
            if(value.has("biome")) {
                ResourceLocation id=new ResourceLocation(value.get("biome").getAsString());
                Biome biome=Biome.REGISTRY.getObject(id);
                if(!Biome.REGISTRY.containsKey(id)||biome==null) throw new IllegalArgumentException("Unknown multi-noise biome " + id + " in " + parameterList.id);
                parsed.add(new Entry(biome,temperature[0],temperature[1],humidity[0],humidity[1],altitude[0],altitude[1],weirdness[0],weirdness[1]));
            } else if(value.has("tag")) {
                ResourceLocation tag=new ResourceLocation(value.get("tag").getAsString());
                for(Biome biome:Biome.REGISTRY) {
                    ResourceLocation id=Biome.REGISTRY.getNameForObject(biome);
                    if(id!=null && tags.contains("biome",tag,id)) parsed.add(new Entry(biome,temperature[0],temperature[1],humidity[0],humidity[1],altitude[0],altitude[1],weirdness[0],weirdness[1]));
                }
            } else throw new IllegalArgumentException("Multi-noise entry in " + parameterList.id + " requires biome or tag");
        }
        if(parsed.isEmpty()) throw new IllegalArgumentException("Multi-noise source " + parameterList.id + " selected no registered biomes");
        this.entries=Collections.unmodifiableList(parsed);
        List<Biome> spawns=new ArrayList<>(); for(Entry entry:parsed) if(!spawns.contains(entry.biome)) spawns.add(entry.biome);
        this.spawnBiomes=Collections.unmodifiableList(spawns);
    }

    @Override public Biome getBiome(BlockPos pos, @Nullable Biome defaultBiome) {
        return select(pos.getX(),pos.getZ());
    }
    @Override public Biome[] getBiomes(@Nullable Biome[] reuse,int x,int z,int width,int length,boolean cacheFlag) {
        int size=width*length; Biome[] output=reuse==null||reuse.length<size?new Biome[size]:reuse;
        for(int dz=0,index=0;dz<length;dz++) for(int dx=0;dx<width;dx++,index++) output[index]=select(x+dx,z+dz);
        return output;
    }
    @Override public Biome[] getBiomesForGeneration(@Nullable Biome[] reuse,int x,int z,int width,int length) {
        int size=width*length; Biome[] output=reuse==null||reuse.length<size?new Biome[size]:reuse;
        for(int dz=0,index=0;dz<length;dz++) for(int dx=0;dx<width;dx++,index++) output[index]=select((x+dx)<<2,(z+dz)<<2);
        return output;
    }
    @Override public List<Biome> getBiomesToSpawnIn() { return spawnBiomes; }
    @Override public boolean areBiomesViable(int x,int z,int radius,List<Biome> allowed) {
        for(int dz=-radius;dz<=radius;dz+=4) for(int dx=-radius;dx<=radius;dx+=4) if(!allowed.contains(select(x+dx,z+dz))) return false;
        return true;
    }
    @Nullable @Override public BlockPos findBiomePosition(int x,int z,int range,List<Biome> allowed,Random random) {
        BlockPos selected=null; int count=0;
        for(int dz=-range;dz<=range;dz+=4) for(int dx=-range;dx<=range;dx+=4) { int px=x+dx,pz=z+dz; if(allowed.contains(select(px,pz))&&random.nextInt(++count)==0) selected=new BlockPos(px,0,pz); }
        return selected;
    }
    private Biome select(int x,int z) {
        double temperature=noise(0,x,z),humidity=noise(1,x,z),altitude=noise(2,x,z),weirdness=noise(3,x,z);
        Entry nearest=entries.get(0); double best=Double.POSITIVE_INFINITY;
        for(Entry entry:entries) {
            double score=distance(temperature,entry.temperatureMin,entry.temperatureMax)+distance(humidity,entry.humidityMin,entry.humidityMax)+distance(altitude,entry.altitudeMin,entry.altitudeMax)+distance(weirdness,entry.weirdnessMin,entry.weirdnessMax);
            if(score<best) { best=score; nearest=entry; }
        }
        return nearest.biome;
    }
    private double noise(int channel,int x,int z) {
        long h=seed^((long)channel*0x9E3779B97F4A7C15L)^((long)(x>>5)*0xC2B2AE3D27D4EB4FL)^((long)(z>>5)*0x165667B19E3779F9L);
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L;h=(h^(h>>>27))*0x94D049BB133111EBL;h^=h>>>31;
        return ((h>>>11)*0x1.0p-52)-1.0D;
    }
    private static double distance(double value,double min,double max) { return value<min?min-value:value>max?value-max:0.0D; }
    private static double[] range(JsonObject object,String key) {
        if(!object.has(key)) return new double[]{-1,1};
        JsonElement value=object.get(key);
        if(value.isJsonArray()) { JsonArray array=value.getAsJsonArray(); if(array.size()!=2) throw new IllegalArgumentException(key+" range must have two values"); double a=array.get(0).getAsDouble(),b=array.get(1).getAsDouble(); if(!Double.isFinite(a)||!Double.isFinite(b)||a>b) throw new IllegalArgumentException(key+" requires finite ordered bounds"); return new double[]{a,b}; }
        double fixed=WorldgenNodes.finite(object,key); return new double[]{fixed,fixed};
    }
    private static final class Entry {
        final Biome biome; final double temperatureMin,temperatureMax,humidityMin,humidityMax,altitudeMin,altitudeMax,weirdnessMin,weirdnessMax;
        Entry(Biome biome,double t0,double t1,double h0,double h1,double a0,double a1,double w0,double w1) { this.biome=biome;temperatureMin=t0;temperatureMax=t1;humidityMin=h0;humidityMax=h1;altitudeMin=a0;altitudeMax=a1;weirdnessMin=w0;weirdnessMax=w1; }
    }
}
