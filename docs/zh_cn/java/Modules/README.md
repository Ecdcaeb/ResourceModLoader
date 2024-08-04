### Modules、

### 获取

通过 `rml.loader.api.mods.module.ModuleType#valueOf` 获取模块类型。

`rml.loader.ResourceModLoader#loadModuleFindAssets` 获取并遍历所有启用了该模块的模组的资源并发布事件。

### 创建

在根目录放置 `rml.modules` 实现客制化模块类型。

文件格式：

[ModuleType](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ModuleType) 的数组。