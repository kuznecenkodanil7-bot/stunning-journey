package com.raidminer.helper;

import com.raidminer.helper.config.ModConfig;
import com.raidminer.helper.obs.ObsWebSocketClient;
import com.raidminer.helper.rules.PunishmentRule;
import com.raidminer.helper.rules.PunishmentType;
import com.raidminer.helper.screen.PunishmentScreen;
import com.raidminer.helper.screen.StatsScreen;
import com.raidminer.helper.storage.ChatMessageMemory;
import com.raidminer.helper.storage.PendingScreenshot;
import com.raidminer.helper.storage.ScreenshotService;
import com.raidminer.helper.storage.SessionData;
import com.raidminer.helper.util.NickParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Главная точка входа клиентского Fabric-мода.
 * Вся логика намеренно хранится на клиенте: команды отправляются как обычные команды игрока.
 */
public final class RaidMinerHelperClient implements ClientModInitializer {
    public static final String MOD_ID = "raidminer_helper_gui";
    public static final Logger LOGGER = LoggerFactory.getLogger("RaidMiner Helper Gui");

    private static final String KEY_CATEGORY = "key.categories.raidminer_helper_gui";

    private static ModConfig config;
    private static SessionData sessionData;
    private static ScreenshotService screenshotService;
    private static ObsWebSocketClient obsClient;
    private static KeyBinding openPanelKey;
    private static KeyBinding stopObsKey;

    private static long recordingStartedAtMillis = -1L;
    private static int lastOverlaySecond = -1;

    @Override
    public void onInitializeClient() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("raidminer_helper_gui.json");
        config = ModConfig.load(configPath);
        sessionData = new SessionData(config.recentPlayersLimit);
        screenshotService = new ScreenshotService(config);
        obsClient = new ObsWebSocketClient(config);

        screenshotService.createBaseFolders();
        screenshotService.cleanupOldScreenshots();

        openPanelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.raidminer_helper_gui.open_panel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KEY_CATEGORY
        ));

        stopObsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.raidminer_helper_gui.stop_obs",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(RaidMinerHelperClient::onClientTick);
        LOGGER.info("RaidMiner Helper Gui initialized");
    }

    private static void onClientTick(MinecraftClient client) {
        while (openPanelKey.wasPressed()) {
            if (client.currentScreen instanceof ChatScreen) {
                continue;
            }
            client.setScreen(new StatsScreen());
        }

        while (stopObsKey.wasPressed()) {
            // Важно: если открыт чат, G должен печататься в чат, а не останавливать OBS.
            if (client.currentScreen instanceof ChatScreen) {
                continue;
            }
            stopObsRecording("остановлено клавишей G");
        }

        updateRecordingOverlay(client);
    }

    private static void updateRecordingOverlay(MinecraftClient client) {
        if (recordingStartedAtMillis <= 0 || client.inGameHud == null) {
            return;
        }
        int seconds = (int) ((System.currentTimeMillis() - recordingStartedAtMillis) / 1000L);
        if (seconds == lastOverlaySecond) {
            return;
        }
        lastOverlaySecond = seconds;
        int minutesPart = seconds / 60;
        int secondsPart = seconds % 60;
        client.inGameHud.setOverlayMessage(Text.literal(String.format(Locale.ROOT, "Идёт запись: %02d:%02d", minutesPart, secondsPart)), false);
    }

    /**
     * Вызывается из mixin при СКМ по открытому чату.
     */
    public static void handleChatMiddleClick(Style clickedStyle, double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        String latestMessage = ChatMessageMemory.latestPlainMessage().orElse("");
        String nick = NickParser.findNickFromStyle(clickedStyle).orElseGet(() -> NickParser.findNick(latestMessage).orElse(null));

        if (nick == null) {
            sendLocalMessage("Ник не найден в сообщении чата.");
            return;
        }

        PendingScreenshot screenshot = null;
        if (!screenshotService.shouldSkipScreenshot(latestMessage)) {
            screenshot = screenshotService.captureTemp(nick);
        }

        sessionData.addRecent(nick);
        client.setScreen(new PunishmentScreen(nick, screenshot));
    }

    public static void sendPunishment(String nick, PunishmentRule rule, String duration, String reason, PendingScreenshot pendingScreenshot) {
        String cleanReason = reason == null || reason.isBlank() ? rule.commandReason() : reason.trim();
        String command = buildPunishmentCommand(nick, rule.type(), duration, cleanReason);

        sendCommand(command);
        sessionData.increment(rule.type());
        sessionData.addRecent(nick);
        screenshotService.finalizeScreenshot(pendingScreenshot, nick, rule.type(), duration, cleanReason);

        if (rule.type() == PunishmentType.IPBAN && shouldStopObsAfterIpBan(cleanReason)) {
            stopObsRecording("автостоп после IPBan");
        }

        sendLocalMessage("Команда отправлена: /" + command);
        MinecraftClient.getInstance().setScreen(new PunishmentScreen(nick, null));
    }

    private static String buildPunishmentCommand(String nick, PunishmentType type, String duration, String reason) {
        String cleanReason = reason == null ? "" : reason.trim();
        String cleanDuration = duration == null ? "" : duration.trim();

        return switch (type) {
            case WARN -> "warn " + nick + " " + cleanReason;
            case MUTE -> "mute " + nick + " " + cleanDuration + optionalReason(cleanReason);
            case BAN -> cleanDuration.isBlank()
                    ? "ban " + nick + optionalReason(cleanReason)
                    : "ban " + nick + " " + cleanDuration + optionalReason(cleanReason);
            case IPBAN -> cleanDuration.isBlank()
                    ? "ipban " + nick + optionalReason(cleanReason)
                    : "ipban " + nick + " " + cleanDuration + optionalReason(cleanReason);
        };
    }

    private static String optionalReason(String reason) {
        return reason == null || reason.isBlank() ? "" : " " + reason;
    }

    private static boolean shouldStopObsAfterIpBan(String reason) {
        String normalized = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
        return !(normalized.equals("бот")
                || normalized.equals("3.8")
                || normalized.equals("3.3")
                || normalized.equals("3.6")
                || normalized.equals("3.9")
                || normalized.equals("3.10"));
    }

    public static void callToCheck(String nick) {
        sendCommand("tpp " + nick);
        sendCommand("tp " + nick);
        sendCommand(config.checkCommandTemplate.replace("{nick}", nick).replaceFirst("^/", ""));
        sendCommand("tell " + nick + " " + config.checkTellText);

        if (config.obsEnabled) {
            obsClient.startRecording().thenAccept(ok -> {
                if (!ok) {
                    sendLocalMessage("OBS недоступен. Таймер проверки запущен, запись могла не включиться.");
                }
            });
        }

        recordingStartedAtMillis = System.currentTimeMillis();
        lastOverlaySecond = -1;
        sendLocalMessage("Игрок вызван на проверку: " + nick);
    }

    public static void removeFromCheck(String nick) {
        sendCommand(config.removeCheckCommandTemplate.replace("{nick}", nick).replaceFirst("^/", ""));
        stopObsRecording("снят с проверки");
        sendLocalMessage("Игрок снят с проверки: " + nick);
    }

    public static void stopObsRecording(String reason) {
        if (config.obsEnabled) {
            obsClient.stopRecording().thenAccept(ok -> {
                if (!ok) {
                    sendLocalMessage("OBS недоступен или запись уже остановлена.");
                }
            });
        }
        recordingStartedAtMillis = -1L;
        lastOverlaySecond = -1;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(Text.literal(""), false);
        }
        LOGGER.info("OBS stop requested: {}", reason);
    }

    public static void openPunishmentForRecent(String nick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.keyboard != null) {
            client.keyboard.setClipboard(nick);
        }
        sessionData.addRecent(nick);
        MinecraftClient.getInstance().setScreen(new PunishmentScreen(nick, null));
    }

    public static SessionData session() {
        return sessionData;
    }

    public static ModConfig config() {
        return config;
    }

    public static ScreenshotService screenshots() {
        return screenshotService;
    }

    public static void discardScreenshot(PendingScreenshot pendingScreenshot) {
        screenshotService.discard(pendingScreenshot);
    }

    public static void sendCommand(String commandWithoutSlash) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client == null ? null : client.getNetworkHandler();
        if (networkHandler == null) {
            sendLocalMessage("Команда не отправлена: нет подключения к серверу.");
            return;
        }
        String command = commandWithoutSlash == null ? "" : commandWithoutSlash.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (!command.isBlank()) {
            networkHandler.sendChatCommand(command);
        }
    }

    public static void sendLocalMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("[RaidMiner] " + message), false);
        }
        LOGGER.info(message);
    }
}
