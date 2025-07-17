package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.*;
import rml.deserializer.AbstractDeserializer;
import rml.loader.api.config.v2.config.ConfigUtils;
import rml.loader.api.utils.ObjectHelper;
import rml.loader.deserialize.Deserializer;

public class ConfigEnum extends ConfigPrimitive{
    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "enum"))
            .require(String.class, "name")
            .require(String.class, "defaultValue")
            .require(String[].class, "enums")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .decode(context ->
                    new ConfigEnum(
                            context.get(ConfigDescription.class, "display"),
                            context.get(String.class, "name"),
                            context.get(String.class, "defaultValue"),
                            context.get(String[].class, "enums"))).build();

    protected Class<?> enumType;
    protected Enum<?> defaultValue;
    protected String defaultValueString;
    protected String[] enums;

    public ConfigEnum(ConfigDescription parentIn, String nameIn, String defaultValue, String[] enums) {
        super(parentIn, nameIn);
        this.defaultValueString = defaultValue;
        this.enums = enums;
    }

    @Override
    public void bind(ConfigGroup group) {
        super.bind(group);
        String enumName = ConfigUtils.CONFIG_CLASS_PREFIX + group.getAbsoluteName() + ".Enum" + this.getName();
        {
            String enumInternalName = enumName.replace('.', '/');
            ClassWriter classWriter = new ClassWriter(0);
            FieldVisitor fieldVisitor;
            MethodVisitor methodVisitor;
            AnnotationVisitor annotationVisitor0;

            classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM, enumInternalName, "Ljava/lang/Enum<L" + enumInternalName + ";>;", "java/lang/Enum", null);

            classWriter.visitSource(".dynamic", null);
            {
                annotationVisitor0 = classWriter.visitAnnotation("Lstanhebben/zenscript/annotations/ZenClass;", true);
                annotationVisitor0.visit("value", enumName);
                annotationVisitor0.visitEnd();
            }
            {
                fieldVisitor = classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC | Opcodes.ACC_ENUM, "$VALUES", "[L" + enumInternalName + ";", null, null);
                fieldVisitor.visitEnd();
            }
            {
                methodVisitor = classWriter.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                methodVisitor.visitCode();
                int idx = 0;
                for (String desc : enums) {
                    {
                        fieldVisitor = classWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC | Opcodes.ACC_ENUM, desc, "L" + enumInternalName + ";", null, null);
                        fieldVisitor.visitEnd();
                    }

                    methodVisitor.visitTypeInsn(Opcodes.NEW, enumInternalName);
                    methodVisitor.visitInsn(Opcodes.DUP);
                    methodVisitor.visitLdcInsn(desc);
                    pushInt(methodVisitor, idx);
                    idx += 1;
                    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, enumInternalName, "<init>", "(Ljava/lang/String;I)V", false);
                    methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, enumInternalName, desc, "L" + enumInternalName + ";");
                }
                pushInt(methodVisitor, idx);
                methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, enumInternalName);
                idx = 0;
                for (String desc : enums) {
                    methodVisitor.visitInsn(Opcodes.DUP);
                    pushInt(methodVisitor, idx);
                    idx += 1;
                    methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, enumInternalName, desc, "L" + enumInternalName + ";");
                    methodVisitor.visitInsn(Opcodes.AASTORE);
                }
                methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, enumInternalName, "$VALUES", "[L" + enumInternalName + ";");
                methodVisitor.visitInsn(Opcodes.RETURN);
                methodVisitor.visitMaxs(4, 0);
                methodVisitor.visitEnd();
            }

            {
                methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()[L"+enumInternalName+";", null, null);
                methodVisitor.visitCode();
                methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, enumInternalName, "$VALUES", "[L"+enumInternalName+";");
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "[L"+enumInternalName+";", "clone", "()Ljava/lang/Object;", false);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "[L"+enumInternalName+";");
                methodVisitor.visitInsn(Opcodes.ARETURN);
                methodVisitor.visitMaxs(1, 0);
                methodVisitor.visitEnd();
            }
            {
                methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "valueOf", "(Ljava/lang/String;)L"+enumInternalName+";", null, null);
                methodVisitor.visitCode();
                methodVisitor.visitLdcInsn(Type.getObjectType(enumInternalName));
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, enumInternalName);
                methodVisitor.visitInsn(Opcodes.ARETURN);
                methodVisitor.visitMaxs(2, 1);
                methodVisitor.visitEnd();
            }
            {
                methodVisitor = classWriter.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", "()V", null);
                methodVisitor.visitCode();
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
                methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
                methodVisitor.visitInsn(Opcodes.RETURN);
                methodVisitor.visitMaxs(3, 3);
                methodVisitor.visitEnd();
            }

            classWriter.visitEnd();

            ConfigUtils.ClassProvider.classes.put(enumName, classWriter.toByteArray());
        }
        try {
            this.enumType = Class.forName(enumName);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        this.defaultValue = Enum.valueOf(ObjectHelper.static_cast(enumType), defaultValueString);
    }


    @Override
    public String createDescription() {
        return Type.getDescriptor(enumType);
    }

    @Override
    public void createToStack(MethodVisitor methodVisitor) {
        createToStack0(methodVisitor, this.defaultValue);
    }

    public static void createToStack0(MethodVisitor methodVisitor, Enum<?> val) {
        String name = Type.getType(val.getDeclaringClass()).getInternalName();
        methodVisitor.visitLdcInsn(val.name());
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, name, "valueOf", "(Ljava/lang/String;)L" + name + ";", false);
    }
}
