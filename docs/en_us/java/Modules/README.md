### Modules

### Look up

Resolve a module type with `rml.loader.api.mods.module.ModuleType#valueOf`.

`rml.loader.ResourceModLoader#loadModule` walks every enabled container that has that module, sets the active mod container, and runs your consumer on each file.

### Create

Place `rml.modules` or `META-INF/rml/modules.json` on the classpath to register extra module types.

File format: an array of [ModuleType](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ModuleType).
