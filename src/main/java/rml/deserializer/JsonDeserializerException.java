package rml.deserializer;

import com.google.gson.JsonElement;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 10:29
 **/
public class JsonDeserializerException extends Exception{
    public JsonDeserializerException(JsonElement element, Throwable throwable){
        super(element.toString(), throwable);
    }

    public JsonDeserializerException(JsonElement element, String s){
        super(s + ", " + element.toString());
    }
    public JsonDeserializerException(JsonElement element){
        super(element.toString());
    }
    public JsonDeserializerException(JsonElement element, String s, Throwable throwable){
        super(s + ", " + element.toString(), throwable);
    }
}
