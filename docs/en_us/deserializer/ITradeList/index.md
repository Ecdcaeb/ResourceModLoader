## [ITradeList](../)

A villager trade.

### `minecraft:emerald_for_items`

Player items for villager emeralds.

#### Fields

`item` [ItemStack](../ItemStack) — items the player offers.

`price` [RandomIntSupplier](../RandomIntSupplier) — emerald count the villager pays.

### `minecraft:list_item_for_emeralds`

Player item plus emeralds for a villager item.

#### Fields

`from`

- `item` [ItemStack](../ItemStack)
- `price` [RandomIntSupplier](../RandomIntSupplier) — emerald count
- `count` [RandomIntSupplier](../RandomIntSupplier) — buying-item count at runtime

`to`

- `item` [ItemStack](../ItemStack)
- `count` [RandomIntSupplier](../RandomIntSupplier) — selling-item count at runtime

### `minecraft:item_and_emerald_to_item`

Emeralds for an item.

#### Fields

`from`

- `price` [RandomIntSupplier](../RandomIntSupplier) — emerald price

`to`

- `item` [ItemStack](../ItemStack)

### `cvh:slots`

Three-slot recipe: `slot1` + `slot2` → `slot3`.

#### Fields

`from.slot1`

- `item` [ItemStack](../ItemStack)
- `price` [RandomIntSupplier](../RandomIntSupplier) — count, defaults to `1`

`from.slot2`

- `item` [ItemStack](../ItemStack)
- `price` [RandomIntSupplier](../RandomIntSupplier) — count, defaults to `1`

`to.slot3`

- `item` [ItemStack](../ItemStack)
- `price` [RandomIntSupplier](../RandomIntSupplier) — count, defaults to `1`
