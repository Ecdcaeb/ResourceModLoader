### ResourceModLoader

The mod which coded by java need not use `rml.info`, just invoke
`mods.Hileb.rml.ResourceModLoader` method :
`public static enableRML(Lnet/minecraftforge/fml/common/ModContainer;)Lnet/minecraftforge/fml/common/ModContainer;`
or `public static enableRML(Ljava/lang/String;)V`. It will transfer the mod to RML to enable the RML functions.