package com.Hileb.custom_village_hileb.json.load.event;

import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/16 10:22
 **/
public class CustomVillageLoaderRegisterEvent extends Event {
    public CustomVillageLoaderRegisterEvent(){}
    @Override
    public boolean isCancelable() {
        return false;
    }

    @Override
    public boolean hasResult() {
        return false;
    }
}
