package rml.loader.api.utils;

import rml.loader.api.annotations.EarlyClass;
import rml.loader.api.annotations.PublicAPI;

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
