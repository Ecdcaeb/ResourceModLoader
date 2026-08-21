## <img src="icon.jpg" width = "128" height = "128" alt="ResourceModLoader" align=center /> Resource Mod Loader
[![Discord](https://img.shields.io/discord/1189950517179330612.svg?color=%237289da&label=Discord&logo=discord&logoColor=%237289da)](https://discord.gg/HehwZApQXK)
[![TotalAccess](https://count.getloli.com/get/@ResourceModLoader?theme=gelbooru)](https://ecdcaeb.github.io/ResourceModLoader/)

Official repository for ResourceModLoader: source, local libraries, and examples.

### What is ResourceModLoader

ResourceModLoader is a loader for non-executable resource mods, and a library for Java mods.

Minecraft Java Edition 1.13 added data packs. Before that, resource packs already had a few data-pack features: JSON recipes from More Recipes, JSON advancements from mods, and similar. Those features only worked inside mods. They did not work in a real resource pack.

RML packages a zip, jar, or directory that contains `rml.info` as a Forge mod and runs those data files. After CraftTweaker and KubeJS support, many people also use it to ship scripts. That is no longer a pure low-code data pack.

### What can I do with ResourceModLoader?

See the [documentation](https://ecdcaeb.github.io/ResourceModLoader/).

### Where can I install it?

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/resource-mod-loader)
- [GitHub releases](https://github.com/Ecdcaeb/ResourceModLoader/releases)
- [GitHub Actions snapshots](https://github.com/Ecdcaeb/ResourceModLoader/actions)

### Use ResourceModLoader as a library

1. Add [CurseMaven](https://cursemaven.com/).
2. Pick a Maven short coordinate from the [list](https://ecdcaeb.github.io/ResourceModLoader/#maven).

[This commit](https://github.com/Ecdcaeb/ShotaASM/commit/f5d23801b5108b2687582204f9e78a4121a72db7) is an example of depending on RML and adding a module type.

### The documentation is short

Yes. Open an issue if you want a page expanded, or send a pull request.
