package com.Hileb.custom_colorful_enchantment.internal.utils;

import net.minecraft.client.renderer.ShaderInstance;

import java.lang.reflect.Field;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/3 12:46
 **/
public class ReflectionHandler {
    public static Field uniform_COLOR_MODULATOR_from_ShaderInstance;
    public static Field uniformArray_uniforms_from_ShaderInstance;
    public static Field map_f_String_s_Uniform_uniformMap_from_ShaderInstance;
    static {
        {
            Class<ShaderInstance> cl=ShaderInstance.class;
            String name=null;
            for (Field field:cl.getDeclaredFields()){
                name=field.getName();
                if ("COLOR_MODULATOR".equals(name) || "f_173312_".equals(name)){
                    uniform_COLOR_MODULATOR_from_ShaderInstance=field;
                    uniform_COLOR_MODULATOR_from_ShaderInstance.setAccessible(true);
                }else if ("uniforms".equals(name) || "f_173331_".equals(name)){
                    uniformArray_uniforms_from_ShaderInstance=field;
                    uniformArray_uniforms_from_ShaderInstance.setAccessible(true);
                }else if ("uniformMap".equals(name) || "f_173333_".equals(name)){
                    map_f_String_s_Uniform_uniformMap_from_ShaderInstance=field;
                    map_f_String_s_Uniform_uniformMap_from_ShaderInstance.setAccessible(true);
                }
            }
        }
    }
}
