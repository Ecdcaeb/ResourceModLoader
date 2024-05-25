### REGISTRY_REMAP

Default at `/registry/remap`

Remap the registry.

```text
Type          |  Name    | Description

String        | registry | the register name for the registry.
JsonObject    | mapping  | the mapping
     \
      --String| *        | the name is old name, the value is the new name.
```

For example:
```json
{
  "registry": "minecraft:items",
  "mapping": {
    "minecraft:totem": "minecraft:totem_of_undying"
  }
}
```
