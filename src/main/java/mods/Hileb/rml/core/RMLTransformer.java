package mods.Hileb.rml.core;

import mods.Hileb.rml.api.asm.MethodName;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.ModContainer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.ListIterator;
import java.util.function.Function;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/14 22:59
 **/
public class RMLTransformer implements IClassTransformer {
    public static final HashMap<String, Function<ClassNode,Integer>> transformers=new HashMap<>();
    static {
        transformers.put("net.minecraftforge.common.crafting.CraftingHelper",
                (cn)->{
                    for(MethodNode mn:cn.methods){
                        /**
                         *     registerI("forge:ore_dict", (context, json) -> {
                         *             return new OreIngredient(JsonUtils.getString(json, "ore"));
                         *         });
                         *         CraftingHelperInitEvent.post();
                         *     }
                         * **/
                        if ("init".equals(mn.name)){
                            InsnList hook=new InsnList();
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/api/event/CraftingHelperInitEvent","post","()V",false));
                            ASMUtil.injectBeforeAllInsnNode(mn.instructions,hook,Opcodes.RETURN);
                            return ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES;
                });
        transformers.put("net.minecraftforge.fml.common.Loader",
                (cn)->{
                    for(MethodNode mn:cn.methods){
                        /**
                         *  private void identifyDuplicates(List<ModContainer> mods) {
                         *         RMLModDiscover.inject(mods);
                         * **/
                        if ("identifyDuplicates".equals(mn.name)){
                            InsnList injectList=new InsnList();
                            injectList.add(new IntInsnNode(Opcodes.ALOAD,1));
                            injectList.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/core/RMLModDiscover","inject","(Ljava/util/List;)V",false));
                            mn.instructions.insertBefore(mn.instructions.get(0),injectList);
                            return ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES;
                });
        transformers.put("net.minecraft.advancements.FunctionManager",
                (cn)->{
                    for(MethodNode mn:cn.methods){
                        /**
                         *
                                FunctionLoadEvent.post(this);
                            }
                         * **/
                        if (MethodName.m_193061.is(mn)){
                            InsnList hook=new InsnList();
                            hook.add(new IntInsnNode(Opcodes.ALOAD,0));
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/api/event/FunctionLoadEvent","post","(Lnet/minecraft/advancements/FunctionManager;)V",false));
                            ASMUtil.injectBeforeAllInsnNode(mn.instructions,hook,Opcodes.RETURN);
                            return ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES;
                });
        transformers.put("net.minecraft.world.storage.loot.LootTableManager",
                (cn)->{
                    for(MethodNode mn: cn.methods){
                        /**
                         * static {
                         *         LootTableRegistryEvent.post();
                         *     }
                         * **/
                        if ("<clinit>".equals(mn.name)){
                            InsnList hook=new InsnList();
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/api/event/LootTableRegistryEvent","post","()V",false));
                            ASMUtil.injectBeforeAllInsnNode(mn.instructions,hook,Opcodes.RETURN);
                        }
                    }
                    return ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES;
                });
        transformers.put("crafttweaker.runtime.CrTTweaker",
                (cn)->{
                    for(MethodNode mn:cn.methods){
                        /**
                         * public void setScriptProvider(IScriptProvider provider) {
                         *         this.scriptProvider = provider;
                         *         RMLCrTLoader.inject(this);
                         *     }
                         **/
                        if ("setScriptProvider".equals(mn.name)){
                            InsnList hook=new InsnList();
                            hook.add(new IntInsnNode(Opcodes.ALOAD,0));
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/compat/crt/RMLCrTLoader","inject","(Lcrafttweaker/runtime/CrTTweaker;)V",false));
                            ASMUtil.injectBeforeAllInsnNode(mn.instructions,hook,Opcodes.RETURN);
                            return ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES;
                });
        transformers.put("net.minecraftforge.common.config.ConfigManager",
                (cn)->{
                    for(MethodNode mn:cn.methods){
                        // public static sync(Ljava/lang/String;Lnet/minecraftforge/common/config/Config$Type;)V
                        if ("sync".equals(mn.name) && "(Ljava/lang/String;Lnet/minecraftforge/common/config/Config$Type;)V".equals(mn.desc)){
                            ListIterator<AbstractInsnNode> iterator=mn.instructions.iterator();
                            AbstractInsnNode node;
                            while (iterator.hasNext()){
                                node=iterator.next();
                                // INVOKESTATIC net/minecraftforge/common/config/ConfigManager.sync (Lnet/minecraftforge/common/config/Configuration;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/Object;)V
                                if (node.getOpcode()==Opcodes.INVOKESTATIC && node instanceof MethodInsnNode){
                                    MethodInsnNode methodInsnNode=(MethodInsnNode) node;
                                    if ("net/minecraftforge/common/config/ConfigManager".equals(methodInsnNode.owner) && "sync".equals(methodInsnNode.name) && "(Lnet/minecraftforge/common/config/Configuration;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/Object;)V".equals(methodInsnNode.desc)){
                                        methodInsnNode.owner="mods/Hileb/rml/api/config/ConfigTransformer";
                                        return ClassWriter.COMPUTE_MAXS;
                                    }
                                }
                            }
                        }
                    }
                    return ClassWriter.COMPUTE_MAXS;
                }
        );
    }
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass!=null && basicClass.length>0){
            try{
                if (transformers.containsKey(transformedName)){
                    ClassReader classReader=new ClassReader(basicClass);
                    ClassNode cn=new ClassNode();
                    classReader.accept(cn, 0);

                    int flags=transformers.get(transformedName).apply(cn);

                    ClassWriter classWriter=new ClassWriter(classReader,flags);
                    cn.accept(classWriter);
                    return ASMUtil.push(transformedName,classWriter.toByteArray());
                }else return basicClass;
            }catch (Exception e){
                e.printStackTrace();
                return basicClass;
            }
        }
        return basicClass;
    }
}
