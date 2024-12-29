package rml.deserializer.struct.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.latvian.kubejs.documentation.O;
import javassist.expr.FieldAccess;
import rml.deserializer.struct.std.StructBoolean;
import rml.deserializer.struct.std.StructElement;
import rml.deserializer.struct.std.StructNull;
import rml.deserializer.struct.std.StructString;
import rml.jrx.reflection.jvm.FieldAccessor;
import rml.jrx.reflection.jvm.ReflectionHelper;

public class JsonStruct {
//    public static final FieldAccessor<Object, JsonPrimitive> jsonPrimitive$value = ReflectionHelper.getFieldAccessor(JsonPrimitive.class, "value");
//
//    public static StructElement toStruct(JsonElement element){
//        if (element == null || element.isJsonNull()) return StructNull.NULL;
//        else if (element instanceof JsonPrimitive jsonPrimitive){
//            Object obj = jsonPrimitive$value.get(jsonPrimitive);
//            if (obj instanceof Boolean z) return StructBoolean.of(z);
//            else if (obj instanceof String s) return new StructString(s);
//            else if (obj instanceof Float f)
//            if (jsonPrimitive.isBoolean()) return StructBoolean.of(jsonPrimitive.getAsBoolean());
//            else if (jsonPrimitive.isString())
//        }
//    }
}
