package rml.loader.module;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import rml.loader.api.config.ConfigFactory;
import rml.loader.api.config.ConfigPatcher;
import rml.loader.api.config.v2.config.elements.ConfigElement;
import rml.loader.api.config.v2.config.elements.ConfigGroup;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.api.reflection.jvm.FieldAccessor;
import rml.loader.api.reflection.jvm.ReflectionHelper;
import rml.loader.runtime.ModuleLoader;

import java.util.Map;

/** Native Forge config integration. It intentionally retains startup-only semantics. */
public final class ConfigModule {
    private final ModuleLoader modules;

    public ConfigModule(ModuleLoader modules) { this.modules = modules; }

    public void loadDefinitions() {
        FieldAccessor<Map<String, Multimap<Config.Type, ASMDataTable.ASMData>>, ConfigManager> metadata =
                ReflectionHelper.getFieldAccessor(ConfigManager.class, "asm_data");
        modules.load(ModuleType.valueOf(new ResourceLocation("rml", "config_define")), context -> {
            ResourceLocation name = context.getResourceLocation();
            ConfigPatcher.OWNED_CONFIGS.put(ConfigFactory.addConfig(name.getPath(), name.getNamespace(), context.getBytes()),
                    name.getNamespace());
            metadata.get(null).put(name.getNamespace(), HashMultimap.create());
        }, ConfigModule.class);
    }

    public void loadNodes() {
        modules.load(ModuleType.valueOf(new ResourceLocation("rml", "config_nodes")), context -> {
            if (!context.isExtension("json")) return;
            try {
                ((ConfigGroup) context.deserialize(ConfigElement.class)).register();
            } catch (Exception e) {
                context.error(e, "Cannot load config nodes {}", context.getResourceLocation());
            }
        }, ConfigModule.class);
    }
}
