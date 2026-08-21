### `rml:config_redefault`

Default path: `/config/redefault`

Applies to ordinary Forge mods. Does not apply to Forge or FML themselves.

Re-defaults run before the written config file, so they change the default, not a later override.

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
