package com.raidminer.helper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.raidminer.helper.RaidMinerHelperClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-конфиг. При первом запуске создаётся автоматически в config/raidminer_helper_gui.json.
 */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean obsEnabled = true;
    public String obsHost = "127.0.0.1";
    public int obsPort = 4455;
    public String obsPassword = "";

    public int recentPlayersLimit = 15;

    public boolean cleanupEnabled = true;
    public int screenshotRetentionDays = 30;
    public String screenshotFolder = "moderation_screenshots";

    public String checkCommandTemplate = "/check {nick}";
    public String removeCheckCommandTemplate = "/checkoff {nick}";
    public String checkTellText = "Здравствуйте, проверка на читы. В течении 5 минут жду ваш Anydesk (наилучший вариант, скачать можно в любом браузере)/Discord. Также сообщаю, что в случае признания на наличие чит-клиентов срок бана составит 20 дней, вместо 30.";

    public Map<String, List<String>> quickReasons = new LinkedHashMap<>();

    public ModConfig() {
        quickReasons.put("mute", new ArrayList<>(List.of("2.2", "2.3", "2.4", "2.5", "2.6", "2.7", "2.8", "2.9", "2.10", "2.11", "2.12", "2.13", "2.14", "2.15")));
        quickReasons.put("ban", new ArrayList<>(List.of("2.2", "2.3", "2.6", "2.7", "некорректный ник", "4.1")));
        quickReasons.put("ipban", new ArrayList<>(List.of("бот", "уход от проверки", "время вышло", "признание", "3.3", "3.6", "3.7", "3.8", "3.9", "3.10")));
    }

    public static ModConfig load(Path path) {
        try {
            if (Files.notExists(path)) {
                ModConfig cfg = new ModConfig();
                cfg.save(path);
                return cfg;
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
                return cfg == null ? new ModConfig() : cfg;
            }
        } catch (Exception e) {
            RaidMinerHelperClient.LOGGER.error("Cannot load config, default values will be used", e);
            return new ModConfig();
        }
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            RaidMinerHelperClient.LOGGER.error("Cannot save config", e);
        }
    }
}
