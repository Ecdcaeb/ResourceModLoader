package rml.loader.runtime;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import rml.loader.content.gameplay.GameplayModule.SlotDefinition;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static net.minecraft.command.CommandBase.*;

/** Concrete command consumers for the reloadable content providers. */
final class RMLContentCommands {
    private static final Set<String> COMMANDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("schedule", "predicate", "item", "loot")));
    private final LoaderRuntime runtime;

    RMLContentCommands(LoaderRuntime runtime) { this.runtime = runtime; }
    Set<String> getCommands() { return COMMANDS; }

    void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        switch (args[0]) {
            case "schedule": schedule(sender, args); return;
            case "predicate": predicate(sender, args); return;
            case "item": item(server, sender, args); return;
            case "loot": loot(server, sender, args); return;
            default: throw new WrongUsageException("Unknown RML content command");
        }
    }

    private void schedule(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 3 && "clear".equals(args[1])) {
            result(sender, runtime.getFunctions().clearSchedule(new ResourceLocation(args[2])));
            return;
        }
        if ((args.length != 4 && args.length != 5) || !"function".equals(args[1])) {
            throw new WrongUsageException("/rml schedule function <id> <time> [append|replace] | schedule clear <id>");
        }
        if (args.length == 5 && !"append".equals(args[4]) && !"replace".equals(args[4])) throw new WrongUsageException("Schedule mode must be append or replace");
        runtime.getFunctions().schedule(new ResourceLocation(args[2]), args[3], args.length == 4 || "replace".equals(args[4]));
        result(sender, 1);
    }

    private void predicate(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3 || !"test".equals(args[1])) throw new WrongUsageException("/rml predicate test <id>");
        WorldServer world = world(sender);
        boolean matches = runtime.getCompiledLoot().testPredicate(new ResourceLocation(args[2]), world.rand, context(world, sender.getCommandSenderEntity(), sender.getPosition()));
        result(sender, matches ? 1 : 0);
        sender.sendMessage(new TextComponentString(args[2] + " = " + matches));
    }

    private void item(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3 || !"modify".equals(args[1])) throw new WrongUsageException("/rml item modify <entity|block> ... <slot> <modifier>");
        int changed = 0;
        if ("entity".equals(args[2])) {
            if (args.length != 6) throw new WrongUsageException("/rml item modify entity <selector> <slot> <modifier>");
            SlotDefinition slot = SlotDefinition.command(args[4]);
            ResourceLocation modifier = new ResourceLocation(args[5]);
            for (Entity entity : getEntityList(server, sender, args[3])) {
                ItemStack stack = slot.resolve(entity);
                if (stack.isEmpty()) continue;
                WorldServer world = (WorldServer) entity.getEntityWorld();
                slot.replace(entity, runtime.getCompiledLoot().modifyItem(modifier, stack, world.rand, context(world, entity, entity.getPosition())));
                if (entity instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entity;
                    player.inventory.markDirty();
                    player.inventoryContainer.detectAndSendChanges();
                    if (player.openContainer != player.inventoryContainer) player.openContainer.detectAndSendChanges();
                }
                changed++;
            }
        } else if ("block".equals(args[2])) {
            if (args.length != 8) throw new WrongUsageException("/rml item modify block <x> <y> <z> <container.slot> <modifier>");
            WorldServer world = world(sender);
            BlockPos position = parseBlockPos(sender, args, 3, false);
            IInventory inventory = inventory(world, position);
            int slot = containerSlot(args[6], inventory.getSizeInventory());
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                inventory.setInventorySlotContents(slot, runtime.getCompiledLoot().modifyItem(new ResourceLocation(args[7]), stack, world.rand,
                        context(world, sender.getCommandSenderEntity(), position)));
                inventory.markDirty();
                net.minecraft.block.state.IBlockState state = world.getBlockState(position);
                world.notifyBlockUpdate(position, state, state, 3);
                changed = 1;
            }
        } else throw new WrongUsageException("Item target must be entity or block");
        result(sender, changed);
        if (changed == 0) throw new CommandException("No item matched the requested slots");
    }

    private void loot(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) throw new WrongUsageException("/rml loot <give|spawn|insert> ... loot <table>");
        WorldServer world = world(sender);
        if ("give".equals(args[1])) {
            if (args.length != 5 || !"loot".equals(args[3])) throw new WrongUsageException("/rml loot give <players> loot <table>");
            List<EntityPlayerMP> players = getPlayers(server, sender, args[2]);
            List<ItemStack> stacks = generate(world, sender, args[4]);
            int delivered = 0;
            for (int index = 0; index < players.size(); index++) {
                EntityPlayerMP player = players.get(index);
                for (ItemStack source : stacks) {
                    ItemStack stack = index + 1 == players.size() ? source : source.copy();
                    delivered += stack.getCount();
                    player.inventory.addItemStackToInventory(stack);
                    if (!stack.isEmpty()) {
                        EntityItem dropped = player.dropItem(stack, false);
                        if (dropped != null) { dropped.setNoPickupDelay(); dropped.setOwner(player.getName()); }
                    }
                }
                player.inventoryContainer.detectAndSendChanges();
            }
            result(sender, delivered);
            return;
        }
        if (args.length != 7 || !"loot".equals(args[5])) throw new WrongUsageException("/rml loot <spawn|insert> <x> <y> <z> loot <table>");
        if ("spawn".equals(args[1])) {
            Vec3d origin = sender.getPositionVector();
            double x = parseDouble(origin.x, args[2], false);
            double y = parseDouble(origin.y, args[3], false);
            double z = parseDouble(origin.z, args[4], false);
            int count = 0;
            for (ItemStack stack : generate(world, sender, args[6])) {
                if (stack.isEmpty()) continue;
                if (world.spawnEntity(new EntityItem(world, x, y, z, stack))) count += stack.getCount();
            }
            result(sender, count);
            return;
        }
        if ("insert".equals(args[1])) {
            IInventory target = inventory(world, parseBlockPos(sender, args, 2, false));
            int count = 0;
            for (ItemStack stack : generate(world, sender, args[6])) count += insert(target, stack);
            target.markDirty();
            result(sender, count);
            return;
        }
        throw new WrongUsageException("Loot target must be give, spawn, or insert");
    }

    private List<ItemStack> generate(WorldServer world, ICommandSender sender, String name) {
        LootTable table = world.getLootTableManager().getLootTableFromLocation(new ResourceLocation(name));
        return table.generateLootForPools(world.rand, context(world, sender.getCommandSenderEntity(), sender.getPosition()));

    }

    private static int insert(IInventory inventory, ItemStack stack) {
        int initial = stack.getCount();
        for (int slot = 0; slot < inventory.getSizeInventory() && !stack.isEmpty(); slot++) {
            if (!inventory.isItemValidForSlot(slot, stack)) continue;
            ItemStack existing = inventory.getStackInSlot(slot);
            int limit = Math.min(inventory.getInventoryStackLimit(), stack.getMaxStackSize());
            if (existing.isEmpty()) {
                int accepted = Math.min(limit, stack.getCount());
                if (accepted == stack.getCount()) {
                    inventory.setInventorySlotContents(slot, stack);
                    return initial;
                }
                inventory.setInventorySlotContents(slot, stack.splitStack(accepted));
            } else if (ItemStack.areItemsEqual(existing, stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                int accepted = Math.min(Math.max(0, limit - existing.getCount()), stack.getCount());
                if (accepted != 0) { existing.grow(accepted); stack.shrink(accepted); }
            }
        }
        return initial - stack.getCount();
    }

    private static int containerSlot(String name, int size) throws CommandException {
        if (!name.startsWith("container.")) throw new WrongUsageException("Expected container.<index>");
        return parseInt(name.substring("container.".length()), 0, size - 1);
    }

    private static IInventory inventory(WorldServer world, BlockPos position) throws CommandException {
        TileEntity tile = world.getTileEntity(position);
        if (!(tile instanceof IInventory)) throw new CommandException("No inventory at %s", position);
        return (IInventory) tile;
    }

    private static WorldServer world(ICommandSender sender) throws CommandException {
        if (!(sender.getEntityWorld() instanceof WorldServer)) throw new CommandException("A server world is required");
        return (WorldServer) sender.getEntityWorld();
    }

    private static LootContext context(WorldServer world, Entity entity, BlockPos origin) {
        return new rml.loader.content.data.PositionedLootContext(world, entity, origin);
    }

    private static void result(ICommandSender sender, int result) {
        sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, result);
        sender.setCommandStat(CommandResultStats.Type.AFFECTED_ITEMS, result);
    }
}
