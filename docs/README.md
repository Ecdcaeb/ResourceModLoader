## Resource Mod Loader

The official document of ResourceModLoader.

The total access count:

[![ResourceModLoader](https://count.getloli.com/get/@ResourceModLoader?theme=gelbooru)](https://ecdcaeb.github.io/ResourceModLoader/)


# summary

ResourceModLoader is a module that allows loading non-executable Mods (`Resource Mod`), but can also be used as a library for modules written in Java.

In JE 1.13, data packs were added to Minecraft. 
Prior to this, the resource packs already had some data pack functions, such as using JSON to define a large number of recipes in more recipes, and mod authors could also use JSON to define their advancements.
However, the data package functions of these resource packages can only be used in mods and cannot be applied to real resource packs.

This module reads the `.zip` and `.jar` file or `directories` containing the `rml.info` file in `mods/`, packages it as a mod and runs it.

# Functions

For details, please view the brief official documentation.

## Forge function

### Can be displayed as a Mod normally.

### The data package function of the resource package provided by Forge can be applied, which includes:

- Define recipes using JSON;

- Define advancements using JSON.

### Can be used as a resource pack.

## ResourceModLoader function

- Use JSON to define OreDic.

- Load mcfunction.

- Define the loot table using JSON.

- Configuration value override.

- Configuration values re-default.

- Remap missing registry.

- Define villagers.

- Add splash texts.

## Expand functions

- Load the KubeJS script.

- Load CrT script.


## Gallery

![](https://media.forgecdn.net/attachments/768/377/2023-12-03-112009.png)  
"More formulas" without mod structure are loaded by fml   

## Attention

ResourceModLoader is not a ModLoader and cannot load Mods instead of ModLoader.

The data does not have the overwriting function, only the <domain> that is the same as <modid> has the data packet function.

More information, see official document : https://ecdcaeb.github.io/ResourceModLoader/