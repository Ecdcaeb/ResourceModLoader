package mods.rml.api.java.utils;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/12 11:17
 **/
public class ObjectHelper {
    public static <T> T orDefault(T t, T defaultValue){
        return t == null ? defaultValue : t;
    }
}
