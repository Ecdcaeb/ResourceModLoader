package rml.loader.content.presentation;

import com.google.gson.JsonElement;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.loader.content.data.DataRules;
import rml.loader.deserialize.NativeTypeDecoders;
import java.util.ArrayList;
import java.util.List;

/** Resolves registry-dependent children once, after native registries have finished. */
final class PresentationDeferredChildren {
    private final List<Pending<?>> pending = new ArrayList<>();
    private boolean ready;

    DataRules.Test condition(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (ready) return manager.decode(DataRules.Test.class, value);
        Pending<DataRules.Test> child = new Pending<>(manager, DataRules.Test.class, value);
        pending.add(child);
        return context -> child.get().test(context);
    }

    NativeTypeDecoders.ItemMatcher ingredient(DeserializerManager manager, JsonElement value) throws JsonDeserializeException {
        if (ready) return manager.decode(NativeTypeDecoders.ItemMatcher.class, value);
        Pending<NativeTypeDecoders.ItemMatcher> child = new Pending<>(manager, NativeTypeDecoders.ItemMatcher.class, value);
        pending.add(child);
        return (stack, context) -> child.get().test(stack, context);
    }

    void resolve() throws JsonDeserializeException {
        if (ready) return;
        // A registered child decoder may itself compose another presentation value.
        for (int i = 0; i < pending.size(); i++) pending.get(i).resolve();
        pending.clear();
        ready = true;
    }

    private static final class Pending<T> {
        private final DeserializerManager manager;
        private final Class<T> type;
        private JsonElement source;
        private T value;
        Pending(DeserializerManager manager, Class<T> type, JsonElement source) {
            this.manager = manager; this.type = type; this.source = source;
        }
        void resolve() throws JsonDeserializeException {
            if (source == null) return;
            value = manager.decode(type, source);
            if (value == null) throw new JsonDeserializeException(source, "Presentation child decoder returned null for " + type.getName());
            source = null;
        }
        T get() {
            if (value == null) throw new IllegalStateException("PresentationModule.initializeAfterRegistries must resolve " + type.getName() + " before use");
            return value;
        }
    }
}
