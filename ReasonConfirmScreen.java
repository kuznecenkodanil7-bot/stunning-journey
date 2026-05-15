package com.raidminer.helper.storage;

import com.raidminer.helper.RaidMinerHelperClient;
import com.raidminer.helper.config.ModConfig;
import com.raidminer.helper.rules.PunishmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Версия для Minecraft 1.21.5: ScreenshotRecorder.takeScreenshot возвращает NativeImage сразу. */
public final class ScreenshotService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
    private static final Set<String> SKIP_MARKERS = Set.of("tick speed", "reach", "fighting suspiciously", "block interaction");

    private final ModConfig config;
    private final Path root;

    public ScreenshotService(ModConfig config) {
        this.config = config;
        this.root = FabricLoader.getInstance().getGameDir().resolve(config.screenshotFolder);
    }

    public void createBaseFolders() {
        try {
            Files.createDirectories(root.resolve("temp"));
            Files.createDirectories(root.resolve("warn"));
            Files.createDirectories(root.resolve("mute"));
            Files.createDirectories(root.resolve("ban"));
            Files.createDirectories(root.resolve("ipban"));
        } catch (IOException e) {
            RaidMinerHelperClient.LOGGER.error("Cannot create screenshot folders", e);
        }
    }

    public boolean shouldSkipScreenshot(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return SKIP_MARKERS.stream().anyMatch(lower::contains);
    }

    public PendingScreenshot captureTemp(String nick) {
        createBaseFolders();
        String datetime = LocalDateTime.now().format(FILE_TIME);
        Path file = root.resolve("temp").resolve(safe(nick + "_" + datetime + ".png"));
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
            image.writeTo(file);
            image.close();
            return new PendingScreenshot(file, datetime);
        } catch (Exception e) {
            RaidMinerHelperClient.LOGGER.error("Cannot capture screenshot", e);
            RaidMinerHelperClient.sendLocalMessage("Не удалось сохранить скриншот.");
            return null;
        }
    }

    public void finalizeScreenshot(PendingScreenshot pending, String nick, PunishmentType type, String duration, String reason) {
        if (pending == null || pending.consumed()) {
            return;
        }
        try {
            Files.createDirectories(root.resolve(type.folderName()));
            String name = safe(String.join("_",
                    nick,
                    type.commandName(),
                    emptyToNone(duration),
                    emptyToNone(reason),
                    pending.datetime()) + ".png");
            Path target = unique(root.resolve(type.folderName()).resolve(name));
            Files.move(pending.path(), target);
            pending.markConsumed();
        } catch (Exception e) {
            RaidMinerHelperClient.LOGGER.error("Cannot finalize screenshot", e);
        }
    }

    public void discard(PendingScreenshot pending) {
        if (pending == null || pending.consumed()) {
            return;
        }
        try {
            Files.deleteIfExists(pending.path());
            pending.markConsumed();
        } catch (IOException e) {
            RaidMinerHelperClient.LOGGER.warn("Cannot delete unused temp screenshot", e);
        }
    }

    public void cleanupOldScreenshots() {
        if (!config.cleanupEnabled) {
            return;
        }
        createBaseFolders();
        Instant border = Instant.now().minusSeconds(Math.max(1, config.screenshotRetentionDays) * 24L * 60L * 60L);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(border);
                        } catch (IOException ignored) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            RaidMinerHelperClient.LOGGER.warn("Cannot delete old screenshot {}", path, e);
                        }
                    });
        } catch (IOException e) {
            RaidMinerHelperClient.LOGGER.warn("Screenshot cleanup failed", e);
        }
    }

    private static String emptyToNone(String value) {
        return value == null || value.isBlank() ? "no_duration" : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ._-]+", "_");
    }

    private static Path unique(Path target) {
        if (Files.notExists(target)) {
            return target;
        }
        String fileName = target.getFileName().toString();
        String base = fileName.replaceFirst("\\.png$", "");
        Path parent = target.getParent();
        for (int i = 1; i < 1000; i++) {
            Path candidate = parent.resolve(base + "_" + i + ".png");
            if (Files.notExists(candidate)) {
                return candidate;
            }
        }
        return target;
    }
}
