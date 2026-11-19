package rml.loader.content.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.FloatProvider;
import rml.deserializer.value.IntProvider;
import rml.deserializer.value.LevelBasedValue;
import rml.deserializer.value.WeightedList;
import rml.loader.runtime.function.FunctionDataPath;
import rml.loader.runtime.function.FunctionStorage;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import static rml.loader.content.data.DecodeSupport.error;
import static rml.loader.content.data.DecodeSupport.fields;
import static rml.loader.content.data.DecodeSupport.required;

/** Context-dependent numeric expressions; every child is resolved through the public decoder registry. */
final class ContextNumberDecoders {
    private ContextNumberDecoders() { }

    static void register(DeserializerManager manager, DataRules rules) {
        for (String name : new String[]{"add", "mul", "min", "max", "avg", "sub", "div", "mod", "pow",
                "floor_div", "floor_mod", "abs", "negate", "sqrt", "sin", "cos", "floor", "ceil", "round", "truncate",
                "clamp", "conditional", "weighted", "number_dispatcher", "sample_int", "sample_float", "level_based", "storage", "input",
                "enchantment_level", "entity_health", "entity_max_health", "block_light", "item_count", "item_damage", "item_durability", "item_max_damage"}) {
            manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", name), DataRules.NumberValue.class,
                    json -> decode(manager, rules, json.getAsJsonObject())));
        }
    }

    private static DataRules.NumberValue decode(DeserializerManager manager, DataRules rules, JsonObject object) throws JsonDeserializeException {
        String type = new ResourceLocation(object.get("type").getAsString()).getPath();
        switch (type) {
            case "add": case "mul": case "min": case "max": case "avg": {
                fields(object, "inputs");
                DataRules.NumberValue[] values = manager.decode(DataRules.NumberValue[].class, required(object, "inputs"));
                if (values.length == 0 && !("add".equals(type) || "mul".equals(type))) throw error(object, "Aggregate requires at least one input");
                final boolean average = "avg".equals(type);
                final double initial = "mul".equals(type) ? 1 : "min".equals(type) ? Double.POSITIVE_INFINITY : "max".equals(type) ? Double.NEGATIVE_INFINITY : 0;
                final DoubleBinaryOperator operator = "mul".equals(type) ? (a, b) -> a * b
                        : "min".equals(type) ? Math::min : "max".equals(type) ? Math::max : Double::sum;
                return context -> {
                    double result = initial;
                    for (DataRules.NumberValue value : values) result = operator.applyAsDouble(result, value.evaluate(context));
                    return finite(average ? result / values.length : result);
                };
            }
            case "sub": case "div": case "mod": case "floor_div": case "floor_mod": case "pow": {
                fields(object, "left", "right");
                DataRules.NumberValue left = number(manager, object, "left");
                DataRules.NumberValue right = number(manager, object, "right");
                final DoubleBinaryOperator operator;
                switch (type) {
                    case "sub": operator = (a, b) -> a - b; break;
                    case "pow": operator = Math::pow; break;
                    case "div": operator = (a, b) -> a / b; break;
                    case "mod": operator = (a, b) -> a % b; break;
                    case "floor_div": operator = (a, b) -> Math.floor(a / b); break;
                    default: operator = (a, b) -> a - Math.floor(a / b) * b;
                }
                return context -> finite(operator.applyAsDouble(left.evaluate(context), right.evaluate(context)));
            }
            case "abs": return unary(manager, object, Math::abs);
            case "negate": return unary(manager, object, value -> -value);
            case "sqrt": return unary(manager, object, Math::sqrt);
            case "sin": return unary(manager, object, Math::sin);
            case "cos": return unary(manager, object, Math::cos);
            case "floor": return unary(manager, object, Math::floor);
            case "ceil": return unary(manager, object, Math::ceil);
            case "round": return unary(manager, object, value -> Math.floor(value + 0.5D));
            case "truncate": return unary(manager, object, value -> value < 0 ? Math.ceil(value) : Math.floor(value));
            case "clamp": {
                fields(object, "value", "min", "max");
                DataRules.NumberValue value = number(manager, object, "value"), min = number(manager, object, "min"), max = number(manager, object, "max");
                return context -> {
                    double low = min.evaluate(context), high = max.evaluate(context);
                    if (low > high) throw new IllegalArgumentException("Clamp minimum exceeds maximum");
                    return finite(Math.max(low, Math.min(high, value.evaluate(context))));
                };
            }
            case "conditional": {
                fields(object, "condition", "on_true", "on_false");
                DataRules.Test condition = rules.predicate(manager, required(object, "condition"));
                DataRules.NumberValue onTrue = number(manager, object, "on_true"), onFalse = number(manager, object, "on_false");
                return context -> (condition.test(context) ? onTrue : onFalse).evaluate(context);
            }
            case "number_dispatcher": {
                fields(object, "cases", "default");
                JsonElement cases = required(object, "cases");
                if (!cases.isJsonArray()) throw error(object, "Number dispatcher cases must be an array");
                DataRules.Test[] conditions = new DataRules.Test[cases.getAsJsonArray().size()];
                DataRules.NumberValue[] values = new DataRules.NumberValue[conditions.length];
                for (int i = 0; i < conditions.length; i++) {
                    JsonObject branch = cases.getAsJsonArray().get(i).getAsJsonObject();
                    fields(branch, "condition", "value");
                    conditions[i] = rules.predicate(manager, required(branch, "condition"));
                    values[i] = number(manager, branch, "value");
                }
                DataRules.NumberValue fallback = number(manager, object, "default");
                return context -> {
                    for (int i = 0; i < conditions.length; i++) if (conditions[i].test(context)) return values[i].evaluate(context);
                    return fallback.evaluate(context);
                };
            }
            case "weighted": {
                fields(object, "distribution");
                WeightedList<DataRules.NumberValue> distribution = manager.decode(new TypeToken<WeightedList<DataRules.NumberValue>>() { }.getType(), required(object, "distribution"));
                return context -> distribution.sample(context.getRandom()).evaluate(context);
            }
            case "sample_int": {
                fields(object, "provider");
                IntProvider provider = manager.decode(IntProvider.class, required(object, "provider"));
                return context -> provider.sample(context.getRandom());
            }
            case "sample_float": {
                fields(object, "provider");
                FloatProvider provider = manager.decode(FloatProvider.class, required(object, "provider"));
                return context -> provider.sample(context.getRandom());
            }
            case "level_based": {
                fields(object, "value");
                LevelBasedValue value = manager.decode(LevelBasedValue.class, required(object, "value"));
                return context -> value.calculate(context.getLevel());
            }
            case "storage": case "input": {
                fields(object, "storage", "path", "fallback");
                ResourceLocation storage = "storage".equals(type) ? manager.decode(ResourceLocation.class, required(object, "storage")) : null;
                if (storage == null && object.has("storage")) throw error(object, "Input lookup cannot specify a storage namespace");
                FunctionDataPath.Compiled path = manager.decode(FunctionDataPath.Compiled.class, required(object, "path"));
                DataRules.NumberValue fallback = object.has("fallback") ? number(manager, object, "fallback") : null;
                return context -> {
                    if (storage != null && context.getWorld() == null) throw new IllegalStateException("Storage lookup requires a server world");
                    NBTBase value = storage == null ? context.getInput(path) : FunctionStorage.get(context.getWorld()).get(storage, path);
                    if (value == null && fallback != null) return fallback.evaluate(context);
                    if (!(value instanceof NBTPrimitive)) throw new IllegalArgumentException("Numeric data path is absent or is not numeric");
                    return finite(((NBTPrimitive) value).getDouble());
                };
            }
            case "entity_health": {
                fields(object, "fallback");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                return context -> context.getEntity() instanceof net.minecraft.entity.EntityLivingBase
                        ? ((net.minecraft.entity.EntityLivingBase) context.getEntity()).getHealth() : fallback;
            }
            case "entity_max_health": {
                fields(object, "fallback");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                return context -> context.getEntity() instanceof net.minecraft.entity.EntityLivingBase
                        ? ((net.minecraft.entity.EntityLivingBase) context.getEntity()).getMaxHealth() : fallback;
            }
            case "block_light": {
                fields(object, "fallback", "offset");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                final net.minecraft.util.math.BlockPos offset = object.has("offset")
                        ? manager.decode(net.minecraft.util.math.BlockPos.class, object.get("offset")) : net.minecraft.util.math.BlockPos.ORIGIN;
                return context -> context.getWorld() != null && context.getOrigin() != null
                        ? context.getWorld().getLight(context.getOrigin().add(offset)) : fallback;
            }
            case "item_count":
                fields(object);
                return context -> context.getTool().getCount();
            case "item_damage":
                fields(object);
                return context -> context.getTool().getItemDamage();
            case "item_durability":
                fields(object);
                return context -> {
                    net.minecraft.item.ItemStack tool = context.getTool();
                    return tool.isEmpty() || !tool.isItemStackDamageable() ? 0 : tool.getMaxDamage() - tool.getItemDamage();
                };
            case "item_max_damage":
                fields(object);
                return context -> {
                    net.minecraft.item.ItemStack tool = context.getTool();
                    return tool.isEmpty() ? 0 : tool.getMaxDamage();
                };
            case "enchantment_level": {
                fields(object, "enchantment");
                ResourceLocation id = manager.decode(ResourceLocation.class, required(object, "enchantment"));
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
                if (enchantment == null) throw error(object, "Unknown enchantment " + id);
                return context -> EnchantmentHelper.getEnchantmentLevel(enchantment, context.getTool());
            }
            default: throw error(object, "Unknown context numeric operator " + type);
        }
    }

    private static DataRules.NumberValue unary(DeserializerManager manager, JsonObject object, DoubleUnaryOperator operator) throws JsonDeserializeException {
        fields(object, "input");
        DataRules.NumberValue input = number(manager, object, "input");
        return context -> finite(operator.applyAsDouble(input.evaluate(context)));
    }
    private static DataRules.NumberValue number(DeserializerManager manager, JsonObject object, String field) throws JsonDeserializeException {
        return manager.decode(DataRules.NumberValue.class, required(object, field));
    }
    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Numeric expression produced a non-finite result");
        return value;
    }
}
