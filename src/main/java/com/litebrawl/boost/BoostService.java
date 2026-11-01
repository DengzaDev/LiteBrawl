package com.litebrawl.boost;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 双倍硬币 / 双倍经验。
 *
 * <p>三种开启方式：</p>
 * <ul>
 *   <li>管理员手动：{@code /lb boost start ...}</li>
 *   <li>定时：每天固定时刻触发（boost.yml 的 schedules）</li>
 *   <li>事件触发：连杀达到 N、在线人数达到 N（config.yml 的 boost.triggers）</li>
 * </ul>
 *
 * <p>每个加成可以限定受众：不填目标 = 全服，填了玩家名 = 只有他们享受。</p>
 */
public final class BoostService {

    public enum BoostType {
        COINS("硬币"),
        EXP("经验");

        private final String label;

        BoostType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 一条生效中的加成。 */
    public static final class Active {
        private final BoostType type;
        private final double multiplier;
        private long endsAt;
        private final Set<String> targets;

        private Active(BoostType type, double multiplier, long endsAt, Set<String> targets) {
            this.type = type;
            this.multiplier = multiplier;
            this.endsAt = endsAt;
            this.targets = targets;
        }

        public double multiplier() {
            return multiplier;
        }

        public Set<String> targets() {
            return targets;
        }

        public int remainingSeconds() {
            long left = (endsAt - System.currentTimeMillis()) / 1000L;
            return left <= 0 ? 0 : (int) left;
        }

        private boolean appliesTo(String playerName) {
            return targets.isEmpty() || targets.contains(playerName.toLowerCase(Locale.ROOT));
        }
    }

    /** 定时计划。 */
    public static final class Schedule {
        private final String id;
        private final String time;
        private final int durationSeconds;
        private final double multiplier;
        private final Set<BoostType> types;
        private final Set<String> targets;
        private String lastFired;

        private Schedule(String id, String time, int durationSeconds, double multiplier,
                         Set<BoostType> types, Set<String> targets, String lastFired) {
            this.id = id;
            this.time = time;
            this.durationSeconds = durationSeconds;
            this.multiplier = multiplier;
            this.types = types;
            this.targets = targets;
            this.lastFired = lastFired;
        }

        public String id() {
            return id;
        }

        public String time() {
            return time;
        }
    }

    private final LiteBrawlPlugin plugin;
    private final File file;
    private final Map<BoostType, Active> active = new EnumMap<>(BoostType.class);
    private final List<Schedule> schedules = new ArrayList<>();

    private int lastOnline = -1;
    private BukkitTask tickTask;
    private BukkitTask scheduleTask;

    public BoostService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boost.yml");
    }

    // ---------------- 生命周期 ----------------

    public void start() {
        load();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        scheduleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSchedules, 100L, 20L * 30L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (scheduleTask != null) {
            scheduleTask.cancel();
            scheduleTask = null;
        }
        save();
    }

    public void restart() {
        stop();
        start();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        List<BoostType> expired = new ArrayList<>();
        for (Map.Entry<BoostType, Active> entry : active.entrySet()) {
            if (entry.getValue().endsAt <= now) {
                expired.add(entry.getKey());
            }
        }
        for (BoostType type : expired) {
            active.remove(type);
            broadcast(plugin.settings().boostEndMessage()
                    .replace("%type%", type.label()));
        }
        if (!expired.isEmpty()) {
            save();
        }
        onPlayerCount(Bukkit.getOnlinePlayers().size());
    }

    // ---------------- 数值应用 ----------------

    public long applyCoins(Player player, long amount) {
        return apply(BoostType.COINS, player, amount);
    }

    public long applyExp(Player player, long amount) {
        return apply(BoostType.EXP, player, amount);
    }

    private long apply(BoostType type, Player player, long amount) {
        Active boost = active.get(type);
        if (boost == null || amount <= 0 || !boost.appliesTo(player.getName())) {
            return amount;
        }
        return Math.round(amount * boost.multiplier);
    }

    public boolean hasBoost(BoostType type, Player player) {
        Active boost = active.get(type);
        return boost != null && boost.appliesTo(player.getName());
    }

    public int remainingSeconds(BoostType type) {
        Active boost = active.get(type);
        return boost == null ? 0 : boost.remainingSeconds();
    }

    // ---------------- 手动开关 ----------------

    public String start(Set<BoostType> types, int seconds, double multiplier, Set<String> targets) {
        if (types.isEmpty()) {
            return "没有指定加成类型";
        }
        if (seconds <= 0) {
            return "时长必须大于 0 秒";
        }
        long endsAt = System.currentTimeMillis() + seconds * 1000L;
        Set<String> normalized = new LinkedHashSet<>();
        for (String name : targets) {
            normalized.add(name.toLowerCase(Locale.ROOT));
        }
        for (BoostType type : types) {
            active.put(type, new Active(type, multiplier, endsAt, normalized));
            broadcast(plugin.settings().boostStartMessage()
                    .replace("%type%", type.label())
                    .replace("%multiplier%", format(multiplier))
                    .replace("%seconds%", String.valueOf(seconds))
                    .replace("%scope%", normalized.isEmpty() ? "全服" : String.join(",", normalized)));
        }
        save();
        return null;
    }

    public String stop(Set<BoostType> types) {
        boolean removed = false;
        for (BoostType type : types) {
            if (active.remove(type) != null) {
                removed = true;
                broadcast(plugin.settings().boostEndMessage().replace("%type%", type.label()));
            }
        }
        if (!removed) {
            return "当前没有生效中的加成";
        }
        save();
        return null;
    }

    public Map<BoostType, Active> activeBoosts() {
        return new EnumMap<>(active);
    }

    // ---------------- 定时计划 ----------------

    public List<Schedule> schedules() {
        return schedules;
    }

    public String addSchedule(String time, int durationSeconds, double multiplier,
                              Set<BoostType> types, Set<String> targets) {
        try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("H:mm"));
        } catch (RuntimeException ex) {
            return "时间格式要 HH:mm（例如 20:00）";
        }
        String id = "s" + (schedules.size() + 1);
        Set<String> normalized = new LinkedHashSet<>();
        for (String name : targets) {
            normalized.add(name.toLowerCase(Locale.ROOT));
        }
        schedules.add(new Schedule(id, time, durationSeconds, multiplier, types, normalized, ""));
        save();
        return null;
    }

    public String removeSchedule(String id) {
        boolean removed = schedules.removeIf(s -> s.id.equalsIgnoreCase(id));
        if (!removed) {
            return "找不到计划: " + id;
        }
        save();
        return null;
    }

    private void checkSchedules() {
        if (schedules.isEmpty()) {
            return;
        }
        LocalTime now = LocalTime.now();
        String today = LocalDate.now().toString();
        boolean changed = false;
        for (Schedule schedule : schedules) {
            if (today.equals(schedule.lastFired)) {
                continue;
            }
            try {
                LocalTime target = LocalTime.parse(schedule.time, DateTimeFormatter.ofPattern("H:mm"));
                if (now.getHour() != target.getHour() || now.getMinute() != target.getMinute()) {
                    continue;
                }
            } catch (RuntimeException ex) {
                continue;
            }
            start(schedule.types, schedule.durationSeconds, schedule.multiplier, schedule.targets);
            schedule.lastFired = today;
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    // ---------------- 事件触发 ----------------

    /** 连杀触发：达到配置的连杀数时自动开一段。 */
    public void onKillStreak(Player player, int streak) {
        if (!plugin.settings().boostStreakEnabled()) {
            return;
        }
        if (streak < plugin.settings().boostStreakCount()) {
            return;
        }
        if (plugin.settings().boostStreakOncePerStreak()) {
            // 每个连杀数只触发一次
            if (streak != plugin.settings().boostStreakCount()) {
                return;
            }
        }
        start(plugin.settings().boostStreakTypes(),
                plugin.settings().boostStreakSeconds(),
                plugin.settings().boostStreakMultiplier(),
                Set.of());
    }

    /** 在线人数触发：只在「跨过阈值」的那一次触发，避免人一直在线就无限续期。 */
    public void onPlayerCount(int online) {
        if (!plugin.settings().boostCountEnabled()) {
            return;
        }
        int threshold = plugin.settings().boostCountThreshold();
        boolean crossed = lastOnline < threshold && online >= threshold;
        lastOnline = online;
        if (!crossed) {
            return;
        }
        start(plugin.settings().boostCountTypes(),
                plugin.settings().boostCountSeconds(),
                plugin.settings().boostCountMultiplier(),
                Set.of());
    }

    // ---------------- 持久化 ----------------

    private void load() {
        active.clear();
        schedules.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection activeSection = yml.getConfigurationSection("active");
        if (activeSection != null) {
            for (String key : activeSection.getKeys(false)) {
                ConfigurationSection section = activeSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                BoostType type;
                try {
                    type = BoostType.valueOf(key.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                long endsAt = section.getLong("ends-at");
                if (endsAt <= System.currentTimeMillis()) {
                    continue;
                }
                Set<String> targets = new LinkedHashSet<>(section.getStringList("targets"));
                active.put(type, new Active(type, section.getDouble("multiplier", 2.0D), endsAt, targets));
            }
        }

        ConfigurationSection scheduleSection = yml.getConfigurationSection("schedules");
        if (scheduleSection != null) {
            for (String id : scheduleSection.getKeys(false)) {
                ConfigurationSection section = scheduleSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                Set<BoostType> types = new LinkedHashSet<>();
                for (String raw : section.getStringList("types")) {
                    try {
                        types.add(BoostType.valueOf(raw.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        // 忽略
                    }
                }
                schedules.add(new Schedule(id,
                        section.getString("time", "20:00"),
                        section.getInt("seconds", 3600),
                        section.getDouble("multiplier", 2.0D),
                        types,
                        new LinkedHashSet<>(section.getStringList("targets")),
                        section.getString("last-fired", "")));
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<BoostType, Active> entry : active.entrySet()) {
            String base = "active." + entry.getKey().name().toLowerCase(Locale.ROOT) + ".";
            yml.set(base + "multiplier", entry.getValue().multiplier);
            yml.set(base + "ends-at", entry.getValue().endsAt);
            yml.set(base + "targets", new ArrayList<>(entry.getValue().targets));
        }
        for (Schedule schedule : schedules) {
            String base = "schedules." + schedule.id + ".";
            yml.set(base + "time", schedule.time);
            yml.set(base + "seconds", schedule.durationSeconds);
            yml.set(base + "multiplier", schedule.multiplier);
            yml.set(base + "targets", new ArrayList<>(schedule.targets));
            List<String> types = new ArrayList<>();
            for (BoostType type : schedule.types) {
                types.add(type.name().toLowerCase(Locale.ROOT));
            }
            yml.set(base + "types", types);
            yml.set(base + "last-fired", schedule.lastFired);
        }
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                return;
            }
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 boost.yml 失败: " + ex.getMessage());
        }
    }

    // ---------------- 工具 ----------------

    public static Set<BoostType> parseTypes(String raw) {
        Set<BoostType> types = new LinkedHashSet<>();
        if (raw == null) {
            return types;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("coin") || lower.equals("both")) {
            types.add(BoostType.COINS);
        }
        if (lower.contains("exp") || lower.equals("both")) {
            types.add(BoostType.EXP);
        }
        return types;
    }

    private static String format(double value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private void broadcast(String mini) {
        var component = Msg.mini(plugin.settings().prefix() + mini);
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }
}
