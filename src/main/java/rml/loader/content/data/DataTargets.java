package rml.loader.content.data;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;

/** One resolution contract for entity targets and score holders across rules. */
final class DataTargets {
    private DataTargets() { }

    static Entity entity(DataContext context, String target) {
        if ("this".equals(target)) return context.getEntity();
        if ("killer_player".equals(target)) {
            Entity source = context.getLootContext() != null ? context.getLootContext().getKillerPlayer()
                    : context.getDamageSource() == null ? null : context.getDamageSource().getTrueSource();
            return source instanceof EntityPlayer ? source : null;
        }
        DamageSource damage = context.getDamageSource();
        if (damage == null) return null;
        if ("direct_attacker".equals(target)) return damage.getImmediateSource();
        if ("killer".equals(target) || "attacker".equals(target)) return damage.getTrueSource();
        return null;
    }

    static Entity killer(DataContext context) {
        return context.getLootContext() != null ? context.getLootContext().getKiller()
                : context.getDamageSource() == null ? null : context.getDamageSource().getTrueSource();
    }

    static String scoreHolder(DataContext context, String holder) {
        if (!"this".equals(holder)) return holder;
        Entity entity = context.getEntity();
        if (entity == null) return null;
        return entity instanceof EntityPlayer ? entity.getName() : entity.getCachedUniqueIdString();
    }
}
