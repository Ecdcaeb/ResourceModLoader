### `rml:mod_groovy_script` (GroovyScript)

Default path: `/groovy_script/run_config.json`

Requires GroovyScript 1.4 or newer.

Unlike GroovyScript's `runConfig.json`, this file only needs `classes`. The value is a JSON object: loader name → string array. Each string is the path of a script inside the pack.

Loaders match GroovyScript 1.4: `preInit`, `init`, `postInit`, plus `all` for every stage.

Example:

```json
{
  "classes": {
    "postInit": [
      "groovy/example/main.groovy",
      "groovy/example/main2.groovy"
    ]
  }
}
```
