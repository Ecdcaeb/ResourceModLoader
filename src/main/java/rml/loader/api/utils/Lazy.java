package rml.loader.api.utils;

import java.util.Objects;
import java.util.function.Supplier;

public final class Lazy<T> implements Supplier<T>{

    public static final Lazy<Void> NULL = new Lazy<>(null);

    public static<E> Lazy<E> of(E obj) {
        return new Lazy<>(Objects.requireNonNull(obj));
    }

    public static<E> Lazy<E> of(Supplier<E> supplier) {
        return new Lazy<>(Objects.requireNonNull(supplier));
    }

    private T obj;
    private Supplier<T> supplier;

    private Lazy(T obj) {
        this(obj, null);
    }

    private Lazy(Supplier<T> supplier) {
        this(null, supplier);
    }

    private Lazy(T obj, Supplier<T> supplier) {
        this.obj = obj;
        this.supplier = supplier;
    }

    @Override
    public T get() {
        if (this.obj == null) {
            if (this.supplier != null) {
                this.obj = supplier.get();
                this.supplier = null;
                return this.obj;
            } else return null;
        } else return this.obj;
    }

    @SuppressWarnings("unchecked")
    public <R> Lazy<R> cast() {
        return (Lazy<R>) this;
    }

}
