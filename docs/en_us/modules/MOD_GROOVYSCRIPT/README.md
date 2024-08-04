### rml:MOD_GROOVYSCRIPT (GroovyScript)

Default at
`/groovy_script/run_config.json`

Unlike `runConfig.json`, `run_config.json` only requires the `classes` field to be filled in. This field is a JsonObject with the key name being loader and the key value being a String array. Each String represents the absolute path of the script file in the package.

For example:

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

