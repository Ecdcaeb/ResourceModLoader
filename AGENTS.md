# Repository Guidelines

## Project Overview

ResourceModLoader (RML) is a Minecraft Forge **1.12.2** mod that turns datapacks into executable content. It ships three cooperating pieces:

- **Mod (`src/main/java`, `runtime/`)** — loads compiled packs at runtime, registers native content (items, blocks, enchantments, worldgen, dialogs, clocks/tests), and hosts an agent service.
- **Compiler (`compiler/`)** — standalone Java 8 tool that turns a datapack source into an installable RML pack (`rml-compiler.jar`).
- **Agent (`agent/`)** — Python 3.11+ service (Google ADK) that drives the game over an authenticated loopback bridge, with a TUI client.

Docs are a MkDocs Material site (`docs/`, `mkdocs.yml`).

## Architecture & Data Flow

1. **Author** a datapack: `pack.mcmeta` (with an `rml` block: id, version, minecraft, optional `constants`) plus `data/<namespace>/<type>/...`.
2. **Compile**: `rml.compiler.Main` (`compile` / `check` / `capabilities`) → `ProjectReader` indexes the source → `RmlCompiler` resolves tags, lowers functions and rules, and emits typed definitions with a source map.
3. **Install**: the ZIP/JAR carries `rml.info` whose `modules` list declares `rml:content`. Forge discovery (`RMLModDiscover`) registers the pack; `ContentCatalog` reads `rml:content` envelopes `{"format":1,"type":...,"id":...,"value":...}`.
4. **Activate**: `LoaderRuntime` wires providers — startup types (recipes, items, dimensions, worldgen) during Forge lifecycle; runtime types (functions, predicates, item modifiers, loot, structures) through `ContentProviders`. `FunctionModule` owns check/apply/deactivate with **candidate + revision guards**; contributions are owned by a `ResourceScope` (`scope.own(...)`) so a failed candidate leaves the previous generation untouched (`OwnedRegistry`, `ContentRuntime`).
5. **Agent**: `AgentSupervisor` spawns `python -m rml_agent serve`; FastAPI streams run events over SSE; Python calls the game's `/invoke` / `/cancel` endpoints; Java executes game work on the server thread and keeps file/archive IO off it.

Data types are decoded by one registry: `rml.deserializer.DeserializerManager` dispatches on the JSON `type` field per Java target class.

## Key Directories

| Path | Purpose |
| --- | --- |
| `src/main/java/rml/deserializer/` | Decoder framework (`DeserializerManager`, `DeserializerBuilder`, `Argument`, generic entries) |
| `src/main/java/rml/loader/deserialize/` | Native value decoders (`NativeTypeDecoders`, `MatcherDecoders`, `NbtDecoders`, `TextDecoders`, `NumericValueDecoders`) |
| `src/main/java/rml/loader/content/` | Compiled content: `data` (`DataRules`, `RuleCatalog`, `RuleFields`), `gameplay`, `worldgen`, `presentation`, `time`, plus `ContentCatalog`/`ContentProviders` |
| `src/main/java/rml/loader/runtime/` | Loader runtime: `LoaderRuntime`, `FunctionModule`, `RMLContentCommands` |
| `src/main/java/rml/loader/agent/` | Java side of the agent: `AgentSupervisor`, `AgentBridge`, preview process supervision |
| `runtime/` | Dependency-free shared runtime module (`:runtime`) reused by the mod and the compiler |
| `compiler/` | Standalone compiler project (`:compiler`) with its own contract tests |
| `agent/src/rml_agent/` | Python service: `service.py`, `engine.py`, `bridge.py`, `workspace.py`, `preview.py` |
| `agent/tests/` | pytest suite for the agent |
| `src/main/resources/META-INF/rml/` | `modules.json` (module type registry) and `deserializer/<class>.json` (lazy decoder discovery) |
| `dev/datapack-example/` | Reference datapack (functions, predicates, modifiers, loot, damage types, clocks, timelines, world tests) |
| `docs/zh_cn/`, `docs/en_us/` | Bilingual documentation sources |

## Development Commands

Windows (PowerShell/cmd) with the cached JDK 8 and Gradle home:

```powershell
$env:JAVA_HOME = 'E:\project\.gradle\jdks\temurin-8-amd64-windows.2'
$env:GRADLE_USER_HOME = 'E:\project\.gradle'
.\gradlew.bat build                                  # mod jar in build/libs
.\gradlew.bat --offline dataCompositionContractTest deserializerContractTest
.\gradlew.bat --offline -p compiler check executableJar
java -jar compiler/build/libs/rml-compiler.jar capabilities
java -jar compiler/build/libs/rml-compiler.jar check dev/datapack-example
java -jar compiler/build/libs/rml-compiler.jar compile dev/datapack-example build/rml-agent-example.zip
```

POSIX: use `./gradlew`. `--offline` works when the wrapper distribution and dependencies are cached.

Docs:

```powershell
python -m venv .venv-docs
.venv-docs/Scripts/python -m pip install -r requirements-docs.txt
.venv-docs/Scripts/python -m mkdocs serve
.venv-docs/Scripts/python -m mkdocs build --strict    # output: build/docs-site
```

Agent:

```powershell
python -m pip install -e "agent[test]"
python -m pytest agent/tests
```

## Code Conventions & Common Patterns

- **Named decoders**: one `AbstractDeserializer` per (target type, resource id) in a `*Decoders` class; ids are `rml:<name>` with optional `minecraft:` aliases. `addDefaultEntry` handles untyped input. Wire new decoders through the owning module's `registerDecoders(manager)` or `NativeTypeDecoders.register`.
- **Lazy discovery**: a Minecraft target class binds to its decoder implementations through `META-INF/rml/deserializer/<binary class name>.json` (array of implementation class names). Minecraft classes are auto-registered through `MCDeserializers.onNewRegistry`.
- **Errors**: JSON problems raise `JsonDeserializeException` carrying the offending JSON; provider boundaries wrap failures in `IllegalArgumentException` with the source path. Compiler diagnostics use stable codes: `invalid_rule`, `unsupported_field`, `invalid_identifier`, `missing_predicate`, `modifier_cycle`.
- **Rule contracts**: compiler-side field rules for RML-native nodes live in `compiler/src/main/java/rml/compiler/rules/RmlRuleValidation.java` (shared helpers in `RuleJson.java`); runtime contracts live in `src/main/java/rml/loader/content/data/Context*Decoders.java` and `RuleFields.java`. Keep both sides in sync when adding a rule type.
- **Content types**: register in the owning module's `getTypes()` (startup) or via `ContentProviders.registerRuntime` (live). Any reversible registration must be owned by the scope and guarded by `scope.isActive()`.
- **Agent tools**: add an async callable in `Engine._tools` (use `perform` for event recording and the per-workspace lock) and extend `AgentBridge` argument validation + dispatch for new game operations.
- **Python**: `asyncio` throughout; bridge calls are authenticated loopback JSON; runs are tasks with durable receipts; workspace builds shell out to the compiler asynchronously.
- **Docs**: keep `zh_cn` and `en_us` in parity, use `<topic>/index.md`, and register new pages in `mkdocs.yml` nav (`mkdocs build --strict` fails on broken internal links).

## Important Files

- `build.gradle`, `settings.gradle`, `gradle.properties` — ForgeGradle 2.3.4, Forge 1.12.2-14.23.5.2847, `stable_39` mappings, Gradle 4.9 wrapper.
- `src/main/resources/META-INF/rml/modules.json` — module type registry (`rml:content`, `rml:functions`, `rml:loot_tables`, `rml:ore_dic`, `rml:registry_remap`, `rml:custom_villagers`, `rml:splash_text`, `rml:structures`, `rml:dimension`, `rml:config_*`, `rml:mod_*`, …).
- `src/main/resources/META-INF/rml/deserializer/*.json` — decoder discovery bindings.
- `compiler/src/main/java/rml/compiler/RmlCompiler.java` — supported type lists and packaging.
- `src/main/java/rml/loader/content/ContentCatalog.java`, `ContentProviders.java` — compiled content envelope and provider registry.
- `src/main/java/rml/loader/content/data/DataRules.java`, `RuleCatalog.java` — rule interfaces, named registry, candidate preparation.
- `src/main/java/rml/loader/runtime/FunctionModule.java` — candidate/revision lifecycle, check/apply/deactivate.
- `agent/src/rml_agent/engine.py`, `service.py`, `bridge.py` — tool definitions, HTTP/SSE service, game bridge.
- `mkdocs.yml`, `requirements-docs.txt`, `.github/workflows/docs.yml`, `.github/workflows/build_snapshot.yml`.
- `dev/datapack-example/pack.mcmeta` — canonical datapack metadata example.

## Runtime/Tooling Preferences

- **JDK 8 only** (`temurin-8-amd64-windows.2` locally); the mod targets Java 8. Gradle 4.9 via the wrapper (Tencent mirror) — do not bump it.
- Prefer `--offline` Gradle invocations once caches are warm; the daemon is disabled by `gradle.properties`.
- Python: 3.11+, pinned `google-adk[db]==2.14.1` and `litellm==1.102.1`; keep a virtualenv outside the repo (`.venv-docs/`, `.venv/` are ignored).
- Docs: MkDocs Material 9.7.0 pinned; **no Ruby/Jekyll** in the toolchain anymore.
- Windows helper scripts: `runClient.bat`, `buildExample.bat`, `release.bat`, `setupDecomp.bat`, `toJar.bat`.
- Deployment: `docs.yml` publishes to GitHub Pages only when *Settings → Pages → Source = GitHub Actions*.

## Testing & QA

- Java "contract tests" are plain `main()` programs with local helpers (`check`, `equal`, `reject`), launched by Gradle `JavaExec` tasks wired into `check`. Run one: `./gradlew.bat --offline dataCompositionContractTest`.
- Root tasks: `deserializerContractTest`, `compiledLootContractTest`, `functionDataPathContractTest`, `functionNbtContractTest`, `damageDefinitionContractTest`, `timeWorldDataContractTest`, `dataCompositionContractTest`, `nativeEnchantmentContractTest`, `gameplayCompositionContractTest`, `worldgenCapabilityContractTest`, `presentationSubmissionContractTest`, `presentationRegistryPhaseContractTest`, `timeCompositionContractTest`, `agentBridgeJournalContractTest`, `agentBridgeArchivesContractTest`, `previewProcessContractTest`, `detachedCompiledPackContractTest`, `detachedImportContractTest`.
- Compiler tasks: `:compiler:sourceContractTest`, `:compiler:languageContractTest`, `:compiler:ruleContractTest`, `:compiler:compilerContractTest`.
- Python: `python -m pytest agent/tests`; set `RML_TEST_COMPILER` to `compiler/build/libs/rml-compiler.jar` to enable the workspace-compiler integration case.
- Live Forge checks (`verifyInWorld` in `GameplayAdvancedContractTest`, `PresentationPlaybackContractTest`, `WorldgenAssemblyContractTest`, `TimeCompositionContractTest`) need a real server world; unit-level contract tests do not.
- Expectations for a change: add/extend a contract test next to the touched package, keep compiler rule schemas covered in `RuleCompilerContractTest`, update both language doc pages, and ensure `mkdocs build --strict` passes. Behavior verified only in a real game is not covered by the contract suites.
