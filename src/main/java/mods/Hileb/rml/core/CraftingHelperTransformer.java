package mods.Hileb.rml.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Iterator;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/5 22:55
 **/
@SuppressWarnings("unused")
public class CraftingHelperTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if ("net.minecraftforge.common.crafting.CraftingHelper".equals(transformedName)){
            if (basicClass!=null){


                ClassReader classReader=new ClassReader(basicClass);
                ClassNode cn=new ClassNode();
                classReader.accept(cn, 0);

                for(MethodNode mn:cn.methods){
                    if ("init".equals(mn.name)){
                        Iterator<AbstractInsnNode> iterator=mn.instructions.iterator();
                        AbstractInsnNode returnNode=null;
                        while (iterator.hasNext()){
                            returnNode=iterator.next();
                            if (returnNode.getOpcode()==Opcodes.RETURN)break;
                        }

                        mn.instructions.insertBefore(returnNode,new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/api/event/CraftingHelperInitEvent","post","()V"));
                        break;
                    }
                }

                ClassWriter classWriter=new ClassWriter(classReader,ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
                cn.accept(classWriter);
                return classWriter.toByteArray();


            }else return null;
        }
        else return basicClass;
    }
}
