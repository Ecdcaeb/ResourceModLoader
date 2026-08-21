## [IVillager](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/)

对村民职业或子职业的操作。

### `minecraft:profession`

注册村民职业。

#### 字段

`professionName` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) — 职业注册名。

`villageTexture` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) — 村民贴图。

`zombieTexture` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) — 僵尸村民贴图。

### `minecraft:cancer`

注册村民子职业（career）。

#### 字段

`profession` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) — 职业名。

`career` String — 子职业名。

### `rml:trade`

给指定子职业添加交易。

#### 字段

`level` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer) — 可选，解锁交易的等级，默认为 `1`。

`profession` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) — 职业。

`career` String — 子职业名。

`trade` [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ITradeList) 的数组。
