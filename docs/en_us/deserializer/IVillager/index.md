## [IVillager](../)

An action on villager professions or careers.

### `minecraft:profession`

Registers a villager profession.

#### Fields

`professionName` [ResourceLocation](../ResourceLocation) — profession registry name.

`villageTexture` [ResourceLocation](../ResourceLocation) — villager texture.

`zombieTexture` [ResourceLocation](../ResourceLocation) — zombie villager texture.

### `minecraft:cancer`

Registers a villager career (sub-profession).

#### Fields

`profession` [ResourceLocation](../ResourceLocation) — profession name.

`career` string — career name.

### `rml:trade`

Adds trades to a career.

#### Fields

`level` [Integer](../Integer) — optional. Trade unlock level. Defaults to `1`.

`profession` [ResourceLocation](../ResourceLocation) — profession.

`career` string — career name.

`trade` array of [ITradeList](../ITradeList).
