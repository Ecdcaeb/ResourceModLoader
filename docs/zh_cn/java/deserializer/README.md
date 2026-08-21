## Deserializer

`Deserializer` 把 JSON 反序列化成目标 Java 对象。

- `rml.loader.deserialize.Deserializer` 是 RML 环境下的反序列化器。基于 RML 开发时应使用它。
- `rml.deserializer.DeserializerBuilder` 用于构造 JsonObject 解析器。
- 解析一个对象，使用 `rml.loader.deserialize.Deserializer#decode`。
- Deserializer 不支持泛型。
- `rml.deserializer.Argument` 是为 `DeserializerBuilder` 准备的具名字段动作。
