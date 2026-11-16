package rml.loader.content.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.loader.content.CompiledTagModule;
import rml.loader.deserialize.NativeTypeDecoders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Native primer, position-stream, template transform and persistence contracts; no game mocks. */
public final class WorldgenCapabilityContractTest {
    private static final JsonParser JSON = new JsonParser();
    private WorldgenCapabilityContractTest() { }

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        DeserializerManager manager = new DeserializerManager("rml");
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "resource_location"), ResourceLocation.class,
                value -> new ResourceLocation(manager.decode(String.class, value))));
        NativeTypeDecoders.register(manager, new CompiledTagModule());
        WorldgenCodecs.registerDecoders(manager);
        JsonObject fixtures = JSON.parse(text("capabilities.json")).getAsJsonObject();
        densityProducesEnclosedCavityAndOverhang(manager, fixtures);
        flatLayersRemainExact(manager, fixtures);
        nestedCustomNodesExecute(manager);
        orderedPlacementBranchesDeterministically(manager);
        nativeTemplateTransformAndProcessors(manager, fixtures);
        WorldgenAssemblyContractTest.verify(manager);
        structureStateSurvivesReloadWithoutDuplicateBounds();
        invalidDefinitionsFailBeforePlacement(manager, fixtures);
        System.out.println("WorldgenCapabilityContractTest passed");
    }

    private static void densityProducesEnclosedCavityAndOverhang(DeserializerManager manager, JsonObject fixtures) throws Exception {
        WorldgenCodecs.DensityFunction density = manager.decode(WorldgenCodecs.DensityFunction.class, fixtures.get("density"));
        WorldgenCodecs.MaterialRule material = manager.decode(WorldgenCodecs.MaterialRule.class, fixtures.get("material"));
        ChunkPrimer primer = new ChunkPrimer();
        RmlChunkGenerator.MaterialContext context = new RmlChunkGenerator.MaterialContext(71);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            RmlChunkGenerator.fillDensityColumn(primer, x, z, x, z, 71, density, material, context, Blocks.STONE.getDefaultState(), Blocks.WATER.getDefaultState(), 0, 64, 1.1);
        check(primer.getBlockState(8, 48, 8).getBlock() == Blocks.AIR, "Cavity center must remain empty below solid terrain");
        check(primer.getBlockState(8, 42, 8).getBlock() == Blocks.STONE, "Cavity has a floor");
        check(primer.getBlockState(8, 54, 8).getBlock() == Blocks.STONE, "Cavity has a solid overhang above the empty center");
        check(primer.getBlockState(2, 48, 8).getBlock() == Blocks.STONE, "Cavity has a horizontal wall");
        check(primer.getBlockState(8, 79, 8).getBlock() == Blocks.GOLD_BLOCK, "Ordered conditional material rules affect occupied voxels");
        check(primer.getBlockState(8, 80, 8).getBlock() == Blocks.AIR, "Zero density is outside terrain");
        check(primer.getBlockState(8, 255, 8).getBlock() == Blocks.AIR, "Top platform voxel is evaluated");
        check(primer.getBlockState(8, 0, 8).getBlock() == Blocks.BEDROCK, "Platform floor is retained");
    }

    private static void flatLayersRemainExact(DeserializerManager manager, JsonObject fixtures) {
        List<RmlChunkGenerator.FlatLayer> layers = RmlChunkGenerator.compileFlatLayers(fixtures.getAsJsonObject("flat"), manager);
        ChunkPrimer primer = new ChunkPrimer();
        RmlChunkGenerator.fillFlatColumn(primer, 0, 0, 0, 0, layers, null, new RmlChunkGenerator.MaterialContext(1));
        check(primer.getBlockState(0, 0, 0).getBlock() == Blocks.BEDROCK, "Flat bedrock starts at zero");
        check(primer.getBlockState(0, 1, 0).getBlock() == Blocks.DIRT && primer.getBlockState(0, 2, 0).getBlock() == Blocks.DIRT, "Flat thickness is exact");
        check(primer.getBlockState(0, 3, 0).getBlock() == Blocks.GRASS && primer.getBlockState(0, 4, 0).getBlock() == Blocks.AIR, "Flat layers do not acquire noise or sea fill");
    }

    private static void nestedCustomNodesExecute(DeserializerManager manager) throws Exception {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("fixture", "plane"), WorldgenCodecs.DensityFunction.class,
                value -> { int offset = value.getAsJsonObject().get("offset").getAsInt(); return (seed, x, y, z) -> y - offset; }));
        WorldgenCodecs.DensityFunction node = manager.decode(WorldgenCodecs.DensityFunction.class, JSON.parse("{\"type\":\"rml:density_abs\",\"input\":{\"type\":\"fixture:plane\",\"offset\":7}}"));
        check(node.sample(0, 0, 4, 0) == 3, "Custom registered children execute inside built-in density nodes");
        WorldgenCodecs.DensityFunction shifted = manager.decode(WorldgenCodecs.DensityFunction.class, JSON.parse("{\"type\":\"rml:density_shift\",\"input\":{\"type\":\"fixture:plane\",\"offset\":7},\"x\":0,\"y\":0.5,\"z\":0}"));
        check(shifted.sample(0, 0, 7, 0) == 0.5, "Fractional shifts retain sub-block density information");
        WorldgenCodecs.BlockStateProvider provider = manager.decode(WorldgenCodecs.BlockStateProvider.class, JSON.parse("{\"type\":\"rml:property_state\",\"state\":{\"Name\":\"minecraft:wheat\"},\"property\":\"age\",\"value\":7}"));
        check(provider.provide(new Random(0), BlockPos.ORIGIN).getValue(net.minecraft.block.BlockCrops.AGE) == 7, "Property state provider applies a shared integer distribution");
    }

    private static void orderedPlacementBranchesDeterministically(DeserializerManager manager) throws Exception {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("fixture", "even_x"), WorldgenPlacement.Filter.class,
                value -> (context, x, y, z) -> (x & 1) == 0));
        JsonArray source = JSON.parse("[{\"type\":\"rml:count\",\"count\":4},{\"type\":\"rml:rarity\",\"once_every\":1},{\"type\":\"rml:in_square\"},{\"type\":\"rml:height\",\"height\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":40,\"max_inclusive\":43}},{\"type\":\"rml:filter\",\"filter\":{\"type\":\"fixture:even_x\"}}]").getAsJsonArray();
        WorldgenPlacement.Next placement = WorldgenPlacement.compile(manager, source);
        List<BlockPos> actual = new ArrayList<>();
        placement.place(new WorldgenPlacement.Context(null, new Random(83), (context, x, y, z) -> actual.add(new BlockPos(x, y, z))), 32, 0, -16);
        List<BlockPos> expected = new ArrayList<>();
        Random random = new Random(83);
        for (int i = 0; i < 4; i++) {
            random.nextInt(1);
            int x = 32 + random.nextInt(16), z = -16 + random.nextInt(16);
            // Use the public shared distribution to retain its documented sampling stream.
            int y = manager.decode(rml.deserializer.value.IntProvider.class, JSON.parse("{\"type\":\"minecraft:uniform\",\"min_inclusive\":40,\"max_inclusive\":43}")).sample(random);
            if ((x & 1) == 0) expected.add(new BlockPos(x, y, z));
        }
        check(actual.equals(expected), "Count branches consume rarity, square, height and custom filters in declaration order");
        JsonArray zero = JSON.parse("[{\"type\":\"rml:count\",\"count\":0}]").getAsJsonArray();
        WorldgenPlacement.compile(manager, zero).place(new WorldgenPlacement.Context(null, random, (context, x, y, z) -> { throw new AssertionError("Zero count emitted a feature"); }), 0, 0, 0);
    }

    private static void nativeTemplateTransformAndProcessors(DeserializerManager manager, JsonObject fixtures) throws Exception {
        Template template = new Template();
        template.read(JsonToNBT.getTagFromJson(text("beam.snbt")));
        WorldgenCodecs.TemplateProcessor processors = WorldgenCodecs.decodeProcessorList(manager, fixtures.get("processors"));
        BlockPos origin = processors.transformOrigin(new BlockPos(10, 100, 10));
        check(origin.equals(new BlockPos(10, 100, 12)), "Rotation transforms an earlier translation");
        PlacementSettings settings = new PlacementSettings().setRotation(processors.rotation());
        StructureBoundingBox bounds = WorldgenStructure.bounds(origin, template.getSize(), settings);
        check(bounds.minX == 9 && bounds.maxX == 10 && bounds.minZ == 12 && bounds.maxZ == 14, "Rotated asymmetric native template has correct bounds");
        Template.BlockInfo stone = new Template.BlockInfo(BlockPos.ORIGIN, Blocks.STONE.getDefaultState(), null);
        check(processors.processBlock(null, origin, stone).blockState.getBlock() == Blocks.GOLD_BLOCK, "Replacement processors change template blocks");
        Template.BlockInfo dirt = new Template.BlockInfo(BlockPos.ORIGIN, Blocks.DIRT.getDefaultState(), null);
        check(processors.processBlock(null, origin, dirt) == null, "Ignored native blocks are omitted");
        IBlockState stairs = Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.NORTH).withRotation(settings.getRotation());
        check(stairs.getValue(BlockStairs.FACING) == EnumFacing.EAST, "Placement rotation transforms directional native states");
    }

    private static void structureStateSurvivesReloadWithoutDuplicateBounds() {
        WorldgenStructureData original = new WorldgenStructureData("fixture");
        ResourceLocation id = new ResourceLocation("fixture", "beam");
        original.processed.put(id, new HashSet<>()); original.processed.get(id).add(17L);
        original.bounds.add(new RmlChunkGenerator.StructureBounds(id, -4, 100, 7, -1, 101, 9));
        NBTTagCompound saved = original.writeToNBT(new NBTTagCompound());
        WorldgenStructureData restored = new WorldgenStructureData("fixture");
        restored.readFromNBT(saved); restored.readFromNBT(saved);
        check(restored.processed.get(id).contains(17L) && restored.bounds.size() == 1, "Reload preserves idempotence and clears prior bounds");
        check(restored.bounds.get(0).contains(new BlockPos(-2, 100, 8)), "Negative-coordinate transformed bounds survive persistence");
    }

    private static void invalidDefinitionsFailBeforePlacement(DeserializerManager manager, JsonObject fixtures) throws Exception {
        for (JsonElement height : fixtures.getAsJsonArray("negative_heights")) reject(() -> WorldgenPlacement.height(manager, height));
        for (JsonElement state : fixtures.getAsJsonArray("negative_states")) reject(() -> WorldgenModule.resolveBlockState(state));
        reject(() -> RmlChunkGenerator.compileFlatLayers(JSON.parse("{\"layers\":[{\"height\":257,\"block\":\"minecraft:stone\"}]}").getAsJsonObject(), manager));
        reject(() -> manager.decode(WorldgenCodecs.DensityFunction.class, JSON.parse("{\"type\":\"rml:density_reference\",\"name\":\"fixture:missing\"}")));
        reject(() -> WorldgenPlacement.compile(manager, JSON.parse("[{\"type\":\"rml:biome\",\"biomes\":[\"fixture:missing\"]}]").getAsJsonArray()));
        Map<String, WorldgenModule.Definition> definitions = new HashMap<>();
        put(definitions, "worldgen/feature", "marker", fixtures.getAsJsonObject("feature"));
        JsonObject placed = JSON.parse(fixtures.get("placed").toString()).getAsJsonObject(); placed.addProperty("count", 2);
        put(definitions, "worldgen/placed_feature", "marker", placed);
        reject(() -> RmlChunkGenerator.validateExecutableSettings(JSON.parse("{\"features\":[\"fixture:marker\"]}").getAsJsonObject(), definitions, manager));
        JsonObject badStructure = JSON.parse("{\"template\":\"fixture:beam\",\"height\":256}").getAsJsonObject();
        WorldgenModule.Definition definition = WorldgenModule.Definition.parse("worldgen/structure", new ResourceLocation("fixture", "bad"), "fixture", badStructure);
        reject(() -> WorldgenStructure.compile(definition, definitions, manager));
    }

    private static void put(Map<String, WorldgenModule.Definition> definitions, String type, String name, JsonObject value) {
        ResourceLocation id = new ResourceLocation("fixture", name);
        definitions.put(WorldgenModule.definitionKey(type, id), WorldgenModule.Definition.parse(type, id, "fixture", value));
    }
    private static String text(String file) throws Exception {
        try (java.io.InputStream stream = WorldgenCapabilityContractTest.class.getResourceAsStream("/worldgen/" + file)) {
            if (stream == null) throw new AssertionError("Missing worldgen fixture " + file);
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
        throw new AssertionError("Invalid worldgen definition was accepted");
    }
}
