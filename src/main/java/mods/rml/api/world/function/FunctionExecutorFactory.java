package mods.rml.api.world.function;

import com.google.gson.JsonObject;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.function.Function;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/5/25 16:30
 **/
public abstract class FunctionExecutorFactory extends IForgeRegistryEntry.Impl<FunctionExecutorFactory> implements Function<JsonObject, FunctionExecutor> { }

