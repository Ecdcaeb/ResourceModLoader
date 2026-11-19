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
        registerText(manager);
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
        register(manager, NBTBase.class, "nbt", value -> nbt(manager, value));
        nbtType(manager, "byte", value -> new NBTTagByte((byte) boundedInteger(manager, value, Byte.MIN_VALUE, Byte.MAX_VALUE)));
        nbtType(manager, "short", value -> new NBTTagShort((short) boundedInteger(manager, value, Short.MIN_VALUE, Short.MAX_VALUE)));
        nbtType(manager, "int", value -> new NBTTagInt(integer(manager, value)));
        nbtType(manager, "long", value -> new NBTTagLong(exactLong(manager, value)));
        nbtType(manager, "float", value -> {
            float number = manager.decode(Float.class, value);
            if (!Float.isFinite(number)) throw error(value, "Expected a finite float");
            return new NBTTagFloat(number);
        });
        nbtType(manager, "double", value -> new NBTTagDouble(finite(manager, value)));
        nbtType(manager, "string", value -> new NBTTagString(manager.decode(String.class, value)));
        nbtType(manager, "byte_array", value -> new NBTTagByteArray(manager.decode(byte[].class, value)));
        nbtType(manager, "int_array", value -> new NBTTagIntArray(manager.decode(int[].class, value)));
        nbtType(manager, "long_array", value -> new NBTTagLongArray(manager.decode(long[].class, value)));
        nbtType(manager, "list", value -> nbtList(manager, value));
        nbtType(manager, "compound", value -> nbtCompound(manager, object(value)));
        MatcherDecoders.register(manager, tags);
        NumericValueDecoders.register(manager);
        register(manager, rml.loader.runtime.function.FunctionDataPath.Compiled.class, "nbt_path",
                value -> rml.loader.runtime.function.FunctionDataPath.compile(manager.decode(String.class, payload(value))));
    }

    private static <T> void register(DeserializerManager manager, Class<T> type, String name, AbstractDeserializer.IDeserializer<T> decoder) {
        manager.addDefaultEntry(new AbstractDeserializer<>(id(name), type, decoder));
    }

    public static AbstractDeserializer<net.minecraft.util.text.ITextComponent> registerText(DeserializerManager manager) {
        return manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "textcomponent"),
                net.minecraft.util.text.ITextComponent.class, value -> text(manager, value)));
    }

    private static net.minecraft.util.text.ITextComponent text(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonArray()) {
            net.minecraft.util.text.ITextComponent result = null;
            for (JsonElement child : value.getAsJsonArray()) {
                net.minecraft.util.text.ITextComponent component = manager.decode(net.minecraft.util.text.ITextComponent.class, child);
                if (result == null) result = component; else result.appendSibling(component);
            }
            if (result == null) throw error(value, "Text component array must not be empty");
            return result;
        }
        JsonElement normalized = value;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject(), result = new JsonObject();
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                JsonElement child = field.getValue();
                if ("extra".equals(field.getKey()) || "with".equals(field.getKey())) {
                    if (!child.isJsonArray()) throw error(value, "Text children must be an array");
                    JsonArray children = new JsonArray();
                    for (JsonElement entry : child.getAsJsonArray()) children.add(textJson(manager, entry));
                    child = children;
                } else if ("hoverEvent".equals(field.getKey()) && child.isJsonObject()) {
                    JsonObject hover = new JsonObject();
                    for (Map.Entry<String, JsonElement> entry : child.getAsJsonObject().entrySet())
                        hover.add(entry.getKey(), "value".equals(entry.getKey()) ? textJson(manager, entry.getValue()) : entry.getValue());
                    child = hover;
                }
                result.add(field.getKey(), child);
            }
            normalized = result;
        }
        net.minecraft.util.text.ITextComponent result = net.minecraft.util.text.ITextComponent.Serializer.jsonToComponent(normalized.toString());
        if (result == null) throw error(value, "Text component must not be null");
        return result;
    }

    private static JsonElement textJson(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        return new com.google.gson.JsonParser().parse(net.minecraft.util.text.ITextComponent.Serializer.componentToJson(
                manager.decode(net.minecraft.util.text.ITextComponent.class, value)));
    }

    private static ResourceLocation id(String name) { return new ResourceLocation("rml", name); }

    private static IntRange intRange(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (!value.isJsonObject()) {
            int exact = integer(manager, value);
            return new IntRange(exact, exact);
        }
        JsonObject object = value.getAsJsonObject();
        return new IntRange(object.has("min") ? integer(manager, object.get("min")) : Integer.MIN_VALUE,
                object.has("max") ? integer(manager, object.get("max")) : Integer.MAX_VALUE);
    }

    private static DoubleRange doubleRange(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
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

    private static void nbtType(DeserializerManager manager, String name, AbstractDeserializer.IDeserializer<NBTBase> decoder) {
        manager.addEntry(new AbstractDeserializer<>(id(name), NBTBase.class, value -> decoder.apply(required(object(value), "value"))));
    }

    private static NBTBase nbt(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonObject()) return nbtCompound(manager, value.getAsJsonObject());
        if (value.isJsonArray()) return nbtList(manager, value);
        if (!value.isJsonPrimitive()) throw error(value, "NBT has no null value");
        if (value.getAsJsonPrimitive().isBoolean()) return new NBTTagByte((byte) (value.getAsBoolean() ? 1 : 0));
        if (value.getAsJsonPrimitive().isString()) return new NBTTagString(value.getAsString());
        BigDecimal number = value.getAsBigDecimal();
        if (number.scale() <= 0) {
            long integer = number.longValueExact();
            return integer >= Integer.MIN_VALUE && integer <= Integer.MAX_VALUE ? new NBTTagInt((int) integer) : new NBTTagLong(integer);
        }
        return new NBTTagDouble(finite(manager, value));
    }

    private static NBTTagCompound nbtCompound(DeserializerManager manager, JsonObject object) throws JsonDeserializeException {
        NBTTagCompound result = new NBTTagCompound();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) result.setTag(entry.getKey(), manager.decode(NBTBase.class, entry.getValue()));
        return result;
    }

    private static NBTTagList nbtList(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (!value.isJsonArray()) throw error(value, "NBT list requires an array");
        NBTTagList result = new NBTTagList();
        for (JsonElement element : value.getAsJsonArray()) {
            NBTBase child = manager.decode(NBTBase.class, element);
            if (result.tagCount() != 0 && result.getTagType() != child.getId()) throw error(value, "NBT list elements must share one native tag type");
            result.appendTag(child);
        }
        return result;
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

    private static int integer(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsBigDecimal().intValueExact();
        return manager.decode(Integer.class, value);
    }

    private static int boundedInteger(DeserializerManager manager, JsonElement value, int min, int max) throws JsonDeserializeException {
        int result = integer(manager, value);
        if (result < min || result > max) throw error(value, "Integer is outside [" + min + "," + max + "]");
        return result;
    }

    private static long exactLong(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsBigDecimal().longValueExact();
        return manager.decode(Long.class, value);
    }

    private static double finite(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
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
