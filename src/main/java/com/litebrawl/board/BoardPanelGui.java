package com.litebrawl.board;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.data.Period;
import com.litebrawl.data.Profile;
import com.litebrawl.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 排行榜面板：一个界面里看 击杀/死亡/等级/硬币 × 总/日/周/月/年。
 *
 * <p>左键 = 下一个时间维度，右键 = 上一个，Shift+左键 = 切换统计项。
 * 用背包界面而不是浮空文字，因为背包点击能精确区分左右键；
 * 浮空 TextDisplay 拿不到「左键」事件。</p>
 */
public final class BoardPanelGui implements Listener {

    private static final int SIZE = 27;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_HINT = 22;
    private static final int LIST_START = 9;
    private static final int LIST_SIZE = 9;

    /** 统计项：只有击杀/死亡有时间维度。 */
    private enum Stat {
        KILLS("击杀", "kills", true),
        DEATHS("死亡", "deaths", true),
        LEVEL("等级", "experience", false),
        COINS("硬币", "coins", false);

        private final String label;
        private final String column;
        private final boolean periodEnabled;

        Stat(String label, String column, boolean periodEnabled) {
            this.label = label;
            this.column = column;
            this.periodEnabled = periodEnabled;
        }
    }

    private static final Period[] PERIODS = {
            Period.ALL, Period.DAY, Period.WEEK, Period.MONTH, Period.YEAR
    };

    private final LiteBrawlPlugin plugin;
    private final Map<UUID, int[]> views = new java.util.HashMap<>();

    public BoardPanelGui(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private int[] view(UUID uuid) {
        return views.computeIfAbsent(uuid, k -> new int[]{0, 0});
    }

    public void open(Player player) {
        int[] view = view(player.getUniqueId());
        Stat[] stats = Stat.values();
        Stat stat = stats[Math.floorMod(view[0], stats.length)];
        Period period = PERIODS[Math.floorMod(view[1], PERIODS.length)];
        if (!stat.periodEnabled) {
            period = Period.ALL;
        }

        Holder holder = new Holder();
        String title = stat.label + " · " + period.label();
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Msg.mini("<dark_gray>排行榜</dark_gray> <dark_gray>|</dark_gray> <white>"
                        + title + "</white>"));
        holder.inventory = inventory;

        List<Profile> top = plugin.profiles().topPeriod(period, stat.column, LIST_SIZE);
        int rank = 1;
        for (Profile profile : top) {
            inventory.setItem(LIST_START + rank - 1, entry(rank, profile, stat));
            rank++;
        }
        if (top.isEmpty()) {
            inventory.setItem(LIST_START + 4, simple(Material.GRAY_DYE, "<gray>暂无数据</gray>"));
        }

        inventory.setItem(SLOT_INFO, simple(Material.NETHER_STAR,
                "<gold>" + stat.label + " · " + period.label() + "</gold>"));
        inventory.setItem(SLOT_HINT, simple(Material.PAPER, "<yellow>左键</yellow> <gray>下一个时间维度</gray>"));
        player.openInventory(inventory);
    }

    private ItemStack entry(int rank, Profile profile, Stat stat) {
        Material material = switch (rank) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.COPPER_BLOCK;
            default -> Material.PAPER;
        };
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini("<yellow>#" + rank + "</yellow> <white>" + profile.getName() + "</white>"));
            meta.lore(List.of(Msg.mini("<gray>数值：<white>" + value(profile, stat) + "</white></gray>")));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String value(Profile profile, Stat stat) {
        return switch (stat.column) {
            case "experience" -> String.valueOf(plugin.profiles().levelOf(profile.getExperience()));
            case "coins" -> String.valueOf(profile.getCoins());
            case "deaths" -> String.valueOf(profile.getDeaths());
            default -> String.valueOf(profile.getKills());
        };
    }

    private ItemStack simple(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini(name));
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

        int[] view = view(player.getUniqueId());
        Stat[] stats = Stat.values();
        Stat current = stats[Math.floorMod(view[0], stats.length)];

        ClickType click = event.getClick();
        if (click == ClickType.LEFT) {
            view[1]++;
        } else if (click == ClickType.RIGHT) {
            view[1]--;
        } else if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            view[0] += click == ClickType.SHIFT_LEFT ? 1 : -1;
            view[1] = 0;
        } else {
            return;
        }

        // 等级/硬币没有时间维度，切换时自动回落到总榜
        Stat next = stats[Math.floorMod(view[0], stats.length)];
        if (!next.periodEnabled && !current.periodEnabled) {
            view[1] = 0;
        }
        open(player);
    }

    /** 玩家退出时清掉他的浏览状态，避免 map 越积越大。 */
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        views.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

}
