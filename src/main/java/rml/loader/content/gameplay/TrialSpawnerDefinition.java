package rml.loader.content.gameplay;

import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;

/** Parsed wave and reward settings used by placed trial spawners. */
public final class TrialSpawnerDefinition {
    public final ResourceLocation id;
    public final ResourceLocation entity;
    public final int spawnCount;
    public final int interval;
    public final int cooldown;
    public final int activationRange;
    public final int spawnRange;
    public final int maxActive;
    public final ItemStack reward;

    private TrialSpawnerDefinition(ResourceLocation id, ResourceLocation entity, int spawnCount, int interval,
                                   int cooldown, int activationRange, int spawnRange, int maxActive, ItemStack reward) {
        this.id = id;
        this.entity = entity;
        this.spawnCount = spawnCount;
        this.interval = interval;
        this.cooldown = cooldown;
        this.activationRange = activationRange;
        this.spawnRange = spawnRange;
        this.maxActive = maxActive;
        this.reward = reward;
    }

    public static TrialSpawnerDefinition decode(DeserializerManager manager, ResourceLocation id, JsonObject json)
            throws rml.deserializer.JsonDeserializeException {
        ResourceLocation entity = manager.decode(ResourceLocation.class, json.get("entity"));
        int spawnCount = integer(json, "spawn_count", 6);
        int interval = integer(json, "spawn_interval", 40);
        int cooldown = integer(json, "cooldown", 600);
        int activationRange = integer(json, "activation_range", 14);
        int spawnRange = integer(json, "spawn_range", 4);
        int maxActive = integer(json, "max_active", 4);
        ItemStack reward = json.has("reward") ? GameplayStackDecoder.decode(manager, json.get("reward")) : ItemStack.EMPTY;
        if (entity == null) throw new IllegalArgumentException("trial spawner requires an entity id");
        if (spawnCount < 1 || interval < 1 || cooldown < 1 || activationRange < 1 || spawnRange < 1 || maxActive < 1) {
            throw new IllegalArgumentException("trial spawner counts, ranges, and times must be positive");
        }
        return new TrialSpawnerDefinition(id, entity, spawnCount, interval, cooldown, activationRange, spawnRange, maxActive, reward.copy());
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
}