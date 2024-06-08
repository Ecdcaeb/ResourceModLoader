package mods.rml.api.java.utils;

import dev.latvian.kubejs.documentation.O;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/12 11:17
 **/
public class ObjectHelper {
    public static <T> T orDefault(T t, T defaultValue){
        return t == null ? defaultValue : t;
    }

    public static String allToString(Object... args){
        StringBuilder builder = new StringBuilder();
        for(Object o : args){
            builder.append(o);
        }
        return builder.toString();
    }

    public static String allToString(String with, Object... args){
        StringBuilder builder = new StringBuilder();
        for(Object o : args){
            builder.append(o).append(with);
        }
        String s = builder.toString();
        return s.substring(0, s.length() - with.length());
    }

    public static String allToString(String... args){
        StringBuilder builder = new StringBuilder();
        for(String o : args){
            builder.append(o);
        }
        return builder.toString();
    }
}
