package rml.compiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import rml.compiler.lang.FunctionCompiler;
import rml.compiler.lang.TagResolver;
import rml.compiler.rules.RuleCompiler;
import rml.compiler.source.Project;
import rml.compiler.source.ProjectReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Source language to RML protocol compiler; it imports no game or loader classes. */
public final class RmlCompiler {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> BUILTIN_TYPES = new HashSet<>(Arrays.asList(
            "function", "recipe", "advancement", "loot_table", "predicate", "item_modifier", "structure"));
    private static final Set<String> NATIVE_TYPES = new HashSet<>(Arrays.asList(
            "banner_pattern", "block_transformer", "cat_sound_variant", "cat_variant", "chat_type",
            "chicken_sound_variant", "chicken_variant", "context_float_provider", "context_int_provider",
            "cow_sound_variant", "cow_variant", "damage_type", "decorated_pot_pattern", "dialog",
            "dimension", "dimension_type", "enchantment", "enchantment_provider", "frog_variant",
            "instrument", "jukebox_song", "painting_variant", "pig_sound_variant", "pig_variant",
            "slot_source", "sulfur_cube_archetype", "test_environment", "test_instance", "timeline",
            "trade_set", "trial_spawner", "trim_material", "trim_pattern", "villager_trade",
            "wolf_sound_variant", "wolf_variant", "world_clock", "zombie_nautilus_variant",
            "worldgen/biome", "worldgen/block_state_provider", "worldgen/carver",
            "worldgen/density_function", "worldgen/feature", "worldgen/flat_level_generator_preset",
            "worldgen/material_condition", "worldgen/material_rule",
            "worldgen/multi_noise_biome_source_parameter_list", "worldgen/noise", "worldgen/noise_settings",
            "worldgen/placed_feature", "worldgen/processor_list", "worldgen/structure",
            "worldgen/structure_set", "worldgen/template_pool", "worldgen/world_preset",
            "item", "block", "machine_recipe"));

    private RmlCompiler() { }

    public static Set<String> getResourceTypes() {
        Set<String> types = new TreeSet<>(BUILTIN_TYPES);
        types.addAll(NATIVE_TYPES);
        return java.util.Collections.unmodifiableSet(types);
    }

    public static CompiledPack compile(Path input) throws IOException {
        return compile(ProjectReader.read(input));
    }

    public static CompiledPack compile(Project project) {
        for (SourceResource resource : project.getResources().values()) {
            if (!BUILTIN_TYPES.contains(resource.getType()) && !NATIVE_TYPES.contains(resource.getType())
                    && !resource.getType().startsWith("tags/")) {
                throw new CompileException("unsupported_feature", resource.getPath(), 0,
                        "The 1.12.2 RML target has no executable provider for " + resource.getType());
            }
        }
        Set<String> knownFunctions = new TreeSet<>(project.getExternalFunctions());
        for (SourceResource function : project.resources("function")) knownFunctions.add(function.getId());
        Map<String, List<String>> functionTags = TagResolver.resolve(project, "function", knownFunctions);
        Map<String, List<String>> functions = FunctionCompiler.compile(project, functionTags);
        Map<String, JsonElement> rules = RuleCompiler.compile(project, functionTags);
        Map<String, byte[]> output = new TreeMap<>();
        JsonObject sourceMap = new JsonObject();
        int resources = 0;
        for (Map.Entry<String, List<String>> function : functions.entrySet()) {
            JsonArray commands = strings(function.getValue());
            String origin = functionOrigin(project, function.getKey(), functionTags);
            emit(output, sourceMap, project, "function", function.getKey(), commands, origin);
            resources++;
        }
        for (Map.Entry<String, JsonElement> rule : rules.entrySet()) {
            int separator = rule.getKey().indexOf('|');
            if (separator <= 0) throw new IllegalStateException("Invalid lowered rule key: " + rule.getKey());
            String type = rule.getKey().substring(0, separator);
            String id = rule.getKey().substring(separator + 1);
            SourceResource origin = project.getResource(type, id);
            if (origin == null && "machine_recipe".equals(type)) origin = project.getResource("recipe", id);
            if (origin == null && "recipe".equals(type)) {
                int generated = id.indexOf("/__rml_smelting/");
                if (generated >= 0) origin = project.getResource(type, id.substring(0, generated));
            }
            if (origin == null) throw new IllegalStateException("Lowered rule lost its source: " + rule.getKey());
            emit(output, sourceMap, project, type, id, rule.getValue(), origin.getPath());
            resources++;
        }
        Set<String> registries = new TreeSet<>();
        for (SourceResource resource : project.getResources().values()) {
            if (resource.getType().startsWith("tags/")) registries.add(resource.getType().substring(5));
        }
        for (String registry : registries) {
            Map<String, List<String>> tags = "function".equals(registry) ? functionTags : TagResolver.resolve(project, registry, null);
            for (Map.Entry<String, List<String>> tag : tags.entrySet()) {
                String type = "function".equals(registry) ? "function_tag" : "tag/" + registry;
                emit(output, sourceMap, project, type, tag.getKey(), strings(tag.getValue()),
                        project.getResource("tags/" + registry, tag.getKey()).getPath());
                resources++;
            }
        }
        for (SourceResource structure : project.resources("structure")) {
            byte[] bytes = structure.getBytes();
            if (bytes.length < 2 || (bytes[0] & 255) != 31 || (bytes[1] & 255) != 139) {
                throw new CompileException("invalid_structure", structure.getPath(), 0, "Structures must contain compressed 1.12.2 NBT");
            }
            emit(output, sourceMap, project, "structure", structure.getId(),
                    new com.google.gson.JsonPrimitive(Base64.getEncoder().encodeToString(bytes)), structure.getPath());
            resources++;
        }
        for (SourceResource resource : project.getResources().values()) {
            if (!NATIVE_TYPES.contains(resource.getType())) continue;
            JsonElement definition = resource.getJson();
            if (definition == null || definition.isJsonNull()) {
                throw new CompileException("invalid_definition", resource.getPath(), 0, "Native definitions cannot be null");
            }
            emit(output, sourceMap, project, resource.getType(), resource.getId(), definition, resource.getPath());
            resources++;
        }
        for (Map.Entry<String, byte[]> asset : project.getAssets().entrySet()) {
            put(output, asset.getKey(), asset.getValue(), asset.getKey());
        }
        JsonArray info = new JsonArray();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_version", 3);
        pack.addProperty("modid", project.getId());
        pack.addProperty("name", project.getName());
        pack.addProperty("version", project.getVersion());
        pack.addProperty("mcversion", project.getMinecraftVersion());
        pack.addProperty("description", project.getDescription());
        pack.addProperty("useDependencyInformation", true);
        pack.add("dependencies", strings(project.getDependencies()));
        JsonArray modules = new JsonArray();
        JsonObject module = new JsonObject();
        module.addProperty("name", "rml:content");
        module.addProperty("location", "content");
        module.addProperty("forceLoaded", true);
        modules.add(module);
        pack.add("modules", modules);
        info.add(pack);
        put(output, "rml.info", encode(info), "pack.mcmeta");
        JsonObject clientMetadata = new JsonObject();
        JsonObject clientPack = new JsonObject();
        clientPack.addProperty("pack_format", 3);
        clientPack.addProperty("description", project.getName());
        clientMetadata.add("pack", clientPack);
        put(output, "pack.mcmeta", encode(clientMetadata), "pack.mcmeta");
        put(output, "META-INF/rml/source-map.json", encode(sourceMap), "pack.mcmeta");
        return new CompiledPack(output, resources, functions.size());
    }

    private static void emit(Map<String, byte[]> output, JsonObject sourceMap, Project project,
                             String type, String id, JsonElement value, String source) {
        id = Ids.parse(id);
        String path = "assets/" + project.getId() + "/content/" + type + "/" + Ids.namespace(id) + "/" + Ids.path(id) + ".json";
        JsonObject envelope = new JsonObject();
        envelope.addProperty("format", 1);
        envelope.addProperty("type", type);
        envelope.addProperty("id", id);
        envelope.add("value", value);
        put(output, path, encode(envelope), source);
        sourceMap.addProperty(path, source);
    }

    private static String functionOrigin(Project project, String id, Map<String, List<String>> tags) {
        SourceResource function = project.getResource("function", id);
        if (function != null) return function.getPath();
        for (String tag : tags.keySet()) {
            String wrapper = project.getId() + ":__rml/tags/" + Ids.namespace(tag) + "/" + Ids.path(tag);
            if (wrapper.equals(id)) return project.getResource("tags/function", tag).getPath();
        }
        throw new IllegalStateException("Generated function lost its source: " + id);
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }

    private static byte[] encode(JsonElement value) { return (JSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8); }

    private static void put(Map<String, byte[]> output, String path, byte[] bytes, String source) {
        if (output.putIfAbsent(path, bytes) != null) {
            throw new CompileException("output_collision", source, 0, "Multiple sources produce " + path);
        }
    }
}
