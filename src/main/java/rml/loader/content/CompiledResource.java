package rml.loader.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.util.ResourceLocation;

/** Platform-side compiled definition, detached from compiler classes and source archives. */
public final class CompiledResource {
    private final String packId;
    private final String type;
    private final ResourceLocation id;
    private final String source;
    private final String value;

    public CompiledResource(String packId, String type, ResourceLocation id, String source, JsonElement value) {
        this.packId = packId;
        this.type = type;
        this.id = id;
        this.source = source;
        this.value = value.toString();
    }

    public String getPackId() { return packId; }
    public String getType() { return type; }
    public ResourceLocation getId() { return id; }
    public String getSource() { return source; }

    /** A provider owns its decoded value; one provider cannot mutate another's definition. */
    public JsonElement getValue() { return new JsonParser().parse(value); }
}
