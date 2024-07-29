## [IVillager](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/)

表示为村民或对村民的操作。

### `minecraft:profession`
表示村民职业

#### 字段：

`professionName` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) 职业注册名

`villageTexture` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) 村民贴图

`zombieTexture` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) 僵尸村民贴图


### `minecraft:cancer`
表示村民子职业。

#### 字段：

`profession` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) 职业名

`career` String 子职业名

### `rml:trade`
对给定村民添加交易。

#### 字段：

`level` [Integer](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/Integer) 可选，解锁交易的等级，默认为1

`profession` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) 职业

`career` [ResourceLocation](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ResourceLocation) 子职业

`trade` [ITradeList](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ITradeList)的数组 交易列表 
