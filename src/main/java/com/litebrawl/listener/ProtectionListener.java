package com.litebrawl.listener;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/** 地图保护与规则开关。 */
public final class ProtectionListener implements Listener {

    private final LiteBrawlPlugin plugin;

    public ProtectionListener(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canBuild(Player player) {
        return plugin.settings().allowBuild() || player.hasPermission("litebrawl.bypass.build");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 岩浆 / 水不蔓延。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (plugin.settings().allowLiquidFlow()) {
            return;
        }
        Material type = event.getBlock().getType();
        if (type == Material.LAVA || type == Material.WATER) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getNewState().getType() == Material.FIRE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        event.setCancelled(true);
    }

    /** 除玩家外不生成生物（盔甲架与展示实体不受影响）。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (plugin.settings().mobSpawn()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        if (event.getEntity() instanceof Player || event.getEntity() instanceof ArmorStand) {
            return;
        }
        if (plugin.bots().isSpawning()) {
            return;
        }
        event.setCancelled(true);
    }

    /** 岩浆 / 火焰免伤、可选的摔落免伤。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (plugin.protection().isProtected(player)) {
            event.setCancelled(true);
            player.setFireTicks(0);
            return;
        }
        if (!plugin.settings().lavaDamage()
                && (cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.MELTING)) {
            event.setCancelled(true);
            player.setFireTicks(0);
            return;
        }
        if (!plugin.settings().fallDamage() && cause == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.settings().allowItemDrop()) {
            return;
        }
        if (event.getPlayer().hasPermission("litebrawl.bypass.build")) {
            return;
        }
        event.setCancelled(true);
    }
}
