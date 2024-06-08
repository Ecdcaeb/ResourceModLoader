package mods.rml.api.world.text;

import dev.latvian.kubejs.documentation.T;
import mods.rml.api.event.client.gui.HandleComponentEvent;
import mods.rml.api.java.reflection.jvm.ReflectionHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/6/8 11:30
 **/

//TODO if https://github.com/CleanroomMC/Cleanroom/pull/107 merged, we need to rewrite this to adapt Cleanroom.
public class TextEffectsHelper {
    private TextEffectsHelper(){}
    public static Map<String, HoverEvent.Action> hoverMapping = ReflectionHelper.getPrivateValue(HoverEvent.Action.class, null, "NAME_MAPPING", "field_150690_d");
    public static Map<String, ClickEvent.Action> clickMapping = ReflectionHelper.getPrivateValue(ClickEvent.Action.class, null, "NAME_MAPPING", "field_150679_e");

    public static ClickEventHandler click(String nameIn, boolean allowedInChatIn){
        return new ClickEventHandler(nameIn, allowedInChatIn);
    }

    public static HoverEventHandler hover(String nameIn, boolean allowedInChatIn){
        return new HoverEventHandler(nameIn, allowedInChatIn);
    }
    public static class ClickEventHandler{
        public ClickEvent.Action action;

        public ClickEventHandler(String nameIn, boolean allowedInChatIn){
            this.action =  EnumHelper.addEnum(ClickEvent.Action.class, nameIn, new Class<?>[]{String.class, boolean.class}, nameIn, allowedInChatIn);
            clickMapping.put(nameIn, this.action);
        }

        public ClickEvent.Action getAction() {
            return action;
        }

        public ClickEvent makeEvent(String value){
            return new ClickEvent(action, value);
        }

        public <T extends HandleComponentEvent> ClickEventHandler eventHandler(BiConsumer<ClickEventHandler, T> handler){
            new EventHandler<>(this, handler);
            return this;
        }

        public boolean is(ClickEvent clickEvent){
            return clickEvent.getAction() == this.action;
        }
    }

    public static class HoverEventHandler{
        public HoverEvent.Action action;

        public HoverEventHandler(String nameIn, boolean allowedInChatIn){
            this.action =  EnumHelper.addEnum(HoverEvent.Action.class, nameIn, new Class<?>[]{String.class, boolean.class}, nameIn, allowedInChatIn);
            hoverMapping.put(nameIn, this.action);
        }

        public HoverEvent.Action getAction() {
            return action;
        }

        public HoverEvent makeEvent(ITextComponent value){
            return new HoverEvent(action, value);
        }


        public <T extends HandleComponentEvent> HoverEventHandler eventHandler(BiConsumer<HoverEventHandler, T> handler){
            new EventHandler<>(this, handler);
            return this;
        }
    }

    public static class EventHandler<E, T extends HandleComponentEvent>{
        private final BiConsumer<E, T> consumer;
        private final E obj;
        public EventHandler(E objIn, BiConsumer<E, T> consumer){
            this.consumer = consumer;
            this.obj = objIn;
            MinecraftForge.EVENT_BUS.register(this);
        }

        @SubscribeEvent
        public void handle(T evt){ consumer.accept(obj, evt); }

    }
}
