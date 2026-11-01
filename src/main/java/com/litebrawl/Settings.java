package com.litebrawl;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * config.yml 的类型化视图。
 * 所有 getter 直接返回解析后的值，业务代码不再碰原始配置键。
 */
public final class Settings {

    private final LiteBrawlPlugin plugin;

    private String prefix = "<gray>[LiteBrawl]</gray> ";

    private Material startMaterial = Material.NETHER_STAR;
    private int startSlot = 8;
    private String startName = "<green>开始游戏</green>";
    private List<String> startLore = List.of();

    private Material kitHelmet = null;
    private Material kitChestplate = Material.IRON_CHESTPLATE;
    private Material kitLeggings = Material.IRON_LEGGINGS;
    private Material kitBoots = Material.IRON_BOOTS;
    private Material kitSword = Material.IRON_SWORD;
    private Material kitBow = Material.BOW;
    private Material kitFood = Material.COOKED_BEEF;
    private java.util.Map<org.bukkit.enchantments.Enchantment, Integer> bowEnchants = new java.util.LinkedHashMap<>();
    private java.util.Map<org.bukkit.enchantments.Enchantment, Integer> swordEnchants = new java.util.LinkedHashMap<>();
    private int kitArrows = 4;
    private int kitFoodAmount = 64;
    private int arrowRegenSeconds = 5;
    private int quiverMaxBonus = 2;
    private boolean kitUnbreakable = true;

    private Material spawnBlock = Material.EMERALD_BLOCK;
    private Set<Material> lethalBlocks = EnumSet.of(Material.BAMBOO_MOSAIC,
            Material.BAMBOO_MOSAIC_SLAB, Material.BAMBOO_MOSAIC_STAIRS);
    private int spawnYOffset = 2;
    private int scanRadius = 160;
    private int scanYMin = -64;
    private int scanYMax = 320;

    private boolean keepInventory = true;
    private boolean allowBuild = false;
    private boolean allowLiquidFlow = false;
    private boolean lavaDamage = false;
    private boolean mobSpawn = false;
    private boolean removeExistingMobs = true;
    private boolean confiscateBottles = true;
    private boolean lethalAffectsBots = true;
    private int mobCleanupSeconds = 10;
    private boolean allowItemDrop = false;
    private boolean fallDamage = true;
    private String deathMessage = "<gray>%victim%</gray> 被 <white>%killer%</white> 击败了";
    private String deathMessageUnknown = "<gray>%victim%</gray> 阵亡了";

    private long killCoins = 5;
    private long killExp = 5;
    private int killGoldenApples = 1;
    private long assistCoins = 2;
    private long assistExp = 2;
    private int assistWindowSeconds = 8;
    private double assistMinDamage = 4.0D;

    private long levelBaseExp = 100L;
    private double levelGrowth = 1.35D;
    private int levelMax = 50;
    private String levelFormat = "<gray>[Lv.%level%]</gray>";
    private String titleSuffix = " <yellow>[%title%]</yellow>";
    private boolean nametagLevel = true;
    private boolean healthDisplay = true;
    private int healthMaxHearts = 10;
    private String healthFormat = "<red>%full%</red><dark_gray>%empty%</dark_gray>";
    private String healthSeparator = " ";
    private int displayRefreshTicks = 20;

    private boolean chatEnabled = true;
    private String chatFormat = "%level% <white>%player%</white><gray>:</gray> %message%";

    private int boardUpdateSeconds = 30;
    private int boardMaxLines = 10;
    private String boardTitle = "<gold>%board%</gold>";
    private String boardLine = "<gray>%rank%.</gray> <white>%player%</white> <yellow>%value%</yellow>";
    private String boardEmpty = "<dark_gray>暂无数据</dark_gray>";
    private String boardTypeLabelsRaw = null;
    private ConfigurationSection boardTypesSection = null;

    private boolean sidebarEnabled = true;
    private int sidebarUpdateSeconds = 1;
    private String sidebarTitle = "<gold><bold>LiteBrawl</bold></gold>";
    private String sidebarStatusPlaying = "<green>在场</green>";
    private String sidebarStatusIdle = "<gray>待机</gray>";
    private List<String> sidebarLines = defaultSidebarLines();

    private boolean bountyEnabled = true;
    private int bountyMinStreak = 5;
    private long bountyBase = 20L;
    private long bountyPerKill = 10L;
    private String bountyAnnounceNew =
            "<gold>赏金</gold> <yellow>%player%</yellow> <gray>连杀 %streak%，悬赏</gray> <gold>%bounty%</gold> <gray>硬币</gray>";
    private String bountyAnnounceClaim =
            "<gold>赏金</gold> <white>%killer%</white> <gray>拿下了</gray> <white>%victim%</white> <gray>的悬赏</gray> <gold>%amount%</gold> <gray>硬币</gray>";

    private boolean rotationEnabled = true;
    private int rotationSeconds = 600;
    private int rotationAnnounceSeconds = 30;
    private String rotationMode = "ORDER";
    private String rotationAnnounce = "<gray>%seconds% 秒后轮换地图…</gray>";
    private String rotationSwitched =
            "<green>地图已切换：</green><white>%map%</white> <dark_gray>(%world%)</dark_gray>";

    private Material teamItemMaterial = Material.COMPASS;
    private int teamItemSlot = 7;
    private String teamItemName = "<aqua><bold>组队</bold></aqua>";
    private List<String> teamItemLore = List.of("<gray>右键创建队伍 / 邀请队友</gray>");
    private String teamGuiTitle = "<dark_gray>组队";
    private int teamMaxSize = 8;
    private int teamInviteSeconds = 60;

    private int protectionSeconds = 3;
    private boolean protectionBlockDamage = true;

    private boolean hotfixAutoLoad = true;

    private boolean tabEnabled = true;
    private int tabUpdateSeconds = 5;
    private String tabHeader = "<gradient:#7ee8fa:#80ff72><bold>LiteBrawl</bold></gradient>";
    private String tabFooter = "<gray>地图 <white>%map%</white></gray>";

    private List<String> sidebarFooter = List.of();

    private boolean shopLobbyOnly = true;

    private Material shopItemMaterial = Material.CHEST;
    private int shopItemSlot = 6;
    private String shopItemName = "<gold><bold>物品商店</bold></gold>";

    private Material cosmeticItemMaterial = Material.EMERALD;
    private int cosmeticItemSlot = 5;
    private String cosmeticItemName = "<green><bold>外观商城</bold></green>";

    private Material lobbyItemMaterial = Material.REDSTONE;
    private int lobbyItemSlot = 4;
    private String lobbyItemName = "<red><bold>返回大厅</bold></red>";
    private String lobbyServer = "";

    private boolean combatNoAttackCooldown = true;
    private int combatTagSeconds = 15;
    private boolean combatTagNotify = true;
    private int arrowGroundSeconds = 3;
    private double combatAttackSpeed = 1024.0D;
    private double knockbackMultiplier = 1.0D;

    private Material settingsItemMaterial = Material.COMPARATOR;
    private int settingsItemSlot = 3;
    private String settingsItemName = "<yellow><bold>设置</bold></yellow>";
    private Material personalItemMaterial = Material.NAME_TAG;
    private int personalItemSlot = 2;
    private String personalItemName = "<light_purple><bold>个人设置</bold></light_purple>";

    /** 物品栏预设 id -> 布局 */
    private final java.util.Map<String, KitLayout> kitLayouts = new java.util.LinkedHashMap<>();
    private String defaultLayoutId = "default";

    private String boostStartMessage = "<gold>双倍%type%</gold> <gray>开启</gray> <dark_gray>·</dark_gray> <white>x%multiplier%</white> <gray>持续 %seconds% 秒（%scope%）</gray>";
    private String boostEndMessage = "<gray>%type%加成已结束。</gray>";
    private boolean boostStreakEnabled = true;
    private int boostStreakCount = 10;
    private int boostStreakSeconds = 300;
    private double boostStreakMultiplier = 2.0D;
    private boolean boostStreakOnce = true;
    private java.util.Set<com.litebrawl.boost.BoostService.BoostType> boostStreakTypes =
            java.util.EnumSet.of(com.litebrawl.boost.BoostService.BoostType.COINS,
                    com.litebrawl.boost.BoostService.BoostType.EXP);
    private boolean boostCountEnabled = false;
    private int boostCountThreshold = 8;
    private int boostCountSeconds = 600;
    private double boostCountMultiplier = 2.0D;
    private java.util.Set<com.litebrawl.boost.BoostService.BoostType> boostCountTypes =
            java.util.EnumSet.of(com.litebrawl.boost.BoostService.BoostType.COINS,
                    com.litebrawl.boost.BoostService.BoostType.EXP);

    private boolean botsEnabled = true;
    private boolean botsAuto = true;
    private int botsMinPlayers = 3;
    private int botsCount = 4;
    private int botsMasterPercent = 50;
    private boolean botsRespawn = true;
    private boolean botsSilent = true;
    private int botsRespawnSeconds = 5;
    private double botMasterDamage = 4.0D;
    private long botMasterCoins = 2L;
    private long botMasterExp = 2L;
    private long botNoviceCoins = 1L;
    private long botNoviceExp = 1L;

    private String storageType = "SQLITE";
    private String mysqlHost = "127.0.0.1";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "litebrawl";
    private String mysqlUser = "root";
    private String mysqlPassword = "";
    private String mysqlTable = "litebrawl_profiles";
    private boolean mysqlUseSsl = false;
    private int storageSaveInterval = 300;

    private boolean antiFarmEnabled = true;
    private int antiFarmWindowSeconds = 600;
    private double antiFarmDecay = 0.5D;
    private double antiFarmMinFactor = 0.1D;

    private String proxyMode = "NONE";
    private List<String> proxyServers = new ArrayList<>();
    private String proxyDefaultServer = "lobby";
    private String proxyServerName = "";

    public Settings(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();

        prefix = c.getString("prefix", prefix);

        startMaterial = material(c, "start-item.material", Material.NETHER_STAR);
        startSlot = clamp(c.getInt("start-item.slot", 8), 0, 35);
        startName = c.getString("start-item.name", startName);
        startLore = new ArrayList<>(c.getStringList("start-item.lore"));

        kitHelmet = materialOrNull(c, "kit.helmet");
        kitChestplate = materialOrNull(c, "kit.chestplate");
        kitLeggings = materialOrNull(c, "kit.leggings");
        kitBoots = materialOrNull(c, "kit.boots");
        kitSword = materialOrNull(c, "kit.sword");
        kitBow = materialOrNull(c, "kit.bow");
        kitFood = materialOrNull(c, "kit.food");
        bowEnchants = parseEnchants(c, "kit.bow-enchants", "power");
        swordEnchants = parseEnchants(c, "kit.sword-enchants", null);
        kitArrows = Math.max(1, c.getInt("kit.arrows", 4));
        kitFoodAmount = Math.max(1, c.getInt("kit.food-amount", 64));
        arrowRegenSeconds = Math.max(1, c.getInt("kit.arrow-regen-seconds", 5));
        quiverMaxBonus = Math.max(0, c.getInt("kit.max-arrow-bonus", 2));
        kitUnbreakable = c.getBoolean("kit.unbreakable", true);

        spawnBlock = material(c, "spawn.block", Material.EMERALD_BLOCK);
        Set<Material> lethal = EnumSet.noneOf(Material.class);
        for (String raw : c.getStringList("spawn.lethal-blocks")) {
            Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (m == null) {
                plugin.getLogger().warning("spawn.lethal-blocks 中的方块不存在: " + raw);
                continue;
            }
            lethal.add(m);
        }
        if (lethal.isEmpty()) {
            lethal.add(Material.BAMBOO_MOSAIC);
            lethal.add(Material.BAMBOO_MOSAIC_SLAB);
            lethal.add(Material.BAMBOO_MOSAIC_STAIRS);
        }
        lethalBlocks = lethal;
        spawnYOffset = c.getInt("spawn.y-offset", 2);
        scanRadius = Math.max(16, c.getInt("spawn.scan-radius", 160));
        scanYMin = c.getInt("spawn.scan-y-min", -64);
        scanYMax = c.getInt("spawn.scan-y-max", 320);

        keepInventory = c.getBoolean("rules.keep-inventory", true);
        allowBuild = c.getBoolean("rules.allow-build", false);
        allowLiquidFlow = c.getBoolean("rules.allow-liquid-flow", false);
        lavaDamage = c.getBoolean("rules.lava-damage", false);
        mobSpawn = c.getBoolean("rules.mob-spawn", false);
        removeExistingMobs = c.getBoolean("rules.remove-existing-mobs", true);
        confiscateBottles = c.getBoolean("rules.confiscate-glass-bottles", true);
        lethalAffectsBots = c.getBoolean("spawn.lethal-affects-bots", true);
        mobCleanupSeconds = Math.max(5, Math.min(300, c.getInt("rules.mob-cleanup-seconds", 10)));
        allowItemDrop = c.getBoolean("rules.allow-item-drop", false);
        fallDamage = c.getBoolean("rules.fall-damage", true);
        deathMessage = c.getString("rules.death-message", deathMessage);
        deathMessageUnknown = c.getString("rules.death-message-unknown", deathMessageUnknown);

        killCoins = c.getLong("rewards.kill.coins", 5);
        killExp = c.getLong("rewards.kill.exp", 5);
        killGoldenApples = c.getInt("rewards.kill.golden-apples", 1);
        assistCoins = c.getLong("rewards.assist.coins", 2);
        assistExp = c.getLong("rewards.assist.exp", 2);
        assistWindowSeconds = Math.max(1, c.getInt("rewards.assist-window-seconds", 8));
        assistMinDamage = Math.max(0.0D, c.getDouble("rewards.assist-min-damage", 4.0D));

        levelBaseExp = Math.max(1L, c.getLong("level.base-exp", 100));
        levelGrowth = Math.max(1.0D, c.getDouble("level.growth", 1.35D));
        levelMax = Math.max(1, c.getInt("level.max-level", 50));
        levelFormat = c.getString("level.format", levelFormat);
        titleSuffix = c.getString("display.title-suffix", titleSuffix);
        nametagLevel = c.getBoolean("display.nametag-level", true);
        healthDisplay = c.getBoolean("display.health.enabled", true);
        healthMaxHearts = Math.max(1, Math.min(30, c.getInt("display.health.max-hearts", 10)));
        healthFormat = c.getString("display.health.format", healthFormat);
        healthSeparator = c.getString("display.health.separator", " ");
        displayRefreshTicks = Math.max(5, Math.min(200, c.getInt("display.refresh-ticks", 20)));

        chatEnabled = c.getBoolean("chat.enabled", true);
        chatFormat = c.getString("chat.format", chatFormat);

        boardUpdateSeconds = Math.max(5, c.getInt("boards.update-seconds", 30));
        boardMaxLines = Math.max(1, Math.min(15, c.getInt("boards.max-lines", 10)));
        boardTitle = c.getString("boards.title", boardTitle);
        boardLine = c.getString("boards.line", boardLine);
        boardEmpty = c.getString("boards.empty-line", boardEmpty);
        boardTypesSection = c.getConfigurationSection("boards.types");

        sidebarEnabled = c.getBoolean("scoreboard.enabled", true);
        sidebarUpdateSeconds = Math.max(1, Math.min(20, c.getInt("scoreboard.update-seconds", 1)));
        sidebarTitle = c.getString("scoreboard.title", sidebarTitle);
        sidebarStatusPlaying = c.getString("scoreboard.status-playing", sidebarStatusPlaying);
        sidebarStatusIdle = c.getString("scoreboard.status-idle", sidebarStatusIdle);
        List<String> lines = new ArrayList<>(c.getStringList("scoreboard.lines"));
        sidebarLines = lines.isEmpty() ? defaultSidebarLines() : List.copyOf(lines);

        bountyEnabled = c.getBoolean("bounty.enabled", true);
        bountyMinStreak = Math.max(1, c.getInt("bounty.min-killstreak", 5));
        bountyBase = Math.max(0L, c.getLong("bounty.base", 20));
        bountyPerKill = Math.max(0L, c.getLong("bounty.per-kill", 10));
        bountyAnnounceNew = c.getString("bounty.announce-new", bountyAnnounceNew);
        bountyAnnounceClaim = c.getString("bounty.announce-claim", bountyAnnounceClaim);

        rotationEnabled = c.getBoolean("rotation.enabled", true);
        rotationMode = c.getString("rotation.mode", "ORDER").trim().toUpperCase(Locale.ROOT);
        rotationSeconds = Math.max(60, c.getInt("rotation.seconds", 600));
        rotationAnnounceSeconds = Math.max(0, Math.min(300, c.getInt("rotation.announce-seconds", 30)));
        rotationAnnounce = c.getString("rotation.announce", rotationAnnounce);
        rotationSwitched = c.getString("rotation.switched", rotationSwitched);

        teamItemMaterial = material(c, "team.item.material", Material.COMPASS);
        teamItemSlot = clamp(c.getInt("team.item.slot", 7), 0, 35);
        teamItemName = c.getString("team.item.name", teamItemName);
        teamItemLore = new ArrayList<>(c.getStringList("team.item.lore"));
        teamGuiTitle = c.getString("team.gui-title", teamGuiTitle);
        teamMaxSize = Math.max(2, Math.min(16, c.getInt("team.max-size", 8)));
        teamInviteSeconds = Math.max(10, Math.min(600, c.getInt("team.invite-seconds", 60)));

        protectionSeconds = Math.max(0, Math.min(60, c.getInt("protection.seconds", 3)));
        protectionBlockDamage = c.getBoolean("protection.block-dealing", true);

        hotfixAutoLoad = c.getBoolean("hotfix.auto-load", true);

        tabEnabled = c.getBoolean("tab.enabled", true);
        tabUpdateSeconds = Math.max(1, Math.min(60, c.getInt("tab.update-seconds", 5)));
        tabHeader = c.getString("tab.header", tabHeader);
        tabFooter = c.getString("tab.footer", tabFooter);

        List<String> footerLines = new ArrayList<>(c.getStringList("scoreboard.footer"));
        if (footerLines.isEmpty()) {
            String rawFooter = c.getString("scoreboard.footer", "");
            if (rawFooter != null && !rawFooter.isBlank()) {
                footerLines = new ArrayList<>(List.of(rawFooter.split("\n")));
            }
        }
        sidebarFooter = List.copyOf(footerLines);

        shopLobbyOnly = c.getBoolean("shop.lobby-only", true);

        shopItemMaterial = material(c, "lobby-items.shop.material", Material.CHEST);
        shopItemSlot = clamp(c.getInt("lobby-items.shop.slot", 6), 0, 35);
        shopItemName = c.getString("lobby-items.shop.name", shopItemName);

        cosmeticItemMaterial = material(c, "lobby-items.cosmetic.material", Material.EMERALD);
        cosmeticItemSlot = clamp(c.getInt("lobby-items.cosmetic.slot", 5), 0, 35);
        cosmeticItemName = c.getString("lobby-items.cosmetic.name", cosmeticItemName);

        lobbyItemMaterial = material(c, "lobby-items.return.material", Material.REDSTONE);
        lobbyItemSlot = clamp(c.getInt("lobby-items.return.slot", 4), 0, 35);
        lobbyItemName = c.getString("lobby-items.return.name", lobbyItemName);
        lobbyServer = c.getString("lobby-items.return.server", "");

        combatNoAttackCooldown = c.getBoolean("combat.no-attack-cooldown", true);
        combatTagSeconds = Math.max(0, Math.min(120, c.getInt("combat.tag-seconds", 15)));
        combatTagNotify = c.getBoolean("combat.tag-notify", true);
        arrowGroundSeconds = Math.max(1, Math.min(60, c.getInt("combat.arrow-ground-seconds", 3)));
        combatAttackSpeed = Math.max(4.0D, c.getDouble("combat.attack-speed", 1024.0D));
        knockbackMultiplier = Math.max(0.0D, Math.min(4.0D,
                c.getDouble("combat.knockback-multiplier", 1.0D)));

        settingsItemMaterial = material(c, "lobby-items.settings.material", Material.COMPARATOR);
        settingsItemSlot = clamp(c.getInt("lobby-items.settings.slot", 3), 0, 35);
        settingsItemName = c.getString("lobby-items.settings.name", settingsItemName);
        personalItemMaterial = material(c, "lobby-items.personal.material", Material.NAME_TAG);
        personalItemSlot = clamp(c.getInt("lobby-items.personal.slot", 2), 0, 35);
        personalItemName = c.getString("lobby-items.personal.name", personalItemName);
        loadKitLayouts(c);

        boostStartMessage = c.getString("boost.start-message", boostStartMessage);
        boostEndMessage = c.getString("boost.end-message", boostEndMessage);
        boostStreakEnabled = c.getBoolean("boost.triggers.killstreak.enabled", true);
        boostStreakCount = Math.max(2, c.getInt("boost.triggers.killstreak.streak", 10));
        boostStreakSeconds = Math.max(10, c.getInt("boost.triggers.killstreak.seconds", 300));
        boostStreakMultiplier = Math.max(1.0D, c.getDouble("boost.triggers.killstreak.multiplier", 2.0D));
        boostStreakOnce = c.getBoolean("boost.triggers.killstreak.once", true);
        boostStreakTypes = parseBoostTypes(c.getStringList("boost.triggers.killstreak.types"));
        boostCountEnabled = c.getBoolean("boost.triggers.player-count.enabled", false);
        boostCountThreshold = Math.max(2, c.getInt("boost.triggers.player-count.count", 8));
        boostCountSeconds = Math.max(10, c.getInt("boost.triggers.player-count.seconds", 600));
        boostCountMultiplier = Math.max(1.0D, c.getDouble("boost.triggers.player-count.multiplier", 2.0D));
        boostCountTypes = parseBoostTypes(c.getStringList("boost.triggers.player-count.types"));

        botsEnabled = c.getBoolean("bots.enabled", true);
        botsAuto = c.getBoolean("bots.auto", true);
        botsMinPlayers = Math.max(1, c.getInt("bots.min-players", 3));
        botsCount = Math.max(0, Math.min(32, c.getInt("bots.count", 4)));
        botsMasterPercent = Math.max(0, Math.min(100, c.getInt("bots.master-percent", 50)));
        botsRespawn = c.getBoolean("bots.respawn", true);
        botsSilent = c.getBoolean("bots.silent", true);
        botsRespawnSeconds = Math.max(1, Math.min(120, c.getInt("bots.respawn-seconds", 5)));
        botMasterDamage = Math.max(1.0D, c.getDouble("bots.master-damage", 4.0D));
        botMasterCoins = c.getLong("bots.reward.master.coins", 2L);
        botMasterExp = c.getLong("bots.reward.master.exp", 2L);
        botNoviceCoins = c.getLong("bots.reward.novice.coins", 1L);
        botNoviceExp = c.getLong("bots.reward.novice.exp", 1L);

        storageType = c.getString("storage.type", "SQLITE").trim().toUpperCase(Locale.ROOT);
        storageSaveInterval = Math.max(30, c.getInt("storage.save-interval-seconds", 300));
        mysqlHost = c.getString("storage.mysql.host", mysqlHost);
        mysqlPort = c.getInt("storage.mysql.port", 3306);
        mysqlDatabase = c.getString("storage.mysql.database", mysqlDatabase);
        mysqlUser = c.getString("storage.mysql.user", mysqlUser);
        mysqlPassword = c.getString("storage.mysql.password", "");
        mysqlTable = sanitizeTable(c.getString("storage.mysql.table", mysqlTable));
        mysqlUseSsl = c.getBoolean("storage.mysql.use-ssl", false);

        antiFarmEnabled = c.getBoolean("anti-farm.enabled", true);
        antiFarmWindowSeconds = Math.max(10, c.getInt("anti-farm.window-seconds", 600));
        antiFarmDecay = Math.max(0.05D, Math.min(0.95D, c.getDouble("anti-farm.decay", 0.5D)));
        antiFarmMinFactor = Math.max(0.0D, Math.min(1.0D, c.getDouble("anti-farm.min-factor", 0.1D)));

        proxyMode = c.getString("proxy.mode", "NONE").trim().toUpperCase(Locale.ROOT);
        proxyServers = new ArrayList<>(c.getStringList("proxy.servers"));
        proxyDefaultServer = c.getString("proxy.default", "lobby");
        proxyServerName = c.getString("proxy.server-name", "");

        if (!proxyMode.equals("NONE")) {
            plugin.getLogger().warning("proxy.mode = " + proxyMode
                    + " 尚未实现，当前版本按单服模式运行（跨服接口已预留）。");
        }
    }

    private Material material(FileConfiguration c, String path, Material def) {
        Material m = materialOrNull(c, path);
        return m == null ? def : m;
    }

    /** 读取方块，NONE / 空值 / 非法值返回 null。 */
    private Material materialOrNull(FileConfiguration c, String path) {
        String raw = c.getString(path);
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("NONE") || raw.equalsIgnoreCase("AIR")) {
            return null;
        }
        Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (m == null) {
            plugin.getLogger().warning("未知方块 " + path + " = " + raw + "，已忽略。");
        }
        return m;
    }

    /**
     * 解析附魔配置，例如：
     * <pre>
     * kit:
     *   bow-enchants:
     *     power: 1
     * </pre>
     * 空段落表示不加附魔；默认值用 defaultKey（配置缺失时给一个默认附魔）。
     */
    private java.util.Map<org.bukkit.enchantments.Enchantment, Integer> parseEnchants(
            FileConfiguration c, String path, String defaultKey) {
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> result = new java.util.LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection(path);
        if (section == null) {
            if (defaultKey != null) {
                org.bukkit.enchantments.Enchantment enchantment = findEnchant(defaultKey);
                if (enchantment != null) {
                    result.put(enchantment, 1);
                }
            }
            return result;
        }
        for (String key : section.getKeys(false)) {
            int level = section.getInt(key, 0);
            if (level <= 0) {
                continue;
            }
            org.bukkit.enchantments.Enchantment enchantment = findEnchant(key);
            if (enchantment == null) {
                plugin.getLogger().warning("未知附魔 " + path + "." + key);
                continue;
            }
            result.put(enchantment, level);
        }
        return result;
    }

    private static org.bukkit.enchantments.Enchantment findEnchant(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        try {
            var enchantment = org.bukkit.Registry.ENCHANTMENT.get(
                    new org.bukkit.NamespacedKey("minecraft", normalized));
            if (enchantment != null) {
                return enchantment;
            }
        } catch (RuntimeException ignored) {
            // 回落到旧接口
        }
        return org.bukkit.enchantments.Enchantment.getByName(normalized.toUpperCase(Locale.ROOT));
    }

    private static String sanitizeTable(String table) {
        if (table == null || table.isBlank()) {
            return "litebrawl_profiles";
        }
        return table.replaceAll("[^A-Za-z0-9_]", "");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 物品栏预设。 */
    public static final class KitLayout {
        private final String id;
        private final String name;
        private final java.util.Map<String, Integer> slots;

        public KitLayout(String id, String name, java.util.Map<String, Integer> slots) {
            this.id = id;
            this.name = name;
            this.slots = slots;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public int slot(String key, int fallback) {
            Integer value = slots.get(key);
            return value == null ? fallback : value;
        }
    }

    private void loadKitLayouts(FileConfiguration c) {
        kitLayouts.clear();
        ConfigurationSection root = c.getConfigurationSection("kit-layouts");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                java.util.Map<String, Integer> slots = new java.util.LinkedHashMap<>();
                ConfigurationSection slotSection = section.getConfigurationSection("slots");
                if (slotSection != null) {
                    for (String key : slotSection.getKeys(false)) {
                        slots.put(key.toLowerCase(Locale.ROOT),
                                Math.max(0, Math.min(35, slotSection.getInt(key))));
                    }
                }
                kitLayouts.put(id.toLowerCase(Locale.ROOT),
                        new KitLayout(id, section.getString("name", id), slots));
            }
        }
        if (kitLayouts.isEmpty()) {
            java.util.Map<String, Integer> slots = new java.util.LinkedHashMap<>();
            slots.put("sword", 0);
            slots.put("bow", 1);
            slots.put("arrows", 2);
            slots.put("food", 3);
            kitLayouts.put("default", new KitLayout("default", "默认", slots));
        }
        defaultLayoutId = kitLayouts.containsKey("default")
                ? "default" : kitLayouts.keySet().iterator().next();
    }

    public KitLayout layout(String id) {
        KitLayout layout = id == null ? null : kitLayouts.get(id.toLowerCase(Locale.ROOT));
        return layout != null ? layout : kitLayouts.get(defaultLayoutId);
    }

    public java.util.Collection<KitLayout> layouts() {
        return kitLayouts.values();
    }

    public String defaultLayoutId() {
        return defaultLayoutId;
    }

    private static List<String> defaultSidebarLines() {
        return List.of(
                "<gray>玩家 <white>%player%</white></gray>",
                "<gray>状态 %status%</gray>",
                " ",
                "<gray>等级 <yellow>Lv.%level%</yellow> <dark_gray>(%exp% exp)</dark_gray></gray>",
                "<gray>硬币 <gold>%coins%</gold></gray>",
                " ",
                "<gray>击杀 <white>%kills%</white> <dark_gray>·</dark_gray> 死亡 <white>%deaths%</white></gray>",
                "<gray>助杀 <white>%assists%</white> <dark_gray>·</dark_gray> K/D <white>%kd%</white></gray>",
                " ",
                "<gray>战场 <white>%alive%</white> 人</gray>");
    }

    // ---------------- getters ----------------

    public String prefix() {
        return prefix;
    }

    public Material startMaterial() {
        return startMaterial;
    }

    public int startSlot() {
        return startSlot;
    }

    public String startName() {
        return startName;
    }

    public List<String> startLore() {
        return startLore;
    }

    public Material kitHelmet() {
        return kitHelmet;
    }

    public Material kitChestplate() {
        return kitChestplate;
    }

    public Material kitLeggings() {
        return kitLeggings;
    }

    public Material kitBoots() {
        return kitBoots;
    }

    public Material kitSword() {
        return kitSword;
    }

    public Material kitBow() {
        return kitBow;
    }

    public Material kitFood() {
        return kitFood;
    }

    public int kitArrows() {
        return kitArrows;
    }

    public java.util.Map<org.bukkit.enchantments.Enchantment, Integer> bowEnchants() {
        return bowEnchants;
    }

    public java.util.Map<org.bukkit.enchantments.Enchantment, Integer> swordEnchants() {
        return swordEnchants;
    }

    public int kitFoodAmount() {
        return kitFoodAmount;
    }

    public int arrowRegenSeconds() {
        return arrowRegenSeconds;
    }

    public long arrowRegenMillis() {
        return arrowRegenSeconds * 1000L;
    }

    public int quiverMaxBonus() {
        return quiverMaxBonus;
    }

    public boolean kitUnbreakable() {
        return kitUnbreakable;
    }

    public Material spawnBlock() {
        return spawnBlock;
    }

    public Set<Material> lethalBlocks() {
        return lethalBlocks;
    }

    /**
     * 是否属于即死方块。
     *
     * <p>配置里写基础方块名时，自动连带它的台阶/楼梯等变体
     * （例如 BAMBOO_MOSAIC 会同时命中 BAMBOO_MOSAIC_SLAB / BAMBOO_MOSAIC_STAIRS），
     * 免得玩家用台阶踩上去却杀不掉。</p>
     */
    public boolean isLethal(Material type) {
        if (type == null) {
            return false;
        }
        if (lethalBlocks.contains(type)) {
            return true;
        }
        for (Material base : lethalBlocks) {
            if (type.name().startsWith(base.name() + "_")) {
                return true;
            }
        }
        return false;
    }

    public int spawnYOffset() {
        return spawnYOffset;
    }

    public int scanRadius() {
        return scanRadius;
    }

    public int scanYMin() {
        return scanYMin;
    }

    public int scanYMax() {
        return scanYMax;
    }

    public boolean keepInventory() {
        return keepInventory;
    }

    public boolean allowBuild() {
        return allowBuild;
    }

    public boolean allowLiquidFlow() {
        return allowLiquidFlow;
    }

    public boolean lavaDamage() {
        return lavaDamage;
    }

    public boolean mobSpawn() {
        return mobSpawn;
    }

    public boolean removeExistingMobs() {
        return removeExistingMobs;
    }

    public boolean confiscateBottles() {
        return confiscateBottles;
    }

    public boolean lethalAffectsBots() {
        return lethalAffectsBots;
    }

    public int mobCleanupSeconds() {
        return mobCleanupSeconds;
    }

    public boolean allowItemDrop() {
        return allowItemDrop;
    }

    public boolean fallDamage() {
        return fallDamage;
    }

    public String deathMessage() {
        return deathMessage;
    }

    public String deathMessageUnknown() {
        return deathMessageUnknown;
    }

    public long killCoins() {
        return killCoins;
    }

    public long killExp() {
        return killExp;
    }

    public int killGoldenApples() {
        return killGoldenApples;
    }

    public long assistCoins() {
        return assistCoins;
    }

    public long assistExp() {
        return assistExp;
    }

    public int assistWindowSeconds() {
        return assistWindowSeconds;
    }

    public long assistWindowMillis() {
        return assistWindowSeconds * 1000L;
    }

    public double assistMinDamage() {
        return assistMinDamage;
    }

    public long levelBaseExp() {
        return levelBaseExp;
    }

    public double levelGrowth() {
        return levelGrowth;
    }

    public int levelMax() {
        return levelMax;
    }

    public String levelFormat() {
        return levelFormat;
    }

    public String titleSuffix() {
        return titleSuffix;
    }

    public boolean nametagLevel() {
        return nametagLevel;
    }

    public boolean healthDisplay() {
        return healthDisplay;
    }

    public int healthMaxHearts() {
        return healthMaxHearts;
    }

    public String healthFormat() {
        return healthFormat;
    }

    public String healthSeparator() {
        return healthSeparator;
    }

    public int displayRefreshTicks() {
        return displayRefreshTicks;
    }

    public boolean chatEnabled() {
        return chatEnabled;
    }

    public String chatFormat() {
        return chatFormat;
    }

    public int boardUpdateSeconds() {
        return boardUpdateSeconds;
    }

    public int boardMaxLines() {
        return boardMaxLines;
    }

    public String boardTitle() {
        return boardTitle;
    }

    public String boardLine() {
        return boardLine;
    }

    public String boardEmpty() {
        return boardEmpty;
    }

    public ConfigurationSection boardTypesSection() {
        return boardTypesSection;
    }

    public boolean sidebarEnabled() {
        return sidebarEnabled;
    }

    public int sidebarUpdateSeconds() {
        return sidebarUpdateSeconds;
    }

    public String sidebarTitle() {
        return sidebarTitle;
    }

    public String sidebarStatusPlaying() {
        return sidebarStatusPlaying;
    }

    public String sidebarStatusIdle() {
        return sidebarStatusIdle;
    }

    public List<String> sidebarLines() {
        return sidebarLines;
    }

    public boolean bountyEnabled() {
        return bountyEnabled;
    }

    public int bountyMinStreak() {
        return bountyMinStreak;
    }

    public long bountyBase() {
        return bountyBase;
    }

    public long bountyPerKill() {
        return bountyPerKill;
    }

    public String bountyAnnounceNew() {
        return bountyAnnounceNew;
    }

    public String bountyAnnounceClaim() {
        return bountyAnnounceClaim;
    }

    public boolean rotationEnabled() {
        return rotationEnabled;
    }

    public int rotationSeconds() {
        return rotationSeconds;
    }

    public int rotationAnnounceSeconds() {
        return rotationAnnounceSeconds;
    }

    public String rotationAnnounce() {
        return rotationAnnounce;
    }

    public String rotationSwitched() {
        return rotationSwitched;
    }

    public Material teamItemMaterial() {
        return teamItemMaterial;
    }

    public int teamItemSlot() {
        return teamItemSlot;
    }

    public String teamItemName() {
        return teamItemName;
    }

    public List<String> teamItemLore() {
        return teamItemLore;
    }

    public String teamGuiTitle() {
        return teamGuiTitle;
    }

    public int teamMaxSize() {
        return teamMaxSize;
    }

    public int teamInviteSeconds() {
        return teamInviteSeconds;
    }

    public int protectionSeconds() {
        return protectionSeconds;
    }

    public boolean protectionBlockDamage() {
        return protectionBlockDamage;
    }

    public boolean hotfixAutoLoad() {
        return hotfixAutoLoad;
    }

    public boolean tabEnabled() {
        return tabEnabled;
    }

    public int tabUpdateSeconds() {
        return tabUpdateSeconds;
    }

    public String tabHeader() {
        return tabHeader;
    }

    public String tabFooter() {
        return tabFooter;
    }

    public List<String> sidebarFooter() {
        return sidebarFooter;
    }

    public boolean shopLobbyOnly() {
        return shopLobbyOnly;
    }

    public Material shopItemMaterial() {
        return shopItemMaterial;
    }

    public int shopItemSlot() {
        return shopItemSlot;
    }

    public String shopItemName() {
        return shopItemName;
    }

    public Material cosmeticItemMaterial() {
        return cosmeticItemMaterial;
    }

    public int cosmeticItemSlot() {
        return cosmeticItemSlot;
    }

    public String cosmeticItemName() {
        return cosmeticItemName;
    }

    public Material lobbyItemMaterial() {
        return lobbyItemMaterial;
    }

    public int lobbyItemSlot() {
        return lobbyItemSlot;
    }

    public String lobbyItemName() {
        return lobbyItemName;
    }

    public String lobbyServer() {
        return lobbyServer;
    }

    public Material settingsItemMaterial() {
        return settingsItemMaterial;
    }

    public int settingsItemSlot() {
        return settingsItemSlot;
    }

    public String settingsItemName() {
        return settingsItemName;
    }

    public Material personalItemMaterial() {
        return personalItemMaterial;
    }

    public int personalItemSlot() {
        return personalItemSlot;
    }

    public String personalItemName() {
        return personalItemName;
    }

    public boolean combatNoAttackCooldown() {
        return combatNoAttackCooldown;
    }

    public double combatAttackSpeed() {
        return combatAttackSpeed;
    }

    public int combatTagSeconds() {
        return combatTagSeconds;
    }

    public boolean combatTagNotify() {
        return combatTagNotify;
    }

    public int arrowGroundSeconds() {
        return arrowGroundSeconds;
    }

    private static java.util.Set<com.litebrawl.boost.BoostService.BoostType> parseBoostTypes(
            java.util.List<String> raw) {
        java.util.Set<com.litebrawl.boost.BoostService.BoostType> types = java.util.EnumSet.noneOf(
                com.litebrawl.boost.BoostService.BoostType.class);
        for (String item : raw) {
            String lower = item.toLowerCase(Locale.ROOT);
            if (lower.startsWith("coin")) {
                types.add(com.litebrawl.boost.BoostService.BoostType.COINS);
            } else if (lower.startsWith("exp")) {
                types.add(com.litebrawl.boost.BoostService.BoostType.EXP);
            }
        }
        if (types.isEmpty()) {
            types.add(com.litebrawl.boost.BoostService.BoostType.COINS);
            types.add(com.litebrawl.boost.BoostService.BoostType.EXP);
        }
        return types;
    }

    public String boostStartMessage() {
        return boostStartMessage;
    }

    public String boostEndMessage() {
        return boostEndMessage;
    }

    public boolean boostStreakEnabled() {
        return boostStreakEnabled;
    }

    public int boostStreakCount() {
        return boostStreakCount;
    }

    public int boostStreakSeconds() {
        return boostStreakSeconds;
    }

    public double boostStreakMultiplier() {
        return boostStreakMultiplier;
    }

    public boolean boostStreakOncePerStreak() {
        return boostStreakOnce;
    }

    public java.util.Set<com.litebrawl.boost.BoostService.BoostType> boostStreakTypes() {
        return boostStreakTypes;
    }

    public boolean boostCountEnabled() {
        return boostCountEnabled;
    }

    public int boostCountThreshold() {
        return boostCountThreshold;
    }

    public int boostCountSeconds() {
        return boostCountSeconds;
    }

    public double boostCountMultiplier() {
        return boostCountMultiplier;
    }

    public java.util.Set<com.litebrawl.boost.BoostService.BoostType> boostCountTypes() {
        return boostCountTypes;
    }

    public boolean botsEnabled() {
        return botsEnabled;
    }

    public boolean botsAuto() {
        return botsAuto;
    }

    public int botsMinPlayers() {
        return botsMinPlayers;
    }

    public int botsCount() {
        return botsCount;
    }

    public int botsMasterPercent() {
        return botsMasterPercent;
    }

    public boolean botsRespawn() {
        return botsRespawn;
    }

    public boolean botsSilent() {
        return botsSilent;
    }

    public int botsRespawnSeconds() {
        return botsRespawnSeconds;
    }

    public double botMasterDamage() {
        return botMasterDamage;
    }

    public long botMasterCoins() {
        return botMasterCoins;
    }

    public long botMasterExp() {
        return botMasterExp;
    }

    public long botNoviceCoins() {
        return botNoviceCoins;
    }

    public long botNoviceExp() {
        return botNoviceExp;
    }

    public String storageType() {
        return storageType;
    }

    public String mysqlHost() {
        return mysqlHost;
    }

    public int mysqlPort() {
        return mysqlPort;
    }

    public String mysqlDatabase() {
        return mysqlDatabase;
    }

    public String mysqlUser() {
        return mysqlUser;
    }

    public String mysqlPassword() {
        return mysqlPassword;
    }

    public String mysqlTable() {
        return mysqlTable;
    }

    public boolean mysqlUseSsl() {
        return mysqlUseSsl;
    }

    public int storageSaveInterval() {
        return storageSaveInterval;
    }

    public String proxyMode() {
        return proxyMode;
    }

    public String rotationMode() {
        return rotationMode;
    }

    public boolean rotationVoteMode() {
        return "VOTE".equals(rotationMode);
    }

    public double knockbackMultiplier() {
        return knockbackMultiplier;
    }

    public boolean webEnabled() {
        return plugin.getConfig().getBoolean("web.enabled", true);
    }

    public String webBind() {
        return plugin.getConfig().getString("web.bind", "127.0.0.1");
    }

    public int webPort() {
        return plugin.getConfig().getInt("web.port", 8787);
    }

    public String webToken() {
        return plugin.getConfig().getString("web.token", "");
    }

    public boolean webOpenBrowser() {
        return plugin.getConfig().getBoolean("web.open-browser", true);
    }

    public List<String> proxyServers() {
        return proxyServers;
    }

    public String proxyDefaultServer() {
        return proxyDefaultServer;
    }

    public boolean antiFarmEnabled() {
        return antiFarmEnabled;
    }

    public int antiFarmWindowSeconds() {
        return antiFarmWindowSeconds;
    }

    public double antiFarmDecay() {
        return antiFarmDecay;
    }

    public double antiFarmMinFactor() {
        return antiFarmMinFactor;
    }

    public String proxyServerName() {
        return proxyServerName;
    }
}
