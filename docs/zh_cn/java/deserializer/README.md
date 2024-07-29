## Deserializer

`Deserializer` 是 rml 设计的，应用于 Json 反序列化到目标对象的工具。

`rml.loader.deserialize.Deserializer` 是应用于 RML 环境下的反序列化器。若基于RML进行开发，应当使用它。

`rml.deserializer.DeserializerBuilder` 是应用于 JsonObject 解析器构造器。

解析一个对象，可以使用 `rml.loader.deserialize.Deserializer#decode`

Deserializer 不支持泛型。

`rml.deserializer.Argument` 是为 `DeserializerBuilder` 打造的，极具特色的参数类型。