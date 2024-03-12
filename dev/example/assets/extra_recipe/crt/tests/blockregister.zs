#loader contenttweaker
import mods.contenttweaker.VanillaFactory;
import mods.contenttweaker.Block;

var zsBlock = VanillaFactory.createBlock("zs_block", <blockmaterial:iron>);
zsBlock.fullBlock = true;
zsBlock.lightOpacity = 255;
zsBlock.translucent = true;
zsBlock.lightValue = 1.0;
zsBlock.blockHardness = 5.0;
zsBlock.blockResistance = 5.0;
zsBlock.toolClass = "pickaxe";
zsBlock.toolLevel = 2;
zsBlock.blockSoundType = <soundtype:metal>;
zsBlock.register();