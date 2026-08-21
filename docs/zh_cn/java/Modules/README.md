### 模块

### 查找

通过 `rml.loader.api.mods.module.ModuleType#valueOf` 获取模块类型。

`rml.loader.ResourceModLoader#loadModule` 会遍历所有启用了该模块的容器，设置当前模组容器，并对每个文件运行你的消费者。

### 创建

在类路径上放置 `rml.modules` 或 `META-INF/rml/modules.json`，即可注册额外的模块类型。

文件格式：[ModuleType](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ModuleType) 的数组。
