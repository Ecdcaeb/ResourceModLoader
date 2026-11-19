package rml.loader.deserialize;

import com.google.common.base.Optional;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.WorldServer;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.DoubleRange;
import rml.deserializer.value.IntRange;
import rml.deserializer.value.WeightedList;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.data.DataContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Native values shared by every registry codec. Nested values use the caller's registry. */
public final class NativeTypeDecoders {
    private NativeTypeDecoders() { }

    @FunctionalInterface public interface ItemMatcher { boolean test(ItemStack stack, DataContext context); }
    @FunctionalInterface public interface BlockMatcher { boolean test(WorldServer world, BlockPos position, IBlockState state); }
    @FunctionalInterface public interface EntityMatcher { boolean test(Entity entity, DataContext context); }

    public static void register(DeserializerManager manager, CompiledTagModule tags) {
        TextDecoders.register(manager);
        register(manager, IntRange.class, "int_range", value -> intRange(manager, value));
        register(manager, DoubleRange.class, "double_range", value -> doubleRange(manager, value));
        register(manager, Vec3d.class, "vec3", value -> vector(manager, value));
        register(manager, Vec2f.class, "vec2", value -> {
            double[] parts = components(manager, value, new String[]{"x", "y"});
            if (Math.abs(parts[0]) > Float.MAX_VALUE || Math.abs(parts[1]) > Float.MAX_VALUE) throw error(value, "Vector exceeds float range");
            return new Vec2f((float) parts[0], (float) parts[1]);
        });
        register(manager, BlockPos.class, "block_pos", value -> {
            JsonElement[] parts = elements(value, new String[]{"x", "y", "z"});
            return new BlockPos(integer(manager, parts[0]), integer(manager, parts[1]), integer(manager, parts[2]));
        });
        register(manager, Vec3i.class, "vec3i", value -> {
            JsonElement[] parts = elements(value, new String[]{"x", "y", "z"});
            return new Vec3i(integer(manager, parts[0]), integer(manager, parts[1]), integer(manager, parts[2]));
        });
        register(manager, AxisAlignedBB.class, "box", value -> {
            JsonObject object = object(value);
            Vec3d min = manager.decode(Vec3d.class, required(object, "min"));
            Vec3d max = manager.decode(Vec3d.class, required(object, "max"));
            if (min.x > max.x || min.y > max.y || min.z > max.z) throw error(value, "Box minimum exceeds maximum");
            return new AxisAlignedBB(min, max);
        });
        register(manager, UUID.class, "uuid", value -> UUID.fromString(manager.decode(String.class, payload(value))));
        register(manager, IBlockState.class, "block_state", value -> blockState(manager, value));
        register(manager, EnumFacing.class, "direction", value -> direction(manager, value));
        register(manager, EnumFacing.Axis.class, "axis", value -> axis(manager, value));
        manager.addEntry(new AbstractDeserializer<>(id("item_stack"), ItemStack.class, value -> itemStack(manager, value)));
        manager.addGenericEntry(WeightedList.class, (registry, arguments, value) -> {
            if (arguments.length != 1) throw error(value, "WeightedList requires one element type");
            JsonElement entries = value.isJsonArray() ? value : required(object(value), "entries");
            if (!entries.isJsonArray()) throw error(value, "Weighted entries must be an array");
            List<Object> values = new ArrayList<>(entries.getAsJsonArray().size());
            long[] weights = new long[entries.getAsJsonArray().size()];
            int index = 0;
            for (JsonElement entry : entries.getAsJsonArray()) {
                JsonObject weighted = object(entry);
                values.add(registry.decode(arguments[0], required(weighted, "data")));
                weights[index++] = weighted.has("weight") ? exactLong(registry, weighted.get("weight")) : 1;
            }
            return new WeightedList<>(values, weights);
        });
        NbtDecoders.register(manager);
        MatcherDecoders.register(manager, tags);
        NumericValueDecoders.register(manager);
        register(manager, rml.loader.runtime.function.FunctionDataPath.Compiled.class, "nbt_path",
                value -> rml.loader.runtime.function.FunctionDataPath.compile(manager.decode(String.class, payload(value))));
    }

    private static <T> void register(DeserializerManager manager, Class<T> type, String name, AbstractDeserializer.IDeserializer<T> decoder) {
        manager.addDefaultEntry(new AbstractDeserializer<>(id(name), type, decoder));
    }

    public static AbstractDeserializer<net.minecraft.util.text.ITextComponent> registerText(DeserializerManager manager) {
        return TextDecoders.register(manager);
    }

    private static ResourceLocation id(String name) { return new ResourceLocation("rml", name); }

    private static IntRange intRange(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.size() != 2) throw error(value, "Integer range array must be [min, max]");
            return new IntRange(integer(manager, array.get(0)), integer(manager, array.get(1)));
        }
        if (!value.isJsonObject()) {
            int exact = integer(manager, value);
            return new IntRange(exact, exact);
        }
        JsonObject object = value.getAsJsonObject();
        return new IntRange(object.has("min") ? integer(manager, object.get("min")) : Integer.MIN_VALUE,
                object.has("max") ? integer(manager, object.get("max")) : Integer.MAX_VALUE);
    }

    private static DoubleRange doubleRange(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.size() != 2) throw error(value, "Double range array must be [min, max]");
            return new DoubleRange(finite(manager, array.get(0)), finite(manager, array.get(1)));
        }
        if (!value.isJsonObject()) {
            double exact = finite(manager, value);
            return new DoubleRange(exact, exact);
        }
        JsonObject object = value.getAsJsonObject();
        return new DoubleRange(object.has("min") ? finite(manager, object.get("min")) : Double.NEGATIVE_INFINITY,
                object.has("max") ? finite(manager, object.get("max")) : Double.POSITIVE_INFINITY);
    }

    private static Vec3d vector(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        double[] parts = components(manager, value, new String[]{"x", "y", "z"});
        return new Vec3d(parts[0], parts[1], parts[2]);
    }

    private static double[] components(DeserializerManager manager, JsonElement value, String[] fields) throws JsonDeserializeException {
        JsonElement[] values = elements(value, fields);
        double[] result = new double[fields.length];
        for (int index = 0; index < fields.length; index++) result[index] = finite(manager, values[index]);
        return result;
    }

    private static JsonElement[] elements(JsonElement value, String[] fields) throws JsonDeserializeException {
        JsonElement[] result = new JsonElement[fields.length];
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.size() != fields.length) throw error(value, "Expected " + fields.length + " coordinates");
            for (int index = 0; index < result.length; index++) result[index] = array.get(index);
        } else {
            JsonObject object = object(value);
            for (int index = 0; index < result.length; index++) result[index] = required(object, fields[index]);
        }
        return result;
    }

    private static IBlockState blockState(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        JsonObject properties = null;
        JsonElement name = value;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            name = required(object, "Name");
            if (object.has("Properties")) properties = object(object.get("Properties"));
        }
        ResourceLocation key = manager.decode(ResourceLocation.class, name);
        if (!Block.REGISTRY.containsKey(key)) throw error(value, "Unknown block: " + key);
        Block block = Block.REGISTRY.getObject(key);
        IBlockState state = block.getDefaultState();
        if (properties != null) {
            for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                IProperty<?> property = block.getBlockState().getProperty(entry.getKey());
                if (property == null) throw error(value, "Unknown property " + entry.getKey() + " on " + key);
                state = property(state, property, manager.decode(String.class, entry.getValue()), value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> IBlockState property(IBlockState state, IProperty<T> property, String name, JsonElement source) throws JsonDeserializeException {
        Optional<T> value = property.parseValue(name);
        if (!value.isPresent()) throw error(source, "Invalid " + property.getName() + " value: " + name);
        return state.withProperty(property, value.get());
    }

    private static EnumFacing direction(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonObject() && value.getAsJsonObject().has("value")) {
            return direction(manager, required(value.getAsJsonObject(), "value"));
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            EnumFacing facing = EnumFacing.byName(value.getAsString().toLowerCase(java.util.Locale.ROOT));
            if (facing == null) throw error(value, "Unknown direction");
            return facing;
        }
        JsonElement[] parts = elements(value, new String[]{"x", "y", "z"});
        int x = integer(manager, parts[0]), y = integer(manager, parts[1]), z = integer(manager, parts[2]);
        int axes = (x == 0 ? 0 : 1) + (y == 0 ? 0 : 1) + (z == 0 ? 0 : 1);
        if (axes != 1 || Math.abs(x) > 1 || Math.abs(y) > 1 || Math.abs(z) > 1) {
            throw error(value, "Direction must name one axis step");
        }
        return EnumFacing.getFacingFromVector(x, y, z);
    }

    private static EnumFacing.Axis axis(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonObject() && value.getAsJsonObject().has("value")) {
            return axis(manager, required(value.getAsJsonObject(), "value"));
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            EnumFacing.Axis axis = EnumFacing.Axis.byName(value.getAsString().toLowerCase(java.util.Locale.ROOT));
            if (axis == null) throw error(value, "Unknown axis");
            return axis;
        }
        return direction(manager, value).getAxis();
    }

    private static ItemStack itemStack(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonObject() && value.getAsJsonObject().has("value")) {
            return itemStack(manager, required(value.getAsJsonObject(), "value"));
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return new ItemStack(manager.decode(net.minecraft.item.Item.class, value));
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.size() < 1 || array.size() > 2) throw error(value, "Item stack array must be [item] or [item, count]");
            int count = array.size() == 2 ? integer(manager, array.get(1)) : 1;
            if (count < 1) throw error(value, "Item stack count must be positive");
            return new ItemStack(manager.decode(net.minecraft.item.Item.class, array.get(0)), count);
        }
        if (value.isJsonObject()) {
            JsonObject payload = new JsonObject();
            for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
                if (!"type".equals(field.getKey())) payload.add(field.getKey(), field.getValue());
            }
            return manager.decode(ItemStack.class, payload);
        }
        throw error(value, "Expected an item stack");
    }

    static int integer(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsBigDecimal().intValueExact();
        return manager.decode(Integer.class, value);
    }

    static int boundedInteger(DeserializerManager manager, JsonElement value, int min, int max) throws JsonDeserializeException {
        int result = integer(manager, value);
        if (result < min || result > max) throw error(value, "Integer is outside [" + min + "," + max + "]");
        return result;
    }

    static long exactLong(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsBigDecimal().longValueExact();
        return manager.decode(Long.class, value);
    }

    static double finite(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        double result = manager.decode(Double.class, value);
        if (!Double.isFinite(result)) throw error(value, "Expected a finite number");
        return result;
    }

    private static JsonElement payload(JsonElement value) throws JsonDeserializeException {
        return value.isJsonObject() ? required(value.getAsJsonObject(), "value") : value;
    }

    static JsonObject object(JsonElement value) throws JsonDeserializeException {
        if (value == null || !value.isJsonObject()) throw error(value, "Expected an object");
        return value.getAsJsonObject();
    }

    static JsonElement required(JsonObject object, String name) throws JsonDeserializeException {
        JsonElement result = object.get(name);
        if (result == null || result.isJsonNull()) throw error(object, "Missing field " + name);
        return result;
    }

    static JsonDeserializeException error(JsonElement value, String message) { return new JsonDeserializeException(value, message); }
}
