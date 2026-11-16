package rml.loader.content;

import com.google.gson.JsonElement;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraftforge.fml.common.FMLCommonHandler;
import rml.runtime.OwnedRegistry;
import rml.runtime.PreparedUnit;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** Decodes actual 1.12 templates; registrations live only as long as their resource scope. */
public final class CompiledStructureModule {
    private static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;
    private static final int DATA_VERSION_1_12_2 = 1343;
    private final Map<ResourceLocation, Template> templates = new HashMap<>();
    private final OwnedRegistry<ResourceLocation, Template> registrations = new OwnedRegistry<>(templates);

    public CompiledStructureModule() {
    }

    public PreparedUnit prepare(CompiledResource resource) {
        Objects.requireNonNull(resource, "resource");
        final Template template;
        try {
            if (!"structure".equals(resource.getType())) {
                throw new IllegalArgumentException("Expected structure resource, got " + resource.getType());
            }
            JsonElement value = resource.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Structure value must be a base64 string containing gzip NBT");
            }
            String encoded = value.getAsString();
            if (encoded.length() > ((MAX_COMPRESSED_BYTES + 2) / 3) * 4) {
                throw new IllegalArgumentException("Compressed structure exceeds 16 MiB");
            }
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > MAX_COMPRESSED_BYTES) {
                throw new IllegalArgumentException("Compressed structure must contain 1 byte to 16 MiB");
            }
            NBTTagCompound nbt;
            // readCompressed uses an unlimited NBT tracker. Use the same gzip/NBT
            // format with the native tracked reader to bound decoded allocations.
            try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
                nbt = CompressedStreamTools.read(input, new NBTSizeTracker(MAX_NBT_BYTES));
                if (input.read() != -1) {
                    throw new IllegalArgumentException("Trailing data after structure NBT");
                }
            }
            if (nbt.hasKey("DataVersion") && !nbt.hasKey("DataVersion", 3)) {
                throw new IllegalArgumentException("DataVersion must be an integer");
            }
            if (!nbt.hasKey("DataVersion")) {
                // This is the same legacy default used by native TemplateManager.
                nbt.setInteger("DataVersion", 500);
            }
            int version = nbt.getInteger("DataVersion");
            if (version < 0 || version > DATA_VERSION_1_12_2) {
                throw new IllegalArgumentException("Unsupported structure DataVersion " + version
                        + "; expected Minecraft 1.12.2 NBT (at most 1343)");
            }
            nbt = FMLCommonHandler.instance().getDataFixer().process(FixTypes.STRUCTURE, nbt);
            validate(nbt);
            template = new Template();
            template.read(nbt);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException(resource.getSource() + ": structure " + resource.getId()
                    + ": " + failure.getMessage(), failure);
        }
        return scope -> scope.own(registrations.register(resource.getId(), template));
    }

    /** Constant-time lookup; a missing definition leaves native TemplateManager in charge. */
    public Template findTemplate(ResourceLocation id) {
        return templates.get(id);
    }

    private static void validate(NBTTagCompound nbt) {
        NBTTagList size = vector(nbt, "size", 3);
        for (int axis = 0; axis < 3; axis++) {
            if (size.getIntAt(axis) < 0) {
                throw new IllegalArgumentException("Structure size cannot be negative");
            }
        }
        NBTTagList palette = list(nbt, "palette", 10);
        for (int index = 0; index < palette.tagCount(); index++) {
            NBTTagCompound state = palette.getCompoundTagAt(index);
            if (!state.hasKey("Name", 8)) {
                throw new IllegalArgumentException("Palette entry " + index + " requires a block Name");
            }
            ResourceLocation name = new ResourceLocation(state.getString("Name"));
            if (!Block.REGISTRY.containsKey(name)) {
                throw new IllegalArgumentException("Unknown palette block " + name);
            }
            if (state.hasKey("Properties")) {
                if (!state.hasKey("Properties", 10)) {
                    throw new IllegalArgumentException("Palette Properties must be a compound");
                }
                Block block = Block.REGISTRY.getObject(name);
                NBTTagCompound properties = state.getCompoundTag("Properties");
                for (String key : properties.getKeySet()) {
                    IProperty<?> property = block.getBlockState().getProperty(key);
                    if (!properties.hasKey(key, 8) || property == null
                            || !property.parseValue(properties.getString(key)).isPresent()) {
                        throw new IllegalArgumentException("Invalid palette property " + name + "[" + key + "]");
                    }
                }
            }
        }
        NBTTagList blocks = list(nbt, "blocks", 10);
        for (int index = 0; index < blocks.tagCount(); index++) {
            NBTTagCompound block = blocks.getCompoundTagAt(index);
            NBTTagList pos = vector(block, "pos", 3);
            for (int axis = 0; axis < 3; axis++) {
                if (pos.getIntAt(axis) < 0 || pos.getIntAt(axis) >= size.getIntAt(axis)) {
                    throw new IllegalArgumentException("Block " + index + " is outside structure size");
                }
            }
            if (!block.hasKey("state", 3) || block.getInteger("state") < 0
                    || block.getInteger("state") >= palette.tagCount()) {
                throw new IllegalArgumentException("Invalid palette state for block " + index);
            }
            if (block.hasKey("nbt") && !block.hasKey("nbt", 10)) {
                throw new IllegalArgumentException("Block nbt must be a compound");
            }
        }
        NBTTagList entities = list(nbt, "entities", 10);
        for (int index = 0; index < entities.tagCount(); index++) {
            NBTTagCompound entity = entities.getCompoundTagAt(index);
            NBTTagList pos = vector(entity, "pos", 6);
            vector(entity, "blockPos", 3);
            if (!entity.hasKey("nbt", 10)) {
                throw new IllegalArgumentException("Entity " + index + " requires an nbt compound");
            }
            for (int axis = 0; axis < 3; axis++) {
                if (!Double.isFinite(pos.getDoubleAt(axis))) {
                    throw new IllegalArgumentException("Entity positions must be finite");
                }
            }
        }
    }

    private static NBTTagList vector(NBTTagCompound nbt, String key, int elementType) {
        NBTTagList result = list(nbt, key, elementType);
        if (result.tagCount() != 3) {
            throw new IllegalArgumentException(key + " must contain exactly three coordinates");
        }
        return result;
    }

    private static NBTTagList list(NBTTagCompound nbt, String key, int elementType) {
        if (!nbt.hasKey(key, 9)) {
            throw new IllegalArgumentException("Missing structure list " + key);
        }
        NBTTagList result = (NBTTagList) nbt.getTag(key);
        if (!result.isEmpty() && result.getTagType() != elementType) {
            throw new IllegalArgumentException("Invalid element type in structure list " + key);
        }
        return result;
    }
}
