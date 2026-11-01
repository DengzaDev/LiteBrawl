package com.litebrawl.combat;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 伤害归属记录，用来区分「完整击杀」与「助杀」。 */
public final class CombatService {

    private static final class Entry {
        private double damage;
        private long lastHit;
    }

    private final LiteBrawlPlugin plugin;
    /** victim -> attacker -> 累计伤害 */
    private final Map<UUID, Map<UUID, Entry>> trackers = new HashMap<>();

    public CombatService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void recordDamage(Player victim, Player attacker, double amount) {
        if (victim == null || attacker == null || victim.equals(attacker)) {
            return;
        }
        long now = System.currentTimeMillis();
        long window = plugin.settings().assistWindowMillis();

        Map<UUID, Entry> perVictim = trackers.computeIfAbsent(victim.getUniqueId(), key -> new HashMap<>());
        perVictim.entrySet().removeIf(e -> now - e.getValue().lastHit > window);

        Entry entry = perVictim.computeIfAbsent(attacker.getUniqueId(), key -> new Entry());
        entry.damage += Math.max(0.0D, amount);
        entry.lastHit = now;
    }

    /**
     * 结算助杀并清空该受害者的记录。
     *
     * @return 助杀者 uuid 列表（已排除击杀者本人）
     */
    public List<UUID> consumeAssists(Player victim, Player killer) {
        Map<UUID, Entry> perVictim = trackers.remove(victim.getUniqueId());
        if (perVictim == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        long window = plugin.settings().assistWindowMillis();
        double minDamage = plugin.settings().assistMinDamage();

        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : perVictim.entrySet()) {
            if (killer != null && entry.getKey().equals(killer.getUniqueId())) {
                continue;
            }
            Entry value = entry.getValue();
            if (now - value.lastHit > window) {
                continue;
            }
            if (value.damage < minDamage) {
                continue;
            }
            result.add(entry.getKey());
        }
        return result;
    }

    public void clear(UUID victim) {
        trackers.remove(victim);
    }

    public void clearAll() {
        trackers.clear();
    }
}
