package com.litebrawl.settings;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** 通用设置界面：记分板 / Tab / 战斗提示。 */
public final class SettingsGui implements Listener {

    private static final int SIZE = 27;
    private static final int SLOT_SIDEBAR = 11;
    private static final int SLOT_TAB = 13;
    private static final int SLOT_COMBAT = 15;

    private final LiteBrawlPlugin plugin;

    public SettingsGui(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Msg.mini("<dark_gray>设置"));
        holder.inventory = inventory;

        PlayerSettingsService s = plugin.playerSettings();
        inventory.setItem(SLOT_SIDEBAR, toggle(Material.PAPER, "记分板", s.sidebar(player)));
        inventory.setItem(SLOT_TAB, toggle(Material.PAPER, "Tab 列表信息", s.tab(player)));
        inventory.setItem(SLOT_COMBAT, toggle(Material.PAPER, "战斗提示", s.combatNotify(player)));

        player.openInventory(inventory);
    }

    private ItemStack toggle(Material material, String label, boolean on) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini((on ? "<green>" : "<gray>") + label));
            meta.lore(List.of(
                    Msg.mini(on ? "<green>已开启</green>" : "<red>已关闭</red>"),
                    Msg.mini("<dark_gray>点击切换</dark_gray>")
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        PlayerSettingsService s = plugin.playerSettings();
        switch (event.getRawSlot()) {
            case SLOT_SIDEBAR -> {
                boolean on = s.toggle(player, PlayerSettingsService.KEY_SIDEBAR, true);
                if (on) {
                    plugin.sidebar().update(player);
                } else {
                    plugin.sidebar().remove(player);
                }
                Msg.info(player, plugin.settings().prefix(), on
                        ? "<green>记分板已开启</green>" : "<gray>记分板已关闭</gray>");
            }
            case SLOT_TAB -> {
                boolean on = s.toggle(player, PlayerSettingsService.KEY_TAB, true);
                if (on) {
                    plugin.tab().refresh(player);
                } else {
                    player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
                }
                Msg.info(player, plugin.settings().prefix(), on
                        ? "<green>Tab 信息已开启</green>" : "<gray>Tab 信息已关闭</gray>");
            }
            case SLOT_COMBAT -> {
                boolean on = s.toggle(player, PlayerSettingsService.KEY_COMBAT_NOTIFY, true);
                Msg.info(player, plugin.settings().prefix(), on
                        ? "<green>战斗提示已开启</green>" : "<gray>战斗提示已关闭</gray>");
            }
            default -> {
                return;
            }
        }
        open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
