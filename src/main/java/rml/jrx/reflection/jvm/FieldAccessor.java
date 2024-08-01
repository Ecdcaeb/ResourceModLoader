package rml.jrx.reflection.jvm;

import rml.jrx.announces.EarlyClass;
import rml.jrx.announces.PublicAPI;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/31 10:23
 **/

@PublicAPI
@EarlyClass
@SuppressWarnings("all")
public class FieldAccessor<T, E> {
    public static final MethodAccessor<Field, Void> makeWritable;
    public static final MethodAccessor<Void, Void> setField;

    static {
        try {
            Class<?> clsFinalFieldHelper = Class.forName("net.minecraftforge.registries.ObjectHolderRef$FinalFieldHelper");
            makeWritable = ReflectionHelper.getMethodAccessor(clsFinalFieldHelper, "makeWritable", "makeWritable", Field.class);
            setField = ReflectionHelper.getMethodAccessor(clsFinalFieldHelper, "setField", "setField", Field.class, Object.class, Object.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    private final boolean isFinal;
    private final Field field;
    public FieldAccessor(Field field){
        this.isFinal = Modifier.isFinal(field.getModifiers());
        if (this.isFinal){
            this.field = makeWritable.invoke(null, field);;
        }else {
            field.setAccessible(true);
            this.field = field;
        }
    }

    public T get(E instance){
        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void set(E instance, T value){
        if (!isFinal) {
            try {
                field.set(instance, value);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }else {
            setField.invoke(null, field, instance, value);
        }
    }

    public Field getField() {
        return field;
    }
}
