package mods.rml.api.tag;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:04
 **/
public interface Tag<T> {
    boolean match(T value);
}
