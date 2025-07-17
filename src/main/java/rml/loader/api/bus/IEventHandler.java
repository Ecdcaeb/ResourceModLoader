package rml.loader.api.bus;

@FunctionalInterface
public interface IEventHandler <EVENT>{
    void accept(EVENT event);
}
