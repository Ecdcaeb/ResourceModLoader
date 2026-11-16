package rml.loader.content.gameplay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.IntProvider;
import rml.deserializer.value.LevelBasedValue;
import rml.loader.content.data.DataRules;
import java.util.ArrayList;
import java.util.List;

/** Domain decoders share the caller's manager, including all nested custom children. */
final class EnchantmentEffectDecoders {
    private EnchantmentEffectDecoders() { }
    static void register(DeserializerManager manager) {
        for (String operation : new String[]{"add", "multiply", "set", "all_of", "exponential", "remove_binomial"}) {
            manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", operation), NativeEnchantment.ValueEffect.class,
                    value -> value(manager, operation, value.getAsJsonObject())));
        }
        effect(manager, "ignite", json -> {
            LevelBasedValue seconds = level(manager, json, "duration", 1);
            return (context, target) -> target.setFire(Math.max(0, Math.min(Integer.MAX_VALUE / 20, (int) seconds.calculate(context.getLevel()))));
        });
        effect(manager, "exhaustion", json -> {
            LevelBasedValue amount = level(manager, json, "amount", 1);
            return (context, target) -> { if (target instanceof EntityPlayer) ((EntityPlayer) target).addExhaustion(Math.max(0, amount.calculate(context.getLevel()))); };
        });
        effect(manager, "impulse", json -> {
            Vec3d direction = manager.decode(Vec3d.class, json.get("direction"));
            LevelBasedValue strength = level(manager, json, "strength", 1);
            return (context, target) -> { double scale = strength.calculate(context.getLevel()); target.addVelocity(direction.x * scale, direction.y * scale, direction.z * scale); target.velocityChanged = true; };
        });
        effect(manager, "potion", json -> {
            ResourceLocation id = manager.decode(ResourceLocation.class, json.get("potion"));
            Potion potion = Potion.REGISTRY.getObject(id);
            if (potion == null) throw new IllegalArgumentException("Unknown potion: " + id);
            LevelBasedValue duration = level(manager, json, "duration", 20);
            LevelBasedValue amplifier = level(manager, json, "amplifier", 0);
            boolean ambient = json.has("ambient") && json.get("ambient").getAsBoolean();
            boolean particles = !json.has("particles") || json.get("particles").getAsBoolean();
            return (context, target) -> { if (target instanceof EntityLivingBase) ((EntityLivingBase) target).addPotionEffect(new PotionEffect(potion,
                    Math.max(0, (int) duration.calculate(context.getLevel())), Math.max(0, (int) amplifier.calculate(context.getLevel())), ambient, particles)); };
        });
        effect(manager, "durability", json -> {
            EntityEquipmentSlot slot = json.has("slot") ? EntityEquipmentSlot.fromString(json.get("slot").getAsString()) : null;
            LevelBasedValue amount = level(manager, json, "amount", 1);
            return (context, target) -> {
                if (!(target instanceof EntityLivingBase)) return;
                EntityLivingBase living = (EntityLivingBase) target;
                ItemStack stack = slot == null ? context.getTool() : living.getItemStackFromSlot(slot);
                int damage = (int) amount.calculate(context.getLevel());
                if (damage > 0) stack.damageItem(damage, living);
                else if (stack.isItemDamaged()) stack.setItemDamage(Math.max(0, stack.getItemDamage() + damage));
            };
        });
        effect(manager, "sound", json -> {
            ResourceLocation id = manager.decode(ResourceLocation.class, json.get("sound"));
            SoundEvent sound = SoundEvent.REGISTRY.getObject(id);
            if (sound == null) throw new IllegalArgumentException("Unknown sound: " + id);
            SoundCategory category = SoundCategory.valueOf(NativeEnchantment.text(json, "category", "players").toUpperCase(java.util.Locale.ROOT));
            LevelBasedValue volume = level(manager, json, "volume", 1), pitch = level(manager, json, "pitch", 1);
            return (context, target) -> target.world.playSound(null, target.posX, target.posY, target.posZ, sound, category,
                    volume.calculate(context.getLevel()), pitch.calculate(context.getLevel()));
        });
        effect(manager, "particles", json -> {
            EnumParticleTypes particle = EnumParticleTypes.getByName(json.get("particle").getAsString());
            if (particle == null) throw new IllegalArgumentException("Unknown native particle: " + json.get("particle"));
            IntProvider count = json.has("count") ? manager.decode(IntProvider.class, json.get("count")) : new IntProvider(1, 1, random -> 1);
            if (count.getMinValue() < 0) throw new IllegalArgumentException("Particle count must be nonnegative");
            Vec3d spread = json.has("spread") ? manager.decode(Vec3d.class, json.get("spread")) : Vec3d.ZERO;
            double speed = json.has("speed") ? manager.decode(Double.class, json.get("speed")) : 0;
            int[] arguments = json.has("arguments") ? manager.decode(int[].class, json.get("arguments")) : new int[0];
            if (arguments.length != particle.getArgumentCount()) throw new IllegalArgumentException("Incorrect particle argument count");
            return (context, target) -> { if (context.getWorld() != null) context.getWorld().spawnParticle(particle, target.posX, target.posY, target.posZ,
                    count.sample(context.getRandom()), spread.x, spread.y, spread.z, speed, arguments); };
        });
        effect(manager, "replace_block", json -> {
            IBlockState state = manager.decode(IBlockState.class, json.get("state"));
            net.minecraft.util.math.BlockPos offset = json.has("offset") ? manager.decode(net.minecraft.util.math.BlockPos.class, json.get("offset")) : net.minecraft.util.math.BlockPos.ORIGIN;
            rml.loader.deserialize.NativeTypeDecoders.BlockMatcher predicate = json.has("predicate") ? manager.decode(rml.loader.deserialize.NativeTypeDecoders.BlockMatcher.class, json.get("predicate")) : (world, pos, old) -> true;
            return (context, target) -> {
                if (context.getWorld() == null) return;
                net.minecraft.util.math.BlockPos pos = target.getPosition().add(offset);
                if (pos.getY() >= 0 && pos.getY() <= 255 && context.getWorld().isBlockLoaded(pos) && predicate.test(context.getWorld(), pos, context.getWorld().getBlockState(pos))) context.getWorld().setBlockState(pos, state, 3);
            };
        });
        effect(manager, "summon", json -> {
            ResourceLocation id = manager.decode(ResourceLocation.class, json.get("entity"));
            if (!EntityList.isRegistered(id)) throw new IllegalArgumentException("Unknown entity: " + id);
            IntProvider count = json.has("count") ? manager.decode(IntProvider.class, json.get("count")) : new IntProvider(1, 1, random -> 1);
            if (count.getMinValue() < 0) throw new IllegalArgumentException("Summon count must be nonnegative");
            return (context, target) -> {
                if (context.getWorld() == null) return;
                int total = count.sample(context.getRandom());
                for (int i = 0; i < total; i++) {
                    Entity spawned = EntityList.createEntityByIDFromName(id, context.getWorld());
                    if (spawned == null) throw new IllegalStateException("Entity factory failed for " + id);
                    spawned.setLocationAndAngles(target.posX, target.posY, target.posZ, target.rotationYaw, 0);
                    if (spawned instanceof net.minecraft.entity.EntityLiving) ((net.minecraft.entity.EntityLiving) spawned).onInitialSpawn(context.getWorld().getDifficultyForLocation(target.getPosition()), null);
                    context.getWorld().spawnEntity(spawned);
                }
            };
        });
    }
    private static NativeEnchantment.ValueEffect value(DeserializerManager manager, String operation, JsonObject json) throws JsonDeserializeException {
        GameplayFields.only(json, "all_of".equals(operation) ? new String[]{"effects"} : new String[]{"amount"});
        if (!json.has("all_of".equals(operation) ? "effects" : "amount")) throw new IllegalArgumentException("Value effect requires " + ("all_of".equals(operation) ? "effects" : "amount"));
        if ("all_of".equals(operation)) {
            List<NativeEnchantment.ValueEffect> effects = new ArrayList<>();
            for (JsonElement child : json.getAsJsonArray("effects")) effects.add(manager.decode(NativeEnchantment.ValueEffect.class, child));
            return (amount, context) -> { for (NativeEnchantment.ValueEffect effect : effects) if (effect.applies(context)) amount = effect.apply(amount, context); return amount; };
        }
        LevelBasedValue amount = level(manager, json, "amount", 0);
        switch (operation) {
            case "add": return (input, context) -> input + amount.calculate(context.getLevel());
            case "multiply": return (input, context) -> input * amount.calculate(context.getLevel());
            case "set": return (input, context) -> amount.calculate(context.getLevel());
            case "exponential": return (input, context) -> (float) Math.pow(input, amount.calculate(context.getLevel()));
            case "remove_binomial": return (input, context) -> {
                float chance = amount.calculate(context.getLevel());
                if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Binomial probability must be in [0,1]");
                int removed = 0;
                for (int i = 0, trials = Math.max(0, (int) input); i < trials; i++) if (context.getRandom().nextFloat() < chance) removed++;
                return input - removed;
            };
            default: throw new IllegalArgumentException("Unknown value effect: " + operation);
        }
    }
    private static LevelBasedValue level(DeserializerManager manager, JsonObject json, String key, float fallback) throws JsonDeserializeException {
        LevelBasedValue decoded = json.has(key) ? manager.decode(LevelBasedValue.class, json.get(key)) : level -> fallback;
        return level -> {
            float value = decoded.calculate(level);
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite enchantment effect " + key);
            return value;
        };
    }
    @FunctionalInterface private interface EffectDecoder { DataRules.Effect decode(JsonObject json) throws JsonDeserializeException; }
    private static void effect(DeserializerManager manager, String name, EffectDecoder decoder) {
        final String[] fields;
        switch (name) {
            case "ignite": fields = new String[]{"duration"}; break;
            case "exhaustion": fields = new String[]{"amount"}; break;
            case "impulse": fields = new String[]{"direction", "strength"}; break;
            case "potion": fields = new String[]{"potion", "duration", "amplifier", "ambient", "particles"}; break;
            case "durability": fields = new String[]{"slot", "amount"}; break;
            case "sound": fields = new String[]{"sound", "category", "volume", "pitch"}; break;
            case "particles": fields = new String[]{"particle", "count", "spread", "speed", "arguments"}; break;
            case "replace_block": fields = new String[]{"state", "offset", "predicate"}; break;
            case "summon": fields = new String[]{"entity", "count"}; break;
            default: throw new IllegalArgumentException("Missing effect field contract: " + name);
        }
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", name), DataRules.Effect.class, value -> {
            GameplayFields.only(value.getAsJsonObject(), fields);
            return decoder.decode(value.getAsJsonObject());
        }));
    }
}
