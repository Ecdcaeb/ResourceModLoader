### 增强数据包（`rml:content`）

`rml:content` 是 RML 2.0 的编译内容入口。它读取 `compiler/` 生成的类型化定义，再由原生模块在启动期或运行期消费；普通资源包目录不会直接触发这些定义。

默认路径：`/content/`。

### 源码布局

编译器接收目录或 ZIP，入口是 `pack.mcmeta` 中的 `rml` 元数据：

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

`data/<namespace>/<type>/<path>.json` 是定义；`data/<namespace>/function/**/*.mcfunction` 是函数；`data/<namespace>/tags/<registry>/...` 是标签；`assets/**` 原样复制。`constants` 在编译期展开一轮，未绑定项保留为运行时参数。

### 编译与产物

```text
gradlew.bat -p compiler check executableJar
java -jar compiler/build/libs/rml-compiler.jar capabilities
java -jar compiler/build/libs/rml-compiler.jar check dev/datapack-example
java -jar compiler/build/libs/rml-compiler.jar compile dev/datapack-example build/example.zip
```

产物是可安装的 RML 包，包含：

- `rml.info`，模块列表只声明 `rml:content`。
- `assets/<pack>/content/<type>/<namespace>/<path>.json`，每个文件都是 `{"format":1,"type":...,"id":...,"value":...}` 信封。
- `META-INF/rml/source-map.json`，把产物路径映射回源码，供诊断使用。
- `pack.mcmeta` 与原始客户端资源。

同一构建内路径冲突、未知类型、无效结构 NBT 和无法解析的源码都会让编译失败；不会产生部分可用的 ZIP。

### 运行期

`rml:content` 读取信封并校验 `format`、`type`、`id` 和 `value`；契约见[内容目录](../../java/deserializer/index.md)。未注册的 `type` 会在准备阶段报错，不会静默跳过。

| 阶段 | 类型 |
| --- | --- |
| 可重载 | `function`、`function_tag`、`tag/<registry>`、`predicate`、`item_modifier`、`loot_table`、`structure`、`context_int_provider`、`context_float_provider` |
| 启动期注册 | `recipe`、`advancement`、`dimension`、`dimension_type`、`item`、`block`、`enchantment`、`damage_type`、`villager_trade`、`trade_set`、`trim_material`、`trim_pattern`、`dialog`、`worldgen/*` 等 |

启动期类型在游戏启动或世界加载时注册；修改、增加或删除它们需要重启。可重载类型由函数模块的候选代次提交：

```text
/rml functions status
/rml functions reload
/rml functions reload example_pack
/rml functions unload example_pack
```

`reload` 先完整解析候选，再一次性替换；失败保留旧代次。跨包引用必须在 `rml:functions` 模块的 `requires` 中声明，循环依赖和仍被依赖的卸载都会被拒绝。

### 工具入口

```text
/rml capabilities
/rml capabilities decoders
/rml tools inspect
/rml tools check [modid]
/rml tools apply <candidate> <revision>
/rml tools deactivate <session> <revision> <modid> [modid...]
```

`check` 准备候选但不激活，响应包含 `candidate`、`expected_revision` 和受影响单元；`apply` 使用这两个值提交。`requires_restart` 表示改动落在启动期定义上。`inspect` 返回包的 `startup` / `reloadable` 能力分类、活动世界测试和当前会话。完整命令语义见[函数文档](../FUNCTIONS/index.md#agent-运行工具)。

### 定义值

定义的 `value` 使用 RML 的统一解码注册表。基础类型、范围、权重表、方块状态、NBT、匹配器，以及数值、条件、修饰器、效果、世界生成、展示、时钟和测试的命名节点共享同一套嵌套规则。物品修饰器示例：

```json
{
  "function": "minecraft:set_count",
  "count": { "type": "minecraft:uniform", "min": 2, "max": 4 },
  "conditions": [
    { "condition": "rml:expression", "expression": { "type": "rml:enchantment_level", "enchantment": "minecraft:fortune", "level": { "min": 3 } } }
  ]
}
```

可用名称以 `/rml capabilities decoders` 的实际输出为准；字段与组合方式见[解析器](../../deserializer/index.md)和[原生组合值](../../java/deserializer/index.md#原生组合值)。

### 验证边界

编译通过只证明源码可解析、引用在编译期成立。原生注册、世界生成、客户端表现和存档行为仍需实际游戏验证；可重载内容用 `check` / `apply` 验证，启动期内容用重启后的 `inspect` 与游戏内命令验证。
