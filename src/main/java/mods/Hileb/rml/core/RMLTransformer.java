package mods.Hileb.rml.core;

import mods.Hileb.rml.api.EarlyClass;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.asm.MethodName;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.function.ToIntFunction;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/14 22:59
 **/
@EarlyClass
@PrivateAPI
public class RMLTransformer implements IClassTransformer {
    public static final MethodName m_193061=MethodName.of()
            .mcp("loadFunctions","()V")
            .srg("func_193061_h","()V")
            .notch("h","()V")
            .build();


    public static final HashMap<String, ToIntFunction<ClassNode>> transformers=new HashMap<>();
    public static final HashSet<GlobalTransformer> globalTransformers = new HashSet<>();
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
                            ASMUtil.injectBefore(mn.instructions, ()->{
                                InsnList hook=new InsnList();
                                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/api/event/CraftingHelperInitEvent","post","()V",false));
                                return hook;
                            }, (node)->node.getOpcode()==Opcodes.RETURN);
                            return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return -1;
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
                            return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return -1;
                });
        transformers.put("net.minecraft.advancements.FunctionManager",
                (cn)->{
                    for(MethodNode mn:cn.methods){
                        /**
                         *
                                FunctionLoadEvent.post(this);
                            }
                         * **/
                        if (m_193061.is(mn)){
                            ASMUtil.injectBefore(mn.instructions, ()->{
                                InsnList hook=new InsnList();
                                hook.add(new IntInsnNode(Opcodes.ALOAD,0));
                                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/api/event/FunctionLoadEvent","post","(Lnet/minecraft/advancements/FunctionManager;)V",false));
                                return hook;
                            }, (node)->node.getOpcode() == Opcodes.RETURN);
                            return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return -1;
                });
        transformers.put("net.minecraft.world.storage.loot.LootTableList",
                (cn)->{
                    for(MethodNode mn: cn.methods){
                        /**
                         * static {
                         *         LootTableRegistryEvent.post();
                         *     }
                         * **/
                        if ("<clinit>".equals(mn.name)){
                            ASMUtil.injectBefore(mn.instructions, ()->{
                                InsnList hook=new InsnList();
                                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/api/event/LootTableRegistryEvent","post","()V",false));
                                return hook;
                            }, (node)->node.getOpcode()==Opcodes.RETURN);
                            return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return -1;
                });
        globalTransformers.add(new GlobalTransformer() {
            @Override
            public boolean isTarget(ClassNode cn) {
                boolean check = cn.interfaces.contains("crafttweaker/runtime/ITweaker"); // make all ITweak Injected.
                if (check) for(MethodNode mn:cn.methods){
                    if ("setScriptProvider".equals(mn.name)){
                        if(mn.instructions.size() >0){
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public int apply(ClassNode cn) {
                for (MethodNode mn : cn.methods) {
                    if ("setScriptProvider".equals(mn.name)) {
                        InsnList list = new InsnList();
                        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mods/Hileb/rml/compat/crt/RMLCrTLoader", "inject", "(Lcrafttweaker/runtime/IScriptProvider;)Lcrafttweaker/runtime/IScriptProvider;", false));
                        list.add(new VarInsnNode(Opcodes.ASTORE, 1)); // provider
                        mn.instructions.insertBefore(mn.instructions.get(0), list);
                        return ClassWriter.COMPUTE_MAXS;
                    }
                }
                return ClassWriter.COMPUTE_MAXS;
            }
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
                                if (node.getOpcode()==Opcodes.INVOKEINTERFACE && node instanceof MethodInsnNode){
                                    MethodInsnNode methodInsnNode=(MethodInsnNode) node;
                                    if ("java/util/Map".equals(methodInsnNode.owner) && "put".equals(methodInsnNode.name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(methodInsnNode.desc) && methodInsnNode.itf){
                                        methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                                        methodInsnNode.owner="mods/Hileb/rml/api/config/ConfigTransformer";
                                        methodInsnNode.name="registerCfg";
                                        methodInsnNode.itf=false;
                                        methodInsnNode.desc="(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                                        return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                                    }
                                }
                            }
                        }
                    }
                    return -1;
                }
        );
        transformers.put("net.minecraftforge.fml.common.LoadController",
                (cn)->{
                    for(MethodNode mn:cn.methods){
                        if ("distributeStateMessage".equals(mn.name) && "(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V".equals(mn.desc)){
                            //beforeFMLBusEventSending(Lnet/minecraftforge/fml/common/event/FMLEvent;)V
                            InsnList hook=new InsnList();
                            hook.add(new VarInsnNode(Opcodes.ALOAD,0));
                            hook.add(new VarInsnNode(Opcodes.ALOAD,1));
                            hook.add(new VarInsnNode(Opcodes.ALOAD,2));
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mods/Hileb/rml/compat/fml/RMLFMLHooks","beforeFMLBusEventSending","(Lnet/minecraftforge/fml/common/LoadController;Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V",false));
                            mn.instructions.insertBefore(mn.instructions.get(0),hook);
                            return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return -1;
                });
        transformers.put("crafttweaker.mc1120.CraftTweaker",
                (cn)->{
                    for(MethodNode mn:cn.methods){
                        /**
                         *  @EventHandler
                         *     public void onPreInitialization(FMLPreInitializationEvent ev) {
                         *         CrTZenClassRegisterEvent.post();
                         *         PROXY.registerEvents();
                         * **/
                        if ("onPreInitialization".equals(mn.name)){
                            InsnList hook = new InsnList();
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mods/Hileb/rml/compat/crt/CrTZenClassRegisterEvent", "post", "()V", false));
                            mn.instructions.insertBefore(mn.instructions.get(0), hook);
                            return ClassWriter.COMPUTE_MAXS;
                        }
                    }
                    return -1;
        });
        transformers.put("net.minecraft.client.gui.GuiMainMenu",
                (cn)->{
                    for(MethodNode mn:cn.methods){
                        /**
                         *  try {
                         *             List<String> list = MCMainScreenTextLoader.inject(Lists.newArrayList());
                         *             iresource = Minecraft.getMinecraft().getResourceManager().getResource(SPLASH_TEXTS);
                         * **/
                        if ("<init>".equals(mn.name)){
                            ListIterator<AbstractInsnNode> iterator = mn.instructions.iterator();
                            AbstractInsnNode node;
                            while (iterator.hasNext()){
                                node = iterator.next();
                                if (node.getOpcode() == Opcodes.INVOKESTATIC){
                                    MethodInsnNode methodInsnNode = (MethodInsnNode) node;
                                    if ("com/google/common/collect/Lists".equals(methodInsnNode.owner) && "newArrayList".equals(methodInsnNode.name) && "()Ljava/util/ArrayList;".equals(methodInsnNode.desc)){
                                        iterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mods/Hileb/rml/deserialize/RMLDeserializeLoader$MCMainScreenTextLoader", "inject", "(Ljava/util/ArrayList;)Ljava/util/ArrayList;", false));
                                        return ClassWriter.COMPUTE_MAXS;
                                    }
                                }
                            }
                        }
                    }
                    return -1;
                });
        transformers.put("net.minecraftforge.client.ForgeHooksClient",
                (cn)->{
                    for(MethodNode mn: cn.methods){
                        if ("renderMainMenu".equals(mn.name) && "(Lnet/minecraft/client/gui/GuiMainMenu;Lnet/minecraft/client/gui/FontRenderer;IILjava/lang/String;)Ljava/lang/String;".equals(mn.desc)){
                            ASMUtil.injectBefore(mn.instructions, ()->{
                                InsnList hook = new InsnList();
                                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mods/Hileb/rml/deserialize/RMLDeserializeLoader$MCMainScreenTextLoader", "processComponent", "(Ljava/lang/String;)Ljava/lang/String;", false));
                                return hook;
                            }, (node)->node.getOpcode()==Opcodes.ARETURN);
                            return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        }
                    }
                    return -1;
                });
    }
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass!=null && basicClass.length>0){
            try{
                if (!globalTransformers.isEmpty() || transformers.containsKey(transformedName)){
                    ClassReader classReader=new ClassReader(basicClass);
                    ClassNode cn=new ClassNode();
                    classReader.accept(cn, 0);
                    int flags = 0;
                    boolean isTarget = false;
                    for(GlobalTransformer transformer:globalTransformers){
                        if (transformer.isTarget(cn)){
                            flags = transformer.apply(cn);
                            isTarget = true;
                        }
                    }
                    if (transformers.containsKey(transformedName)){
                        flags = transformers.get(transformedName).applyAsInt(cn);
                        isTarget = true;
                    }
                    if (!isTarget) return basicClass;
                    else {
                        ClassWriter classWriter = new ClassWriter(classReader, flags);
                        cn.accept(classWriter);
                        return ASMUtil.push(transformedName, classWriter.toByteArray());
                    }
                }else return basicClass;
            }catch (Exception e){
                e.printStackTrace();
                return basicClass;
            }
        }
        return basicClass;
    }
}
