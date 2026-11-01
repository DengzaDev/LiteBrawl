package com.litebrawl.combat;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 反刷分：短时间内重复击杀同一个对手，奖励递减。
 *
 * <p>系数 = decay^重复次数，最低不低于 min-factor。
 * 例如 decay=0.5、min=0.1：第 1 次 100%，第 2 次 50%，第 3 次 25%…</p>
 */
public final class AntiFarmService {

    /** victim -> {次数, 最后时间}，按 killer 分开记 */
    private final Map<UUID, Map<UUID, long[]>> history = new HashMap<>();

    private final LiteBrawlPlugin plugin;

    public AntiFarmService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    /** 计算本次击杀的收益系数（0.0 ~ 1.0）。 */
    public double factor(Player killer, Player victim) {
        if (!plugin.settings().antiFarmEnabled()) {
            return 1.0D;
        }
        Map<UUID, long[]> mine = history.get(killer.getUniqueId());
        if (mine == null) {
            return 1.0D;
        }
        prune(mine);
        long[] record = mine.get(victim.getUniqueId());
        if (record == null) {
            return 1.0D;
        }
        double decay = plugin.settings().antiFarmDecay();
        double factor = Math.pow(decay, record[0]);
        return Math.max(plugin.settings().antiFarmMinFactor(), factor);
    }

    /** 记录一次击杀（在发奖励之后调用）。 */
    public void record(Player killer, Player victim) {
        if (!plugin.settings().antiFarmEnabled()) {
            return;
        }
        Map<UUID, long[]> mine = history.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>());
        prune(mine);
        long[] record = mine.get(victim.getUniqueId());
        if (record == null) {
            mine.put(victim.getUniqueId(), new long[]{1L, System.currentTimeMillis()});
        } else {
            record[0]++;
            record[1] = System.currentTimeMillis();
        }
    }

    /** 清掉超过窗口期的记录，避免长期占内存。 */
    private void prune(Map<UUID, long[]> mine) {
        long window = plugin.settings().antiFarmWindowSeconds() * 1000L;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, long[]>> it = mine.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue()[1] > window) {
                it.remove();
            }
        }
    }

    public void clear(UUID killer) {
        history.remove(killer);
    }

    public void clearAll() {
        history.clear();
    }
}
