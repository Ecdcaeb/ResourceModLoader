### 注册名重定义（`rml:registry_remap`）

默认路径：`/registry/remap`

重映射缺失的注册名。

| 类型 | 键名 | 描述 |
| --- | --- | --- |
| String | `registry` | 注册表的注册名 |
| JsonObject | `mapping` | 旧名 → 新名 |

例如，不灭图腾在 1.11.1 到 1.11.2 改过注册名（Forge 本身已经包含这部分）：

```json
{
  "registry": "minecraft:items",
  "mapping": {
    "minecraft:totem": "minecraft:totem_of_undying"
  }
}
```
