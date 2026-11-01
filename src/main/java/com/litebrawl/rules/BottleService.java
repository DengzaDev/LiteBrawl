package com.litebrawl.rules;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

/**
 * 自动没收玻璃瓶。
 *
 * <p>喝完药水原版会塞一个空瓶子进背包，占格子还可能被合成/丢出。
 * 这里三处一起管：喝完立刻清、捡到直接拦、定时再兜底扫一遍。</p>
 */
public final class BottleService implements Listener {

    private final LiteBrawlPlugin plugin;
    private BukkitTask task;

    public BottleService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.settings().confiscateBottles()) {
            return;
        }
        // 兜底：每 2 秒清一次（比如通过指令/其他方式拿到瓶子）
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sweepAll, 40L, 40L);
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

    private void sweepAll() {
        if (!plugin.settings().confiscateBottles()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sweep(player);
        }
    }

    /** 清掉玩家背包里的空瓶子，返回清掉的数量。 */
    public int sweep(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int removed = 0;
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == Material.GLASS_BOTTLE) {
                removed += stack.getAmount();
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) {
            inventory.setStorageContents(contents);
        }
        return removed;
    }

    /** 喝完药水：原版会把空瓶塞回背包，等它塞完（下一 tick）再清。 */
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!plugin.settings().confiscateBottles()) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                sweep(player);
            }
        });
    }

    /** 地上的空瓶子也捡不起来。 */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!plugin.settings().confiscateBottles()) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (event.getItem().getItemStack().getType() == Material.GLASS_BOTTLE) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }
}
