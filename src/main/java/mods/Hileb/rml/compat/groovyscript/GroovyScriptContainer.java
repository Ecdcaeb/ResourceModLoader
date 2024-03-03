package mods.Hileb.rml.compat.groovyscript;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.sandbox.GroovySandbox;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/3 11:24
 **/
public class GroovyScriptContainer {
    private static JsonObject updateRunConfigJson(ModContainer container, JsonObject json) {
        json.addProperty("packName", container.getName());
        json.addProperty("packId", container.getModId());
        json.addProperty("version", container.getVersion());
        return json;
    }
    /**
     * {@link GroovySandbox#getClassFiles()}
     * {@link GroovySandbox#getScriptFiles()}
     * {@link GroovyScript#getResourcesFile()}
     *
     * hard to generate a running time {@link File}
     * all logic writed with {@link File}
     * **/

}
