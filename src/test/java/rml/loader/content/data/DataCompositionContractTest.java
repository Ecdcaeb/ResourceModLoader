package rml.loader.content.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.LootFunctionManager;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;
import rml.deserializer.JsonDeserializeException;
import rml.deserializer.value.IntProvider;
import rml.deserializer.value.LevelBasedValue;
import rml.loader.content.CompiledLootModule;
import rml.loader.content.CompiledTagModule;
import rml.loader.content.ContentCatalog;
import rml.loader.deserialize.NativeTypeDecoders;
import rml.loader.runtime.function.FunctionDataPath;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Consumer regressions for custom children and context crossing the native loot adapters. */
public final class DataCompositionContractTest {
    private static final JsonParser JSON = new JsonParser();

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        DeserializerManager manager = new DeserializerManager("rml");
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "resource_location"), ResourceLocation.class,
                value -> new ResourceLocation(manager.decode(String.class, value))));
        CompiledTagModule tags = new CompiledTagModule();
        NativeTypeDecoders.register(manager, tags);
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("minecraft", "item"), ItemStack.class,
                value -> {
                    com.google.gson.JsonObject object = value.getAsJsonObject();
                    net.minecraft.item.Item item = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(
                            new ResourceLocation(object.get("item").getAsString()));
                    if (item == null) throw new JsonDeserializeException(value, "Unknown item");
                    int count = object.has("count") ? object.get("count").getAsInt() : 1;
                    return new ItemStack(item, count);
                }));
        DataRules rules = new DataRules(new ContentCatalog(null), tags, (id, context) -> { throw new AssertionError("Unexpected function " + id); });
        rules.registerDecoders(manager);
        CompiledLootModule.registerConditions();
        rules.registerNative();
        distributionsAndLevelValuesCompose(manager);
        NBTTagCompound inputs = new NBTTagCompound(); inputs.setInteger("amount", 9);
        NBTTagCompound payload = new NBTTagCompound(); payload.setString("name", "bound"); inputs.setTag("payload", payload);
        DataContext context = new DataContext(null, null, null, new Random(17), 3).withInputs(inputs)
                .withDamageSource(new DamageSource("contract").setFireDamage()).withTool(new ItemStack(Items.DIAMOND_PICKAXE));
        inputs.setInteger("amount", -1);
        context.getInputs().setInteger("amount", -2);
        contextSurvivesNativeRules(manager, context);
        modifierPreservesBookAndInputOwnership(manager, context);
        nativeLootTransformsCompose(manager, context);
        parityPredicatesAndModifiers(manager, context);
        legacyRuleDecodersStayComposable(tags, context);
        typedModifierDecodersCompose(manager, context);
        enchantmentAndAttributeCapabilities(manager, context);
        conditionalEffectsShortCircuit(manager, context);
        textChildrenUseSharedManager(manager);
        reject(() -> manager.decode(IntProvider.class, json("{\"type\":\"minecraft:uniform\",\"min_inclusive\":1.5,\"max_inclusive\":2}")));
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:damage_source\",\"fire\":true,\"ignored\":true}")));
        reject(() -> manager.decode(NativeTypeDecoders.ItemMatcher.class, json("{\"items\":[\"contract:missing_item\"]}")));
        System.out.println("DataCompositionContractTest passed");
    }

    private static void distributionsAndLevelValuesCompose(DeserializerManager manager) throws Exception {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "integer"), IntProvider.class,
                value -> new IntProvider(7, 7, random -> 7)));
        IntProvider sample = manager.decode(IntProvider.class, json("{\"type\":\"minecraft:weighted_list\",\"distribution\":[{\"data\":{\"type\":\"minecraft:clamped\",\"source\":{\"type\":\"contract:integer\"},\"min_inclusive\":2,\"max_inclusive\":5}}]}"));
        check(sample.sample(new Random(9)) == 5 && sample.getMinValue() == 5 && sample.getMaxValue() == 5,
                "Custom integer distribution must survive weighted selection and clamp bounds");
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "level"), LevelBasedValue.class,
                value -> level -> level + 1));
        LevelBasedValue value = manager.decode(LevelBasedValue.class, json("{\"type\":\"minecraft:lookup\",\"values\":[2],\"fallback\":{\"type\":\"minecraft:fraction\",\"numerator\":{\"type\":\"minecraft:exponent\",\"base\":{\"type\":\"contract:level\"},\"power\":2},\"denominator\":2}}"));
        check(value.calculate(1) == 2 && value.calculate(3) == 8, "Level lookup fallback must retain nested custom formulas");
        reject(() -> manager.decode(IntProvider.class, json("{\"type\":\"minecraft:clamped\",\"source\":1,\"min_inclusive\":5,\"max_inclusive\":2}")));
    }

    private static void contextSurvivesNativeRules(DeserializerManager manager, DataContext context) throws Exception {
        Gson gson = new GsonBuilder().registerTypeHierarchyAdapter(LootCondition.class, new LootConditionManager.Serializer())
                .registerTypeHierarchyAdapter(LootFunction.class, new LootFunctionManager.Serializer()).create();
        LootCondition condition = gson.fromJson("{\"condition\":\"rml:expression\",\"expression\":{\"type\":\"rml:all_of\",\"terms\":["
                + "{\"type\":\"rml:damage_source\",\"fire\":true,\"projectile\":false},"
                + "{\"type\":\"rml:match_tool\",\"predicate\":{\"items\":[\"minecraft:diamond_pickaxe\"]}},"
                + "{\"type\":\"rml:compare\",\"operator\":\"=\",\"left\":{\"type\":\"rml:input\",\"path\":\"amount\"},\"right\":9},"
                + "{\"type\":\"rml:level\",\"min\":3,\"max\":3}]}}", LootCondition.class);
        check(condition.testCondition(context.getRandom(), context.withEntity(null).withLevel(3).asLootContext()),
                "Native predicate evaluation must retain damage, tool, detached inputs and effect level");
        DataContext restored = DataContext.fromLoot(context.asLootContext(), context.getRandom());
        check(restored.getOrigin() == null, "Native adaptation must not fabricate an origin");
        LootFunction function = gson.fromJson("{\"function\":\"rml:expression\",\"expression\":{\"type\":\"rml:set_count\",\"count\":{\"type\":\"rml:add\",\"inputs\":[{\"type\":\"rml:input\",\"path\":\"amount\"},{\"type\":\"rml:context_level\"}]}}}", LootFunction.class);
        ItemStack original = new ItemStack(Items.APPLE, 2);
        check(function.apply(original, context.getRandom(), context.asLootContext()).getCount() == 12 && original.getCount() == 2,
                "Native modifier must compute with preserved context without mutating its input");
    }

    private static void modifierPreservesBookAndInputOwnership(DeserializerManager manager, DataContext context) throws Exception {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        Map<net.minecraft.enchantment.Enchantment, Integer> levels = new LinkedHashMap<>();
        levels.put(Enchantments.FORTUNE, 4); levels.put(Enchantments.UNBREAKING, 1);
        EnchantmentHelper.setEnchantments(levels, book);
        DataRules.Modifier remove = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:set_enchantments\",\"enchantments\":{\"minecraft:fortune\":0}}"));
        ItemStack changed = remove.apply(book, context);
        check(!EnchantmentHelper.getEnchantments(changed).containsKey(Enchantments.FORTUNE)
                && EnchantmentHelper.getEnchantments(changed).get(Enchantments.UNBREAKING) == 1
                && EnchantmentHelper.getEnchantments(book).get(Enchantments.FORTUNE) == 4,
                "Removing one stored enchantment must preserve others and the original book");
        DataRules.Modifier copy = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:copy_nbt\",\"source\":\"inputs\",\"path\":\"payload\",\"target\":\"custom\"}"));
        ItemStack result = copy.apply(new ItemStack(Items.APPLE), context);
        check("bound".equals(result.getTagCompound().getCompoundTag("custom").getString("name")), "Input payload must reach item NBT");
        result.getTagCompound().getCompoundTag("custom").setString("name", "changed");
        check("bound".equals(context.getInputs().getCompoundTag("payload").getString("name")), "Item writes must not alias validated inputs");
        FunctionDataPath.Compiled destination = FunctionDataPath.compile("nested[0].value");
        NBTTagCompound root = new NBTTagCompound(); destination.set(root, new net.minecraft.nbt.NBTTagInt(5));
        check(((net.minecraft.nbt.NBTPrimitive) destination.get(root)).getInt() == 5, "Compiled writes must create typed compound/list parents");
        net.minecraft.nbt.NBTTagList integers = new net.minecraft.nbt.NBTTagList();
        integers.appendTag(new net.minecraft.nbt.NBTTagInt(2)); root.setTag("numbers", integers);
        reject(() -> FunctionDataPath.compile("numbers[0]").set(root, new net.minecraft.nbt.NBTTagString("invalid")));
        check(root.getTagList("numbers", 3).getIntAt(0) == 2, "A rejected heterogeneous list write must preserve its native value");
        DataRules.Modifier skipped = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:reference\",\"id\":\"contract:absent\",\"conditions\":[{\"type\":\"rml:any_of\",\"terms\":[]}]}"));
        check(skipped.apply(book, context) == book, "A false modifier condition must skip named lookup and preserve the original stack");
    }

    private static void nativeLootTransformsCompose(DeserializerManager manager, DataContext context) throws Exception {
        DataRules.Modifier smelt = manager.decode(DataRules.Modifier.class, json("{\"type\":\"minecraft:furnace_smelt\"}"));
        ItemStack ore = new ItemStack(Blocks.IRON_ORE, 3);
        ItemStack ingot = smelt.apply(ore, context);
        check(ingot.getItem() == Items.IRON_INGOT && ingot.getCount() == 3 && ore.getItem() == Item.getItemFromBlock(Blocks.IRON_ORE),
                "Smelting must read the native furnace table, keep the stack count, and leave the input untouched");
        DataRules.Modifier randomize = manager.decode(DataRules.Modifier.class,
                json("{\"type\":\"minecraft:enchant_randomly\",\"enchantments\":[\"minecraft:sharpness\",\"minecraft:unbreaking\"]}"));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        Map<Enchantment, Integer> rolled = EnchantmentHelper.getEnchantments(randomize.apply(sword, context));
        check(rolled.size() == 1 && (rolled.containsKey(Enchantments.SHARPNESS) || rolled.containsKey(Enchantments.UNBREAKING))
                && EnchantmentHelper.getEnchantments(sword).isEmpty(),
                "Random enchanting must pick exactly one applicable allowed enchantment without mutating the input");
        DataRules.Modifier invalidLevels = manager.decode(DataRules.Modifier.class,
                json("{\"type\":\"minecraft:enchant_with_levels\",\"levels\":0}"));
        reject(() -> invalidLevels.apply(new ItemStack(Items.DIAMOND_SWORD), context));
        DataRules.Modifier looting = manager.decode(DataRules.Modifier.class,
                json("{\"type\":\"minecraft:looting_enchant\",\"count\":{\"type\":\"minecraft:constant\",\"value\":2}}"));
        ItemStack drop = new ItemStack(Items.APPLE, 4);
        check(looting.apply(drop, context) == drop, "Looting must require an actual killer in the loot context");
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.addEnchantment(Enchantments.FORTUNE, 3);
        DataRules.Test fortune = manager.decode(DataRules.Test.class,
                json("{\"type\":\"rml:enchantment_level\",\"enchantment\":\"minecraft:fortune\",\"level\":{\"min\":3,\"max\":3}}"));
        check(fortune.test(context.withTool(pickaxe)) && !fortune.test(context), "Enchantment level predicates must read the actual tool");
        reject(() -> manager.decode(DataRules.Modifier.class, json("{\"type\":\"minecraft:furnace_smelt\",\"count\":1}")));
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:enchantment_level\",\"enchantment\":\"minecraft:absent\",\"level\":0}")));
    }

    private static void parityPredicatesAndModifiers(DeserializerManager manager, DataContext context) throws Exception {
        DataRules.Test properties = manager.decode(DataRules.Test.class, json("{\"type\":\"rml:entity_properties\",\"entity\":\"this\",\"predicate\":{\"entity_types\":[\"minecraft:zombie\"]},\"flags\":{\"on_fire\":true}}"));
        check(!properties.test(context), "Entity properties require an actual target entity");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:entity_properties\",\"entity\":\"absent\",\"predicate\":{\"entity_types\":[\"minecraft:zombie\"]}}")));
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:entity_properties\",\"predicate\":{\"entity_types\":[\"minecraft:zombie\"]},\"flags\":{\"swimming\":true}}")));
        DataRules.Test scores = manager.decode(DataRules.Test.class, json("{\"type\":\"rml:entity_scores\",\"entity\":\"this\",\"scores\":{\"kills\":{\"min\":1}}}"));
        check(!scores.test(context), "Entity scores require a server world");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:entity_scores\",\"entity\":\"this\",\"scores\":{}}")));
        DataRules.Test stateProperty = manager.decode(DataRules.Test.class,
                json("{\"type\":\"rml:block_state_property\",\"block\":\"minecraft:stone\",\"properties\":{\"variant\":\"granite\"}}"));
        check(!stateProperty.test(context), "Block state properties require a server world");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:block_state_property\",\"block\":\"minecraft:stone\",\"ignored\":true}")));
        DataRules.Test location = manager.decode(DataRules.Test.class,
                json("{\"type\":\"rml:location_check\",\"offset\":[0,1,0],\"block\":{\"blocks\":[\"minecraft:stone\"]},\"light\":{\"min\":0},\"biome\":\"minecraft:plains\"}"));
        check(!location.test(context), "Location checks require a server world");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:location_check\",\"ignored\":true}")));

        DataRules.Modifier copyName = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:copy_name\",\"source\":\"block_entity\"}"));
        ItemStack apple = new ItemStack(Items.APPLE);
        check(copyName.apply(apple, context) == apple, "Copying a block entity name without a world must preserve the item");
        reject(() -> manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:copy_name\",\"source\":\"absent\"}")));

        DataRules.Modifier fillHead = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:fill_player_head\",\"entity\":\"killer\"}"));
        ItemStack head = new ItemStack(Items.SKULL, 1, 3);
        check(fillHead.apply(head, context) == head, "Filling a player head without a killer must preserve the item");
        reject(() -> manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:fill_player_head\",\"entity\":\"absent\"}")));

        DataRules.Modifier contents = manager.decode(DataRules.Modifier.class,
                json("{\"type\":\"rml:set_contents\",\"items\":[{\"item\":\"minecraft:apple\",\"count\":2}],\"block_entity\":\"minecraft:chest\"}"));
        NBTTagCompound blockEntity = contents.apply(new ItemStack(Blocks.CHEST), context).getTagCompound().getCompoundTag("BlockEntityTag");
        check("minecraft:chest".equals(blockEntity.getString("id")) && blockEntity.getTagList("Items", 10).tagCount() == 1,
                "Container contents must be written as block entity items");
        check(blockEntity.getTagList("Items", 10).getCompoundTagAt(0).getByte("Slot") == 0
                        && "minecraft:apple".equals(blockEntity.getTagList("Items", 10).getCompoundTagAt(0).getString("id"))
                        && blockEntity.getTagList("Items", 10).getCompoundTagAt(0).getByte("Count") == 2,
                "Container slots must preserve their index and stack size");

        DataRules.Modifier loot = manager.decode(DataRules.Modifier.class,
                json("{\"type\":\"rml:set_loot_table\",\"name\":\"minecraft:chests/simple_dungeon\",\"seed\":7}"));
        NBTTagCompound lootTag = loot.apply(new ItemStack(Blocks.CHEST), context).getTagCompound().getCompoundTag("BlockEntityTag");
        check("minecraft:chests/simple_dungeon".equals(lootTag.getString("LootTable")) && lootTag.getLong("LootTableSeed") == 7,
                "Loot table references must be written with their seed");

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.addEnchantment(Enchantments.FORTUNE, 3);
        DataRules.NumberValue level = manager.decode(DataRules.NumberValue.class,
                json("{\"type\":\"rml:enchantment_level\",\"enchantment\":\"minecraft:fortune\"}"));
        check(level.evaluate(context.withTool(pickaxe)) == 3 && level.evaluate(context) == 0, "Tool enchantment levels must decode as numbers");
        reject(() -> manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:enchantment_level\",\"enchantment\":\"minecraft:absent\"}")));
    }

    private static void legacyRuleDecodersStayComposable(CompiledTagModule tags, DataContext context) throws Exception {
        DataRules legacy = new DataRules(new ContentCatalog(null), tags, (id, value) -> { throw new AssertionError("Unexpected function " + id); });
        check(legacy.number(json("5")).evaluate(context) == 5, "Legacy numbers must keep their constant form");
        check(legacy.number(json("{\"type\":\"level\"}")).evaluate(context.withLevel(4)) == 4, "Legacy level providers must read the effect level");
        check(legacy.number(json("{\"type\":\"uniform\",\"min\":2,\"max\":2}")).evaluate(context) == 2, "Legacy uniform providers must keep inclusive bounds");
        DataRules.Test level = legacy.predicate(json("{\"condition\":\"rml:level\",\"min\":3,\"max\":3}"));
        check(level.test(context.withLevel(3)) && !level.test(context.withLevel(4)), "Legacy level predicates must respect their range");
        check(legacy.predicate(json("{\"condition\":\"minecraft:random_chance\",\"chance\":1}")).test(context), "A certain legacy chance must pass");
        check(legacy.predicate(json("{\"condition\":\"rml:inverted\",\"term\":{\"condition\":\"minecraft:random_chance\",\"chance\":0}}")).test(context),
                "Legacy predicates must compose through inversion");
        check(legacy.predicate(json("{\"condition\":\"minecraft:killed_by_player\",\"inverse\":true}")).test(context),
                "An absent loot context must not count as a player kill");
        DataRules.Modifier count = legacy.modifier(json("{\"type\":\"minecraft:set_count\",\"count\":3}"));
        check(count.apply(new ItemStack(Items.APPLE, 1), context).getCount() == 3, "Legacy modifiers must read the function form");
        DataRules.Modifier sequence = legacy.modifier(json("[{\"function\":\"minecraft:set_count\",\"count\":2},{\"function\":\"minecraft:set_nbt\",\"tag\":\"{demo:1}\"}]"));
        ItemStack sequenced = sequence.apply(new ItemStack(Items.APPLE), context);
        check(sequenced.getCount() == 2 && sequenced.getTagCompound().getInteger("demo") == 1, "Legacy modifier arrays must apply in order");
        DataRules.Modifier components = legacy.modifier(json("{\"function\":\"minecraft:set_components\",\"components\":{\"minecraft:provides_trim_material\":\"minecraft:copper\"}}"));
        check("minecraft:copper".equals(components.apply(new ItemStack(Items.APPLE), context).getTagCompound().getString("rml_trim_material")),
                "Legacy trim components must lower to item NBT");
        DataRules.Effect conditional = legacy.effect(json("{\"type\":\"rml:conditional\",\"condition\":{\"condition\":\"rml:level\",\"min\":0,\"max\":10},\"on_true\":{\"type\":\"rml:heal\",\"amount\":1}}"));
        check(conditional != null, "Legacy effects must compose conditions and effects");
        reject(() -> legacy.number(json("{\"type\":\"absent\"}")));
        reject(() -> legacy.predicate(json("{\"condition\":\"rml:level\",\"ignored\":1}")));
        reject(() -> legacy.modifier(json("{\"function\":\"minecraft:set_count\",\"count\":1,\"ignored\":1}")));
        reject(() -> legacy.effect(json("{\"type\":\"rml:heal\",\"amount\":1,\"ignored\":1}")));
    }

    private static void typedModifierDecodersCompose(DeserializerManager manager, DataContext context) throws Exception {
        DataRules.Modifier count = manager.decode(DataRules.Modifier.class,
                json("{\"type\":\"rml:set_count\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":2,\"max\":2}}"));
        check(count.apply(new ItemStack(Items.APPLE), context).getCount() == 2, "Typed count modifiers must decode nested numeric providers");
        DataRules.Modifier nbt = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:set_nbt\",\"tag\":\"{demo:1}\"}"));
        check(nbt.apply(new ItemStack(Items.APPLE), context).getTagCompound().getInteger("demo") == 1, "Typed NBT modifiers must merge their tag");
        DataRules.Modifier components = manager.decode(DataRules.Modifier.class,
                json("{\"type\":\"minecraft:set_components\",\"components\":{\"minecraft:provides_trim_material\":\"minecraft:gold\"}}"));
        check("minecraft:gold".equals(components.apply(new ItemStack(Items.APPLE), context).getTagCompound().getString("rml_trim_material")),
                "Typed trim components must lower to item NBT");
        DataRules.Modifier missing = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:reference\",\"id\":\"contract:absent\"}"));
        check(missing != null, "Named modifier references must stay unresolved until evaluation");
    }

    private static void enchantmentAndAttributeCapabilities(DeserializerManager manager, DataContext context) throws Exception {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.addEnchantment(Enchantments.FORTUNE, 3);
        pickaxe.addEnchantment(Enchantments.UNBREAKING, 1);
        DataContext tool = context.withTool(pickaxe);
        DataRules.Test enchanted = manager.decode(DataRules.Test.class,
                json("{\"type\":\"rml:enchantments\",\"enchantments\":{\"minecraft:fortune\":{\"min\":3},\"minecraft:unbreaking\":{\"min\":1}}}"));
        check(enchanted.test(tool) && !enchanted.test(context), "Enchantment predicates must read every listed level");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:enchantments\",\"enchantments\":{}}")));
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:enchantments\",\"enchantments\":{\"minecraft:absent\":{\"min\":1}}}")));

        DataRules.Modifier attributes = manager.decode(DataRules.Modifier.class,
                json("{\"type\":\"rml:set_attributes\",\"modifiers\":[{\"attribute\":\"generic.attackDamage\",\"name\":\"demo:bonus\",\"amount\":2,\"operation\":\"addition\",\"slot\":\"mainhand\"}]}"));
        NBTTagList list = attributes.apply(new ItemStack(Items.APPLE), context).getTagCompound().getTagList("AttributeModifiers", 10);
        check(list.tagCount() == 1, "Attribute modifiers must be written as a typed list");
        NBTTagCompound entry = list.getCompoundTagAt(0);
        check("generic.attackDamage".equals(entry.getString("AttributeName")) && "demo:bonus".equals(entry.getString("Name"))
                        && entry.getDouble("Amount") == 2 && entry.getInteger("Operation") == 0 && "mainhand".equals(entry.getString("Slot"))
                        && (entry.getLong("UUIDMost") != 0 || entry.getLong("UUIDLeast") != 0),
                "Attribute modifiers must keep their name, amount, operation, slot and UUID");
        reject(() -> manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:set_attributes\",\"modifiers\":[{\"attribute\":\"generic.attackDamage\",\"name\":\"demo:bonus\",\"amount\":2,\"operation\":\"absent\"}]}")));
        reject(() -> manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:set_attributes\",\"modifiers\":[{\"attribute\":\"generic.attackDamage\",\"name\":\"demo:bonus\",\"amount\":2,\"operation\":\"addition\",\"slot\":\"absent\"}]}")));

        ItemStack counted = new ItemStack(Items.DIAMOND_PICKAXE, 2, 5);
        DataContext countedContext = context.withTool(counted);
        DataRules.NumberValue itemCount = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:item_count\"}"));
        DataRules.NumberValue itemDamage = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:item_damage\"}"));
        check(itemCount.evaluate(countedContext) == 2 && itemDamage.evaluate(countedContext) == 5, "Tool count and damage must decode as numbers");
        DataRules.NumberValue health = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:entity_health\",\"fallback\":7}"));
        DataRules.NumberValue light = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:block_light\",\"fallback\":4,\"offset\":[0,1,0]}"));
        check(health.evaluate(context) == 7 && light.evaluate(context) == 4, "Missing entity or world must use the declared fallback");

        DataRules.Modifier metadata = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:set_data\",\"data\":3}"));
        check(metadata.apply(new ItemStack(Items.APPLE), context).getItemDamage() == 3, "Metadata modifiers must set the item damage value");
        DataRules.Modifier repair = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:set_repair_cost\",\"cost\":7}"));
        check(repair.apply(new ItemStack(Items.APPLE), context).getRepairCost() == 7, "Repair cost modifiers must set the anvil cost");
        DataRules.Modifier unbreakable = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:set_unbreakable\"}"));
        check(unbreakable.apply(new ItemStack(Items.DIAMOND_PICKAXE), context).getTagCompound().getBoolean("Unbreakable"),
                "Unbreakable modifiers must write the native flag");
        DataRules.Modifier breakable = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:set_unbreakable\",\"value\":false}"));
        check(!breakable.apply(new ItemStack(Items.DIAMOND_PICKAXE), context).getTagCompound().getBoolean("Unbreakable"),
                "Unbreakable modifiers must be able to clear the flag");
        ItemStack flagged = unbreakable.apply(new ItemStack(Items.DIAMOND_PICKAXE), context);
        NativeTypeDecoders.ItemMatcher flag = manager.decode(NativeTypeDecoders.ItemMatcher.class,
                json("{\"type\":\"rml:item_match\",\"items\":[\"minecraft:diamond_pickaxe\"],\"unbreakable\":true}"));
        check(flag.test(flagged, context) && !flag.test(new ItemStack(Items.DIAMOND_PICKAXE), context),
                "Item matchers must read the unbreakable flag");
        ItemStack costly = repair.apply(new ItemStack(Items.APPLE), context);
        NativeTypeDecoders.ItemMatcher cost = manager.decode(NativeTypeDecoders.ItemMatcher.class,
                json("{\"type\":\"rml:item_match\",\"items\":[\"minecraft:apple\"],\"repair_cost\":{\"min\":7}}"));
        check(cost.test(costly, context), "Item matchers must read the repair cost");
        NativeTypeDecoders.ItemMatcher stackSize = manager.decode(NativeTypeDecoders.ItemMatcher.class,
                json("{\"type\":\"rml:item_match\",\"items\":[\"minecraft:apple\"],\"max_stack_size\":{\"min\":64}}"));
        check(stackSize.test(new ItemStack(Items.APPLE), context), "Item matchers must read the maximum stack size");
        ItemStack tagged = new ItemStack(Items.APPLE);
        tagged.setTagCompound(new NBTTagCompound());
        tagged.getTagCompound().setInteger("demo", 1);
        tagged.getTagCompound().setInteger("extra", 2);
        NativeTypeDecoders.ItemMatcher subset = manager.decode(NativeTypeDecoders.ItemMatcher.class,
                json("{\"type\":\"rml:item_match\",\"items\":[\"minecraft:apple\"],\"nbt\":{\"demo\":1}}"));
        NativeTypeDecoders.ItemMatcher exact = manager.decode(NativeTypeDecoders.ItemMatcher.class,
                json("{\"type\":\"rml:item_match\",\"items\":[\"minecraft:apple\"],\"nbt\":{\"demo\":1},\"nbt_exact\":true}"));
        check(subset.test(tagged, context) && !exact.test(tagged, context), "nbt_exact must reject additional tags");
        DataRules.NumberValue repairCost = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:item_repair_cost\"}"));
        DataRules.NumberValue maxStack = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:item_max_stack_size\"}"));
        check(repairCost.evaluate(context.withTool(costly)) == 7 && maxStack.evaluate(context.withTool(new ItemStack(Items.APPLE))) == 64,
                "Item state readers must match the actual stack");

        NativeTypeDecoders.EntityMatcher equipped = manager.decode(NativeTypeDecoders.EntityMatcher.class,
                json("{\"type\":\"rml:entity_match\",\"entity_types\":[\"minecraft:zombie\"],\"equipment\":{\"mainhand\":{\"items\":[\"minecraft:diamond_sword\"]}}}"));
        check(equipped != null, "Equipment matchers must decode");
        reject(() -> manager.decode(NativeTypeDecoders.EntityMatcher.class, json("{\"type\":\"rml:entity_match\",\"entity_types\":[\"minecraft:zombie\"],\"equipment\":{\"absent\":{}}}")));

        ItemStack enchantedSword = new ItemStack(Items.DIAMOND_SWORD);
        enchantedSword.addEnchantment(Enchantments.SHARPNESS, 5);
        NativeTypeDecoders.ItemMatcher enchantedItem = manager.decode(NativeTypeDecoders.ItemMatcher.class,
                json("{\"type\":\"rml:item_match\",\"items\":[\"minecraft:diamond_sword\"],\"enchantments\":{\"minecraft:sharpness\":{\"min\":5}}}"));
        check(enchantedItem.test(enchantedSword, context) && !enchantedItem.test(new ItemStack(Items.DIAMOND_SWORD), context),
                "Item matchers must read listed enchantment levels");
        DataRules.Test held = manager.decode(DataRules.Test.class, json("{\"type\":\"rml:held_item\",\"predicate\":{\"items\":[\"minecraft:diamond_sword\"]}}"));
        check(!held.test(context), "Held item checks require a living entity");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:held_item\",\"predicate\":{},\"hand\":\"absent\"}")));

        ItemStack durable = new ItemStack(Items.DIAMOND_PICKAXE);
        durable.setItemDamage(10);
        DataContext durableContext = context.withTool(durable);
        DataRules.NumberValue durability = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:item_durability\"}"));
        DataRules.NumberValue maxDamage = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:item_max_damage\"}"));
        check(maxDamage.evaluate(durableContext) == durable.getMaxDamage() && durability.evaluate(durableContext) == durable.getMaxDamage() - 10,
                "Durability readers must match the actual tool");
        NativeTypeDecoders.ItemMatcher durableMatch = manager.decode(NativeTypeDecoders.ItemMatcher.class,
                json("{\"type\":\"rml:item_match\",\"items\":[\"minecraft:diamond_pickaxe\"],\"durability\":{\"min\":" + (durable.getMaxDamage() - 10) + "},\"max_damage\":{\"min\":" + durable.getMaxDamage() + "}}"));
        check(durableMatch.test(durable, context), "Item matchers must read remaining and maximum durability");
        NativeTypeDecoders.ItemMatcher worn = manager.decode(NativeTypeDecoders.ItemMatcher.class,
                json("{\"type\":\"rml:item_match\",\"items\":[\"minecraft:diamond_pickaxe\"],\"durability\":{\"max\":1}}"));
        check(!worn.test(durable, context), "Durability ranges must reject mismatched tools");

        NativeTypeDecoders.EntityMatcher vitals = manager.decode(NativeTypeDecoders.EntityMatcher.class,
                json("{\"type\":\"rml:entity_match\",\"entity_types\":[\"minecraft:zombie\"],\"max_health\":{\"min\":20},\"absorption\":{\"min\":0}}"));
        check(vitals != null, "Entity matchers must decode health and absorption ranges");
        DataRules.NumberValue maxHealth = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:entity_max_health\",\"fallback\":20}"));
        check(maxHealth.evaluate(context) == 20, "Missing entities must use the max-health fallback");
        DataRules.NumberValue absorption = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:entity_absorption\",\"fallback\":1.5}"));
        DataRules.NumberValue dimension = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:dimension\",\"fallback\":-1}"));
        DataRules.NumberValue hardness = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:block_hardness\",\"fallback\":2.5}"));
        check(absorption.evaluate(context) == 1.5 && dimension.evaluate(context) == -1 && hardness.evaluate(context) == 2.5,
                "Absorption, dimension and hardness readers must use their fallbacks without a world or entity");
        DataRules.NumberValue food = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:player_food\",\"fallback\":12}"));
        DataRules.NumberValue saturation = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:player_saturation\",\"fallback\":3.5}"));
        DataRules.NumberValue experience = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:player_xp_level\",\"fallback\":9}"));
        DataRules.NumberValue progress = manager.decode(DataRules.NumberValue.class, json("{\"type\":\"rml:player_xp_progress\",\"fallback\":0.25}"));
        check(food.evaluate(context) == 12 && saturation.evaluate(context) == 3.5
                        && experience.evaluate(context) == 9 && progress.evaluate(context) == 0.25,
                "Player readers must use their fallbacks without a player entity");

        DataRules.Test fluid = manager.decode(DataRules.Test.class, json("{\"type\":\"rml:location_check\",\"fluid\":\"water\"}"));
        check(!fluid.test(context), "Fluid checks require a server world");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:location_check\",\"fluid\":\"oil\"}")));
        DataRules.Test position = manager.decode(DataRules.Test.class,
                json("{\"type\":\"rml:location_check\",\"position\":{\"x\":{\"min\":0},\"y\":{\"min\":0},\"z\":{\"min\":0}}}"));
        check(!position.test(context), "Position checks require a server world");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:location_check\",\"position\":{\"w\":1}}")));
        DataRules.Test team = manager.decode(DataRules.Test.class, json("{\"type\":\"rml:entity_team\",\"team\":\"red\"}"));
        check(!team.test(context), "Team checks require an actual entity");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:entity_team\"}")));
        DataRules.Test riding = manager.decode(DataRules.Test.class, json("{\"type\":\"rml:entity_riding\",\"vehicle\":\"minecraft:boat\"}"));
        check(!riding.test(context), "Riding checks require an actual entity");
        reject(() -> manager.decode(DataRules.Test.class, json("{\"type\":\"rml:entity_riding\",\"vehicle\":[]}")));
        DataRules.Test always = manager.decode(DataRules.Test.class, json("{\"type\":\"rml:always\"}"));
        DataRules.Test never = manager.decode(DataRules.Test.class, json("{\"type\":\"rml:never\"}"));
        check(always.test(context) && !never.test(context), "Constant predicates must decode to fixed results");
        ItemStack untouched = new ItemStack(Items.APPLE);
        DataRules.Modifier identity = manager.decode(DataRules.Modifier.class, json("{\"type\":\"rml:identity\"}"));
        check(identity.apply(untouched, context) == untouched, "Identity modifiers must return the same stack");
        manager.decode(DataRules.Effect.class, json("{\"type\":\"rml:noop\"}")).apply(context, null);
    }

    private static void conditionalEffectsShortCircuit(DeserializerManager manager, DataContext context) throws Exception {
        int[] total = {0};
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "accumulate"), DataRules.Effect.class,
                value -> (data, target) -> total[0] += data.getLevel()));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "unreachable"), DataRules.Effect.class,
                value -> (data, target) -> { throw new AssertionError("Unselected effect executed"); }));
        DataRules.Effect effect = manager.decode(DataRules.Effect.class, json("{\"type\":\"rml:sequence\",\"effects\":["
                + "{\"type\":\"rml:conditional\",\"condition\":{\"type\":\"rml:damage_source\",\"fire\":true},\"on_true\":{\"type\":\"contract:accumulate\"},\"on_false\":{\"type\":\"contract:unreachable\"}},"
                + "{\"type\":\"rml:weighted\",\"distribution\":[{\"data\":{\"type\":\"contract:accumulate\"}}]}]}"));
        effect.apply(context, null);
        check(total[0] == 6, "Sequence and weighted consumers must execute custom child effects exactly once");
    }

    private static void textChildrenUseSharedManager(DeserializerManager manager) throws Exception {
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("contract", "text"), ITextComponent.class,
                value -> new TextComponentString("custom")));
        ITextComponent text = manager.decode(ITextComponent.class, json("{\"text\":\"parent\",\"extra\":[{\"type\":\"contract:text\"}],\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"type\":\"contract:text\"}}}"));
        check("parentcustom".equals(text.getUnformattedText())
                && "custom".equals(text.getStyle().getHoverEvent().getValue().getUnformattedText()),
                "Native text siblings and hover contents must retain custom decoder children");
    }

    private static JsonElement json(String text) { return JSON.parse(text); }
    private interface Checked { void run() throws Exception; }
    private static void reject(Checked action) throws Exception {
        try { action.run(); } catch (JsonDeserializeException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Malformed definition was accepted");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
