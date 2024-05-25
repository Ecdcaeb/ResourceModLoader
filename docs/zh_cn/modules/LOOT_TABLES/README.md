---
sort: 4
---

### 战利品表(LOOT_TABLES)

默认在 `/loot_tables/`

格式：参照wiki

<https://minecraft.fandom.com/wiki/Loot_table#Recurring_JSON_structures_within_loot_tables_and_other_data_pack_files>

参照国内教程

<https://ruhuasiyu.github.io/VanillaModTutorial/#%E6%88%98%E5%88%A9%E5%93%81%E8%A1%A8>

!!!注意， 在Forge, 每个pool都被要求有一个`name`字段。

例子: 

```json
{
  "pools": [
    {
      "name": "main",
      "__other": "other......"
    }
  ]
}
```