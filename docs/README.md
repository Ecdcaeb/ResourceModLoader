## Resource Mod Loader

Official documentation for ResourceModLoader.

Total visits:

[![ResourceModLoader](https://count.getloli.com/get/@ResourceModLoader?theme=gelbooru)](https://ecdcaeb.github.io/ResourceModLoader/)

# Summary

ResourceModLoader loads non-executable resource mods. Java mods can also use it as a library.

Minecraft Java Edition 1.13 added data packs. Before that, resource packs already had a few data-pack features: JSON recipes from More Recipes, JSON advancements from mods, and similar. Those features only worked inside mods. They did not work in a real resource pack.

RML scans `mods/` for zip files, jar files, and directories that contain `rml.info`, wraps each one as a Forge mod, and runs it.

# Features

See the rest of this site for details.

## Forge

- Shows up as a normal mod in the mod list.
- Uses Forge resource-pack data features:
  - JSON recipes
  - JSON advancements
- Can be used as a resource pack.

## ResourceModLoader

- Define ore dictionary entries in JSON.
- Load `.mcfunction` files.
- Register loot tables from JSON.
- Override config values.
- Re-default config values.
- Remap missing registry names.
- Define villagers.
- Add splash texts.

## Integrations

- Load KubeJS scripts.
- Load CraftTweaker scripts.
- Load GroovyScript classes.

## Gallery

![](https://media.forgecdn.net/attachments/768/377/2023-12-03-112009.png)

"More Recipes" content without a Java mod structure, loaded through FML.

## Limits

ResourceModLoader is not a replacement for Forge. It cannot load ordinary Java mods by itself.

Data files do not overwrite other mods. Only the namespace that matches the pack `modid` gets data-pack behaviour.

More: <https://ecdcaeb.github.io/ResourceModLoader/>

## Development

### Maven

```json
{
  "1.1.1": "curse.maven:resource-mod-loader-945917:5573938-sources-5573944",
  "1.1.2": "curse.maven:resource-mod-loader-945917:5614802-sources-5614803",
  "snapshot-79": "curse.maven:resource-mod-loader-945917:5768125-source-5768126"
}
```
