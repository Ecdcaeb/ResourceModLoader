### Redefault（默认配置）

在`/conifg/redefault`

文件名仅仅在原有配置后加上json后缀。

仅适用于依赖Forge config机制的普通Mod。（不包括Forge和FML）

例如对`add_potion.cfg`的修改为`add_potion.cfg.json`。

写入需要强制修改的默认配置值，只呈现需要修改的部分。

例如:
```json
{
  "entityelectricshakingconf": {
    "ap_addLimit_desc": 100
  }
}
```

优先级高于默认值，低于文件。
