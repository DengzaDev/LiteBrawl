package com.litebrawl.game;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * 即死方块（默认竹马赛克）。
 *
 * <p>不用 PlayerMoveEvent 判定，改成每 5 tick 轮询一次当前位置。
 * 原因：站在方块上不动、被传送、方块被人塞到脚下时都不会触发移动事件，
 * 事件驱动的写法在这些情况下会静默失效。轮询的代价是每个在线玩家每 0.25 秒
 * 两次 getBlockAt，可以忽略。</p>
 */
public final class LethalBlockService {

    private static final int CHECK_TICKS = 5;

    private final LiteBrawlPlugin plugin;
    private BukkitTask task;

    public LethalBlockService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, CHECK_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (plugin.settings().lethalBlocks().isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.hasPermission("litebrawl.bypass.lethal")) {
                continue;
            }
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world == null) {
                continue;
            }
            Material below = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1,
                    location.getBlockZ()).getType();
            Material feet = world.getBlockAt(location.getBlockX(), location.getBlockY(),
                    location.getBlockZ()).getType();
            if (plugin.settings().isLethal(below) || plugin.settings().isLethal(feet)) {
                kill(player);
            }
        }
        checkBots();
    }

    /** 假人踩到即死方块也要死（僵尸走位同样吃这条规则）。 */
    private void checkBots() {
        if (!plugin.settings().lethalAffectsBots()) {
            return;
        }
        for (Entity entity : plugin.bots().entities()) {
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                continue;
            }
            Location location = living.getLocation();
            World world = location.getWorld();
            if (world == null) {
                continue;
            }
            Material below = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1,
                    location.getBlockZ()).getType();
            Material feet = world.getBlockAt(location.getBlockX(), location.getBlockY(),
                    location.getBlockZ()).getType();
            if (plugin.settings().isLethal(below) || plugin.settings().isLethal(feet)) {
                living.setHealth(0.0D);
            }
        }
    }

    private void kill(Player player) {
        if (player.isDead()) {
            return;
        }
        player.setHealth(0.0D);
        if (player.isDead()) {
            return;
        }
        // 创造模式对伤害免疫，这里临时切回生存再判定，否则这条规则对被 op 的测试账号永远无效
        if (player.getGameMode() == GameMode.CREATIVE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.damage(1.0E9D);
    }
}
