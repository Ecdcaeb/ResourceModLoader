package rml.loader.deserialize.utils;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import rml.deserializer.AbstractDeserializer;
import rml.loader.deserialize.Deserializer;

public class ITextComponentDeserializer {
    public static final AbstractDeserializer<ITextComponent> ITEXT_COMPONENT = Deserializer.MANAGER.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "textcomponent"), ITextComponent.class, AbstractDeserializer.safeRun((jsonElement -> ITextComponent.Serializer.jsonToComponent(String.valueOf(jsonElement))))));
}
