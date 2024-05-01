package mods.rml.compat.crt;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/1 10:46
 **/
public class CrTAutoZenTransformer {
    public static int transform(ClassNode classNode){
        classNode.visibleAnnotations.add(new AnnotationNode("Lstanhebben/zenscript/annotations/ZenClass;"));
        for (FieldNode fieldNode : classNode.fields){
            fieldNode.visibleAnnotations.add(new AnnotationNode("Lstanhebben/zenscript/annotations/ZenProperty;"));
        }
        for (MethodNode methodNode : classNode.methods){
            if ("<clinit>".equals(methodNode.name)) continue;
            else if (!"<init>".equals(methodNode.name))methodNode.visibleAnnotations.add(new AnnotationNode("Lstanhebben/zenscript/annotations/ZenMethod;"));
            else methodNode.visibleAnnotations.add(new AnnotationNode("Lstanhebben/zenscript/annotations/ZenConstructor;"));
        }
        return 0;
    }
}
