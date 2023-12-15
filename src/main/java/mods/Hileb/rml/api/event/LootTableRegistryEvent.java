package mods.Hileb.rml.api.event;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/15 12:42
 **/
public class LootTableRegistryEvent extends Event {
    public LootTableRegistryEvent(){

    }
    public void register(ResourceLocation name){
        LootTableList.register(name);
    }
    public static void post(){
        MinecraftForge.EVENT_BUS.post(new LootTableRegistryEvent());
    }
}
