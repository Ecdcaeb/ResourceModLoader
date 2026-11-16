package rml.loader.content.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.FloatProvider;
import rml.deserializer.value.IntProvider;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/** Ordered position streams shared by placed features and custom placement modifiers. */
public final class WorldgenPlacement {
    private WorldgenPlacement() { }
    @FunctionalInterface public interface Next { void place(Context context, int x, int y, int z); }
    @FunctionalInterface public interface Modifier { void apply(Context context, int x, int y, int z, Next next); }
    @FunctionalInterface public interface Filter { boolean test(Context context, int x, int y, int z); }
    public static final class Context {
        public final World world;
        public final Random random;
        public final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        private final Next sink;
        public Context(World world, Random random, Next sink) { this.world = world; this.random = random; this.sink = sink; }
    }

    static void register(DeserializerManager manager, Predicate<ResourceLocation> knownBiome) {
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("count"), Modifier.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type", "count");
            IntProvider count = manager.decode(IntProvider.class, json.getAsJsonObject().get("count"));
            if (count.getMinValue() < 0 || count.getMaxValue() > 65536) throw new IllegalArgumentException("Placement count must be 0..65536");
            return (context, x, y, z, next) -> { int n = count.sample(context.random); for (int i = 0; i < n; i++) next.place(context, x, y, z); };
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("rarity"), Modifier.class, json -> {
            JsonObject object = json.getAsJsonObject();
            WorldgenNodes.fields(object, "type", "once_every", "chance");
            if (object.has("once_every") == object.has("chance")) throw new IllegalArgumentException("Rarity requires exactly one once_every or chance");
            if (object.has("once_every")) {
                IntProvider rarity = manager.decode(IntProvider.class, object.get("once_every"));
                if (rarity.getMinValue() < 1) throw new IllegalArgumentException("once_every must be positive");
                return (context, x, y, z, next) -> { if (context.random.nextInt(rarity.sample(context.random)) == 0) next.place(context, x, y, z); };
            }
            FloatProvider chance = manager.decode(FloatProvider.class, object.get("chance"));
            if (chance.getMinValue() < 0 || chance.getMaxValue() > 1) throw new IllegalArgumentException("Placement chance must be 0..1");
            return (context, x, y, z, next) -> { if (context.random.nextFloat() < chance.sample(context.random)) next.place(context, x, y, z); };
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("height"), Modifier.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type", "height");
            IntProvider height = height(manager, json.getAsJsonObject().get("height"));
            return (context, x, y, z, next) -> next.place(context, x, height.sample(context.random), z);
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("in_square"), Modifier.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type");
            return (context, x, y, z, next) -> next.place(context, x + context.random.nextInt(16), y, z + context.random.nextInt(16));
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("surface_height"), Modifier.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type");
            return (context, x, y, z, next) -> { int height = context.world.getHeight(context.position.setPos(x, 0, z)).getY(); if (height <= 255) next.place(context, x, height, z); };
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("filter"), Modifier.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type", "filter");
            Filter filter = manager.decode(Filter.class, json.getAsJsonObject().get("filter"));
            return (context, x, y, z, next) -> { if (filter.test(context, x, y, z)) next.place(context, x, y, z); };
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("biome"), Modifier.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type", "biomes");
            Filter filter = biomes(json.getAsJsonObject().getAsJsonArray("biomes"), knownBiome);
            return (context, x, y, z, next) -> { if (filter.test(context, x, y, z)) next.place(context, x, y, z); };
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("filter_biome"), Filter.class, json -> { WorldgenNodes.fields(json.getAsJsonObject(), "type", "biomes"); return biomes(json.getAsJsonObject().getAsJsonArray("biomes"), knownBiome); }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("filter_air"), Filter.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type");
            return (context, x, y, z) -> y >= 0 && y <= 255 && context.world.isAirBlock(context.position.setPos(x, y, z));
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("filter_block"), Filter.class, json -> {
            JsonObject object = json.getAsJsonObject();
            WorldgenNodes.fields(object, "type", "state", "y_offset");
            IBlockState state = manager.decode(IBlockState.class, object.get("state"));
            int offset = object.has("y_offset") ? object.get("y_offset").getAsBigDecimal().intValueExact() : 0;
            return (context, x, y, z) -> (long)y + offset >= 0 && (long)y + offset <= 255 && context.world.getBlockState(context.position.setPos(x, y + offset, z)).equals(state);
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("filter_not"), Filter.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type", "filter");
            Filter child = manager.decode(Filter.class, json.getAsJsonObject().get("filter"));
            return (context, x, y, z) -> !child.test(context, x, y, z);
        }));
        for (String operation : new String[]{"all", "any"}) manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("filter_" + operation), Filter.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type", "filters");
            JsonArray array = json.getAsJsonObject().getAsJsonArray("filters");
            if (array.size() == 0) throw new IllegalArgumentException("Filter composition must not be empty");
            Filter[] filters = new Filter[array.size()];
            for (int i = 0; i < filters.length; i++) filters[i] = manager.decode(Filter.class, array.get(i));
            boolean all = "all".equals(operation);
            return (context, x, y, z) -> { for (Filter filter : filters) if (filter.test(context, x, y, z) != all) return !all; return all; };
        }));
    }

    static IntProvider height(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        IntProvider height = manager.decode(IntProvider.class, json);
        if (height.getMinValue() < 0 || height.getMaxValue() > 255) throw new IllegalArgumentException("Height provider must remain within 0..255");
        return height;
    }
    static Next compile(DeserializerManager manager, JsonArray modifiers) throws JsonDeserializeException {
        Next result = (context, x, y, z) -> { if (y >= 0 && y <= 255) context.sink.place(context, x, y, z); };
        for (int i = modifiers.size() - 1; i >= 0; i--) {
            Modifier modifier = manager.decode(Modifier.class, modifiers.get(i));
            Next next = result;
            result = (context, x, y, z) -> modifier.apply(context, x, y, z, next);
        }
        return result;
    }
    private static Filter biomes(JsonArray array, Predicate<ResourceLocation> knownBiome) {
        if (array == null || array.size() == 0) throw new IllegalArgumentException("Biome filter requires nonempty biomes");
        Set<ResourceLocation> ids = new HashSet<>();
        for (JsonElement element : array) {
            ResourceLocation id = new ResourceLocation(element.getAsString());
            if (!knownBiome.test(id)) throw new IllegalArgumentException("Unknown placement biome " + id);
            ids.add(id);
        }
        return (context, x, y, z) -> ids.contains(Biome.REGISTRY.getNameForObject(context.world.getBiome(context.position.setPos(x, y, z))));
    }
}
