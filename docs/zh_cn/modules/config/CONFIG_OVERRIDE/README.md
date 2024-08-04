### rml:CONFIG_OVERRIDE
默认位于 `/conifg/override`

仅适用于通用 forge 模式。（不包含 Forge 和 FML）

#### Json Patch

文件名添加“json”。

例如，`add_potion.cfg` 对应 `add_potion.cfg.json`。

仅写入要修改的值。

示例：
```json
{
“entityelectricshakingconf”：{
“ap_addLimit_desc”：100
}
}
```

#### Cfg Patch
使用 `.cfg` 文件修补配置。

文件名添加“.patch”。

例如，`add_potion.cfg` 对应 `add_potion.cfg.patch`。

示例：
```editorconfig
# 配置文件

##########################################################################################################
# entityelectricshakingconf
#--------------------------------------------------------------------------------------------------------------------#
# ap.configMisc.desc
#############################################################################################################

entityelectricshakingconf {
# ap.config.maxCountIs1.desc
B:ap_maxIs1_desc=true
}
```

保留要修改的项目。

高于 cfg 文件。
