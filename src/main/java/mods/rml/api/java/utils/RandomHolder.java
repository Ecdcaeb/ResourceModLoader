package mods.rml.api.java.utils;

import mods.rml.api.announces.EarlyClass;
import mods.rml.api.announces.PublicAPI;

import java.util.Random;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/4/3 18:27
 **/
@EarlyClass
@PublicAPI
public class RandomHolder {
    public static final Random RANDOM = new Random();
}
