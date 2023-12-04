package mods.Hileb.rml.compat.kubejs;

import dev.latvian.kubejs.script.ScriptFile;
import dev.latvian.kubejs.script.ScriptPack;

import java.io.CharArrayReader;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 22:22
 **/
public class BuffedJSFile extends ScriptFile {
    public Throwable error;
    public BuffedJSFile(ScriptPack pc, String fileName, int weight,char[] fileIn) {
        super(pc, fileName, weight, () -> new CharArrayReader(fileIn));
    }
}
