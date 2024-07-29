## [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/)
一堆物品。例如，mc中，一个物品格子内的物品就是一堆物品。

### `minecraft:item`
默认解析器。
#### 字段：

`item` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) 物品注册名

`data` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer) meta值，如果物品有变种，则该字段为必要，默认为0

`count` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer) 数量，默认为1

`nbt` NBTTagCompound（用字符串形式表示的nbt） 物品的nbt。


### `rml:enchantmented_item`
由于mc1.12.2附魔在nbt中使用数字id，因此此处使用单独的方法额外添加附魔。
表达一个附魔的物品。

#### 字段：

`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack) 前置物品

`enchantment` [EnchantmentData](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/EnchantmentData)的数组 附魔




