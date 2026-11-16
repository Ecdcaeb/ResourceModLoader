### 模块

### 查找

通过 `rml.loader.api.mods.module.ModuleType#valueOf` 获取模块类型。

通过 `ResourceModLoader.runtime().getModules().load(type, consumer, Caller.class)` 执行启动期消费者。加载器按包和文件的稳定顺序读取普通文件；进入消费者前设置 Forge 当前模组，退出时始终恢复。

`read(type, Caller.class)` 返回独立的只读文件快照列表，目录遍历和压缩包已关闭。也可以使用 `read(type, Caller.class, modid)` 只准备指定包。读取失败会拒绝准备，适用于托管运行时提供者；解析阶段不得注册回调或修改世界。

旧的静态 `loadModule` 已移除。文件上下文使用 `getUri()`、`getRelativePath()`、`openBufferedReader()` 和原样读取字节的 `getBytes()`。

### 创建

在类路径上放置 `rml.modules` 或 `META-INF/rml/modules.json`，即可注册额外的模块类型。

文件格式：[ModuleType](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ModuleType) 的数组。
