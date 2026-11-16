package rml.loader.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;
import net.minecraft.world.storage.loot.conditions.RandomChance;

import java.util.Random;

/** Native serializer and condition contract; no world, game mocks or test framework required. */
public final class CompiledLootContractTest {
    private static final LootContext CONTEXT = new LootContext(0.0F, null, null, null, null, null);

    public static void main(String[] args) {
        CompiledLootModule.registerConditions();
        CompiledLootModule.registerConditions();
        Gson gson = new GsonBuilder().registerTypeHierarchyAdapter(
                LootCondition.class, new LootConditionManager.Serializer()).create();
        emptyTermsHaveBooleanIdentities(gson);
        nativeTermsShortCircuit();
        nestedConditionsRoundTrip(gson);
        malformedLogicalTermsAreRejected(gson);
        constructorsOwnTheirArrays();
        System.out.println("CompiledLootContractTest passed");
    }

    private static void emptyTermsHaveBooleanIdentities(Gson gson) {
        LootCondition all = gson.fromJson("{\"condition\":\"rml:all_of\",\"terms\":[]}", LootCondition.class);
        LootCondition any = gson.fromJson("{\"condition\":\"rml:any_of\",\"terms\":[]}", LootCondition.class);
        Random actual = new Random(81L);
        check(all.testCondition(actual, CONTEXT), "Empty all_of is true");
        check(!any.testCondition(actual, CONTEXT), "Empty any_of is false");
        check(actual.nextLong() == new Random(81L).nextLong(), "Empty terms consume no randomness");
    }

    private static void nativeTermsShortCircuit() {
        LootCondition[] terms = { new RandomChance(0.0F), new RandomChance(1.0F) };
        Random actual = new Random(415L);
        Random expected = new Random(415L);
        check(!new LogicalConditions.AllOf(terms).testCondition(actual, CONTEXT), "All stops on false");
        expected.nextFloat();
        check(actual.nextLong() == expected.nextLong(), "All skips later native conditions");

        terms = new LootCondition[] { new RandomChance(1.0F), new RandomChance(0.0F) };
        actual = new Random(416L);
        expected = new Random(416L);
        check(new LogicalConditions.AnyOf(terms).testCondition(actual, CONTEXT), "Any stops on true");
        expected.nextFloat();
        check(actual.nextLong() == expected.nextLong(), "Any skips later native conditions");

        actual = new Random(417L);
        expected = new Random(417L);
        check(new LogicalConditions.Inverted(new RandomChance(0.0F)).testCondition(actual, CONTEXT),
                "Inverted negates the native term");
        expected.nextFloat();
        check(actual.nextLong() == expected.nextLong(), "Inverted evaluates its term exactly once");
    }

    private static void nestedConditionsRoundTrip(Gson gson) {
        JsonElement source = new JsonParser().parse("{\"condition\":\"rml:all_of\",\"terms\":["
                + "{\"condition\":\"rml:any_of\",\"terms\":[{\"condition\":\"random_chance\",\"chance\":0.0},"
                + "{\"condition\":\"random_chance\",\"chance\":1.0}]},"
                + "{\"condition\":\"rml:inverted\",\"term\":{\"condition\":\"rml:any_of\",\"terms\":[]}}]}");
        LootCondition original = gson.fromJson(source, LootCondition.class);
        check(original instanceof LogicalConditions.AllOf, "All has its own native serializer class");
        JsonElement serialized = gson.toJsonTree(original, LootCondition.class);
        // Native serializers normalize unqualified native IDs to the minecraft namespace.
        JsonElement normalized = new JsonParser().parse(source.toString().replace(
                "\"random_chance\"", "\"minecraft:random_chance\""));
        check(normalized.equals(serialized), "Nested native and logical fields survive serialization");
        LootCondition decoded = gson.fromJson(serialized, LootCondition.class);
        check(decoded.testCondition(new Random(42L), CONTEXT), "Round-tripped nested conditions execute");
        check(gson.fromJson("{\"condition\":\"rml:any_of\",\"terms\":[]}", LootCondition.class)
                instanceof LogicalConditions.AnyOf, "Any has a distinct class");
        check(gson.fromJson("{\"condition\":\"rml:inverted\",\"term\":{\"condition\":\"rml:all_of\",\"terms\":[]}}",
                LootCondition.class) instanceof LogicalConditions.Inverted, "Inverted has a distinct class");
    }

    private static void malformedLogicalTermsAreRejected(Gson gson) {
        String[] invalid = {
                "{\"condition\":\"rml:all_of\"}",
                "{\"condition\":\"rml:any_of\",\"terms\":null}",
                "{\"condition\":\"rml:all_of\",\"terms\":{}}",
                "{\"condition\":\"rml:any_of\",\"terms\":[null]}",
                "{\"condition\":\"rml:all_of\",\"terms\":[true]}",
                "{\"condition\":\"rml:inverted\"}",
                "{\"condition\":\"rml:inverted\",\"term\":null}",
                "{\"condition\":\"rml:inverted\",\"term\":[]}",
                "{\"condition\":\"rml:inverted\",\"term\":{\"condition\":\"rml:missing\"}}"
        };
        for (String source : invalid) {
            try {
                gson.fromJson(source, LootCondition.class);
            } catch (JsonSyntaxException expected) {
                continue;
            }
            throw new AssertionError("Malformed logical condition accepted: " + source);
        }
    }

    private static void constructorsOwnTheirArrays() {
        LootCondition[] terms = { new RandomChance(1.0F) };
        LogicalConditions.AllOf all = new LogicalConditions.AllOf(terms);
        LogicalConditions.AnyOf any = new LogicalConditions.AnyOf(terms);
        terms[0] = new RandomChance(0.0F);
        check(all.testCondition(new Random(1L), CONTEXT), "All owns its terms array");
        check(any.testCondition(new Random(1L), CONTEXT), "Any owns its terms array");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
