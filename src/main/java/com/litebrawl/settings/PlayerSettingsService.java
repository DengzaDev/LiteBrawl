package com.litebrawl.settings;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.data.Profile;
import org.bukkit.entity.Player;

/** 玩家个人设置的读写入口（持久化在 Profile 的 settings 列）。 */
public final class PlayerSettingsService {

    public static final String KEY_SIDEBAR = "sidebar";
    public static final String KEY_TAB = "tab";
    public static final String KEY_COMBAT_NOTIFY = "combat-notify";
    public static final String KEY_LAYOUT = "layout";
    public static final String KEY_BOTS = "bots";

    private final LiteBrawlPlugin plugin;

    public PlayerSettingsService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    private Profile profile(Player player) {
        return plugin.profiles().get(player);
    }

    public boolean sidebar(Player player) {
        return profile(player).settingBool(KEY_SIDEBAR, true);
    }

    public boolean tab(Player player) {
        return profile(player).settingBool(KEY_TAB, true);
    }

    public boolean combatNotify(Player player) {
        return profile(player).settingBool(KEY_COMBAT_NOTIFY, true);
    }

    public String layout(Player player) {
        return profile(player).setting(KEY_LAYOUT, plugin.settings().defaultLayoutId());
    }

    /** 是否显示并受到假人攻击（默认 true）。 */
    public boolean bots(Player player) {
        return profile(player).settingBool(KEY_BOTS, true);
    }

    /**
     * 取某个物品该放哪一格。
     *
     * <p>玩家自己调过的槽位优先，没调过就回落到预设布局。</p>
     */
    public int slot(Player player, String key, int fallback) {
        String raw = profile(player).setting("layout-" + key, null);
        if (raw != null) {
            try {
                return Math.max(0, Math.min(8, Integer.parseInt(raw)));
            } catch (NumberFormatException ignored) {
                // 配置坏了就回落到预设
            }
        }
        return plugin.settings().layout(layout(player)).slot(key, fallback);
    }

    public void setSlot(Player player, String key, int slot) {
        Profile profile = profile(player);
        profile.setSetting("layout-" + key, String.valueOf(Math.max(0, Math.min(8, slot))));
        plugin.profiles().saveAsync(profile);
    }

    public void clearCustomSlots(Player player) {
        Profile profile = profile(player);
        for (String key : new String[]{"sword", "bow", "arrows", "food"}) {
            profile.setSetting("layout-" + key, null);
        }
        plugin.profiles().saveAsync(profile);
    }

    public boolean toggle(Player player, String key, boolean fallback) {
        Profile profile = profile(player);
        boolean next = !profile.settingBool(key, fallback);
        profile.setSetting(key, next);
        plugin.profiles().saveAsync(profile);
        return next;
    }

    public void setLayout(Player player, String layoutId) {
        Profile profile = profile(player);
        profile.setSetting(KEY_LAYOUT, layoutId);
        profile.setSetting("layout-sword", null);
        profile.setSetting("layout-bow", null);
        profile.setSetting("layout-arrows", null);
        profile.setSetting("layout-food", null);
        plugin.profiles().saveAsync(profile);
    }
}
