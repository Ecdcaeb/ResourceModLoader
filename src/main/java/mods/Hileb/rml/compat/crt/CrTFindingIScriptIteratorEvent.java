package mods.Hileb.rml.compat.crt;

import crafttweaker.runtime.IScriptIterator;
import crafttweaker.runtime.IScriptProvider;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.Iterator;
import java.util.List;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/19 22:54
 **/
public class CrTFindingIScriptIteratorEvent extends Event {
    public final List<IScriptIterator> scriptIterators;
    public CrTFindingIScriptIteratorEvent(List<IScriptIterator> list){
        scriptIterators=list;
    }
    public void load(IScriptProvider provider){
        Iterator<IScriptIterator> iterator=provider.getScripts();
        while (iterator.hasNext()){
            scriptIterators.add(iterator.next());
        }
    }
    @Override
    public boolean isCancelable() {
        return false;
    }
}
