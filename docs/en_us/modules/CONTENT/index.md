### Enhanced data packs (`rml:content`)

`rml:content` is the compiled content entry point of RML 2.0. It reads typed definitions produced by `compiler/` and hands them to native modules at startup or at runtime. A plain resource-pack directory does not trigger these definitions.

Default location: `/content/`.

### Source layout

The compiler accepts a directory or ZIP whose entry point is the `rml` metadata in `pack.mcmeta`:

```json
{
  "pack": { "pack_format": 1, "description": "RML enhanced data-pack example" },
  "rml": {
    "language_version": 1,
    "id": "example_pack",
    "name": "Example Pack",
    "version": "1.0",
    "minecraft": "1.12.2",
    "constants": { "reward_item": "minecraft:diamond" }
  }
}
```

```text
pack.mcmeta
data/example_pack/function/load.mcfunction
data/example_pack/loot_table/reward.json
data/example_pack/predicate/allowed.json
data/example_pack/item_modifier/double_count.json
data/example_pack/damage_type/arcane.json
data/example_pack/timeline/welcome.json
data/example_pack/test_instance/gold_block.json
data/minecraft/tags/function/load.json
assets/example_pack/models/item/token.json
```

`data/<namespace>/<type>/<path>.json` holds definitions, `data/<namespace>/function/**/*.mcfunction` holds functions, `data/<namespace>/tags/<registry>/...` holds tags, and `assets/**` is copied through. `constants` are expanded once at compile time; still-unbound names remain runtime parameters.

### Compile and output

```text
gradlew.bat -p compiler check executableJar
java -jar compiler/build/libs/rml-compiler.jar capabilities
java -jar compiler/build/libs/rml-compiler.jar check dev/datapack-example
java -jar compiler/build/libs/rml-compiler.jar compile dev/datapack-example build/example.zip
```

The result is an installable RML pack containing:

- `rml.info`, whose module list declares only `rml:content`.
- `assets/<pack>/content/<type>/<namespace>/<path>.json`, each an envelope of `{"format":1,"type":...,"id":...,"value":...}`.
- `META-INF/rml/source-map.json`, mapping output paths back to sources for diagnostics.
- `pack.mcmeta` and the original client assets.

Output-path collisions, unknown types, invalid structure NBT and unreadable sources fail the build. No partially usable ZIP is written.

### Runtime

`rml:content` validates `format`, `type`, `id` and `value`. An unregistered `type` fails during preparation instead of being skipped silently.

| Lifecycle | Types |
| --- | --- |
| Reloadable | `function`, `function_tag`, `tag/<registry>`, `predicate`, `item_modifier`, `loot_table`, `structure`, `context_int_provider`, `context_float_provider` |
| Startup registration | `recipe`, `advancement`, `dimension`, `dimension_type`, `item`, `block`, `enchantment`, `damage_type`, `villager_trade`, `trade_set`, `trim_material`, `trim_pattern`, `dialog`, `worldgen/*` and the remaining native definition types |

Startup types are registered during game startup or world load; adding, changing or removing them requires a restart. Reloadable types are committed through a function-module candidate generation:

```text
/rml functions status
/rml functions reload
/rml functions reload example_pack
/rml functions unload example_pack
```

`reload` prepares the whole candidate before replacing anything and keeps the previous generation on failure. Cross-pack references must be declared in the `requires` field of the `rml:functions` module; cycles and unloading a still-referenced pack are rejected.

### Tooling

```text
/rml capabilities
/rml capabilities decoders
/rml tools inspect
/rml tools check [modid]
/rml tools apply <candidate> <revision>
/rml tools deactivate <session> <revision> <modid> [modid...]
```

`check` prepares a candidate without activating it and returns `candidate`, `expected_revision` and the affected units; `apply` commits with those two values. `requires_restart` reports changes in startup definitions. `inspect` lists the `startup` / `reloadable` capability of every type, active world tests and the current session. See [functions](../FUNCTIONS/index.md#structured-runtime-tools) for the full command contract.

### Definition values

Each definition's `value` uses the shared RML decoder registry. Primitives, ranges, weighted lists, block states, NBT and matchers, plus named numeric, predicate, modifier, effect, worldgen, presentation, clock and test nodes, follow the same nesting rules. An item modifier example:

```json
{
  "function": "minecraft:set_count",
  "count": { "type": "minecraft:uniform", "min": 2, "max": 4 },
  "conditions": [
    { "condition": "rml:expression", "expression": { "type": "rml:enchantment_level", "enchantment": "minecraft:fortune", "level": { "min": 3 } } }
  ]
}
```

The authoritative name list is `/rml capabilities decoders`; fields and composition are documented in [deserializers](../../deserializer/index.md) and [native composed values](../../java/deserializer/index.md#native-composed-values).

### Verification boundary

A successful compile only proves that sources parse and compile-time references resolve. Native registration, world generation, client presentation and save behaviour still require a running game. Verify reloadable content with `check` / `apply`, and startup content with `inspect` plus in-game commands after a restart.
