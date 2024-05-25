---
sort: 1
---

### Modules

This part is about the resource mod

You can selectively apply load functions and paths through modules.

The module is defined in `modules` in `rml.info` and is an array of JsonObject. Each element is as follows:
```json
{
  "type":"CONFIG_OVERRIDE",
  "location":"mods/forge/config/override",
  "forceLoaded":false
}
```

Among them, `type` represents module, necessary content.

`location` is optional. If not filled in, it will default. The actual path is `/assets/<domain>/xxxx`, which is the path agreed in this document.

`forceLoaded` is optional, defaults to false, set to true to crash when the module is not loaded.

If the `modules` field is not exist, all default modules are applied by default.