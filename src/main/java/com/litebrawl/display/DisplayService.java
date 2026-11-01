package com.litebrawl.display;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.cosmetic.Cosmetic;
import com.litebrawl.data.Profile;
import com.litebrawl.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * 玩家名字展示：<code>[Lv.n]玩家名[称号]</code>。
 *
 * <p>三个地方各用不同手段：</p>
 * <ul>
 *   <li><b>Tab 列表</b>：{@code playerListName(Component)}，完整支持渐变色</li>
 *   <li><b>聊天</b>：聊天里插入 {@code %display%} 组件，完整支持渐变色</li>
 *   <li><b>头顶铭牌</b>：只能用 Scoreboard 队伍的 prefix/suffix + 队伍颜色，
 *       所以渐变改不了，会退化成队伍颜色（这是客户端渲染限制，不是偷懒）</li>
 * </ul>
 */
public final class DisplayService implements org.bukkit.event.Listener {

    private static final String TEAM_PREFIX = "lb_";

    private final LiteBrawlPlugin plugin;
    private BukkitTask task;

    public DisplayService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long ticks = Math.max(5, plugin.settings().displayRefreshTicks());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, ticks, ticks);
    }

    /** 受伤/回血后立刻刷一次，让血量条跟得上战斗节奏。 */
    @org.bukkit.event.EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!plugin.settings().healthDisplay()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            refresh(player);
        }
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

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /** 刷新一个玩家的 Tab 名字和头顶铭牌。 */
    public void refresh(Player player) {
        Component display = displayName(player);
        player.playerListName(display);
        updateTeam(player, prefix(player), suffix(player));
    }

    /** 完整名字：[Lv.n] + 已购买名字样式 + [称号]。 */
    public Component displayName(Player player) {
        Profile profile = plugin.profiles().getIfLoaded(player.getUniqueId());
        int level = profile == null ? 1 : plugin.profiles().levelOf(profile.getExperience());

        Component levelPart = Msg.mini(Msg.replace(plugin.settings().levelFormat(),
                "level", String.valueOf(level)));

        Cosmetic style = plugin.cosmetics().equipped(player, com.litebrawl.cosmetic.CosmeticType.NAME_STYLE);
        Component namePart;
        if (style != null && !style.payload().isBlank()) {
            namePart = Msg.mini(style.payload().replace("%name%", player.getName()));
        } else {
            namePart = Component.text(player.getName());
        }

        return levelPart.append(namePart).append(suffix(player));
    }

    /** 头顶铭牌的前缀：等级（可在 config 里关掉，关掉后 vanilla 消息也不会再带 [Lv.n]）。 */
    public Component prefix(Player player) {
        if (!plugin.settings().nametagLevel()) {
            return Component.empty();
        }
        Profile profile = plugin.profiles().getIfLoaded(player.getUniqueId());
        int level = profile == null ? 1 : plugin.profiles().levelOf(profile.getExperience());
        return Msg.mini(Msg.replace(plugin.settings().levelFormat(), "level", String.valueOf(level)));
    }

    /** 头顶铭牌的后缀：[称号] + 血量条（Tab 也用这一份）。 */
    public Component suffix(Player player) {
        Component component = Component.empty();
        Cosmetic title = plugin.cosmetics().equipped(player, com.litebrawl.cosmetic.CosmeticType.TITLE);
        if (title != null && !title.payload().isBlank()) {
            component = component.append(Msg.mini(
                    Msg.replace(plugin.settings().titleSuffix(), "title", title.payload())));
        }
        return component.append(health(player));
    }

    /** 血量条：❤ 满格 + ♡ 空格，总数按最大血量换算，最多 max-hearts 个。 */
    public Component health(Player player) {
        if (!plugin.settings().healthDisplay()) {
            return Component.empty();
        }
        int maxHearts = plugin.settings().healthMaxHearts();
        int total = Math.min(maxHearts, (int) Math.ceil(player.getMaxHealth() / 2.0D));
        int full = (int) Math.ceil(player.getHealth() / 2.0D);
        full = Math.max(0, Math.min(total, full));
        String fullText = "❤".repeat(full);
        String emptyText = "♡".repeat(total - full);
        String rendered = plugin.settings().healthFormat()
                .replace("%full%", fullText)
                .replace("%empty%", emptyText);
        return Msg.mini(plugin.settings().healthSeparator() + rendered);
    }

    private void updateTeam(Player player, Component prefix, Component suffix) {
        var manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getMainScoreboard();
        String name = TEAM_PREFIX + player.getUniqueId().toString().substring(0, 8);
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            try {
                team = scoreboard.registerNewTeam(name);
            } catch (IllegalArgumentException ex) {
                return;
            }
        }
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
        team.prefix(prefix);
        team.suffix(suffix);

        Cosmetic style = plugin.cosmetics().equipped(player, com.litebrawl.cosmetic.CosmeticType.NAME_STYLE);
        // 头顶名字只能用队伍颜色，渐变做不到；没有样式时用默认白色
        team.color(style == null || style.color() == null
                ? net.kyori.adventure.text.format.NamedTextColor.WHITE
                : style.color());
    }
}
