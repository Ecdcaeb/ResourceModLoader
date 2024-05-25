package mods.rml.api.tag;

import com.google.common.collect.HashBiMap;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:05
 **/
public class TagManager<T> {
    public final HashBiMap<ResourceLocation, Tag<? extends T>> registry = HashBiMap.create();
    public final Class<T> base;
    public TagManager(Class<T> clazz){
        base = clazz;
    }
}
