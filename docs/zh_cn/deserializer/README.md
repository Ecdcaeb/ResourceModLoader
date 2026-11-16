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

### 扩充的原生类型

以下类型通过同一 `DeserializerManager` 注册，可用于字段、数组、泛型容器以及其他模组新增的构造器。

| 目标类型 | 默认命名解析器 / 输入 |
| --- | --- |
| `IntRange` | `rml:int_range`；整数或 `{ "min": 1, "max": 8 }`，边界包含端点 |
| `DoubleRange` | `rml:double_range`；有限数值或上下界对象，省略一端表示该端无限制 |
| `Vec3d` / `Vec2f` | `rml:vec3` / `rml:vec2`；坐标数组或 `x/y/z` 字段 |
| `BlockPos` | `rml:block_pos`；整数坐标数组或 `x/y/z` 字段 |
| `AxisAlignedBB` | `rml:box`；`min`、`max` 分别嵌套 Vec3d |
| `UUID` | `rml:uuid`；UUID 字符串，或带 `value` 的显式构造器 |
| `IBlockState` | `rml:block_state`；方块 ID，或原版 `Name` / `Properties` 对象，未知属性和值会报错 |
| `WeightedList<T>` | `entries` 数组，每项包含 `data: T` 和正整数 `weight`；元素保留泛型类型 |
| `NBTBase` | `rml:nbt`；支持嵌套复合值、同类型列表及显式 byte/short/int/long/float/double/string/compound/list/byte_array/int_array/long_array 构造器 |
| `ItemMatcher` | `rml:item_match`；物品 ID、`items` / `count` / `damage` / `nbt` |
| `BlockMatcher` | `rml:block_match`；方块 ID、`blocks` / `properties` / 方块实体 `nbt` |
| `EntityMatcher` | `rml:entity_match`；实体 ID、`entity_types` / `health` / `alive` / `on_fire` / `nbt` |

范围、权重类型位于 `rml.deserializer.value`；匹配器接口位于 `NativeTypeDecoders`。三种匹配器均可递归使用 `rml:all_of`、`rml:any_of` 和 `rml:not`；ID 列表中的 `#namespace:tag` 查询相应注册表的活动标签。

例如，解码为 `WeightedList<IBlockState>`：

```json
{"entries":[{"data":{"Name":"minecraft:stone","Properties":{"variant":"granite"}},"weight":3},{"data":"minecraft:gold_block","weight":1}]}
```

解码为 `ItemMatcher`，组合物品组、数量范围和取反条件：

```json
{"type":"rml:all_of","terms":[{"items":["minecraft:diamond_sword","minecraft:iron_sword"],"count":{"min":1}},{"type":"rml:not","term":{"damage":{"min":100}}}]}
```

显式 NBT 构造器可以保持数值类型，并让字面复合值保留名为 `type` 的数据字段：

```json
{"type":"rml:compound","value":{"type":"ritual","charges":{"type":"rml:short","value":4},"enabled":true}}
```

数值表达式、谓词、物品修饰器、效果以及世界生成、玩法、展示、时钟和测试的类型由相应原生模块的 `registerDecoders(manager)` 注册；嵌套子值继续使用该 manager。Java 泛型及扩展注册方式见[开发接口](../java/deserializer/README.md)。

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
