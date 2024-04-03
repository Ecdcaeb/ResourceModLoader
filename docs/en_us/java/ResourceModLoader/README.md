### ResourceModLoader

The mod which coded by java need not use `rml.info`，just invoke
`mods.rml.ResourceModLoader`

`enableRML(Lmods/Hileb/rml/api/mods/ContainerHolder;)Lnet/minecraftforge/fml/common/ModContainer;`

or (If you can't get your `ModContainer`) `enableRML(Ljava/lang/String;[Lmods/Hileb/rml/api/mods/ContainerHolder$Modules;)V`. It will transfer the mod to RML to enable the RML functions.
