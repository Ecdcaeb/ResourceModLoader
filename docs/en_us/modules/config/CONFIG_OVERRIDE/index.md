### `rml:config_override`

Default path: `/config/override`

Applies to ordinary Forge mods. Does not apply to Forge or FML themselves.

Overrides win over values already written in the `.cfg` file.

#### JSON patch

Append `.json` to the config file name.

`add_potion.cfg` → `add_potion.cfg.json`.

Write only the values you want to change:

```json
{
  "entityelectricshakingconf": {
    "ap_addLimit_desc": 100
  }
}
```

#### Cfg patch

Append `.patch` to the config file name.

`add_potion.cfg` → `add_potion.cfg.patch`.

Keep only the entries you want to change:

```editorconfig
# Configuration file

##########################################################################################################
# entityelectricshakingconf
#--------------------------------------------------------------------------------------------------------#
# ap.configMisc.desc
##########################################################################################################

entityelectricshakingconf {
# ap.config.maxCountIs1.desc
B:ap_maxIs1_desc=true
}
```
