## [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/)
A pile of items. For example, in MC, the items in an item grid are a pile of items.

### `minecraft:item`
Default parser.
#### Fields:

`item` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) Item registration name

`data` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer) Meta value, if the item has variants, this field is required, default is 0

`count` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer) Quantity, default is 1

`nbt` NBTTagCompound (nbt in string form) The nbt of the item.

### `rml:enchantmented_item`
Since mc1.12.2 enchantments use numeric ids in nbt, a separate method is used here to add additional enchantments.
Represents an enchanted item.

#### Fields:

`item` [ItemStack](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ItemStack) Pre-item

`enchantment` Array of [EnchantmentData](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/EnchantmentData) enchantments