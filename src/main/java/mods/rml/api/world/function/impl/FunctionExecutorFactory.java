package mods.rml.api.world.function.impl;

import com.google.gson.JsonObject;
import mods.rml.api.world.function.FunctionExecutor;

import java.util.function.Function;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/6/8 16:47
 **/
public class FunctionExecutorFactory extends mods.rml.api.world.function.FunctionExecutorFactory {
    public Function<JsonObject, FunctionExecutor> function;
    public FunctionExecutorFactory(Function<JsonObject, FunctionExecutor> function){
        this.function = function;
    }
    @Override
    public FunctionExecutor apply(JsonObject jsonObject) {
        return this.function.apply(jsonObject);
    }
}
