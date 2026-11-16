---
sort: 3
---

### `rml:functions`

Default path: `/functions/`

Put `.mcfunction` files under that folder, the same way a data pack does.

See <https://minecraft.fandom.com/wiki/Function_(Java_Edition)>.

`.executor` files in the same folder define function executors.

### Managed lifecycle

Functions and executors are parsed and checked as a complete candidate before activation. Executor construction has no event subscriptions. The runtime owns function entries and listeners and releases them on replacement, deactivation, and server shutdown.

Operators with permission level 2 can use:

```text
/rml functions status
/rml functions reload
/rml functions reload rml_example
/rml functions unload rml_example
```

`reload` reads registered packs again, including added, changed, and deleted source files. A modid selects one pack; omitting it prepares all function modules. Unknown commands, missing functions, dependency failures, or invalid sources reject the complete candidate and retain the old generation. This command does not discover new `rml.info` files or rerun Forge startup.

`status` reports active packs, generations, units, resources, and the last rejected change. `unload` deactivates only that pack's functions and executors. It does not delete world data, undo executed commands, or unload other module types.

Owned registry handles prevent old cleanup from deleting new functions or later unload from resurrecting already-released generations. Vanilla function reload rebinds active entries before preparing their replacements.

Declare cross-pack dependencies explicitly on the function module:

```json
{
  "name": "rml:functions",
  "requires": ["shared_rules"]
}
```

Dependencies must be active or included in the candidate. Replacing a dependency requires preparing its active dependents in the same batch; use `reload` without a modid. Unloading a still-required pack fails. Arbitrary commands inside `.mcfunction` files do not automatically infer pack dependencies.

A positive `ticks` interval executes on multiples of world total time; zero disables the tick rule, and a negative value runs each tick. World-load rules run on actual world-load events; hot replacement does not synthesize an extra load event.
