## [ItemStack](../)

A stack of items: the contents of one inventory slot.

### `minecraft:item`

Default deserializer.

#### Fields

`item` [ResourceLocation](../ResourceLocation) — item registry name.

`data` [Integer](../Integer) — metadata. Required when the item has subtypes. Defaults to `0`.

`count` [Integer](../Integer) — stack size. Defaults to `1`.

`nbt` `NBTTagCompound` (JSON object or SNBT string) — item NBT.

### `rml:enchantmented_item`

Minecraft 1.12.2 stores enchantments as numeric ids in NBT. This type adds enchantments by registry name instead.

#### Fields

`item` [ItemStack](../ItemStack) — base stack.

`enchantment` array of [EnchantmentData](../EnchantmentData).
