package mods.rml.compat.kubejs;

import dev.latvian.kubejs.script.ScriptFile;
import dev.latvian.kubejs.script.ScriptPack;
import mods.rml.api.PrivateAPI;

import java.io.CharArrayReader;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 22:22
 **/
@PrivateAPI
public class BuffedJSFile extends ScriptFile {
    @PrivateAPI public Throwable error;
    @PrivateAPI public BuffedJSFile(ScriptPack pc, String fileName, int weight,char[] fileIn) {
        super(pc, fileName, weight, () -> new CharArrayReader(fileIn));
    }
}
