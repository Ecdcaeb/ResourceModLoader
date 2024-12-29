package rml.deserializer;

import rml.deserializer.struct.std.StructElement;
import rml.jrx.announces.EarlyClass;
import rml.jrx.announces.PublicAPI;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 10:29
 **/

@EarlyClass
@PublicAPI
public class JsonDeserializeException extends Exception{
    public JsonDeserializeException(StructElement element, Throwable throwable){
        super(String.valueOf(element), throwable);
    }

    public JsonDeserializeException(StructElement element, String s){
        super(s + ", " + element);
    }
    public JsonDeserializeException(StructElement element){
        super(String.valueOf(element));
    }
    public JsonDeserializeException(StructElement element, String s, Throwable throwable){
        super(s + ", " + element, throwable);
    }
}
