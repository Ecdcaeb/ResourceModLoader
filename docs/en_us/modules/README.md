---
sort: 1
---

### Modules

This section is for resource mods.

You choose which loaders run, and where they look, with modules.

Modules are the `modules` array in `rml.info`. Each element is a JSON object:

```json
{
  "name": "rml:config_override",
  "location": "mods/forge/config/override",
  "forceLoaded": false
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `name` | yes | Module type id. Unprefixed names use the `rml` domain. |
| `location` | no | Path under `/assets/<domain>/`. Defaults to the type's built-in path. |
| `forceLoaded` | no | If `true`, a load error crashes the game. Defaults to `false`. |

If `modules` is missing, every default module type is enabled.
