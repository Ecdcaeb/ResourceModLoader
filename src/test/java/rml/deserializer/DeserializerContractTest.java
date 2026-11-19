package rml.deserializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import rml.deserializer.value.IntRange;
import rml.deserializer.value.WeightedList;
import rml.loader.content.CompiledTagModule;
import rml.loader.deserialize.NativeTypeDecoders;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/** Consumer contracts for nested, extensible decoded values; no world or game mocks. */
public final class DeserializerContractTest {
    private static final JsonParser JSON = new JsonParser();

    public static void main(String[] args) throws Exception {
        DeserializerManager manager = manager();
        nestedGenericRecordsKeepTheirElementTypes(manager);
        customSubtypeComposesInsideWeightedValues(manager);
        genericArraysAndPrimitiveArraysDecode(manager);
        rawJsonKeepsItsTypeField(manager);
        optionalFieldsDoNotHideInvalidValues(manager);
        geometryAndRangesRejectDataLoss(manager);
        typedNbtPreservesNamesAndNumericKinds(manager);
        System.out.println("DeserializerContractTest passed");
    }

    private static DeserializerManager manager() {
        net.minecraft.init.Bootstrap.register();
        DeserializerManager manager = new DeserializerManager("rml");
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "resource_location"),
                ResourceLocation.class, value -> new ResourceLocation(manager.decode(String.class, value))));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "item"),
                net.minecraft.item.Item.class, value -> {
                    net.minecraft.item.Item item = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(
                            manager.decode(ResourceLocation.class, value));
                    if (item == null) throw new JsonDeserializeException(value, "Unknown item");
                    return item;
                }));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "item"),
                net.minecraft.item.ItemStack.class, value -> {
                    com.google.gson.JsonObject object = value.getAsJsonObject();
                    net.minecraft.item.Item item = manager.decode(net.minecraft.item.Item.class, object.get("item"));
                    int count = object.has("count") ? object.get("count").getAsInt() : 1;
                    return new net.minecraft.item.ItemStack(item, count);
                }));
        NativeTypeDecoders.register(manager, new CompiledTagModule());
        manager.named(Term.class, new ResourceLocation("t", "literal")).record(Literal.class).build();
        manager.named(Term.class, new ResourceLocation("t", "sum")).record(Sum.class).build();
        return manager;
    }

    private static void nestedGenericRecordsKeepTheirElementTypes(DeserializerManager manager) throws Exception {
        Type type = new TypeToken<Map<ResourceLocation, List<Term>>>() { }.getType();
        Map<ResourceLocation, List<Term>> result = manager.decode(type, json("{\"demo:result\":[{\"type\":\"t:sum\",\"terms\":[{\"type\":\"t:literal\",\"value\":7},{\"type\":\"t:sum\",\"terms\":[{\"type\":\"t:literal\",\"value\":11}]}]}]}"));
        check(result.get(new ResourceLocation("demo", "result")).get(0).evaluate() == 18, "Nested custom record terms must execute as typed values");
        Type optional = new TypeToken<Optional<List<Term>>>() { }.getType();
        Optional<List<Term>> missing = manager.decode(optional, json("null"));
        check(!missing.isPresent(), "Null generic Optional is empty");
        Optional<List<Term>> present = manager.decode(optional, json("[{\"type\":\"t:literal\",\"value\":9}]"));
        check(present.get().get(0).evaluate() == 9, "Optional retains its list element decoder");
        Type keys = new TypeToken<Map<ResourceLocation, Integer>>() { }.getType();
        reject(() -> manager.decode(keys, json("{\"stone\":1,\"minecraft:stone\":2}")), "Normalized duplicate registry keys must not overwrite each other");
    }

    private static void customSubtypeComposesInsideWeightedValues(DeserializerManager manager) throws Exception {
        Type type = new TypeToken<WeightedList<Term>>() { }.getType();
        WeightedList<Term> values = manager.decode(type, json("{\"entries\":[{\"data\":{\"type\":\"t:sum\",\"terms\":[{\"type\":\"t:literal\",\"value\":2},{\"type\":\"t:literal\",\"value\":3}]},\"weight\":4000000000000000000},{\"data\":{\"type\":\"t:literal\",\"value\":13},\"weight\":4000000000000000000}]}"));
        check(values.getTotalWeight() == 8000000000000000000L, "Large integer weights retain exact precision");
        Random random = new Random(87123);
        int first = 0;
        for (int index = 0; index < 512; index++) {
            int actual = values.sample(random).evaluate();
            check(actual == 5 || actual == 13, "Weighted selection executes only decoded custom terms");
            if (actual == 5) first++;
        }
        check(first > 160 && first < 352, "Both equal large weights remain reachable without overflow bias");
        reject(() -> manager.decode(type, json("{\"entries\":[{\"data\":{\"type\":\"t:literal\",\"value\":1},\"weight\":0}]}")), "Zero weight must fail");
    }

    private static void genericArraysAndPrimitiveArraysDecode(DeserializerManager manager) throws Exception {
        Type type = new TypeToken<List<Term>[]>() { }.getType();
        List<Term>[] values = manager.decode(type, json("[[{\"type\":\"t:literal\",\"value\":4}],[{\"type\":\"t:literal\",\"value\":8}]]"));
        check(values[0].get(0).evaluate() + values[1].get(0).evaluate() == 12, "Generic array elements retain their nested type");
        int[] numbers = manager.decode(int[].class, json("[1,2,3]"));
        check(numbers[0] + numbers[1] + numbers[2] == 6, "Primitive arrays initialize and decode boxed elements correctly");
    }

    private static void rawJsonKeepsItsTypeField(DeserializerManager manager) throws Exception {
        JsonObject value = manager.decode(JsonObject.class, json("{\"type\":\"payload:meaning\",\"count\":7}"));
        check("payload:meaning".equals(value.get("type").getAsString()) && value.get("count").getAsInt() == 7,
                "Raw JSON payload type is not a decoder discriminator");
    }

    private static void optionalFieldsDoNotHideInvalidValues(DeserializerManager manager) throws Exception {
        manager.named(Term.class, new ResourceLocation("t", "optional"))
                .optionalDefault(Integer.class, "value", 7)
                .decode(context -> new Literal(context.get(Integer.class, "value"))).build();
        check(manager.decode(Term.class, json("{\"type\":\"t:optional\"}")).evaluate() == 7, "Absent optional field uses its default");
        reject(() -> manager.decode(Term.class, json("{\"type\":\"t:optional\",\"value\":{\"type\":\"missing:decoder\"}}")), "Malformed provided optional field must report an error");
        reject(() -> manager.decode(Term.class, json("{\"type\":\"t:literal\"}")), "Missing constructor field must fail before invocation");
    }

    private static void geometryAndRangesRejectDataLoss(DeserializerManager manager) throws Exception {
        manager.named(Vec3d.class, new ResourceLocation("t", "offset"))
                .require(Vec3d.class, "base").require(Vec3d.class, "offset")
                .decode(context -> context.get(Vec3d.class, "base").add(context.get(Vec3d.class, "offset"))).build();
        Vec3d value = manager.decode(Vec3d.class, json("{\"type\":\"t:offset\",\"base\":[0,64,0],\"offset\":{\"type\":\"t:offset\",\"base\":[1,0,0],\"offset\":[0,0,2]}}"));
        check(value.x == 1 && value.y == 64 && value.z == 2, "Custom vector constructors nest through the same manager");
        IntRange range = manager.decode(IntRange.class, json("{\"min\":2,\"max\":5}"));
        check(range.test(2) && range.test(5) && !range.test(1) && !range.test(6), "Range endpoints are inclusive");
        reject(() -> manager.decode(BlockPos.class, json("[0,64.5,0]")), "Block positions must not silently truncate fractions");
        Vec3i size = manager.decode(Vec3i.class, json("{\"type\":\"rml:vec3i\",\"x\":2,\"y\":3,\"z\":4}"));
        check(size.getX() == 2 && size.getY() == 3 && size.getZ() == 4, "Integer vectors decode coordinates by name");
        check(manager.decode(Vec3i.class, json("[1,2,3]")).equals(new Vec3i(1, 2, 3)), "Integer vectors decode coordinate arrays");
        reject(() -> manager.decode(Vec3i.class, json("[1,2]")), "Integer vectors must not accept a partial coordinate list");
        net.minecraft.util.EnumFacing direction = manager.decode(net.minecraft.util.EnumFacing.class,
                json("{\"type\":\"rml:direction\",\"value\":\"north\"}"));
        check(direction == net.minecraft.util.EnumFacing.NORTH, "Named directions decode case-insensitively");
        check(manager.decode(net.minecraft.util.EnumFacing.class, json("{\"type\":\"rml:direction\",\"x\":0,\"y\":-1,\"z\":0}")) == net.minecraft.util.EnumFacing.DOWN,
                "Direction vectors must name one axis step");
        reject(() -> manager.decode(net.minecraft.util.EnumFacing.class, json("{\"type\":\"rml:direction\",\"x\":1,\"y\":1,\"z\":0}")),
                "Directions must not combine axes");
        check(manager.decode(net.minecraft.util.EnumFacing.Axis.class, json("{\"type\":\"rml:axis\",\"value\":\"y\"}")) == net.minecraft.util.EnumFacing.Axis.Y,
                "Axes decode by name");
        net.minecraft.item.ItemStack stack = manager.decode(net.minecraft.item.ItemStack.class,
                json("{\"type\":\"rml:item_stack\",\"value\":\"minecraft:apple\"}"));
        check(stack.getItem() == net.minecraft.init.Items.APPLE && stack.getCount() == 1, "Item shorthand decodes one item");
        net.minecraft.item.ItemStack pair = manager.decode(net.minecraft.item.ItemStack.class,
                json("{\"type\":\"rml:item_stack\",\"value\":[\"minecraft:apple\",3]}"));
        check(pair.getItem() == net.minecraft.init.Items.APPLE && pair.getCount() == 3, "Item shorthand decodes an item and count");
        reject(() -> manager.decode(net.minecraft.item.ItemStack.class, json("{\"type\":\"rml:item_stack\",\"value\":[\"minecraft:apple\",0]}")),
                "Item shorthand must reject a nonpositive count");
        net.minecraft.item.ItemStack defaultStack = manager.decode(net.minecraft.item.ItemStack.class,
                json("{\"item\":\"minecraft:apple\",\"count\":2}"));
        check(defaultStack.getItem() == net.minecraft.init.Items.APPLE && defaultStack.getCount() == 2,
                "Named item shorthand must not replace the default item decoder");
        reject(() -> manager.decode(IntRange.class, json("{\"min\":7,\"max\":3}")), "Reversed bounds must fail");
    }

    private static void typedNbtPreservesNamesAndNumericKinds(DeserializerManager manager) throws Exception {
        NBTBase base = manager.decode(NBTBase.class, json("{\"type\":\"rml:compound\",\"value\":{\"type\":\"ritual\",\"charges\":{\"type\":\"rml:short\",\"value\":4},\"enabled\":true}}"));
        NBTTagCompound compound = (NBTTagCompound) base;
        check("ritual".equals(compound.getString("type")) && compound.getTagId("charges") == 2
                && compound.getShort("charges") == 4 && compound.getBoolean("enabled"), "NBT names and native tag types survive composition");
        reject(() -> manager.decode(NBTBase.class, json("{\"type\":\"rml:byte\",\"value\":256}")), "Typed NBT byte overflow must fail");
        reject(() -> manager.decode(NBTBase.class, json("[1,\"mixed\"]")), "Heterogeneous NBT list must not silently drop a value");
    }

    public interface Term { int evaluate(); }

    public static final class Literal implements Term {
        private final int value;
        @Record({"value"}) public Literal(int value) { this.value = value; }
        @Override public int evaluate() { return value; }
    }

    public static final class Sum implements Term {
        private final List<Term> terms;
        @Record({"terms"}) public Sum(List<Term> terms) { this.terms = terms; }
        @Override public int evaluate() { int value = 0; for (Term term : terms) value += term.evaluate(); return value; }
    }

    private static JsonElement json(String value) { return JSON.parse(value); }
    private interface CheckedAction { void run() throws Exception; }
    private static void reject(CheckedAction action, String message) throws Exception {
        try { action.run(); } catch (JsonDeserializeException expected) { return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
