package rml.loader.deserialize;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.ResourceLocation;
import rml.deserializer.AbstractDeserializer;
import rml.loader.api.annotations.BeDiscovered;
import rml.loader.api.utils.RandomHolder;
import rml.loader.api.utils.values.RandomIntSupplier;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/7/14 15:11
 **/
@BeDiscovered(BeDiscovered.PRE_INIT)
public class RMLDeserializer {

    public static final AbstractDeserializer<IntArrayList> INT_ARRAY_LIST = Deserializer.MANAGER.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("iu", "ints"), IntArrayList.class, jsonElement -> new IntArrayList(Lists.newArrayList(Deserializer.decode(Integer[].class, jsonElement)))));

    public static final AbstractDeserializer<Integer> RANDOM_INT = Deserializer.named(Integer.class, new ResourceLocation("rml", "random_int"))
            .require(RandomIntSupplier.class, "random")
            .decode(context -> context.get(RandomIntSupplier.class, "random").get(RandomHolder.RANDOM))
            .build();
}
