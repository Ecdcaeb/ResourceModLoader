package com.Hileb.custom_colorful_enchantment.internal.utils;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.LinkedList;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/3 12:59
 **/
public class ASMHelper {
    public static void makeFunctionPre(MethodNode mn, InsnList mixinHead){
        mn.instructions.insertBefore(mn.instructions.get(0),mixinHead);
    }
    public static void makeFunctionPost(MethodNode mn, InsnList mixinReturn){
        LinkedList<AbstractInsnNode> nodes=new LinkedList<>();
        for(AbstractInsnNode node:mn.instructions){
            if (node.getOpcode()== Opcodes.RETURN){
                nodes.add(node);
            }
        }
        for(AbstractInsnNode node:nodes){
            mn.instructions.insertBefore(node,mixinReturn);
        }
    }

    public static void visitRender(MethodNode mn){
        InsnList il=new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD,1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"com/Hileb/custom_colorful_enchantment/internal/utils/RenderUtil","callOnHead","(Lnet/minecraft/world/item/ItemStack;)V",false));
        makeFunctionPre(mn,il);
    }
    public static void visitUpdate(MethodNode mn){
        InsnList il=new InsnList();
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"com/Hileb/custom_colorful_enchantment/internal/utils/ShaderHandler","onReload","()V",false));
        makeFunctionPre(mn,il);
    }
}
