package mods.Hileb.rml.core;

import net.minecraftforge.fml.common.FMLLog;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/14 23:01
 **/
public class ASMUtil {
    public static File gameDir;
    public static final boolean saveTransformedClass= FMLLog.log.isDebugEnabled();
    public static byte[] push(String rawName,byte[] clazz){
        if (saveTransformedClass){
            final File outRoot=new File(gameDir,"clazzs/");
            final File outFile = new File(outRoot, rawName.replace('.', File.separatorChar) + ".class");
            final File outDir = outFile.getParentFile();

            if (!outRoot.exists()) {
                outRoot.mkdirs();
            }
            if (!outDir.exists()) {
                outDir.mkdirs();
            }

            if (outFile.exists()) {
                outFile.delete();
            }

            try {
                final OutputStream output = new FileOutputStream(outFile);
                output.write(clazz);
                output.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return clazz;
    }
    public static void injectBeforeAllInsnNode(InsnList method, InsnList hook, int code){
        LinkedList<AbstractInsnNode> nodes=new LinkedList<>();
        ListIterator<AbstractInsnNode> iterator=method.iterator();
        AbstractInsnNode node;
        while (iterator.hasNext()){
            node=iterator.next();
            if (node.getOpcode()==code){
                nodes.add(node);
            }
        }
        for(AbstractInsnNode node1:nodes){
            method.insertBefore(node1,hook);
        }
    }
}
