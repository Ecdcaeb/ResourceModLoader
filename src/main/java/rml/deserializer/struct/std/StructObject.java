package rml.deserializer.struct.std;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StructObject extends StructElement implements Map<String, StructElement>{
    protected final Map<String, StructElement> map;

    public StructObject(){
        this.map = new HashMap<>();
    }

    public StructObject(Map<String, StructElement> map) {
        this.map = map;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public StructElement get(Object key) {
        return map.get(key);
    }

    @Override
    public @Nullable StructElement put(String key, StructElement value) {
        return map.put(key, value);
    }

    @Override
    public StructElement remove(Object key) {
        return map.remove(key);
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ? extends StructElement> m) {
        map.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public @NotNull Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public @NotNull Collection<StructElement> values() {
        return map.values();
    }

    @Override
    public @NotNull Set<Entry<String, StructElement>> entrySet() {
        return map.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StructObject that = (StructObject) o;
        return Objects.equals(map, that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(map);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        int i = 1;
        int size = this.size();
        for (Map.Entry<String, StructElement> entry : this.entrySet()) {
            builder.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue().toString());
            if (i < size) builder.append(',').append('\n');
            else builder.append('\n');
        }
        builder.append('}');

        return builder.toString();
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public StructObject getAsObject() {
        return this;
    }
}
