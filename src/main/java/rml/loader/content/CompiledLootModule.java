package rml.loader.content;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableManager;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import rml.loader.content.data.DataRules;
import rml.runtime.OwnedRegistry;
import rml.runtime.PreparedUnit;
import rml.runtime.ResourceScope;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Prepared native loot resources, published and released on the owning game thread. */
public final class CompiledLootModule {
    private final DataRules rules;
    private final Map<ResourceLocation, Registration<LootTable>> tables = new HashMap<>();
    private final Map<ResourceLocation, Registration<LootCondition>> predicates = new HashMap<>();
    private final Map<ResourceLocation, Registration<LootFunction[]>> modifiers = new HashMap<>();
    private final OwnedRegistry<ResourceLocation, Registration<LootTable>> tableRegistry =
            new OwnedRegistry<>(tables);
    private final OwnedRegistry<ResourceLocation, Registration<LootCondition>> predicateRegistry =
            new OwnedRegistry<>(predicates);
    private final OwnedRegistry<ResourceLocation, Registration<LootFunction[]>> modifierRegistry =
            new OwnedRegistry<>(modifiers);

    /** Construction does not initialize native serializers or register global conditions. */
    public CompiledLootModule(DataRules rules) { this.rules = Objects.requireNonNull(rules, "rules"); }

    /** Invoke at Forge preInit; repeated calls from the same loader are harmless. */
    public static void registerConditions() {
        LogicalConditions.register();
    }

    /** Decode the entire definition before publishing any owned resource. */
    public PreparedUnit prepare(CompiledResource resource) {
        Objects.requireNonNull(resource, "resource");
        try {
            JsonElement value = resource.getValue();
            ResourceLocation id = Objects.requireNonNull(resource.getId(), "resource id");
            switch (resource.getType()) {
                case "loot_table":
                    requireObject(value, "loot table");
                    validateNativeArrays(value);
                    // Forge's pool/entry adapters require its naming context. Custom tables skip
                    // LootTableLoadEvent, which cannot participate in candidate rollback.
                    LootTable table = ForgeHooks.loadLootTable(NativeGson.INSTANCE, id,
                            value.toString(), true, null);
                    if (table == null) {
                        throw new JsonSyntaxException("Native loot table decoder returned null");
                    }
                    return registration(tableRegistry, id, table);
                case "predicate": {
                    requireObject(value, "predicate");
                    validateNativeArrays(value);
                    LootCondition predicate = NativeGson.INSTANCE.fromJson(value, LootCondition.class);
                    if (predicate == null) {
                        throw new JsonSyntaxException("Native predicate decoder returned null");
                    }
                    PreparedUnit nativeRegistration = registration(predicateRegistry, id, predicate);
                    PreparedUnit ruleRegistration = rules.prepareNativePredicate(id, predicate);
                    return scope -> {
                        nativeRegistration.activate(scope);
                        ruleRegistration.activate(scope);
                    };
                }
                case "item_modifier": {
                    LootFunction[] functions;
                    if (value.isJsonArray()) {
                        JsonArray array = value.getAsJsonArray();
                        functions = new LootFunction[array.size()];
                        for (int i = 0; i < functions.length; i++) {
                            functions[i] = decodeFunction(array.get(i), "item modifier[" + i + "]");
                        }
                    } else {
                        functions = new LootFunction[] { decodeFunction(value, "item modifier") };
                    }
                    PreparedUnit nativeRegistration = registration(modifierRegistry, id, functions);
                    PreparedUnit ruleRegistration = rules.prepareNativeModifier(id, functions);
                    return scope -> {
                        nativeRegistration.activate(scope);
                        ruleRegistration.activate(scope);
                    };
                }
                default:
                    throw new IllegalArgumentException("Unsupported loot resource type '"
                            + resource.getType() + "'");
            }
        } catch (RuntimeException cause) {
            throw new IllegalArgumentException("Cannot prepare " + resource.getType() + " '"
                    + resource.getId() + "' from " + resource.getSource() + " (pack "
                    + resource.getPackId() + "): " + cause.getMessage(), cause);
        }
    }

    /** Returns only an active, already-decoded table; absent IDs use the native lookup path. */
    public LootTable findLootTable(ResourceLocation id) {
        return activeValue(tables.get(Objects.requireNonNull(id, "id")));
    }

    public boolean testPredicate(ResourceLocation id, Random random, LootContext context) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(context, "context");
        LootCondition predicate = activeValue(predicates.get(id));
        if (predicate == null) {
            throw new IllegalArgumentException("No active compiled predicate '" + id + "'");
        }
        return predicate.testCondition(random, context);
    }

    /** Applies native functions in order, always preserving the caller's original stack. */
    public ItemStack modifyItem(ResourceLocation id, ItemStack stack, Random random, LootContext context) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(context, "context");
        LootFunction[] functions = activeValue(modifiers.get(id));
        if (functions == null) {
            throw new IllegalArgumentException("No active compiled item modifier '" + id + "'");
        }
        ItemStack modified = stack.copy();
        for (LootFunction function : functions) {
            // LootFunction.apply does not test its conditions; native LootEntryItem does this.
            if (LootConditionManager.testAllConditions(function.getConditions(), random, context)) {
                modified = Objects.requireNonNull(function.apply(modified, random, context),
                        "Native loot function returned null");
            }
        }
        return modified;
    }

    private static LootFunction decodeFunction(JsonElement value, String name) {
        requireObject(value, name);
        validateNativeArrays(value);
        LootFunction function = NativeGson.INSTANCE.fromJson(value, LootFunction.class);
        if (function == null) {
            throw new JsonSyntaxException("Native function decoder returned null for " + name);
        }
        return function;
    }

    private static void requireObject(JsonElement value, String name) {
        if (value == null || !value.isJsonObject()) {
            throw new JsonSyntaxException("Expected an object for " + name);
        }
    }

    /** Gson accepts null array members which native loot would otherwise dereference at use. */
    private static void validateNativeArrays(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                String name = field.getKey();
                JsonElement child = field.getValue();
                if ("pools".equals(name) || "entries".equals(name)
                        || "conditions".equals(name) || "functions".equals(name)) {
                    if (!child.isJsonArray()) {
                        throw new JsonSyntaxException("Expected an array for '" + name + "'");
                    }
                    JsonArray array = child.getAsJsonArray();
                    for (int i = 0; i < array.size(); i++) {
                        requireObject(array.get(i), name + "[" + i + "]");
                    }
                }
                validateNativeArrays(child);
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                validateNativeArrays(child);
            }
        }
    }

    private static <T> PreparedUnit registration(
            OwnedRegistry<ResourceLocation, Registration<T>> registry, ResourceLocation id, T value) {
        return scope -> {
            AutoCloseable handle = registry.register(id, new Registration<>(scope, value));
            try {
                scope.own(handle);
            } catch (RuntimeException cause) {
                try {
                    handle.close();
                } catch (Exception cleanup) {
                    cause.addSuppressed(cleanup);
                }
                throw cause;
            }
        };
    }

    private static <T> T activeValue(Registration<T> registration) {
        return registration != null && registration.scope.isActive() ? registration.value : null;
    }

    private static final class Registration<T> {
        private final ResourceScope scope;
        private final T value;

        private Registration(ResourceScope scope, T value) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.value = value;
        }
    }

    /** Shares native and mod-registered adapters, resolved only when preparation needs them. */
    private static final class NativeGson {
        // LootTableManager.GSON_INSTANCE in stable_39; verified SRG name for production.
        private static final Gson INSTANCE = ObfuscationReflectionHelper.getPrivateValue(
                LootTableManager.class, null, "field_186526_b");
    }
}
