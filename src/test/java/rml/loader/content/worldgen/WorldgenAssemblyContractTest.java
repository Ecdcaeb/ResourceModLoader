package rml.loader.content.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.template.Template;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.loader.content.CompiledTagModule;
import rml.loader.deserialize.NativeTypeDecoders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Native-template assembly contracts, plus a reversible real-server placement entrypoint. */
public final class WorldgenAssemblyContractTest {
    private static final JsonParser JSON = new JsonParser();
    private WorldgenAssemblyContractTest() { }

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        verify(manager());
        System.out.println("WorldgenAssemblyContractTest passed");
    }

    static void verify(DeserializerManager manager) throws Exception {
        Fixture fixture = new Fixture();
        JsonObject rigid = fixture.rigid();
        WorldgenStructure.Plan connected = fixture.compile(manager, rigid).plan(null, new Random(29), 0, 0);
        check(connected != null && connected.pieces.size() == 2, "Incompatible primary pool falls back to the matching second native template");
        check(connected.pieces.get(0).templateId().equals(new ResourceLocation("fixture:assembly_start")), "Root uses the start template");
        check(connected.pieces.get(1).templateId().equals(new ResourceLocation("fixture:assembly_end")), "Fallback uses the end template");
        checkConnection(connected);
        check(connected.bounds.minX == -2 && connected.bounds.maxX == 0 && connected.bounds.minZ == 0 && connected.bounds.maxZ == 5,
                "Quarter-turn root and matching child have correct transformed assembly bounds");
        check(connected.pieces.get(1).depth == 1, "Recursive outgoing pool terminates at configured depth");
        JsonObject emptyPrimary = copy(rigid);
        emptyPrimary.getAsJsonObject("pools").getAsJsonObject("fixture:assembly_blocked").add("elements", new com.google.gson.JsonArray());
        check(fixture.compile(manager, emptyPrimary).plan(null, new Random(29), 0, 0).pieces.size() == 2, "Empty primary pool also resolves its fallback");

        JsonObject mirrored = copy(rigid); mirrored.getAsJsonObject("structure").addProperty("mirror", "front_back");
        WorldgenStructure.Plan mirrorPlan = fixture.compile(manager, mirrored).plan(null, new Random(29), 0, 0);
        check(mirrorPlan.pieces.size() == 2, "Mirrored connectors still assemble"); checkConnection(mirrorPlan);

        JsonObject noDepth = copy(rigid); noDepth.getAsJsonObject("structure").addProperty("max_depth", 0);
        check(fixture.compile(manager, noDepth).plan(null, new Random(29), 0, 0).pieces.size() == 1, "Depth zero places only the root");
        JsonObject limited = copy(rigid); limited.getAsJsonObject("structure").addProperty("max_depth", 64); limited.getAsJsonObject("structure").addProperty("max_pieces", 2);
        check(fixture.compile(manager, limited).plan(null, new Random(29), 0, 0).pieces.size() == 2, "Piece budget bounds a recursive pool graph");
        JsonObject distance = copy(rigid); distance.getAsJsonObject("structure").addProperty("max_distance", 3);
        check(fixture.compile(manager, distance).plan(null, new Random(29), 0, 0).pieces.size() == 1, "Child bounds outside assembly radius are rejected");

        JsonObject collision = copy(rigid); collision.getAsJsonObject("structure").addProperty("max_depth", 64);
        JsonObject turning = element(collision, "fixture:assembly_end").getAsJsonArray("connectors").get(1).getAsJsonObject();
        turning.add("pos", JSON.parse("[0,0,1]")); turning.addProperty("facing", "west");
        WorldgenStructure.Plan loop = fixture.compile(manager, collision).plan(null, new Random(29), 0, 0);
        check(loop.pieces.size() == 4, "Returning connector cannot place a fifth template through the existing root bounds");
        for (int i = 0; i < loop.pieces.size(); i++) for (int j = i + 1; j < loop.pieces.size(); j++)
            check(!loop.pieces.get(i).bounds.intersectsWith(loop.pieces.get(j).bounds), "Accepted piece bounds never intersect");

        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("fixture:z_elevation"), WorldgenAssembly.Projection.class,
                value -> (world, x, z) -> z));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("fixture:connector_child"), WorldgenAssembly.Connector.class,
                value -> manager.decode(WorldgenAssembly.Connector.class, value.getAsJsonObject().get("value"))));
        JsonObject projected = copy(rigid);
        JsonObject projection = JSON.parse("{\"type\":\"rml:scaled_projection\",\"source\":{\"type\":\"fixture:z_elevation\"},\"scale\":1}").getAsJsonObject();
        element(projected, "fixture:assembly_start").add("projection", projection);
        element(projected, "fixture:assembly_end").add("projection", projection);
        JsonObject wrapped = new JsonObject(); wrapped.addProperty("type", "fixture:connector_child");
        wrapped.add("value", element(projected, "fixture:assembly_start").getAsJsonArray("connectors").get(0));
        element(projected, "fixture:assembly_start").getAsJsonArray("connectors").set(0, wrapped);
        WorldgenStructure.Plan slope = fixture.compile(manager, projected).plan(null, new Random(29), 0, 0);
        check(slope.pieces.size() == 2 && slope.bounds.minY == 100 && slope.bounds.maxY == 104, "Custom projection children produce terrain-relative native column elevations");
        checkConnection(slope);
        check(slope.pieces.get(0).connectors[0].position.getY() == 102, "Source connector follows the same projection as its blocks");
        java.lang.reflect.Field nativeBlocks = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                Template.class, new String[]{"blocks", "field_186270_a"});
        for (WorldgenStructure.Piece piece : slope.pieces) {
            Template source = new Template();
            source.read(JsonToNBT.getTagFromJson(text(piece.templateId().getPath() + ".snbt")));
            @SuppressWarnings("unchecked") List<Template.BlockInfo> sourceBlocks = (List<Template.BlockInfo>) nativeBlocks.get(source);
            Map<BlockPos, IBlockState> expected = new HashMap<>();
            for (Template.BlockInfo block : sourceBlocks) expected.put(block.pos, block.blockState);
            for (WorldgenStructure.ColumnPlacement column : piece.columns) {
                @SuppressWarnings("unchecked") List<Template.BlockInfo> blocks = (List<Template.BlockInfo>) nativeBlocks.get(column.template);
                for (Template.BlockInfo block : blocks) check(block.blockState.equals(expected.remove(block.pos)),
                        "Projection must preserve each native source position and block state exactly once");
            }
            check(expected.isEmpty(), "Projected templates must retain every source block");
        }
        JsonObject tooHigh = copy(projected); tooHigh.getAsJsonObject("structure").addProperty("height", 254);
        check(fixture.compile(manager, tooHigh).plan(null, new Random(29), 0, 0) == null, "Projected bounds crossing Y255 reject before placement");
        check(fixture.compile(manager, projected).plan(null, new Random(29), 0, 0).pieces.size() == 2, "Rejected planning does not poison a later assembly");

        JsonObject fallbackCycle = copy(rigid);
        fallbackCycle.getAsJsonObject("pools").getAsJsonObject("fixture:assembly_start").addProperty("fallback", "fixture:assembly_blocked");
        fallbackCycle.getAsJsonObject("pools").getAsJsonObject("fixture:assembly_blocked").addProperty("fallback", "fixture:assembly_start");
        reject(() -> fixture.compile(manager, fallbackCycle));
        JsonObject unknown = copy(rigid); element(unknown, "fixture:assembly_start").getAsJsonArray("connectors").get(0).getAsJsonObject().addProperty("pool", "fixture:missing_pool");
        reject(() -> fixture.compile(manager, unknown));
        JsonObject outside = copy(rigid); element(outside, "fixture:assembly_start").getAsJsonArray("connectors").get(0).getAsJsonObject().add("pos", JSON.parse("[3,0,1]"));
        reject(() -> fixture.compile(manager, outside));
        JsonObject unsupported = copy(rigid); element(unsupported, "fixture:assembly_end").add("projection", JSON.parse("{\"type\":\"rml:terrain_projection\",\"offset\":1}"));
        reject(() -> fixture.compile(manager, unsupported));
        JsonObject depth = copy(rigid); depth.getAsJsonObject("structure").addProperty("max_depth", 65); reject(() -> fixture.compile(manager, depth));
    }

    /** Run on the server thread in a test dimension; the selected 3-by-6 area must have terrain below Y180. */
    public static void verifyInWorld(WorldServer world, int x, int z) throws Exception {
        Fixture fixture = new Fixture();
        WorldgenStructure structure = fixture.compile(manager(), copy(fixture.source));
        List<Snapshot> snapshots = new ArrayList<>();
        for (int dx = -2; dx <= 0; dx++) for (int dz = 0; dz <= 5; dz++) {
            if (world.getHeight(new BlockPos(x + dx, 0, z + dz)).getY() > 180) throw new IllegalArgumentException("Assembly test requires terrain below Y180");
            for (int y = 180; y <= 190; y++) snapshots.add(new Snapshot(world, new BlockPos(x + dx, y, z + dz)));
        }
        try {
            for (Snapshot snapshot : snapshots) world.setBlockState(snapshot.position, Blocks.AIR.getDefaultState(), 2);
            for (int dx = -2; dx <= 0; dx++) for (int dz = 0; dz <= 5; dz++) world.setBlockState(new BlockPos(x + dx, 180 + dz, z + dz), Blocks.DIRT.getDefaultState(), 2);
            WorldgenStructure.Plan plan = structure.plan(world, new Random(29), x, z);
            check(plan != null && plan.pieces.size() == 2, "Real terrain assembly chooses the fallback and terminates at two pieces");
            check(plan.bounds.minY == 181 && plan.bounds.maxY == 185, "Native terrain heights project the two floors before either template writes");
            checkConnection(plan);
            check(structure.place(world, new Random(29), x, z) != null, "Native assembly placement succeeds");
            for (int index = 0; index < plan.pieces.size(); index++) {
                WorldgenStructure.Piece piece = plan.pieces.get(index);
                for (WorldgenStructure.ColumnPlacement column : piece.columns) {
                    NBTTagList blocks = column.template.writeToNBT(new NBTTagCompound()).getTagList("blocks", 10);
                    for (int i = 0; i < blocks.tagCount(); i++) {
                        NBTTagList local = blocks.getCompoundTagAt(i).getTagList("pos", 3);
                        BlockPos position = Template.transformedBlockPos(piece.settings, new BlockPos(local.getIntAt(0), local.getIntAt(1), local.getIntAt(2))).add(column.origin);
                        check(world.getBlockState(position).getBlock() == (index == 0 ? Blocks.EMERALD_BLOCK : Blocks.DIAMOND_BLOCK), "Native template writes every projected assembly block");
                    }
                }
            }
        } finally {
            for (Snapshot snapshot : snapshots) snapshot.restore(world);
        }
        for (Snapshot snapshot : snapshots) check(world.getBlockState(snapshot.position).equals(snapshot.state), "Real-server fixture restores prior block states");
    }

    private static void checkConnection(WorldgenStructure.Plan plan) {
        WorldgenStructure.PlacedConnector outgoing = plan.pieces.get(0).connectors[0];
        WorldgenStructure.Piece child = plan.pieces.get(1);
        WorldgenStructure.PlacedConnector incoming = child.connectors[child.incoming];
        check(outgoing.position.offset(outgoing.facing).equals(incoming.position), "Connector endpoints occupy adjacent transformed world positions");
        check(outgoing.facing == incoming.facing.getOpposite() && outgoing.top == incoming.top, "Connector orientations and aligned joints match");
    }
    private static DeserializerManager manager() {
        DeserializerManager manager = new DeserializerManager("rml");
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft:resource_location"), ResourceLocation.class,
                value -> new ResourceLocation(manager.decode(String.class, value))));
        NativeTypeDecoders.register(manager, new CompiledTagModule()); WorldgenCodecs.registerDecoders(manager); return manager;
    }
    private static JsonObject element(JsonObject fixture, String pool) { return fixture.getAsJsonObject("pools").getAsJsonObject(pool).getAsJsonArray("elements").get(0).getAsJsonObject(); }
    private static JsonObject copy(JsonObject json) { return JSON.parse(json.toString()).getAsJsonObject(); }
    private static String text(String name) throws Exception {
        try (java.io.InputStream stream = WorldgenAssemblyContractTest.class.getResourceAsStream("/worldgen/" + name)) {
            if (stream == null) throw new AssertionError("Missing worldgen fixture " + name);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int count; (count = stream.read(buffer)) != -1;) bytes.write(buffer, 0, count);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private interface Checked { void run() throws Exception; }
    private static void reject(Checked action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException | rml.deserializer.JsonDeserializeException expected) { return; }
        throw new AssertionError("Invalid assembly definition was accepted");
    }
    private static final class Fixture {
        final JsonObject source;
        final Map<ResourceLocation, Template> templates = new HashMap<>();
        Fixture() throws Exception {
            source = JSON.parse(text("assembly.json")).getAsJsonObject();
            for (String name : new String[]{"assembly_start", "assembly_end"}) { Template template = new Template(); template.read(JsonToNBT.getTagFromJson(text(name + ".snbt"))); templates.put(new ResourceLocation("fixture", name), template); }
        }
        JsonObject rigid() {
            JsonObject result = copy(source); result.getAsJsonObject("structure").addProperty("height", 100);
            for (Map.Entry<String, JsonElement> entry : result.getAsJsonObject("pools").entrySet()) for (JsonElement element : entry.getValue().getAsJsonObject().getAsJsonArray("elements")) element.getAsJsonObject().remove("projection");
            return result;
        }
        WorldgenStructure compile(DeserializerManager manager, JsonObject fixture) {
            Map<String, WorldgenModule.Definition> definitions = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : fixture.getAsJsonObject("pools").entrySet()) {
                ResourceLocation id = new ResourceLocation(entry.getKey());
                definitions.put(WorldgenModule.definitionKey("worldgen/template_pool", id), WorldgenModule.Definition.parse("worldgen/template_pool", id, "assembly fixture", entry.getValue().getAsJsonObject()));
            }
            WorldgenModule.Definition definition = WorldgenModule.Definition.parse("worldgen/structure", new ResourceLocation("fixture:assembly"), "assembly fixture", fixture.getAsJsonObject("structure"));
            WorldgenStructure result = WorldgenStructure.compile(definition, definitions, manager); result.bindTemplates(templates::get); return result;
        }
    }
    private static final class Snapshot {
        final BlockPos position;
        final IBlockState state;
        final NBTTagCompound tile;
        Snapshot(WorldServer world, BlockPos position) {
            this.position = position; this.state = world.getBlockState(position);
            TileEntity existing = world.getTileEntity(position); this.tile = existing == null ? null : existing.writeToNBT(new NBTTagCompound());
        }
        void restore(WorldServer world) {
            world.setBlockState(position, state, 2);
            if (tile != null) { TileEntity restored = world.getTileEntity(position); if (restored == null) throw new IllegalStateException("Missing restored fixture tile entity at " + position); restored.readFromNBT(tile); restored.markDirty(); }
        }
    }
}
