package com.litebrawl.game;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.Settings;
import com.litebrawl.util.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** 发装备、发大厅物品。 */
public final class KitService {

    private final LiteBrawlPlugin plugin;
    private final NamespacedKey startKey;
    private final NamespacedKey shopKey;
    private final NamespacedKey cosmeticKey;
    private final NamespacedKey lobbyKey;
    private final NamespacedKey settingsKey;
    private final NamespacedKey personalKey;

    public KitService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        this.startKey = new NamespacedKey(plugin, "start_item");
        this.shopKey = new NamespacedKey(plugin, "shop_item_button");
        this.cosmeticKey = new NamespacedKey(plugin, "cosmetic_item_button");
        this.lobbyKey = new NamespacedKey(plugin, "lobby_item_button");
        this.settingsKey = new NamespacedKey(plugin, "settings_button");
        this.personalKey = new NamespacedKey(plugin, "personal_button");
    }

    /** 清空并发放整套基础装备。 */
    public void giveKit(Player player) {
        Settings settings = plugin.settings();
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[]{null, null, null, null});
        inventory.setItemInOffHand(null);

        // 玩家自定义槽位优先，没调过就用预设布局
        inventory.setItem(plugin.playerSettings().slot(player, "sword", 0),
                gear(settings.kitSword(), settings.swordEnchants()));
        inventory.setItem(plugin.playerSettings().slot(player, "bow", 1),
                gear(settings.kitBow(), settings.bowEnchants()));
        inventory.setItem(plugin.playerSettings().slot(player, "arrows", 2),
                new ItemStack(Material.ARROW, settings.kitArrows()));
        if (settings.kitFood() != null) {
            inventory.setItem(plugin.playerSettings().slot(player, "food", 3),
                    new ItemStack(settings.kitFood(), settings.kitFoodAmount()));
        }

        if (settings.kitBoots() != null) {
            inventory.setBoots(gear(settings.kitBoots()));
        }
        if (settings.kitLeggings() != null) {
            inventory.setLeggings(gear(settings.kitLeggings()));
        }
        if (settings.kitChestplate() != null) {
            inventory.setChestplate(gear(settings.kitChestplate()));
        }
        if (settings.kitHelmet() != null) {
            inventory.setHelmet(gear(settings.kitHelmet()));
        }
    }

    /** 大厅：开始 / 组队 / 商店 / 外观 / 返回大厅。 */
    public void giveLobbyItems(Player player) {
        Settings settings = plugin.settings();
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(settings.startSlot(), startItem());
        inventory.setItem(settings.teamItemSlot(), plugin.teams().teamItem());
        inventory.setItem(settings.shopItemSlot(), button(settings.shopItemMaterial(),
                settings.shopItemName(), shopKey, "<gray>右键打开物品商店</gray>"));
        inventory.setItem(settings.cosmeticItemSlot(), button(settings.cosmeticItemMaterial(),
                settings.cosmeticItemName(), cosmeticKey, "<gray>右键管理已购买的外观</gray>"));
        inventory.setItem(settings.lobbyItemSlot(), button(settings.lobbyItemMaterial(),
                settings.lobbyItemName(), lobbyKey, "<gray>右键返回大厅</gray>"));
        inventory.setItem(settings.settingsItemSlot(), button(settings.settingsItemMaterial(),
                settings.settingsItemName(), settingsKey, "<gray>右键打开设置</gray>"));
        inventory.setItem(settings.personalItemSlot(), button(settings.personalItemMaterial(),
                settings.personalItemName(), personalKey, "<gray>右键打开个人设置（外观 / 物品栏预设）</gray>"));
    }

    /** 大厅物品：右键开始。 */
    public ItemStack startItem() {
        Settings settings = plugin.settings();
        ItemStack stack = new ItemStack(settings.startMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini(settings.startName()));
            meta.lore(settings.startLore().stream().map(Msg::mini).toList());
            meta.getPersistentDataContainer().set(startKey, PersistentDataType.STRING, "true");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack button(Material material, String name, NamespacedKey key, String lore) {
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini(name));
            meta.lore(List.of(Msg.mini(lore)));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "true");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isStartItem(ItemStack stack) {
        return marked(stack, startKey);
    }

    public boolean isShopButton(ItemStack stack) {
        return marked(stack, shopKey);
    }

    public boolean isCosmeticButton(ItemStack stack) {
        return marked(stack, cosmeticKey);
    }

    public boolean isLobbyButton(ItemStack stack) {
        return marked(stack, lobbyKey);
    }

    public boolean isSettingsButton(ItemStack stack) {
        return marked(stack, settingsKey);
    }

    public boolean isPersonalButton(ItemStack stack) {
        return marked(stack, personalKey);
    }

    private boolean marked(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    private ItemStack gear(Material material) {
        return gear(material, java.util.Map.of());
    }

    private ItemStack gear(Material material,
                           java.util.Map<org.bukkit.enchantments.Enchantment, Integer> enchants) {
        if (material == null || material == Material.AIR) {
            return new ItemStack(Material.AIR);
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            boolean changed = false;
            if (plugin.settings().kitUnbreakable() && material.getMaxDurability() > 0) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
                changed = true;
            }
            if (enchants != null && !enchants.isEmpty()) {
                for (var entry : enchants.entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
                changed = true;
            }
            if (changed) {
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }
}
