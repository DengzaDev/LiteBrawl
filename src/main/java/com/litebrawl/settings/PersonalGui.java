package com.litebrawl.settings;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.cosmetic.Cosmetic;
import com.litebrawl.cosmetic.CosmeticType;
import com.litebrawl.util.Msg;
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

/**
 * 个人设置：物品栏预设 + 外观（名字样式 / 称号 / 各种特效）。
 *
 * <p>外观那一排是「点一下切到下一个」，已购买的循环切换，切到末尾就是卸下，
 * 这样不用为每个类型再开子界面。</p>
 */
public final class PersonalGui implements Listener {

    private static final int SIZE = 36;
    private static final int SLOT_CLEAR = 35;

    private static final CosmeticType[] CATEGORIES = {
            CosmeticType.NAME_STYLE,
            CosmeticType.TITLE,
            CosmeticType.KILL_MESSAGE,
            CosmeticType.DEATH_MESSAGE,
            CosmeticType.KILL_EFFECT,
            CosmeticType.CHAT_EFFECT
    };

    private final LiteBrawlPlugin plugin;

    public PersonalGui(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final List<String> layoutIds = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Msg.mini("<dark_gray>个人设置"));
        holder.inventory = inventory;

        // 第一排：物品栏预设
        int slot = 0;
        String current = plugin.playerSettings().layout(player);
        for (var layout : plugin.settings().layouts()) {
            if (slot >= 9) {
                break;
            }
            inventory.setItem(slot, layoutItem(layout, layout.id().equalsIgnoreCase(current)));
            holder.layoutIds.add(layout.id());
            slot++;
        }

        // 第二排：外观类别
        int index = 9;
        for (CosmeticType type : CATEGORIES) {
            inventory.setItem(index++, cosmeticItem(player, type));
        }

        // 第三排：自己调物品栏槽位
        inventory.setItem(18, slotItem(player, "sword", "剑", Material.IRON_SWORD));
        inventory.setItem(19, slotItem(player, "bow", "弓", Material.BOW));
        inventory.setItem(20, slotItem(player, "arrows", "箭", Material.ARROW));
        inventory.setItem(21, slotItem(player, "food", "食物", Material.COOKED_BEEF));
        inventory.setItem(23, botsItem(player));
        inventory.setItem(25, simple(Material.BOOK, "<gold>排行榜面板</gold>",
                "<gray>左键切时间维度</gray>", "<gray>右键反向 / Shift 切统计项</gray>", "<yellow>点击打开</yellow>"));

        ItemStack clear = new ItemStack(Material.BARRIER);
        ItemMeta meta = clear.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini("<red>卸下全部外观</red>"));
            clear.setItemMeta(meta);
        }
        inventory.setItem(SLOT_CLEAR, clear);

        player.openInventory(inventory);
    }

    private ItemStack layoutItem(com.litebrawl.Settings.KitLayout layout, boolean selected) {
        ItemStack stack = new ItemStack(selected ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini((selected ? "<green>" : "<gray>") + layout.name()));
            meta.lore(List.of(
                    Msg.mini("<gray>剑 <white>" + layout.slot("sword", 0) + "</white>"
                            + " · 弓 <white>" + layout.slot("bow", 1) + "</white>"
                            + " · 箭 <white>" + layout.slot("arrows", 2) + "</white>"
                            + " · 食物 <white>" + layout.slot("food", 3) + "</white></gray>"),
                    Msg.mini(selected ? "<green>当前使用</green>" : "<yellow>点击使用</yellow>")
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 单个物品的槽位调节项。 */
    private ItemStack slotItem(Player player, String key, String label, Material material) {
        int slot = plugin.playerSettings().slot(player, key, switch (key) {
            case "bow" -> 1;
            case "arrows" -> 2;
            case "food" -> 3;
            default -> 0;
        });
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini("<white>" + label + "</white> <dark_gray>·</dark_gray> <yellow>第 "
                    + (slot + 1) + " 格</yellow>"));
            meta.lore(List.of(
                    Msg.mini("<gray>值 <white>" + slot + "</white>（0-8）</gray>"),
                    Msg.mini("<yellow>点击切到下一格</yellow>")
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 假人开关。 */
    private ItemStack botsItem(Player player) {
        boolean on = plugin.playerSettings().bots(player);
        ItemStack stack = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini("<white>显示假人</white>"));
            meta.lore(List.of(
                    Msg.mini(on ? "<green>已开启</green> <gray>（会被假人攻击）</gray>"
                            : "<red>已关闭</red> <gray>（看不见也不会被打）</gray>"),
                    Msg.mini("<dark_gray>点击切换</dark_gray>")
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack simple(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini(name));
            meta.lore(java.util.Arrays.stream(lore).map(Msg::mini).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack cosmeticItem(Player player, CosmeticType type) {
        String current = plugin.profiles().get(player).equippedCosmetic(type.name());
        Cosmetic equipped = plugin.cosmetics().get(current);
        long owned = plugin.cosmetics().ownedOf(player).stream()
                .filter(c -> c.type() == type).count();

        ItemStack stack = new ItemStack(equipped == null ? Material.GRAY_DYE : Material.LIME_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini("<white>" + type.label() + "</white>"));
            List<String> lore = new ArrayList<>();
            lore.add(equipped == null
                    ? "<dark_gray>未装备</dark_gray>"
                    : "<gray>当前：</gray>" + equipped.name());
            lore.add("<dark_gray>已拥有 " + owned + " 个</dark_gray>");
            lore.add(owned == 0 ? "<red>还没买，去 /shop</red>" : "<yellow>点击切换下一个</yellow>");
            meta.lore(lore.stream().map(Msg::mini).toList());
            stack.setItemMeta(meta);
        }
        return stack;
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

        if (slot < 9 && slot < holder.layoutIds.size()) {
            String id = holder.layoutIds.get(slot);
            plugin.playerSettings().setLayout(player, id);
            Msg.info(player, plugin.settings().prefix(),
                    "<green>物品栏预设已切换</green> <dark_gray>·</dark_gray> <white>%name%</white>"
                            + " <gray>(下一局生效)</gray>",
                    "name", plugin.settings().layout(id).name());
            open(player);
            return;
        }

        if (slot >= 9 && slot < 9 + CATEGORIES.length) {
            cycle(player, CATEGORIES[slot - 9]);
            open(player);
            return;
        }

        if (slot == 25) {
            player.closeInventory();
            plugin.boardPanel().open(player);
            return;
        }

        if (slot == 23) {
            boolean on = plugin.playerSettings().toggle(player,
                    PlayerSettingsService.KEY_BOTS, true);
            plugin.bots().refreshVisibility(player);
            Msg.info(player, plugin.settings().prefix(), on
                    ? "<green>已开启假人</green> <gray>（会被假人攻击）</gray>"
                    : "<gray>已关闭假人，看不见也不会被打。</gray>");
            open(player);
            return;
        }

        if (slot >= 18 && slot <= 21) {
            String key = switch (slot) {
                case 18 -> "sword";
                case 19 -> "bow";
                case 20 -> "arrows";
                default -> "food";
            };
            int current = plugin.playerSettings().slot(player, key, 0);
            plugin.playerSettings().setSlot(player, key, (current + 1) % 9);
            open(player);
            return;
        }

        if (slot == SLOT_CLEAR) {
            for (CosmeticType type : CosmeticType.values()) {
                plugin.cosmetics().unequip(player, type);
            }
            plugin.display().refresh(player);
            Msg.info(player, plugin.settings().prefix(), "<gray>已卸下全部外观。</gray>");
            open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    /** 在已拥有的同类外观之间循环，越界表示卸下。 */
    private void cycle(Player player, CosmeticType type) {
        List<Cosmetic> owned = plugin.cosmetics().ownedOf(player).stream()
                .filter(c -> c.type() == type)
                .toList();
        if (owned.isEmpty()) {
            Msg.info(player, plugin.settings().prefix(),
                    "<red>你还没有这个类型的外观，去 </red><white>/shop</white><red> 看看。</red>");
            return;
        }
        String current = plugin.profiles().get(player).equippedCosmetic(type.name());
        int index = -1;
        for (int i = 0; i < owned.size(); i++) {
            if (owned.get(i).id().equals(current)) {
                index = i;
                break;
            }
        }
        int next = index + 1;
        if (next >= owned.size()) {
            plugin.cosmetics().unequip(player, type);
            Msg.info(player, plugin.settings().prefix(),
                    "<gray>已卸下 %type%</gray>", "type", type.label());
        } else {
            plugin.cosmetics().equip(player, owned.get(next).id());
            Msg.info(player, plugin.settings().prefix(),
                    "<green>已装备</green> <white>%name%</white>",
                    "name", Msg.plain(Msg.mini(owned.get(next).name())));
        }
        plugin.display().refresh(player);
    }
}
