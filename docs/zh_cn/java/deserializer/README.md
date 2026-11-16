## Deserializer

`Deserializer` 把 JSON 反序列化成目标 Java 对象。

- `rml.loader.deserialize.Deserializer` 是 RML 环境下的反序列化器。基于 RML 开发时应使用它。
- `rml.deserializer.DeserializerBuilder` 用于构造 JsonObject 解析器。
- 解析一个对象，使用 `rml.loader.deserialize.Deserializer#decode`。
- `decode(Class<T>, JsonElement)` 解码具体类型；`decode(Type, JsonElement)` 保留嵌套泛型。`Type` 可以由 Gson `TypeToken` 提供。
- `rml.deserializer.Argument` 是为 `DeserializerBuilder` 准备的具名字段动作。

### 泛型与嵌套容器

`List<T>`、`Set<T>`、`Collection<T>`、`Iterable<T>`、`Map<K,V>`、`Optional<T>` 和泛型数组递归使用同一个 `DeserializerManager`。列表沿用数组的单元素解包规则；Map 的键也按声明的类型解码，转换后重复的键会报错。`Optional<T>` 对缺失或 JSON null 返回空值；未知类型变量必须由调用者提供具体类型。

```java
Type type = new TypeToken<Map<ResourceLocation, List<WeightedList<IBlockState>>>>() { }.getType();
Map<ResourceLocation, List<WeightedList<IBlockState>>> states = Deserializer.decode(type, json);
```

`DeserializerBuilder.require(Type, name)`、`optional(Type, name)`、`optionalDefault(Type, name, value)` 和 `Context.get(Type, name)` 保留相同的泛型。可选字段仅在缺失时省略或使用默认值；显式提供但无法解码的值会报错。

`record(Class)` 根据带 `@Record` 的公开构造器参数解码字段，保留 `getGenericParameterTypes()`。构造器中的 `List<T>`、Map、数组、Optional 可以继续嵌套。先前字段动作已生成的值优先使用；构建后的解析器保留动作快照。

### 注册可组合类型

仍使用 `AbstractDeserializer` / `DeserializerBuilder`。对象的 `type` 在目标 Java 类型自己的注册表中分派；新增节点内部使用传入的 manager 解码子节点，扩展类型即可参与既有组合。

```java
manager.named(Vec3d.class, new ResourceLocation("demo", "offset"))
        .require(Vec3d.class, "base")
        .require(Vec3d.class, "offset")
        .decode(context -> context.get(Vec3d.class, "base")
                .add(context.get(Vec3d.class, "offset")))
        .build();
```

```json
{"type":"demo:offset","base":[0,64,0],"offset":{"type":"demo:offset","base":[1,0,0],"offset":[0,0,2]}}
```

自定义泛型类型通过 `manager.addGenericEntry(RawType.class, (registry, arguments, value) -> ...)` 注册。`arguments` 是实际类型参数，子值调用 `registry.decode(arguments[i], child)`；内置 `WeightedList<T>` 使用这一入口。

`JsonElement`、`JsonObject`、`JsonArray` 等原始 JSON 目标保留数据中的 `type` 字段，不把它当作对象构造器。未注册专用默认解析器的枚举接受不区分大小写的枚举常量名。
