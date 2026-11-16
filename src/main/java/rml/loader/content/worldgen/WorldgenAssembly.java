package rml.loader.content.worldgen;

import com.google.gson.JsonObject;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed connector and projection values for native 1.12 template assembly. */
public final class WorldgenAssembly {
    private static final EnumFacing[] DIRECTIONS = EnumFacing.values();
    private static final java.lang.reflect.Field BLOCKS = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
            Template.class, new String[]{"blocks", "field_186270_a"});
    private static final java.lang.reflect.Field ENTITIES = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
            Template.class, new String[]{"entities", "field_186271_b"});
    private static final java.lang.reflect.Field SIZE = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
            Template.class, new String[]{"size", "field_186272_c"});
    private WorldgenAssembly() { }

    /** Elevation is sampled while planning, before any structure changes the terrain. */
    @FunctionalInterface public interface Projection {
        int elevation(World world, int x, int z);
        default boolean isRigid() { return false; }
    }
    static final Projection RIGID = new Projection() {
        @Override public int elevation(World world, int x, int z) { return 0; }
        @Override public boolean isRigid() { return true; }
    };

    public static final class Connector {
        public final BlockPos position;
        public final EnumFacing facing, top;
        public final ResourceLocation name, target, pool;
        public final boolean aligned;

        public Connector(BlockPos position, EnumFacing facing, EnumFacing top, ResourceLocation name,
                         ResourceLocation target, ResourceLocation pool, boolean aligned) {
            if (position == null || facing == null || top == null || name == null || target == null)
                throw new IllegalArgumentException("Connector position, directions, name and target are required");
            if (facing.getAxis() == top.getAxis()) throw new IllegalArgumentException("Connector top must be perpendicular to facing");
            this.position = position.toImmutable(); this.facing = facing; this.top = top;
            this.name = name; this.target = target; this.pool = pool; this.aligned = aligned;
        }
    }

    static void register(DeserializerManager manager) {
        manager.addDefaultEntry(new AbstractDeserializer<>(WorldgenNodes.id("connector"), Connector.class, json -> {
            JsonObject value = json.getAsJsonObject();
            WorldgenNodes.fields(value, "type", "pos", "facing", "top", "name", "target", "pool", "joint");
            BlockPos position = manager.decode(BlockPos.class, value.get("pos"));
            EnumFacing facing = direction(value.get("facing").getAsString());
            EnumFacing top = value.has("top") ? direction(value.get("top").getAsString())
                    : facing.getAxis() == EnumFacing.Axis.Y ? EnumFacing.NORTH : EnumFacing.UP;
            ResourceLocation name = manager.decode(ResourceLocation.class, value.get("name"));
            ResourceLocation target = value.has("target") ? manager.decode(ResourceLocation.class, value.get("target")) : name;
            ResourceLocation pool = value.has("pool") ? manager.decode(ResourceLocation.class, value.get("pool")) : null;
            String joint = value.has("joint") ? value.get("joint").getAsString() : "aligned";
            if (!"aligned".equals(joint) && !"rollable".equals(joint)) throw new IllegalArgumentException("Connector joint must be aligned or rollable");
            return new Connector(position, facing, top, name, target, pool, "aligned".equals(joint));
        }));
        manager.addDefaultEntry(new AbstractDeserializer<>(WorldgenNodes.id("rigid_projection"), Projection.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type"); return RIGID;
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("terrain_projection"), Projection.class, json -> {
            WorldgenNodes.fields(json.getAsJsonObject(), "type");
            return (world, x, z) -> world.getHeight(new BlockPos(x, 0, z)).getY();
        }));
        manager.addEntry(new AbstractDeserializer<>(WorldgenNodes.id("scaled_projection"), Projection.class, json -> {
            JsonObject value = json.getAsJsonObject(); WorldgenNodes.fields(value, "type", "source", "scale");
            Projection source = manager.decode(Projection.class, value.get("source"));
            double scale = WorldgenNodes.finite(value, "scale");
            return (world, x, z) -> exactElevation(source.elevation(world, x, z) * scale);
        }));
    }

    private static int exactElevation(double value) {
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalStateException("Projection elevation exceeds integer coordinates");
        return (int)Math.floor(value);
    }
    private static EnumFacing direction(String name) {
        try { return EnumFacing.valueOf(name.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unknown connector direction " + name, error); }
    }
    static EnumFacing transform(EnumFacing direction, PlacementSettings settings) {
        BlockPos vector = Template.transformedBlockPos(settings, new BlockPos(direction.getDirectionVec()));
        for (EnumFacing candidate : DIRECTIONS) if (candidate.getXOffset() == vector.getX()
                && candidate.getYOffset() == vector.getY() && candidate.getZOffset() == vector.getZ()) return candidate;
        throw new IllegalStateException("Template transform did not preserve a connector direction");
    }

    /** Native templates split once by local column, including entity-only columns. */
    static Column[] columns(Template template) {
        Map<Long, ColumnData> grouped = new LinkedHashMap<>();
        BlockPos size = template.getSize();
        List<Template.BlockInfo> blocks = entries(BLOCKS, template);
        for (Template.BlockInfo block : blocks) {
            validatePosition(block.pos, size);
            group(grouped, block.pos.getX(), block.pos.getZ(), template).blocks.add(new Template.BlockInfo(
                    block.pos.toImmutable(), block.blockState, block.tileentityData == null ? null : block.tileentityData.copy()));
        }
        List<Template.EntityInfo> entities = entries(ENTITIES, template);
        for (Template.EntityInfo entity : entities) {
            validatePosition(entity.blockPos, size);
            group(grouped, entity.blockPos.getX(), entity.blockPos.getZ(), template).entities.add(new Template.EntityInfo(
                    entity.pos, entity.blockPos.toImmutable(), entity.entityData.copy()));
        }
        Column[] result = new Column[grouped.size()];
        int index = 0;
        for (ColumnData group : grouped.values()) result[index++] = new Column(group.x, group.z, group.template);
        return result;
    }
    private static void validatePosition(BlockPos position, BlockPos size) {
        if (position.getX() < 0 || position.getX() >= size.getX()
                || position.getY() < 0 || position.getY() >= size.getY()
                || position.getZ() < 0 || position.getZ() >= size.getZ())
            throw new IllegalArgumentException("Projected template block/entity anchor lies outside its declared bounds");
    }
    private static ColumnData group(Map<Long, ColumnData> groups, int x, int z, Template source) {
        long key = ((long)x << 32) ^ (z & 0xffffffffL);
        ColumnData result = groups.get(key);
        if (result == null) { result = new ColumnData(x, z, source); groups.put(key, result); }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> entries(java.lang.reflect.Field field, Template template) {
        try { return (List<T>) field.get(template); }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read native template entries", error); }
    }
    static final class Column {
        final int x, z;
        final Template template;
        Column(int x, int z, Template template) { this.x = x; this.z = z; this.template = template; }
    }
    private static final class ColumnData {
        final int x, z;
        final Template template = new Template();
        final List<Template.BlockInfo> blocks = entries(BLOCKS, template);
        final List<Template.EntityInfo> entities = entries(ENTITIES, template);
        ColumnData(int x, int z, Template source) {
            this.x = x; this.z = z;
            try { SIZE.set(template, source.getSize().toImmutable()); }
            catch (IllegalAccessException error) { throw new IllegalStateException("Cannot set native template size", error); }
            template.setAuthor(source.getAuthor());
        }
    }
}
