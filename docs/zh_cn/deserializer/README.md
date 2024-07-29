## Deserializer

`Deserializer` 是 rml 设计的，应用于 Json 反序列化到目标对象的工具。

### 数组解包

对于给定的JsonElement，`Deserializer`将进行如下操作：

- 如果给定类型和 JsonElement 均为数组，反序列化为数组。
- 如果给定类型是数组，但 JsonElement 不是，则反序列化为只有一个内容的数组。
- 如果给定类型不是数组，则反序列化为给定类型。

例如，在一个被指定为整形数组的字段，以下格式等价：
```json
{"demo":1}
```
```json
{"demo":[1]}
```

### 自由组合

`Deserializer`内建并注册了对基础类型的默认反序列化器，rml添加了许多客制化类型的反序列化器。
对于一个特定的类型，若 json 不为 JsonObject，则应用默认反序列化器，否则使用 `type` 字段中指定的反序列化器，如果字段不存在，则仍使用默认。

例如，对于一个表达整形的字段，可用默认的 `google:primitive` ，也可使用`rml:random_int`。一个数组中也可以混用，实现各组件的自由组合。
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

### 所有有文档的类型：
[Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

[RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier)

[ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation)

[IVillager](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/IVillager)

[ITradeList](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ITradeList)

[ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack)

[EnchantmentData](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/EnchantmentData)

[TagOre](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/TagOre)
