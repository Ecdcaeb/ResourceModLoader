### 模块

这一部分适用于Resource Mod。

对于原版部分，可以部分参照数据包执行 <https://ruhuasiyu.github.io/VanillaModTutorial/#%E8%B5%84%E6%BA%90%E5%8C%85/%E5%91%BD%E5%90%8D%E7%A9%BA%E9%97%B4>

你可以通过模块选择性应用加载功能和路径。

模块在`rml.info`中`modules`定义，是一个关于 JsonObject 的数组，每个元素如下：
```json
{
  "name":"CONFIG_OVERRIDE",
  "location":"mods/forge/config/override",
  "forceLoaded":false
}
```

其中，`type`代表模块，必要内容。

`location`可选，如果不填则默认，实际路径是`/assets/<domain>/xxxx`即本文档协定的路径。

`forceLoaded`可选，默认为false，设置为true可以在该模块未加载时崩溃。

如果`modules`字段不存在，则默认应用全部默认模块。