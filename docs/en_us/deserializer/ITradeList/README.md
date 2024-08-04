## [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/)
Indicates a villager trade.

### `minecraft:emerald_for_items`
Exchange items for emeralds.

#### Fields:

`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack) Items provided by players

`price` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) Number of emeralds provided by villagers

### `minecraft:list_item_for_emeralds`
Items plus emeralds in exchange for items.

#### Fields:

`from`

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) Number of emeralds

__`count` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) Item quantity, replace item quantity at runtime

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack) Item

`to`

__`count` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) Item quantity, replace item quantity at runtime

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack) Item

### `minecraft:list_item_for_emeralds`
Exchange emeralds for items. 
#### Fields: 

`from` 

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) Price 

`to` 

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack) 

### `cvh:slots` 
Specifies the slot item. 

slot1 + slot2 -> slot3 
#### Field: 

`slot1`

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) Item quantity

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack) items

`slot2`

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) Item quantity

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack) items

`slot3` 

__`price` [RandomIntSupplier](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/RandomIntSupplier) Item quantity 

__`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack) items