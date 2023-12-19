package mods.Hileb.rml.compat.crt;

import crafttweaker.runtime.IScriptIterator;
import crafttweaker.runtime.IScriptProvider;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/19 22:40
 **/
public class EventScriptProvider implements IScriptProvider {
    public IScriptProvider proxy;
    public EventScriptProvider(IScriptProvider provider){
        proxy=provider;
    }
    @Override
    public Iterator<IScriptIterator> getScripts() {
        return this.combine().iterator();
    }
    public List<IScriptIterator> combine(){
        LinkedList<IScriptIterator> list=new LinkedList<>();

        Iterator<IScriptIterator> iterator=proxy.getScripts();
        while (iterator.hasNext()){
            list.add(iterator.next());
        }
        MinecraftForge.EVENT_BUS.post(new CrTFindingIScriptIteratorEvent(list));
        return list;
    }
}
