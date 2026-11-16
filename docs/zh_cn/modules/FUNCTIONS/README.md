---
sort: 3
---

### 函数（`rml:functions`）

默认路径：`/functions/`

把 `.mcfunction` 文件放进该目录，和数据包一样。

参见：<https://minecraft.fandom.com/wiki/Function_(Java_Edition)>

国内教程：<https://ruhuasiyu.github.io/VanillaModTutorial/#%E5%87%BD%E6%95%B0>

同一目录下的 `.executor` 文件用于定义函数执行器。

### 托管生命周期

函数和执行器先作为候选完整解析、检查引用和依赖，通过后才替换活动版本。执行器构造不注册监听器；运行时持有监听器及函数表条目，在替换、卸载和服务器停止时释放。

操作员权限 2 可使用：

```text
/rml functions status
/rml functions reload
/rml functions reload rml_example
/rml functions unload rml_example
```

`reload` 重新读取已登记包的函数来源，支持新增文件、修改和删除。指定 modid 时只准备该包；不指定时准备全部函数模块。所有候选源文件必须成功解析；未知命令、缺失函数或依赖错误会拒绝候选并保留旧代次。该命令不动态发现新 `rml.info`，也不重新执行 Forge 启动阶段。

`status` 显示活动包、代次、单元和资源数量，以及最近一次拒绝原因。`unload` 停用该包的可重载运行内容，不删除存档数据、不撤销已经执行的命令，也不卸载配置和原生启动期模块。

函数表使用按代次持有的注册句柄。旧代次释放不会删除新函数，卸载新代次不会恢复已经释放的旧版本。原版函数重载后会重新绑定仍然活动的条目，再准备更新。

跨包依赖在对应 `rml:functions` 模块中显式声明：

```json
{
  "name": "rml:functions",
  "requires": ["shared_rules"]
}
```

依赖包必须已活动或包含在同一候选中。若修改被其他活动包依赖的包，需要同时准备这些依赖者，可使用不带 modid 的 `reload`；直接卸载仍被依赖的包会失败。`.mcfunction` 的任意命令不会自动推导包依赖，作者必须声明跨包耦合。

tick 执行器的 `ticks` 为正数时按世界总 tick 的整倍数执行，为 `0` 时停用，为负数时每 tick 执行。世界加载规则只在实际世界加载事件发生时触发，热替换不会额外模拟一次世界加载。

### 增强数据包编译的函数

独立 `compiler/` 工程接受 `data/<namespace>/function/*.mcfunction`，将函数、标签和规则编译到 `rml:content`。这些函数使用 `CompiledFunctionEngine`：在原生函数入口保留执行主体、世界、坐标和调用顺序，并提供增强 `execute`、持久 NBT `data storage`、函数参数和 `return`。

源码的 `schedule function <id> <time> [append|replace]` 编译为 `/rml schedule function ...`；默认替换相同函数的计划，`schedule clear <id>` 取消计划。运行时调度不保留调用者实体，按服务器函数语义执行，并在对应代次卸载时取消任务。

`/rml functions reload [modid]` 同时准备该内容包的可重载函数、标签、数值提供者、战利品、谓词、修饰器和结构模板；全部准备成功后提交。`unload` 释放这些运行资源。原生注册、维度、配方、物品、方块和其他启动期提供者仍需重启，命令不把它们作为可热插拔内容。

运行时参数可以从 storage 传入，例如 `function demo:message with storage demo:arguments values`，目标函数使用 `$say $(message)`。`pack.mcmeta` 中声明的常量在编译时展开。公开命名解码器可以扩展数值、条件和效果，复用字段与泛型容器的组合规则。
