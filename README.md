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

### Runtime architecture

The `runtime/` Java 8 subproject contains the dependency-free lifecycle kernel and directory/ZIP source snapshots. The root project owns Forge containers, module consumers, and game integration; the kernel imports no Minecraft, Forge, Gson, or logging APIs. Runtime classes are bundled into the mod jar.

Startup providers live in `rml.loader.module` and receive `ModuleLoader` through their constructors. `LoaderRuntime` is the composition root; Forge events and ASM hooks route into it. Providers do not look up global loader services. The former `RMLLoaders` aggregate and filesystem scanner have been removed.

The core mod container registers its lifecycle listener with FML so Forge enables RML and delivers construction, initialization, and server lifecycle events to the runtime. It exposes its own directory or jar to Forge's resource manager so built-in carrier models and textures load.

Run its isolated contract checks with a Java 8-compatible Gradle environment:

```text
./gradlew -p runtime check
```

The checks exercise dependency validation, failed activation rollback, generation ownership, repeated replacement/unload, registry rebinding, and directory/archive source parity. The managed function provider exposes `/rml functions status`, `reload [modid]`, and `unload <modid>`; other integrations retain startup semantics.

The Java API has moved to instance services under `ResourceModLoader.runtime()`. See the [migration reference](docs/en_us/java/ResourceModLoader/README.md) and [2.0 plan](docs/zh_cn/2_RML_2.0_Plan.md).

### Enhanced data-pack source

The standalone Java 8 `compiler/` project compiles data-pack directories or ZIPs into installable RML content. It preserves namespaces and assets, resolves function and registry tags, lowers enhanced commands and rules, and keeps source locations in the artifact. Native Forge providers execute the compiled definitions.

```text
./gradlew -p compiler check executableJar
java -jar compiler/build/libs/rml-compiler.jar capabilities
java -jar compiler/build/libs/rml-compiler.jar compile dev/datapack-example build/rml-agent-example.zip
```

The source catalog follows Minecraft's data-driven registries. The target game remains Forge 1.12.2; native definitions and version differences are validated by their providers. Functions, data rules, world generation, gameplay, presentation, clocks and world tests share the extensible decoder registry. Generic containers preserve their element types, including nested weighted values and typed constructor arguments.

See the [source example](dev/datapack-example), [decoder API](docs/zh_cn/java/deserializer/README.md), and [source and runtime guide](docs/zh_cn/2_RML_2.0_Plan.md#14-增强数据包的当前工程入口). The example clock drives the overworld from time zero; use a preview world. `/rml capabilities` lists actual runtime providers, and `/rml capabilities decoders` lists registered decoder names.

Agent integrations can use `/rml tools inspect`, `check [pack]`, `apply <candidate> <revision>`, and `deactivate <session> <revision> <pack> [pack...]`. Every response is JSON with diagnostics and the current state. Check prepares a detached candidate; apply commits that exact candidate with revision protection. See the [runtime tool contract](docs/zh_cn/modules/FUNCTIONS/README.md#agent-运行工具).

### Server-hosted Agent and terminal

The `agent/` Python 3.11+ package runs Google ADK 2.14.1 in a process owned by the Minecraft server. Install it with `python -m pip install ./agent`, build `:compiler:executableJar`, and configure `config/rml-agent.json` with the Python command, compiler JAR and model provider. Supply credentials through the configured environment variable before starting Minecraft. An unconfigured model is reported explicitly.

`/rml agent start|status|stop` manages the service. Start returns a private connection file for `rml-agent tui --connection <file>`; enabling automatic startup restores the console session and existing connections. The TUI displays actual model/tool events, file diffs and compiler output. Ctrl-C cancels a request; Ctrl-D detaches while the service continues running.

Each operator has an isolated, persistent workspace and conversation. Compiled builds enter the real runtime through checked candidates and revision protection. Native commands retain the player's identity and return their actual messages. Registry additions require installing the build for the next JVM launch. Durable request receipts prevent repeated game mutations after reconnects or service restarts.

`run_scenario` waits for an installed world test's assertions and cleanup, returning its native pass/fail/cancel result. Cancelling the Agent request cancels that scenario. Retained results can be queried or cancelled within the owning session; the game exposes the same behavior through `/rml test run|status|cancel`. Tests restore captured blocks, block entities and world time; use an appropriate test area because this is not an isolated preview world.

See [Agent setup, sessions and verification](docs/zh_cn/2_RML_2.0_Plan.md#15-服务端托管-agent-与-tui-会话) for model configuration, SSH connections, artifact access and the distinction between live application and startup installation.

