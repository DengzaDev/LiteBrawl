package com.litebrawl.cosmetic;

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

import java.util.ArrayList;
import java.util.List;

/** 外观管理界面：列出已购买的外观，点击装备。 */
public final class CosmeticGui implements Listener {

    private static final int SIZE = 27;
    private static final int SLOT_CLEAR = 22;

    private final LiteBrawlPlugin plugin;

    public CosmeticGui(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final List<Cosmetic> items = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Msg.mini("<dark_gray>我的外观"));

        List<Cosmetic> owned = plugin.cosmetics().ownedOf(player);
        int slot = 10;
        for (Cosmetic cosmetic : owned) {
            if (slot >= SLOT_CLEAR - 1) {
                break;
            }
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                boolean equipped = cosmetic.id().equals(
                        plugin.profiles().get(player).equippedCosmetic(cosmetic.type().name()));
                meta.displayName(Msg.mini(cosmetic.name()));
                meta.lore(List.of(
                        Msg.mini("<gray>类型：</gray><white>" + cosmetic.type().label() + "</white>"),
                        Msg.mini(equipped ? "<green>已装备</green>" : "<yellow>点击装备</yellow>")
                ));
                stack.setItemMeta(meta);
            }
            inventory.setItem(slot++, stack);
            holder.items.add(cosmetic);
        }

        ItemStack clear = new ItemStack(Material.BARRIER);
        ItemMeta clearMeta = clear.getItemMeta();
        if (clearMeta != null) {
            clearMeta.displayName(Msg.mini("<red>全部卸下</red>"));
            clear.setItemMeta(clearMeta);
        }
        inventory.setItem(SLOT_CLEAR, clear);

        if (owned.isEmpty()) {
            ItemStack empty = new ItemStack(Material.CHEST);
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.mini("<gray>还没有外观</gray>"));
                meta.lore(List.of(Msg.mini("<gray>去 <white>/shop</white> 买吧</gray>")));
                empty.setItemMeta(meta);
            }
            inventory.setItem(13, empty);
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
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
        int slot = event.getRawSlot();
        if (slot == SLOT_CLEAR) {
            for (CosmeticType type : CosmeticType.values()) {
                plugin.cosmetics().unequip(player, type);
            }
            Msg.info(player, plugin.settings().prefix(), "<gray>已卸下全部外观。</gray>");
            player.closeInventory();
            return;
        }
        int index = slot - 10;
        if (index < 0 || index >= holder.items.size()) {
            return;
        }
        Cosmetic cosmetic = holder.items.get(index);
        String error = plugin.cosmetics().equip(player, cosmetic.id());
        if (error != null) {
            Msg.info(player, plugin.settings().prefix(), "<red>%error%</red>", "error", error);
        } else {
            Msg.info(player, plugin.settings().prefix(),
                    "<green>已装备</green> <white>%name%</white>",
                    "name", Msg.plain(Msg.mini(cosmetic.name())));
        }
        open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    static Component emptyLine() {
        return Component.empty();
    }
}
