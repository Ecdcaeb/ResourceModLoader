### `rml:config_redefault`

默认路径：`/config/redefault`

仅适用于普通 Forge 模组，不包含 Forge 和 FML 自身。

重新默认发生在已写入的配置文件之前，因此改的是默认值，而不是后续覆盖。

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
