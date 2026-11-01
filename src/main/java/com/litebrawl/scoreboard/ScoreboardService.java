package com.litebrawl.scoreboard;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.data.Profile;
import com.litebrawl.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 侧边栏记分板。
 *
 * <p>每一行用一个纯颜色码字符串当 entry（不可见），真正的文字放在 Team 前缀里，
 * 这样既能改文字又不用重建整个 scoreboard。</p>
 */
public final class ScoreboardService {

    private static final int MAX_LINES = 15;
    private static final String OBJECTIVE_NAME = "litebrawl_sidebar";
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('\u00A7').hexColors().build();

    private final LiteBrawlPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();

    private BukkitTask task;

    public ScoreboardService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    /** 一个玩家的记分板状态。 */
    private static final class Session {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final List<String> entries = new ArrayList<>();
        private final List<Team> teams = new ArrayList<>();
        private int lineCount = -1;

        private Session(Scoreboard scoreboard, Objective objective) {
            this.scoreboard = scoreboard;
            this.objective = objective;
        }
    }

    // ---------------- 生命周期 ----------------

    public void start() {
        if (!plugin.settings().sidebarEnabled()) {
            return;
        }
        long period = Math.max(1, plugin.settings().sidebarUpdateSeconds()) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                reset(player);
            }
        }
        sessions.clear();
        hidden.clear();
    }

    /** 配置重载后整体重建，避免新旧行数不一致。 */
    public void restart() {
        stop();
        start();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isEnabled(player)) {
                update(player);
            }
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isEnabled(player)) {
                update(player);
            }
        }
    }

    // ---------------- 开关 ----------------

    public boolean isEnabled(Player player) {
        if (hidden.contains(player.getUniqueId())) {
            return false;
        }
        return plugin.playerSettings().sidebar(player);
    }

    /** @return 切换后的状态（与设置界面共用同一份持久化设置） */
    public boolean toggle(Player player) {
        boolean on = plugin.playerSettings().toggle(player,
                com.litebrawl.settings.PlayerSettingsService.KEY_SIDEBAR, true);
        if (on) {
            update(player);
        } else {
            remove(player);
        }
        return on;
    }

    // ---------------- 渲染 ----------------

    public void update(Player player) {
        if (!plugin.settings().sidebarEnabled()) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null || player.getScoreboard() != session.scoreboard) {
            session = create(player);
            if (session == null) {
                return;
            }
            sessions.put(player.getUniqueId(), session);
            player.setScoreboard(session.scoreboard);
        }
        apply(player, session);
    }

    public void remove(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            reset(player);
        }
    }

    private void reset(Player player) {
        var manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private Session create(Player player) {
        var manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return null;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective;
        try {
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY,
                    LEGACY.serialize(Msg.mini(plugin.settings().sidebarTitle())));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("创建记分板失败: " + ex.getMessage());
            return null;
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        return new Session(scoreboard, objective);
    }

    private void apply(Player player, Session session) {
        List<String> template = new ArrayList<>(plugin.settings().sidebarLines());
        template.addAll(plugin.settings().sidebarFooter());
        if (template.size() > MAX_LINES) {
            template = new ArrayList<>(template.subList(0, MAX_LINES));
        }
        int lineCount = template.size();

        // 行数变化（改配置后）需要重建，否则会残留旧行
        if (lineCount != session.lineCount) {
            rebuildLines(session, lineCount);
        }

        for (int i = 0; i < lineCount; i++) {
            Team team = session.teams.get(i);
            team.prefix(render(player, template.get(i)));
            session.objective.getScore(session.entries.get(i)).setScore(lineCount - i);
        }
    }

    private void rebuildLines(Session session, int lineCount) {
        // 清掉旧的行
        for (int i = 0; i < session.entries.size(); i++) {
            Team team = session.teams.get(i);
            if (team != null) {
                team.unregister();
            }
        }
        session.entries.clear();
        session.teams.clear();

        for (int i = 0; i < lineCount; i++) {
            String entry = entryKey(i);
            Team team = session.scoreboard.registerNewTeam(teamName(i));
            team.addEntry(entry);
            team.prefix(Component.empty());
            session.entries.add(entry);
            session.teams.add(team);
        }
        session.lineCount = lineCount;
    }

    private String rotationCountdown() {
        int seconds = plugin.rotation().remainingSeconds();
        return seconds <= 0 ? "-" : String.valueOf(seconds);
    }

    /** 用纯颜色码做 entry：客户端不显示任何字符，只保留唯一性。 */
    private static String entryKey(int index) {
        return "\u00A70\u00A7" + Integer.toHexString(index);
    }

    private static String teamName(int index) {
        return "lbl" + index;
    }

    private Component render(Player player, String template) {
        if (template == null || template.isBlank()) {
            return Component.space();
        }
        Profile profile = plugin.profiles().get(player);
        int level = plugin.profiles().levelOf(profile.getExperience());
        boolean playing = plugin.game().isPlaying(player);
        long alive = Bukkit.getOnlinePlayers().stream().filter(plugin.game()::isPlaying).count();

        String text = Msg.replace(template,
                "player", player.getName(),
                "status", playing ? plugin.settings().sidebarStatusPlaying() : plugin.settings().sidebarStatusIdle(),
                "level", String.valueOf(level),
                "exp", String.valueOf(profile.getExperience()),
                "coins", String.valueOf(profile.getCoins()),
                "kills", String.valueOf(profile.getKills()),
                "deaths", String.valueOf(profile.getDeaths()),
                "assists", String.valueOf(profile.getAssists()),
                "kd", String.format(Locale.ROOT, "%.2f", profile.kd()),
                "alive", String.valueOf(alive),
                "health", String.format(Locale.ROOT, "%.1f", player.getHealth()),
                "arrows", String.valueOf(plugin.quivers().count(player)),
                "arrows_max", String.valueOf(plugin.quivers().maxFor(player)),
                "bounty", String.valueOf(plugin.bounty().bounty(player.getUniqueId())),
                "streak", String.valueOf(plugin.bounty().streak(player.getUniqueId())),
                "protection", String.valueOf(plugin.protection().remainingSeconds(player)),
                "team", plugin.teams().teamNameOf(player.getUniqueId()),
                "map", plugin.maps().active().map(m -> m.displayName()).orElse("-"),
                "countdown", rotationCountdown(),
                "combat", String.valueOf(plugin.combatTag().remainingSeconds(player)));
        return Msg.mini(text);
    }
}
