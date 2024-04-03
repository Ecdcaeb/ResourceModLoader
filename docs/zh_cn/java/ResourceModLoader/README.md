### ResourceModLoader

使用Java代码编写的Mod无需`rml.info`，调用
`mods.rml.ResourceModLoader`下

`enableRML(Lmods/Hileb/rml/api/mods/ContainerHolder;)Lnet/minecraftforge/fml/common/ModContainer;`

或（如果没有办法获得`ModContainer`）`enableRML(Ljava/lang/String;[Lmods/Hileb/rml/api/mods/ContainerHolder$Modules;)V`即可将Mod传递到RML启用RML功能。
