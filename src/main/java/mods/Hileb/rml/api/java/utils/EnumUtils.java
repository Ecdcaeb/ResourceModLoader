package mods.Hileb.rml.api.java.utils;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/1 13:39
 **/
public class EnumUtils {
    public static String toStringFrom(Enum<?>[] enums){
        StringBuilder stringBuilder = new StringBuilder();
        for(Enum<?> e:enums){
            stringBuilder.append(e.name());
        }
        return stringBuilder.toString();
    }
}
