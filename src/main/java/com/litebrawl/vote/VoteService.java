package com.litebrawl.vote;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.map.LiteMap;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 地图票选。
 *
 * <p>玩家用 {@code /vote <地图id>} 投票，轮换时如果开了 VOTE 模式就切到票数最高的图，
 * 平票按地图注册顺序取第一个。</p>
 */
public final class VoteService {

    private final LiteBrawlPlugin plugin;
    /** 玩家 -> 地图 id */
    private final Map<UUID, String> votes = new LinkedHashMap<>();

    public VoteService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return null 表示成功 */
    public String vote(Player player, String mapId) {
        LiteMap map = plugin.maps().byId(mapId).orElse(null);
        if (map == null) {
            return "没有这张地图: " + mapId;
        }
        votes.put(player.getUniqueId(), map.id());
        return null;
    }

    public String voteOf(Player player) {
        return votes.get(player.getUniqueId());
    }

    /** 票数统计（按票数降序）。 */
    public List<Map.Entry<String, Integer>> tally() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LiteMap map : plugin.maps().all()) {
            counts.put(map.id(), 0);
        }
        for (String id : votes.values()) {
            counts.merge(id, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> result = new ArrayList<>(counts.entrySet());
        result.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return result;
    }

    /** 得票最高的地图 id；没人投票返回 null。 */
    public String winner() {
        List<Map.Entry<String, Integer>> tally = tally();
        if (tally.isEmpty() || tally.get(0).getValue() <= 0) {
            return null;
        }
        return tally.get(0).getKey();
    }

    public void reset() {
        votes.clear();
    }

    public int totalVotes() {
        return votes.size();
    }
}
