package mods.rml.api.asm;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/10 23:43
 **/
public class ASMHelper {
    /*
    @Unique
    public static void locate(InsnList method, Predicate<List<AbstractInsnNode>> contextHandler, Consumer<ListIterator<AbstractInsnNode>> injector){
        ListIterator<AbstractInsnNode> iterator = method.iterator();
        List<AbstractInsnNode> context = new LinkedList<>();
        while (iterator.hasNext()){
            if (contextHandler.test(context)){
                injector.accept(iterator);
            }
            context.add(iterator.next());
        }
    }

    public static void test(){
        InsnList method;//virtue
        locate(method, (context)->context.size() == 0, (ite)->ite.add(new InsnNode(Opcodes.RETURN)));
    }
    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    public void test(CallbackInfo ci){
        ci.cancel();
    }
    */
}
