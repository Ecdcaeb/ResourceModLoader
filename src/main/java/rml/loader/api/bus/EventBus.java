package rml.loader.api.bus;

@FunctionalInterface
public interface EventBus<EVENT> {
    <T extends EVENT> T post(T evt);
}
