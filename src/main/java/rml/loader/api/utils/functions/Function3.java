package rml.loader.api.utils.functions;

@FunctionalInterface
public interface Function3<R, P1, P2, P3>{
    R execute(P1 p1, P2 p2, P3 p3);
}
