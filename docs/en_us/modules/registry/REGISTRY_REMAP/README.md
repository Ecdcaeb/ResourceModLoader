### `rml:registry_remap`

Default path: `/registry/remap`

Remap missing registry names.

| Type | Name | Description |
| --- | --- | --- |
| string | `registry` | Registry name |
| object | `mapping` | Old name → new name |

Example:

```json
{
  "registry": "minecraft:items",
  "mapping": {
    "minecraft:totem": "minecraft:totem_of_undying"
  }
}
```
