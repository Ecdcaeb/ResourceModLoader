### rml:MOD_GROOVYSCRIPT (GroovyScript)

默认在：
`/groovy_script/run_config.json`

与`runConfig.json`不同，`run_config.json`只要求填写`classes`字段，该字段为一个JsonObject，键名为加载器，键值为String数组，每个String表示该脚本文件在包内的绝对路径。

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

