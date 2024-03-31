package mods.Hileb.rml.api.java.reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/31 10:23
 **/
public class MethodAccessor<T, E> {
    private final Method method;
    public MethodAccessor(Method method){
        method.setAccessible(true);
        this.method = method;
    }

    public T invoke(E instance, Object... args){
        if (method.getReturnType()==void.class){
            return null;
        }else {
            try {
                return (T)method.invoke(instance, args);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Method getMethod() {
        return method;
    }
}
