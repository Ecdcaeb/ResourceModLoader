package mods.Hileb.rml.api.text;

import mods.Hileb.rml.api.event.client.gui.HandleComponentEvent;
import mods.Hileb.rml.api.java.reflection.FieldAccessor;
import mods.Hileb.rml.api.java.reflection.MethodAccessor;
import mods.Hileb.rml.api.java.reflection.ReflectionHelper;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/31 10:20
 **/
@SideOnly(Side.CLIENT)
public class ChangeModAction {
    public static ClickEvent.Action CHANGE_MOD = EnumHelper.addEnum(ClickEvent.Action.class, "rml_change_mod", new Class<?>[]{String.class, boolean.class}, "rml_change_mod", true);

    public static final FieldAccessor<ModContainer, GuiModList> selectedMod = ReflectionHelper.getFieldAccessor(GuiModList.class, "selectedMod");
    public static final MethodAccessor<Void, GuiModList> updateCache = ReflectionHelper.getMethodAccessor(GuiModList.class, "updateCache", null, new Class<?>[0]);

    @SubscribeEvent
    public static void onTextComponentActionHandle(HandleComponentEvent.Click event){
        ClickEvent.Action action = event.getClickEvent().getAction();
        if (action == CHANGE_MOD && !event.isHandled()){
            if (event.getGuiScreen() instanceof GuiModList){
                GuiModList guiModList = (GuiModList)event.getGuiScreen();
                selectedMod.set(guiModList, Loader.instance().getIndexedModList().get(event.clickEvent.getValue()));
                updateCache.invoke(guiModList);
                event.setHandled(true);
            }
        }
    }

    public static ClickEvent makeEvent(String modid){
        return new ClickEvent(CHANGE_MOD, modid);
    }
}
