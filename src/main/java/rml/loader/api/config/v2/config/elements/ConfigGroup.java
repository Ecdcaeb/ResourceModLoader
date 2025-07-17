package rml.loader.api.config.v2.config.elements;

import net.minecraft.util.ResourceLocation;
import org.objectweb.asm.*;
import rml.deserializer.AbstractDeserializer;
import rml.loader.api.config.v2.config.ConfigUtils;
import rml.loader.deserialize.Deserializer;

import java.util.*;

public class ConfigGroup extends ConfigElement {
    public static final AbstractDeserializer<ConfigElement> DESERIALIZER = Deserializer.named(ConfigElement.class, new ResourceLocation("rml", "group"))
            .require(String.class, "name")
            .optionalDefault(ConfigDescription.class, "display", ConfigDescription.EMPTY)
            .optionalDefault(ConfigElement[].class, "elements", new ConfigElement[0])
            .decode(context -> {
                ConfigGroup configGroup = new ConfigGroup(context.get(ConfigDescription.class, "display"), context.get(String.class, "name"));
                for (ConfigElement element : context.get(ConfigElement[].class, "elements")) {
                    configGroup.addChild(element);
                }
                return configGroup;
            }).build();

    protected List<ConfigElement> children;

    public ConfigGroup(ConfigDescription parentIn, String nameIn) {
        super(nameIn, parentIn);
        this.children = new ArrayList<>();
    }

    private <T extends ConfigElement> T addChild(T element){
        this.children.add(element);
        element.bind(this);
        return element;
    }

    public String getClassName() {
        String className = ConfigUtils.CONFIG_CLASS_PREFIX + this.getAbsoluteName();
        {
            int idx = className.lastIndexOf('.') + 1;
            if (idx > 0 && idx < className.length()) {
                className = className.substring(0, idx) +
                        Character.toUpperCase(className.charAt(idx)) +
                        className.substring(idx + 1);
            }
        }
        return className;
    }

    public Set<String> getClasses() {
        HashSet<String> hashSet = new HashSet<>();
        for (ConfigElement element : this.children) {
            if (element instanceof ConfigGroup) {
                hashSet.addAll(((ConfigGroup)element).getClasses());
            }
        }
        hashSet.add(this.getClassName());
        return hashSet;
    }

    public void register() {
        register0();
        if (this.parent == null) {
            try {
                ConfigUtils.ConfigAnytimeAnytime.register(Class.forName(getClassName()));
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    protected void register0() {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor = null;
        MethodVisitor methodVisitor = null;
        AnnotationVisitor annotationVisitor0 = null;
        String className = getClassName().replace('.', '/');
        boolean isInstance = this.getParent() != null;

        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, className, null, "java/lang/Object", null);
        classWriter.visitSource(".rml_config_dynamic", null);

        {
            if (!isInstance) {
                annotationVisitor0 = classWriter.visitAnnotation("Lnet/minecraftforge/common/config/Config;", true);
                annotationVisitor0.visit("modid", this.getName());
                if (this.getDisplayName() != null) {
                    annotationVisitor0.visit("name", this.getDisplayName());
                }
                annotationVisitor0.visitEnd();
            }
        }

        methodVisitor = classWriter.visitMethod(isInstance ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                isInstance ? "<init>" : "<clinit>", "()V", null, null);
        methodVisitor.visitCode();
        if (isInstance) {
            // super();
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        }

        for (ConfigElement configElement : this.children) {
            if (configElement instanceof ConfigPrimitive) {
                ConfigPrimitive configPrimitive = (ConfigPrimitive) configElement;
                fieldVisitor = classWriter.visitField(isInstance ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        configPrimitive.getName(), configPrimitive.createDescription(), configPrimitive.createSignature(), null);
                {
                    if (isInstance) methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                    configPrimitive.createToStack(methodVisitor);
                    methodVisitor.visitFieldInsn(isInstance ? Opcodes.PUTFIELD : Opcodes.PUTSTATIC, className, configPrimitive.getName(), configPrimitive.createDescription());
                }
            } else if (configElement instanceof ConfigGroup) {
                ConfigGroup configGroup = (ConfigGroup) configElement;
                configGroup.register0();
                String catClass = configGroup.getClassName().replace('.', '/');
                String desc = 'L' + catClass + ';';
                fieldVisitor = classWriter.visitField(isInstance ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        configGroup.getName(), desc, null, null);

                {
                    // {{configCategory.getName()}} = new {{catClass}}();
                    if (isInstance) methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                    methodVisitor.visitTypeInsn(Opcodes.NEW, catClass);
                    methodVisitor.visitInsn(Opcodes.DUP);
                    methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, catClass, "<init>", "()V", false);
                    methodVisitor.visitFieldInsn(isInstance ? Opcodes.PUTFIELD : Opcodes.PUTSTATIC, className, configGroup.getName(), desc);
                }
            }
            if (fieldVisitor != null) {
                if (configElement.isRequiresWorldRestart()) {
                    annotationVisitor0 = fieldVisitor.visitAnnotation("Lnet/minecraftforge/common/config/Config$RequiresWorldRestart;", true);
                    annotationVisitor0.visitEnd();
                }
                if (configElement.isRequiresMcRestart()) {
                    annotationVisitor0 = fieldVisitor.visitAnnotation("Lnet/minecraftforge/common/config/Config$RequiresMcRestart;", true);
                    annotationVisitor0.visitEnd();
                }
                if (configElement.isSlidingOption()) {
                    annotationVisitor0 = fieldVisitor.visitAnnotation("Lnet/minecraftforge/common/config/Config$SlidingOption;", true);
                    annotationVisitor0.visitEnd();
                }
                if (configElement.getDisplayName() != null) {
                    annotationVisitor0 = fieldVisitor.visitAnnotation("Lnet/minecraftforge/common/config/Config$Name;", true);
                    annotationVisitor0.visit("value", configElement.getDisplayName());
                    annotationVisitor0.visitEnd();
                }
                if (configElement.getComment() != null) {
                    annotationVisitor0 = fieldVisitor.visitAnnotation("Lnet/minecraftforge/common/config/Config$Comment;", true);
                    AnnotationVisitor arrayVisitor = annotationVisitor0.visitArray("value");
                    for (String value : configElement.getComment()) {
                        arrayVisitor.visit(null, value);
                    }
                    arrayVisitor.visitEnd();
                    annotationVisitor0.visitEnd();
                }
                if (configElement.getLangKey() != null) {
                    annotationVisitor0 = fieldVisitor.visitAnnotation("Lnet/minecraftforge/common/config/Config$LangKey;", true);
                    annotationVisitor0.visit("value", configElement.getLangKey());
                    annotationVisitor0.visitEnd();
                }
                if (configElement instanceof ConfigRangedDouble) {
                    ConfigRangedDouble configRangedDouble = (ConfigRangedDouble) configElement;
                    annotationVisitor0 = fieldVisitor.visitAnnotation("Lnet/minecraftforge/common/config/Config$RangeDouble;", true);
                    annotationVisitor0.visit("min", configRangedDouble.getMin());
                    annotationVisitor0.visit("max", configRangedDouble.getMax());
                    annotationVisitor0.visitEnd();
                }
                if (configElement instanceof ConfigRangedInt) {
                    ConfigRangedInt configRangedInt = (ConfigRangedInt) configElement;
                    annotationVisitor0 = fieldVisitor.visitAnnotation("Lnet/minecraftforge/common/config/Config$RangeInt;", true);
                    annotationVisitor0.visit("min", configRangedInt.getMin());
                    annotationVisitor0.visit("max", configRangedInt.getMax());
                    annotationVisitor0.visitEnd();
                }

                fieldVisitor = null;
            }
        }
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitEnd();

        methodVisitor = classWriter.visitMethod(isInstance ? Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC : Opcodes.ACC_PUBLIC,
                isInstance ? "<clinit>" : "<init>", "()V", null, null);
        methodVisitor.visitCode();
        if (!isInstance) {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        }
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitEnd();

        ConfigUtils.ClassProvider.classes.put(className.replace('/', '.'), classWriter.toByteArray());
    }

}
