## [IVillager](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/)

An action on villager professions or careers.

### `minecraft:profession`

Registers a villager profession.

#### Fields

`professionName` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) — profession registry name.

`villageTexture` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) — villager texture.

`zombieTexture` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) — zombie villager texture.

### `minecraft:cancer`

Registers a villager career (sub-profession).

#### Fields

`profession` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) — profession name.

`career` string — career name.

### `rml:trade`

Adds trades to a career.

#### Fields

`level` [Integer](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/Integer) — optional. Trade unlock level. Defaults to `1`.

`profession` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ResourceLocation) — profession.

`career` string — career name.

`trade` array of [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ITradeList).
