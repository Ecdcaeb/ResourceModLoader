### Modules

### Look up

Resolve a module type with `rml.loader.api.mods.module.ModuleType#valueOf`.

Run startup consumers with `ResourceModLoader.runtime().getModules().load(type, consumer, Caller.class)`. Pack and regular-file order is stable. The loader sets the active Forge container for each consumer and always restores the previous container.

`read(type, Caller.class)` returns detached source snapshots after closing directory walks and archives. Use `read(type, Caller.class, modid)` to prepare one pack. Source errors abort preparation. Managed providers must keep parsing free of game effects and listener registration.

The old static `loadModule` entry point is removed. File contexts expose `getUri()`, `getRelativePath()`, `openBufferedReader()`, and binary-preserving `getBytes()`.

### Create

Place `rml.modules` or `META-INF/rml/modules.json` on the classpath to register extra module types.

File format: an array of [ModuleType](https://ecdcaeb.github.io/ResourceModLoader/en_us/deserializer/ModuleType).
