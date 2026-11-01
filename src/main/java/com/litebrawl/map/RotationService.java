package com.litebrawl.map;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * 多地图轮换。
 *
 * <p>按注册顺序循环切换当前地图，换图时把所有在线玩家送回地图出生点（大厅状态），
 * 让他们重新点「开始游戏」，避免出现「人被传走了还带着上一张图的装备」。</p>
 */
public final class RotationService {

    private final LiteBrawlPlugin plugin;
    private int remainingSeconds;
    private BukkitTask task;

    public RotationService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.settings().rotationEnabled()) {
            return;
        }
        if (plugin.maps().all().size() < 2) {
            plugin.getLogger().info("注册地图少于 2 张，自动轮换不启用（加了第二张图重启即可生效）。");
            return;
        }
        remainingSeconds = plugin.settings().rotationSeconds();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("地图轮换已启用：每 " + remainingSeconds + " 秒切换一次");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    /** 距离下一次轮换还剩多少秒（未启用时返回 -1）。 */
    public int remainingSeconds() {
        return task == null ? -1 : remainingSeconds;
    }

    private void tick() {
        remainingSeconds--;
        int announce = plugin.settings().rotationAnnounceSeconds();
        if (announce > 0 && remainingSeconds == announce) {
            String message = plugin.settings().rotationAnnounce()
                    .replace("%seconds%", String.valueOf(announce));
            String winner = plugin.votes().winner();
            if (plugin.settings().rotationVoteMode() && winner != null) {
                message = message + " <gray>当前票数最高：</gray><white>" + winner + "</white>";
            }
            broadcast(message);
        }
        if (remainingSeconds <= 0) {
            rotate();
            remainingSeconds = plugin.settings().rotationSeconds();
        }
    }

    /** 立刻切到下一张地图。 */
    public void rotate() {
        List<LiteMap> all = new ArrayList<>(plugin.maps().all());
        if (all.size() < 2) {
            return;
        }
        LiteMap current = plugin.maps().active().orElse(null);
        int index = current == null ? -1 : indexOf(all, current.id());
        LiteMap next = all.get((index + 1) % all.size());
        // VOTE 模式下优先切到票数最高的地图
        if (plugin.settings().rotationVoteMode()) {
            String winner = plugin.votes().winner();
            if (winner != null) {
                LiteMap voted = plugin.maps().byId(winner).orElse(null);
                if (voted != null) {
                    next = voted;
                }
            }
        }

        plugin.maps().setActive(next.id());
        plugin.votes().reset();
        broadcast(plugin.settings().rotationSwitched()
                .replace("%map%", next.displayName())
                .replace("%world%", next.worldName()));

        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.game().prepareLobby(player);
        }
    }

    private int indexOf(List<LiteMap> maps, String id) {
        for (int i = 0; i < maps.size(); i++) {
            if (maps.get(i).id().equalsIgnoreCase(id)) {
                return i;
            }
        }
        return -1;
    }

    private void broadcast(String miniText) {
        var component = Msg.mini(plugin.settings().prefix() + miniText);
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }
}
