### rml:CONFIG_OVERRIDE

Default at `/conifg/override`

Only for the common forge mod.(Not contain Forge & FML)


#### Json Patch

File name add "json".

For example `add_potion.cfg` for `add_potion.cfg.json`.

Only write the value you want to interrupt.

Example:
```json
{
  "entityelectricshakingconf": {
    "ap_addLimit_desc": 100
  }
}
```

#### Cfg Patch
use a `.cfg` file to patch a config.

File name add ".patch".
For example `add_potion.cfg` for `add_potion.cfg.patch`.

Example:
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

Remain the items you want to patch.

Higher than cfg file.
