package mods.Hileb.rml.api.event.client.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/31 9:16
 **/
public class HandleComponentEvent extends Event {
    public boolean isHandled;
    public ITextComponent textComponent;
    public GuiScreen guiScreen;

    public boolean isHandled() {
        return isHandled;
    }

    public void setHandled(boolean handled) {
        isHandled = handled;
    }

    public GuiScreen getGuiScreen() {
        return guiScreen;
    }

    public ITextComponent getTextComponent() {
        return textComponent;
    }

    public HandleComponentEvent(boolean isHandled, ITextComponent textComponent, GuiScreen guiScreen){
        this.isHandled = isHandled;
        this.textComponent = textComponent;
        this.guiScreen = guiScreen;
    }

    public static boolean postHover(boolean isHandled, ITextComponent textComponent, GuiScreen guiScreen, int x, int y){
        Hover event = new Hover(isHandled, textComponent, guiScreen, x, y);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isHandled();
    }
    public static boolean postClick(boolean isHandled, ITextComponent textComponent, GuiScreen guiScreen){
        Click event = new Click(isHandled, textComponent, guiScreen);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isHandled();
    }

    public static class Click extends HandleComponentEvent{
        public ClickEvent clickEvent;
        public Click(boolean isHandled, ITextComponent textComponent, GuiScreen guiScreen) {
            super(isHandled, textComponent, guiScreen);
            this.clickEvent = textComponent.getStyle().getClickEvent();
        }

        public ClickEvent getClickEvent() {
            return clickEvent;
        }
    }

    public static class Hover extends HandleComponentEvent{
        public int x;
        public int y;
        public  Hover(boolean isHandled, ITextComponent textComponent, GuiScreen guiScreen, int x, int y) {
            super(isHandled, textComponent, guiScreen);
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }
}
