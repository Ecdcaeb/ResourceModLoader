package com.Hileb.custom_colorful_enchantment.internal.instances;

import com.Hileb.custom_colorful_enchantment.ColorfulEnchantmentConfig;
import com.Hileb.custom_colorful_enchantment.api.colors.IColorFactory;
import com.Hileb.custom_colorful_enchantment.internal.json.FactoryLoader;
import cpw.mods.modlauncher.api.ITransformationService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 20:17
 **/
@Mod("custom_colorful_enchantment")
public class DeferredBusHandler {
    public DeferredBusHandler(){
        FMLJavaModLoadingContext.get().getModEventBus().addListener(DeferredBusHandler::handle);
    }
    public static void handle(FMLCommonSetupEvent event) {
        ColorfulEnchantmentConfig.initConfig();
        MinecraftForge.EVENT_BUS.register(EventHandler.class);
        FactoryLoader.load(ColorfulEnchantmentConfig.instance);
    }
}
