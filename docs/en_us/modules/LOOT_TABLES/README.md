---
sort: 4
---

### `rml:loot_tables`

Default path: `/loot_tables/`

Format: Minecraft loot tables. See <https://minecraft.fandom.com/wiki/Loot_table#Recurring_JSON_structures_within_loot_tables_and_other_data_pack_files>.

Forge requires a `name` on every pool:

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
