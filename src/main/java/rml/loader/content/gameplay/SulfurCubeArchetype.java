package rml.loader.content.gameplay;

import com.google.gson.JsonObject;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;

public final class SulfurCubeArchetype {
    public final ResourceLocation id;
    public final int size;
    public final float contactDamage;
    public final ResourceLocation lootTable;
    public final PotionEffect contactEffect;

    private SulfurCubeArchetype(ResourceLocation id, int size, float contactDamage, ResourceLocation lootTable, PotionEffect contactEffect) {
        this.id = id;
        this.size = size;
        this.contactDamage = contactDamage;
        this.lootTable = lootTable;
        this.contactEffect = contactEffect;
    }

    public static SulfurCubeArchetype decode(DeserializerManager manager, ResourceLocation id, JsonObject json)
            throws rml.deserializer.JsonDeserializeException {
        int size = json.has("size") ? json.get("size").getAsInt() : 2;
        float damage = json.has("contact_damage") ? json.get("contact_damage").getAsFloat() : size;
        ResourceLocation loot = json.has("loot_table") ? manager.decode(ResourceLocation.class, json.get("loot_table")) : new ResourceLocation("minecraft", "entities/slime");
        PotionEffect effect = null;
        if (json.has("contact_effect")) {
            JsonObject effectJson = json.getAsJsonObject("contact_effect");
            ResourceLocation potionId = manager.decode(ResourceLocation.class, effectJson.get("potion"));
            Potion potion = Potion.REGISTRY.getObject(potionId);
            if (potion == null) throw new IllegalArgumentException("Unknown potion effect: " + potionId);
            int duration = effectJson.has("duration") ? effectJson.get("duration").getAsInt() : 100;
            int amplifier = effectJson.has("amplifier") ? effectJson.get("amplifier").getAsInt() : 0;
            if (duration < 1 || amplifier < 0) throw new IllegalArgumentException("contact effect duration must be positive and amplifier nonnegative");
            effect = new PotionEffect(potion, duration, amplifier);
        }
        if (size < 1 || size > 16 || damage < 0 || Float.isNaN(damage) || Float.isInfinite(damage)) {
            throw new IllegalArgumentException("sulfur cube size must be 1..16 and contact_damage must be finite and nonnegative");
        }
        return new SulfurCubeArchetype(id, size, damage, loot, effect);
    }
}