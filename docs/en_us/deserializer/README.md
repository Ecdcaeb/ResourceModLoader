## Deserializer

`Deserializer` is a tool designed by rml, which is used to deserialize Json to the target object.

### Array unpacking

For a given JsonElement, `Deserializer` will do the following:

- If both the given type and JsonElement are arrays, deserialize to an array.

- If the given type is an array, but the JsonElement is not, deserialize to an array with only one content.

- If the given type is not an array, deserialize to the given type.

For example, in a field specified as an integer array, the following formats are equivalent:

```json
{"demo":1}
```
```json
{"demo":[1]}
```

### Free combination

`Deserializer` has built-in and registered default deserializers for basic types, and rml adds many custom deserializers for customized types.

For a specific type, if json is not a JsonObject, the default deserializer is applied, otherwise the deserializer specified in the `type` field is used. If the field does not exist, the default is still used.

For example, for a field that expresses an integer, the default `google:primitive` can be used, or `rml:random_int` can be used. They can also be mixed in an array to achieve free combination of components.
```json
{
  "demo":[
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

### All documented Types：
[Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer)

[RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier)

[ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation)

[IVillager](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/IVillager)

[ITradeList](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ITradeList)

[ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack)

[EnchantmentData](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/EnchantmentData)

[TagOre](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/TagOre)

[ModuleType](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ModuleType)