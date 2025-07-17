package rml.loader.api.bus;

import java.util.LinkedList;
import java.util.List;

public class EventBus<EVENT> {
    protected final EventBus<? super EVENT> parent;
    protected final List<IEventHandler<EVENT>> handlers;

    protected EventBus(){
        this.handlers = new LinkedList<>();
        this.parent = null;
    }

    protected EventBus(EventBus<? super EVENT> parentIn) {
        this.parent = parentIn;
        this.handlers = new LinkedList<>();
    }

    public <T extends EVENT> T post(T evt) {
        for (IEventHandler<EVENT> handler : this.handlers) {
            handler.accept(evt);
        }
        if (parent != null) {
            return parent.post(evt);
        } else return evt;
    }

    public IEventHandler<EVENT> register(IEventHandler<EVENT> handler){
        this.handlers.add(handler);
        return handler;
    }

    public static<T> EventBus<T> of(){
        return new EventBus<>();
    }

    public static<V, T extends V> EventBus<T> of(EventBus<V> parent) {
        return new EventBus<T>(parent);
    }
}
