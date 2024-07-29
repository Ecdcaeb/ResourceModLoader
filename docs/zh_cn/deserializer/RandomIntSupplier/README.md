## [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/)

这是一个产生随机数的玩意。

### `rml:default_constant`
默认解析器，直接解释字段为[Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)。
例如：
`1`。
此时该 `RandomIntSupplier` 产生的随机数恒为1。

### `minecraft:price`
#### 字段：

`min`[Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)


`max`[Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

在 `[min, max]` 中随机取值，符合原版村民交易价格逻辑。要求：min 不得大于 max。


### `cvh:constant`
#### 字段：

`constant`[Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)


### `cvh:poisson_distribution`

#### 字段：

`min`[Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

`max`[Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

`lambda`[Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer)

柏松分布，然后在 `[min, max]` 中重新分配。