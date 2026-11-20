package rml.loader.content.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;
import rml.loader.content.CompiledResource;
import rml.loader.content.ContentCatalog;
import rml.loader.content.CompiledTagModule;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import net.minecraft.entity.EntityList;
import rml.runtime.OwnedRegistry;
import rml.runtime.PreparedUnit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/** Compiles enhanced data rules once, then evaluates them against an explicit native context. */
public final class DataRules {
    private final CompiledTagModule tags;
    private DeserializerManager decoders;
    private final BiFunction<ResourceLocation, DataContext, Integer> invokeFunction;
    private static final Set<String> TYPES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("context_int_provider", "context_float_provider")));
    private final RuleCatalog catalog = new RuleCatalog();

    public interface NumberValue { double evaluate(DataContext context); }
    public interface Test { boolean test(DataContext context); }
    public interface Modifier { ItemStack apply(ItemStack stack, DataContext context); }
    public interface Effect { void apply(DataContext context, Entity target); }

    public DataRules(ContentCatalog catalog, CompiledTagModule tags, BiFunction<ResourceLocation, DataContext, Integer> invokeFunction) {
        Objects.requireNonNull(catalog, "catalog");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.invokeFunction = Objects.requireNonNull(invokeFunction, "invokeFunction");
    }

    /** Registers composable typed values in the loader's shared decoder registry. */
    public void registerDecoders(DeserializerManager manager) {
        Objects.requireNonNull(manager, "manager");
        this.decoders = manager;
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "number"), NumberValue.class,
                json -> number(json)));
        registerNumberDecoder(manager, "minecraft:constant");
        registerNumberDecoder(manager, "minecraft:uniform");
        registerNumberDecoder(manager, "minecraft:binomial");
        registerNumberDecoder(manager, "rml:context_level");
        registerNumberDecoder(manager, "rml:reference");
        registerNumberDecoder(manager, "context_int_provider");
        registerNumberDecoder(manager, "context_float_provider");
        registerNumberDecoder(manager, "minecraft:context_int_provider");
        registerNumberDecoder(manager, "minecraft:context_float_provider");
        registerNumberDecoder(manager, "minecraft:score");
        registerTestDecoder(manager, "minecraft:all_of");
        registerTestDecoder(manager, "minecraft:any_of");
        registerTestDecoder(manager, "minecraft:inverted");
        registerTestDecoder(manager, "minecraft:reference");
        registerTestDecoder(manager, "rml:all_of");
        registerTestDecoder(manager, "rml:any_of");
        registerTestDecoder(manager, "rml:inverted");
        registerTestDecoder(manager, "rml:reference");
        registerTestDecoder(manager, "rml:random_chance");
        registerTestDecoder(manager, "rml:entity_type");
        registerTestDecoder(manager, "rml:entity_on_fire");
        registerTestDecoder(manager, "rml:health");
        registerTestDecoder(manager, "rml:level");
        registerTestDecoder(manager, "rml:dimension");
        registerTestDecoder(manager, "rml:biome");
        registerTestDecoder(manager, "rml:block");
        registerTestDecoder(manager, "rml:function_result");
        registerTestDecoder(manager, "rml:item");
        registerTestDecoder(manager, "rml:tag");
        registerTestDecoder(manager, "minecraft:random_chance");
        registerTestDecoder(manager, "minecraft:random_chance_with_looting");
        registerTestDecoder(manager, "minecraft:killed_by_player");
        registerModifierDecoder(manager, "minecraft:set_count");
        registerModifierDecoder(manager, "minecraft:set_damage");
        registerModifierDecoder(manager, "minecraft:set_nbt");
        registerModifierDecoder(manager, "minecraft:set_components");
        registerModifierDecoder(manager, "rml:sequence");
        registerModifierDecoder(manager, "rml:reference");
        registerModifierDecoder(manager, "rml:set_count");
        registerModifierDecoder(manager, "rml:set_damage");
        registerModifierDecoder(manager, "rml:set_nbt");
        registerEffectDecoder(manager, "rml:run_function");
        registerEffectDecoder(manager, "rml:damage");
        registerEffectDecoder(manager, "rml:heal");
        registerEffectDecoder(manager, "rml:sequence");
        registerEffectDecoder(manager, "rml:conditional");
        registerEffectDecoder(manager, "rml:weighted");
        registerEffectDecoder(manager, "rml:noop");
        registerEffectDecoder(manager, "rml:repeat");
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "test"), Test.class,
                json -> predicate(json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "modifier"), Modifier.class,
                json -> modifier(json)));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "effect"), Effect.class,
                json -> effect(json)));
        ContextNumberDecoders.register(manager, this);
        ContextPredicateDecoders.register(manager);
        ContextModifierDecoders.register(manager);
    }

    private void registerNumberDecoder(DeserializerManager manager, String type) {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation(type), NumberValue.class,
                json -> number(manager, json)));
    }

    private void registerTestDecoder(DeserializerManager manager, String type) {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation(type), Test.class,
                json -> predicate(manager, requireObject(json, "predicate"))));
    }

    private void registerModifierDecoder(DeserializerManager manager, String type) {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation(type), Modifier.class,
                json -> ModifierRules.typed(this, manager, json)));
    }

    private void registerEffectDecoder(DeserializerManager manager, String type) {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation(type), Effect.class,
                json -> EffectRules.decode(this, decoders, json)));
    }

    public Modifier modifier(DeserializerManager manager, JsonElement definition) throws JsonDeserializeException {
        Objects.requireNonNull(manager, "manager");
        if (definition != null && definition.isJsonObject() && definition.getAsJsonObject().has("type"))
            return manager.decode(Modifier.class, definition);
        return modifier(definition);
    }
    public Effect effect(DeserializerManager manager, JsonElement definition) throws JsonDeserializeException {
        Objects.requireNonNull(manager, "manager");
        if (definition != null && definition.isJsonObject() && definition.getAsJsonObject().has("type"))
            return manager.decode(Effect.class, definition);
        return effect(definition);
    }


    public NumberValue number(DeserializerManager manager, JsonElement definition) throws JsonDeserializeException {
        return number(manager, definition, false);
    }

    private NumberValue number(DeserializerManager manager, JsonElement definition, boolean integer) throws JsonDeserializeException {
        Objects.requireNonNull(manager, "manager");
        if (definition == null || definition.isJsonNull()) throw invalid("Missing numeric definition");
        if (definition.isJsonPrimitive() && definition.getAsJsonPrimitive().isNumber()) return number(definition, integer);
        JsonObject object = requireObject(definition, "numeric definition");
        String type = string(object, "type", "constant");
        if ("uniform".equals(type) || "minecraft:uniform".equals(type)) {
            only(object, "type", "value", "min", "max");
            JsonObject bounds = object.has("value") ? requireObject(object.get("value"), "uniform value") : object;
            if (object.has("value")) only(bounds, "min", "max");
            NumberValue min = number(manager, required(bounds, "min"), integer);
            NumberValue max = number(manager, required(bounds, "max"), integer);
            return integer
                    ? context -> uniformInt(min.evaluate(context), max.evaluate(context), context)
                    : context -> { double low = min.evaluate(context), high = max.evaluate(context); return low >= high ? low : low + context.getRandom().nextDouble() * (high - low); };
        }
        if ("constant".equals(type) || "minecraft:constant".equals(type)
                || "binomial".equals(type) || "minecraft:binomial".equals(type)
                || "score".equals(type) || "minecraft:score".equals(type)
                || type.endsWith("context_int_provider") || type.endsWith("context_float_provider")
                || "rml:reference".equals(type) || "rml:context_level".equals(type))
            return number(definition, integer);
        NumberValue custom = manager.decode(NumberValue.class, definition);
        return context -> checkedNumber(custom.evaluate(context), integer);
    }

    public Test predicate(DeserializerManager manager, JsonElement definition) throws JsonDeserializeException {
        Objects.requireNonNull(manager, "manager");
        if (definition != null && definition.isJsonObject() && definition.getAsJsonObject().has("type"))
            return manager.decode(Test.class, definition);
        return predicate(definition);
    }

    private Test predicate(DeserializerManager manager, JsonObject definition) throws JsonDeserializeException {
        String type = string(definition, "type", "");
        RuleFields.predicate(type, definition);
        if ("rml:all_of".equals(type) || "minecraft:all_of".equals(type)
                || "rml:any_of".equals(type) || "minecraft:any_of".equals(type)) {
            JsonArray values = required(definition, "terms").getAsJsonArray();
            final Test[] terms = new Test[values.size()];
            for (int i = 0; i < terms.length; i++) terms[i] = predicate(manager, values.get(i));
            return type.endsWith("all_of")
                    ? context -> { for (Test term : terms) if (!term.test(context)) return false; return true; }
                    : context -> { for (Test term : terms) if (term.test(context)) return true; return false; };
        }
        if ("rml:inverted".equals(type) || "minecraft:inverted".equals(type)) {
            final Test term = predicate(manager, required(definition, "term"));
            return context -> !term.test(context);
        }
        return predicateDirect(definition);
    }

    NumberValue activeNumber(boolean integer, ResourceLocation id) { return catalog.activeNumber(integer, id); }

    CompiledTagModule tags() { return tags; }

    Test namedPredicate(ResourceLocation id) { return catalog.namedPredicate(id); }

    Modifier namedModifier(ResourceLocation id) { return catalog.namedModifier(id); }

    public NumberValue number(JsonElement definition) {
        if (decoders != null && definition != null && definition.isJsonObject() && definition.getAsJsonObject().has("type")) {
            try { return number(decoders, definition, false); }
            catch (JsonDeserializeException e) { throw new IllegalArgumentException("Cannot decode numeric expression", e); }
        }
        return number(definition, false);
    }

    private NumberValue number(JsonElement definition, boolean integer) {
        return LegacyNumbers.decode(this, definition, integer);
    }


    public Test predicate(JsonElement definition) {
        if (decoders != null && definition != null && definition.isJsonObject() && definition.getAsJsonObject().has("type")) {
            try { return predicate(decoders, definition); }
            catch (JsonDeserializeException e) { throw new IllegalArgumentException("Cannot decode predicate expression", e); }
        }
        return predicateDirect(definition);
    }

    private Test predicateDirect(JsonElement definition) {
        return LegacyPredicates.decode(this, definition);
    }

    public Modifier modifier(JsonElement definition) {
        if (definition != null && definition.isJsonObject() && definition.getAsJsonObject().has("type")) {
            if (decoders == null) return ModifierRules.direct(this, definition);
            try { return modifier(decoders, definition); }
            catch (JsonDeserializeException e) { throw new IllegalArgumentException("Cannot decode modifier expression", e); }
        }
        JsonArray sequence = definition != null && definition.isJsonArray() ? definition.getAsJsonArray() : singleton(definition);
        final Modifier[] functions = new Modifier[sequence.size()];
        for (int i = 0; i < functions.length; i++) {
            JsonElement function = sequence.get(i);
            functions[i] = decoders != null && function.isJsonObject() && function.getAsJsonObject().has("type")
                    ? modifier(function) : ModifierRules.direct(this, function);
        }
        return (stack, context) -> { ItemStack result = stack; for (Modifier function : functions) result = function.apply(result, context); return result; };
    }

    public Effect effect(JsonElement definition) {
        if (decoders != null && definition != null && definition.isJsonObject() && definition.getAsJsonObject().has("type")) {
            try { return effect(decoders, definition); }
            catch (JsonDeserializeException e) { throw new IllegalArgumentException("Cannot decode effect expression", e); }
        }
        return EffectRules.decode(this, decoders, definition);
    }

    public Set<String> getRuntimeTypes() { return TYPES; }

    public PreparedUnit prepare(CompiledResource resource) {
        return catalog.prepare(resource, (value, integer) -> decoders == null ? number(value, integer) : number(decoders, value, integer));
    }

    /** Publishes a native loot condition for live enhanced predicate references. */
    public PreparedUnit prepareNativePredicate(ResourceLocation id, LootCondition condition) {
        return catalog.prepareNativePredicate(id, condition);
    }
    /** Creates a scope-owned live predicate target; references resolve its current active value. */
    public PreparedUnit preparePredicate(ResourceLocation id, JsonElement definition) {
        return catalog.preparePredicate(id, definition, this::predicate);
    }

    /** Registers active native modifier arrays as live targets for enhanced references. */
    public PreparedUnit prepareNativeModifier(ResourceLocation id, LootFunction[] functions) {
        return catalog.prepareNativeModifier(id, functions);
    }

    /** Native serializer hook; expressions are compiled during deserialization, before repeated evaluation. */
    public void registerNative() { RuleSerializers.register(this); }

    public int invoke(ResourceLocation id, DataContext context) { return invokeFunction.apply(id, context); }

    private static void only(JsonObject object, String... allowed) { RuleFields.only(object, allowed); }
    static double checkedNumber(double value, boolean integer) {
        if (Double.isNaN(value) || Double.isInfinite(value)) throw invalid("Non-finite numeric provider value");
        if (integer && (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE || value != Math.rint(value)))
            throw invalid("Integer provider values must be integral 32-bit values");
        return value;
    }

    static double uniformInt(double minValue, double maxValue, DataContext context) {
        int min = (int) checkedNumber(minValue, true);
        int max = (int) checkedNumber(maxValue, true);
        if (min >= max) return min;
        long range = (long) max - min + 1L;
        long bits;
        long value;
        do {
            bits = context.getRandom().nextInt() & 0xffffffffL;
            value = bits % range;
        } while (bits - value + range - 1L > 0xffffffffL);
        return min + value;
    }

    private static JsonArray singleton(JsonElement value) { JsonArray a = new JsonArray(); a.add(value); return a; }
    static JsonObject requireObject(JsonElement value, String field) { if (value == null || !value.isJsonObject()) throw invalid("Expected object for " + field); return value.getAsJsonObject(); }
    static JsonElement required(JsonObject o, String key) { JsonElement e=o.get(key); if(e==null || e.isJsonNull()) throw invalid("Missing required field '"+key+"'"); return e; }
    static JsonElement optional(JsonObject o, String key, double fallback) { return o.has(key) ? o.get(key) : new com.google.gson.JsonPrimitive(fallback); }
    static String string(JsonObject o, String key, String fallback) { JsonElement e=o.get(key); return e==null ? fallback : e.getAsString(); }
    static double requiredNumber(JsonObject o, String key) { double d=required(o,key).getAsDouble(); if(Double.isNaN(d)||Double.isInfinite(d)) throw invalid("Non-finite number in '"+key+"'"); return d; }
    static int integer(JsonObject o, String key, int min, int max) {
        JsonElement value = required(o, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Field '" + key + "' must be an integer");
        double numeric = value.getAsDouble();
        if (Double.isNaN(numeric) || Double.isInfinite(numeric) || numeric != Math.rint(numeric)
                || numeric < min || numeric > max) throw invalid("Field '" + key + "' must be an integer within " + min + ".." + max);
        return (int) numeric;
    }
    static boolean bool(JsonObject o, String key, boolean fallback) { return o.has(key) ? o.get(key).getAsBoolean() : fallback; }
    static ResourceLocation resource(JsonObject o, String key) { try { return new ResourceLocation(required(o,key).getAsString()); } catch(RuntimeException e) { throw new IllegalArgumentException("Invalid resource location in '"+key+"'", e); } }
    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
