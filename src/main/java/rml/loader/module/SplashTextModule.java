package rml.loader.module;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.runtime.ModuleLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

/** Client text resources. The destination belongs to the caller, not shared static state. */
public final class SplashTextModule {
    private final ModuleLoader modules;

    public SplashTextModule(ModuleLoader modules) { this.modules = modules; }

    public void appendTo(List<String> destination) {
        modules.load(ModuleType.valueOf(new ResourceLocation("rml", "splash_text")), context -> {
            try (BufferedReader reader = context.openBufferedReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) destination.add(line);
                }
            } catch (IOException e) {
                context.error(e, "Cannot load splash text {}", context.getResourceLocation());
            }
        }, SplashTextModule.class);
    }

    public static String format(String text) {
        try {
            return ITextComponent.Serializer.jsonToComponent(text).getFormattedText();
        } catch (RuntimeException e) {
            return text;
        }
    }
}
