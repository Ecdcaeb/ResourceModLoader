package mods.rml.api.java.reflection.low;

import mods.rml.api.announces.EarlyClass;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/24 16:41
 **/

@EarlyClass
public interface ValueAccessor<T> {
    T get();
}
