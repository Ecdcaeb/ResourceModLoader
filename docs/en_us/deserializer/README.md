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

### Documented types

- [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer)
- [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier)
- [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation)
- [IVillager](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/IVillager)
- [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ITradeList)
- [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack)
- [EnchantmentData](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/EnchantmentData)
- [TagOre](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/TagOre)
- [ModuleType](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ModuleType)
