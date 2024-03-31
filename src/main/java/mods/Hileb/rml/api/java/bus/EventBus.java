package mods.Hileb.rml.api.java.bus;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import mods.Hileb.rml.api.java.reflection.MethodAccessor;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

public class EventBus {
    private Multimap<Class<?>, Consumer<?>> subscribers = LinkedListMultimap.create();
    public void register(Class<?> clazz, Consumer<?> consumer){subscribers.put(clazz, consumer);}

    public <T> void register(Consumer<T> consumer){
        TypeToken<T> typeToken = new TypeToken<T>(consumer.getClass()){};
        register((Class<?>)typeToken.getRawType(), consumer);
    }

    public void register(Object obj){
        boolean isStatic = obj instanceof Class;
        Class<?> clazz = isStatic ? (Class<?>) obj : obj.getClass();
        @SuppressWarnings("unchecked")
        Set<? extends Class<?>> supers = isStatic ? Sets.newHashSet(clazz) : TypeToken.of(clazz).getTypes().rawTypes();

        for (Method method : clazz.getMethods())
        {
            if (isStatic && !Modifier.isStatic(method.getModifiers()))
                continue;
            else if (!isStatic && Modifier.isStatic(method.getModifiers()))
                continue;

            for (Class<?> cls : supers)
            {
                try
                {
                    Method real = cls.getDeclaredMethod(method.getName(), method.getParameterTypes());
                    if (real.isAnnotationPresent(SubscribeEvent.class))
                    {
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        if (parameterTypes.length != 1)
                        {
                            throw new IllegalArgumentException(
                                    "Method " + method + " has @SubscribeEvent annotation, but requires " + parameterTypes.length +
                                            " arguments.  Event handler methods must require a single argument."
                            );
                        }

                        Class<?> eventType = parameterTypes[0];

                        if (!Event.class.isAssignableFrom(eventType))
                        {
                            throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent annotation, but takes a argument that is not an Event " + eventType);
                        }


                        register(eventType, new AccessorHandler<>( isStatic ? null:obj, real));
                        break;
                    }
                }
                catch (NoSuchMethodException e)
                {
                    ; // Eat the error, this is not unexpected
                }
            }
        }
    }

    public <T> T post(T event){
        if (subscribers.containsKey(event.getClass())){
            for(Consumer<?> consumer:subscribers.get(event.getClass())){
                ((Consumer<T>)consumer).accept(event);
            }
        }
        return event;
    }

    public static class AccessorHandler<T,B> extends MethodAccessor<Void,B> implements Consumer<T>{
        public B instance;

        public AccessorHandler(B instance, Method method) {
            super(method);
            this.instance = instance;
        }

        @Override
        public void accept(T t) {
            invoke(instance, t);
        }
    }
}
