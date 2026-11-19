### `rml:mod_groovy_script`（GroovyScript）

默认路径：`/groovy_script/run_config.json`

需要 GroovyScript 1.4 或更新版本。

与 GroovyScript 的 `runConfig.json` 不同，这里只需要 `classes` 字段。它是一个 JsonObject：键是加载器名，值是 String 数组，每个 String 是脚本在包内的路径。

加载器与 GroovyScript 1.4 一致：`preInit`、`init`、`postInit`，以及适用于所有阶段的 `all`。

例如：

```json
{
  "classes": {
    "postInit": [
      "groovy/example/main.groovy",
      "groovy/example/main2.groovy"
    ]
  }
}
```
