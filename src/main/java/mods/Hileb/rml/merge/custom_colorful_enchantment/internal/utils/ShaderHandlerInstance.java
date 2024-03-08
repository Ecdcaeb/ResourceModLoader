package com.Hileb.custom_colorful_enchantment.internal.utils;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/3 12:34
 **/
public class ShaderHandlerInstance {
    public ShaderInstance shaderInstance;
    public Uniform uniformColorModulator;
    public Map<String, Uniform> map;
    public List<Uniform> uniforms;
    public ShaderHandlerInstance(String mcpNameIn,String srgNameIn){
        try {
            shaderInstance=null;
            Class<GameRenderer> cl= GameRenderer.class;
            String name=null;
            for(Field f:cl.getDeclaredFields()){
                name=f.getName();
                if (mcpNameIn.equals(name) || srgNameIn.equals(name)){
                    f.setAccessible(true);
                    shaderInstance= (ShaderInstance)f.get(Minecraft.getInstance().gameRenderer);
                }
            }
            uniformColorModulator=makeUniform(shaderInstance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        initInstance(this);
    }
    public ShaderHandlerInstance(ShaderInstance shaderInstanceIn){
        try {
            shaderInstance=shaderInstanceIn;
            uniformColorModulator=makeUniform(shaderInstance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        initInstance(this);
    }
    @SuppressWarnings("all")
    public static void initInstance(ShaderHandlerInstance instance){
        try {
            instance.map=(Map<String, Uniform>)ReflectionHandler.map_f_String_s_Uniform_uniformMap_from_ShaderInstance.get(instance.shaderInstance);
            instance.uniforms= (List<Uniform>)ReflectionHandler.uniformArray_uniforms_from_ShaderInstance.get(instance.shaderInstance);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public final void redefineUniform(float r,float g,float b){
        uniformColorModulator.setSafe(r,g,b,1);
    }
    public static Uniform makeUniform(ShaderInstance shader) throws IllegalAccessException {

        return (Uniform) ReflectionHandler.uniform_COLOR_MODULATOR_from_ShaderInstance.get(shader);
    }
    public final void onReload() throws IllegalAccessException {
        uniformColorModulator=makeUniform(shaderInstance);
    }
}
