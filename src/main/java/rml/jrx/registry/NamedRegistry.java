package rml.jrx.registry;

import net.minecraft.util.ResourceLocation;
import rml.internal.net.minecraftforge.common.util.LazyOptional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/27 15:41
 **/
public class NamedRegistry<T> implements Iterable<Map.Entry<ResourceLocation, LazyOptional<T>>>{
    private final HashMap<ResourceLocation, LazyOptional<T>> registry = new HashMap<>();
    private final HashSet<Listener<T>> listeners = new HashSet<>();

    public NamedRegistry(){}

    @SafeVarargs
    public NamedRegistry(Listener<T>... listeners){
        this.listeners.addAll(Arrays.asList(listeners));
    }

    public void addListener(Listener<T> listener){listeners.add(listener);}
    public void register(ResourceLocation name, LazyOptional<T> obj){
        for(Listener<T> listener : listeners) listener.register(registry, name, obj);
        registry.put(name, obj);
    }

    public void register(ResourceLocation name, T obj){
        register(name, LazyOptional.of(obj));
    }

    public void register(ResourceLocation name, Supplier<T> obj){
        register(name, LazyOptional.of(obj));
    }

    public T get(ResourceLocation name){
        return registry.get(name).orElse(null);
    }

    public LazyOptional<T> getOptional(ResourceLocation name){
        return registry.get(name);
    }

    public Collection<LazyOptional<T>> values(){
        return registry.values();
    }

    public Collection<ResourceLocation> keys(){
        return registry.keySet();
    }

    @Override
    public Iterator<Map.Entry<ResourceLocation, LazyOptional<T>>> iterator() {
        return registry.entrySet().iterator();
    }

    public interface Listener<T>{
        void register(HashMap<ResourceLocation, LazyOptional<T>> registry, ResourceLocation name, LazyOptional<T> obj);
    }

}
