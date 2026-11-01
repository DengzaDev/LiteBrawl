package com.litebrawl.listener;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** 玩家生命周期：进服、死亡、重生、右键开始。 */
public final class PlayerListener implements Listener {

    private final LiteBrawlPlugin plugin;

    public PlayerListener(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.profiles().loadAsync(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.game().prepareLobby(player);
                plugin.sidebar().update(player);
                plugin.display().refresh(player);
                plugin.bots().refreshVisibility(player);
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.game().quit(player);
        plugin.teams().quit(player);
        plugin.sidebar().remove(player);
        plugin.profiles().unload(player);
    }

    /** 即死方块改由 LethalBlockService 轮询判定（站桩不移动时事件不会触发）。 */

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        plugin.game().handleDeath(event);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = plugin.maps().activeSpawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
        plugin.game().onRespawn(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (plugin.teams().isTeamItem(item)) {
            event.setCancelled(true);
            plugin.teams().openGui(event.getPlayer());
            return;
        }
        if (plugin.kits().isShopButton(item)) {
            event.setCancelled(true);
            plugin.shop().open(event.getPlayer());
            return;
        }
        if (plugin.kits().isCosmeticButton(item)) {
            event.setCancelled(true);
            plugin.cosmeticGui().open(event.getPlayer());
            return;
        }
        if (plugin.kits().isSettingsButton(item)) {
            event.setCancelled(true);
            plugin.settingsGui().open(event.getPlayer());
            return;
        }
        if (plugin.kits().isPersonalButton(item)) {
            event.setCancelled(true);
            plugin.personalGui().open(event.getPlayer());
            return;
        }
        if (plugin.kits().isLobbyButton(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (plugin.combatTag().inCombat(player)) {
                Msg.info(player, plugin.settings().prefix(),
                        "<red>战斗中无法返回大厅</red> <gray>(%seconds% 秒)</gray>",
                        "seconds", String.valueOf(plugin.combatTag().remainingSeconds(player)));
                return;
            }
            plugin.lobby().send(player);
            return;
        }
        if (!plugin.kits().isStartItem(item)) {
            return;
        }
        event.setCancelled(true);
        plugin.game().start(event.getPlayer());
    }
}
