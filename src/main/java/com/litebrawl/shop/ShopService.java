package com.litebrawl.shop;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.data.Profile;
import com.litebrawl.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;

/**
 * 硬币商店。
 *
 * <p>商品完全由 config.yml 的 shop.items 驱动：普通物品、药水（带效果）、
 * 以及特殊商品（箭袋扩容）都走同一套配置，加道具不用改代码。</p>
 */
public final class ShopService {

    private final LiteBrawlPlugin plugin;
    private final NamespacedKey priceKey;

    private final Map<String, ShopItem> byKey = new LinkedHashMap<>();
    /** 已买但还没发放的物品（进战场时统一给） */
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();
    private final Map<Integer, ShopItem> bySlot = new LinkedHashMap<>();
    private int rows = 3;

    public ShopService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        this.priceKey = new NamespacedKey(plugin, "shop_item");
    }

    public void load() {
        byKey.clear();
        bySlot.clear();
        rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("shop.rows", 3)));
        int size = rows * 9;

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String rawMaterial = entry.getString("material", "STONE");
            Material material = Material.matchMaterial(rawMaterial == null ? "STONE" : rawMaterial.toUpperCase(Locale.ROOT));
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("shop.items." + key + " 的 material 无效: " + rawMaterial);
                continue;
            }
            int slot = Math.max(0, Math.min(size - 1, entry.getInt("slot", 0)));
            if (bySlot.containsKey(slot)) {
                plugin.getLogger().warning("shop.items." + key + " 的 slot " + slot + " 已被占用，已跳过");
                continue;
            }

            PotionEffectType effect = null;
            String effectName = entry.getString("potion-effect", null);
            if (effectName != null && !effectName.isBlank()) {
                effect = PotionEffectType.getByName(effectName.trim().toUpperCase(Locale.ROOT));
                if (effect == null) {
                    plugin.getLogger().warning("shop.items." + key + " 的 potion-effect 无效: " + effectName);
                }
            }

            ShopItem.Special special = ShopItem.Special.NONE;
            String specialName = entry.getString("special", "");
            String cosmeticId = entry.getString("cosmetic", "");
            if (specialName != null && specialName.equalsIgnoreCase("quiver")) {
                special = ShopItem.Special.QUIVER;
            } else if (specialName != null && specialName.equalsIgnoreCase("cosmetic")) {
                if (plugin.cosmetics().get(cosmeticId) == null) {
                    plugin.getLogger().warning("shop.items." + key + " 的 cosmetic 无效: " + cosmeticId);
                    continue;
                }
                special = ShopItem.Special.COSMETIC;
            }

            ShopItem item = new ShopItem(key, slot, material,
                    entry.getInt("amount", 1),
                    entry.getLong("price", 0L),
                    entry.getString("name", key),
                    entry.getStringList("lore"),
                    effect,
                    entry.getInt("duration", 200),
                    entry.getInt("amplifier", 0),
                    special,
                    cosmeticId);
            byKey.put(key.toLowerCase(Locale.ROOT), item);
            bySlot.put(slot, item);
        }
    }

    public Map<String, ShopItem> items() {
        return byKey;
    }

    public void open(Player player) {
        if (plugin.settings().shopLobbyOnly() && plugin.game().isPlaying(player)) {
            Msg.info(player, plugin.settings().prefix(),
                    "<red>战斗中不能打开商店，回大厅再买。</red>");
            return;
        }
        String title = plugin.getConfig().getString("shop.title", "<dark_gray>杂货铺");
        ShopHolder holder = new ShopHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Msg.mini(title));
        holder.inventory(inventory);

        for (ShopItem item : bySlot.values()) {
            inventory.setItem(item.slot(), render(item));
        }
        inventory.setItem(rows * 9 - 5, balanceItem(player));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6F, 1.2F);
    }

    private ItemStack render(ShopItem item) {
        ItemStack stack = new ItemStack(item.material(), item.amount());
        ItemMeta rawMeta = stack.getItemMeta();
        if (rawMeta == null) {
            return stack;
        }
        ItemMeta meta = rawMeta;
        if (item.potionEffect() != null && rawMeta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(PotionType.WATER);
            potionMeta.addCustomEffect(
                    new PotionEffect(item.potionEffect(), item.duration(), item.amplifier()), true);
            meta = potionMeta;
        }
        meta.displayName(Msg.mini(item.name()));
        List<Component> lore = new ArrayList<>();
        for (String line : item.lore()) {
            lore.add(Msg.mini(line.replace("%price%", String.valueOf(item.price()))));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(priceKey, PersistentDataType.STRING, item.key());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack balanceItem(Player player) {
        Profile profile = plugin.profiles().get(player);
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini("<gold>我的硬币</gold>"));
            meta.lore(List.of(
                    Msg.mini("<gray>余额：<gold>" + profile.getCoins() + "</gold></gray>"),
                    Msg.mini("<gray>等级：<yellow>Lv." + plugin.profiles().levelOf(profile.getExperience()) + "</yellow></gray>")
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 取走待发放物品（进战场时调用）。 */
    public List<ItemStack> consumePending(Player player) {
        List<ItemStack> items = pending.remove(player.getUniqueId());
        return items == null ? List.of() : items;
    }

    public int pendingCount(Player player) {
        List<ItemStack> items = pending.get(player.getUniqueId());
        return items == null ? 0 : items.size();
    }

    public ShopItem itemOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String key = meta.getPersistentDataContainer().get(priceKey, PersistentDataType.STRING);
        return key == null ? null : byKey.get(key.toLowerCase(Locale.ROOT));
    }

    /** @return 是否购买成功 */
    public boolean purchase(Player player, ShopItem item) {
        Profile profile = plugin.profiles().get(player);

        if (item.special() == ShopItem.Special.COSMETIC) {
            var cosmetic = plugin.cosmetics().get(item.cosmeticId());
            if (cosmetic == null) {
                Msg.info(player, plugin.settings().prefix(), "<red>这个外观不存在了。</red>");
                return false;
            }
            if (plugin.cosmetics().owns(player, cosmetic)) {
                Msg.info(player, plugin.settings().prefix(),
                        "<red>你已经拥有</red> <white>%name%</white><red> 了。</red>",
                        "name", Msg.plain(Msg.mini(cosmetic.name())));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6F, 1.0F);
                return false;
            }
        }

        if (item.special() == ShopItem.Special.QUIVER) {
            int maxPossible = plugin.settings().kitArrows() + plugin.settings().quiverMaxBonus();
            if (plugin.quivers().maxFor(player) >= maxPossible) {
                Msg.info(player, plugin.settings().prefix(),
                        "<red>箭袋已经到上限了（%max% 支）。</red>", "max", String.valueOf(maxPossible));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6F, 1.0F);
                return false;
            }
        }

        if (profile.getCoins() < item.price()) {
            Msg.info(player, plugin.settings().prefix(),
                    "<red>硬币不足</red> <dark_gray>·</dark_gray> <gray>需要 <gold>%price%</gold>，你有 <gold>%have%</gold></gray>",
                    "price", String.valueOf(item.price()),
                    "have", String.valueOf(profile.getCoins()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6F, 1.0F);
            return false;
        }

        profile.takeCoins(item.price());
        plugin.profiles().saveAsync(profile);

        if (item.special() == ShopItem.Special.COSMETIC) {
            var cosmetic = plugin.cosmetics().get(item.cosmeticId());
            plugin.cosmetics().unlockAndEquip(player, cosmetic);
            Msg.info(player, plugin.settings().prefix(),
                    "<green>已解锁并装备</green> <white>%name%</white> <dark_gray>·</dark_gray> <gold>-%price%</gold> <gray>硬币</gray>",
                    "name", Msg.plain(Msg.mini(cosmetic.name())),
                    "price", String.valueOf(item.price()));
        } else if (item.special() == ShopItem.Special.QUIVER) {
            int newMax = plugin.quivers().upgrade(player);
            Msg.info(player, plugin.settings().prefix(),
                    "<green>箭袋上限提升到</green> <white>%max%</white> <gray>支</gray> <dark_gray>·</dark_gray> <gold>-%price%</gold> <gray>硬币</gray>",
                    "max", String.valueOf(newMax),
                    "price", String.valueOf(item.price()));
        } else {
            ItemStack stack = new ItemStack(item.material(), item.amount());
            ItemMeta rawMeta = stack.getItemMeta();
            if (rawMeta != null) {
                ItemMeta meta = rawMeta;
                if (item.potionEffect() != null && rawMeta instanceof PotionMeta potionMeta) {
                    potionMeta.setBasePotionType(PotionType.WATER);
                    potionMeta.addCustomEffect(
                            new PotionEffect(item.potionEffect(), item.duration(), item.amplifier()), true);
                    meta = potionMeta;
                }
                meta.displayName(Msg.mini(item.name()));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                stack.setItemMeta(meta);
            }
            // 不立刻发放：进待发放队列，下一次进战场时由 giveKit 一起给
            pending.computeIfAbsent(player.getUniqueId(), key -> new ArrayList<>()).add(stack);
            Msg.info(player, plugin.settings().prefix(),
                    "<green>已购买</green> <white>%name%</white> <dark_gray>·</dark_gray> <gold>-%price%</gold> <gray>硬币</gray>",
                    "name", Msg.plain(Msg.mini(item.name())),
                    "price", String.valueOf(item.price()));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.4F);
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof ShopHolder) {
            top.setItem(rows * 9 - 5, balanceItem(player));
        }
        return true;
    }

    /** 商店 GUI 的持有者标记，用于安全识别点击来源。 */
    public static final class ShopHolder implements InventoryHolder {

        private Inventory inventory;

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
