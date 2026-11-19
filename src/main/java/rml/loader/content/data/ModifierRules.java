package rml.loader.content.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;

import java.util.Map;

/** Item modifiers shared by the typed registry and the direct-JSON facade. */
final class ModifierRules {
    private ModifierRules() { }

    @FunctionalInterface
    private interface Numbers {
        DataRules.NumberValue decode(JsonElement value) throws JsonDeserializeException;
    }

    @FunctionalInterface
    private interface Conditions {
        DataRules.Test[] decode(JsonElement value) throws JsonDeserializeException;
    }

    static DataRules.Modifier typed(DataRules rules, DeserializerManager manager, JsonElement definition) throws JsonDeserializeException {
        JsonObject object = DataRules.requireObject(definition, "item modifier");
        String type = DataRules.string(object, "type", "");
        if ("rml:reference".equals(type)) return direct(rules, definition);
        RuleFields.modifier(type, object);
        if ("rml:sequence".equals(type)) {
            JsonArray array = DataRules.required(object, "functions").getAsJsonArray();
            final DataRules.Modifier[] sequence = new DataRules.Modifier[array.size()];
            for (int index = 0; index < sequence.length; index++) sequence[index] = manager.decode(DataRules.Modifier.class, array.get(index));
            return (stack, context) -> {
                ItemStack result = stack;
                for (DataRules.Modifier entry : sequence) result = entry.apply(result, context);
                return result;
            };
        }
        Numbers numbers = value -> rules.number(manager, value);
        Conditions conditions = value -> tests(value, element -> rules.predicate(manager, element));
        return build(rules, object, type, numbers, conditions);
    }

    static DataRules.Modifier direct(DataRules rules, JsonElement definition) {
        JsonObject object = DataRules.requireObject(definition, "item modifier");
        String type = DataRules.string(object, "function", DataRules.string(object, "type", ""));
        RuleFields.modifier(type, object);
        Numbers numbers = value -> LegacyNumbers.decode(rules, value, false);
        Conditions conditions = value -> tests(value, rules::predicate);
        try {
            return build(rules, object, type, numbers, conditions);
        } catch (JsonDeserializeException error) {
            throw new IllegalArgumentException("Cannot decode item modifier", error);
        }
    }

    private static DataRules.Modifier build(DataRules rules, JsonObject object, String type, Numbers numbers, Conditions conditions) throws JsonDeserializeException {
        final DataRules.Test[] tests = object.has("conditions") ? conditions.decode(object.get("conditions")) : new DataRules.Test[0];
        switch (type) {
            case "rml:reference": case "reference": {
                final ResourceLocation id = DataRules.resource(object, object.has("name") ? "name" : "id");
                return (stack, context) -> {
                    if (!matches(tests, context)) return stack;
                    return rules.namedModifier(id).apply(stack, context);
                };
            }
            case "minecraft:set_count": case "rml:set_count": case "set_count": {
                final DataRules.NumberValue count = numbers.decode(DataRules.required(object, "count"));
                return (stack, context) -> {
                    if (!matches(tests, context) || stack.isEmpty()) return stack;
                    ItemStack copy = stack.copy();
                    copy.setCount(stackCount(count.evaluate(context)));
                    return copy;
                };
            }
            case "minecraft:set_damage": case "rml:set_damage": case "set_damage": {
                final DataRules.NumberValue damage = numbers.decode(DataRules.required(object, "damage"));
                return (stack, context) -> {
                    if (!matches(tests, context) || stack.isEmpty() || !stack.isItemStackDamageable()) return stack;
                    ItemStack copy = stack.copy();
                    copy.setItemDamage((int) Math.floor((1.0D - Math.max(0.0D, Math.min(1.0D, damage.evaluate(context)))) * copy.getMaxDamage()));
                    return copy;
                };
            }
            case "minecraft:set_nbt": case "rml:set_nbt": case "set_nbt": {
                final NBTTagCompound tag;
                try { tag = JsonToNBT.getTagFromJson(DataRules.required(object, "tag").getAsString()); }
                catch (Exception error) { throw new IllegalArgumentException("Invalid item modifier SNBT", error); }
                return (stack, context) -> {
                    if (!matches(tests, context) || stack.isEmpty()) return stack;
                    ItemStack copy = stack.copy();
                    NBTTagCompound merged = copy.hasTagCompound() ? copy.getTagCompound().copy() : new NBTTagCompound();
                    merged.merge(tag);
                    copy.setTagCompound(merged);
                    return copy;
                };
            }
            case "minecraft:set_components":
                return setComponents(DataRules.requireObject(DataRules.required(object, "components"), "components"), tests);
            case "minecraft:set_name": case "minecraft:set_lore": case "minecraft:enchant_randomly": case "minecraft:enchant_with_levels": case "minecraft:furnace_smelt":
                throw DataRules.invalid("Modifier '" + type + "' must be lowered to a native function or implemented by its owning loot bridge");
            default: throw DataRules.invalid("Unsupported item modifier function '" + type + "'");
        }
    }

    private static DataRules.Modifier setComponents(JsonObject components, DataRules.Test[] conditions) {
        if (components.entrySet().isEmpty()) throw DataRules.invalid("set_components requires at least one component");
        NBTTagCompound tag = new NBTTagCompound();
        for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
            final ResourceLocation component;
            try { component = new ResourceLocation(entry.getKey()); }
            catch (RuntimeException error) { throw new IllegalArgumentException("Invalid item component id '" + entry.getKey() + "'", error); }
            if (!new ResourceLocation("minecraft", "provides_trim_material").equals(component))
                throw DataRules.invalid("Unsupported runtime set_components entry '" + component + "'");
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw DataRules.invalid("provides_trim_material must be a resource location string");
            final ResourceLocation material;
            try { material = new ResourceLocation(value.getAsString()); }
            catch (RuntimeException error) { throw new IllegalArgumentException("Invalid provides_trim_material resource location", error); }
            tag.setString("rml_trim_material", material.toString());
        }
        return (stack, context) -> {
            if (!matches(conditions, context) || stack.isEmpty()) return stack;
            ItemStack copy = stack.copy();
            NBTTagCompound merged = copy.hasTagCompound() ? copy.getTagCompound().copy() : new NBTTagCompound();
            merged.merge(tag);
            copy.setTagCompound(merged);
            return copy;
        };
    }

    private static DataRules.Test[] tests(JsonElement value, ElementDecoder decoder) throws JsonDeserializeException {
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            DataRules.Test[] result = new DataRules.Test[array.size()];
            for (int index = 0; index < result.length; index++) result[index] = decoder.decode(array.get(index));
            return result;
        }
        return new DataRules.Test[]{decoder.decode(value)};
    }

    @FunctionalInterface
    private interface ElementDecoder {
        DataRules.Test decode(JsonElement value) throws JsonDeserializeException;
    }

    private static boolean matches(DataRules.Test[] tests, DataContext context) {
        for (DataRules.Test test : tests) if (!test.test(context)) return false;
        return true;
    }

    private static int stackCount(double value) {
        if (Double.isNaN(value) || value <= 0) return 0;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.floor(value);
    }
}
