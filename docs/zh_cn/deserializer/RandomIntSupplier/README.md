## [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/)

随机整数来源。

### `rml:default_constant`

默认反序列化器。字段就是 [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)。

例如：`1`。此时该 `RandomIntSupplier` 始终返回 `1`。

### `minecraft:price`

#### 字段

`min` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

`max` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

在 `[min, max]` 中取值，符合原版村民交易价格逻辑。要求：`min` 不得大于 `max`。

### `cvh:constant`

#### 字段

`constant` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

### `cvh:poisson_distribution`

#### 字段

`min` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

`max` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

`lambda` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

泊松分布采样后，再映射到 `[min, max]`。
