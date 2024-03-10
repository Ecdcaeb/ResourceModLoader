package mods.Hileb.rml.api.asm;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
