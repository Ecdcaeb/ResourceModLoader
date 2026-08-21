## [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/)

A villager trade.

### `minecraft:emerald_for_items`

Player items for villager emeralds.

#### Fields

`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack) — items the player offers.

`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) — emerald count the villager pays.

### `minecraft:list_item_for_emeralds`

Player item plus emeralds for a villager item.

#### Fields

`from`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack)
- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) — emerald count
- `count` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) — buying-item count at runtime

`to`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack)
- `count` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) — selling-item count at runtime

### `minecraft:item_and_emerald_to_item`

Emeralds for an item.

#### Fields

`from`

- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) — emerald price

`to`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack)

### `cvh:slots`

Three-slot recipe: `slot1` + `slot2` → `slot3`.

#### Fields

`from.slot1`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack)
- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) — count, defaults to `1`

`from.slot2`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack)
- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) — count, defaults to `1`

`to.slot3`

- `item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack)
- `price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) — count, defaults to `1`
