package com.Hileb.custom_colorful_enchantment.api.registry;

import com.Hileb.custom_colorful_enchantment.api.colors.IColorInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

import java.util.HashMap;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 19:57
 **/
public class ColorInstancesRegistry {
    public static HashMap<ResourceLocation, IColorInstance> REGISTRY = new HashMap();

    public ColorInstancesRegistry() {
    }

    public static void post() {
        MinecraftForge.EVENT_BUS.post(new RegistryEvent());
    }

    public static class RegistryEvent extends Event {
        public RegistryEvent() {
        }

        public void register(ResourceLocation name, IColorInstance factory) {
            ColorInstancesRegistry.REGISTRY.put(name, factory);
        }

        public boolean isCancelable() {
            return false;
        }
    }
}
