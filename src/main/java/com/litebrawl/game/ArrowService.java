package com.litebrawl.game;

import org.bukkit.event.player.PlayerPickupArrowEvent;
import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * 箭矢规则：不能捡、落地 3 秒后自动消失。
 *
 * <p>箭由箭袋机制自动补充，地面上的箭只是「残留物」，不该被拾取也不该长期堆积。
 * 两种形态都处理：插在地上的 Arrow 实体，以及掉在地上的箭头物品。</p>
 */
public final class ArrowService implements Listener {

    private final LiteBrawlPlugin plugin;

    public ArrowService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    /** 插在地上的箭：禁止拾取，N 秒后移除。 */
    @EventHandler
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        scheduleRemoval(arrow);
    }

    /** 掉在地上的箭头物品：N 秒后移除。 */
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (item.getItemStack().getType() == Material.ARROW) {
            scheduleRemoval(item);
        }
    }

    /** 普通拾取也拦掉，避免通过 Shift 点击等方式绕过。 */
    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (event.getItem().getItemStack().getType() == Material.ARROW) {
            event.setCancelled(true);
        }
    }

    private void scheduleRemoval(org.bukkit.entity.Entity entity) {
        long ticks = Math.max(1, plugin.settings().arrowGroundSeconds()) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                entity.remove();
            }
        }, ticks);
    }
}
