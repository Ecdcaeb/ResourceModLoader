package rml.loader.content.presentation;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable-at-runtime lookup for per-entity sound routing. */
final class PresentationSoundResolver {
    private static Map<ResourceLocation, PresentationCodecs.EntitySounds> variants = Collections.emptyMap();
    private static Map<ResourceLocation, SoundEvent> events = Collections.emptyMap();
    private static Map<ResourceLocation, String> defaultTypes = Collections.emptyMap();
    private static Map<ResourceLocation, ResourceLocation> defaults = Collections.emptyMap();

    private PresentationSoundResolver() { }

    static synchronized void configure(Map<ResourceLocation, PresentationCodecs.EntitySounds> sounds,
                                       Map<ResourceLocation, SoundEvent> soundEvents,
                                       Map<ResourceLocation, ResourceLocation> defaultVariants,
                                       Map<ResourceLocation, String> variantTypes) {
        variants = Collections.unmodifiableMap(new HashMap<>(sounds));
        events = Collections.unmodifiableMap(new HashMap<>(soundEvents));
        defaults = Collections.unmodifiableMap(new HashMap<>(defaultVariants));
        defaultTypes = Collections.unmodifiableMap(new HashMap<>(variantTypes));
    }
    static void replace(PlaySoundAtEntityEvent event) {
        PresentationCodecs.EntitySounds soundSet = selected(event.getEntity());
        if (soundSet == null) return;
        SoundEvent current = event.getSound();
        String trigger = trigger(current == null ? null : current.getRegistryName());
        PresentationCodecs.SoundPlayback playback = trigger == null ? authoredPlayback(soundSet, current) : playback(soundSet, trigger);
        if (playback == null) return;
        SoundEvent replacement = events.get(playback.sound);
        if (replacement == null) return;
        event.setSound(replacement);
        event.setVolume(playback.volume);
        event.setPitch(playback.pitch);
    }
    static SoundEvent sound(Entity entity, String trigger) {
        PresentationCodecs.EntitySounds soundSet = selected(entity);
        PresentationCodecs.SoundPlayback playback = soundSet == null ? null : playback(soundSet, trigger);
        return playback == null ? null : events.get(playback.sound);
    }

    private static PresentationCodecs.EntitySounds selected(Entity entity) {
        String selected = entity.getEntityData().getString("rml_presentation_sound_variant");
        String type = entity.getEntityData().getString("rml_presentation_sound_variant_type");
        if (selected.isEmpty()) {
            selected = entity.getEntityData().getString("rml_presentation_variant");
            type = entity.getEntityData().getString("rml_presentation_variant_type");
        }
        ResourceLocation entityType = EntityList.getKey(entity);
        if (selected.isEmpty()) {
            ResourceLocation defaultId = defaults.get(entityType);
            type = defaultTypes.get(entityType);
            selected = defaultId == null ? "" : defaultId.toString();
        }
        if (selected.isEmpty() || type.isEmpty()) return null;
        return variants.get(PresentationIds.key(type, new ResourceLocation(selected)));
    }
    private static PresentationCodecs.SoundPlayback authoredPlayback(PresentationCodecs.EntitySounds sounds, SoundEvent event) {
        if (event == null || event.getRegistryName() == null) return null;
        ResourceLocation id = event.getRegistryName();
        if (sounds.ambient != null && sounds.ambient.sound.equals(id)) return sounds.ambient;
        if (sounds.hurt != null && sounds.hurt.sound.equals(id)) return sounds.hurt;
        if (sounds.death != null && sounds.death.sound.equals(id)) return sounds.death;
        return null;
    }

    private static PresentationCodecs.SoundPlayback playback(PresentationCodecs.EntitySounds sounds, String trigger) {
        switch (trigger) {
            case "ambient": return sounds.ambient;
            case "hurt": return sounds.hurt;
            case "death": return sounds.death;
            default: return null;
        }
    }

    private static String trigger(ResourceLocation sound) {
        if (sound == null) return null;
        String path = sound.getPath();
        if (path.endsWith(".hurt") || path.contains(".hurt.")) return "hurt";
        if (path.endsWith(".death") || path.contains(".death.")) return "death";
        if (path.endsWith(".ambient") || path.contains(".ambient.")) return "ambient";
        return null;
    }
}
