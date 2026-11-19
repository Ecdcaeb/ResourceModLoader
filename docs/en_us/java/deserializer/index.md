## Deserializer

`Deserializer` turns JSON into typed Java objects.

- `rml.loader.deserialize.Deserializer` is the manager used inside RML. Use it if you depend on RML.
- `rml.deserializer.DeserializerBuilder` builds object parsers for `JsonObject`.
- `rml.loader.deserialize.Deserializer#decode` decodes one value.
- `rml.deserializer.Argument` is a named field action for `DeserializerBuilder`.

### Generics and nested containers

`List<T>`, `Set<T>`, `Collection<T>`, `Iterable<T>`, `Map<K,V>`, `Optional<T>` and generic arrays recurse through the same `DeserializerManager`. Lists follow the single-element unpacking rule; map keys are decoded with their declared type and normalized duplicate keys fail. `Optional<T>` is empty for a missing value or JSON null. Unknown type variables must be supplied by the caller.

```java
Type type = new TypeToken<Map<ResourceLocation, List<WeightedList<IBlockState>>>>() { }.getType();
Map<ResourceLocation, List<WeightedList<IBlockState>>> states = Deserializer.decode(type, json);
```

`DeserializerBuilder.require(Type, name)`, `optional(Type, name)`, `optionalDefault(Type, name, value)` and `Context.get(Type, name)` preserve the same generic types. An optional field is omitted only when absent; a value that is present but cannot be decoded fails.

`record(Class)` decodes constructor parameters annotated with `@Record`, keeping `getGenericParameterTypes()`. Nested lists, maps, arrays and optionals keep working.

### Registering composable types

Use `AbstractDeserializer` / `DeserializerBuilder`. The object's `type` dispatches inside the registry of the target Java type, and a new node decodes its children with the manager it receives.

```java
manager.named(Vec3d.class, new ResourceLocation("demo", "offset"))
        .require(Vec3d.class, "base")
        .require(Vec3d.class, "offset")
        .decode(context -> context.get(Vec3d.class, "base")
                .add(context.get(Vec3d.class, "offset")))
        .build();
```

```json
{"type":"demo:offset","base":[0,64,0],"offset":{"type":"demo:offset","base":[1,0,0],"offset":[0,0,2]}}
```

Custom generic types register through `manager.addGenericEntry(RawType.class, (registry, arguments, value) -> ...)`; `arguments` are the actual type arguments and child values call `registry.decode(arguments[i], child)`. The built-in `WeightedList<T>` uses this entry.

Raw JSON targets such as `JsonElement` and `JsonObject` keep a data field named `type` instead of treating it as a constructor. Enums without a dedicated default decoder accept case-insensitive constant names.

### Native composed values

| Target type | Registered composition and consumers |
| --- | --- |
| `IntProvider` | constant, uniform, biased, double-biased, clamped, truncated normal, trapezoid and weighted distributions; used by heights, placement counts and enchantment selection |
| `FloatProvider` | constant, uniform, truncated normal and trapezoid distributions; convertible to context numbers |
| `LevelBasedValue` | constant, linear, level squared, clamped, fraction, exponent, lookup and fallback; used by enchantment cost, effects and trial scaling |
| `DataRules.NumberValue` | arithmetic, trigonometry, rounding, clamping, conditions, weighted choice, dispatch tables, storage, inputs, level values and distribution sampling |
| `DataRules.Test` | logical composition, number comparison, time, weather, light, score, distance, item/entity/block matching, real damage sources and enchantment chances |
| `DataRules.Modifier` | sequence, conditions, weighted choice, count/durability, count limits, enchantment add/remove, enchantment bonus, name/lore and NBT copy/merge/append |
| `DataRules.Effect` | sequence, conditions, weighted choice, functions, damage, healing and the ignite, potion, impulse, particle, sound, block transform and entity spawn nodes registered by the gameplay module |
| `ITextComponent` | native text, translation and styles; `extra`, translation arguments and hover contents use the same manager |
| `FunctionDataPath.Compiled` | pre-parsed NBT read/write paths reused by inputs, storage and item NBT operations |

Integer and float distributions use `minecraft:` names but the target Java type decides the fields. Integer `uniform` uses `min_inclusive` / `max_inclusive`; float `uniform` uses `min_inclusive` / `max_exclusive`. Wrap one as a context number with `rml:sample_int` or `rml:sample_float`.

`rml:input` and `rml:storage` paths use compiled NBT paths; storage also requires a storage resource id. A missing number falls back only when `fallback` is provided; a present value of the wrong type fails.

The loot-facing predicate `rml:enchantment_level` matches the actual level on the context tool. These item modifiers use native tables directly and also accept the equivalent `rml:` names:

- `minecraft:furnace_smelt` returns the furnace result with the original stack count, or the input when no recipe exists.
- `minecraft:enchant_randomly` picks one applicable enchantment from the optional `enchantments` list, or from all applicable enchantments, and rolls a level inside its range.
- `minecraft:enchant_with_levels` reads `levels` and optional `treasure` through the native enchantment table.
- `minecraft:looting_enchant` adds drops from the real killer's looting level and `count`; `limit` caps the final count, and without a killer the stack is unchanged.

Additional composed conditions fill gaps in the 1.12 field surface:

- `rml:entity_properties`: `entity` selects `this`, `killer`, `killer_player`, `attacker` or `direct_attacker`; `predicate` accepts a bare entity matcher or a typed node; `flags` supports `on_fire`, `sneaking`, `sprinting` and `baby`.
- `rml:entity_scores`: `entity` plus a `scores` map; every objective must be present and inside its range.
- `rml:block_state_property`: matches a block state from `block` plus `properties`, with an optional `offset`.
- `rml:location_check`: checks the block, light range, biome, `fluid` (`water` or `lava`) and `position` `x`/`y`/`z` ranges at `offset`.
- `rml:enchantment_level` (number) returns the actual level on the context tool and composes with arithmetic, comparisons and other numeric nodes.
- `rml:enchantments`: an `enchantments` map requires every listed level to fall inside its range; missing enchantments count as `0`.
- `rml:entity_health`, `rml:entity_max_health`, `rml:entity_absorption`, `rml:dimension`, `rml:block_hardness`, `rml:block_light`, `rml:item_count`, `rml:item_damage`, `rml:item_durability` and `rml:item_max_damage`: read context entity health/max health/absorption, dimension, block hardness and light, tool count, metadata, remaining durability and maximum durability; `fallback` covers a missing world or entity.
- `rml:held_item`: checks the context entity's held stack with an item matcher, using `hand` (`mainhand` or `offhand`).

These item modifiers fill missing 1.12 container and name operations:

- `rml:copy_name`: source `this`, `killer`, `killer_player` or `block_entity`.
- `rml:fill_player_head`: writes `SkullOwner` on a player head from `this` or `killer`.
- `rml:set_contents`: writes an `items` array into the block entity `Items`, with an optional `block_entity` id.
- `rml:set_loot_table`: writes `name` and an optional `seed` into `BlockEntityTag`.
- `rml:set_attributes`: writes a `modifiers` array into `AttributeModifiers`; each entry has `attribute`, `name`, `amount`, `operation` (`addition`, `multiply_base`, `multiply_total`), optional `slot` and `uuid`; a missing UUID is derived from attribute, name and slot.
- `rml:set_data`: sets the item metadata (`data`) from a numeric provider; values outside `0..32767` fail.
- `rml:set_repair_cost`: writes `RepairCost` from a numeric provider, affecting anvil cost.
- `rml:set_unbreakable`: writes or clears `Unbreakable`; omitting `value` means `true`.

### Registration phases and native consumption

Native items, blocks and enchantments declare registration shells first. Effects, matchers, recipe results and presentation conditions that depend on those entries resolve after registration; worldgen initializes on the biome registration event, and clock/test definitions resolve during FML initialization. Declaring a shell must not treat still-unregistered items from the same pack as unknown ids.

Enchantment attribute effects are removed or recomputed when equipment or conditions change; projectile effects run through the native bow firing and ammunition paths and keep enchantment data from before a bow breaks. Other launchers can call `GameplayModule.spawnEnchantedProjectile` and `consumeEnchantedAmmunition`.

Structure pool joints, projections, density, material rules and placement modifiers, plus dialog text, inputs and actions, all use the same decoder registry. Runnable definitions live in `src/test/resources/worldgen/`, `src/test/resources/rml/gameplay/` and `dev/datapack-example/data/rml_presentation_repair/`. `/rml capabilities decoders` lists the names actually registered at runtime.
