### Redefault（默认配置）

在`/conifg/redefault`

文件名仅仅在原有配置后加上json后缀。

例如对`forge.cfg`的修改为`forge.cfg.json`。

写入需要强制修改的默认配置值，只呈现需要修改的部分。

例如:
```json
{
    "client": {
        "forgeCloudsEnabled":true
    }
}
```

优先级高于默认值，低于文件。
