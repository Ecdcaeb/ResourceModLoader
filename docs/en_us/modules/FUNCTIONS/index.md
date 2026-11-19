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

`status` reports active packs, generations, units, resources, and the last rejected change. `unload` deactivates that pack's reloadable runtime content. It does not delete world data, undo executed commands, or unload startup registrations.

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

### Structured runtime tools

Permission level 2 also exposes a JSON command interface for operators and Agents:

```text
/rml tools inspect
/rml tools check [pack]
/rml tools apply <candidate> <revision>
/rml tools deactivate <session> <revision> <pack> [pack...]
```

`inspect` lists installed packs, capability lifecycles, world tests and active runtime state. `check` prepares a detached candidate and returns its token, expected revision and affected units. Pass that token and revision to `apply`; it commits the prepared content without rereading changed source files. Each session retains one candidate, superseded by the next check even if that check fails. A commit attempt consumes the candidate. Deactivation additionally requires the session ID, and dependent packs must be deactivated together.

Every response contains `protocol`, `operation`, `scope`, `ok`, `result`, `diagnostics` and the actual post-operation `state`. Use `ok` to determine success; command dispatch alone does not indicate success. Diagnostics use stable codes such as `revision_conflict`, `stale_candidate`, `stale_session`, `prepare_failed`, and `requires_restart`. A `cleanup_failed` response may describe an already-committed new generation, so always use its returned state.

These operations manage registered runtime content. Added, changed or removed compiled startup definitions cause `check` and `functions reload` to return `requires_restart`. Resource packs, mod declarations and external scripts are not reloaded by this interface.
