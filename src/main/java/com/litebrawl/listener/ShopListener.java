package com.litebrawl.listener;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.shop.ShopItem;
import com.litebrawl.shop.ShopService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** 商店 GUI 点击处理。 */
public final class ShopListener implements Listener {

    private final LiteBrawlPlugin plugin;

    public ShopListener(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopService.ShopHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ShopItem item = plugin.shop().itemOf(event.getCurrentItem());
        if (item == null) {
            return;
        }
        plugin.shop().purchase(player, item);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopService.ShopHolder) {
            event.setCancelled(true);
        }
    }
}
