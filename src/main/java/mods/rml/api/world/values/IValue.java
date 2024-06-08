package mods.rml.api.world.values;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/6/8 17:02
 **/
@FunctionalInterface
public interface IValue<T> {
    T get();
}
