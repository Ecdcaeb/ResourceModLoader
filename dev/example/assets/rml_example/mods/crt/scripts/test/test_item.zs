#priority 1
#debug
#loader contenttweaker

import mods.contenttweaker.VanillaFactory;
import mods.contenttweaker.Item;

val zsItem as Item = VanillaFactory.createItem("zs_item_aaa");
zsItem.maxDamage = 8848;
zsItem.rarity = "rare";
zsItem.creativeTab = <creativetab:tools>;
zsItem.toolClass = "pickaxe";
zsItem.toolLevel = 5;
zsItem.beaconPayment = true;
zsItem.register();