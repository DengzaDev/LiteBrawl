package com.litebrawl.listener;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/** 伤害归属与箭袋补充。 */
public final class CombatListener implements Listener {

    private final LiteBrawlPlugin plugin;

    public CombatListener(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    /** 击退倍率（config: combat.knockback-multiplier），1.0 = 原版力度。 */
    @EventHandler
    public void onVelocity(org.bukkit.event.player.PlayerVelocityEvent event) {
        double multiplier = plugin.settings().knockbackMultiplier();
        if (Math.abs(multiplier - 1.0D) < 0.001D) {
            return;
        }
        event.setVelocity(event.getVelocity().multiply(multiplier));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!plugin.game().isPlaying(victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (plugin.teams().sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.protection().isProtected(victim) || plugin.protection().isProtected(attacker)) {
            event.setCancelled(true);
            return;
        }
        plugin.combat().recordDamage(victim, attacker, event.getFinalDamage());
        plugin.combatTag().tag(victim);
        plugin.combatTag().tag(attacker);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player player)) {
            return;
        }
        if (!plugin.game().isPlaying(player)) {
            return;
        }
        plugin.quivers().onArrowShot(player);
    }

    /** 箭袋满时不再拾取地上的箭，避免突破上限。 */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getItem().getItemStack().getType() != Material.ARROW) {
            return;
        }
        if (!plugin.game().isPlaying(player)) {
            return;
        }
        if (plugin.quivers().count(player) >= plugin.quivers().maxFor(player)) {
            event.setCancelled(true);
        }
    }
}
