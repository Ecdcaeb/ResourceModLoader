#priority 1
#modloaded contenttweaker
#loader preinit

import mods.contenttweaker.VanillaFactory;
import mods.contenttweaker.Item;

val cotItem as Item = VanillaFactory.createItem("cot_item");
cotItem.rarity = "epic";
cotItem.creativeTab = <creativetab:misc>;
cotItem.beaconPayment = true;
cotItem.maxStackSize = 999;
cotItem.register();