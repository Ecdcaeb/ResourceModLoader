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
    private final Map<ResourceLocation, NumberValue> integers = new HashMap<>();
    private final Map<ResourceLocation, NumberValue> floats = new HashMap<>();
    private final OwnedRegistry<ResourceLocation, NumberValue> intRegistry = new OwnedRegistry<>(integers);
    private final OwnedRegistry<ResourceLocation, NumberValue> floatRegistry = new OwnedRegistry<>(floats);
    private static final Set<String> TYPES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("context_int_provider", "context_float_provider")));
    private final Map<ResourceLocation, Test> namedPredicates = new HashMap<>();
    private final OwnedRegistry<ResourceLocation, Test> predicateRegistry = new OwnedRegistry<>(namedPredicates);
    private final Map<ResourceLocation, Modifier> namedModifiers = new HashMap<>();
    private final OwnedRegistry<ResourceLocation, Modifier> modifierRegistry = new OwnedRegistry<>(namedModifiers);

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
                json -> decodeModifier(manager, json)));
    }

    private void registerEffectDecoder(DeserializerManager manager, String type) {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation(type), Effect.class,
                json -> effectDirect(json)));
    }

    private Modifier decodeModifier(DeserializerManager manager, JsonElement definition) throws JsonDeserializeException {
        JsonObject object = requireObject(definition, "item modifier");
        String type = string(object, "type", "");
        if ("rml:reference".equals(type)) return modifierFunction(definition);
        RuleFields.modifier(type, object);
        if ("rml:sequence".equals(type)) {
            JsonArray array = required(object, "functions").getAsJsonArray();
            final Modifier[] sequence = new Modifier[array.size()];
            for (int i = 0; i < sequence.length; i++) sequence[i] = manager.decode(Modifier.class, array.get(i));
            return (stack, context) -> { ItemStack result = stack; for (Modifier entry : sequence) result = entry.apply(result, context); return result; };
        }
        final Test[] conditions = object.has("conditions") ? tests(manager, object.get("conditions")) : new Test[0];
        if ("minecraft:set_count".equals(type) || "rml:set_count".equals(type)) {
            final NumberValue count = number(manager, required(object, "count"));
            return (stack, context) -> {
                if (!matches(conditions, context) || stack.isEmpty()) return stack;
                ItemStack copy = stack.copy();
                copy.setCount(stackCount(count.evaluate(context)));
                return copy;
            };
        }
        if ("minecraft:set_damage".equals(type) || "rml:set_damage".equals(type)) {
            final NumberValue damage = number(manager, required(object, "damage"));
            return (stack, context) -> {
                if (!matches(conditions, context) || stack.isEmpty() || !stack.isItemStackDamageable()) return stack;
                ItemStack copy = stack.copy();
                copy.setItemDamage((int) Math.floor((1.0D - Math.max(0.0D, Math.min(1.0D, damage.evaluate(context)))) * copy.getMaxDamage()));
                return copy;
            };
        }
        if ("minecraft:set_components".equals(type)) return setComponentsModifier(
                requireObject(required(object, "components"), "components"), conditions);
        if ("minecraft:set_nbt".equals(type) || "rml:set_nbt".equals(type)) {
            final NBTTagCompound tag;
            try { tag = JsonToNBT.getTagFromJson(required(object, "tag").getAsString()); }
            catch (Exception e) { throw new IllegalArgumentException("Invalid item modifier SNBT", e); }
            return (stack, context) -> {
                if (!matches(conditions, context) || stack.isEmpty()) return stack;
                ItemStack copy = stack.copy();
                NBTTagCompound merged = copy.hasTagCompound() ? copy.getTagCompound().copy() : new NBTTagCompound();
                merged.merge(tag);
                copy.setTagCompound(merged);
                return copy;
            };
        }
        return modifierFunction(definition);
    }

    private Test[] tests(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            Test[] result = new Test[array.size()];
            for (int i = 0; i < result.length; i++) result[i] = predicate(manager, array.get(i));
            return result;
        }
        return new Test[] { predicate(manager, value) };
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

    NumberValue activeNumber(boolean integer, ResourceLocation id) {
        NumberValue value = (integer ? integers : floats).get(id);
        if (value == null) throw new IllegalStateException("No active numeric provider '" + id + "'");
        return value;
    }

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
        JsonObject object = requireObject(definition, "predicate");
        String type = string(object, "condition", string(object, "type", ""));
        if (object.has("condition") && object.has("type")
                && !object.get("condition").getAsString().equals(object.get("type").getAsString()))
            throw invalid("Predicate cannot specify different 'condition' and 'type' values");
        RuleFields.predicate(type, object);
        switch (type) {
            case "minecraft:reference": case "reference": case "rml:reference": {
                final ResourceLocation id = resource(object, object.has("name") ? "name" : "id");
                return context -> {
                    Test referenced = namedPredicates.get(id);
                    if (referenced == null) throw new IllegalStateException("No active compiled predicate '" + id + "'");
                    return referenced.test(context);
                };
            }
            case "rml:all_of": case "minecraft:all_of": case "all_of": {
                final Test[] terms = tests(required(object, "terms"));
                return context -> { for (Test term : terms) if (!term.test(context)) return false; return true; };
            }
            case "rml:any_of": case "minecraft:any_of": case "any_of": {
                final Test[] terms = tests(required(object, "terms"));
                return context -> { for (Test term : terms) if (term.test(context)) return true; return false; };
            }
            case "rml:inverted": case "minecraft:inverted": case "inverted": {
                final Test term = predicate(required(object, "term")); return context -> !term.test(context);
            }
            case "minecraft:random_chance_with_looting": case "random_chance_with_looting": {
                final double chance = requiredNumber(object, "chance");
                final double multiplier = requiredNumber(object, "looting_multiplier");
                if (chance < 0 || chance > 1 || multiplier < 0) throw invalid("Invalid random_chance_with_looting parameters");
                return context -> {
                    LootContext loot = context.getLootContext();
                    double effective = chance + (loot == null ? 0 : loot.getLootingModifier() * multiplier);
                    return context.getRandom().nextFloat() < effective;
                };
            }
            case "minecraft:random_chance": case "random_chance": case "rml:random_chance": {
                final NumberValue chance = number(required(object, "chance"));
                return context -> {
                    double value = chance.evaluate(context);
                    if (!Double.isFinite(value) || value < 0 || value > 1) throw invalid("chance must be within 0..1");
                    return context.getRandom().nextFloat() < value;
                };
            }
            case "minecraft:killed_by_player": case "killed_by_player": {
                final boolean inverse = bool(object, "inverse", false);
                return context -> {
                    LootContext loot = context.getLootContext();
                    boolean playerKill = loot == null ? context.getEntity() instanceof EntityPlayer : loot.getKillerPlayer() != null;
                    return playerKill != inverse;
                };
            }
            case "rml:entity_type": case "entity_type": {
                final ResourceLocation id = resource(object, "entity");
                return context -> context.getEntity() != null && id.equals(EntityListCompat.id(context.getEntity()));
            }
            case "rml:entity_on_fire": case "entity_on_fire": {
                final boolean expected = bool(object, "on_fire", true);
                return context -> context.getEntity() != null && context.getEntity().isBurning() == expected;
            }
            case "rml:health": case "health": {
                final NumberValue min = number(optional(object, "min", 0));
                final NumberValue max = number(optional(object, "max", Double.MAX_VALUE));
                return context -> context.getEntity() instanceof EntityLivingBase && inRange(((EntityLivingBase) context.getEntity()).getHealth(), min.evaluate(context), max.evaluate(context));
            }
            case "rml:level": case "level": {
                final NumberValue min = number(optional(object, "min", 0));
                final NumberValue max = number(optional(object, "max", Integer.MAX_VALUE));
                return context -> inRange(context.getLevel(), min.evaluate(context), max.evaluate(context));
            }
            case "rml:dimension": case "dimension": {
                final int dimension = integer(object, "value", Integer.MIN_VALUE, Integer.MAX_VALUE);
                return context -> context.getWorld() != null && context.getWorld().provider.getDimension() == dimension;
            }
            case "rml:biome": case "biome": {
                final ResourceLocation biome = resource(object, "value");
                return context -> context.getWorld() != null && context.getOrigin() != null
                        && biome.equals(context.getWorld().getBiome(context.getOrigin()).getRegistryName());
            }
            case "rml:block": case "block": {
                final ResourceLocation block = resource(object, "value");
                final net.minecraft.block.Block expected = net.minecraft.block.Block.getBlockFromName(block.toString());
                if (expected == null) throw invalid("Unknown block '" + block + "'");
                return context -> context.getWorld() != null && context.getOrigin() != null
                        && expected == context.getWorld().getBlockState(context.getOrigin()).getBlock();
            }
            case "rml:function_result": case "function_result": {
                final ResourceLocation function = resource(object, "function");
                final int expected = integer(object, "value", Integer.MIN_VALUE, Integer.MAX_VALUE);
                return context -> invokeFunction.apply(function, context) == expected;
            }
            case "rml:item": case "item": {
                final ResourceLocation item = resource(object, "value");
                final int data = integer(object, "data", 0, 32767);
                return context -> context.getEntity() instanceof EntityLivingBase
                        && item.equals(((EntityLivingBase) context.getEntity()).getHeldItemMainhand().getItem().getRegistryName())
                        && (((EntityLivingBase) context.getEntity()).getHeldItemMainhand().getMetadata() == data);
            }
            case "rml:tag": case "tag": {
                final String registry = string(object, "registry", "block");
                final ResourceLocation tag = resource(object, "tag");
                final ResourceLocation id = resource(object, "value");
                return context -> tags.contains(registry, tag, id);
            }
            default: throw invalid("Unsupported predicate type '" + type + "'");
        }
    }

    public Modifier modifier(JsonElement definition) {
        if (definition != null && definition.isJsonObject() && definition.getAsJsonObject().has("type")) {
            if (decoders == null) return modifierFunction(definition);
            try { return modifier(decoders, definition); }
            catch (JsonDeserializeException e) { throw new IllegalArgumentException("Cannot decode modifier expression", e); }
        }
        JsonArray sequence = definition != null && definition.isJsonArray() ? definition.getAsJsonArray() : singleton(definition);
        final Modifier[] functions = new Modifier[sequence.size()];
        for (int i = 0; i < functions.length; i++) {
            JsonElement function = sequence.get(i);
            functions[i] = decoders != null && function.isJsonObject() && function.getAsJsonObject().has("type")
                    ? modifier(function) : modifierFunction(function);
        }
        return (stack, context) -> { ItemStack result = stack; for (Modifier function : functions) result = function.apply(result, context); return result; };
    }

    public Effect effect(JsonElement definition) {
        if (decoders != null && definition != null && definition.isJsonObject() && definition.getAsJsonObject().has("type")) {
            try { return effect(decoders, definition); }
            catch (JsonDeserializeException e) { throw new IllegalArgumentException("Cannot decode effect expression", e); }
        }
        return effectDirect(definition);
    }

    private Effect effectDirect(JsonElement definition) {
        JsonObject object = requireObject(definition, "effect");
        String type = string(object, "type", "");
        RuleFields.effect(type, object);
        if ("rml:sequence".equals(type)) {
            JsonArray definitions = required(object, "effects").getAsJsonArray();
            Effect[] sequence = new Effect[definitions.size()];
            for (int i = 0; i < sequence.length; i++) sequence[i] = effect(definitions.get(i));
            return (context, target) -> { for (Effect effect : sequence) effect.apply(context, target); };
        }
        if ("rml:conditional".equals(type)) {
            Test condition = predicate(required(object, "condition"));
            Effect whenTrue = effect(required(object, "on_true"));
            Effect whenFalse = object.has("on_false") ? effect(object.get("on_false")) : null;
            return (context, target) -> {
                Effect selected = condition.test(context) ? whenTrue : whenFalse;
                if (selected != null) selected.apply(context, target);
            };
        }
        if ("rml:weighted".equals(type)) {
            if (decoders == null) throw invalid("Weighted effects require the shared decoder registry");
            final rml.deserializer.value.WeightedList<Effect> effects;
            try {
                effects = decoders.decode(new com.google.gson.reflect.TypeToken<rml.deserializer.value.WeightedList<Effect>>() { }.getType(), required(object, "distribution"));
            } catch (JsonDeserializeException error) { throw new IllegalArgumentException("Cannot decode weighted effects", error); }
            return (context, target) -> effects.sample(context.getRandom()).apply(context, target);
        }
        if ("rml:run_function".equals(type)) {
            final ResourceLocation id = resource(object, "function");
            return (context, target) -> invokeFunction.apply(id, target == null ? context : context.withEntity(target));
        }
        if ("rml:damage".equals(type)) {
            final NumberValue amount = number(required(object, "amount"));
            return (context, target) -> { if (target != null) target.attackEntityFrom(net.minecraft.util.DamageSource.GENERIC, (float) Math.max(0, amount.evaluate(context))); };
        }
        if ("rml:heal".equals(type)) {
            final NumberValue amount = number(required(object, "amount"));
            return (context, target) -> { if (target instanceof EntityLivingBase) ((EntityLivingBase) target).heal((float) Math.max(0, amount.evaluate(context))); };
        }
        throw invalid("Unsupported effect type '" + type + "'");
    }

    public Set<String> getRuntimeTypes() { return TYPES; }

    public PreparedUnit prepare(CompiledResource resource) {
        Objects.requireNonNull(resource, "resource");
        final boolean integer = "context_int_provider".equals(resource.getType());
        if (!integer && !"context_float_provider".equals(resource.getType())) throw new IllegalArgumentException("Unsupported data rule resource " + resource.getType() + " at " + resource.getSource());
        final NumberValue value;
        try { value = decoders == null ? number(resource.getValue(), integer) : number(decoders, resource.getValue(), integer); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid " + resource.getType() + " at " + resource.getSource() + " (" + resource.getId() + ")", e); }
        return scope -> scope.own((integer ? intRegistry : floatRegistry).register(resource.getId(), value));
    }

    /** Publishes a native loot condition for live enhanced predicate references. */
    public PreparedUnit prepareNativePredicate(ResourceLocation id, LootCondition condition) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(condition, "condition");
        Test test = context -> {
            LootContext loot = context.asLootContext();
            return condition.testCondition(context.getRandom(), loot);
        };
        return scope -> scope.own(predicateRegistry.register(id, test));
    }
    /** Creates a scope-owned live predicate target; references resolve its current active value. */
    public PreparedUnit preparePredicate(ResourceLocation id, JsonElement definition) {
        Objects.requireNonNull(id, "id");
        final Test compiled = predicate(definition);
        return scope -> scope.own(predicateRegistry.register(id, compiled));
    }

    /** Registers active native modifier arrays as live targets for enhanced references. */
    public PreparedUnit prepareNativeModifier(ResourceLocation id, LootFunction[] functions) {
        Objects.requireNonNull(id, "id");
        final LootFunction[] copy = Objects.requireNonNull(functions, "functions").clone();
        Modifier target = (stack, context) -> {
            LootContext loot = context.asLootContext();
            ItemStack result = stack.copy();
            for (LootFunction function : copy) {
                if (LootConditionManager.testAllConditions(function.getConditions(), context.getRandom(), loot))
                    result = Objects.requireNonNull(function.apply(result, context.getRandom(), loot), "Native loot function returned null");
            }
            return result;
        };
        return scope -> scope.own(modifierRegistry.register(id, target));
    }

    /** Native serializer hook; expressions are compiled during deserialization, before repeated evaluation. */
    public void registerNative() { RuleSerializers.register(this); }

    public int invoke(ResourceLocation id, DataContext context) { return invokeFunction.apply(id, context); }

    private Modifier modifierFunction(JsonElement value) {
        JsonObject object = requireObject(value, "item modifier");
        String type = string(object, "function", string(object, "type", ""));
        RuleFields.modifier(type, object);
        final Test[] conditions = object.has("conditions") ? tests(object.get("conditions")) : new Test[0];
        switch (type) {
            case "rml:reference": case "reference": {
                final ResourceLocation id = resource(object, object.has("name") ? "name" : "id");
                return (stack, context) -> {
                    if (!matches(conditions, context)) return stack;
                    Modifier referenced = namedModifiers.get(id);
                    if (referenced == null) throw new IllegalStateException("No active compiled item modifier '" + id + "'");
                    return referenced.apply(stack, context);
                };
            }
            case "minecraft:set_count": case "set_count": {
                final NumberValue count = number(required(object, "count"));
                return (stack, context) -> { if (!matches(conditions, context) || stack.isEmpty()) return stack; ItemStack copy = stack.copy(); copy.setCount(stackCount(count.evaluate(context))); return copy; };
            }
            case "minecraft:set_damage": case "set_damage": {
                final NumberValue damage = number(required(object, "damage"));
                return (stack, context) -> { if (!matches(conditions, context) || stack.isEmpty() || !stack.isItemStackDamageable()) return stack; ItemStack copy = stack.copy(); copy.setItemDamage((int) Math.floor((1.0D - Math.max(0.0D, Math.min(1.0D, damage.evaluate(context)))) * copy.getMaxDamage())); return copy; };
            }
            case "minecraft:set_nbt": case "set_nbt": {
                final NBTTagCompound tag;
                try { tag = JsonToNBT.getTagFromJson(required(object, "tag").getAsString()); }
                catch (Exception e) { throw new IllegalArgumentException("Invalid item modifier SNBT", e); }
                return (stack, context) -> {
                    if (!matches(conditions, context) || stack.isEmpty()) return stack;
                    ItemStack copy = stack.copy();
                    NBTTagCompound merged = copy.hasTagCompound() ? copy.getTagCompound().copy() : new NBTTagCompound();
                    merged.merge(tag);
                    copy.setTagCompound(merged);
                    return copy;
                };
            }
            case "minecraft:set_components": {
                JsonObject componentObject = requireObject(required(object, "components"), "components");
                return setComponentsModifier(componentObject, conditions);
            }
            case "minecraft:set_name": case "minecraft:set_lore": case "minecraft:enchant_randomly": case "minecraft:enchant_with_levels": case "minecraft:furnace_smelt":
                throw invalid("Modifier '" + type + "' must be lowered to a native function or implemented by its owning loot bridge");
            default: throw invalid("Unsupported item modifier function '" + type + "'");
        }
    }

    private Modifier setComponentsModifier(JsonObject components, Test[] conditions) {
        if (components.entrySet().isEmpty()) throw invalid("set_components requires at least one component");
        NBTTagCompound tag = new NBTTagCompound();
        for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
            final ResourceLocation component;
            try { component = new ResourceLocation(entry.getKey()); }
            catch (RuntimeException e) { throw new IllegalArgumentException("Invalid item component id '" + entry.getKey() + "'", e); }
            if (!new ResourceLocation("minecraft", "provides_trim_material").equals(component))
                throw invalid("Unsupported runtime set_components entry '" + component + "'");
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw invalid("provides_trim_material must be a resource location string");
            final ResourceLocation material;
            try { material = new ResourceLocation(value.getAsString()); }
            catch (RuntimeException e) { throw new IllegalArgumentException("Invalid provides_trim_material resource location", e); }
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

    private Test[] tests(JsonElement value) { if (value.isJsonArray()) { JsonArray a = value.getAsJsonArray(); Test[] r = new Test[a.size()]; for (int i=0;i<r.length;i++) r[i]=predicate(a.get(i)); return r; } return new Test[]{predicate(value)}; }
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

    private static int stackCount(double value) {
        if (Double.isNaN(value) || value <= 0) return 0;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.floor(value);
    }

    private static void only(JsonObject object, String... allowed) { RuleFields.only(object, allowed); }

    private static boolean matches(Test[] tests, DataContext context) { for (Test test : tests) if (!test.test(context)) return false; return true; }
    private static boolean inRange(double value, double min, double max) { return value >= min && value <= max; }
    private static JsonArray singleton(JsonElement value) { JsonArray a = new JsonArray(); a.add(value); return a; }
    static JsonObject requireObject(JsonElement value, String field) { if (value == null || !value.isJsonObject()) throw invalid("Expected object for " + field); return value.getAsJsonObject(); }
    static JsonElement required(JsonObject o, String key) { JsonElement e=o.get(key); if(e==null || e.isJsonNull()) throw invalid("Missing required field '"+key+"'"); return e; }
    private static JsonElement optional(JsonObject o, String key, double fallback) { return o.has(key) ? o.get(key) : new com.google.gson.JsonPrimitive(fallback); }
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
    private static boolean bool(JsonObject o, String key, boolean fallback) { return o.has(key) ? o.get(key).getAsBoolean() : fallback; }
    static ResourceLocation resource(JsonObject o, String key) { try { return new ResourceLocation(required(o,key).getAsString()); } catch(RuntimeException e) { throw new IllegalArgumentException("Invalid resource location in '"+key+"'", e); } }
    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    private static final class EntityListCompat {
        static ResourceLocation id(Entity entity) { return EntityList.getKey(entity); }
    }
}
