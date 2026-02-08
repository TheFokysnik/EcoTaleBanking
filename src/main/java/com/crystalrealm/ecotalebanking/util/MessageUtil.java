package com.crystalrealm.ecotalebanking.util;

import com.crystalrealm.ecotalebanking.lang.LangManager;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for formatting and sending banking messages.
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public final class MessageUtil {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static final DecimalFormat COIN_FORMAT;
    private static final DecimalFormat PERCENT_FORMAT;

    /** Cache of PlayerRef objects by UUID for sending messages from ECS context */
    private static final Map<UUID, Object> PLAYER_REF_CACHE = new ConcurrentHashMap<>();

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        COIN_FORMAT = new DecimalFormat("#,##0.##", symbols);
        PERCENT_FORMAT = new DecimalFormat("0.##", symbols);
    }

    private MessageUtil() {}

    // ── PlayerRef Cache ─────────────────────────────────────────

    public static void cachePlayerRef(UUID uuid, Object playerRef) {
        if (uuid != null && playerRef != null) {
            PLAYER_REF_CACHE.put(uuid, playerRef);
        }
    }

    public static void clearCache() {
        PLAYER_REF_CACHE.clear();
    }

    /**
     * Sends a chat notification to a player if they are online (cached PlayerRef).
     * Silent if the player is offline or the cache entry is invalid.
     *
     * @param playerUuid player to notify
     * @param miniMessageText MiniMessage-formatted text
     */
    public static void sendNotification(java.util.UUID playerUuid, String miniMessageText) {
        Object playerRef = PLAYER_REF_CACHE.get(playerUuid);
        if (playerRef == null) return;

        try {
            String json = MiniMessageParser.toJson(miniMessageText);
            Class<?> msgClass = Class.forName("com.hypixel.hytale.server.core.Message");
            Method parseMethod = msgClass.getMethod("parse", String.class);
            Object message = parseMethod.invoke(null, json);
            Method sendMethod = playerRef.getClass().getMethod("sendMessage", msgClass);
            sendMethod.invoke(playerRef, message);
        } catch (Exception e) {
            LOGGER.debug("sendNotification failed for {}: {}", playerUuid, e.getMessage());
            PLAYER_REF_CACHE.remove(playerUuid);
        }
    }

    // ── Chat Messages (from commands) ───────────────────────────

    /**
     * Sends a message via CommandContext → sendMessage(Message).
     */
    public static void sendViaContext(Object context, String text) {
        try {
            String jsonText = MiniMessageParser.toJson(text);
            Class<?> msgClass = Class.forName("com.hypixel.hytale.server.core.Message");
            Method parseMethod = msgClass.getMethod("parse", String.class);
            Object msg = parseMethod.invoke(null, jsonText);

            Method sendMsg = context.getClass().getMethod("sendMessage", msgClass);
            sendMsg.invoke(context, msg);
        } catch (Exception e) {
            LOGGER.debug("sendViaContext failed: {}", e.getMessage());
        }
    }

    // ── Formatting Helpers ──────────────────────────────────────

    /**
     * Formats a currency amount: 1234.56 → "1,234.56"
     */
    public static String formatCoins(double amount) {
        return COIN_FORMAT.format(amount);
    }

    /**
     * Formats a BigDecimal amount.
     */
    public static String formatCoins(BigDecimal amount) {
        return COIN_FORMAT.format(amount.doubleValue());
    }

    /**
     * Formats a percentage: 0.05 → "5%"
     */
    public static String formatPercent(double rate) {
        return PERCENT_FORMAT.format(rate * 100) + "%";
    }

    /**
     * Formats a percentage from BigDecimal.
     */
    public static String formatPercent(BigDecimal rate) {
        return formatPercent(rate.doubleValue());
    }

    /**
     * Formats a number of days into a human-readable form.
     */
    public static String formatDays(long days) {
        if (days == 1) return "1 day";
        return days + " days";
    }

    /**
     * Colored status.
     */
    public static String coloredStatus(String status) {
        return switch (status.toUpperCase()) {
            case "ACTIVE" -> "<green>" + status;
            case "PAID", "MATURED" -> "<aqua>" + status;
            case "OVERDUE" -> "<yellow>" + status;
            case "DEFAULTED", "WITHDRAWN" -> "<red>" + status;
            default -> "<gray>" + status;
        };
    }
}
