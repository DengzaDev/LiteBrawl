package com.litebrawl;

import com.litebrawl.board.BoardPanelGui;
import com.litebrawl.board.BoardService;
import com.litebrawl.boost.BoostService;
import com.litebrawl.backup.BackupService;
import com.litebrawl.bot.BotService;
import com.litebrawl.chat.MessageService;
import com.litebrawl.cosmetic.CosmeticGui;
import com.litebrawl.display.DisplayService;
import com.litebrawl.cosmetic.CosmeticService;
import com.litebrawl.hotfix.HotfixLoader;
import com.litebrawl.combat.CombatService;
import com.litebrawl.combat.AntiFarmService;
import com.litebrawl.combat.CombatTagService;
import com.litebrawl.command.RootCommand;
import com.litebrawl.data.ProfileService;
import com.litebrawl.data.SqlStorage;
import com.litebrawl.data.Storage;
import com.litebrawl.game.BountyService;
import com.litebrawl.game.GameService;
import com.litebrawl.game.KitService;
import com.litebrawl.game.LethalBlockService;
import com.litebrawl.game.ArrowService;
import com.litebrawl.game.LobbyService;
import com.litebrawl.game.MobCleanupService;
import com.litebrawl.game.ProtectionService;
import com.litebrawl.game.QuiverService;
import com.litebrawl.listener.ChatListener;
import com.litebrawl.listener.CombatListener;
import com.litebrawl.listener.PlayerListener;
import com.litebrawl.listener.ProtectionListener;
import com.litebrawl.listener.ShopListener;
import com.litebrawl.map.MapService;
import com.litebrawl.map.RotationService;
import com.litebrawl.npc.NpcService;
import com.litebrawl.proxy.ProxyService;
import com.litebrawl.vote.VoteService;
import com.litebrawl.web.WebConfigServer;
import com.litebrawl.shop.ShopService;
import com.litebrawl.rules.BottleService;
import com.litebrawl.scoreboard.ScoreboardService;
import com.litebrawl.settings.PersonalGui;
import com.litebrawl.settings.PlayerSettingsService;
import com.litebrawl.settings.SettingsGui;
import com.litebrawl.team.TeamService;
import com.litebrawl.ui.TabService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class LiteBrawlPlugin extends JavaPlugin {

    private static final String[] COMMANDS = {
            "litebrawl", "maps", "map", "shop", "start", "stats", "leaderboard", "sidebar", "board", "play", "vote", "link",
            "team", "cosmetic", "spawn"
    };

    private Settings settings;
    private Storage storage;

    private ProfileService profiles;
    private MapService maps;
    private KitService kits;
    private QuiverService quivers;
    private CombatService combat;
    private GameService game;
    private LethalBlockService lethalBlocks;
    private BountyService bounty;
    private RotationService rotation;
    private TeamService teams;
    private ProtectionService protection;
    private MessageService messages;
    private CosmeticService cosmetics;
    private HotfixLoader hotfixes;
    private TabService tab;
    private LobbyService lobby;
    private CosmeticGui cosmeticGui;
    private NpcService npcs;
    private BotService bots;
    private MobCleanupService mobCleanup;
    private CombatTagService combatTag;
    private AntiFarmService antiFarm;
    private ProxyService proxy;
    private VoteService votes;
    private WebConfigServer web;
    private boolean firstRun;
    private ArrowService arrows;
    private BoostService boost;
    private BottleService bottles;
    private DisplayService display;
    private PlayerSettingsService playerSettings;
    private SettingsGui settingsGui;
    private PersonalGui personalGui;
    private ShopService shop;
    private BoardService boards;
    private BackupService backups;
    private BoardPanelGui boardPanel;
    private ScoreboardService sidebar;

    @Override
    public void onEnable() {
        boolean firstInstall = !new java.io.File(getDataFolder(), "config.yml").exists();
        this.firstRun = firstInstall;
        saveDefaultConfig();
        this.settings = new Settings(this);

        this.storage = new SqlStorage(this, settings);
        try {
            storage.init();
        } catch (Exception ex) {
            getLogger().severe("数据库初始化失败，LiteBrawl 将被禁用: " + ex.getMessage());
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.profiles = new ProfileService(this);
        this.maps = new MapService(this);
        this.kits = new KitService(this);
        this.quivers = new QuiverService(this);
        this.combat = new CombatService(this);
        this.game = new GameService(this);
        this.lethalBlocks = new LethalBlockService(this);
        this.bounty = new BountyService(this);
        this.rotation = new RotationService(this);
        this.teams = new TeamService(this);
        this.protection = new ProtectionService(this);
        this.messages = new MessageService(this);
        this.cosmetics = new CosmeticService(this);
        this.hotfixes = new HotfixLoader(this);
        this.tab = new TabService(this);
        this.lobby = new LobbyService(this);
        this.cosmeticGui = new CosmeticGui(this);
        this.npcs = new NpcService(this);
        this.bots = new BotService(this);
        this.mobCleanup = new MobCleanupService(this);
        this.combatTag = new CombatTagService(this);
        this.antiFarm = new AntiFarmService(this);
        this.proxy = new ProxyService(this);
        this.votes = new VoteService(this);
        this.web = new WebConfigServer(this);
        this.arrows = new ArrowService(this);
        this.boost = new BoostService(this);
        this.bottles = new BottleService(this);
        this.display = new DisplayService(this);
        this.playerSettings = new PlayerSettingsService(this);
        this.settingsGui = new SettingsGui(this);
        this.personalGui = new PersonalGui(this);
        this.shop = new ShopService(this);
        this.boards = new BoardService(this);
        this.boardPanel = new BoardPanelGui(this);
        this.backups = new BackupService(this);
        this.sidebar = new ScoreboardService(this);

        maps.load();
        // 外观必须先于商店加载：商店里引用了 cosmetics.items 的 id
        cosmetics.load();
        shop.load();
        boards.load();
        npcs.load();
        npcs.start();
        profiles.startAutoSave();

        registerListeners();
        registerCommands();

        quivers.start();
        boards.start();
        sidebar.start();
        lethalBlocks.start();
        rotation.start();
        tab.start();
        display.start();
        startWeb();
        startPeriodCleanup();
        backups.start();
        boost.start();
        bottles.start();
        mobCleanup.start();
        bots.start();
        hotfixes.autoLoad();

        getLogger().info("LiteBrawl 已启用 · 存储=" + storage.describe());
        if (maps.active().isEmpty()) {
            getLogger().warning("还没有配置任何地图。使用 /maps create <目录名> <显示名> 开始。");
        }
        if (firstInstall) {
            printFirstRunGuide();
        }
    }

    /**
     * 周期榜数据定期清理：日 / 周 / 月 / 年榜每期每个玩家一行，不清理就会无限膨胀。
     *
     * <p>启动时先异步清一次，之后每 6 小时一次。保留天数每次现读配置，
     * 所以 {@code /litebrawl reload} 改完立即生效；设为 0 表示不清理。</p>
     */
    private void startPeriodCleanup() {
        Runnable purge = () -> {
            int keepDays = settings.periodRetentionDays();
            if (keepDays <= 0) {
                return;
            }
            int removed = storage.purgeOldPeriods(keepDays);
            if (removed > 0) {
                getLogger().info("已清理 " + removed + " 条过期的周期榜数据（保留最近 " + keepDays + " 天）");
            }
        };
        getServer().getScheduler().runTaskAsynchronously(this, purge);
        long ticks = 6L * 60 * 60 * 20;
        getServer().getScheduler().runTaskTimerAsynchronously(this, purge, ticks, ticks);
    }

    /** 网页配置服务：首次启动自动生成 token，并在本机尝试打开浏览器。 */
    private void startWeb() {
        if (!settings.webEnabled()) {
            return;
        }
        String token = settings.webToken();
        if (token == null || token.isBlank()) {
            getConfig().set("web.token", java.util.UUID.randomUUID().toString().replace("-", ""));
            saveConfig();
            settings.reload();
        }
        web.start();
        getLogger().info("配置管理页面: " + web.url());
        if (firstRun && settings.webOpenBrowser()) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(web.url()));
            } catch (Throwable ignored) {
                // 服务器没有图形界面时打开浏览器会失败，忽略即可
            }
        }
    }

    /** 首次安装时打印一份上手清单，顺便写一份 txt 到插件目录。 */
    private void printFirstRunGuide() {
        String[] lines = {
                "",
                "========================================",
                " LiteBrawl 首次安装 · 三步开玩",
                "========================================",
                " 1) 把地图世界文件夹放进服务端根目录",
                " 2) /maps create <文件夹名> <显示名>",
                " 3) /map setspawn   然后   /map scan",
                "",
                " 可选：",
                "   /map bar set <榜单>   在脚下生成全息榜（左键/右键切时间维度）",
                "   /lb npc set shop      在脚下生成商店 NPC",
                "   /lb boost start coins 600 2   开 10 分钟双倍硬币",
                "   /lb status            随时查看运行状态",
                "",
                " 配置都在 plugins/LiteBrawl/ 下：",
                "   config.yml    主配置（装备/经济/地图/假人/外观…）",
                "   messages.yml  播报文案",
                "   详细说明见插件目录的 README.md",
                "========================================",
        };
        for (String line : lines) {
            getLogger().info(line);
        }
        writeQuickStartFile();
    }

    private void writeQuickStartFile() {
        java.io.File target = new java.io.File(getDataFolder(), "快速上手.txt");
        if (target.exists()) {
            return;
        }
        try {
            java.nio.file.Files.writeString(target.toPath(), String.join(System.lineSeparator(),
                    "LiteBrawl 快速上手",
                    "",
                    "1. 把地图世界文件夹放进服务端根目录（和 world 同级）",
                    "2. /maps create <文件夹名> <显示名>",
                    "3. /map setspawn        站到出生点执行",
                    "4. /map scan            扫描地图里已有的绿宝石块作为出生点",
                    "5. 玩家进服点大厅的下界之星开始",
                    "",
                    "常用命令：",
                    "  /lb status                      运行状态",
                    "  /map bar set kills_week         生成击杀周榜（左键/右键切维度）",
                    "  /lb npc set shop                生成商店 NPC",
                    "  /lb boost start coins 600 2     双倍硬币 10 分钟",
                    "  /lb hotfix <文件名>             热更新（放 HotFix 目录）",
                    "",
                    "详细配置项见 config.yml 里的中文注释。"
            ), java.nio.charset.StandardCharsets.UTF_8);
            getLogger().info("已生成 " + target.getName());
        } catch (java.io.IOException ex) {
            getLogger().warning("写快速上手文件失败: " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (backups != null) {
            backups.stop();
        }
        if (tab != null) {
            tab.stop();
        }
        if (web != null) {
            web.stop();
        }
        if (bottles != null) {
            bottles.stop();
        }
        if (boost != null) {
            boost.stop();
        }
        if (display != null) {
            display.stop();
        }
        if (mobCleanup != null) {
            mobCleanup.stop();
        }
        if (bots != null) {
            bots.stop();
        }
        if (combatTag != null) {
            combatTag.clearAll();
        }
        if (npcs != null) {
            npcs.stop();
            npcs.removeAll();
        }
        if (hotfixes != null) {
            hotfixes.unloadAll();
        }
        if (teams != null) {
            teams.clearAll();
        }
        if (protection != null) {
            protection.clearAll();
        }
        if (rotation != null) {
            rotation.stop();
        }
        if (bounty != null) {
            bounty.clearAll();
        }
        if (lethalBlocks != null) {
            lethalBlocks.stop();
        }
        if (quivers != null) {
            quivers.stop();
        }
        if (boards != null) {
            boards.shutdown();
        }
        if (sidebar != null) {
            sidebar.stop();
        }
        if (combat != null) {
            combat.clearAll();
        }
        if (profiles != null) {
            profiles.shutdown();
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (Exception ex) {
                getLogger().warning("关闭数据库连接失败: " + ex.getMessage());
            }
        }
    }

    /** /litebrawl reload：重读磁盘配置 + 重建运行时组件。 */
    public void reload() {
        reloadConfig();
        reloadRuntime();
    }

    /** 只重建运行时组件，不重读磁盘（热更新覆盖内存配置后走这条）。 */
    public void reloadRuntime() {
        settings.reload();
        messages.load();
        cosmetics.load();
        shop.load();
        boards.reload();
        sidebar.restart();
        rotation.restart();
        tab.restart();
        display.restart();
        boost.restart();
        bottles.restart();
        mobCleanup.restart();
        bots.restart();
    }

    /** 热更新用：把规则写进内存 config，然后立刻生效。 */
    public void applyRuntimeOverrides(Map<String, Object> overrides) {
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            getConfig().set(entry.getKey(), entry.getValue());
        }
        reloadRuntime();
    }

    private void registerListeners() {
        var manager = getServer().getPluginManager();
        manager.registerEvents(new ProtectionListener(this), this);
        manager.registerEvents(new PlayerListener(this), this);
        manager.registerEvents(new CombatListener(this), this);
        manager.registerEvents(new ChatListener(this), this);
        manager.registerEvents(new ShopListener(this), this);
        manager.registerEvents(teams, this);
        manager.registerEvents(cosmeticGui, this);
        manager.registerEvents(npcs, this);
        manager.registerEvents(bots, this);
        manager.registerEvents(mobCleanup, this);
        manager.registerEvents(arrows, this);
        manager.registerEvents(boardPanel, this);
        manager.registerEvents(boards, this);
        manager.registerEvents(display, this);
        manager.registerEvents(bottles, this);
        manager.registerEvents(settingsGui, this);
        manager.registerEvents(personalGui, this);
    }

    private void registerCommands() {
        RootCommand root = new RootCommand(this);
        for (String name : COMMANDS) {
            PluginCommand command = getCommand(name);
            if (command == null) {
                getLogger().warning("plugin.yml 中缺少命令注册: " + name);
                continue;
            }
            command.setExecutor(root);
            command.setTabCompleter(root);
        }
    }

    public Settings settings() {
        return settings;
    }

    public Storage storage() {
        return storage;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public MapService maps() {
        return maps;
    }

    public KitService kits() {
        return kits;
    }

    public QuiverService quivers() {
        return quivers;
    }

    public CombatService combat() {
        return combat;
    }

    public GameService game() {
        return game;
    }

    public ShopService shop() {
        return shop;
    }

    public BoardService boards() {
        return boards;
    }

    public BoardPanelGui boardPanel() {
        return boardPanel;
    }

    public ScoreboardService sidebar() {
        return sidebar;
    }

    public BountyService bounty() {
        return bounty;
    }

    public RotationService rotation() {
        return rotation;
    }

    public TeamService teams() {
        return teams;
    }

    public ProtectionService protection() {
        return protection;
    }

    public MessageService messages() {
        return messages;
    }

    public CosmeticService cosmetics() {
        return cosmetics;
    }

    public HotfixLoader hotfixes() {
        return hotfixes;
    }

    public TabService tab() {
        return tab;
    }

    public LobbyService lobby() {
        return lobby;
    }

    public CosmeticGui cosmeticGui() {
        return cosmeticGui;
    }

    public NpcService npcs() {
        return npcs;
    }

    public BotService bots() {
        return bots;
    }

    public CombatTagService combatTag() {
        return combatTag;
    }

    public AntiFarmService antiFarm() {
        return antiFarm;
    }

    public ProxyService proxy() {
        return proxy;
    }

    public VoteService votes() {
        return votes;
    }

    public WebConfigServer web() {
        return web;
    }

    public ArrowService arrows() {
        return arrows;
    }

    public DisplayService display() {
        return display;
    }

    public BackupService backups() {
        return backups;
    }

    public BoostService boost() {
        return boost;
    }

    public PlayerSettingsService playerSettings() {
        return playerSettings;
    }

    public SettingsGui settingsGui() {
        return settingsGui;
    }

    public PersonalGui personalGui() {
        return personalGui;
    }
}
