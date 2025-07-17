package rml.loader.api.reflection.low;

import rml.loader.api.annotations.EarlyClass;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/24 16:41
 **/

@EarlyClass
public interface ValueAccessor<T> {
    T get();
}
