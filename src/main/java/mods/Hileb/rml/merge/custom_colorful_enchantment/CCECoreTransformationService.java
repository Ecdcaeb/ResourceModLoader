package com.Hileb.custom_colorful_enchantment;

import com.Hileb.custom_colorful_enchantment.internal.instances.ItemRendererTransformer;
import com.Hileb.custom_colorful_enchantment.internal.instances.ShaderInstanceTransformer;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 20:32
 **/
@SuppressWarnings("unused")
public class CCECoreTransformationService implements ITransformationService{

    public static final String NAME="Custom Colorful Enchantment";
    public static final Logger LOGGER= LogManager.getLogger(NAME);
    static {
        LOGGER.warn("aaaa");
    }
    public CCECoreTransformationService(){

    }
    @Override
    public @NotNull String name() {
        return NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {
    }
    /*
    * 11261
    * 143699
    * */
    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {

    }
    @Override
    public @NotNull List<ITransformer> transformers() {
        LOGGER.info("Transformer loaded");
        return List.of(new ItemRendererTransformer(),new ShaderInstanceTransformer());
    }

}
