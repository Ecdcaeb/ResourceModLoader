### 物品(Item)

例如
```json
{
        "item": "minecraft:diamond",
        "count": 1,
        "data": 0, 
        "nbt": "{haha:true}",  
        "enchantment": [
          {
            "name": "minecraft:fire_protection",
            "level":{
                "type":"minecraft:price",
                "min": 12,
                "max":12
            }
          }
        ]
 }
```

```text
类型           |  键名    | 描述

String         | item      | 物品注册名
int-positive   | count     | 物品数量
int-positive   | data      | 物品元数据
JsonObjectArray|enchantment| 接受的附魔
\ String       | name      | 附魔的注册名
  ValueGetter  | level     | 附魔的等级
```