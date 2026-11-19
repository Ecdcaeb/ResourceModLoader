## Deserializer

`Deserializer` 把 JSON 反序列化成目标 Java 对象。

- `rml.loader.deserialize.Deserializer` 是 RML 环境下的反序列化器。基于 RML 开发时应使用它。
- `rml.deserializer.DeserializerBuilder` 用于构造 JsonObject 解析器。
- 解析一个对象，使用 `rml.loader.deserialize.Deserializer#decode`。
- `decode(Class<T>, JsonElement)` 解码具体类型；`decode(Type, JsonElement)` 保留嵌套泛型。`Type` 可以由 Gson `TypeToken` 提供。
- `rml.deserializer.Argument` 是为 `DeserializerBuilder` 准备的具名字段动作。

### 泛型与嵌套容器

`List<T>`、`Set<T>`、`Collection<T>`、`Iterable<T>`、`Map<K,V>`、`Optional<T>` 和泛型数组递归使用同一个 `DeserializerManager`。列表沿用数组的单元素解包规则；Map 的键也按声明的类型解码，转换后重复的键会报错。`Optional<T>` 对缺失或 JSON null 返回空值；未知类型变量必须由调用者提供具体类型。

```java
Type type = new TypeToken<Map<ResourceLocation, List<WeightedList<IBlockState>>>>() { }.getType();
Map<ResourceLocation, List<WeightedList<IBlockState>>> states = Deserializer.decode(type, json);
```

`DeserializerBuilder.require(Type, name)`、`optional(Type, name)`、`optionalDefault(Type, name, value)` 和 `Context.get(Type, name)` 保留相同的泛型。可选字段仅在缺失时省略或使用默认值；显式提供但无法解码的值会报错。

`record(Class)` 根据带 `@Record` 的公开构造器参数解码字段，保留 `getGenericParameterTypes()`。构造器中的 `List<T>`、Map、数组、Optional 可以继续嵌套。先前字段动作已生成的值优先使用；构建后的解析器保留动作快照。

### 注册可组合类型

仍使用 `AbstractDeserializer` / `DeserializerBuilder`。对象的 `type` 在目标 Java 类型自己的注册表中分派；新增节点内部使用传入的 manager 解码子节点，扩展类型即可参与既有组合。

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

自定义泛型类型通过 `manager.addGenericEntry(RawType.class, (registry, arguments, value) -> ...)` 注册。`arguments` 是实际类型参数，子值调用 `registry.decode(arguments[i], child)`；内置 `WeightedList<T>` 使用这一入口。

`JsonElement`、`JsonObject`、`JsonArray` 等原始 JSON 目标保留数据中的 `type` 字段，不把它当作对象构造器。未注册专用默认解析器的枚举接受不区分大小写的枚举常量名。

### 原生组合值

以下类型共用同一个 `DeserializerManager`；子节点可以是内置节点，也可以是其他模组注册的命名解析器。

| 目标类型 | 已接入的组合与消费者 |
| --- | --- |
| `IntProvider` | 常量、均匀、向下偏置、双重向下偏置、截断、截断正态、梯形、权重分布；用于高度、放置次数和附魔选择 |
| `FloatProvider` | 常量、均匀、截断正态、梯形分布；可转换成上下文数值 |
| `LevelBasedValue` | 常量、线性、等级平方、截断、分式、指数、查表及回退；用于附魔成本、效果和试炼规模 |
| `DataRules.NumberValue` | 算术、三角函数、舍入、范围截断、条件、权重选择、分支表、storage、输入绑定、等级值和分布采样 |
| `DataRules.Test` | 逻辑组合、数值比较、时间、天气、光照、计分、距离、物品/实体/方块匹配、真实伤害来源和附魔概率 |
| `DataRules.Modifier` | 序列、条件、权重选择、数量/耐久、数量边界、附魔增删、附魔掉落加成、名称/描述和 NBT 复制/合并/追加 |
| `DataRules.Effect` | 序列、条件、权重选择、函数、伤害、治疗，以及玩法模块注册的点燃、药水、冲量、粒子、声音、方块变换和实体生成 |
| `ITextComponent` | 原生文本、翻译及样式；`extra`、翻译参数和悬浮文本中的子组件也经过同一 manager |
| `FunctionDataPath.Compiled` | 预解析的 NBT 读写路径；上下文输入、storage 与物品 NBT 操作复用它 |

整数分布与浮点分布使用 `minecraft:` 名称，但字段由目标 Java 类型决定。整数 `uniform` 使用 `min_inclusive` / `max_inclusive`；浮点 `uniform` 使用 `min_inclusive` / `max_exclusive`。上下文数值通过 `rml:sample_int` 或 `rml:sample_float` 包装它们：

```json
{
  "type": "rml:add",
  "inputs": [
    {"type": "rml:input", "path": "amount"},
    {"type": "rml:sample_int", "provider": {
      "type": "minecraft:weighted_list",
      "distribution": [
        {"weight": 3, "data": 1},
        {"weight": 1, "data": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4}}
      ]
    }}
  ]
}
```

`rml:input` 与 `rml:storage` 的 `path` 使用编译后的 NBT 路径；后者还要求 `storage` 资源 ID。缺失数值只有在明确提供 `fallback` 时才回退，存在但类型错误的值会报错。

效果树同样允许自定义子节点。例如下面只在真实火焰伤害上下文中执行两个效果：

```json
{
  "type": "rml:conditional",
  "condition": {"type": "rml:damage_source", "fire": true},
  "on_true": {"type": "rml:sequence", "effects": [
    {"type": "rml:heal", "amount": {"type": "rml:context_level"}},
    {"type": "rml:run_function", "function": "demo:after_fire"}
  ]}
}
```

`DataContext` 保留实体、真实位置、随机流、等级、工具、伤害来源与输入绑定；具名规则经过原生战利品接口时也保留这些值。缺失位置不会替换成 `(0,0,0)`。`withInputs` 隔离传入 NBT；`getInputs` 返回副本，`getInput(compiledPath)` 返回只读视图，调用者修改前必须复制。

`rml:damage_source.absolute` 对应 1.12 的原生标志；虚空伤害需要另按 `names:["outOfWorld"]` 识别。物品修饰器 `rml:set_enchantments` 接受 `enchantments` 映射，等级为零会移除指定附魔；`add:true` 在现有等级上相加。`rml:copy_nbt` 的来源为 `entity`、`block_entity`、`tool`、`inputs` 或 `storage`，`target` 相对物品的 tag，`operation` 为 `replace`、`merge` 或 `append`。

`rml:enchantment_level` 按工具上的实际附魔等级匹配 `level` 区间。以下物品修饰器直接复用原生表，并同时接受 `rml:` 前缀的等价名称：

- `minecraft:furnace_smelt` 读取熔炉产物并保留原堆叠数量；没有配方时返回原物品。
- `minecraft:enchant_randomly` 从可选 `enchantments` 列表或全部可应用附魔中随机选择一个，并在其等级区间内附魔。
- `minecraft:enchant_with_levels` 用 `levels` 与可选 `treasure` 调用原生附魔表。
- `minecraft:looting_enchant` 按真实击杀者的抢夺等级和 `count` 增加掉落数量，`limit` 可限制最终数量；缺少击杀者时保持原物品。

补齐 1.12 缺失字段风格的组合条件：

- `rml:entity_properties`：`entity` 选择 `this`、`killer`、`killer_player`、`attacker` 或 `direct_attacker`；`predicate` 接受裸实体匹配对象或带 `type` 的节点；`flags` 支持 `on_fire`、`sneaking`、`sprinting`、`baby`。
- `rml:entity_scores`：`entity` 加 `scores` 映射，多个计分目标必须同时落在区间内。
- `rml:block_state_property`：`block` 与 `properties` 匹配方块状态，可带 `offset`。
- `rml:location_check`：按 `offset` 检查方块、光照区间与生物群系。
- `rml:enchantment_level`（数值）返回工具上的实际附魔等级，可参与算术、比较和其他数值节点。
- `rml:enchantments`：`enchantments` 映射要求工具上每个附魔等级都落在对应区间，未附魔按 `0` 处理。
- `rml:entity_health`、`rml:block_light`、`rml:item_count`、`rml:item_damage`：读取上下文实体生命、方块光照、工具数量与工具 meta；缺失世界或实体时可用 `fallback`。

以下物品修饰器补齐 1.12 缺失的容器与名称操作：

- `rml:copy_name`：来源为 `this`、`killer`、`killer_player` 或 `block_entity`。
- `rml:fill_player_head`：从 `this` 或 `killer` 写入玩家头颅的 `SkullOwner`。
- `rml:set_contents`：`items` 数组写入方块实体的 `Items`，可选 `block_entity` 写入方块实体 id。
- `rml:set_loot_table`：`name` 与可选 `seed` 写入 `BlockEntityTag`。
- `rml:set_attributes`：`modifiers` 数组写入 `AttributeModifiers`；每项含 `attribute`、`name`、`amount`、`operation`（`addition`、`multiply_base`、`multiply_total`）、可选 `slot` 与 `uuid`，缺省 UUID 由属性、名称与槽位确定。
- `rml:set_data`：按数值提供者设置物品 meta（`data`），超出 `0..32767` 会报错。

### 注册阶段与原生消费

原生物品、方块和附魔先声明注册壳。依赖这些注册项的效果、匹配器、配方结果和展示条件在注册完成后解析；世界生成在生物群系注册事件中初始化，时间与测试定义在 FML 初始化阶段解析。不能在声明壳时提前把同包尚未注册的物品当成未知 ID。

附魔的属性效果在装备/条件变化时移除或重算；投射物效果通过原生弓的发射和耗弹入口消费，并保留弓断裂前的附魔数据。其他发射器可调用 `GameplayModule.spawnEnchantedProjectile` 和 `consumeEnchantedAmmunition` 接入相同规则。

结构池的连接点、投影、密度、材质、放置修饰器，以及对话的文本、输入和动作也使用统一解码注册表。可运行的定义见 `src/test/resources/worldgen/`、`src/test/resources/rml/gameplay/` 和 `dev/datapack-example/data/rml_presentation_repair/`。运行时可用 `/rml capabilities decoders` 查询实际已注册名称。

