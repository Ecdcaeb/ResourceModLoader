package mods.rml.api.java.reflection.jvm;

import mods.rml.api.announces.EarlyClass;
import mods.rml.api.announces.PublicAPI;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/31 10:23
 **/

@PublicAPI
@EarlyClass
public class MethodAccessor<T, E> {
    private final Method method;
    public MethodAccessor(Method method){
        method.setAccessible(true);
        this.method = method;
    }

    public T invoke(E instance, Object... args){
        if (method.getReturnType()==void.class){
            try {
                method.invoke(instance, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return null;
        }else {
            try {
                return (T) method.invoke(instance, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Method getMethod() {
        return method;
    }
}