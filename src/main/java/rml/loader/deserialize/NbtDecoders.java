package rml.loader.deserialize;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;

import java.math.BigDecimal;
import java.util.Map;

/** Native NBT trees with explicit typed constructors. */
final class NbtDecoders {
    private NbtDecoders() { }

    static void register(DeserializerManager manager) {
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "nbt"), NBTBase.class,
                value -> nbt(manager, value)));
        nbtType(manager, "byte", value -> new NBTTagByte((byte) NativeTypeDecoders.boundedInteger(manager, value, Byte.MIN_VALUE, Byte.MAX_VALUE)));
        nbtType(manager, "short", value -> new NBTTagShort((short) NativeTypeDecoders.boundedInteger(manager, value, Short.MIN_VALUE, Short.MAX_VALUE)));
        nbtType(manager, "int", value -> new NBTTagInt(NativeTypeDecoders.integer(manager, value)));
        nbtType(manager, "long", value -> new NBTTagLong(NativeTypeDecoders.exactLong(manager, value)));
        nbtType(manager, "float", value -> {
            float number = manager.decode(Float.class, value);
            if (!Float.isFinite(number)) throw NativeTypeDecoders.error(value, "Expected a finite float");
            return new NBTTagFloat(number);
        });
        nbtType(manager, "double", value -> new NBTTagDouble(NativeTypeDecoders.finite(manager, value)));
        nbtType(manager, "string", value -> new NBTTagString(manager.decode(String.class, value)));
        nbtType(manager, "byte_array", value -> new NBTTagByteArray(manager.decode(byte[].class, value)));
        nbtType(manager, "int_array", value -> new NBTTagIntArray(manager.decode(int[].class, value)));
        nbtType(manager, "long_array", value -> new NBTTagLongArray(manager.decode(long[].class, value)));
        nbtType(manager, "list", value -> nbtList(manager, value));
        nbtType(manager, "compound", value -> nbtCompound(manager, NativeTypeDecoders.object(value)));
    }

    private static void nbtType(DeserializerManager manager, String name, AbstractDeserializer.IDeserializer<NBTBase> decoder) {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", name), NBTBase.class,
                value -> decoder.apply(NativeTypeDecoders.required(NativeTypeDecoders.object(value), "value"))));
    }

    private static NBTBase nbt(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonObject()) return nbtCompound(manager, value.getAsJsonObject());
        if (value.isJsonArray()) return nbtList(manager, value);
        if (!value.isJsonPrimitive()) throw NativeTypeDecoders.error(value, "NBT has no null value");
        if (value.getAsJsonPrimitive().isBoolean()) return new NBTTagByte((byte) (value.getAsBoolean() ? 1 : 0));
        if (value.getAsJsonPrimitive().isString()) return new NBTTagString(value.getAsString());
        BigDecimal number = value.getAsBigDecimal();
        if (number.scale() <= 0) {
            long integer = number.longValueExact();
            return integer >= Integer.MIN_VALUE && integer <= Integer.MAX_VALUE ? new NBTTagInt((int) integer) : new NBTTagLong(integer);
        }
        return new NBTTagDouble(NativeTypeDecoders.finite(manager, value));
    }

    private static NBTTagCompound nbtCompound(DeserializerManager manager, JsonObject object) throws JsonDeserializeException {
        NBTTagCompound result = new NBTTagCompound();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) result.setTag(entry.getKey(), manager.decode(NBTBase.class, entry.getValue()));
        return result;
    }

    private static NBTTagList nbtList(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (!value.isJsonArray()) throw NativeTypeDecoders.error(value, "NBT list requires an array");
        NBTTagList result = new NBTTagList();
        for (JsonElement element : value.getAsJsonArray()) {
            NBTBase child = manager.decode(NBTBase.class, element);
            if (result.tagCount() != 0 && result.getTagType() != child.getId()) throw NativeTypeDecoders.error(value, "NBT list elements must share one native tag type");
            result.appendTag(child);
        }
        return result;
    }
}
