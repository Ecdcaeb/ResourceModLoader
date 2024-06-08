package mods.rml.api.world.text;

import mods.rml.api.event.client.gui.HandleComponentEvent;
import mods.rml.api.java.reflection.jvm.FieldAccessor;
import mods.rml.api.java.reflection.jvm.MethodAccessor;
import mods.rml.api.java.reflection.jvm.ReflectionHelper;
import net.minecraft.util.text.event.ClickEvent;
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
public class ChangeMod {
    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2024/3/31 10:20
     **/
    @SideOnly(Side.CLIENT)
    public static class ChangeModAction {
        public static ClickEvent.Action CHANGE_MOD = EnumHelper.addEnum(ClickEvent.Action.class, "rml_change_mod", new Class<?>[]{String.class, boolean.class}, "rml_change_mod", true);

        public static final FieldAccessor<ArrayList<ModContainer>, GuiModList> mods = ReflectionHelper.getFieldAccessor(GuiModList.class, "mods");
        public static final MethodAccessor<Void, GuiModList> selectModIndex = ReflectionHelper.getMethodAccessor(GuiModList.class, "selectModIndex", null, new Class<?>[]{int.class});
        @SubscribeEvent
        public static void onTextComponentActionHandle(HandleComponentEvent.Click event) {
            ClickEvent.Action action = event.getClickEvent().getAction();
            if (action == CHANGE_MOD && !event.isHandled()) {
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

        public static ClickEvent makeEvent(String modid) {
            return new ClickEvent(CHANGE_MOD, modid);
        }
    }
}
