## [IVillager](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/)

Represents actions for or on villagers.

### `minecraft:profession`
Indicates the villager's profession

#### Fields

`professionName` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) Profession registration name

`villageTexture` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) Villager texture

`zombieTexture` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) Zombie villager texture


### `minecraft:cancer`
Indicates the villager's cancer.

#### Fields

`profession` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) profession name

`career` String cancer name

### `rml:trade`
对给定村民添加交易。

#### Fields

`level` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer) Optional, unlock trade level, default is 1

`profession` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) profession

`career` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) career

`trade` Array of [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ITradeList) trade list
