package rml.loader.deserialize;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.FloatProvider;
import rml.deserializer.value.IntProvider;
import rml.deserializer.value.LevelBasedValue;
import rml.deserializer.value.WeightedList;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Random;

/** Reusable distributions and recursive level values, independent of a particular content family. */
public final class NumericValueDecoders {
    private NumericValueDecoders() { }

    public static void register(DeserializerManager manager) {
        manager.addDefaultEntry(new AbstractDeserializer<>(id("constant"), IntProvider.class, json -> integerProvider(manager, json)));
        for (String name : new String[]{"uniform", "biased_to_bottom", "very_biased_to_bottom", "clamped", "clamped_normal", "trapezoid", "weighted_list"})
            manager.addEntry(new AbstractDeserializer<>(id(name), IntProvider.class, json -> integerProvider(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(id("constant"), FloatProvider.class, json -> floatProvider(manager, json)));
        for (String name : new String[]{"uniform", "clamped_normal", "trapezoid"})
            manager.addEntry(new AbstractDeserializer<>(id(name), FloatProvider.class, json -> floatProvider(manager, json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(id("constant"), LevelBasedValue.class, json -> levelValue(manager, json)));
        for (String name : new String[]{"linear", "levels_squared", "clamped", "fraction", "exponent", "lookup"})
            manager.addEntry(new AbstractDeserializer<>(id(name), LevelBasedValue.class, json -> levelValue(manager, json)));
    }

    private static IntProvider integerProvider(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        if (json.isJsonPrimitive()) return constant(integer(manager, json));
        JsonObject object = object(json);
        String type = type(object);
        if ("constant".equals(type)) {
            fields(object, "value");
            return constant(integer(manager, required(object, "value")));
        }
        if ("weighted_list".equals(type)) {
            fields(object, "distribution");
            WeightedList<IntProvider> distribution = manager.decode(new TypeToken<WeightedList<IntProvider>>() { }.getType(), required(object, "distribution"));
            int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
            for (int i = 0; i < distribution.size(); i++) {
                minimum = Math.min(minimum, distribution.get(i).getMinValue());
                maximum = Math.max(maximum, distribution.get(i).getMaxValue());
            }
            return new IntProvider(minimum, maximum, random -> distribution.sample(random).sample(random));
        }
        int minimum = integer(manager, required(object, "min_inclusive"));
        int maximum = integer(manager, required(object, "max_inclusive"));
        if (minimum > maximum) throw error(json, "Integer provider minimum exceeds maximum");
        final long width = (long) maximum - minimum;
        switch (type) {
            case "uniform":
                fields(object, "min_inclusive", "max_inclusive");
                return new IntProvider(minimum, maximum, random -> (int) (minimum + bounded(random, width + 1)));
            case "biased_to_bottom":
                fields(object, "min_inclusive", "max_inclusive");
                return new IntProvider(minimum, maximum, random -> (int) (minimum + bounded(random, bounded(random, width + 1) + 1)));
            case "very_biased_to_bottom":
                fields(object, "min_inclusive", "max_inclusive");
                return new IntProvider(minimum, maximum, random -> (int) (minimum + bounded(random, bounded(random, bounded(random, width + 1) + 1) + 1)));
            case "clamped": {
                fields(object, "source", "min_inclusive", "max_inclusive");
                IntProvider source = manager.decode(IntProvider.class, required(object, "source"));
                return new IntProvider(clamp(source.getMinValue(), minimum, maximum), clamp(source.getMaxValue(), minimum, maximum),
                        random -> clamp(source.sample(random), minimum, maximum));
            }
            case "clamped_normal": {
                fields(object, "mean", "deviation", "min_inclusive", "max_inclusive");
                float mean = number(manager, required(object, "mean"));
                float deviation = number(manager, required(object, "deviation"));
                if (deviation < 0) throw error(json, "Normal deviation must be nonnegative");
                return new IntProvider(minimum, maximum, random -> (int) Math.max(minimum, Math.min(maximum, mean + random.nextGaussian() * deviation)));
            }
            case "trapezoid": {
                fields(object, "min_inclusive", "max_inclusive", "plateau");
                int plateau = integer(manager, required(object, "plateau"));
                if (plateau < 0 || plateau > width) throw error(json, "Plateau must be within the provider range");
                if (width == plateau) return new IntProvider(minimum, maximum, random -> (int) (minimum + bounded(random, width + 1)));
                final long slope = (width - plateau) / 2;
                final long flat = width - slope;
                return new IntProvider(minimum, maximum, random -> (int) (minimum + bounded(random, flat + 1) + bounded(random, slope + 1)));
            }
            default: throw error(json, "Unknown integer provider " + type);
        }
    }

    private static FloatProvider floatProvider(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        if (json.isJsonPrimitive()) return constant(number(manager, json));
        JsonObject object = object(json);
        String type = type(object);
        switch (type) {
            case "constant":
                fields(object, "value");
                return constant(number(manager, required(object, "value")));
            case "uniform": {
                fields(object, "min_inclusive", "max_exclusive");
                float min = number(manager, required(object, "min_inclusive"));
                float max = number(manager, required(object, "max_exclusive"));
                if (!(max > min)) throw error(json, "Uniform float maximum must exceed minimum");
                double width = (double) max - min;
                return new FloatProvider(min, max, random -> Math.min(Math.nextDown(max), (float) (min + random.nextFloat() * width)));
            }
            case "clamped_normal": {
                fields(object, "mean", "deviation", "min", "max");
                float min = number(manager, required(object, "min"));
                float max = number(manager, required(object, "max"));
                float mean = number(manager, required(object, "mean"));
                float deviation = number(manager, required(object, "deviation"));
                if (deviation < 0) throw error(json, "Normal deviation must be nonnegative");
                return new FloatProvider(min, max, random -> (float) Math.max(min, Math.min(max, mean + random.nextGaussian() * deviation)));
            }
            case "trapezoid": {
                fields(object, "min", "max", "plateau");
                float min = number(manager, required(object, "min"));
                float max = number(manager, required(object, "max"));
                float plateau = number(manager, required(object, "plateau"));
                double width = (double) max - min;
                if (plateau < 0 || plateau > width) throw error(json, "Plateau must be within the provider range");
                double slope = (width - plateau) / 2;
                double flat = width - slope;
                return new FloatProvider(min, max, random -> (float) (min + random.nextFloat() * flat + random.nextFloat() * slope));
            }
            default: throw error(json, "Unknown float provider " + type);
        }
    }

    private static LevelBasedValue levelValue(DeserializerManager manager, JsonElement json) throws JsonDeserializeException {
        if (json.isJsonPrimitive()) {
            float value = number(manager, json);
            return level -> value;
        }
        JsonObject object = object(json);
        switch (type(object)) {
            case "constant": {
                fields(object, "value");
                float value = number(manager, required(object, "value"));
                return level -> value;
            }
            case "linear": {
                fields(object, "base", "per_level_above_first");
                float base = number(manager, required(object, "base"));
                float perLevel = number(manager, required(object, "per_level_above_first"));
                return level -> base + perLevel * (level - 1);
            }
            case "levels_squared": {
                fields(object, "added");
                float added = number(manager, required(object, "added"));
                return level -> (float) level * level + added;
            }
            case "clamped": {
                fields(object, "value", "min", "max");
                LevelBasedValue value = manager.decode(LevelBasedValue.class, required(object, "value"));
                float min = number(manager, required(object, "min"));
                float max = number(manager, required(object, "max"));
                if (min > max) throw error(json, "Level value minimum exceeds maximum");
                return level -> Math.max(min, Math.min(max, value.calculate(level)));
            }
            case "fraction": {
                fields(object, "numerator", "denominator");
                LevelBasedValue numerator = manager.decode(LevelBasedValue.class, required(object, "numerator"));
                LevelBasedValue denominator = manager.decode(LevelBasedValue.class, required(object, "denominator"));
                return level -> { float divisor = denominator.calculate(level); return divisor == 0 ? 0 : numerator.calculate(level) / divisor; };
            }
            case "exponent": {
                fields(object, "base", "power");
                LevelBasedValue base = manager.decode(LevelBasedValue.class, required(object, "base"));
                LevelBasedValue power = manager.decode(LevelBasedValue.class, required(object, "power"));
                return level -> (float) Math.pow(base.calculate(level), power.calculate(level));
            }
            case "lookup": {
                fields(object, "values", "fallback");
                JsonElement entries = required(object, "values");
                if (!entries.isJsonArray()) throw error(json, "Lookup values must be an array");
                float[] values = new float[entries.getAsJsonArray().size()];
                for (int i = 0; i < values.length; i++) values[i] = number(manager, entries.getAsJsonArray().get(i));
                LevelBasedValue fallback = manager.decode(LevelBasedValue.class, required(object, "fallback"));
                return level -> level > 0 && level <= values.length ? values[level - 1] : fallback.calculate(level);
            }
            default: throw error(json, "Unknown level value " + type(object));
        }
    }

    private static IntProvider constant(int value) { return new IntProvider(value, value, random -> value); }
    private static FloatProvider constant(float value) { return new FloatProvider(value, value, random -> value); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static long bounded(Random random, long bound) {
        if (bound <= Integer.MAX_VALUE) return random.nextInt((int) bound);
        long bits, value;
        do { bits = random.nextLong() >>> 1; value = bits % bound; } while (bits - value + bound - 1 < 0);
        return value;
    }

    private static ResourceLocation id(String name) { return new ResourceLocation("minecraft", name); }
    private static JsonObject object(JsonElement value) throws JsonDeserializeException {
        if (value == null || !value.isJsonObject()) throw error(value, "Expected a numeric provider object");
        return value.getAsJsonObject();
    }
    private static String type(JsonObject value) { return value.has("type") ? new ResourceLocation(value.get("type").getAsString()).getPath() : "constant"; }
    private static JsonElement required(JsonObject value, String key) throws JsonDeserializeException {
        JsonElement field = value.get(key);
        if (field == null || field.isJsonNull()) throw error(value, "Missing numeric provider field " + key);
        return field;
    }
    private static int integer(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonObject()) return manager.decode(Integer.class, value);
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new NumberFormatException();
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException error) { throw error(value, "Expected an exact 32-bit integer"); }
    }
    private static float number(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (!value.isJsonObject() && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())) throw error(value, "Expected a number");
        float result = manager.decode(Float.class, value);
        if (!Float.isFinite(result)) throw error(value, "Expected a finite float");
        return result;
    }
    private static void fields(JsonObject value, String... allowed) throws JsonDeserializeException {
        for (Map.Entry<String, JsonElement> entry : value.entrySet()) {
            if ("type".equals(entry.getKey())) continue;
            boolean found = false;
            for (String name : allowed) if (name.equals(entry.getKey())) { found = true; break; }
            if (!found) throw error(value, "Unsupported numeric provider field " + entry.getKey());
        }
    }
    private static JsonDeserializeException error(JsonElement value, String message) { return new JsonDeserializeException(value, message); }
}
