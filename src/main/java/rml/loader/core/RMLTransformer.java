package rml.loader.core;

import com.google.gson.JsonObject;
import crafttweaker.mc1120.CraftTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import rml.loader.api.annotations.EarlyClass;
import rml.loader.api.annotations.PrivateAPI;
import rml.loader.api.asm.MethodName;
import rml.loader.api.event.early.RMLAddTransformerEvent;
import rml.loader.api.utils.Tasks;

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
    private static final MethodName LOOT_LOOKUP = MethodName.of()
            .mcp("getLootTableFromLocation", "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/world/storage/loot/LootTable;")
            .srg("func_186521_a", "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/world/storage/loot/LootTable;")
            .notch("a", "(Lnf;)Lbfs;").build();
    private static final MethodName TEMPLATE_LOOKUP = MethodName.of()
            .mcp("get", "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/world/gen/structure/template/Template;")
            .srg("func_189942_b", "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/world/gen/structure/template/Template;")
            .notch("b", "(Lnet/minecraft/server/MinecraftServer;Lnf;)Lbcj;").build();
    private static final MethodName TEMPLATE_CREATE = MethodName.of()
            .mcp("getTemplate", "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/world/gen/structure/template/Template;")
            .srg("func_186237_a", "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/world/gen/structure/template/Template;")
            .notch("a", "(Lnet/minecraft/server/MinecraftServer;Lnf;)Lbcj;").build();
    private static final MethodName ADVANCEMENT_LOAD = MethodName.of()
            .mcp("loadAdvancements", "(Ljava/util/Map;)V").srg("func_192083_a", "(Ljava/util/Map;)V")
            .notch("a", "(Ljava/util/Map;)V").classes("net/minecraft/advancements/AdvancementList", "j").build();
    private static final MethodName ITEM_DAMAGE = MethodName.of()
            .mcp("damageItem", "(ILnet/minecraft/entity/EntityLivingBase;)V")
            .srg("func_77972_a", "(ILnet/minecraft/entity/EntityLivingBase;)V")
            .notch("a", "(ILvp;)V").classes("net/minecraft/item/ItemStack", "aip").build();
    private static final MethodName BOW_RELEASE = MethodName.of()
            .mcp("onPlayerStoppedUsing", "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;I)V")
            .srg("func_77615_a", "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;I)V")
            .notch("a", "(Laip;Lamu;Lvp;I)V").build();
    private static final MethodName BOW_AMMUNITION = MethodName.of()
            .mcp("findAmmo", "(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;")
            .srg("func_185060_a", "(Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;")
            .notch("a", "(Laed;)Laip;").classes("net/minecraft/item/ItemBow", "ahg").build();
    private static final MethodName WORLD_SPAWN = MethodName.of()
            .mcp("spawnEntity", "(Lnet/minecraft/entity/Entity;)Z").srg("func_72838_d", "(Lnet/minecraft/entity/Entity;)Z")
            .notch("a", "(Lvg;)Z").classes("net/minecraft/world/World", "amu").build();
    private static final MethodName ITEM_SHRINK = MethodName.of()
            .mcp("shrink", "(I)V").srg("func_190918_g", "(I)V").notch("g", "(I)V")
            .classes("net/minecraft/item/ItemStack", "aip").build();


    public static final HashMap<String, ToIntFunction<ClassNode>> transformers=new HashMap<>();

    //hope not used
    public static final HashSet<GlobalTransformer> globalTransformers = new HashSet<>();

    static {
        //init the bootstrap transformers
        Transformers.initMinecraftTransformers();
        Transformers.initForgeTransformers();
    }

    /**
     * @param name the obfuscated name
     * @param transformedName de-obfuscated name
     * @param basicClass the basic class byte array
     *
     * @return the transformed class byte array
     */
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass!=null && basicClass.length>0){
            try{
                if (!globalTransformers.isEmpty() || transformers.containsKey(transformedName)){
                    ClassReader classReader = new ClassReader(basicClass);
                    ClassNode cn = new ClassNode();
                    classReader.accept(cn, 0);
                    //publicClass(cn);
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
                        ClassWriter classWriter = new LaunchClassWriter(classReader, flags);
                        cn.accept(classWriter);
                        return ASMUtil.push(transformedName, classWriter.toByteArray());
                    }
                }else return basicClass;
            }catch (Exception e){
                //crash the game when transform failed.
                throw new RuntimeException(e);
            }
        }
        return basicClass;
    }

    /**
     * @param classNode the class to public
     */
    public static void publicClass(ClassNode classNode){
        classNode.access = toPublic(classNode.access);
        for(MethodNode mn : classNode.methods){
            mn.access = toPublic(mn.access);
        }
        for(FieldNode fn : classNode.fields){
            fn.access = toPublic(fn.access);
        }
    }


    /**
     * @param access the access
     *
     * @return the public access
     */
    private static int toPublic(int access)
    {
        return access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
    }

    //Fix the crash of calling getCommonSuperClass
    //Usually, the crash from the obfuscated environment
    public static class LaunchClassWriter extends ClassWriter{

        public LaunchClassWriter(ClassReader classReader, int flags) {
            super(classReader, flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return getCommonSuperClass0(type1, type2);
        }

        public static String getCommonSuperClass0(final String type1, final String type2) {
            ClassLoader classLoader = LaunchClassLoader.class.getClassLoader();
            Class<?> class1;
            try {
                class1 = Class.forName(type1.replace('/', '.'), false, classLoader);
            } catch (ClassNotFoundException e) {
                try {
                    class1 = Class.forName(type1.replace('/', '.'), false, Launch.classLoader);
                } catch (ClassNotFoundException e1) {
                    throw new TypeNotPresentException(type1, e1);
                }
            }
            Class<?> class2;
            try {
                class2 = Class.forName(type2.replace('/', '.'), false, classLoader);
            } catch (ClassNotFoundException e) {
                try {
                    class2 = Class.forName(type2.replace('/', '.'), false, Launch.classLoader);
                } catch (ClassNotFoundException e2) {
                    throw new TypeNotPresentException(type2, e2);
                }
            }
            if (class1.isAssignableFrom(class2)) {
                return type1;
            }
            if (class2.isAssignableFrom(class1)) {
                return type2;
            }
            if (class1.isInterface() || class2.isInterface()) {
                return "java/lang/Object";
            } else {
                do {
                    class1 = class1.getSuperclass();
                } while (!class1.isAssignableFrom(class2));
                return class1.getName().replace('.', '/');
            }
        }
    }
    
    public static void register(final String canonicalClassName, final ToIntFunction<ClassNode> singleTransformer){
        if (RMLAddTransformerEvent.SingleTransformer.BUS.post(new RMLAddTransformerEvent.SingleTransformer(singleTransformer, canonicalClassName)).isCanceled()) {
            return;
        }
        if (transformers.containsKey(canonicalClassName)){
            transformers.computeIfPresent(canonicalClassName, (k, orgTran) -> value -> orgTran.applyAsInt(value) | singleTransformer.applyAsInt(value));
        }else transformers.put(canonicalClassName, singleTransformer);
    }

    public static void register(GlobalTransformer globalTransformer) {
        if (!RMLAddTransformerEvent.GlobalTransformer.BUS.post(new RMLAddTransformerEvent.GlobalTransformer(globalTransformer)).isCanceled()) {
            globalTransformers.add(globalTransformer);
        }
    }

    /**
     * @Project ResourceModLoader
     * @Author Hileb
     * @Date 2024/3/11 23:00
     **/
    public static class Transformers {
        public static void initForgeTransformers(){
            register("net.minecraftforge.common.crafting.CraftingHelper",
                    (cn)->{
                        Tasks tasks = new Tasks("init", "getItemStack", "getItemStackBasic");

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
                                    InsnList hook = new InsnList();
                                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/api/event/CraftingHelperInitEvent","post","()V",false));
                                    return hook;
                                }, (node) -> node.getOpcode() == Opcodes.RETURN);
                                tasks.complete("init");
                            } else if ("getItemStack".equals(mn.name) || "getItemStackBasic".equals(mn.name)){
                                /**
                                 * Overwrite:
                                 *
                                 * {@link net.minecraftforge.common.crafting.CraftingHelper#getItemStack(JsonObject, JsonContext)}
                                 * public static ItemStack getItemStack(JsonObject json, JsonContext context) {
                                 *     return RMLFMLHook.LateHook.getItemStack(json, context);
                                 * }
                                 *
                                 * {@link net.minecraftforge.common.crafting.CraftingHelper#getItemStackBasic(JsonObject, JsonContext)}
                                 * public static ItemStack getItemStackBasic(JsonObject json, JsonContext context) {
                                 *     return RMLFMLHook.LateHook.getItemStack(json, context);
                                 * }
                                 *
                                 * {@link rml.layer.compat.fml.RMLFMLHooks.LateHooks#getItemStack(JsonObject, JsonContext)}
                                 * **/
                                if (mn.tryCatchBlocks != null) mn.tryCatchBlocks.clear();
                                if (mn.localVariables != null) mn.localVariables.clear();
                                mn.instructions.clear();
                                mn.visitVarInsn(Opcodes.ALOAD, 0);
                                mn.visitVarInsn(Opcodes.ALOAD, 1);
                                mn.visitMethodInsn(Opcodes.INVOKESTATIC, "rml/layer/compat/fml/RMLFMLHooks$LateHooks", "getItemStack", "(Lcom/google/gson/JsonObject;Lnet/minecraftforge/common/crafting/JsonContext;)Lnet/minecraft/item/ItemStack;", false);
                                mn.visitInsn(Opcodes.ARETURN);
                                tasks.complete(mn.name);
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    });
            register("net.minecraftforge.fml.common.Loader",
                    (cn)->{
                        Tasks tasks = new Tasks("identifyDuplicates");
                        for(MethodNode mn:cn.methods){
                            /**
                             *  private void identifyDuplicates(List<ModContainer> mods) {
                             *         RMLModDiscover.inject(mods);
                             * **/
                            if ("identifyDuplicates".equals(mn.name)){
                                InsnList injectList = new InsnList();
                                injectList.add(new IntInsnNode(Opcodes.ALOAD,1));
                                injectList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/core/RMLModDiscover","inject","(Ljava/util/List;)V",false));
                                mn.instructions.insert(injectList);
                                tasks.complete("identifyDuplicates");
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    });
            register("net.minecraftforge.common.config.ConfigManager",
                    (cn)->{
                        Tasks tasks = new Tasks("sync");
                        for(MethodNode mn:cn.methods){
                            // public static sync(Ljava/lang/String;Lnet/minecraftforge/common/config/Config$Type;)V
                            if ( !tasks.isCompleted("sync") && "sync".equals(mn.name) && "(Ljava/lang/String;Lnet/minecraftforge/common/config/Config$Type;)V".equals(mn.desc)){
                                ListIterator<AbstractInsnNode> iterator=mn.instructions.iterator();
                                AbstractInsnNode node;
                                while (iterator.hasNext() && !tasks.isCompleted("sync")){
                                    node=iterator.next();
                                    if (node.getOpcode() == Opcodes.INVOKEINTERFACE && node instanceof MethodInsnNode){
                                        MethodInsnNode methodInsnNode=(MethodInsnNode) node;
                                        if ("java/util/Map".equals(methodInsnNode.owner) && "put".equals(methodInsnNode.name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(methodInsnNode.desc) && methodInsnNode.itf){
                                            methodInsnNode.setOpcode(Opcodes.INVOKESTATIC);
                                            methodInsnNode.owner= "rml/loader/api/config/ConfigPatcher";
                                            methodInsnNode.name="registerCfg";
                                            methodInsnNode.itf=false;
                                            methodInsnNode.desc="(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                                            tasks.complete("sync");
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS  | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    }
            );
            register("net.minecraftforge.client.ForgeHooksClient",
                    (cn)->{
                        Tasks tasks = new Tasks("renderMainMenu");
                        for(MethodNode mn: cn.methods){
                            if ("renderMainMenu".equals(mn.name)){
                                ASMUtil.injectBefore(mn.instructions, ()->{
                                    InsnList hook = new InsnList();
                                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/layer/compat/fml/RMLFMLHooks$LateHooks", "formatSplashText", "(Ljava/lang/String;)Ljava/lang/String;", false));
                                    return hook;
                                }, (node)->node.getOpcode() == Opcodes.ARETURN);
                                tasks.complete("renderMainMenu");
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    });
            /**
             * Impl of {@link rml.loader.api.event.early.FMLBeforeStageEvent}
             * **/
            register("net.minecraftforge.fml.common.LoadController",
                    (cn)->{
                        Tasks tasks = new Tasks("distributeStateMessage");
                        for(MethodNode mn:cn.methods){
                            if ("distributeStateMessage".equals(mn.name) && "(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V".equals(mn.desc)){
                                InsnList hook=new InsnList();
                                hook.add(new VarInsnNode(Opcodes.ALOAD,0));
                                hook.add(new VarInsnNode(Opcodes.ALOAD,1));
                                hook.add(new VarInsnNode(Opcodes.ALOAD,2));
                                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/layer/compat/fml/RMLFMLHooks","beforeFMLBusEventSending","(Lnet/minecraftforge/fml/common/LoadController;Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V",false));
                                mn.instructions.insert(hook);
                                tasks.complete("distributeStateMessage");
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    });
            /**
             * Impl of {@link rml.loader.api.event.client.gui.ModMenuInfoEvent}
             * **/
            register("net.minecraftforge.fml.client.GuiModList$Info",
                    (cn)->{
                        Tasks tasks = new Tasks("<init>");
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
                                            iterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/api/event/client/gui/ModMenuInfoEvent", "post", "(Ljava/util/List;Ljava/lang/Object;Lnet/minecraftforge/fml/client/GuiModList;)Ljava/util/List;", false));
                                            tasks.complete("<init>");
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    });
            //public net.minecraftforge.fml.client.GuiModList elements : fields and methods
            register("net.minecraftforge.fml.client.GuiModList", (cn)-> {
                for (FieldNode fn : cn.fields) {
                    if ("selectedMod".equals(fn.name)) {
                        fn.access = toPublic(fn.access);
                    }
                }
                return 0;
            });
            //Add Deserializer for all IForgeRegistry
            register("net.minecraftforge.registries.RegistryBuilder", (cn)->{
                Tasks tasks = new Tasks("create");
                for(MethodNode mn : cn.methods){
                    if ("create".equals(mn.name)){
                        ASMUtil.injectBefore(mn.instructions, ()->{
                            InsnList hook = new InsnList();
                            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            hook.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraftforge/registries/RegistryBuilder", "registryName", "Lnet/minecraft/util/ResourceLocation;"));
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/deserialize/MCDeserializers", "onNewRegistry", "(Lnet/minecraftforge/registries/IForgeRegistry;Lnet/minecraft/util/ResourceLocation;)Lnet/minecraftforge/registries/IForgeRegistry;", false));
                            return hook;
                        }, (node) -> node.getOpcode() == Opcodes.ARETURN);
                        tasks.complete("create");
                    }
                }
                if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                else tasks.throwError();
                return -1;
            });
        }
        public static void initMinecraftTransformers(){
            register("net.minecraft.item.ItemStack", cn -> {
                Tasks tasks = new Tasks("dataItemDamage");
                for (MethodNode method : cn.methods) {
                    if (!ITEM_DAMAGE.is(method)) continue;
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new VarInsnNode(Opcodes.ILOAD, 1));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/layer/compat/fml/RMLFMLHooks$LateHooks", "modifyItemDamage",
                            "(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/EntityLivingBase;)I", false));
                    hook.add(new VarInsnNode(Opcodes.ISTORE, 1));
                    method.instructions.insert(hook);
                    tasks.complete("dataItemDamage");
                }
                if (!tasks.isCompleted()) tasks.throwError();
                return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
            });
            register("net.minecraft.item.ItemBow", cn -> {
                Tasks tasks = new Tasks("projectileAmmunition", "projectileSnapshot", "dataProjectile", "dataAmmunition");
                for (MethodNode method : cn.methods) {
                    if (!BOW_RELEASE.is(method)) continue;
                    int weapon = method.maxLocals++;
                    int ammunition = -1;
                    InsnList initialize = new InsnList();
                    initialize.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    initialize.add(new VarInsnNode(Opcodes.ASTORE, weapon));
                    method.instructions.insert(initialize);
                    for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
                        if (!(node instanceof MethodInsnNode)) continue;
                        MethodInsnNode call = (MethodInsnNode) node;
                        if (BOW_AMMUNITION.is(call)) {
                            AbstractInsnNode store = call.getNext();
                            while (store != null && store.getOpcode() < 0) store = store.getNext();
                            if (!(store instanceof VarInsnNode) || store.getOpcode() != Opcodes.ASTORE)
                                throw new IllegalStateException("Cannot identify ItemBow ammunition storage");
                            ammunition = ((VarInsnNode) store).var;
                            tasks.complete("projectileAmmunition");
                        } else if (ITEM_DAMAGE.is(call)) {
                            InsnList snapshot = new InsnList();
                            snapshot.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            snapshot.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/layer/compat/fml/RMLFMLHooks$LateHooks", "projectileWeapon",
                                    "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;", false));
                            snapshot.add(new VarInsnNode(Opcodes.ASTORE, weapon));
                            method.instructions.insertBefore(call, snapshot);
                            tasks.complete("projectileSnapshot");
                        } else if (WORLD_SPAWN.is(call)) {
                            if (ammunition < 0) throw new IllegalStateException("ItemBow spawns a projectile before selecting ammunition");
                            InsnList arguments = new InsnList();
                            arguments.add(new VarInsnNode(Opcodes.ALOAD, 3));
                            arguments.add(new VarInsnNode(Opcodes.ALOAD, weapon));
                            arguments.add(new VarInsnNode(Opcodes.ALOAD, ammunition));
                            method.instructions.insertBefore(call, arguments);
                            call.setOpcode(Opcodes.INVOKESTATIC);
                            call.owner = "rml/layer/compat/fml/RMLFMLHooks$LateHooks";
                            call.name = "spawnEnchantedProjectile";
                            call.desc = "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z";
                            call.itf = false;
                            tasks.complete("dataProjectile");
                        } else if (ITEM_SHRINK.is(call)) {
                            InsnList arguments = new InsnList();
                            arguments.add(new VarInsnNode(Opcodes.ALOAD, weapon));
                            arguments.add(new VarInsnNode(Opcodes.ALOAD, 3));
                            method.instructions.insertBefore(call, arguments);
                            call.setOpcode(Opcodes.INVOKESTATIC);
                            call.owner = "rml/layer/compat/fml/RMLFMLHooks$LateHooks";
                            call.name = "consumeEnchantedAmmunition";
                            call.desc = "(Lnet/minecraft/item/ItemStack;ILnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V";
                            call.itf = false;
                            tasks.complete("dataAmmunition");
                        }
                    }
                }
                if (!tasks.isCompleted()) tasks.throwError();
                return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
            });
            register("net.minecraft.world.storage.loot.LootTableManager", cn -> {
                Tasks tasks = new Tasks("compiledLoot");
                for (MethodNode method : cn.methods) {
                    if (LOOT_LOOKUP.is(method)) {
                        prependCompiledLookup(method, 1, "findLootTable", "net/minecraft/world/storage/loot/LootTable");
                        tasks.complete("compiledLoot");
                    }
                }
                if (!tasks.isCompleted()) tasks.throwError();
                return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
            });
            register("net.minecraft.world.gen.structure.template.TemplateManager", cn -> {
                Tasks tasks = new Tasks("compiledTemplate", "compiledTemplateCreate");
                for (MethodNode method : cn.methods) {
                    if (TEMPLATE_LOOKUP.is(method) || TEMPLATE_CREATE.is(method)) {
                        prependCompiledLookup(method, 2, "findStructure", "net/minecraft/world/gen/structure/template/Template");
                        tasks.complete(TEMPLATE_LOOKUP.is(method) ? "compiledTemplate" : "compiledTemplateCreate");
                    }
                }
                if (!tasks.isCompleted()) tasks.throwError();
                return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
            });
            register("net.minecraft.advancements.AdvancementManager", cn -> {
                Tasks tasks = new Tasks("compiledAdvancements");
                for (MethodNode method : cn.methods) {
                    for (ListIterator<AbstractInsnNode> iterator = method.instructions.iterator(); iterator.hasNext();) {
                        AbstractInsnNode instruction = iterator.next();
                        if (instruction instanceof MethodInsnNode && ADVANCEMENT_LOAD.is((MethodInsnNode) instruction)) {
                            MethodInsnNode call = (MethodInsnNode) instruction;
                            call.setOpcode(Opcodes.INVOKESTATIC);
                            call.owner = "rml/loader/content/CompiledContentHooks";
                            call.name = "loadAdvancements";
                            call.desc = "(Lnet/minecraft/advancements/AdvancementList;Ljava/util/Map;)V";
                            call.itf = false;
                            tasks.complete("compiledAdvancements");
                        }
                    }
                }
                if (!tasks.isCompleted()) tasks.throwError();
                return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
            });
            register("net.minecraft.advancements.FunctionManager",
                    (cn)->{
                        Tasks tasks = new Tasks("m_193061");
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
                                tasks.complete("m_193061");
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    });
            register("net.minecraft.world.storage.loot.LootTableList",
                    (cn)->{
                        Tasks tasks = new Tasks("<clinit>");
                        for(MethodNode mn: cn.methods){
                            /**
                             * static {
                             *         LootTableRegistryEvent.post();
                             *     }
                             * **/
                            if ("<clinit>".equals(mn.name)){
                                ASMUtil.injectBefore(mn.instructions, ()->{
                                    InsnList hook = new InsnList();
                                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/api/event/LootTableRegistryEvent","post","()V",false));
                                    return hook;
                                }, (node)->node.getOpcode() == Opcodes.RETURN);
                                tasks.complete("<clinit>");
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    });

            register("net.minecraft.client.gui.GuiMainMenu",
                    (cn)->{
                        Tasks tasks = new Tasks("<init>");
                        for(MethodNode mn:cn.methods){
                            /**
                             *  try {
                             *             List<String> list = RMLFMLHooks.LateHooks.addSplashTexts(Lists.newArrayList());
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
                                            iterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/layer/compat/fml/RMLFMLHooks$LateHooks", "addSplashTexts", "(Ljava/util/ArrayList;)Ljava/util/ArrayList;", false));
                                            tasks.complete("<init>");
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    });

            register("net.minecraft.client.gui.GuiScreen",
                    (cn)->{
                        Tasks bar = new Tasks("click", "hover");
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
                        if (bar.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else bar.throwError();
                        return -1;
                    });
        }

        private static void prependCompiledLookup(MethodNode method, int idSlot, String hook, String result) {
            InsnList code = new InsnList();
            LabelNode nativePath = new LabelNode();
            code.add(new VarInsnNode(Opcodes.ALOAD, idSlot));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/loader/content/CompiledContentHooks", hook,
                    "(Lnet/minecraft/util/ResourceLocation;)L" + result + ";", false));
            code.add(new InsnNode(Opcodes.DUP));
            code.add(new JumpInsnNode(Opcodes.IFNULL, nativePath));
            code.add(new InsnNode(Opcodes.ARETURN));
            code.add(nativePath);
            code.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(code);
        }

        public static void initGroovyScriptTransformer(){
            register("com.cleanroommc.groovyscript.sandbox.GroovyScriptSandbox",
                    (cn)->{
                        Tasks tasks = new Tasks("loadScripts");
                        for(MethodNode mn : cn.methods){
                            if ("loadScripts".equals(mn.name) && "(Lgroovy/lang/Binding;Ljava/util/Set;Z)V".equals(mn.desc)){
                                InsnList hook = new InsnList();
                                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                                hook.add(new VarInsnNode(Opcodes.ILOAD, 3));
                                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/layer/compat/groovyscripts/RMLGroovySandBox", "load", "(Lcom/cleanroommc/groovyscript/sandbox/GroovyScriptSandbox;Lgroovy/lang/Binding;Ljava/util/Set;Z)V", false));
                                mn.instructions.insert(hook);
                                tasks.complete("loadScripts");
                            }
                        }
                        if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                        else tasks.throwError();
                        return -1;
                    });
        }
        public static class Late{
            public static void initModTransformers(Object[] objects){
                ASMDataTable asmDatas = (ASMDataTable)objects[1];
                if (Loader.isModLoaded(CraftTweaker.MODID)){
                    register("crafttweaker.mc1120.CraftTweaker",
                            (cn)->{
                                Tasks tasks = new Tasks("onPreInitialization");
                                for(MethodNode mn:cn.methods){
                                    /**
                                     *  @EventHandler
                                     *     public void onPreInitialization(FMLPreInitializationEvent ev) {
                                     *         CrTZenClassRegisterEvent.post();
                                     *         PROXY.registerEvents();
                                     * **/
                                    if ("onPreInitialization".equals(mn.name)){
                                        InsnList hook = new InsnList();
                                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/layer/compat/crt/CrTZenClassRegisterEvent", "post", "()V", false));
                                        mn.instructions.insert(hook);
                                        tasks.complete("onPreInitialization");
                                    }
                                }
                                if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                                else tasks.throwError();
                                return -1;
                            });

                    //Find all ITweaker and inject it
                    for (ASMDataTable.ASMData asmData : asmDatas.getAll("crafttweaker/runtime/ITweaker")) {

                        /**
                         *   setScriptProvider(IScriptProvider provider){
                         * +     provider = RMLCrTLoader.inject(provider);
                         *     //...
                         *   }
                         *
                         * **/
                        register(asmData.getClassName().replace('/', '.'),
                                (cn)->{
                                    Tasks tasks = new Tasks("setScriptProvider");
                                    for (MethodNode mn : cn.methods) {
                                        if ("setScriptProvider".equals(mn.name)) {
                                            InsnList list = new InsnList();
                                            list.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "rml/layer/compat/crt/RMLCrTLoader", "inject", "(Lcrafttweaker/runtime/IScriptProvider;)Lcrafttweaker/runtime/IScriptProvider;", false));
                                            list.add(new VarInsnNode(Opcodes.ASTORE, 1)); // provider
                                            mn.instructions.insert(list);
                                            tasks.complete("setScriptProvider");
                                        }
                                    }
                                    if (tasks.isCompleted()) return ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
                                    else tasks.throwError();
                                    return -1;
                                });
                    }
                }

            }
        }

    }
}
