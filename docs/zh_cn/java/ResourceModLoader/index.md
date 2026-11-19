### ResourceModLoader

Java 模组不需要 `rml.info`。通过实例服务登记资源内容：

```java
ResourceModLoader.runtime().getPacks().register(new ContainerHolder(modContainer));
```

模组容器尚未建立时，可以按 modid 延迟登记；第二个参数为 `null` 时，在容器可用时选择全部默认模块：

```java
ResourceModLoader.runtime().getPacks().request("example", null);
```

`getPacks().all()` 返回按 modid 排序的只读快照，重复登记同一 modid 会失败。登记不会执行模块，也不会启动事件监听器。

本次是 Java API 的破坏性切换：移除 `enableRML`、`loadModule`、`loadContainerHolders`、公开容器集合及旧文件扫描工具。模块消费者改用 `runtime().getModules().load(type, consumer, Caller.class)`；需要完整校验的提供者使用 `read` 取得独立文件快照，再交给运行内核激活。

`ContainerHolder` 及模块声明只读；使用 getter 访问。文件上下文使用 `getUri()` 标识原来源，`getBytes()` 原样返回字节，不再经过字符解码和重新编码。

`runtime/` 是无外部依赖的 Java 8 子项目，提供 `ContentRuntime`、`ContentUnit`、`PackDefinition`、`ResourceScope` 和 `OwnedRegistry`。它不依赖 Forge、Minecraft、Gson 或日志框架；Forge 适配在根项目中。

`ContentRuntime` 校验依赖与确定顺序，准备单次使用的候选，在调用方提供的线程检查下切换代次。激活时登记的回调必须检查 `ResourceScope.isActive()`，资源必须通过 `scope.own(...)` 登记。准备或激活失败保留旧版本；提交后旧资源清理失败会保留新版本，并阻止后续激活，错误代码为 `cleanup_failed`。

当前接入托管生命周期的是函数模块。资源包的 Forge 容器仍作为启动期资源适配存在；配置、原生注册和外部脚本管理器不具备通用热卸载保证。
