package rml.loader.content.data;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonParser;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.LootFunctionManager;
import rml.loader.content.LogicalConditions;

import java.util.Random;

/** Native loot adapters for the compiled RML expression trees. */
final class RuleSerializers {
    private static DataRules rules;
    private static boolean registered;

    private RuleSerializers() { }

    static synchronized void register(DataRules value) {
        if (registered) {
            if (rules != value) throw new IllegalStateException("Native rule serializers already bound to another DataRules instance");
            return;
        }
        rules = value;
        LogicalConditions.register();
        LootConditionManager.registerCondition(new ExpressionConditionSerializer());
        LootFunctionManager.registerFunction(new ExpressionFunctionSerializer());
        registered = true;
    }

    public static final class ExpressionCondition implements LootCondition {
        private final DataRules.Test test;
        private final JsonElement expression;

        ExpressionCondition(JsonElement expression) {
            this.expression = copy(expression);
            this.test = rules.predicate(expression);
        }

        @Override
        public boolean testCondition(Random random, LootContext context) {
            return test.test(DataContext.fromLoot(context, random));
        }
    }

    private static final class ExpressionConditionSerializer extends LootCondition.Serializer<ExpressionCondition> {
        ExpressionConditionSerializer() { super(new ResourceLocation("rml", "expression"), ExpressionCondition.class); }
        @Override public void serialize(JsonObject object, ExpressionCondition value, JsonSerializationContext context) {
            object.add("expression", copy(value.expression));
        }
        @Override public ExpressionCondition deserialize(JsonObject object, JsonDeserializationContext context) {
            JsonElement expression = object.get("expression");
            if (expression == null || !expression.isJsonObject()) throw new JsonSyntaxException("rml:expression condition requires an expression object");
            return new ExpressionCondition(expression);
        }
    }

    private static final class ExpressionFunction extends LootFunction {
        private final DataRules.Modifier modifier;
        private final JsonElement expression;
        ExpressionFunction(LootCondition[] conditions, JsonElement expression) {
            super(conditions);
            this.expression = copy(expression);
            this.modifier = rules.modifier(expression);
        }
        @Override public ItemStack apply(ItemStack stack, Random random, LootContext context) {
            return modifier.apply(stack, DataContext.fromLoot(context, random));
        }
    }

    private static final class ExpressionFunctionSerializer extends LootFunction.Serializer<ExpressionFunction> {
        ExpressionFunctionSerializer() { super(new ResourceLocation("rml", "expression"), ExpressionFunction.class); }
        @Override public void serialize(JsonObject object, ExpressionFunction value, JsonSerializationContext context) {
            object.add("expression", copy(value.expression));
        }
        @Override public ExpressionFunction deserialize(JsonObject object, JsonDeserializationContext context, LootCondition[] conditions) {
            JsonElement expression = object.get("expression");
            if (expression == null) throw new JsonSyntaxException("rml:expression function requires an expression");
            return new ExpressionFunction(conditions, expression);
        }
    }

    private static JsonElement copy(JsonElement value) {
        return new JsonParser().parse(value.toString());
    }
}
