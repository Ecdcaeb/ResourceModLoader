## [ItemStack](../)

一堆物品。例如，一个物品格子里的内容就是一个 ItemStack。

### `minecraft:item`

默认解析器。

#### 字段

`item` [ResourceLocation](../ResourceLocation) — 物品注册名。

`data` [Integer](../Integer) — meta 值。物品有变种时必填，默认为 `0`。

`count` [Integer](../Integer) — 数量，默认为 `1`。

`nbt` NBTTagCompound（JSON 对象或 SNBT 字符串）— 物品 NBT。

### `rml:enchantmented_item`

1.12.2 的附魔在 NBT 里使用数字 id，因此用这个类型按注册名额外添加附魔。

#### 字段

`item` [ItemStack](../ItemStack) — 前置物品。

`enchantment` [EnchantmentData](../EnchantmentData) 的数组。
