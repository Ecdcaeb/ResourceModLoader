package rml.loader.content.gameplay;

import com.google.gson.JsonObject;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.LevelBasedValue;
import rml.loader.content.data.DataContext;
import rml.loader.content.data.DataRules;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Native transient attribute modifiers, reconciled against actual equipment and predicate eligibility. */
public final class EnchantmentAttribute {
    final String id, attribute;
    final int operation;
    final LevelBasedValue amount;
    final DataRules.Test requirements;
    private EnchantmentAttribute(String id, String attribute, int operation, LevelBasedValue amount, DataRules.Test requirements) {
        this.id = id; this.attribute = attribute; this.operation = operation; this.amount = amount; this.requirements = requirements;
    }
    public static EnchantmentAttribute decode(DeserializerManager manager, JsonObject json) throws JsonDeserializeException {
        GameplayFields.only(json, "id", "attribute", "operation", "amount", "requirements");
        String id = manager.decode(String.class, json.get("id"));
        String attribute = manager.decode(String.class, json.get("attribute"));
        String operation = NativeEnchantment.text(json, "operation", "add");
        int nativeOperation;
        switch (operation) {
            case "add": nativeOperation = 0; break;
            case "multiply_base": nativeOperation = 1; break;
            case "multiply_total": nativeOperation = 2; break;
            default: throw new IllegalArgumentException("Unknown attribute operation: " + operation);
        }
        if (id.isEmpty() || attribute.isEmpty()) throw new IllegalArgumentException("Attribute id and native name are required");
        return new EnchantmentAttribute(id, attribute, nativeOperation, manager.decode(LevelBasedValue.class, json.get("amount")),
                json.has("requirements") ? manager.decode(DataRules.Test.class, json.get("requirements")) : context -> true);
    }
    UUID uuid(ResourceLocation enchantment, EntityEquipmentSlot slot) {
        return UUID.nameUUIDFromBytes(("rml:enchantment/" + enchantment + "/" + id + "/" + slot.getName()).getBytes(StandardCharsets.UTF_8));
    }
    void reconcile(net.minecraft.entity.ai.attributes.AbstractAttributeMap attributes, UUID uuid, DataContext context, boolean equipped) {
        IAttributeInstance instance = attributes.getAttributeInstanceByName(attribute);
        if (instance == null) return; // Different native entity types expose different registered attributes.
        AttributeModifier current = instance.getModifier(uuid);
        if (!equipped || !requirements.test(context)) {
            if (current != null) instance.removeModifier(uuid);
            return;
        }
        double value = amount.calculate(context.getLevel());
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite attribute amount: " + id);
        if (current != null && current.getAmount() == value && current.getOperation() == operation) return;
        if (current != null) instance.removeModifier(uuid);
        instance.applyModifier(new AttributeModifier(uuid, "rml.enchantment." + id, value, operation).setSaved(false));
    }
}
