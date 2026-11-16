package rml.loader.module;

import com.google.gson.JsonParseException;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.mods.module.ModuleType;
import rml.loader.deserialize.Deserializer;
import rml.loader.runtime.ModuleLoader;

import java.io.IOException;

/** Startup-only ore registrations. Source loading is supplied by the Forge composition root. */
public final class OreDictionaryModule {
    private final ModuleLoader modules;

    public OreDictionaryModule(ModuleLoader modules) { this.modules = modules; }

    public void load() {
        modules.load(ModuleType.valueOf(new ResourceLocation("rml", "ore_dic")), context -> {
            if (!context.isExtension("json")) return;
            try {
                for (TagOre entry : context.deserialize(TagOre[].class)) {
                    OreDictionary.registerOre(entry.ore, entry.item);
                }
            } catch (IOException | JsonParseException | JsonDeserializeException e) {
                context.error(e, "Cannot load ore dictionary entries {}", context.getResourceLocation());
            }
        }, OreDictionaryModule.class);
    }

    public static final class TagOre {
        public static final AbstractDeserializer<TagOre> DESERIALIZER = Deserializer.named(TagOre.class, new ResourceLocation("rml", "ore_tag"))
                .require(ItemStack.class, "item")
                .require(String.class, "ore")
                .decode(context -> new TagOre(context.get(String.class, "ore"), context.get(ItemStack.class, "item")))
                .markDefault().build();

        private final String ore;
        private final ItemStack item;

        private TagOre(String ore, ItemStack item) {
            this.ore = ore;
            this.item = item;
        }
    }
}
