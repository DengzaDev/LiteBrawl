package com.litebrawl.combat;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 战斗模式（战斗标签）。
 *
 * <p>玩家之间发生伤害后进入战斗模式，持续 N 秒（config）。期间禁止 /spawn、返回大厅、
 * 打开商店等「跑路」操作。侧边栏可用 %combat% 看剩余秒数。</p>
 */
public final class CombatTagService {

    private final LiteBrawlPlugin plugin;
    private final Map<UUID, Long> tags = new HashMap<>();

    public CombatTagService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void tag(Player player) {
        int seconds = plugin.settings().combatTagSeconds();
        if (seconds <= 0) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean wasInCombat = inCombat(player);
        tags.put(uuid, System.currentTimeMillis() + seconds * 1000L);
        if (!wasInCombat && notify(player)) {
            Msg.info(player, plugin.settings().prefix(),
                    "<red>进入战斗模式</red> <dark_gray>·</dark_gray> <gray>%seconds% 秒内无法回城</gray>",
                    "seconds", String.valueOf(seconds));
        }
    }

    public boolean inCombat(Player player) {
        Long until = tags.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            tags.remove(player.getUniqueId());
            if (notify(player)) {
                Msg.info(player, plugin.settings().prefix(), "<green>已脱离战斗模式。</green>");
            }
            return false;
        }
        return true;
    }

    public int remainingSeconds(Player player) {
        Long until = tags.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long left = (until - System.currentTimeMillis()) / 1000L;
        return left <= 0 ? 0 : (int) left + 1;
    }

    private boolean notify(Player player) {
        return plugin.settings().combatTagNotify() && plugin.playerSettings().combatNotify(player);
    }

    public void clear(UUID uuid) {
        tags.remove(uuid);
    }

    public void clearAll() {
        tags.clear();
    }
}
