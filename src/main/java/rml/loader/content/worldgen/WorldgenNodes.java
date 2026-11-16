package rml.loader.content.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.FloatProvider;
import rml.deserializer.value.IntProvider;

import java.util.Map;
import java.util.function.Function;

/** Compiles recursive worldgen operators; all child dispatch stays on the shared manager. */
final class WorldgenNodes {
    private WorldgenNodes() { }

    static void register(DeserializerManager manager, Function<ResourceLocation, WorldgenCodecs.DensityFunction> densities) {
        manager.addEntry(new AbstractDeserializer<>(id("density_reference"), WorldgenCodecs.DensityFunction.class, json -> {
            fields(json.getAsJsonObject(), "type", "name");
            ResourceLocation name = new ResourceLocation(json.getAsJsonObject().get("name").getAsString());
            WorldgenCodecs.DensityFunction result = densities.apply(name);
            if (result == null) throw new IllegalArgumentException("Unknown density_function " + name);
            return result;
        }));
        manager.addEntry(new AbstractDeserializer<>(id("density_coordinate"), WorldgenCodecs.DensityFunction.class, json -> {
            JsonObject object = json.getAsJsonObject(); fields(object, "type", "axis");
            switch (object.get("axis").getAsString()) {
                case "x": return (seed, x, y, z) -> x;
                case "y": return (seed, x, y, z) -> y;
                case "z": return (seed, x, y, z) -> z;
                default: throw new IllegalArgumentException("Density coordinate axis must be x, y, or z");
            }
        }));
        for (String operation : new String[]{"min", "max", "subtract", "divide"}) {
            manager.addEntry(new AbstractDeserializer<>(id("density_" + operation), WorldgenCodecs.DensityFunction.class, json -> {
                JsonObject object = json.getAsJsonObject();
                fields(object, "type", "left", "right");
                WorldgenCodecs.DensityFunction left = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("left"));
                WorldgenCodecs.DensityFunction right = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("right"));
                switch (operation) {
                    case "min": return (seed, x, y, z) -> Math.min(left.sample(seed, x, y, z), right.sample(seed, x, y, z));
                    case "max": return (seed, x, y, z) -> Math.max(left.sample(seed, x, y, z), right.sample(seed, x, y, z));
                    case "subtract": return (seed, x, y, z) -> left.sample(seed, x, y, z) - right.sample(seed, x, y, z);
                    default: return (seed, x, y, z) -> left.sample(seed, x, y, z) / right.sample(seed, x, y, z);
                }
            }));
        }
        for (String operation : new String[]{"abs", "square", "cube", "negate", "half_negative", "quarter_negative", "squeeze"}) {
            manager.addEntry(new AbstractDeserializer<>(id("density_" + operation), WorldgenCodecs.DensityFunction.class, json -> {
                fields(json.getAsJsonObject(), "type", "input");
                WorldgenCodecs.DensityFunction input = manager.decode(WorldgenCodecs.DensityFunction.class, json.getAsJsonObject().get("input"));
                java.util.function.DoubleUnaryOperator operator;
                switch (operation) {
                    case "abs": operator = Math::abs; break;
                    case "square": operator = value -> value * value; break;
                    case "cube": operator = value -> value * value * value; break;
                    case "negate": operator = value -> -value; break;
                    case "half_negative": operator = value -> value < 0 ? value * 0.5 : value; break;
                    case "quarter_negative": operator = value -> value < 0 ? value * 0.25 : value; break;
                    default: operator = value -> { double clamped = Math.max(-1, Math.min(1, value)); return clamped / 2 - clamped * clamped * clamped / 24; };
                }
                return (seed, x, y, z) -> operator.applyAsDouble(input.sample(seed, x, y, z));
            }));
        }
        manager.addEntry(new AbstractDeserializer<>(id("density_clamp"), WorldgenCodecs.DensityFunction.class, json -> {
            JsonObject object = json.getAsJsonObject();
            fields(object, "type", "input", "min", "max");
            WorldgenCodecs.DensityFunction input = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("input"));
            double min = finite(object, "min"), max = finite(object, "max");
            if (min > max) throw new IllegalArgumentException("density_clamp min exceeds max");
            return (seed, x, y, z) -> Math.max(min, Math.min(max, input.sample(seed, x, y, z)));
        }));
        manager.addEntry(new AbstractDeserializer<>(id("density_range"), WorldgenCodecs.DensityFunction.class, json -> {
            JsonObject object = json.getAsJsonObject();
            fields(object, "type", "input", "in_range", "out_of_range", "min_inclusive", "max_exclusive");
            WorldgenCodecs.DensityFunction input = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("input"));
            WorldgenCodecs.DensityFunction inside = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("in_range"));
            WorldgenCodecs.DensityFunction outside = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("out_of_range"));
            double min = finite(object, "min_inclusive"), max = finite(object, "max_exclusive");
            if (min >= max) throw new IllegalArgumentException("density_range must have positive width");
            return (seed, x, y, z) -> { double value = input.sample(seed, x, y, z); return (value >= min && value < max ? inside : outside).sample(seed, x, y, z); };
        }));
        manager.addEntry(new AbstractDeserializer<>(id("density_shift"), WorldgenCodecs.DensityFunction.class, json -> {
            JsonObject object = json.getAsJsonObject();
            fields(object, "type", "input", "x", "y", "z");
            WorldgenCodecs.DensityFunction input = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("input"));
            WorldgenCodecs.DensityFunction dx = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("x"));
            WorldgenCodecs.DensityFunction dy = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("y"));
            WorldgenCodecs.DensityFunction dz = manager.decode(WorldgenCodecs.DensityFunction.class, object.get("z"));
            return (seed, x, y, z) -> shifted(input, seed, x + dx.sample(seed, x, y, z), y + dy.sample(seed, x, y, z), z + dz.sample(seed, x, y, z));
        }));
        manager.addEntry(new AbstractDeserializer<>(id("rotated_state"), WorldgenCodecs.BlockStateProvider.class, json -> {
            JsonObject object = json.getAsJsonObject();
            fields(object, "type", "source", "rotation");
            WorldgenCodecs.BlockStateProvider source = manager.decode(WorldgenCodecs.BlockStateProvider.class, object.get("source"));
            Rotation[] rotations = Rotation.values();
            IntProvider rotation = object.has("rotation") ? manager.decode(IntProvider.class, object.get("rotation")) : new IntProvider(0, 3, random -> random.nextInt(4));
            if (rotation.getMinValue() < 0 || rotation.getMaxValue() > 3) throw new IllegalArgumentException("Rotation index must be 0..3");
            return (random, pos) -> source.provide(random, pos).withRotation(rotations[rotation.sample(random)]);
        }));
        manager.addEntry(new AbstractDeserializer<>(id("property_state"), WorldgenCodecs.BlockStateProvider.class, json -> {
            JsonObject object = json.getAsJsonObject();
            fields(object, "type", "state", "property", "value");
            IBlockState state = manager.decode(IBlockState.class, object.get("state"));
            String name = object.get("property").getAsString();
            IProperty<?> property = state.getBlock().getBlockState().getProperty(name);
            if (property == null) throw new IllegalArgumentException("Unknown state property " + name);
            IntProvider value = manager.decode(IntProvider.class, object.get("value"));
            long size = (long)value.getMaxValue() - value.getMinValue() + 1;
            if (size > property.getAllowedValues().size()) throw new IllegalArgumentException("Property provider exceeds allowed values for " + name);
            IBlockState[] states = new IBlockState[(int)size];
            for (int i = 0; i < states.length; i++) states[i] = property(state, property, Integer.toString(value.getMinValue() + i));
            return (random, pos) -> states[value.sample(random) - value.getMinValue()];
        }));
        manager.addEntry(new AbstractDeserializer<>(id("noise_state"), WorldgenCodecs.BlockStateProvider.class, json -> {
            JsonObject object = json.getAsJsonObject();
            fields(object, "type", "noise", "states", "scale", "seed");
            WorldgenCodecs.Noise noise = manager.decode(WorldgenCodecs.Noise.class, object.get("noise"));
            WorldgenCodecs.BlockStateProvider[] states = children(manager, object.getAsJsonArray("states"), WorldgenCodecs.BlockStateProvider.class, WorldgenCodecs.BlockStateProvider[]::new);
            double scale = finite(object, "scale");
            if (scale <= 0) throw new IllegalArgumentException("Noise state scale must be positive");
            long seed = object.has("seed") ? object.get("seed").getAsLong() : 0;
            return (random, pos) -> {
                double value = noise.sample(seed, pos.getX() * scale, pos.getY() * scale, pos.getZ() * scale);
                int selected = Math.max(0, Math.min(states.length - 1, (int)Math.floor((value + 1) * 0.5 * states.length)));
                return states[selected].provide(random, pos);
            };
        }));
        manager.addEntry(new AbstractDeserializer<>(id("material_sequence"), WorldgenCodecs.MaterialRule.class, json -> {
            fields(json.getAsJsonObject(), "type", "rules");
            WorldgenCodecs.MaterialRule[] rules = children(manager, json.getAsJsonObject().getAsJsonArray("rules"), WorldgenCodecs.MaterialRule.class, WorldgenCodecs.MaterialRule[]::new);
            return context -> { for (WorldgenCodecs.MaterialRule rule : rules) { IBlockState result = rule.apply(context); if (result != null) return result; } return null; };
        }));
        for (String operation : new String[]{"all", "any"}) {
            manager.addEntry(new AbstractDeserializer<>(id("condition_" + operation), WorldgenCodecs.MaterialCondition.class, json -> {
                fields(json.getAsJsonObject(), "type", "conditions");
                WorldgenCodecs.MaterialCondition[] tests = children(manager, json.getAsJsonObject().getAsJsonArray("conditions"), WorldgenCodecs.MaterialCondition.class, WorldgenCodecs.MaterialCondition[]::new);
                boolean all = "all".equals(operation);
                return context -> { for (WorldgenCodecs.MaterialCondition test : tests) if (test.test(context) != all) return !all; return all; };
            }));
        }
        manager.addEntry(new AbstractDeserializer<>(id("condition_not"), WorldgenCodecs.MaterialCondition.class, json -> {
            fields(json.getAsJsonObject(), "type", "condition");
            WorldgenCodecs.MaterialCondition test = manager.decode(WorldgenCodecs.MaterialCondition.class, json.getAsJsonObject().get("condition"));
            return context -> !test.test(context);
        }));
        manager.addEntry(new AbstractDeserializer<>(id("condition_random"), WorldgenCodecs.MaterialCondition.class, json -> {
            fields(json.getAsJsonObject(), "type", "chance");
            FloatProvider chance = manager.decode(FloatProvider.class, json.getAsJsonObject().get("chance"));
            if (chance.getMinValue() < 0 || chance.getMaxValue() > 1) throw new IllegalArgumentException("Chance must be within 0..1");
            return context -> context.random().nextFloat() < chance.sample(context.random());
        }));
        manager.addEntry(new AbstractDeserializer<>(id("condition_block"), WorldgenCodecs.MaterialCondition.class, json -> {
            fields(json.getAsJsonObject(), "type", "state");
            IBlockState state = manager.decode(IBlockState.class, json.getAsJsonObject().get("state"));
            return context -> context.current().equals(state);
        }));
    }
    private static double shifted(WorldgenCodecs.DensityFunction input, long seed, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || x < Integer.MIN_VALUE || x >= Integer.MAX_VALUE || y < Integer.MIN_VALUE || y >= Integer.MAX_VALUE || z < Integer.MIN_VALUE || z >= Integer.MAX_VALUE)
            throw new IllegalStateException("Shifted density coordinates exceed the integer sampling domain");
        int ix = (int)Math.floor(x), iy = (int)Math.floor(y), iz = (int)Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        if (fx == 0 && fy == 0 && fz == 0) return input.sample(seed, ix, iy, iz);
        double bottom = mix(mix(input.sample(seed, ix, iy, iz), input.sample(seed, ix + 1, iy, iz), fx),
                mix(input.sample(seed, ix, iy, iz + 1), input.sample(seed, ix + 1, iy, iz + 1), fx), fz);
        double top = mix(mix(input.sample(seed, ix, iy + 1, iz), input.sample(seed, ix + 1, iy + 1, iz), fx),
                mix(input.sample(seed, ix, iy + 1, iz + 1), input.sample(seed, ix + 1, iy + 1, iz + 1), fx), fz);
        return mix(bottom, top, fy);
    }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }

    static ResourceLocation id(String path) { return new ResourceLocation("rml", path); }
    static double finite(JsonObject object, String key) {
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }
    static void fields(JsonObject object, String... allowed) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            boolean found = false;
            for (String key : allowed) if (key.equals(entry.getKey())) { found = true; break; }
            if (!found) throw new IllegalArgumentException("Unsupported field " + entry.getKey());
        }
    }
    private static <T> T[] children(DeserializerManager manager, JsonArray array, Class<T> type, java.util.function.IntFunction<T[]> factory) throws JsonDeserializeException {
        if (array == null || array.size() == 0) throw new IllegalArgumentException("Expected nonempty child array");
        T[] result = factory.apply(array.size());
        for (int i = 0; i < result.length; i++) result[i] = manager.decode(type, array.get(i));
        return result;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState property(IBlockState state, IProperty property, String value) {
        com.google.common.base.Optional parsed = property.parseValue(value);
        if (!parsed.isPresent()) throw new IllegalArgumentException("Invalid property value " + value);
        return state.withProperty(property, (Comparable)parsed.get());
    }
}
