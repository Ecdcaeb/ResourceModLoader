package com.Hileb.custom_colorful_enchantment.internal.instances;

import com.Hileb.custom_colorful_enchantment.api.registry.ColorFactoryRegistry;
import com.Hileb.custom_colorful_enchantment.api.registry.ColorInstancesRegistry;
import com.Hileb.custom_colorful_enchantment.internal.instances.colors.ColorColorEnchantment;
import com.Hileb.custom_colorful_enchantment.internal.instances.colors.ColorColorItem;
import com.Hileb.custom_colorful_enchantment.internal.instances.colors.ColorInstanceNBT;
import com.Hileb.custom_colorful_enchantment.internal.instances.colors.ColorInstanceVanilla;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 20:16
 **/
public class EventHandler {
    public EventHandler() {
    }

    @SubscribeEvent
    public static void onPreRegister(ColorFactoryRegistry.RegistryEvent event) {
        event.register(new ResourceLocation("custom_colorful_enchantment", "enchantment"), new ColorColorEnchantment.Factory());
        event.register(new ResourceLocation("custom_colorful_enchantment", "ingredient"), new ColorColorItem.Factory());
    }

    @SubscribeEvent
    public static void onPostRegister(ColorInstancesRegistry.RegistryEvent event) {
        event.register(new ResourceLocation("custom_colorful_enchantment", "nbt"), new ColorInstanceNBT());
        event.register(new ResourceLocation("minecraft", "enchantment"), new ColorInstanceVanilla());
    }
}
