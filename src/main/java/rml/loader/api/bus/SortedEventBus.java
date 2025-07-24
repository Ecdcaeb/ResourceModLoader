package rml.loader.api.bus;

import rml.loader.api.utils.ObjectHelper;

import java.util.Iterator;

public class SortedEventBus<EVENT> implements EventBus<EVENT>{

    public enum Priority{
        HIGHEST(0),
        HIGH(1),
        NORMAL(2),
        LOW(3),
        LOWEST(4);

        private final int idx;
        Priority(int idx){
            this.idx = idx;
        }

        public int getIdx() {
            return idx;
        }
    }

    protected final DefaultEventBus<? super EVENT>[] bus;

    public SortedEventBus() {
        this.bus = ObjectHelper.static_cast(new DefaultEventBus[]{
                new DefaultEventBus<>(),
                new DefaultEventBus<>(),
                new DefaultEventBus<>(),
                new DefaultEventBus<>(),
                new DefaultEventBus<>()
        });
    }

    public SortedEventBus(SortedEventBus<? super EVENT> sortedEventBus) {
        this.bus = ObjectHelper.static_cast(new DefaultEventBus[]{
                new DefaultEventBus<>(sortedEventBus.bus[0]),
                new DefaultEventBus<>(sortedEventBus.bus[1]),
                new DefaultEventBus<>(sortedEventBus.bus[2]),
                new DefaultEventBus<>(sortedEventBus.bus[3]),
                new DefaultEventBus<>(sortedEventBus.bus[4])
        });
    }

    public SortedEventBus(DefaultEventBus<? super EVENT> parentIn) {
        this.bus = ObjectHelper.static_cast(new DefaultEventBus[]{
                new DefaultEventBus<>(),
                new DefaultEventBus<>(),
                new DefaultEventBus<>(parentIn),
                new DefaultEventBus<>(),
                new DefaultEventBus<>()
        });
    }

    @Override
    public <T extends EVENT> T post(T evt) {
        this.bus[0].post(evt);
        this.bus[1].post(evt);
        this.bus[2].post(evt);
        this.bus[3].post(evt);
        this.bus[4].post(evt);

        return evt;
    }

    public DefaultEventBus.IEventHandler<EVENT> register(Priority priority, DefaultEventBus.IEventHandler<EVENT> handler) {
        return ObjectHelper.static_cast(this.bus[priority.getIdx()].register(ObjectHelper.static_cast(handler)));
    }

    public DefaultEventBus.IEventHandler<EVENT> register(Priority priority, String label, DefaultEventBus.IEventHandler<EVENT> handler) {
        return ObjectHelper.static_cast(this.bus[priority.getIdx()].register(label, ObjectHelper.static_cast(handler)));
    }

    public <T> T unregister(T token) {
        for (DefaultEventBus<?> defaultEventBus : bus){
            defaultEventBus.unregister(token);
        }
        return token;
    }

    public static<T> SortedEventBus<T> of(){
        return new SortedEventBus<>();
    }

    public static<V, T extends V> SortedEventBus<T> of(DefaultEventBus<V> parent) {
        return new SortedEventBus<>(parent);
    }

    public static<V, T extends V> SortedEventBus<T> of(SortedEventBus<V> parent) {
        return new SortedEventBus<>(parent);
    }
}
