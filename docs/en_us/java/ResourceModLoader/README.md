### ResourceModLoader

A Java mod does not need `rml.info`. Register its declarations through the adapter's instance services:

```java
ResourceModLoader.runtime().getPacks().register(new ContainerHolder(modContainer));
```

Before its Forge container exists, request registration by modid. A null module array selects all default types when the container becomes available:

```java
ResourceModLoader.runtime().getPacks().request("example", null);
```

`getPacks().all()` returns a read-only snapshot ordered by modid. Duplicate registration fails. Registration does not execute modules or subscribe listeners.

This is a breaking Java API migration: `enableRML`, `loadModule`, `loadContainerHolders`, the public container collection, and the old filesystem scanner are removed. Consumers use `runtime().getModules().load(type, consumer, Caller.class)`. Transactional providers use `read` to obtain detached source snapshots before preparing runtime content.

Container and module declarations are immutable; use their getters. File contexts expose `getUri()` for source identity and `getBytes()` for original binary bytes, without text transcoding.

The Java 8 `runtime/` subproject has no external dependencies. It provides `ContentRuntime`, `ContentUnit`, `PackDefinition`, `ResourceScope`, and `OwnedRegistry` without Forge, Minecraft, Gson, or logging types. The root project owns Forge adaptation.

`ContentRuntime` validates dependencies, orders preparation deterministically, and applies single-use candidates under an injected thread guard. Registered callbacks must check `ResourceScope.isActive()`; every reversible resource must be registered with `scope.own(...)`. Failed preparation/activation preserves the old generation. Cleanup failure after commit retains the new generation and blocks further activation with `cleanup_failed`.

The function provider currently uses this lifecycle. Forge resource containers remain a startup adapter; config, native registration, and external script managers do not acquire a general hot-unload guarantee.
