package com.litebrawl.game;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 复活 / 入场保护：一段时间内免伤，同时默认也不允许打出伤害。 */
public final class ProtectionService {

    private final LiteBrawlPlugin plugin;
    private final Map<UUID, Long> until = new HashMap<>();

    public ProtectionService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void protect(Player player) {
        int seconds = plugin.settings().protectionSeconds();
        if (seconds <= 0) {
            return;
        }
        until.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean isProtected(Player player) {
        Long time = until.get(player.getUniqueId());
        if (time == null) {
            return false;
        }
        if (System.currentTimeMillis() >= time) {
            until.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public int remainingSeconds(Player player) {
        Long time = until.get(player.getUniqueId());
        if (time == null) {
            return 0;
        }
        long left = (time - System.currentTimeMillis()) / 1000L;
        return left <= 0 ? 0 : (int) left + 1;
    }

    public void clear(UUID uuid) {
        until.remove(uuid);
    }

    public void clearAll() {
        until.clear();
    }
}
