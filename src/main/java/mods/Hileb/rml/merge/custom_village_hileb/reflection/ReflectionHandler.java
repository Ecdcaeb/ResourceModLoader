package com.Hileb.custom_village_hileb.reflection;

import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession;

import java.lang.reflect.Field;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:52
 **/
public class ReflectionHandler {
    public static Field villagerRegistry_villagerProfession$careers;
    public static void init(){
        for(Field field: VillagerProfession.class.getDeclaredFields()){
            if ("careers".equals(field.getName())){
                villagerRegistry_villagerProfession$careers=field;
                field.setAccessible(true);
            }
        }
    }
}
