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

### Module reference

| Module | Purpose |
| --- | --- |
| [`rml:content`](CONTENT/index.md) | Compiled enhanced data-pack definitions |
| [`rml:functions`](FUNCTIONS/index.md) | Functions, executors, candidate generations and tooling |
| [`rml:loot_tables`](LOOT_TABLES/index.md) | Native loot tables |
| [`rml:ore_dic`](ORE_DIC/index.md) | Ore dictionary entries |
| [`rml:custom_villagers`](village/index.md) | Villager professions and trades |
| [`rml:registry_remap`](registry/REGISTRY_REMAP/index.md) | Remap missing registry names |
| [`rml:splash_text`](text/SPLASH_TEXT/index.md) | Main-menu splash texts |
| [`rml:config_define`](config/CONFIG_DEFINE/index.md), [`rml:config_override`](config/CONFIG_OVERRIDE/index.md), [`rml:config_redefault`](config/CONFIG_REDEFAULT/index.md) | Config definition, overrides and defaults |
| [`rml:mod_kubejs`](MOD_KUBEJS/index.md), [`rml:mod_crt`](MOD_CRT/index.md), [`rml:mod_groovy_script`](MOD_GROOVYSCRIPT/index.md) | External script integrations |
