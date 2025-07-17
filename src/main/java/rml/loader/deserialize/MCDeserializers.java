package rml.loader.deserialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.JsonDeserializeException;
import rml.loader.api.annotations.BeDiscovered;
import rml.loader.api.annotations.PrivateAPI;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/20 22:20
 **/
@BeDiscovered(BeDiscovered.PRE_INIT)
public class MCDeserializers {
    // Build Deserializer for all IForgeRegistry
    @PrivateAPI
    public static <T extends IForgeRegistryEntry<T>> IForgeRegistry<T> onNewRegistry(final IForgeRegistry<T> registry, final ResourceLocation name) {
        AbstractDeserializer<T> abstractDeserializer = new AbstractDeserializer<>(name, registry.getRegistrySuperType(), jsonElement -> {
            ResourceLocation resourceLocation = Deserializer.decode(ResourceLocation.class, jsonElement);
            T obj = registry.getValue(resourceLocation);
            if (obj != null) return obj;
            else throw new JsonDeserializeException(jsonElement, "No Such RegistryEntry for " + name);
        });
        Deserializer.MANAGER.addDefaultEntry(abstractDeserializer);
        return registry;
    }
}
