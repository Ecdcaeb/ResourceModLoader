### 模块

这一部分适用于 Resource Mod。

原版数据部分可以参考数据包教程：<https://ruhuasiyu.github.io/VanillaModTutorial/#%E8%B5%84%E6%BA%90%E5%8C%85/%E5%91%BD%E5%90%8D%E7%A9%BA%E9%97%B4>

你可以通过模块选择加载功能和路径。

模块在 `rml.info` 的 `modules` 中定义，是 JsonObject 数组，每个元素如下：

```json
{
  "name": "rml:config_override",
  "location": "mods/forge/config/override",
  "forceLoaded": false
}
```

| 字段 | 必填 | 含义 |
| --- | --- | --- |
| `name` | 是 | 模块类型 id。不带域名前缀时默认使用 `rml`。 |
| `location` | 否 | `/assets/<domain>/` 下的路径。不填则使用类型默认路径。 |
| `forceLoaded` | 否 | 为 `true` 时，该模块加载失败会崩溃。默认为 `false`。 |

如果没有 `modules` 字段，则启用全部默认模块。
