package com.Hileb.custom_colorful_enchantment.internal.utils;

import com.Hileb.custom_colorful_enchantment.CCECoreTransformationService;
import com.Hileb.custom_colorful_enchantment.api.colors.IColorInstance;
import com.Hileb.custom_colorful_enchantment.api.registry.ColorInstancesRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 20:30
 **/
@SuppressWarnings("unused")
public class RenderUtil {
    @SuppressWarnings("all")
    public static int getColor(ItemStack stack) {
        List<Integer> colors = new ArrayList();
        Iterator var2 = ColorInstancesRegistry.REGISTRY.entrySet().iterator();

        while(var2.hasNext()) {
            Map.Entry<ResourceLocation, IColorInstance> e = (Map.Entry)var2.next();
            IColorInstance iColorInstance = (IColorInstance)e.getValue();
            if (iColorInstance.hasColor(stack)) {
                colors.add(iColorInstance.getRGBColor(stack));
            }
        }

        if (colors.size() == 0) {
            return 0;
        } else {
            if (stack.isEnchanted() && colors.size() >= 2) {
                colors.remove(((Integer)8405195));
            }

            int color = 0;
            int size = colors.size();

            for(int i = 0; i < size; ++i) {
                color += colors.get(i) / size;
            }

            return color;
        }
    }
    @SuppressWarnings("unused")
    public static void callOnHead(ItemStack stack){
        if (!stack.isEmpty()){
            int color=getColor(stack);
            CCECoreTransformationService.LOGGER.info("getColor : "+color);
            ShaderHandler.redefineColor(color);
        }
    }
}
