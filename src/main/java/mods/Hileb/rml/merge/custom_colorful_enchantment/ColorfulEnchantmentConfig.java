package com.Hileb.custom_colorful_enchantment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @Project Custom-Colorful-Enchantment
 * @Author Hileb
 * @Date 2023/10/2 19:59
 **/
public class ColorfulEnchantmentConfig {
    public static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
    public static Config instance = new Config();

    public ColorfulEnchantmentConfig() {
    }

    public static void initConfig() {
        File gameRunRoot= Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.GAMEDIR.get()).get().toFile();
        File config = new File(gameRunRoot, "config");
        if (!config.exists()) {
            config.mkdir();
        }

        File file = new File(config, "custom_colorful_enchantment_cfg.json");
        if (!file.exists()) {
            try {
                file.createNewFile();
                PrintWriter pw = new PrintWriter(file, "UTF-8");
                pw.println(GSON.toJson(instance));
                pw.close();
            } catch (IOException var5) {
                throw new RuntimeException(var5);
            }
        }

        try {
            Config cConfig = GSON.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), Config.class);
            instance = cConfig;
        } catch (IOException var4) {
            throw new RuntimeException(var4);
        }
    }

    public static class Config {
        public List<JsonObject> colors = new ArrayList();

        public Config() {
        }
    }
}
