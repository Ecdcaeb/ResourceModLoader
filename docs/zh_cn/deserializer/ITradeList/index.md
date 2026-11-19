## [ITradeList](../)

村民交易。

### `minecraft:emerald_for_items`

玩家用物品换村民的绿宝石。

#### 字段

`item` [ItemStack](../ItemStack) — 玩家提供的物品。

`price` [RandomIntSupplier](../RandomIntSupplier) — 村民给出的绿宝石数量。

### `minecraft:list_item_for_emeralds`

玩家物品加绿宝石，换村民物品。

#### 字段

`from`

- `item` [ItemStack](../ItemStack)
- `price` [RandomIntSupplier](../RandomIntSupplier) — 绿宝石数量
- `count` [RandomIntSupplier](../RandomIntSupplier) — 运行时买入物品数量

`to`

- `item` [ItemStack](../ItemStack)
- `count` [RandomIntSupplier](../RandomIntSupplier) — 运行时卖出物品数量

### `minecraft:item_and_emerald_to_item`

绿宝石换物品。

#### 字段

`from`

- `price` [RandomIntSupplier](../RandomIntSupplier) — 绿宝石价格

`to`

- `item` [ItemStack](../ItemStack)

### `cvh:slots`

三格配方：`slot1` + `slot2` → `slot3`。

#### 字段

`from.slot1`

- `item` [ItemStack](../ItemStack)
- `price` [RandomIntSupplier](../RandomIntSupplier) — 数量，默认 `1`

`from.slot2`

- `item` [ItemStack](../ItemStack)
- `price` [RandomIntSupplier](../RandomIntSupplier) — 数量，默认 `1`

`to.slot3`

- `item` [ItemStack](../ItemStack)
- `price` [RandomIntSupplier](../RandomIntSupplier) — 数量，默认 `1`
