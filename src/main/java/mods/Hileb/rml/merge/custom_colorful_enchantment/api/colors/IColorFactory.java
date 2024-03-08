package com.Hileb.custom_colorful_enchantment.api.colors;

import com.google.gson.JsonObject;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 19:56
 **/
public interface IColorFactory {
    IColorInstance getInstance(JsonObject var1);
}
