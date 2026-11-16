package rml.loader.content.worldgen;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Retains successful placements and rejected candidates across chunk and world reloads. */
public final class WorldgenStructureData extends WorldSavedData {
    private static final String NAME = "rml_worldgen_structures";
    final Map<ResourceLocation, Set<Long>> processed = new HashMap<>();
    final List<RmlChunkGenerator.StructureBounds> bounds = new ArrayList<>();

    public WorldgenStructureData(String name) { super(name); }
    static WorldgenStructureData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        WorldgenStructureData data = (WorldgenStructureData)storage.getOrLoadData(WorldgenStructureData.class, NAME);
        if (data == null) { data = new WorldgenStructureData(NAME); storage.setData(NAME, data); }
        return data;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        processed.clear(); bounds.clear();
        NBTTagCompound sets = tag.getCompoundTag("sets");
        for (String name : sets.getKeySet()) {
            NBTTagList chunks = sets.getTagList(name, 4);
            Set<Long> keys = new HashSet<>();
            for (int i = 0; i < chunks.tagCount(); i++) keys.add(((NBTTagLong)chunks.get(i)).getLong());
            processed.put(new ResourceLocation(name), keys);
        }
        NBTTagList boxes = tag.getTagList("bounds", 10);
        for (int i = 0; i < boxes.tagCount(); i++) {
            NBTTagCompound box = boxes.getCompoundTagAt(i);
            int[] coordinates = box.getIntArray("box");
            if (coordinates.length != 6) throw new IllegalArgumentException("Invalid saved worldgen bounds");
            bounds.add(new RmlChunkGenerator.StructureBounds(new ResourceLocation(box.getString("id")), coordinates[0], coordinates[1], coordinates[2], coordinates[3], coordinates[4], coordinates[5]));
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagCompound sets = new NBTTagCompound();
        for (Map.Entry<ResourceLocation, Set<Long>> entry : processed.entrySet()) {
            NBTTagList chunks = new NBTTagList();
            for (long key : entry.getValue()) chunks.appendTag(new NBTTagLong(key));
            sets.setTag(entry.getKey().toString(), chunks);
        }
        tag.setTag("sets", sets);
        NBTTagList boxes = new NBTTagList();
        for (RmlChunkGenerator.StructureBounds bound : bounds) {
            NBTTagCompound box = new NBTTagCompound();
            box.setString("id", bound.id.toString());
            box.setIntArray("box", new int[]{bound.minX, bound.minY, bound.minZ, bound.maxX, bound.maxY, bound.maxZ});
            boxes.appendTag(box);
        }
        tag.setTag("bounds", boxes);
        return tag;
    }
}
