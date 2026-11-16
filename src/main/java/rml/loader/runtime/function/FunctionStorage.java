package rml.loader.runtime.function;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Persistent namespaced function storage with dotted compound and indexed list paths. */
public final class FunctionStorage extends WorldSavedData {
    private static final String KEY = "rml_function_storage";
    private final NBTTagCompound values = new NBTTagCompound();

    public FunctionStorage() { this(KEY); }

    /** MapStorage uses the persisted data name when reopening a world. */
    public FunctionStorage(String name) { super(name); }

    public static FunctionStorage get(WorldServer world) {
        WorldServer storageWorld = world.getMinecraftServer().getWorld(0);
        MapStorage storage = storageWorld.getMapStorage();
        FunctionStorage data = (FunctionStorage) storage.getOrLoadData(FunctionStorage.class, KEY);
        if (data == null) {
            data = new FunctionStorage();
            storage.setData(KEY, data);
        }
        return data;
    }

    public NBTBase get(ResourceLocation id, String path) {
        return get(id, FunctionDataPath.compile(path));
    }

    public NBTBase get(ResourceLocation id, FunctionDataPath.Compiled path) {
        if (!values.hasKey(id.getNamespace())) return null;
        NBTTagCompound namespace = values.getCompoundTag(id.getNamespace());
        if (!namespace.hasKey(id.getPath())) return null;
        return path.get(namespace.getCompoundTag(id.getPath()));
    }

    public void set(ResourceLocation id, String path, NBTBase value) {
        FunctionDataPath.set(namespace(id), path, value.copy());
        markDirty();
    }
    public void mergeRoot(ResourceLocation id, NBTTagCompound incoming) {
        namespace(id).merge((NBTTagCompound) incoming.copy());
        markDirty();
    }

    public boolean remove(ResourceLocation id, String path) {
        if (!values.hasKey(id.getNamespace())) return false;
        NBTTagCompound namespace = values.getCompoundTag(id.getNamespace());
        if (!namespace.hasKey(id.getPath())) return false;
        boolean changed = FunctionDataPath.remove(namespace.getCompoundTag(id.getPath()), path);
        if (changed) markDirty();
        return changed;
    }

    private NBTTagCompound namespace(ResourceLocation id) {
        NBTTagCompound namespaces = values.getCompoundTag(id.getNamespace());
        values.setTag(id.getNamespace(), namespaces);
        NBTTagCompound root = namespaces.getCompoundTag(id.getPath());
        namespaces.setTag(id.getPath(), root);
        return root;
    }

    @Override public void readFromNBT(NBTTagCompound nbt) {
        values.merge(nbt.getCompoundTag("values"));
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("values", values.copy());
        return nbt;
    }
}
