package rml.loader.content.gameplay;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Overworld-owned wave ledger receives deaths even while a spawner's dimension or chunk is unloaded. */
public final class TrialWaveData extends WorldSavedData {
    private static final String NAME = "rml_trial_waves";
    private final Map<UUID, Set<UUID>> deaths = new HashMap<>();
    public TrialWaveData() { super(NAME); }
    public TrialWaveData(String name) { super(name); }
    static TrialWaveData get(MinecraftServer server) {
        MapStorage storage = server.getWorld(0).getMapStorage();
        TrialWaveData data = (TrialWaveData) storage.getOrLoadData(TrialWaveData.class, NAME);
        if (data == null) { data = new TrialWaveData(); storage.setData(NAME, data); }
        return data;
    }
    void open(UUID wave) {
        if (!deaths.containsKey(wave)) { deaths.put(wave, new HashSet<>()); markDirty(); }
    }
    void record(UUID wave, UUID mob) {
        Set<UUID> pending = deaths.get(wave);
        if (pending != null && pending.add(mob)) markDirty();
    }
    boolean drain(UUID wave, Set<UUID> live) {
        Set<UUID> pending = deaths.get(wave);
        if (pending == null || pending.isEmpty()) return false;
        boolean changed = live.removeAll(pending);
        pending.clear();
        markDirty();
        return changed;
    }
    void close(UUID wave) { if (wave != null && deaths.remove(wave) != null) markDirty(); }
    @Override public void readFromNBT(NBTTagCompound tag) {
        deaths.clear();
        NBTTagList waves = tag.getTagList("Waves", 10);
        for (int i = 0; i < waves.tagCount(); i++) {
            NBTTagCompound wave = waves.getCompoundTagAt(i);
            Set<UUID> pending = new HashSet<>();
            NBTTagList mobs = wave.getTagList("Deaths", 10);
            for (int j = 0; j < mobs.tagCount(); j++) pending.add(mobs.getCompoundTagAt(j).getUniqueId("Id"));
            deaths.put(wave.getUniqueId("Id"), pending);
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList waves = new NBTTagList();
        for (Map.Entry<UUID, Set<UUID>> entry : deaths.entrySet()) {
            NBTTagCompound wave = new NBTTagCompound();
            wave.setUniqueId("Id", entry.getKey());
            NBTTagList mobs = new NBTTagList();
            for (UUID id : entry.getValue()) { NBTTagCompound mob = new NBTTagCompound(); mob.setUniqueId("Id", id); mobs.appendTag(mob); }
            wave.setTag("Deaths", mobs);
            waves.appendTag(wave);
        }
        tag.setTag("Waves", waves);
        return tag;
    }
}
