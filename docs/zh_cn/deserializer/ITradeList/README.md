## [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/)
表示为村民交易。

### `minecraft:emerald_for_items`
用物品换取绿宝石。

#### 字段：

`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack) 玩家提供的物品

`price` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) 村民提供绿宝石的数量

### `minecraft:list_item_for_emeralds`
物品加绿宝石换取物品。

#### 字段：

`from`

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) 绿宝石的数量

__`count` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) 物品数量，运行时替换物品数量

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack) 物品

`to`

__`count` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) 物品数量，运行时替换物品数量

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack) 物品

### `minecraft:list_item_for_emeralds`
绿宝石换取物品。

#### 字段：

`from`

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) 价格

`to`

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack) 物品


### `cvh:slots`
指定格子物品。

slot1 + slot2 -> slot3

#### 字段：

`slot1`

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) 物品数量

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack) 物品

`slot2`

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) 物品数量

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack) 物品

`slot3`

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) 物品数量

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack) 物品

