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
| `forceLoaded` | no | For startup modules, `true` makes loading errors fatal; defaults to `false`. Managed functions always validate the complete candidate and do not allow partial activation through this flag. |
| `requires` | no | Array of content dependency modids; defaults to empty. The function runtime validates this graph, rejects cycles, and prevents unloading packs with active dependents. |

If `modules` is missing, every default module type is enabled.
