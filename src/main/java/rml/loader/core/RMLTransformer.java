package rml.loader.core;

import crafttweaker.mc1120.CraftTweaker;
import rml.jrx.announces.EarlyClass;
import rml.jrx.announces.PrivateAPI;
import rml.jrx.asm.MethodName;
import rml.jrx.utils.Tasks;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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
    public static final MethodName m_193061 = MethodName.of()
            .mcp("loadFunctions","()V")
            .srg("func_193061_h","()V")
            .notch("h","()V")
            .build();
    public static final MethodName m_175276 = MethodName.of()
            .mcp("handleComponentClick", "(Lnet/minecraft/util/text/ITextComponent;)Z")
            .srg("func_175276_a", "(Lnet/minecraft/util/text/ITextComponent;)Z")
            .notch("a", "(Lhh;)Z")
            .build();
    public static final MethodName m_175272 = MethodName.of()
            .mcp("handleComponentHover", "(Lnet/minecraft/util/text/ITextComponent;II)V")
            .srg("func_175272_a", "(Lnet/minecraft/util/text/ITextComponent;II)V")
            .notch("a", "(Lhh;II)V")
            .build();
    public static final MethodName m_179140 = MethodName.of()
            .mcp("disableLighting", "()V")
            .srg("func_179140_f", "()V")
            .notch("g", "()V")
            .build();


    public static final HashMap<String, ToIntFunction<ClassNode>> transformers=new HashMap<>();
    public static final HashSet<GlobalTransformer> globalTransformers = new HashSet<>();
    static {
        Transformers.initMinecraftTransformers();
        Transformers.initForgeTransformers();
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

    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2024/3/11 23:00
     **/
    public static class Transformers {
        public static void initForgeTransformers(){
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
                                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/api/event/CraftingHelperInitEvent","post","()V",false));
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
                                injectList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/core/RMLModDiscover","inject","(Ljava/util/List;)V",false));
                                mn.instructions.insertBefore(mn.instructions.get(0),injectList);
                                return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                            }
                        }
                        return -1;
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
                                            methodInsnNode.owner= "rml/loader/api/config/ConfigPatcher";
                                            methodInsnNode.name="registerCfg";
                                            methodInsnNode.itf=false;
                                            methodInsnNode.desc="(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                                            return ClassWriter.COMPUTE_MAXS  | ClassWriter.COMPUTE_FRAMES;
                                        }
                                    }
                                }
                            }
                        }
                        return -1;
                    }
            );
            transformers.put("net.minecraftforge.client.ForgeHooksClient",
                    (cn)->{
                        for(MethodNode mn: cn.methods){
                            if ("renderMainMenu".equals(mn.name) && "(Lnet/minecraft/client/gui/GuiMainMenu;Lnet/minecraft/client/gui/FontRenderer;IILjava/lang/String;)Ljava/lang/String;".equals(mn.desc)){
                                ASMUtil.injectBefore(mn.instructions, ()->{
                                    InsnList hook = new InsnList();
                                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/deserialize/RMLLoaders$MCMainScreenTextLoader", "processComponent", "(Ljava/lang/String;)Ljava/lang/String;", false));
                                    return hook;
                                }, (node)->node.getOpcode()==Opcodes.ARETURN);
                                return ClassWriter.COMPUTE_MAXS  | ClassWriter.COMPUTE_FRAMES;
                            }
                        }
                        return -1;
                    });
            transformers.put("net.minecraftforge.fml.client.GuiModList$Info",
                    (cn)->{
                        for(MethodNode mn:cn.methods){
                            if ("<init>".equals(mn.name)){
                                ListIterator<AbstractInsnNode> iterator = mn.instructions.iterator();
                                AbstractInsnNode node;
                                while (iterator.hasNext()){
                                    node = iterator.next();
                                    if (node instanceof MethodInsnNode){
                                        MethodInsnNode methodInsnNode = (MethodInsnNode) node;
                                        if ("resizeContent".equals(methodInsnNode.name)){
                                            iterator.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                            iterator.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                            iterator.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                            iterator.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraftforge/fml/client/GuiModList", "selectedMod", "Lnet/minecraftforge/fml/common/ModContainer;"));
                                            iterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/api/event/client/gui/ModMenuInfoEvent", "post", "(Ljava/util/List;Ljava/lang/Object;Lnet/minecraftforge/fml/client/GuiModList;Lnet/minecraftforge/fml/common/ModContainer;)Ljava/util/List;", false));
                                            return ClassWriter.COMPUTE_MAXS;
                                        }
                                    }
                                }
                            }
                        }
                        return -1;
                    });
        }
        public static void initMinecraftTransformers(){
            transformers.put("net.minecraft.advancements.FunctionManager",
                    (cn)->{
                        for(MethodNode mn:cn.methods){
                            /**
                             FunctionLoadEvent.post(this);
                             }
                             * **/
                            if (m_193061.is(mn)){
                                ASMUtil.injectBefore(mn.instructions, ()->{
                                    InsnList hook=new InsnList();
                                    hook.add(new IntInsnNode(Opcodes.ALOAD,0));
                                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/api/event/FunctionLoadEvent","post","(Lnet/minecraft/advancements/FunctionManager;)V",false));
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
                                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/api/event/LootTableRegistryEvent","post","()V",false));
                                    return hook;
                                }, (node)->node.getOpcode()==Opcodes.RETURN);
                                return ClassWriter.COMPUTE_MAXS  | ClassWriter.COMPUTE_FRAMES;
                            }
                        }
                        return -1;
                    });

            transformers.put("net.minecraftforge.fml.common.LoadController",
                    (cn)->{
                        for(MethodNode mn:cn.methods){
                            if ("distributeStateMessage".equals(mn.name) && "(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V".equals(mn.desc)){
                                //beforeFMLBusEventSending(Lnet/minecraftforge/fml/common/event/FMLEvent;)V
                                InsnList hook=new InsnList();
                                hook.add(new VarInsnNode(Opcodes.ALOAD,0));
                                hook.add(new VarInsnNode(Opcodes.ALOAD,1));
                                hook.add(new VarInsnNode(Opcodes.ALOAD,2));
                                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/compat/fml/RMLFMLHooks","beforeFMLBusEventSending","(Lnet/minecraftforge/fml/common/LoadController;Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V",false));
                                mn.instructions.insertBefore(mn.instructions.get(0),hook);
                                return ClassWriter.COMPUTE_MAXS  | ClassWriter.COMPUTE_FRAMES;
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
                                            iterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/deserialize/RMLLoaders$MCMainScreenTextLoader", "inject", "(Ljava/util/ArrayList;)Ljava/util/ArrayList;", false));
                                            return ClassWriter.COMPUTE_MAXS  | ClassWriter.COMPUTE_FRAMES;
                                        }
                                    }
                                }
                            }
                        }
                        return -1;
                    });

            transformers.put("net.minecraft.client.gui.GuiScreen",
                    (cn)->{
                        Tasks bar = new Tasks(new String[]{"click", "hover"});
                        for(MethodNode mn : cn.methods){
                            if (m_175276.is(mn)){
                                Label returnTrueLabel = new Label();
                                LabelNode labelNode = new LabelNode(returnTrueLabel);
                                ASMUtil.injectBefore(mn.instructions,
                                        () -> {
                                            InsnList hook = new InsnList();
                                            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/api/event/client/gui/HandleComponentEvent", "postClick", "(Lnet/minecraft/util/text/ITextComponent;Lnet/minecraft/client/gui/GuiScreen;)Z", false));
                                            hook.add(new JumpInsnNode(Opcodes.IFNE, labelNode));
                                            return hook;
                                        }, (node)->{
                                            if (node.getNext()!=null){
                                                node = node.getNext();
                                                if (node.getOpcode() == Opcodes.LDC){
                                                    if ("Don't know how to handle {}".equals(((LdcInsnNode)node).cst)){
                                                        return true;
                                                    }else return false;
                                                }else return false;
                                            }return false;
                                        });
                                mn.instructions.add(labelNode);
                                mn.instructions.add(new InsnNode(Opcodes.ICONST_M1));
                                mn.instructions.add(new InsnNode(Opcodes.IRETURN));
                                bar.complete("click");
                            }else if (m_175272.is(mn)){
                                ASMUtil.injectBefore(mn.instructions,
                                        () -> {
                                            InsnList hook = new InsnList();
                                            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                            hook.add(new VarInsnNode(Opcodes.ILOAD, 2));
                                            hook.add(new VarInsnNode(Opcodes.ILOAD, 3));
                                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/api/event/client/gui/HandleComponentEvent", "postHover", "(Lnet/minecraft/util/text/ITextComponent;Lnet/minecraft/client/gui/GuiScreen;II)V", false));
                                            return hook;
                                        }, (node)->{
                                            if (node.getOpcode() != Opcodes.INVOKESTATIC)return false;
                                            else {
                                                MethodInsnNode md = (MethodInsnNode) node;
                                                return m_179140.is(md.name, md.desc) && ("bus".equals(md.owner) || "net/minecraft/client/renderer/GlStateManager".equals(md.owner));
                                            }
                                        });
                                bar.complete("hover");
                            }
                        }
                        if (bar.isCompleted()) return ClassWriter.COMPUTE_MAXS;
                        else{
                            throw new RuntimeException("RML Cannot Transform Class Correctly. Unhandled Works:" + bar.getFailsString());
                        }
                    });
        }
        public static class Late{
            public static void initModTransformers(Object[] objects){
                ASMDataTable asmDatas = (ASMDataTable)objects[1];
                if (Loader.isModLoaded(CraftTweaker.MODID)){
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
                                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/compat/crt/CrTZenClassRegisterEvent", "post", "()V", false));
                                        mn.instructions.insertBefore(mn.instructions.get(0), hook);
                                        return ClassWriter.COMPUTE_MAXS  | ClassWriter.COMPUTE_FRAMES;
                                    }
                                }
                                return -1;
                            });
                    for (ASMDataTable.ASMData asmData : asmDatas.getAll("crafttweaker/runtime/ITweaker")) {

                        String name = asmData.getClassName().replace('/', '.');
                        transformers.put(name,
                                (cn)->{
                                    for (MethodNode mn : cn.methods) {
                                        if ("setScriptProvider".equals(mn.name)) {
                                            InsnList list = new InsnList();
                                            list.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/compat/crt/RMLCrTLoader", "inject", "(Lcrafttweaker/runtime/IScriptProvider;)Lcrafttweaker/runtime/IScriptProvider;", false));
                                            list.add(new VarInsnNode(Opcodes.ASTORE, 1)); // provider
                                            mn.instructions.insertBefore(mn.instructions.get(0), list);
                                            return ClassWriter.COMPUTE_MAXS;
                                        }
                                    }
                                    return 0;
                                });
                    }
                }
            }
        }

    }
}
