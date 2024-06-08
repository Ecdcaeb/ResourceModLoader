package mods.rml.api.world.text;

import mods.rml.api.event.client.gui.HandleComponentEvent;
import mods.rml.api.java.reflection.jvm.FieldAccessor;
import mods.rml.api.java.reflection.jvm.MethodAccessor;
import mods.rml.api.java.reflection.jvm.ReflectionHelper;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/1 13:36
 **/

@SideOnly(Side.CLIENT)
public class RMLTextEffects {
    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2024/3/31 10:20
     **/
    @SideOnly(Side.CLIENT)
    public static class ChangeModClickAction {
        public static final FieldAccessor<ArrayList<ModContainer>, GuiModList> mods = ReflectionHelper.getFieldAccessor(GuiModList.class, "mods");
        public static final MethodAccessor<Void, GuiModList> selectModIndex = ReflectionHelper.getMethodAccessor(GuiModList.class, "selectModIndex", null, new Class<?>[]{int.class});

        public static final TextEffectsHelper.ClickEventHandler CHANGE_MOD = TextEffectsHelper.click("rml_change_mod", false);

        @SubscribeEvent
        public static void onTextComponentActionHandle(HandleComponentEvent.Click event) {
            if (CHANGE_MOD.is(event.getClickEvent()) && !event.isHandled()) {
                if (event.getGuiScreen() instanceof GuiModList) {
                    GuiModList guiModList = (GuiModList) event.getGuiScreen();
                    String modid = event.getClickEvent().getValue();
                    ArrayList<ModContainer> modList = mods.get(guiModList);
                    for(int i = 0, size = modList.size(); i < size; i++){
                        if (modid.equals(modList.get(i).getModId())){
                            selectModIndex.invoke(guiModList, i);
                            event.setHandled(true);
                            return;
                        }
                    }
                }
            }
        }
    }
}
