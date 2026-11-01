package com.litebrawl.game;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 赏金系统（按连杀计算）。
 *
 * <p>规则：连续击杀达到 N 次（config）后开始被悬赏，首个赏金为基础值，
 * 之后每多杀一个再叠加一份。被人击杀时赏金归击杀者所有，连杀与赏金一起清零。
 * 用内存保存：不落库的代价是重启后清零，好处是不用改表结构，而且悬赏本来就该是短期的。</p>
 */
public final class BountyService {

    private final LiteBrawlPlugin plugin;
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Long> bounties = new HashMap<>();

    public BountyService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public int streak(UUID uuid) {
        return streaks.getOrDefault(uuid, 0);
    }

    public long bounty(UUID uuid) {
        return bounties.getOrDefault(uuid, 0L);
    }

    public boolean hasBounty(UUID uuid) {
        return bounty(uuid) > 0L;
    }

    /**
     * 记录一次击杀。
     *
     * @return 本次新增的赏金；0 表示还没到悬赏线或系统关闭
     */
    public long onKill(Player killer) {
        if (!plugin.settings().bountyEnabled()) {
            return 0L;
        }
        UUID uuid = killer.getUniqueId();
        int current = streaks.merge(uuid, 1, Integer::sum);
        if (current < plugin.settings().bountyMinStreak()) {
            return 0L;
        }
        long existing = bounties.getOrDefault(uuid, 0L);
        long added = existing <= 0L
                ? plugin.settings().bountyBase()
                : plugin.settings().bountyPerKill();
        if (added <= 0L) {
            return 0L;
        }
        bounties.put(uuid, existing + added);
        return added;
    }

    /**
     * 被击杀：取走赏金并清零连杀。
     *
     * @return 该玩家身上的赏金（可能为 0）
     */
    public long claim(Player victim) {
        UUID uuid = victim.getUniqueId();
        // 注意：没有赏金时 remove 返回 null，直接拆箱会 NPE（这就是之前那次报错）
        Long amount = bounties.remove(uuid);
        streaks.remove(uuid);
        return amount == null || amount <= 0L ? 0L : amount;
    }

    /** 手动清零（例如被管理员踢出对局）。 */
    public void reset(UUID uuid) {
        streaks.remove(uuid);
        bounties.remove(uuid);
    }

    public void clearAll() {
        streaks.clear();
        bounties.clear();
    }
}
