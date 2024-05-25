### 注册名重定义（REGISTRY_REMAP）

默认在 `/registry/remap`

重定义一个注册名

```text
类型           |  键名    | 描述

String        | registry | 注册表的注册名
JsonObject    | mapping  | 表
     \
      --String| *        | 键名为旧名，值为新名。
```

例如，不斯图腾在1.11.1到1.11.2中更改了注册名，这里对旧的注册名进行了重定义：（Forge本身已经包含了这部分的功能）
```json
{
  "registry": "minecraft:items",
  "mapping": {
    "minecraft:totem": "minecraft:totem_of_undying"
  }
}
```
