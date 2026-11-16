package rml.loader.content.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.IntProvider;
import rml.loader.content.CompiledStructureModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/** Plans a bounded connector graph before placing transformed native templates. */
final class WorldgenStructure {
    private static final Rotation[] ROTATIONS = Rotation.values();
    final ResourceLocation id;
    private final Pool root;
    private final Map<ResourceLocation, Pool> pools;
    private final IntProvider height;
    private final int minY, maxY, maxDepth, maxPieces, maxDistance;
    private final WorldgenPlacement.Filter filter;
    private final Rotation rotation;
    private final Mirror mirror;

    private WorldgenStructure(ResourceLocation id, Pool root, Map<ResourceLocation, Pool> pools, IntProvider height,
                              int minY, int maxY, int maxDepth, int maxPieces, int maxDistance,
                              WorldgenPlacement.Filter filter, Rotation rotation, Mirror mirror) {
        this.id = id; this.root = root; this.pools = pools; this.height = height;
        this.minY = minY; this.maxY = maxY; this.maxDepth = maxDepth; this.maxPieces = maxPieces; this.maxDistance = maxDistance;
        this.filter = filter; this.rotation = rotation; this.mirror = mirror;
    }

    static WorldgenStructure compile(WorldgenModule.Definition definition, Map<String, WorldgenModule.Definition> definitions,
                                     DeserializerManager manager) {
        JsonObject json = definition.json;
        WorldgenNodes.fields(json, "template", "template_pool", "processor_list", "height", "min_y", "max_y", "filter", "rotation", "mirror", "max_depth", "max_pieces", "max_distance");
        if (json.has("template") == json.has("template_pool")) throw new IllegalArgumentException("Structure requires exactly one template or template_pool");
        int minY = integer(json, "min_y", 0), maxY = integer(json, "max_y", 255);
        int maxDepth = integer(json, "max_depth", 8), maxPieces = integer(json, "max_pieces", 128), maxDistance = integer(json, "max_distance", 128);
        if (minY < 0 || maxY > 255 || minY > maxY) throw new IllegalArgumentException("Structure bounds must be within 0..255");
        if (maxDepth < 0 || maxDepth > 64 || maxPieces < 1 || maxPieces > 1024 || maxDistance < 1 || maxDistance > 512)
            throw new IllegalArgumentException("Assembly limits require depth 0..64, pieces 1..1024 and distance 1..512");
        Rotation rotation = json.has("rotation") ? Rotation.valueOf(json.get("rotation").getAsString().toUpperCase(java.util.Locale.ROOT)) : Rotation.NONE;
        Mirror mirror = json.has("mirror") ? Mirror.valueOf(json.get("mirror").getAsString().toUpperCase(java.util.Locale.ROOT)) : Mirror.NONE;
        try {
            IntProvider height = json.has("height") ? WorldgenPlacement.height(manager, json.get("height")) : null;
            WorldgenPlacement.Filter filter = json.has("filter") ? manager.decode(WorldgenPlacement.Filter.class, json.get("filter")) : (context, x, y, z) -> true;
            Compiler compiler = new Compiler(definitions, manager, json);
            Pool root;
            if (json.has("template")) {
                JsonObject element = new JsonObject(); element.add("template", json.get("template"));
                root = new Pool(definition.id); root.elements = new Element[]{compiler.element(element)}; root.weight = 1;
            } else root = compiler.pool(manager.decode(ResourceLocation.class, json.get("template_pool")));
            compiler.checkFallbacks();
            return new WorldgenStructure(definition.id, root, compiler.pools, height, minY, maxY, maxDepth, maxPieces, maxDistance, filter, rotation, mirror);
        } catch (JsonDeserializeException error) { throw new IllegalArgumentException("Invalid structure " + definition.id + ": " + error.getMessage(), error); }
    }

    void validateTemplates(CompiledStructureModule templates) { bindTemplates(templates::findTemplate); }
    void bindTemplates(Function<ResourceLocation, Template> lookup) {
        Map<ResourceLocation, NativeTemplate> compiled = new HashMap<>();
        Set<Pool> all = new HashSet<>(pools.values()); all.add(root);
        for (Pool pool : all) for (Element element : pool.elements) {
            NativeTemplate nativeTemplate = compiled.get(element.id);
            if (nativeTemplate == null) {
                Template template = lookup.apply(element.id);
                if (template == null) throw new IllegalArgumentException("Unknown native template " + element.id + " for " + id);
                BlockPos size = template.getSize();
                if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1 || size.getY() > 256)
                    throw new IllegalArgumentException("Invalid native template size for " + element.id);
                nativeTemplate = new NativeTemplate(template); compiled.put(element.id, nativeTemplate);
            }
            element.nativeTemplate = nativeTemplate;
            if (!element.projection.isRigid() && nativeTemplate.columns == null) nativeTemplate.columns = WorldgenAssembly.columns(nativeTemplate.template);
            for (WorldgenAssembly.Connector connector : element.connectors) validateConnector(connector, nativeTemplate.template.getSize(), element.id);
        }
    }

    StructureBoundingBox place(World world, Random random, int x, int z) {
        Plan plan = plan(world, random, x, z);
        if (plan == null) return null;
        for (Piece piece : plan.pieces) piece.place(world);
        return plan.bounds;
    }
    StructureBoundingBox locate(World world, Random random, int x, int z) {
        Plan plan = plan(world, random, x, z); return plan == null ? null : plan.bounds;
    }

    Plan plan(World world, Random random, int x, int z) {
        int y = height == null ? world.getHeight(new BlockPos(x, 0, z)).getY() : height.sample(random);
        BlockPos start = new BlockPos(x, y, z);
        WorldgenPlacement.Context context = new WorldgenPlacement.Context(world, random, (c, px, py, pz) -> { throw new IllegalStateException("Structure filters cannot emit positions"); });
        Elevations elevations = new Elevations(world);
        List<Piece> pieces = new ArrayList<>();
        Piece first = null;
        for (Pool pool = root; pool != null && first == null; pool = pool.fallback) {
            for (Element element : order(pool, random)) {
                BlockPos origin = element.processor.transformOrigin(start);
                Piece candidate = piece(elevations, element, origin, settings(element, rotation), BlockPos.ORIGIN, 0, -1);
                if (acceptable(context, candidate, pieces, start)) { first = candidate; break; }
            }
        }
        if (first == null) return null;
        pieces.add(first);
        for (int cursor = 0; cursor < pieces.size() && pieces.size() < maxPieces; cursor++) {
            Piece parent = pieces.get(cursor);
            if (parent.depth >= maxDepth) continue;
            for (int index = 0; index < parent.connectors.length && pieces.size() < maxPieces; index++) {
                if (index == parent.incoming) continue;
                PlacedConnector outgoing = parent.connectors[index];
                if (outgoing.source.pool == null) continue;
                Piece child = attach(context, elevations, outgoing, parent.depth + 1, pieces, start);
                if (child != null) pieces.add(child);
            }
        }
        StructureBoundingBox bounds = copy(first.bounds);
        for (int i = 1; i < pieces.size(); i++) bounds.expandTo(pieces.get(i).bounds);
        return new Plan(Collections.unmodifiableList(pieces), bounds);
    }

    private Piece attach(WorldgenPlacement.Context context, Elevations elevations, PlacedConnector outgoing, int depth, List<Piece> pieces, BlockPos start) {
        BlockPos target = outgoing.position.offset(outgoing.facing);
        for (Pool pool = pools.get(outgoing.source.pool); pool != null; pool = pool.fallback) {
            for (Element element : order(pool, context.random)) {
                int firstRotation = context.random.nextInt(4);
                for (int turn = 0; turn < 4; turn++) {
                    PlacementSettings settings = settings(element, ROTATIONS[(firstRotation + turn) & 3]);
                    for (int index = 0; index < element.connectors.length; index++) {
                        WorldgenAssembly.Connector incoming = element.connectors[index];
                        if (!outgoing.source.target.equals(incoming.name)) continue;
                        if (WorldgenAssembly.transform(incoming.facing, settings) != outgoing.facing.getOpposite()) continue;
                        if (outgoing.source.aligned && WorldgenAssembly.transform(incoming.top, settings) != outgoing.top) continue;
                        BlockPos local = Template.transformedBlockPos(settings, incoming.position);
                        BlockPos desiredOrigin = target.subtract(local);
                        BlockPos offset = element.processor.transformOrigin(BlockPos.ORIGIN);
                        BlockPos origin = element.processor.transformOrigin(desiredOrigin.subtract(offset));
                        Piece candidate = piece(elevations, element, origin, settings, incoming.position, depth, index);
                        if (candidate != null && candidate.connectors[index].position.equals(target) && acceptable(context, candidate, pieces, start)) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private Piece piece(Elevations elevations, Element element, BlockPos origin, PlacementSettings settings, BlockPos anchor, int depth, int incoming) {
        if (element.nativeTemplate == null) throw new IllegalStateException("Structure templates have not been bound for " + id);
        NativeTemplate nativeTemplate = element.nativeTemplate;
        BlockPos size = nativeTemplate.template.getSize();
        boolean rigid = element.projection.isRigid();
        BlockPos anchorWorld = Template.transformedBlockPos(settings, anchor).add(origin);
        int reference = rigid ? 0 : elevations.at(element.projection, anchorWorld.getX(), anchorWorld.getZ());
        ColumnPlacement[] columns = rigid ? null : new ColumnPlacement[nativeTemplate.columns.length];
        StructureBoundingBox bounds = rigid ? bounds(origin, size, settings) : null;
        if (!rigid) for (int i = 0; i < columns.length; i++) {
            WorldgenAssembly.Column column = nativeTemplate.columns[i];
            BlockPos at = Template.transformedBlockPos(settings, new BlockPos(column.x, 0, column.z)).add(origin);
            long shiftedY = (long)origin.getY() + elevations.at(element.projection, at.getX(), at.getZ()) - reference;
            if (shiftedY < minY || shiftedY + size.getY() - 1 > maxY) return null;
            BlockPos shiftedOrigin = new BlockPos(origin.getX(), (int)shiftedY, origin.getZ());
            columns[i] = new ColumnPlacement(column.template, shiftedOrigin);
            StructureBoundingBox columnBounds = new StructureBoundingBox(at.getX(), (int)shiftedY, at.getZ(), at.getX(), (int)shiftedY + size.getY() - 1, at.getZ());
            if (bounds == null) bounds = columnBounds; else bounds.expandTo(columnBounds);
        }
        PlacedConnector[] connectors = new PlacedConnector[element.connectors.length];
        for (int i = 0; i < connectors.length; i++) {
            WorldgenAssembly.Connector source = element.connectors[i];
            BlockPos position = Template.transformedBlockPos(settings, source.position).add(origin);
            long projectedY = rigid ? position.getY() : (long)position.getY() + elevations.at(element.projection, position.getX(), position.getZ()) - reference;
            if (projectedY < minY || projectedY > maxY) return null;
            position = new BlockPos(position.getX(), (int)projectedY, position.getZ());
            connectors[i] = new PlacedConnector(source, position, WorldgenAssembly.transform(source.facing, settings), WorldgenAssembly.transform(source.top, settings));
            StructureBoundingBox connectorBounds = new StructureBoundingBox(position.getX(), position.getY(), position.getZ(), position.getX(), position.getY(), position.getZ());
            if (bounds == null) bounds = connectorBounds; else bounds.expandTo(connectorBounds);
        }
        if (bounds == null) bounds = bounds(origin, size, settings);
        settings.setBoundingBox(bounds);
        return new Piece(element, origin, settings, bounds, connectors, columns, depth, incoming);
    }

    private boolean acceptable(WorldgenPlacement.Context context, Piece candidate, List<Piece> pieces, BlockPos start) {
        if (candidate == null) return false;
        StructureBoundingBox box = candidate.bounds;
        if (box.minY < minY || box.maxY > maxY || (long)box.minX < (long)start.getX() - maxDistance
                || (long)box.maxX > (long)start.getX() + maxDistance || (long)box.minZ < (long)start.getZ() - maxDistance
                || (long)box.maxZ > (long)start.getZ() + maxDistance || (long)box.minY < (long)start.getY() - maxDistance
                || (long)box.maxY > (long)start.getY() + maxDistance) return false;
        for (Piece other : pieces) if (box.intersectsWith(other.bounds)) return false;
        return filter.test(context, candidate.origin.getX(), candidate.origin.getY(), candidate.origin.getZ());
    }
    private PlacementSettings settings(Element element, Rotation rotation) {
        return new PlacementSettings().setMirror(mirror).setRotation(WorldgenCodecs.compose(rotation, element.processor.rotation()));
    }
    private static Element[] order(Pool pool, Random random) {
        Element[] remaining = pool.elements.clone(), result = new Element[remaining.length];
        int total = pool.weight;
        for (int index = 0; index < result.length; index++) {
            int selected = random.nextInt(total);
            for (int i = 0; i < remaining.length; i++) if (remaining[i] != null) {
                Element element = remaining[i];
                if (selected < element.weight) { result[index] = element; total -= element.weight; remaining[i] = null; break; }
                selected -= element.weight;
            }
        }
        return result;
    }
    private static void validateConnector(WorldgenAssembly.Connector connector, BlockPos size, ResourceLocation template) {
        BlockPos p = connector.position;
        if (p.getX() < 0 || p.getY() < 0 || p.getZ() < 0 || p.getX() >= size.getX() || p.getY() >= size.getY() || p.getZ() >= size.getZ())
            throw new IllegalArgumentException("Connector lies outside template " + template);
        boolean boundary;
        switch (connector.facing) {
            case WEST: boundary = p.getX() == 0; break;
            case EAST: boundary = p.getX() == size.getX() - 1; break;
            case DOWN: boundary = p.getY() == 0; break;
            case UP: boundary = p.getY() == size.getY() - 1; break;
            case NORTH: boundary = p.getZ() == 0; break;
            default: boundary = p.getZ() == size.getZ() - 1;
        }
        if (!boundary) throw new IllegalArgumentException("Connector must face out of template boundary " + template);
    }
    static StructureBoundingBox bounds(BlockPos origin, BlockPos size, PlacementSettings settings) {
        BlockPos a = Template.transformedBlockPos(settings, BlockPos.ORIGIN).add(origin);
        BlockPos b = Template.transformedBlockPos(settings, new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1)).add(origin);
        return new StructureBoundingBox(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }
    private static StructureBoundingBox copy(StructureBoundingBox box) { return new StructureBoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ); }
    private static int integer(JsonObject json, String key, int fallback) { return json.has(key) ? json.get(key).getAsBigDecimal().intValueExact() : fallback; }
    private static final class Elevations {
        final World world;
        final Map<WorldgenAssembly.Projection, Map<Long, Integer>> values = new java.util.IdentityHashMap<>();
        Elevations(World world) { this.world = world; }
        int at(WorldgenAssembly.Projection projection, int x, int z) {
            Map<Long, Integer> samples = values.computeIfAbsent(projection, key -> new HashMap<>());
            long key = ((long)x << 32) ^ (z & 0xffffffffL);
            Integer value = samples.get(key);
            if (value == null) { value = projection.elevation(world, x, z); samples.put(key, value); }
            return value;
        }
    }

    static final class Plan {
        final List<Piece> pieces;
        final StructureBoundingBox bounds;
        Plan(List<Piece> pieces, StructureBoundingBox bounds) { this.pieces = pieces; this.bounds = bounds; }
    }
    static final class Piece {
        final Element element;
        final BlockPos origin;
        final PlacementSettings settings;
        final StructureBoundingBox bounds;
        final PlacedConnector[] connectors;
        final ColumnPlacement[] columns;
        final int depth, incoming;
        Piece(Element element, BlockPos origin, PlacementSettings settings, StructureBoundingBox bounds, PlacedConnector[] connectors, ColumnPlacement[] columns, int depth, int incoming) {
            this.element = element; this.origin = origin; this.settings = settings; this.bounds = bounds;
            this.connectors = connectors; this.columns = columns; this.depth = depth; this.incoming = incoming;
        }
        void place(World world) {
            if (columns == null) element.nativeTemplate.template.addBlocksToWorld(world, origin, element.processor, settings, 2);
            else for (ColumnPlacement column : columns) column.template.addBlocksToWorld(world, column.origin, element.processor, settings, 2);
        }
        ResourceLocation templateId() { return element.id; }
    }
    static final class PlacedConnector {
        final WorldgenAssembly.Connector source;
        final BlockPos position;
        final EnumFacing facing, top;
        PlacedConnector(WorldgenAssembly.Connector source, BlockPos position, EnumFacing facing, EnumFacing top) { this.source = source; this.position = position; this.facing = facing; this.top = top; }
    }
    static final class ColumnPlacement {
        final Template template;
        final BlockPos origin;
        ColumnPlacement(Template template, BlockPos origin) { this.template = template; this.origin = origin; }
    }
    private static final class NativeTemplate {
        final Template template;
        WorldgenAssembly.Column[] columns;
        NativeTemplate(Template template) { this.template = template; }
    }
    private static final class Element {
        final ResourceLocation id;
        final int weight;
        final WorldgenCodecs.TemplateProcessor processor;
        final WorldgenAssembly.Connector[] connectors;
        final WorldgenAssembly.Projection projection;
        NativeTemplate nativeTemplate;
        Element(ResourceLocation id, int weight, WorldgenCodecs.TemplateProcessor processor, WorldgenAssembly.Connector[] connectors, WorldgenAssembly.Projection projection) {
            this.id = id; this.weight = weight; this.processor = processor; this.connectors = connectors; this.projection = projection;
        }
    }
    private static final class Pool {
        final ResourceLocation id;
        Element[] elements = new Element[0];
        int weight;
        Pool fallback;
        Pool(ResourceLocation id) { this.id = id; }
    }
    private static final class Compiler {
        final Map<String, WorldgenModule.Definition> definitions;
        final DeserializerManager manager;
        final JsonObject structure;
        final Map<ResourceLocation, Pool> pools = new LinkedHashMap<>();
        Compiler(Map<String, WorldgenModule.Definition> definitions, DeserializerManager manager, JsonObject structure) { this.definitions = definitions; this.manager = manager; this.structure = structure; }
        Pool pool(ResourceLocation id) throws JsonDeserializeException {
            Pool existing = pools.get(id); if (existing != null) return existing;
            WorldgenModule.Definition definition = WorldgenModule.find(definitions, "worldgen/template_pool", id);
            if (definition == null) throw new IllegalArgumentException("Unknown template_pool " + id);
            WorldgenNodes.fields(definition.json, "elements", "fallback");
            Pool pool = new Pool(id); pools.put(id, pool);
            JsonArray entries = definition.json.getAsJsonArray("elements");
            if (entries == null) throw new IllegalArgumentException("Template pool requires elements array");
            pool.elements = new Element[entries.size()]; long weight = 0;
            for (int i = 0; i < pool.elements.length; i++) { pool.elements[i] = element(entries.get(i).getAsJsonObject()); weight += pool.elements[i].weight; }
            if (weight > Integer.MAX_VALUE) throw new IllegalArgumentException("Template pool weight exceeds integer range");
            pool.weight = (int)weight;
            if (definition.json.has("fallback")) pool.fallback = pool(manager.decode(ResourceLocation.class, definition.json.get("fallback")));
            for (Element element : pool.elements) for (WorldgenAssembly.Connector connector : element.connectors) if (connector.pool != null) pool(connector.pool);
            return pool;
        }
        Element element(JsonObject json) throws JsonDeserializeException {
            WorldgenNodes.fields(json, "template", "weight", "processor_list", "connectors", "projection");
            int weight = integer(json, "weight", 1); if (weight < 1) throw new IllegalArgumentException("Template weights must be positive");
            JsonArray processors = new JsonArray(); appendProcessors(processors, json); appendProcessors(processors, structure);
            JsonObject list = new JsonObject(); list.add("processors", processors);
            WorldgenAssembly.Connector[] connectors;
            if (json.has("connectors")) {
                JsonArray entries = json.getAsJsonArray("connectors"); connectors = new WorldgenAssembly.Connector[entries.size()];
                for (int i = 0; i < connectors.length; i++) connectors[i] = manager.decode(WorldgenAssembly.Connector.class, entries.get(i));
            } else connectors = new WorldgenAssembly.Connector[0];
            WorldgenAssembly.Projection projection = json.has("projection") ? manager.decode(WorldgenAssembly.Projection.class, json.get("projection")) : WorldgenAssembly.RIGID;
            return new Element(manager.decode(ResourceLocation.class, json.get("template")), weight, WorldgenCodecs.decodeProcessorList(manager, list), connectors, projection);
        }
        void appendProcessors(JsonArray result, JsonObject source) throws JsonDeserializeException {
            if (!source.has("processor_list")) return;
            ResourceLocation id = manager.decode(ResourceLocation.class, source.get("processor_list"));
            WorldgenModule.Definition list = WorldgenModule.find(definitions, "worldgen/processor_list", id);
            if (list == null) throw new IllegalArgumentException("Unknown processor_list " + id);
            for (JsonElement processor : list.json.getAsJsonArray("processors")) result.add(processor);
        }
        void checkFallbacks() {
            for (Pool start : pools.values()) {
                Set<Pool> seen = new HashSet<>();
                for (Pool pool = start; pool != null; pool = pool.fallback) if (!seen.add(pool)) throw new IllegalArgumentException("Cyclic template pool fallback at " + pool.id);
            }
        }
    }
}
