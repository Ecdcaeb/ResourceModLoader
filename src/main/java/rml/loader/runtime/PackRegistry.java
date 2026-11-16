package rml.loader.runtime;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import rml.loader.api.mods.ContainerHolder;
import rml.loader.api.mods.module.Module;
import rml.loader.api.mods.module.ModuleType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Forge container catalog. Registration does not activate content. */
public final class PackRegistry {
    private final Map<String, ContainerHolder> packs = new TreeMap<>();
    private final Map<String, Module[]> pending = new TreeMap<>();
    private List<ContainerHolder> snapshot = Collections.emptyList();

    public ContainerHolder register(ContainerHolder pack) {
        Objects.requireNonNull(pack, "pack");
        String id = pack.getContainer().getModId();
        if (packs.containsKey(id)) {
            throw new IllegalArgumentException("RML pack is already registered: " + id);
        }
        packs.put(id, pack);
        snapshot = Collections.unmodifiableList(new ArrayList<>(packs.values()));
        return pack;
    }

    public void request(String modId, Module[] modules) {
        Objects.requireNonNull(modId, "modId");
        if (modId.isEmpty() || packs.containsKey(modId) || pending.containsKey(modId)) {
            throw new IllegalArgumentException("Invalid or duplicate RML pack request: " + modId);
        }
        pending.put(modId, modules == null ? null : modules.clone());
    }

    public List<ContainerHolder> all() {
        resolvePending();
        return snapshot;
    }

    public ContainerHolder find(String modId) {
        resolvePending();
        return packs.get(modId);
    }

    public List<ContainerHolder> withModule(ModuleType module) {
        Objects.requireNonNull(module, "module");
        List<ContainerHolder> selected = new ArrayList<>();
        for (ContainerHolder pack : all()) {
            if (pack.hasModule(module)) selected.add(pack);
        }
        return selected;
    }

    private void resolvePending() {
        if (pending.isEmpty()) return;
        Iterator<Map.Entry<String, Module[]>> requests = pending.entrySet().iterator();
        while (requests.hasNext()) {
            Map.Entry<String, Module[]> request = requests.next();
            ModContainer mod = Loader.instance().getIndexedModList().get(request.getKey());
            if (mod != null) {
                Module[] modules = request.getValue();
                register(modules == null ? new ContainerHolder(mod) : new ContainerHolder(mod, modules));
                requests.remove();
            }
        }
    }
}
