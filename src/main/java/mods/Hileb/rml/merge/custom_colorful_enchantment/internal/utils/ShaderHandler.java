package com.Hileb.custom_colorful_enchantment.internal.utils;

import net.minecraft.client.renderer.GameRenderer;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/3 13:03
 **/
public class ShaderHandler {

    public static ShaderHandlerInstance rendertypeArmorGlintShader=new ShaderHandlerInstance(GameRenderer.getRendertypeArmorGlintShader());

    public static ShaderHandlerInstance rendertypeArmorEntityGlintShader=new ShaderHandlerInstance(GameRenderer.getRendertypeArmorEntityGlintShader()) ;

    public static ShaderHandlerInstance rendertypeGlintTranslucentShader=new ShaderHandlerInstance(GameRenderer.getRendertypeGlintTranslucentShader());

    public static ShaderHandlerInstance rendertypeGlintShader=new ShaderHandlerInstance(GameRenderer.getRendertypeGlintShader());

    public static ShaderHandlerInstance rendertypeGlintDirectShader=new ShaderHandlerInstance(GameRenderer.getRendertypeGlintDirectShader());

    public static ShaderHandlerInstance rendertypeEntityGlintShader=new ShaderHandlerInstance(GameRenderer.getRendertypeEntityGlintShader());

    public static ShaderHandlerInstance rendertypeEntityGlintDirectShader=new ShaderHandlerInstance(GameRenderer.getRendertypeEntityGlintDirectShader());



    public static final ShaderHandlerInstance[] values={rendertypeGlintDirectShader,rendertypeGlintShader,rendertypeEntityGlintDirectShader,rendertypeEntityGlintShader,rendertypeGlintTranslucentShader,rendertypeArmorEntityGlintShader,rendertypeArmorGlintShader};

    public static void redefineColor(int rgb){
        float r=((rgb>>16)&0xff)/255f;
        float g=((rgb>>8)&0xff)/255f;
        float b=((rgb)&0xff)/255f;

        for(ShaderHandlerInstance shaderHandlerInstance:values){
            shaderHandlerInstance.redefineUniform(r,g,b);
        }
    }
    @SuppressWarnings("unused")
    public static void onReload(){
        try {
            for (ShaderHandlerInstance shaderHandlerInstance : values) {
                shaderHandlerInstance.onReload();
            }
        }catch (Exception exception){
            throw new RuntimeException(exception);
        }
    }
}
