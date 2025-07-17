package rml.loader.api.utils.functions;

@FunctionalInterface
public interface Function2<R, P1, P2>{
    R execute(P1 p1, P2 p2);
}
