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
                "random", "wrap", "lerp", "remap", "gamerule", "time", "sum", "product", "min_of", "max_of", "sign", "round_to", "inverse_lerp", "hypot", "tan", "exp", "log", "atan2", "log10", "fract", "step", "smoothstep", "pingpong", "quantize", "degrees", "radians", "position_x", "position_y", "position_z", "entity_x", "entity_y", "entity_z", "distance", "bit_and", "bit_or", "bit_xor", "shift_left", "shift_right", "noise", "hash",
                "enchantment_level", "entity_health", "entity_max_health", "entity_absorption", "dimension", "block_hardness", "block_light", "item_count", "item_damage", "item_durability", "item_max_damage", "item_repair_cost", "item_max_stack_size",
                "player_food", "player_saturation", "player_xp_level", "player_xp_progress"}) {
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
            case "sub": case "div": case "mod": case "floor_div": case "floor_mod": case "pow": case "atan2": {
                fields(object, "left", "right");
                DataRules.NumberValue left = number(manager, object, "left");
                DataRules.NumberValue right = number(manager, object, "right");
                final DoubleBinaryOperator operator;
                switch (type) {
                    case "sub": operator = (a, b) -> a - b; break;
                    case "pow": operator = Math::pow; break;
                    case "atan2": operator = Math::atan2; break;
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
            case "entity_absorption": {
                fields(object, "fallback");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                return context -> context.getEntity() instanceof net.minecraft.entity.EntityLivingBase
                        ? ((net.minecraft.entity.EntityLivingBase) context.getEntity()).getAbsorptionAmount() : fallback;
            }
            case "dimension": {
                fields(object, "fallback");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                return context -> context.getWorld() != null ? context.getWorld().provider.getDimension() : fallback;
            }
            case "block_hardness": {
                fields(object, "fallback", "offset");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                final net.minecraft.util.math.BlockPos offset = object.has("offset")
                        ? manager.decode(net.minecraft.util.math.BlockPos.class, object.get("offset")) : net.minecraft.util.math.BlockPos.ORIGIN;
                return context -> {
                    if (context.getWorld() == null || context.getOrigin() == null) return fallback;
                    net.minecraft.util.math.BlockPos position = net.minecraft.util.math.BlockPos.ORIGIN.equals(offset)
                            ? context.getOrigin() : context.getOrigin().add(offset);
                    return context.getWorld().getBlockState(position).getBlockHardness(context.getWorld(), position);
                };
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
            case "item_repair_cost":
                fields(object);
                return context -> context.getTool().getRepairCost();
            case "item_max_stack_size":
                fields(object);
                return context -> context.getTool().isEmpty() ? 0 : context.getTool().getMaxStackSize();
            case "random": {
                fields(object, "min", "max", "integer");
                final double min = object.has("min") ? finite(manager.decode(Double.class, object.get("min"))) : 0;
                final double max = object.has("max") ? finite(manager.decode(Double.class, object.get("max"))) : 1;
                final boolean integral = object.has("integer") && manager.decode(Boolean.class, object.get("integer"));
                if (max < min) throw error(object, "Random bounds are reversed");
                return context -> {
                    if (max == min) return min;
                    double value = min + context.getRandom().nextDouble() * (max - min);
                    return integral ? Math.floor(value) : value;
                };
            }
            case "wrap": {
                fields(object, "value", "min", "max");
                final DataRules.NumberValue value = manager.decode(DataRules.NumberValue.class, required(object, "value"));
                final double min = finite(manager.decode(Double.class, required(object, "min")));
                final double max = finite(manager.decode(Double.class, required(object, "max")));
                if (max <= min) throw error(object, "Wrap range must be positive");
                return context -> {
                    double current = value.evaluate(context);
                    double span = max - min;
                    double offset = (current - min) % span;
                    if (offset < 0) offset += span;
                    return min + offset;
                };
            }
            case "sum": case "product": case "min_of": case "max_of": {
                fields(object, "inputs");
                DataRules.NumberValue[] values = manager.decode(DataRules.NumberValue[].class, required(object, "inputs"));
                if (values.length == 0) throw error(object, "Aggregates require at least one input");
                final String aggregate = type;
                return context -> {
                    double result = "sum".equals(aggregate) ? 0 : "product".equals(aggregate) ? 1 : Double.NaN;
                    boolean first = true;
                    for (DataRules.NumberValue value : values) {
                        double current = value.evaluate(context);
                        if ("sum".equals(aggregate)) result += current;
                        else if ("product".equals(aggregate)) result *= current;
                        else if (first) { result = current; first = false; }
                        else result = "min_of".equals(aggregate) ? Math.min(result, current) : Math.max(result, current);
                    }
                    return finite(result);
                };
            }
            case "sign": {
                fields(object, "value");
                final DataRules.NumberValue value = manager.decode(DataRules.NumberValue.class, required(object, "value"));
                return context -> {
                    double current = value.evaluate(context);
                    return current > 0 ? 1 : current < 0 ? -1 : 0;
                };
            }
            case "round_to": {
                fields(object, "value", "step");
                final DataRules.NumberValue value = manager.decode(DataRules.NumberValue.class, required(object, "value"));
                final double step = finite(manager.decode(Double.class, required(object, "step")));
                if (step <= 0) throw error(object, "Round step must be positive");
                return context -> Math.floor(value.evaluate(context) / step + 0.5) * step;
            }
            case "inverse_lerp": {
                fields(object, "value", "min", "max");
                final DataRules.NumberValue value = manager.decode(DataRules.NumberValue.class, required(object, "value"));
                final double min = finite(manager.decode(Double.class, required(object, "min")));
                final double max = finite(manager.decode(Double.class, required(object, "max")));
                return context -> {
                    double current = value.evaluate(context);
                    return max == min ? 0 : (current - min) / (max - min);
                };
            }
            case "hypot": {
                fields(object, "x", "y", "z");
                final DataRules.NumberValue x = manager.decode(DataRules.NumberValue.class, required(object, "x"));
                final DataRules.NumberValue y = manager.decode(DataRules.NumberValue.class, required(object, "y"));
                final DataRules.NumberValue z = object.has("z") ? manager.decode(DataRules.NumberValue.class, object.get("z")) : null;
                return context -> {
                    double first = x.evaluate(context), second = y.evaluate(context);
                    double third = z == null ? 0 : z.evaluate(context);
                    return Math.sqrt(first * first + second * second + third * third);
                };
            }
            case "tan":
                return unary(manager, object, Math::tan);
            case "exp":
                return unary(manager, object, Math::exp);
            case "log":
                return unary(manager, object, value -> {
                    if (value <= 0) throw new IllegalArgumentException("Logarithm requires a positive value");
                    return Math.log(value);
                });
            case "log10":
                return unary(manager, object, value -> {
                    if (value <= 0) throw new IllegalArgumentException("Logarithm requires a positive value");
                    return Math.log10(value);
                });
            case "fract": return unary(manager, object, value -> value - Math.floor(value));
            case "degrees": return unary(manager, object, Math::toDegrees);
            case "radians": return unary(manager, object, Math::toRadians);
            case "step": {
                fields(object, "value", "edge");
                final DataRules.NumberValue value = number(manager, object, "value");
                final DataRules.NumberValue edge = number(manager, object, "edge");
                return context -> value.evaluate(context) < edge.evaluate(context) ? 0 : 1;
            }
            case "smoothstep": {
                fields(object, "value", "from", "to");
                final DataRules.NumberValue value = number(manager, object, "value");
                final double from = finite(manager.decode(Double.class, required(object, "from")));
                final double to = finite(manager.decode(Double.class, required(object, "to")));
                if (from == to) throw error(object, "Smoothstep range must not be empty");
                return context -> {
                    double ratio = Math.max(0, Math.min(1, (value.evaluate(context) - from) / (to - from)));
                    return ratio * ratio * (3 - 2 * ratio);
                };
            }
            case "pingpong": {
                fields(object, "value", "length");
                final DataRules.NumberValue value = number(manager, object, "value");
                final double length = finite(manager.decode(Double.class, required(object, "length")));
                if (length <= 0) throw error(object, "Ping-pong length must be positive");
                return context -> {
                    double wrapped = value.evaluate(context) % (2 * length);
                    if (wrapped < 0) wrapped += 2 * length;
                    return wrapped <= length ? wrapped : 2 * length - wrapped;
                };
            }
            case "quantize": {
                fields(object, "value", "step");
                final DataRules.NumberValue value = number(manager, object, "value");
                final double step = finite(manager.decode(Double.class, required(object, "step")));
                if (step <= 0) throw error(object, "Quantize step must be positive");
                return context -> Math.floor(value.evaluate(context) / step) * step;
            }
            case "position_x": case "position_y": case "position_z": {
                fields(object, "fallback");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                final int axis = type.endsWith("_x") ? 0 : type.endsWith("_y") ? 1 : 2;
                return context -> {
                    net.minecraft.util.math.BlockPos origin = context.getOrigin();
                    if (origin == null) return fallback;
                    return axis == 0 ? origin.getX() : axis == 1 ? origin.getY() : origin.getZ();
                };
            }
            case "entity_x": case "entity_y": case "entity_z": {
                fields(object, "fallback");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                final int axis = type.endsWith("_x") ? 0 : type.endsWith("_y") ? 1 : 2;
                return context -> {
                    net.minecraft.entity.Entity entity = context.getEntity();
                    if (entity == null) return fallback;
                    return axis == 0 ? entity.posX : axis == 1 ? entity.posY : entity.posZ;
                };
            }
            case "distance": {
                fields(object, "fallback");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                return context -> context.getEntity() == null || context.getOrigin() == null ? fallback
                        : Math.sqrt(context.getEntity().getDistanceSq(context.getOrigin()));
            }
            case "bit_and": case "bit_or": case "bit_xor": case "shift_left": case "shift_right": {
                fields(object, "left", "right");
                final DataRules.NumberValue left = number(manager, object, "left");
                final DataRules.NumberValue right = number(manager, object, "right");
                final String operation = type;
                return context -> {
                    long a = (long) left.evaluate(context), b = (long) right.evaluate(context);
                    switch (operation) {
                        case "bit_and": return finite(a & b);
                        case "bit_or": return finite(a | b);
                        case "bit_xor": return finite(a ^ b);
                        case "shift_left": return finite(a << b);
                        default: return finite(a >> b);
                    }
                };
            }
            case "noise": {
                fields(object, "x", "y", "z", "scale", "seed");
                final DataRules.NumberValue x = number(manager, object, "x");
                final DataRules.NumberValue y = number(manager, object, "y");
                final DataRules.NumberValue z = object.has("z") ? number(manager, object, "z") : null;
                final double scale = object.has("scale") ? finite(manager.decode(Double.class, object.get("scale"))) : 1;
                final long seed = object.has("seed") ? manager.decode(Long.class, object.get("seed")) : 0;
                if (scale == 0) throw error(object, "Noise scale must not be zero");
                return context -> valueNoise(seed,
                        x.evaluate(context) / scale,
                        y.evaluate(context) / scale,
                        (z == null ? 0 : z.evaluate(context)) / scale);
            }
            case "hash": {
                fields(object, "inputs", "seed");
                final DataRules.NumberValue[] values = manager.decode(DataRules.NumberValue[].class, required(object, "inputs"));
                final long seed = object.has("seed") ? manager.decode(Long.class, object.get("seed")) : 0;
                return context -> {
                    long hash = seed;
                    for (DataRules.NumberValue value : values) hash = mix(hash, Double.doubleToLongBits(value.evaluate(context)));
                    return (hash >>> 11) * 0x1.0p-53;
                };
            }
            case "lerp": {
                fields(object, "from", "to", "t");
                final double from = finite(manager.decode(Double.class, required(object, "from")));
                final double to = finite(manager.decode(Double.class, required(object, "to")));
                final DataRules.NumberValue t = manager.decode(DataRules.NumberValue.class, required(object, "t"));
                return context -> from + (to - from) * t.evaluate(context);
            }
            case "remap": {
                fields(object, "value", "from_min", "from_max", "to_min", "to_max");
                final DataRules.NumberValue value = manager.decode(DataRules.NumberValue.class, required(object, "value"));
                final double fromMin = finite(manager.decode(Double.class, required(object, "from_min")));
                final double fromMax = finite(manager.decode(Double.class, required(object, "from_max")));
                final double toMin = finite(manager.decode(Double.class, required(object, "to_min")));
                final double toMax = finite(manager.decode(Double.class, required(object, "to_max")));
                if (fromMax == fromMin) throw error(object, "Remap source range must not be empty");
                return context -> {
                    double ratio = (value.evaluate(context) - fromMin) / (fromMax - fromMin);
                    return toMin + ratio * (toMax - toMin);
                };
            }
            case "gamerule": {
                fields(object, "rule", "fallback");
                final String name = manager.decode(String.class, required(object, "rule"));
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                return context -> {
                    if (context.getWorld() == null) return fallback;
                    net.minecraft.world.GameRules gameRules = context.getWorld().getGameRules();
                    if (!gameRules.hasRule(name)) return fallback;
                    String value = gameRules.getString(name);
                    if ("true".equalsIgnoreCase(value)) return 1;
                    if ("false".equalsIgnoreCase(value)) return 0;
                    try { return Double.parseDouble(value); }
                    catch (NumberFormatException failure) { return fallback; }
                };
            }
            case "time": {
                fields(object, "fallback");
                final double fallback = object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
                return context -> context.getWorld() == null ? fallback : context.getWorld().getWorldTime() % 24000;
            }
            case "player_food": {
                fields(object, "fallback");
                final double fallback = playerFallback(manager, object);
                return context -> context.getEntity() instanceof net.minecraft.entity.player.EntityPlayer
                        ? ((net.minecraft.entity.player.EntityPlayer) context.getEntity()).getFoodStats().getFoodLevel() : fallback;
            }
            case "player_saturation": {
                fields(object, "fallback");
                final double fallback = playerFallback(manager, object);
                return context -> context.getEntity() instanceof net.minecraft.entity.player.EntityPlayer
                        ? ((net.minecraft.entity.player.EntityPlayer) context.getEntity()).getFoodStats().getSaturationLevel() : fallback;
            }
            case "player_xp_level": {
                fields(object, "fallback");
                final double fallback = playerFallback(manager, object);
                return context -> context.getEntity() instanceof net.minecraft.entity.player.EntityPlayer
                        ? ((net.minecraft.entity.player.EntityPlayer) context.getEntity()).experienceLevel : fallback;
            }
            case "player_xp_progress": {
                fields(object, "fallback");
                final double fallback = playerFallback(manager, object);
                return context -> context.getEntity() instanceof net.minecraft.entity.player.EntityPlayer
                        ? ((net.minecraft.entity.player.EntityPlayer) context.getEntity()).experience : fallback;
            }
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
    private static double playerFallback(DeserializerManager manager, JsonObject object) throws JsonDeserializeException {
        return object.has("fallback") ? finite(manager.decode(Double.class, object.get("fallback"))) : 0;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Numeric expression produced a non-finite result");
        return value;
    }

    private static double valueNoise(long seed, double x, double y, double z) {
        long ix = (long) Math.floor(x), iy = (long) Math.floor(y), iz = (long) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        double u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy), w = fz * fz * (3 - 2 * fz);
        double near = lerp(lerp(lattice(seed, ix, iy, iz), lattice(seed, ix + 1, iy, iz), u),
                lerp(lattice(seed, ix, iy + 1, iz), lattice(seed, ix + 1, iy + 1, iz), u), v);
        double far = lerp(lerp(lattice(seed, ix, iy, iz + 1), lattice(seed, ix + 1, iy, iz + 1), u),
                lerp(lattice(seed, ix, iy + 1, iz + 1), lattice(seed, ix + 1, iy + 1, iz + 1), u), v);
        return lerp(near, far, w);
    }

    private static double lattice(long seed, long x, long y, long z) {
        long hash = mix(mix(mix(seed, x), y), z);
        return (hash >>> 11) * 0x1.0p-53 * 2 - 1;
    }

    private static long mix(long hash, long value) {
        long mixed = hash + 0x9E3779B97F4A7C15L * value;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static double lerp(double from, double to, double ratio) {
        return from + (to - from) * ratio;
    }
}
