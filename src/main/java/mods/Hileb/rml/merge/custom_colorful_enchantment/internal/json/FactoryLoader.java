package com.Hileb.custom_colorful_enchantment.internal.json;

import com.Hileb.custom_colorful_enchantment.ColorfulEnchantmentConfig;
import com.Hileb.custom_colorful_enchantment.api.colors.IColorFactory;
import com.Hileb.custom_colorful_enchantment.api.registry.ColorFactoryRegistry;
import com.Hileb.custom_colorful_enchantment.api.registry.ColorInstancesRegistry;
import com.google.gson.JsonObject;
import com.mojang.realmsclient.util.JsonUtils;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 19:59
 **/
public class FactoryLoader {
    public FactoryLoader() {
    }

    public static void load(ColorfulEnchantmentConfig.Config config) {
        ColorFactoryRegistry.post();
        Iterator var1 = config.colors.iterator();

        while(var1.hasNext()) {
            JsonObject json = (JsonObject)var1.next();
            ResourceLocation name = new ResourceLocation(JsonUtils.getRequiredString("type",json));
            IColorFactory factory = (IColorFactory)ColorFactoryRegistry.REGISTRY.get(name);
            ColorInstancesRegistry.REGISTRY.put(new ResourceLocation(name.toString(), String.valueOf(json.toString().hashCode())), factory.getInstance(json));
        }

        ColorInstancesRegistry.post();
    }
}
