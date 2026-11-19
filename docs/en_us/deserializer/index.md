## Deserializer

`Deserializer` turns JSON into typed Java objects.

### Array unpacking

For a given `JsonElement`:

- If the target type and the JSON are both arrays, decode an array.
- If the target type is an array but the JSON is not, decode a one-element array.
- If the target type is not an array, decode that type.

These two integer-array fields are equivalent:

```json
{"demo": 1}
```

```json
{"demo": [1]}
```

### Named types

Built-in deserializers cover Java primitives. RML registers more for Minecraft types.

If the JSON is not a `JsonObject`, the default deserializer for that class is used. If it is a `JsonObject` with `type`, that named deserializer is used. If `type` is missing, the default is used.

An integer field can use `google:primitive` or `rml:random_int`. An array can mix both:

```json
{
  "demo": [
    {
      "type": "rml:random_int",
      "random": {
        "type": "minecraft:price",
        "min": {
          "type": "rml:random_int",
          "random": {
            "type": "cvh:poisson_distribution",
            "lambda": 5,
            "min": 7,
            "max": 21
          }
        },
        "max": 22
      }
    },
    9
  ]
}
```

### Extended native types

These types are registered in the same `DeserializerManager`, so they work in fields, arrays, generic containers and constructors added by other mods.

| Target type | Default named decoder / input |
| --- | --- |
| `IntRange` | `rml:int_range`; an integer or `{ "min": 1, "max": 8 }`, endpoints inclusive |
| `DoubleRange` | `rml:double_range`; a finite number or a bounds object, an omitted side is unbounded |
| `Vec3d` / `Vec2f` | `rml:vec3` / `rml:vec2`; a coordinate array or `x/y/z` fields |
| `Vec3i` | `rml:vec3i`; an integer coordinate array or `x/y/z` fields, for sizes and offsets |
| `EnumFacing` / `EnumFacing.Axis` | `rml:direction` / `rml:axis`; a direction name or single-axis vector, and an axis name |
| `ItemStack` | `rml:item_stack`; an item id, `[item, count]`, or an `item` / `count` / `data` / `nbt` object |
| `BlockPos` | `rml:block_pos`; an integer coordinate array or `x/y/z` fields |
| `AxisAlignedBB` | `rml:box`; nested `Vec3d` values in `min` and `max` |
| `UUID` | `rml:uuid`; a UUID string, or an explicit constructor with `value` |
| `IBlockState` | `rml:block_state`; a block id, or a vanilla `Name` / `Properties` object; unknown properties and values fail |
| `WeightedList<T>` | an `entries` array of `data: T` plus a positive `weight`; element types are preserved |
| `NBTBase` | `rml:nbt`; nested compounds, homogeneous lists and explicit byte/short/int/long/float/double/string/compound/list/byte_array/int_array/long_array constructors |
| `ItemMatcher` | `rml:item_match`; item ids plus `items` / `count` / `damage` / `nbt` / `enchantments` |
| `BlockMatcher` | `rml:block_match`; block ids plus `blocks` / `properties` / block entity `nbt` |
| `EntityMatcher` | `rml:entity_match`; entity ids plus `entity_types` / `health` / `alive` / `on_fire` / `nbt` / `equipment` (`mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`) |

Ranges and weighted values live in `rml.deserializer.value`; matcher interfaces live in `NativeTypeDecoders`. All three matchers compose recursively through `rml:all_of`, `rml:any_of` and `rml:not`; `#namespace:tag` entries in id lists query the active tags of the matching registry.

Decoding a `WeightedList<IBlockState>`:

```json
{"entries":[{"data":{"Name":"minecraft:stone","Properties":{"variant":"granite"}},"weight":3},{"data":"minecraft:gold_block","weight":1}]}
```

Decoding an `ItemMatcher` that composes an item group, a count range and a negation:

```json
{"type":"rml:all_of","terms":[{"items":["minecraft:diamond_sword","minecraft:iron_sword"],"count":{"min":1}},{"type":"rml:not","term":{"damage":{"min":100}}}]}
```

Numeric expressions, predicates, item modifiers, effects, worldgen, presentation, clock and test types are registered by each native module's `registerDecoders(manager)`; nested values keep using that manager. See [Java deserializer APIs](../java/deserializer/index.md) for generics and extension registration.

### Documented types

- [Integer](Integer)
- [RandomIntSupplier](RandomIntSupplier)
- [ResourceLocation](ResourceLocation)
- [IVillager](IVillager)
- [ITradeList](ITradeList)
- [ItemStack](ItemStack)
- [EnchantmentData](EnchantmentData)
- [TagOre](TagOre)
- [ModuleType](ModuleType)
