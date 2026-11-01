package com.litebrawl.data;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据缓存 + 等级换算。
 *
 * <p>等级由累计经验纯函数计算，不入库，避免两处数值漂移。</p>
 */
public final class ProfileService {

    private final LiteBrawlPlugin plugin;
    private final Map<UUID, Profile> cache = new ConcurrentHashMap<>();
    private BukkitTask autoSaveTask;

    public ProfileService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public Profile get(Player player) {
        Profile profile = cache.get(player.getUniqueId());
        if (profile == null) {
            profile = load(player.getUniqueId(), player.getName());
        } else {
            profile.setName(player.getName());
        }
        return profile;
    }

    public Profile getIfLoaded(UUID uuid) {
        return cache.get(uuid);
    }

    public Profile load(UUID uuid, String name) {
        Profile profile = plugin.storage().find(uuid).orElseGet(() -> new Profile(uuid, name));
        profile.setName(name);
        cache.put(uuid, profile);
        return profile;
    }

    /** 异步预载，避免主线程读库阻塞。 */
    public void loadAsync(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> load(uuid, name));
    }

    public void save(Profile profile) {
        if (profile == null) {
            return;
        }
        plugin.storage().save(profile);
    }

    public void saveAsync(Profile profile) {
        if (profile == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> save(profile));
    }

    public void unload(Player player) {
        Profile profile = cache.remove(player.getUniqueId());
        saveAsync(profile);
    }

    public void startAutoSave() {
        long ticks = plugin.settings().storageSaveInterval() * 20L;
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Profile profile : cache.values()) {
                if (profile.isDirty()) {
                    plugin.storage().save(profile);
                }
            }
        }, ticks, ticks);
    }

    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        for (Profile profile : new ArrayList<>(cache.values())) {
            plugin.storage().save(profile);
        }
        cache.clear();
    }

    public List<Profile> top(String column, int limit) {
        return plugin.storage().top(column, limit);
    }

    /** 记一次击杀（同时写日/周/月/年四个桶）。 */
    public void recordKill(Profile profile) {
        record(profile, "kills");
    }

    /** 记一次死亡。 */
    public void recordDeath(Profile profile) {
        record(profile, "deaths");
    }

    private void record(Profile profile, String column) {
        if (profile == null) {
            return;
        }
        for (Period period : Period.tracked()) {
            plugin.storage().recordStat(profile.getUuid(), profile.getName(), period.key(), column, 1);
        }
    }

    /** 按周期取榜；ALL 走总数表。 */
    public List<Profile> topPeriod(Period period, String column, int limit) {
        String key = period == null ? null : period.key();
        if (key == null) {
            return top(column, limit);
        }
        return plugin.storage().topPeriod(key, column, limit);
    }

    /** 累计经验换算等级（1 级起步，0 经验）。 */
    public int levelOf(long experience) {
        int max = plugin.settings().levelMax();
        double base = plugin.settings().levelBaseExp();
        double growth = plugin.settings().levelGrowth();
        int level = 1;
        long accumulated = 0L;
        double step = base;
        while (level < max) {
            accumulated += Math.max(1L, Math.round(step));
            if (experience < accumulated) {
                break;
            }
            level++;
            step *= growth;
        }
        return level;
    }

    /** 当前等级内的经验进度，0..1。 */
    public double progressOf(long experience) {
        int level = levelOf(experience);
        long lower = experienceForLevel(level);
        long upper = experienceForLevel(level + 1);
        if (upper <= lower) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (double) (experience - lower) / (double) (upper - lower)));
    }

    /** 达到某个等级所需的累计经验。 */
    public long experienceForLevel(int level) {
        if (level <= 1) {
            return 0L;
        }
        double base = plugin.settings().levelBaseExp();
        double growth = plugin.settings().levelGrowth();
        long accumulated = 0L;
        double step = base;
        for (int i = 1; i < level && i < plugin.settings().levelMax(); i++) {
            accumulated += Math.max(1L, Math.round(step));
            step *= growth;
        }
        return accumulated;
    }
}
