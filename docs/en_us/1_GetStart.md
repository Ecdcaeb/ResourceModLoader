---
sort: 2
---

### Getting started

Create an `rml.info` file for the resource pack.

For basic mod metadata, `rml.info` uses the same structure as `mcmod.info` (Forge: <https://docs.minecraftforge.net/en/1.12.x/gettingstarted/structuring/#the-mcmodinfo-file>).

`modid` is the namespace (`domain`) of the pack.

`rml.info` also has `pack_version`, the module-loading format. It defaults to `2`. Versions:

```
[1.0, 1.0.11]  1
[1.1.0]        2
[1.1.1,)       3
```

A pack that uses every feature lives at <https://github.com/Ecdcaeb/ResourceModLoader/tree/main/dev/example>.
