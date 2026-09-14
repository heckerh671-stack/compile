package com.soulsplugin.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory cooldown tracker keyed by player UUID + an
 * arbitrary key string (e.g. "LEVIATHAN_ABILITY_1"). Cooldowns are not
 * persisted across restarts by design -- they are short-lived combat state.
 */
public class CooldownManager {

    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    /** Returns remaining cooldown in seconds, or 0 if the ability is ready. */
    public long getRemainingSeconds(UUID uuid, String key) {
        Map<String, Long> playerMap = cooldowns.get(uuid);
        if (playerMap == null) {
            return 0;
        }
        Long readyAt = playerMap.get(key);
        if (readyAt == null) {
            return 0;
        }
        long remainingMillis = readyAt - System.currentTimeMillis();
        return remainingMillis <= 0 ? 0 : (remainingMillis / 1000) + 1;
    }

    public boolean isReady(UUID uuid, String key) {
        return getRemainingSeconds(uuid, key) == 0;
    }

    public void startCooldown(UUID uuid, String key, int seconds) {
        cooldowns.computeIfAbsent(uuid, u -> new HashMap<>())
                .put(key, System.currentTimeMillis() + (seconds * 1000L));
    }
}
