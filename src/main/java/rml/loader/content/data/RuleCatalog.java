package rml.loader.content.data;

import com.google.gson.JsonElement;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.functions.LootFunction;
import rml.deserializer.JsonDeserializeException;
import rml.loader.content.CompiledResource;
import rml.runtime.OwnedRegistry;
import rml.runtime.PreparedUnit;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Owns the named rule targets that a prepared content generation publishes and retires. */
final class RuleCatalog {
    interface NumberDecoder {
        DataRules.NumberValue decode(JsonElement value, boolean integer) throws JsonDeserializeException;
    }

    private final Map<ResourceLocation, DataRules.NumberValue> integers = new HashMap<>();
    private final Map<ResourceLocation, DataRules.NumberValue> floats = new HashMap<>();
    private final OwnedRegistry<ResourceLocation, DataRules.NumberValue> intRegistry = new OwnedRegistry<>(integers);
    private final OwnedRegistry<ResourceLocation, DataRules.NumberValue> floatRegistry = new OwnedRegistry<>(floats);
    private final Map<ResourceLocation, DataRules.Test> namedPredicates = new HashMap<>();
    private final OwnedRegistry<ResourceLocation, DataRules.Test> predicateRegistry = new OwnedRegistry<>(namedPredicates);
    private final Map<ResourceLocation, DataRules.Modifier> namedModifiers = new HashMap<>();
    private final OwnedRegistry<ResourceLocation, DataRules.Modifier> modifierRegistry = new OwnedRegistry<>(namedModifiers);

    DataRules.NumberValue activeNumber(boolean integer, ResourceLocation id) {
        DataRules.NumberValue value = (integer ? integers : floats).get(id);
        if (value == null) throw new IllegalStateException("No active numeric provider '" + id + "'");
        return value;
    }

    DataRules.Test namedPredicate(ResourceLocation id) {
        DataRules.Test value = namedPredicates.get(id);
        if (value == null) throw new IllegalStateException("No active compiled predicate '" + id + "'");
        return value;
    }

    DataRules.Modifier namedModifier(ResourceLocation id) {
        DataRules.Modifier value = namedModifiers.get(id);
        if (value == null) throw new IllegalStateException("No active compiled item modifier '" + id + "'");
        return value;
    }

    PreparedUnit prepare(CompiledResource resource, NumberDecoder decoder) {
        Objects.requireNonNull(resource, "resource");
        final boolean integer = "context_int_provider".equals(resource.getType());
        if (!integer && !"context_float_provider".equals(resource.getType())) {
            throw new IllegalArgumentException("Unsupported data rule resource " + resource.getType() + " at " + resource.getSource());
        }
        final DataRules.NumberValue value;
        try {
            value = decoder.decode(resource.getValue(), integer);
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid " + resource.getType() + " at " + resource.getSource() + " (" + resource.getId() + ")", error);
        }
        return scope -> scope.own((integer ? intRegistry : floatRegistry).register(resource.getId(), value));
    }

    /** Publishes a native loot condition for live enhanced predicate references. */
    PreparedUnit prepareNativePredicate(ResourceLocation id, LootCondition condition) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(condition, "condition");
        DataRules.Test test = context -> {
            LootContext loot = context.asLootContext();
            return condition.testCondition(context.getRandom(), loot);
        };
        return scope -> scope.own(predicateRegistry.register(id, test));
    }

    /** Creates a scope-owned live predicate target; references resolve its current active value. */
    PreparedUnit preparePredicate(ResourceLocation id, JsonElement definition, Function<JsonElement, DataRules.Test> decoder) {
        Objects.requireNonNull(id, "id");
        final DataRules.Test compiled = decoder.apply(definition);
        return scope -> scope.own(predicateRegistry.register(id, compiled));
    }

    /** Registers active native modifier arrays as live targets for enhanced references. */
    PreparedUnit prepareNativeModifier(ResourceLocation id, LootFunction[] functions) {
        Objects.requireNonNull(id, "id");
        final LootFunction[] copy = Objects.requireNonNull(functions, "functions").clone();
        DataRules.Modifier target = (stack, context) -> {
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
}
