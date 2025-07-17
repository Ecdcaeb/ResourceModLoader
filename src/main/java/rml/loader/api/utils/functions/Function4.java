package rml.loader.api.utils.functions;

@FunctionalInterface
public interface Function4<R, P1, P2, P3, P4>{
    R execute(P1 p1, P2 p2, P3 p3, P4 p4);
}
