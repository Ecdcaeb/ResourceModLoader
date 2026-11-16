package rml.loader.content.time;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Persistent state for RML clocks and timeline cursors in one native world. */
public final class TimeWorldData extends WorldSavedData {
    private static final String NAME = "rml_time_state";
    final Map<ResourceLocation, Clock> clocks = new HashMap<>();
    final Map<ResourceLocation, Timeline> timelines = new HashMap<>();

    TimeWorldData() { this(NAME); }

    /** MapStorage restores saved data through this native constructor contract. */
    public TimeWorldData(String name) { super(name); }

    static TimeWorldData get(WorldServer world) {
        MapStorage storage = world.getMapStorage();
        TimeWorldData data = (TimeWorldData) storage.getOrLoadData(TimeWorldData.class, NAME);
        if (data == null) {
            data = new TimeWorldData();
            storage.setData(NAME, data);
        }
        return data;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        clocks.clear();
        timelines.clear();
        NBTTagCompound clockTag = tag.getCompoundTag("clocks");
        for (String key : clockTag.getKeySet()) clocks.put(new ResourceLocation(key), Clock.read(clockTag.getCompoundTag(key)));
        NBTTagCompound timelineTag = tag.getCompoundTag("timelines");
        for (String key : timelineTag.getKeySet()) timelines.put(new ResourceLocation(key), Timeline.read(timelineTag.getCompoundTag(key)));
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagCompound clockTag = new NBTTagCompound();
        for (Map.Entry<ResourceLocation, Clock> entry : clocks.entrySet()) clockTag.setTag(entry.getKey().toString(), entry.getValue().write());
        NBTTagCompound timelineTag = new NBTTagCompound();
        for (Map.Entry<ResourceLocation, Timeline> entry : timelines.entrySet()) timelineTag.setTag(entry.getKey().toString(), entry.getValue().write());
        tag.setTag("clocks", clockTag);
        tag.setTag("timelines", timelineTag);
        return tag;
    }

    static final class Clock {
        long time;
        double rate = 1.0;
        double fraction;
        boolean paused;
        boolean configured;
        static Clock read(NBTTagCompound n) { Clock c = new Clock(); c.time=n.getLong("time"); c.rate=n.getDouble("rate"); c.fraction=n.getDouble("fraction"); c.paused=n.getBoolean("paused"); c.configured=n.getBoolean("configured"); return c; }
        NBTTagCompound write() { NBTTagCompound n=new NBTTagCompound(); n.setLong("time",time); n.setDouble("rate",rate); n.setDouble("fraction",fraction); n.setBoolean("paused",paused); n.setBoolean("configured",configured); return n; }
    }
    static final class Timeline {
        long previous;
        long lastClock;
        long cycle;
        int eventIndex;
        boolean completed;
        boolean started;
        static Timeline read(NBTTagCompound n) { Timeline t=new Timeline(); t.previous=n.getLong("previous"); t.lastClock=n.getLong("last_clock"); t.cycle=n.getLong("cycle"); t.eventIndex=n.getInteger("event_index"); t.completed=n.getBoolean("completed"); t.started=n.getBoolean("started"); return t; }
        NBTTagCompound write() { NBTTagCompound n=new NBTTagCompound(); n.setLong("previous",previous); n.setLong("last_clock",lastClock); n.setLong("cycle",cycle); n.setInteger("event_index",eventIndex); n.setBoolean("completed",completed); n.setBoolean("started",started); return n; }
    }
}
