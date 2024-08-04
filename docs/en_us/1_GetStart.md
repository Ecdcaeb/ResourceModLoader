---
sort: 2
---

### Get Start

Create an `rml.info` file for the resource pack.

In terms of basic module information, `rml.info` has the same structure as `mcmod.info` (Forge official documentation: <https://docs.minecraftforge.net/en/1.12.x/gettingstarted/structuring/#the-mcmodinfo-file>).

Among them, `modid` is the `namespace(doMain)` of your resource pack.

In addition, `rml.info` also requires a `pack_version` field, which indicates the module loading version. The default value is 2. Here is a list of reference versions:

```
[1.0, 1.0.11] 1

[1.1.0] 2

[1.1.1,) 3
```

You can see an example of all functions here <https://github.com/Ecdcaeb/ResourceModLoader/tree/main/dev/example>.