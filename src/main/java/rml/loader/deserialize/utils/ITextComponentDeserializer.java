package rml.loader.deserialize.utils;

import rml.loader.deserialize.NativeTypeDecoders;
import net.minecraft.util.text.ITextComponent;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;

public class ITextComponentDeserializer {
    public static final AbstractDeserializer<ITextComponent> ITEXT_COMPONENT = NativeTypeDecoders.registerText(Deserializer.MANAGER);
}
