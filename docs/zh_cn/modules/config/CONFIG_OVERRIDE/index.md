### `rml:config_override`

默认路径：`/config/override`

仅适用于普通 Forge 模组，不包含 Forge 和 FML 自身。

覆盖值优先于已经写进 `.cfg` 的内容。

#### JSON 补丁

在配置文件名后追加 `.json`。

`add_potion.cfg` → `add_potion.cfg.json`。

只写要修改的值：

```json
{
  "entityelectricshakingconf": {
    "ap_addLimit_desc": 100
  }
}
```

#### Cfg 补丁

在配置文件名后追加 `.patch`。

`add_potion.cfg` → `add_potion.cfg.patch`。

只保留要修改的项：

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
