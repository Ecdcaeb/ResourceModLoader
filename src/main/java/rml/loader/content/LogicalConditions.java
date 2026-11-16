package rml.loader.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;

import java.util.Objects;
import java.util.Random;

/** Native loot conditions shared by compiled predicates, pools, entries and functions. */
public final class LogicalConditions {
    private static boolean registered;

    private LogicalConditions() { }

    /** Called explicitly at preInit, before any compiled content is decoded. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        LootConditionManager.registerCondition(new AllOfSerializer());
        LootConditionManager.registerCondition(new AnyOfSerializer());
        LootConditionManager.registerCondition(new InvertedSerializer());
        registered = true;
    }

    public static final class AllOf implements LootCondition {
        private final LootCondition[] terms;

        public AllOf(LootCondition[] terms) {
            this.terms = copyTerms(terms);
        }

        @Override
        public boolean testCondition(Random random, LootContext context) {
            return LootConditionManager.testAllConditions(terms, random, context);
        }
    }

    public static final class AnyOf implements LootCondition {
        private final LootCondition[] terms;

        public AnyOf(LootCondition[] terms) {
            this.terms = copyTerms(terms);
        }

        @Override
        public boolean testCondition(Random random, LootContext context) {
            for (LootCondition term : terms) {
                if (term.testCondition(random, context)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Inverted implements LootCondition {
        private final LootCondition term;

        public Inverted(LootCondition term) {
            this.term = Objects.requireNonNull(term, "term");
        }

        @Override
        public boolean testCondition(Random random, LootContext context) {
            return !term.testCondition(random, context);
        }
    }

    private static LootCondition[] copyTerms(LootCondition[] terms) {
        LootCondition[] copy = Objects.requireNonNull(terms, "terms").clone();
        for (LootCondition term : copy) {
            Objects.requireNonNull(term, "terms element");
        }
        return copy;
    }

    private static LootCondition[] readTerms(JsonObject object, JsonDeserializationContext context) {
        JsonElement value = object.get("terms");
        if (value == null || !value.isJsonArray()) {
            throw new JsonSyntaxException("Logical condition requires a 'terms' array");
        }
        JsonArray array = value.getAsJsonArray();
        LootCondition[] terms = new LootCondition[array.size()];
        for (int i = 0; i < terms.length; i++) {
            terms[i] = readCondition(array.get(i), "terms[" + i + "]", context);
        }
        return terms;
    }

    private static LootCondition readCondition(JsonElement value, String field,
                                               JsonDeserializationContext context) {
        if (value == null || !value.isJsonObject()) {
            throw new JsonSyntaxException("Logical condition requires an object at '" + field + "'");
        }
        LootCondition condition = context.deserialize(value, LootCondition.class);
        if (condition == null) {
            throw new JsonSyntaxException("Native decoder returned null for '" + field + "'");
        }
        return condition;
    }

    private static final class AllOfSerializer extends LootCondition.Serializer<AllOf> {
        private AllOfSerializer() {
            super(new ResourceLocation("rml", "all_of"), AllOf.class);
        }

        @Override
        public void serialize(JsonObject object, AllOf condition, JsonSerializationContext context) {
            object.add("terms", context.serialize(condition.terms, LootCondition[].class));
        }

        @Override
        public AllOf deserialize(JsonObject object, JsonDeserializationContext context) {
            return new AllOf(readTerms(object, context));
        }
    }

    private static final class AnyOfSerializer extends LootCondition.Serializer<AnyOf> {
        private AnyOfSerializer() {
            super(new ResourceLocation("rml", "any_of"), AnyOf.class);
        }

        @Override
        public void serialize(JsonObject object, AnyOf condition, JsonSerializationContext context) {
            object.add("terms", context.serialize(condition.terms, LootCondition[].class));
        }

        @Override
        public AnyOf deserialize(JsonObject object, JsonDeserializationContext context) {
            return new AnyOf(readTerms(object, context));
        }
    }

    private static final class InvertedSerializer extends LootCondition.Serializer<Inverted> {
        private InvertedSerializer() {
            super(new ResourceLocation("rml", "inverted"), Inverted.class);
        }

        @Override
        public void serialize(JsonObject object, Inverted condition, JsonSerializationContext context) {
            object.add("term", context.serialize(condition.term, LootCondition.class));
        }

        @Override
        public Inverted deserialize(JsonObject object, JsonDeserializationContext context) {
            return new Inverted(readCondition(object.get("term"), "term", context));
        }
    }
}
