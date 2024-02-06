package mods.Hileb.rml.api.file;

import com.google.gson.JsonElement;
import mods.Hileb.rml.api.EarlyClass;
import mods.Hileb.rml.api.PublicAPI;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/2/7 1:42
 **/
@EarlyClass
@PublicAPI
public class JsonHelper {
    public static boolean getBoolean(JsonElement element){return element.getAsBoolean();}
    public static int getInteger(JsonElement element){return element.getAsInt();}
    public static char getChar(JsonElement element){return element.getAsCharacter();}
    public static String getString(JsonElement element){return element.getAsString();}
    public static short getShort(JsonElement element){return element.getAsShort();}
    public static float getFloat(JsonElement element){return element.getAsFloat();}
    public static boolean getBool(JsonElement element){return element.getAsBoolean();}
    public static boolean getBool(JsonElement element){return element.getAsBoolean();}
    public static boolean getBool(JsonElement element){return element.getAsBoolean();}
}
