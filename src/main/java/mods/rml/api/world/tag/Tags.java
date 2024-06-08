package mods.rml.api.world.tag;

import java.util.HashMap;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:19
 **/
public class Tags {
    private static final HashMap<Class<?>, TagManager<?>> MANAGERS = new HashMap<>();
    public static <T> void registerManager(Class<T> tClass, TagManager<T> tagManager){
        MANAGERS.put(tClass, tagManager);
    }

    @SuppressWarnings("all")
    public static <T> TagManager<T> getManager(Class<T> tClass){
        return (TagManager<T>) MANAGERS.get(tClass);
    }
}
