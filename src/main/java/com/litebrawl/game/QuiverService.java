package com.litebrawl.game;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 箭袋（quiver）机制。
 *
 * <p>箭仍然是真实物品，玩家射出一支后按固定间隔补回，补到上限为止。
 * 上限 = 配置的基础值 + 商店买的扩容（用掉一次死亡就清空，避免永久滚雪球）。</p>
 */
public final class QuiverService {

    private final LiteBrawlPlugin plugin;
    /** uuid -> 下一次补充的时间戳 */
    private final Map<UUID, Long> pending = new ConcurrentHashMap<>();
    /** uuid -> 商店扩容的额外格数 */
    private final Map<UUID, Integer> bonus = new HashMap<>();

    private BukkitTask task;

    public QuiverService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        pending.clear();
        bonus.clear();
    }

    /** 该玩家当前的箭袋上限。 */
    public int maxFor(Player player) {
        return plugin.settings().kitArrows() + bonus.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * 商店买「箭袋扩容」。
     *
     * @return 新的上限；已经到顶返回 -1
     */
    public int upgrade(Player player) {
        int current = bonus.getOrDefault(player.getUniqueId(), 0);
        if (current >= plugin.settings().quiverMaxBonus()) {
            return -1;
        }
        int next = current + 1;
        bonus.put(player.getUniqueId(), next);
        return plugin.settings().kitArrows() + next;
    }

    public void resetBonus(UUID uuid) {
        bonus.remove(uuid);
    }

    /** 射出一支箭后登记补充。 */
    public void onArrowShot(Player player) {
        if (count(player) >= maxFor(player)) {
            return;
        }
        pending.putIfAbsent(player.getUniqueId(), System.currentTimeMillis() + plugin.settings().arrowRegenMillis());
    }

    public void clear(UUID uuid) {
        pending.remove(uuid);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() > now) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            int max = maxFor(player);
            int have = count(player);
            if (have >= max) {
                iterator.remove();
                continue;
            }
            player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
            if (have + 1 >= max) {
                iterator.remove();
            } else {
                entry.setValue(now + plugin.settings().arrowRegenMillis());
            }
        }
    }

    public int count(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == Material.ARROW) {
                total += stack.getAmount();
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == Material.ARROW) {
            total += offhand.getAmount();
        }
        return total;
    }
}
