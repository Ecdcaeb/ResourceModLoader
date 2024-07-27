package rml.deserializer;

import com.google.gson.JsonElement;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 10:29
 **/
public class JsonDeserializeException extends Exception{
    public JsonDeserializeException(JsonElement element, Throwable throwable){
        super(element.toString(), throwable);
    }

    public JsonDeserializeException(JsonElement element, String s){
        super(s + ", " + element.toString());
    }
    public JsonDeserializeException(JsonElement element){
        super(element.toString());
    }
    public JsonDeserializeException(JsonElement element, String s, Throwable throwable){
        super(s + ", " + element.toString(), throwable);
    }
}
