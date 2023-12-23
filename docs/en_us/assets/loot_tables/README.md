---
sort: 4
---

### Loot Tables

at `/loot_tables/`

format: see mc wiki.

https://minecraft.fandom.com/wiki/Loot_table#Recurring_JSON_structures_within_loot_tables_and_other_data_pack_files

note: in Forge, `name` is required for each loot

for example: 

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