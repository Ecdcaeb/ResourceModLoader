package rml.loader.api.bus;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class DefaultEventBus<EVENT> implements EventBus<EVENT>{
    protected final EventBus<? super EVENT> parent;
    protected final List<IEventHandler<EVENT>> handlers;

    protected DefaultEventBus(){
        this.handlers = new LinkedList<>();
        this.parent = null;
    }

    protected DefaultEventBus(EventBus<? super EVENT> parentIn) {
        this.parent = parentIn;
        this.handlers = new LinkedList<>();
    }

    @Override
    public <T extends EVENT> T post(T evt) {
        for (IEventHandler<EVENT> handler : this.handlers) {
            handler.accept(evt);
        }
        if (parent != null) {
            return parent.post(evt);
        } else return evt;
    }

    public IEventHandler<EVENT> register(IEventHandler<EVENT> handler){
        this.handlers.add(handler);
        return handler;
    }

    public IEventHandler<EVENT> register(String label, IEventHandler<EVENT> handler) {
        this.handlers.add(handler = new IEventHandler.WrappedOwnerEventHandler<>(new IEventHandler.WrappedOwnerEventHandler.Owner.StringOwner(label), handler));
        return handler;
    }

    public <T> T unregister(T token) {
        Iterator<IEventHandler<EVENT>> iterator = this.handlers.iterator();
        while (iterator.hasNext()) {
            iterator.next().isOwner(token);
            iterator.remove();
        }
        return token;
    }

    public static<T> DefaultEventBus<T> of(){
        return new DefaultEventBus<>();
    }

    public static<V, T extends V> DefaultEventBus<T> of(DefaultEventBus<V> parent) {
        return new DefaultEventBus<T>(parent);
    }

    @FunctionalInterface
    public interface IEventHandler<EVENT>{
        void accept(EVENT event);

        default boolean isOwner(Object obj) {
            return obj == this;
        }

        class ReflectionMethodEventHandler<EVENT> implements IEventHandler<EVENT> {
            private final Object instance;
            private final Method method;
            public ReflectionMethodEventHandler(Object instance, Method method) {
                this.instance = instance;
                this.method = method;
            }

            @Override
            public void accept(EVENT o) {
                try {
                    this.method.invoke(this.instance, o);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }

            public Method getMethod() {
                return method;
            }

            public Object getInstance() {
                return instance;
            }

            @Override
            public boolean isOwner(Object obj) {
                return IEventHandler.super.isOwner(obj) || (this.instance == null ? obj == method.getDeclaringClass() : obj == this.instance);
            }
        }

        class UnreflectedMethodEventHandler<EVENT> extends ReflectionMethodEventHandler<EVENT> {
            MethodHandle methodHandle;
            public UnreflectedMethodEventHandler(Class<?> evt, Object instance, Method method) {
                super(instance, method);
                try {
                    this.methodHandle = MethodHandles.lookup().in(evt).unreflect(method);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void accept(EVENT o) {
                try {
                    if (Modifier.isStatic(this.getMethod().getModifiers())) {
                        this.methodHandle.invoke(o);
                    } else this.methodHandle.invoke(this.getInstance(), o);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        }

        class DynamicEventHandler<EVENT> implements IEventHandler<EVENT> {
            protected IEventHandler<EVENT> eventiEventHandler;
            protected Predicate<EVENT> typehander;

            public DynamicEventHandler(Predicate<EVENT> typehander, IEventHandler<EVENT> eventiEventHandler) {
                this.eventiEventHandler = eventiEventHandler;
                this.typehander = typehander;
            }

            public DynamicEventHandler(Type generic, IEventHandler<EVENT> eventiEventHandler) {
                this.eventiEventHandler = eventiEventHandler;
                this.typehander = (evt) -> evt instanceof Generic && ((Generic)evt).getGenericType() == generic;
            }

            @Override
            public void accept(EVENT event) {
                if (this.typehander.test(event)) {
                    this.eventiEventHandler.accept(event);
                }
            }

            @FunctionalInterface
            public interface Generic {
                Type getGenericType();
            }
        }

        class WrappedOwnerEventHandler<EVENT> implements IEventHandler<EVENT> {
            protected IEventHandler<EVENT> eventiEventHandler;
            protected Owner owner;
            public WrappedOwnerEventHandler(Owner owner, IEventHandler<EVENT> eventiEventHandler) {
                this.eventiEventHandler = eventiEventHandler;
                this.owner = owner;
            }

            @Override
            public void accept(EVENT event) {
                try {
                    this.owner.beforeCall();
                    this.eventiEventHandler.accept(event);
                    this.owner.afterCall();
                } catch (Throwable throwable) {
                    this.owner.handlerError(throwable);
                }
            }

            @Override
            public boolean isOwner(Object obj) {
                return IEventHandler.super.isOwner(obj) || (this.owner != null && this.owner.equals(obj));
            }

            public interface Owner {
                default void beforeCall(){}
                default void afterCall(){}
                default String getLabel(){ return "UnknownOwner";}
                default void handlerError(Throwable throwable){
                    throw new RuntimeException("Error at invoke listener of " + getLabel(), throwable);
                }

                class ModOwner implements Owner {
                    protected ModContainer modContainer;
                    public ModOwner(ModContainer modContainer) {
                        this.modContainer = modContainer;
                    }

                    public ModOwner(String modid) {
                        this(Loader.instance().getIndexedModList().get(modid));
                    }

                    public ModOwner() {
                        this(Loader.instance().activeModContainer());
                    }

                    @Override
                    public void beforeCall() {
                        Loader.instance().setActiveModContainer(this.modContainer);
                    }

                    @Override
                    public void afterCall() {
                        Loader.instance().setActiveModContainer(null);
                    }

                    @Override
                    public String getLabel() {
                        return "mod{id:" + modContainer.getModId() + ", name:" + modContainer.getName() +"}";
                    }

                    @Override
                    public final boolean equals(Object o) {
                        if (!(o instanceof ModOwner)) return false;

                        ModOwner modOwner = (ModOwner) o;
                        return Objects.equals(modContainer, modOwner.modContainer);
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hashCode(modContainer);
                    }
                }

                class StringOwner implements Owner {
                    protected String tag;

                    public StringOwner(String tag) {
                        this.tag = tag;
                    }

                    @Override
                    public String getLabel() {
                        return tag;
                    }

                    @Override
                    public boolean equals(Object obj) {
                        return this.tag.equals(String.valueOf(obj));
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hashCode(tag);
                    }
                }

                class ClassOwner extends StringOwner {

                    public ClassOwner(Class<?> cls) {
                        super(cls.getName());
                    }
                }
            }
        }
    }
}
