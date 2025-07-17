package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import rml.deserializer.*;
import rml.loader.api.utils.ObjectHelper;
import rml.loader.deserialize.Deserializer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class ConfigMap extends ConfigPrimitive {
    public enum ConfigMapType{
        INT(Integer.class),
        DOUBLE(Double.class),
        STRING(String.class),
        BOOL(Boolean.class),
        INTS(Integer[].class),
        DOUBLES(Double[].class),
        STRINGS(String[].class),
        BOOLS(Boolean[].class)
        ;

        public static final AbstractDeserializer<ConfigMapType> DESERIALIZER = Deserializer.MANAGER.addDefaultEntry(
                new AbstractDeserializer<>(new ResourceLocation("rml", "type"),
                        ConfigMapType.class, jsonElement -> {
                            try {
                                return ConfigMapType.valueOf(Deserializer.decode(String.class, jsonElement));
                            } catch (IllegalArgumentException e) {
                                throw new JsonDeserializeException(jsonElement, "ConfigMapType not found", e);
                            }
                        }));

        private final Class<?> cls;
        ConfigMapType(Class<?> cls){
            this.cls = cls;
        }

        public Class<?> getType() {
            return cls;
        }
    }
    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "map"))
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .require(String.class, "name")
            .require(ConfigMapType.class, "map_type")
            .action((manager, jsonObject, context) -> {
                if (context.getJsonObject().has("defaultValue")) {
                    Argument.map("defaultValue", context.get(ConfigMapType.class, "map_type").getType()).execute(Deserializer.MANAGER, context.getJsonObject().get("defaultValue").getAsJsonObject(), context);
                } else context.put("defaultValue", new HashMap<>());
            })
            .decode(context -> new ConfigMap(
                    context.get(ConfigDescription.class, "display"),
                    context.get(String.class, "name"),
                    context.get(ConfigMapType.class, "map_type").getType(),
                    ObjectHelper.static_cast(context.get(Map.class, "defaultValue")))).build();

    protected Class<?> type;
    protected Map<String, ?> defaultVal;

    public ConfigMap(ConfigDescription parentIn, String nameIn, Class<?> type, Map<String, ?> defaultVal) {
        super(parentIn, nameIn);
        this.type = type;
        this.defaultVal = defaultVal == null ? new HashMap<>() : defaultVal;
    }

    @Override
    public String createDescription() {
        return "Ljava/util/HashMap;";
    }

    @Override
    public String createSignature() {
        return "Ljava/util/HashMap<Ljava/lang/String;"+ Type.getDescriptor(this.type) +">;";
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        methodVisitor.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);

        BiConsumer<MethodVisitor, ?> consumer = cast(STACK_PUTTER.get(this.type));

        for (Map.Entry<String, ?> entry : this.defaultVal.entrySet()) {
            methodVisitor.visitInsn(Opcodes.DUP);
            methodVisitor.visitLdcInsn(entry.getKey());
            consumer.accept(methodVisitor, cast(entry.getValue()));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            methodVisitor.visitInsn(Opcodes.POP);
        }
    }

    @SuppressWarnings("unchecked")
    private static<T> T cast(Object o) {
        return (T)o;
    }

    static Map<Class<?>, BiConsumer<MethodVisitor, ?>> STACK_PUTTER = new HashMap<>();

    private static<T> void addStackPutter(final Class<T> c, BiConsumer<MethodVisitor, T> methodVisitorTBiConsumer) {
        STACK_PUTTER.put(c, methodVisitorTBiConsumer);
        STACK_PUTTER.put(java.lang.reflect.Array.newInstance(c, 0).getClass(), (methodVisitor, object) -> {
            T[] t = cast(object);
            pushInt(methodVisitor, t.length);
            methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, c.getName().replace('.', '/'));
            for (int i = 0; i < t.length; i ++) {
                methodVisitor.visitInsn(Opcodes.DUP);
                pushInt(methodVisitor, i);
                methodVisitorTBiConsumer.accept(methodVisitor, t[i]);
                methodVisitor.visitInsn(Opcodes.AASTORE);
            }
        });
    }


    static {
        addStackPutter(Boolean.class, (methodVisitor, aBoolean) -> {
            if (aBoolean == null) methodVisitor.visitInsn(Opcodes.ACONST_NULL);
            else if (aBoolean == Boolean.TRUE) {
                methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;");
            } else methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
        });
        addStackPutter(Double.class, (methodVisitor, aByte) -> {
            methodVisitor.visitLdcInsn(aByte);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        });
        addStackPutter(Enum.class, ConfigEnum::createToStack0);
        addStackPutter(Integer.class, (methodVisitor, aByte) -> {
            pushInt(methodVisitor, aByte);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        });
        addStackPutter(String.class, MethodVisitor::visitLdcInsn);
    }
}
