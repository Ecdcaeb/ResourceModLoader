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
| `forceLoaded` | 否 | 启动期模块为 `true` 时加载失败会崩溃，默认为 `false`。托管函数模块始终校验整个候选，不以此字段允许部分激活。 |
| `requires` | 否 | 内容依赖包的 modid 数组，默认为空。当前函数运行时据此校验依赖、拒绝循环及阻止卸载仍被依赖的包。 |

如果没有 `modules` 字段，则启用全部默认模块。

### 模块列表

| 模块 | 用途 |
| --- | --- |
| [`rml:content`](CONTENT/index.md) | 编译器生成的增强数据包定义 |
| [`rml:functions`](FUNCTIONS/index.md) | 函数、执行器、候选代次与工具入口 |
| [`rml:loot_tables`](LOOT_TABLES/index.md) | 原生战利品表 |
| [`rml:ore_dic`](ORE_DIC/index.md) | 矿辞条目 |
| [`rml:custom_villagers`](village/index.md) | 村民职业与交易 |
| [`rml:registry_remap`](registry/REGISTRY_REMAP/index.md) | 缺失注册名重映射 |
| [`rml:splash_text`](text/SPLASH_TEXT/index.md) | 主界面闪烁标语 |
| [`rml:config_define`](config/CONFIG_DEFINE/index.md)、[`rml:config_override`](config/CONFIG_OVERRIDE/index.md)、[`rml:config_redefault`](config/CONFIG_REDEFAULT/index.md) | 配置定义、覆盖与默认值 |
| [`rml:mod_kubejs`](MOD_KUBEJS/index.md)、[`rml:mod_crt`](MOD_CRT/index.md)、[`rml:mod_groovy_script`](MOD_GROOVYSCRIPT/index.md) | 外部脚本集成 |
