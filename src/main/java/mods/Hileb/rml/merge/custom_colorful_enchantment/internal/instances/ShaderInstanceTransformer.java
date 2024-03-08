package com.Hileb.custom_colorful_enchantment.internal.instances;

import com.Hileb.custom_colorful_enchantment.internal.utils.ASMHelper;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/3 14:07
 **/
public class ShaderInstanceTransformer implements ITransformer<MethodNode> {
    @Override
    public @NotNull MethodNode transform(MethodNode input, ITransformerVotingContext context) {
        ASMHelper.visitUpdate(input);
        return input;
    }

    @Override
    public @NotNull TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }
    @Override
    public @NotNull Set<Target> targets() {
        return Set.of(Target.targetMethod("net.minecraft.client.renderer.ShaderInstance","updateLocations","()V"));
        //return Set.of(new Target("net.minecraft.client.renderer.ShaderInstance","updateLocations","()V",TargetType.METHOD));
    }
}
