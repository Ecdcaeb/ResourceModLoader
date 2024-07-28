## Deserializer

`Deserializer` 是 rml 设计的，应用于 Json 反序列化到目标对象的工具。

对于给定的JsonElement，`Deserializer`将进行如下操作：

- 如果给定类型和 JsonElement 均为数组，反序列化为数组。
- 如果给定类型是数组，但 JsonElement 不是，则反序列化为只有一个内容的数组。
- 如果给定类型不是数组，则反序列化为给定类型。

例如，在一个被指定为整形数组的字段，以下格式等价：
```json
"demo":1    ==     "demo":{1}
```

`Deserializer`内建并注册了对基础类型的默认反序列化器，rml添加了许多客制化类型的反序列化器。
对于一个特定的类型，若 json 不为 JsonObject，则应用默认反序列化器，否则使用 `type` 字段中指定的反序列化器，如果字段不存在，则仍使用默认。

因此，实际上，`rml.info`还可以如此填写：

```json
[
{
    "pack_version":3
    "modid": "rml_example",
    "name": "RML Example Mod",
    "description": "The Example Mod for RML!",
    "version": "dev",
    "mcversion": "1.12.2",
    "authorList": [
      "Hileb"
    ],
    "credits": "RML -provide this example",
    "useDependencyInformation":true,
    "dependencies": [
      "required:crafttweaker"
    ],
    "modules":
    {
        "type": "rml:default",
        "name":"rml:SPLASH_TEXT",
        "location":"mods/minecraft/text/splash_text.txt",
        "forceLoaded":true
    }
}
]
```