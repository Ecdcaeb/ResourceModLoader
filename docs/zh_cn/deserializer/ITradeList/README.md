## [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/)

村民交易。

### `minecraft:emerald_for_items`

玩家用物品换村民的绿宝石。

#### 字段

`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack) — 玩家提供的物品。

`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) — 村民给出的绿宝石数量。

### `minecraft:list_item_for_emeralds`

玩家物品加绿宝石，换村民物品。

#### 字段

`from`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack)
- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) — 绿宝石数量
- `count` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) — 运行时买入物品数量

`to`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack)
- `count` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) — 运行时卖出物品数量

### `minecraft:item_and_emerald_to_item`

绿宝石换物品。

#### 字段

`from`

- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) — 绿宝石价格

`to`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack)

### `cvh:slots`

三格配方：`slot1` + `slot2` → `slot3`。

#### 字段

`from.slot1`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack)
- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) — 数量，默认 `1`

`from.slot2`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack)
- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) — 数量，默认 `1`

`to.slot3`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ItemStack)
- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/RandomIntSupplier) — 数量，默认 `1`
