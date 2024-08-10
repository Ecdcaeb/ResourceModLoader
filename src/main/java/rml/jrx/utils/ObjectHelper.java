package rml.jrx.utils;

import com.google.common.base.Predicate;
import rml.jrx.announces.EarlyClass;
import rml.jrx.announces.PublicAPI;

import java.util.Objects;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/12 11:17
 **/

@EarlyClass
@PublicAPI
public class ObjectHelper {
    public static <T> T orDefault(T t, T defaultValue){
        return orDefault(Objects::nonNull, t, defaultValue);
    }

    public static <T> T orDefault(Predicate<T> predicate, T t, T defaultValue){
        return predicate.test(t) ? t : defaultValue;
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
