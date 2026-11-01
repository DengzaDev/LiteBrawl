package com.litebrawl.game;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * 清理世界里已有的生物。
 *
 * <p>光取消 EntitySpawnEvent 只能挡住「新生成」，从存档区块里读出来的旧怪不会触发那个事件，
 * 所以这里再加两道：启动时全量扫一遍、区块加载时扫该区块，外加一个定时兜底。</p>
 *
 * <p>假人（tag = litebrawl:bot）和 NPC 盔甲架会被跳过。</p>
 */
public final class MobCleanupService implements Listener {

    private static final String BOT_TAG = "litebrawl:bot";

    private final LiteBrawlPlugin plugin;
    private BukkitTask task;

    public MobCleanupService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!shouldClean()) {
            return;
        }
        sweepAll();
        long ticks = plugin.settings().mobCleanupSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepAll, ticks, ticks);
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

    private boolean shouldClean() {
        return !plugin.settings().mobSpawn() && plugin.settings().removeExistingMobs();
    }

    private void sweepAll() {
        if (!shouldClean()) {
            return;
        }
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            removed += sweep(world);
        }
        if (removed > 0) {
            plugin.getLogger().info("清理了 " + removed + " 个世界生物");
        }
    }

    private int sweep(World world) {
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (shouldRemove(entity)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!shouldClean()) {
            return;
        }
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (shouldRemove(entity)) {
                entity.remove();
            }
        }
    }

    /** 玩家、假人、盔甲架（NPC）以外的 LivingEntity 都要清掉。 */
    private boolean shouldRemove(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        if (entity instanceof Player || entity instanceof ArmorStand) {
            return false;
        }
        return !entity.getScoreboardTags().contains(BOT_TAG);
    }
}
