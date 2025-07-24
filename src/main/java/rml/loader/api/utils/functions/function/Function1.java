package rml.loader.api.utils.functions.function;

@FunctionalInterface
public interface Function1<R, P1>{
    R execute(P1 p1);
}
