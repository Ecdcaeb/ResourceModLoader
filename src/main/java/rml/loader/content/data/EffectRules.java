package rml.loader.content.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.WeightedList;

/** Effect trees shared by the typed registry and the direct-JSON facade. */
final class EffectRules {
    private EffectRules() { }

    static DataRules.Effect decode(DataRules rules, DeserializerManager decoders, JsonElement definition) {
        JsonObject object = DataRules.requireObject(definition, "effect");
        String type = DataRules.string(object, "type", "");
        RuleFields.effect(type, object);
        switch (type) {
            case "rml:noop":
                return (context, target) -> { };
            case "rml:sequence": {
                JsonArray definitions = DataRules.required(object, "effects").getAsJsonArray();
                DataRules.Effect[] sequence = new DataRules.Effect[definitions.size()];
                for (int index = 0; index < sequence.length; index++) sequence[index] = rules.effect(definitions.get(index));
                return (context, target) -> { for (DataRules.Effect effect : sequence) effect.apply(context, target); };
            }
            case "rml:conditional": {
                DataRules.Test condition = rules.predicate(DataRules.required(object, "condition"));
                DataRules.Effect whenTrue = rules.effect(DataRules.required(object, "on_true"));
                DataRules.Effect whenFalse = object.has("on_false") ? rules.effect(object.get("on_false")) : null;
                return (context, target) -> {
                    DataRules.Effect selected = condition.test(context) ? whenTrue : whenFalse;
                    if (selected != null) selected.apply(context, target);
                };
            }
            case "rml:weighted": {
                if (decoders == null) throw DataRules.invalid("Weighted effects require the shared decoder registry");
                final WeightedList<DataRules.Effect> effects;
                try {
                    effects = decoders.decode(new TypeToken<WeightedList<DataRules.Effect>>() { }.getType(), DataRules.required(object, "distribution"));
                } catch (JsonDeserializeException error) {
                    throw new IllegalArgumentException("Cannot decode weighted effects", error);
                }
                return (context, target) -> effects.sample(context.getRandom()).apply(context, target);
            }
            case "rml:run_function": {
                final ResourceLocation id = DataRules.resource(object, "function");
                return (context, target) -> rules.invoke(id, target == null ? context : context.withEntity(target));
            }
            case "rml:damage": {
                final DataRules.NumberValue amount = rules.number(DataRules.required(object, "amount"));
                return (context, target) -> {
                    if (target != null) target.attackEntityFrom(DamageSource.GENERIC, (float) Math.max(0, amount.evaluate(context)));
                };
            }
            case "rml:heal": {
                final DataRules.NumberValue amount = rules.number(DataRules.required(object, "amount"));
                return (context, target) -> {
                    if (target instanceof EntityLivingBase) ((EntityLivingBase) target).heal((float) Math.max(0, amount.evaluate(context)));
                };
            }
            default: throw DataRules.invalid("Unsupported effect type '" + type + "'");
        }
    }
}
