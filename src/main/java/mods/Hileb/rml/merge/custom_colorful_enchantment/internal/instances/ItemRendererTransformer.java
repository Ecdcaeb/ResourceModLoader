package com.Hileb.custom_colorful_enchantment.internal.instances;

import com.Hileb.custom_colorful_enchantment.internal.utils.ASMHelper;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/3 14:07
 **/
public class ItemRendererTransformer implements ITransformer<MethodNode> {
    @Override
    public @NotNull MethodNode transform(MethodNode input, ITransformerVotingContext context) {
        ASMHelper.visitRender(input);
        return input;
    }

    @Override
    public @NotNull TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }
    @Override
    public @NotNull Set<Target> targets() {
        return Set.of(Target.targetMethod("net.minecraft.client.renderer.entity.ItemRenderer","render","(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"));
    }
}
