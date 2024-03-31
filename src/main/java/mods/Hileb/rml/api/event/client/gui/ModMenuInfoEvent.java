package mods.Hileb.rml.api.event.client.gui;

import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

@SideOnly(Side.CLIENT)
public class ModMenuInfoEvent extends Event {
    public ModContainer mod;
    public List<ITextComponent> textComponents;
    public GuiModList guiModList;

    public ModMenuInfoEvent(GuiModList modList, ModContainer mod, List<ITextComponent> textComponents){
        this.guiModList = modList;
        this.mod = mod;
        this.textComponents = textComponents;
    }

    public ModContainer getMod() {
        return mod;
    }

    public List<ITextComponent> getTextComponents() {
        return textComponents;
    }

    public GuiModList getGuiModList() {
        return guiModList;
    }

    public static void post(GuiModList modList, ModContainer mod, List<ITextComponent> textComponentList){
        FMLLog.log.warn("event posted");
        MinecraftForge.EVENT_BUS.post(new ModMenuInfoEvent(modList, mod, textComponentList));
    }

}