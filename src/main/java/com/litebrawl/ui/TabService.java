package com.litebrawl.ui;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tab 列表上下方文字（玩家列表的 header / footer）。
 *
 * <p>支持占位符：%player% %online% %map% %countdown% %alive%，
 * 文案在 config.yml 的 tab.header / tab.footer 里改，<newline> 可以换行。</p>
 */
public final class TabService {

    private final LiteBrawlPlugin plugin;
    private BukkitTask task;

    public TabService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.settings().tabEnabled()) {
            return;
        }
        long ticks = plugin.settings().tabUpdateSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> refresh(), 20L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
        }
    }

    public void restart() {
        stop();
        start();
    }

    public void refresh() {
        if (!plugin.settings().tabEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.playerSettings().tab(player)) {
                continue;
            }
            player.sendPlayerListHeaderAndFooter(render(player, plugin.settings().tabHeader()),
                    render(player, plugin.settings().tabFooter()));
        }
    }

    public void refresh(Player player) {
        if (!plugin.settings().tabEnabled()) {
            return;
        }
        player.sendPlayerListHeaderAndFooter(render(player, plugin.settings().tabHeader()),
                render(player, plugin.settings().tabFooter()));
    }

    private Component render(Player viewer, String template) {
        if (template == null || template.isBlank()) {
            return Component.empty();
        }
        String map = plugin.maps().active().map(m -> m.displayName()).orElse("-");
        int countdown = plugin.rotation().remainingSeconds();
        long alive = Bukkit.getOnlinePlayers().stream().filter(plugin.game()::isPlaying).count();
        return Msg.mini(Msg.replace(template,
                "player", viewer.getName(),
                "online", String.valueOf(Bukkit.getOnlinePlayers().size()),
                "map", map,
                "countdown", countdown <= 0 ? "-" : String.valueOf(countdown),
                "alive", String.valueOf(alive)));
    }
}
