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
    public static void transform(ClassNode classNode){
        classNode.visibleAnnotations.add(new AnnotationNode("Lstanhebben/zenscript/annotations/ZenClass;"));
        for (FieldNode fieldNode : classNode.fields){
            fieldNode.visibleAnnotations.add(new AnnotationNode("Lstanhebben/zenscript/annotations/ZenProperty;"));
        }
        for (MethodNode methodNode : classNode.methods){
            methodNode.visibleAnnotations.add(new AnnotationNode("Lstanhebben/zenscript/annotations/ZenMethod;"));
        }
    }
}
