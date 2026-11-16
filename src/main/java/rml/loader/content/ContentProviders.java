package rml.loader.content;

import rml.runtime.PreparedUnit;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Composition-time native capability registry. A declared type always has a real provider. */
public final class ContentProviders {
    private final Map<String, Function<CompiledResource, PreparedUnit>> runtime = new HashMap<>();
    private final Set<String> startup = new HashSet<>();

    public void registerRuntime(String type, Function<CompiledResource, PreparedUnit> provider) {
        if (startup.contains(type) || runtime.putIfAbsent(type, provider) != null) {
            throw new IllegalArgumentException("Duplicate content provider: " + type);
        }
    }

    public void registerStartup(String type) {
        if (runtime.containsKey(type) || !startup.add(type)) throw new IllegalArgumentException("Duplicate content provider: " + type);
    }

    public boolean isStartup(String type) { return startup.contains(type); }

    public PreparedUnit prepare(CompiledResource resource) {
        Function<CompiledResource, PreparedUnit> provider = runtime.get(resource.getType());
        if (provider == null) throw new IllegalArgumentException("No native content provider for " + resource.getType() + " at " + resource.getSource());
        return provider.apply(resource);
    }

    public Set<String> getTypes() {
        Set<String> types = new HashSet<>(startup);
        types.addAll(runtime.keySet());
        return Collections.unmodifiableSet(types);
    }
}
