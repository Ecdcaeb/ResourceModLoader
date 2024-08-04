### Modules、

### Get

Get the module type through `rml.loader.api.mods.module.ModuleType#valueOf`.

`rml.loader.ResourceModLoader#loadModuleFindAssets` gets and traverses the resources of all mods that have enabled the module and post events.

### Create

Create `rml.modules` in the root directory to implement customized module types.

File format:

[ModuleType](https://ecdcaeb.github.io/ResourceModLoader/zh_cn/deserializer/ModuleType) 的数组。