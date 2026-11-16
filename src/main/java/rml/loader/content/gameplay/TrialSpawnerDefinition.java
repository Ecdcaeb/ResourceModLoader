package rml.loader.content.gameplay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.IntProvider;
import rml.deserializer.value.LevelBasedValue;
import rml.loader.content.data.DataRules;

/** Compiled normal/ominous wave settings. Distributions are sampled when a wave starts. */
public final class TrialSpawnerDefinition {
    public final ResourceLocation id;
    public final int activationRange, spawnRange;
    public final Settings normal, ominous;
    public final DataRules.Test ominousCondition;
    private TrialSpawnerDefinition(ResourceLocation id, int activationRange, int spawnRange, Settings normal, Settings ominous, DataRules.Test ominousCondition) {
        this.id = id; this.activationRange = activationRange; this.spawnRange = spawnRange;
        this.normal = normal; this.ominous = ominous; this.ominousCondition = ominousCondition;
    }
    public static TrialSpawnerDefinition decode(DeserializerManager manager, ResourceLocation id, JsonObject json) throws JsonDeserializeException {
        GameplayFields.only(json, "normal", "ominous", "entity", "spawn_count", "spawn_interval", "cooldown", "activation_range", "spawn_range", "max_active", "reward",
                "additional_spawn_count", "additional_max_active", "reward_per_player", "reward_modifier", "on_start", "on_spawn", "on_complete");
        int range = json.has("activation_range") ? manager.decode(Integer.class, json.get("activation_range")) : 14;
        int spawnRange = json.has("spawn_range") ? manager.decode(Integer.class, json.get("spawn_range")) : 4;
        if (range < 1 || spawnRange < 1) throw new IllegalArgumentException("Trial ranges must be positive");
        JsonObject flat = new JsonObject();
        for (String field : Settings.FIELDS) if (json.has(field)) flat.add(field, json.get(field));
        if (json.has("normal") && flat.size() != 0) throw new IllegalArgumentException("Trial cannot mix normal settings with flat wave settings");
        Settings normal = manager.decode(Settings.class, json.has("normal") ? json.get("normal") : flat);
        Settings ominous = null;
        DataRules.Test condition = null;
        if (json.has("ominous")) {
            JsonObject options = json.getAsJsonObject("ominous");
            GameplayFields.only(options, "condition", "settings");
            condition = manager.decode(DataRules.Test.class, options.get("condition"));
            ominous = manager.decode(Settings.class, options.get("settings"));
        }
        return new TrialSpawnerDefinition(id, range, spawnRange, normal, ominous, condition);
    }

    public static final class Settings {
        private static final String[] FIELDS = {"entity", "spawn_count", "spawn_interval", "cooldown", "max_active", "reward", "additional_spawn_count", "additional_max_active", "reward_per_player", "reward_modifier", "on_start", "on_spawn", "on_complete"};
        public final ResourceLocation entity;
        public final IntProvider spawnCount, interval, cooldown, maxActive;
        public final LevelBasedValue additionalSpawnCount, additionalMaxActive;
        public final ItemStack reward;
        public final boolean rewardPerPlayer;
        public final DataRules.Modifier rewardModifier;
        public final DataRules.Effect onStart, onSpawn, onComplete;
        private Settings(ResourceLocation entity, IntProvider spawnCount, IntProvider interval, IntProvider cooldown, IntProvider maxActive,
                         LevelBasedValue additionalSpawnCount, LevelBasedValue additionalMaxActive, ItemStack reward, boolean rewardPerPlayer,
                         DataRules.Modifier rewardModifier, DataRules.Effect onStart, DataRules.Effect onSpawn, DataRules.Effect onComplete) {
            this.entity = entity; this.spawnCount = spawnCount; this.interval = interval; this.cooldown = cooldown; this.maxActive = maxActive;
            this.additionalSpawnCount = additionalSpawnCount; this.additionalMaxActive = additionalMaxActive; this.reward = reward;
            this.rewardPerPlayer = rewardPerPlayer; this.rewardModifier = rewardModifier; this.onStart = onStart; this.onSpawn = onSpawn; this.onComplete = onComplete;
        }
        public static Settings decode(DeserializerManager manager, JsonObject json) throws JsonDeserializeException {
            GameplayFields.only(json, FIELDS);
            ResourceLocation entity = manager.decode(ResourceLocation.class, json.get("entity"));
            Class<? extends net.minecraft.entity.Entity> entityClass = net.minecraft.entity.EntityList.getClassFromName(entity.toString());
            if (entityClass == null || !net.minecraft.entity.EntityLiving.class.isAssignableFrom(entityClass)) throw new IllegalArgumentException("Trial entity must be a registered living mob: " + entity);
            IntProvider count = integer(manager, json, "spawn_count", 6), interval = integer(manager, json, "spawn_interval", 40);
            IntProvider cooldown = integer(manager, json, "cooldown", 600), active = integer(manager, json, "max_active", 4);
            if (count.getMinValue() < 1 || interval.getMinValue() < 1 || cooldown.getMinValue() < 1 || active.getMinValue() < 1) throw new IllegalArgumentException("Trial counts and durations must be positive");
            LevelBasedValue additionalCount = json.has("additional_spawn_count") ? manager.decode(LevelBasedValue.class, json.get("additional_spawn_count")) : level -> 0;
            LevelBasedValue additionalActive = json.has("additional_max_active") ? manager.decode(LevelBasedValue.class, json.get("additional_max_active")) : level -> 0;
            ItemStack reward = json.has("reward") ? GameplayStackDecoder.decode(manager, json.get("reward")) : ItemStack.EMPTY;
            return new Settings(entity, count, interval, cooldown, active, additionalCount, additionalActive, reward.copy(),
                    json.has("reward_per_player") && manager.decode(Boolean.class, json.get("reward_per_player")),
                    json.has("reward_modifier") ? manager.decode(DataRules.Modifier.class, json.get("reward_modifier")) : (stack, context) -> stack,
                    effect(manager, json, "on_start"), effect(manager, json, "on_spawn"), effect(manager, json, "on_complete"));
        }
        public int target(int base, int participants) { return scaled(base, participants, additionalSpawnCount); }
        public int activeLimit(int base, int participants) { return scaled(base, participants, additionalMaxActive); }
        private static int scaled(int base, int participants, LevelBasedValue scaling) {
            float additional = scaling.calculate(Math.max(1, participants));
            if (!Float.isFinite(additional) || additional < 0 || base + (double) additional > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid participant scaling result");
            return base + (int) Math.floor(additional);
        }
        private static IntProvider integer(DeserializerManager manager, JsonObject json, String key, int fallback) throws JsonDeserializeException {
            return json.has(key) ? manager.decode(IntProvider.class, json.get(key)) : new IntProvider(fallback, fallback, random -> fallback);
        }
        private static DataRules.Effect effect(DeserializerManager manager, JsonObject json, String key) throws JsonDeserializeException {
            return json.has(key) ? manager.decode(DataRules.Effect.class, json.get(key)) : null;
        }
    }
}