package mods.Hileb.rml.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/3 9:44
 **/
@SuppressWarnings("unused")
public class RMLLoaderTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if ("net.minecraftforge.fml.common.Loader".equals(transformedName)){
            if (basicClass!=null){


                ClassReader classReader=new ClassReader(basicClass);
                ClassNode cn=new ClassNode();
                classReader.accept(cn, 0);

                for(MethodNode mn:cn.methods){
                    if ("identifyDuplicates".equals(mn.name)){
                        InsnList injectList=new InsnList();
                        injectList.add(new IntInsnNode(Opcodes.ALOAD,1));
                        injectList.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/core/RMLModDiscover","inject","(Ljava/util/List;)V"));
                        mn.instructions.insertBefore(mn.instructions.get(0),injectList);
                        break;
                    }
                }

                ClassWriter classWriter=new ClassWriter(classReader,ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
                cn.accept(classWriter);
                return classWriter.toByteArray();


            }else return null;
        }else return basicClass;
    }

}
