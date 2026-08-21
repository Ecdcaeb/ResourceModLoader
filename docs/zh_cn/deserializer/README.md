## Deserializer

`Deserializer` 是 RML 设计的，把 JSON 反序列化成目标对象的工具。

### 数组解包

对于给定的 JsonElement：

- 如果目标类型和 JSON 都是数组，反序列化为数组。
- 如果目标类型是数组，但 JSON 不是，则反序列化为只有一个元素的数组。
- 如果目标类型不是数组，则按该类型反序列化。

在一个整型数组字段上，以下两种写法等价：

```json
{"demo": 1}
```

```json
{"demo": [1]}
```

### 自由组合

`Deserializer` 内建并注册了基础类型的默认反序列化器，RML 还为许多 Minecraft 类型注册了自定义反序列化器。

如果 JSON 不是 JsonObject，使用该类的默认反序列化器。如果是带 `type` 字段的 JsonObject，使用指定的命名反序列化器。没有 `type` 时仍使用默认。

整型字段可以用默认的 `google:primitive`，也可以用 `rml:random_int`。数组里还可以混用：

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

### 有文档的类型

- [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)
- [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier)
- [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation)
- [IVillager](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/IVillager)
- [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ITradeList)
- [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack)
- [EnchantmentData](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/EnchantmentData)
- [TagOre](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/TagOre)
- [ModuleType](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ModuleType)
