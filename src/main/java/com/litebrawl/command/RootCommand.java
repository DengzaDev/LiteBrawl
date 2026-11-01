package com.litebrawl.command;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.board.BoardType;
import com.litebrawl.data.Profile;
import com.litebrawl.map.EmeraldScanner;
import com.litebrawl.map.LiteMap;
import com.litebrawl.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 所有命令的入口。 */
public final class RootCommand implements CommandExecutor, TabCompleter {

    private final LiteBrawlPlugin plugin;

    public RootCommand(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "litebrawl" -> admin(sender, args);
            case "maps" -> maps(sender, args);
            case "map" -> map(sender, args);
            case "shop" -> shop(sender);
            case "start" -> start(sender);
            case "stats" -> stats(sender, args);
            case "leaderboard" -> leaderboard(sender, args);
            case "sidebar" -> toggleSidebar(sender);
            case "team" -> team(sender, args);
            case "spawn" -> spawn(sender);
            case "board" -> board(sender);
            case "play" -> play(sender, args);
            case "vote" -> vote(sender, args);
            case "link" -> link(sender);
            case "cosmetic" -> cosmetic(sender, args);
            default -> {
            }
        }
        return true;
    }

    private void msg(CommandSender sender, String mini, String... placeholders) {
        Msg.info(sender, plugin.settings().prefix(), mini, placeholders);
    }

    // ---------------- /litebrawl ----------------

    private void admin(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reload();
                msg(sender, "<green>配置已重载。</green> <gray>(存储类型变更需要重启服务器)</gray>");
            }
            case "coins" -> economy(sender, args, true);
            case "exp" -> economy(sender, args, false);
            case "hotfix" -> hotfix(sender, args);
            case "npc" -> npc(sender, args);
            case "boost" -> boost(sender, args);
            case "status" -> status(sender);
            case "link" -> linkAdmin(sender, args);
            case "bot" -> bot(sender, args);
            default -> help(sender);
        }
    }

    private void help(CommandSender sender) {
        msg(sender, "<gray>—— <white>LiteBrawl</white> ——</gray>"
                + "<newline><white>/maps create <目录名> <显示名></white> <dark_gray>创建地图</dark_gray>"
                + "<newline><white>/maps list|select|delete</white> <dark_gray>地图列表/切换/删除</dark_gray>"
                + "<newline><white>/map setspawn</white> <dark_gray>把脚下设为出生点</dark_gray>"
                + "<newline><white>/map scan [半径]</white> <dark_gray>扫描地图里已有的出生方块（默认用配置半径）</dark_gray>"
                + "<newline><white>/map info</white> <dark_gray>当前地图信息</dark_gray>"
                + "<newline><white>/map bar set|remove|list <榜单></white> <dark_gray>全息排行榜</dark_gray>"
                + "<newline><white>/map next</white> <dark_gray>立即切到下一张地图</dark_gray>"
                + "<newline><white>/litebrawl coins|exp give|take|set <玩家> <数量></white>"
                + "<newline><white>/litebrawl reload</white> <dark_gray>重载配置</dark_gray>"
                + "<newline><white>/litebrawl link <玩家名></white> <dark_gray>替玩家发网页面板绑定码</dark_gray>"
                + "<newline><white>/shop</white> <white>/stats</white> <white>/top</white> <white>/sb</white>");
    }

    private void economy(CommandSender sender, String[] args, boolean coins) {
        String unit = coins ? "硬币" : "经验";
        String cmd = coins ? "coins" : "exp";
        if (args.length < 4) {
            msg(sender, "<red>用法: /litebrawl " + cmd + " <give|take|set> <玩家> <数量></red>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            msg(sender, "<red>找不到在线玩家: %target%</red>", "target", args[2]);
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException ex) {
            msg(sender, "<red>数量必须是整数。</red>");
            return;
        }
        if (amount < 0) {
            msg(sender, "<red>数量不能为负数。</red>");
            return;
        }

        Profile profile = plugin.profiles().get(target);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (coins) {
                    profile.addCoins(amount);
                } else {
                    profile.addExperience(amount);
                }
            }
            case "take" -> {
                if (coins) {
                    profile.takeCoins(amount);
                } else {
                    profile.setExperience(Math.max(0L, profile.getExperience() - amount));
                }
            }
            case "set" -> {
                if (coins) {
                    profile.setCoins(amount);
                } else {
                    profile.setExperience(amount);
                }
            }
            default -> {
                msg(sender, "<red>用法: /litebrawl " + cmd + " <give|take|set> <玩家> <数量></red>");
                return;
            }
        }
        plugin.profiles().saveAsync(profile);
        msg(sender, "<green>已把 <white>%player%</white> 的%unit%调整为 <gold>%value%</gold></green>",
                "player", target.getName(),
                "unit", unit,
                "value", String.valueOf(coins ? profile.getCoins() : profile.getExperience()));
    }

    // ---------------- /maps ----------------

    private void maps(CommandSender sender, String[] args) {
        if (args.length == 0) {
            msg(sender, "<gray>/maps create <目录名> <显示名> <dark_gray>|</dark_gray> /maps list <dark_gray>|</dark_gray> /maps select <id> <dark_gray>|</dark_gray> /maps delete <id></gray>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 3) {
                    msg(sender, "<red>用法: /maps create <目录名> <显示名></red>");
                    return;
                }
                String displayName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                String error = plugin.maps().create(args[1], displayName);
                if (error != null) {
                    msg(sender, "<red>%error%</red>", "error", error);
                    return;
                }
                msg(sender, "<green>地图已创建: <white>%id%</white></green>"
                                + "<newline><gray>下一步：站到出生点执行 </gray><white>/map setspawn</white>"
                                + "<newline><gray>然后执行 </gray><white>/map scan</white><gray> 扫描绿宝石出生点</gray>",
                        "id", args[1]);
            }
            case "list" -> {
                if (plugin.maps().all().isEmpty()) {
                    msg(sender, "<gray>还没有任何地图。</gray>");
                    return;
                }
                StringBuilder builder = new StringBuilder("<gray>—— 地图列表 ——</gray>");
                for (LiteMap map : plugin.maps().all()) {
                    boolean active = map.id().equals(plugin.maps().activeId());
                    builder.append("<newline>")
                            .append(active ? "<green>● </green>" : "<dark_gray>○ </dark_gray>")
                            .append("<white>").append(map.id()).append("</white>")
                            .append(" <gray>(").append(map.displayName()).append(")</gray>")
                            .append(" <dark_gray>世界=").append(map.worldName()).append("</dark_gray>")
                            .append(" <dark_gray>出生点=").append(map.hasSpawn() ? "已设置" : "未设置").append("</dark_gray>")
                            .append(" <dark_gray>出生方块=").append(map.spawnPoints().size()).append("</dark_gray>");
                }
                msg(sender, builder.toString());
            }
            case "select" -> {
                if (args.length < 2) {
                    msg(sender, "<red>用法: /maps select <地图id></red>");
                    return;
                }
                String error = plugin.maps().setActiveChecked(args[1]);
                if (error != null) {
                    msg(sender, "<red>%error%</red>", "error", error);
                    return;
                }
                msg(sender, "<green>已切换到地图 <white>%id%</white></green>", "id", args[1]);
            }
            case "delete" -> {
                if (args.length < 2) {
                    msg(sender, "<red>用法: /maps delete <地图id></red>");
                    return;
                }
                String error = plugin.maps().delete(args[1]);
                if (error != null) {
                    msg(sender, "<red>%error%</red>", "error", error);
                    return;
                }
                msg(sender, "<green>已删除地图 <white>%id%</white> <gray>(世界文件未删除)</gray></green>", "id", args[1]);
            }
            default -> msg(sender, "<red>未知子命令。</red>");
        }
    }

    // ---------------- /map ----------------

    private void map(CommandSender sender, String[] args) {
        if (args.length == 0) {
            msg(sender, "<gray>/map setspawn <dark_gray>|</dark_gray> /map scan <dark_gray>|</dark_gray> /map info <dark_gray>|</dark_gray> /map bar set|remove|list <榜单></gray>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setspawn" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return;
                }
                if (plugin.maps().active().isEmpty()) {
                    msg(sender, "<red>没有激活的地图，先执行 /maps create。</red>");
                    return;
                }
                plugin.maps().setSpawn(player.getLocation());
                msg(sender, "<green>出生点已设置为当前位置。</green>");
            }
            case "scan" -> scan(sender, args);
            case "info" -> {
                msg(sender, plugin.maps().describeActive());
                int left = plugin.rotation().remainingSeconds();
                if (left > 0) {
                    msg(sender, "<gray>下次自动轮换：<white>%seconds%</white> 秒后</gray>",
                            "seconds", String.valueOf(left));
                }
            }
            case "next" -> {
                if (plugin.maps().all().size() < 2) {
                    msg(sender, "<red>至少要注册 2 张地图才能轮换（/maps create）。</red>");
                    return;
                }
                plugin.rotation().rotate();
            }
            case "bar" -> bar(sender, args);
            default -> msg(sender, "<red>未知子命令。</red>");
        }
    }

    private void scan(CommandSender sender, String[] args) {
        if (plugin.maps().isScanning()) {
            msg(sender, "<yellow>已有扫描任务在进行中。</yellow>");
            return;
        }
        LiteMap map = plugin.maps().active().orElse(null);
        if (map == null) {
            msg(sender, "<red>没有激活的地图。</red>");
            return;
        }
        if (map.world() == null) {
            msg(sender, "<red>地图世界未加载: %world%</red>", "world", map.worldName());
            return;
        }

        int radius = 0;
        if (args.length > 1) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                msg(sender, "<red>半径必须是整数（单位：方块）。</red>");
                return;
            }
        }
        int effective = radius > 0 ? Math.max(16, Math.min(1024, radius)) : plugin.settings().scanRadius();

        msg(sender, "<gray>开始扫描 <white>%id%</white> 的出生方块，半径 <white>%radius%</white> 格，可能需要几秒钟…</gray>",
                "id", map.id(), "radius", String.valueOf(effective));

        plugin.maps().scanActive(radius, new EmeraldScanner.Callback() {
            @Override
            public void done(List<int[]> points) {
                LiteMap current = plugin.maps().active().orElse(null);
                if (current == null) {
                    return;
                }
                current.setSpawnPoints(points);
                plugin.maps().save();
                msg(sender, "<green>扫描完成：找到 <white>%count%</white> 个出生方块。</green>",
                        "count", String.valueOf(points.size()));
            }

            @Override
            public void failed(String reason) {
                msg(sender, "<red>扫描失败: %reason%</red>", "reason", reason);
            }
        });
    }

    private void bar(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "<gray>/map bar set <榜单> <dark_gray>|</dark_gray> /map bar remove <榜单> <dark_gray>|</dark_gray> /map bar list</gray>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length < 3) {
                    msg(sender, "<red>用法: /map bar set <kills|level|coins|deaths></red>");
                    return;
                }
                Player player = asPlayer(sender);
                if (player == null) {
                    return;
                }
                BoardType type = BoardType.byId(args[2]);
                if (type == null) {
                    msg(sender, "<red>未知榜单，可用: kills / deaths / level / coins</red>");
                    return;
                }
                plugin.boards().place(type, player);
                msg(sender, "<green>已在当前位置放置 <white>%type%</white> 榜单。</green>", "type", type.id());
            }
            case "remove" -> {
                if (args.length < 3) {
                    msg(sender, "<red>用法: /map bar remove <kills|level|coins|deaths></red>");
                    return;
                }
                BoardType type = BoardType.byId(args[2]);
                if (type == null) {
                    msg(sender, "<red>未知榜单类型。</red>");
                    return;
                }
                if (!plugin.boards().remove(type)) {
                    msg(sender, "<red>该榜单还没有放置。</red>");
                    return;
                }
                msg(sender, "<green>已移除 <white>%type%</white> 榜单。</green>", "type", type.id());
            }
            case "list" -> {
                if (plugin.boards().placed().isEmpty()) {
                    msg(sender, "<gray>还没有放置任何榜单。</gray>");
                    return;
                }
                StringBuilder builder = new StringBuilder("<gray>—— 已放置榜单 ——</gray>");
                for (BoardType type : plugin.boards().placed()) {
                    builder.append("<newline><white>").append(type.id()).append("</white>");
                }
                msg(sender, builder.toString());
            }
            default -> msg(sender, "<red>未知子命令。</red>");
        }
    }

    // ---------------- 玩家命令 ----------------

    private void toggleSidebar(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.settings().sidebarEnabled()) {
            msg(sender, "<red>服务器未启用记分板（config.yml 的 scoreboard.enabled）。</red>");
            return;
        }
        boolean on = plugin.sidebar().toggle(player);
        msg(sender, on ? "<green>记分板已开启。</green>" : "<gray>记分板已关闭。</gray>");
    }

    private void shop(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        plugin.shop().open(player);
    }

    private void start(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        plugin.game().start(player);
    }

    private void stats(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                msg(sender, "<red>找不到在线玩家: %target%</red>", "target", args[0]);
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            msg(sender, "<red>控制台请指定玩家: /stats <玩家></red>");
            return;
        }

        Profile profile = plugin.profiles().get(target);
        int level = plugin.profiles().levelOf(profile.getExperience());
        msg(sender, "<gray>—— <white>%player%</white> 的战绩 ——</gray>"
                        + "<newline><gray>等级: <yellow>Lv.%level%</yellow> <dark_gray>(经验 %exp%)</dark_gray></gray>"
                        + "<newline><gray>硬币: <gold>%coins%</gold></gray>"
                        + "<newline><gray>击杀: <white>%kills%</white> 死亡: <white>%deaths%</white> 助杀: <white>%assists%</white></gray>"
                        + "<newline><gray>K/D: <white>%kd%</white></gray>",
                "player", target.getName(),
                "level", String.valueOf(level),
                "exp", String.valueOf(profile.getExperience()),
                "coins", String.valueOf(profile.getCoins()),
                "kills", String.valueOf(profile.getKills()),
                "deaths", String.valueOf(profile.getDeaths()),
                "assists", String.valueOf(profile.getAssists()),
                "kd", String.format(Locale.ROOT, "%.2f", profile.kd()));
    }

    private void leaderboard(CommandSender sender, String[] args) {
        if (args.length == 0) {
            msg(sender, "<gray>/top <kills|deaths> [day|week|month|year] <dark_gray>|</dark_gray> /top <level|coins></gray>");
            return;
        }
        BoardType type = BoardType.byId(args[0]);
        if (type == null) {
            msg(sender, "<red>未知榜单，可用: kills / deaths / level / coins</red>"
                    + "<newline><gray>时间维度：kills_day / kills_week / kills_month / kills_year，"
                    + "或 /top kills week</gray>");
            return;
        }
        com.litebrawl.data.Period period = args.length > 1
                ? com.litebrawl.data.Period.byId(args[1])
                : type.period();
        List<Profile> top = plugin.profiles().topPeriod(period, type.column(), 10);
        if (top.isEmpty()) {
            msg(sender, "<gray>%label% 暂无数据。</gray>",
                    "label", type.defaultLabel() + (period == type.period() ? "" : " · " + period.label()));
            return;
        }
        StringBuilder builder = new StringBuilder("<gold><bold>%s</bold></gold>".formatted(
                type.defaultLabel() + (period == type.period() ? "" : " · " + period.label())));
        int rank = 1;
        for (Profile profile : top) {
            String value = switch (type.column()) {
                case "experience" -> String.valueOf(plugin.profiles().levelOf(profile.getExperience()));
                case "coins" -> String.valueOf(profile.getCoins());
                case "deaths" -> String.valueOf(profile.getDeaths());
                default -> String.valueOf(profile.getKills());
            };
            builder.append("<newline><gray>").append(rank++).append(". </gray><white>")
                    .append(profile.getName()).append("</white> <dark_gray>-</dark_gray> <yellow>")
                    .append(value).append("</yellow>");
        }
        msg(sender, builder.toString());
    }

    /** /spawn：回出生点，战斗中不允许。 */
    private void spawn(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (plugin.combatTag().inCombat(player)) {
            msg(sender, "<red>战斗中无法返回出生点</red> <dark_gray>·</dark_gray> <gray>%seconds% 秒后可以</gray>",
                    "seconds", String.valueOf(plugin.combatTag().remainingSeconds(player)));
            return;
        }
        plugin.game().prepareLobby(player);
        msg(sender, "<green>已返回出生点。</green>");
    }

    /** /board：一个面板里看 击杀/死亡/等级/硬币 × 总日周月年。 */
    private void board(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        plugin.boardPanel().open(player);
    }

    /** /vote [地图id]：投票下一张地图，不带参数看票数。 */
    private void vote(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.settings().rotationVoteMode()) {
            msg(sender, "<red>当前不是票选模式（config.yml 的 rotation.mode 改成 VOTE）</red>");
            return;
        }
        if (args.length > 0) {
            String error = plugin.votes().vote(player, args[0]);
            if (error != null) {
                msg(sender, "<red>%error%</red>", "error", error);
                return;
            }
            msg(sender, "<green>已投票给</green> <white>%map%</white>", "map", args[0]);
            return;
        }
        var tally = plugin.votes().tally();
        if (tally.isEmpty()) {
            msg(sender, "<gray>还没有地图可以投票。</gray>");
            return;
        }
        StringBuilder builder = new StringBuilder("<gray>—— 地图票数 ——</gray>");
        for (var entry : tally) {
            builder.append("<newline><white>").append(entry.getKey()).append("</white>")
                    .append(" <yellow>").append(entry.getValue()).append("</yellow> 票");
        }
        builder.append("<newline><gray>投票：<white>/vote <地图id></white></gray>");
        msg(sender, builder.toString());
    }

    /** /link：生成网页面板绑定码。 */
    private void link(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.settings().webEnabled()) {
            msg(sender, "<red>网页面板没开启（config.yml 的 web.enabled）</red>");
            return;
        }
        String code = plugin.web().issueLinkCode(player);
        String base = "http://" + plugin.settings().webBind() + ":" + plugin.settings().webPort();
        Msg.info(player, plugin.settings().prefix(),
                "<gray>网页面板绑定码：</gray><gradient:#7ee8fa:#80ff72><bold>%code%</bold></gradient>",
                "code", code);
        Msg.info(player, plugin.settings().prefix(),
                "<gray>打开 </gray><white>%url%</white> <gray>输这个码，5 分钟内有效</gray>",
                "url", base + "/me");
    }

    /** /litebrawl link <玩家名>：管理员代发网页面板绑定码（玩家不在线也能发）。 */
    private void linkAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "<red>用法: /litebrawl link <玩家名></red>");
            return;
        }
        if (!plugin.settings().webEnabled()) {
            msg(sender, "<red>网页面板没开启（config.yml 的 web.enabled）</red>");
            return;
        }
        if (!(sender instanceof Player) && !sender.hasPermission("litebrawl.admin")) {
            msg(sender, "<red>只有管理员能替别人发绑定码</red>");
            return;
        }
        String name = args[1];
        org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(name);
        }
        boolean known = target != null;
        if (target == null) {
            // 没缓存过：离线模式会按名字推导 UUID，至少能让已经进过服的玩家用上
            target = Bukkit.getOfflinePlayer(name);
        }
        if (!known && !target.hasPlayedBefore()) {
            msg(sender, "<yellow>注意：%target% 没在本服进过，按离线 UUID 发的码；"
                    + "如果他其实进过服，让他自己打 /link 更稳妥。</yellow>", "target", name);
        }
        String code = plugin.web().issueLinkCode(target.getUniqueId());
        String url = "http://" + plugin.settings().webBind() + ":" + plugin.settings().webPort() + "/me";
        msg(sender, "<gray>已为 </gray><white>%target%</white> <gray>生成绑定码：</gray>"
                        + "<gradient:#7ee8fa:#80ff72><bold>%code%</bold></gradient> <gray>（5 分钟内有效）</gray>",
                "target", name, "code", code);
        msg(sender, "<gray>打开 </gray><white>%url%</white> <gray>输这个码</gray>", "url", url);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            Msg.info(online, plugin.settings().prefix(),
                    "<gray>管理员给你发了网页面板绑定码：</gray>"
                            + "<gradient:#7ee8fa:#80ff72><bold>%code%</bold></gradient>"
                            + "<newline><gray>打开 </gray><white>%url%</white> <gray>输这个码，5 分钟内有效</gray>",
                    "code", code, "url", url);
        }
    }

    /** /litebrawl status：一次性看清服务端状态，排查用。 */
    private void status(CommandSender sender) {
        var maps = plugin.maps();
        var active = maps.active().orElse(null);
        long playing = Bukkit.getOnlinePlayers().stream().filter(plugin.game()::isPlaying).count();
        int hotfixes = plugin.hotfixes().loadedIds().size();
        int boosts = plugin.boost().activeBoosts().size();

        msg(sender, "<gray>—— <white>LiteBrawl</white> 状态 ——</gray>"
                        + "<newline><gray>版本：<white>" + plugin.getPluginMeta().getVersion() + "</white></gray>"
                        + "<newline><gray>存储：<white>" + plugin.storage().describe() + "</white></gray>"
                        + "<newline><gray>地图：<white>" + maps.all().size() + "</white> 张，当前 <white>"
                        + (active == null ? "无" : active.displayName()) + "</white>"
                        + (active == null ? "" : "（出生点 " + (active.hasSpawn() ? "已设" : "<red>未设</red>")
                        + " · 出生方块 " + active.spawnPoints().size() + "）") + "</gray>"
                        + "<newline><gray>在线：<white>" + Bukkit.getOnlinePlayers().size()
                        + "</white>，在战场：<white>" + playing + "</white>，假人：<white>"
                        + plugin.bots().count() + "</white></gray>"
                        + "<newline><gray>假人难度检查：<white>"
                        + (active != null && active.world() != null
                        ? active.world().getDifficulty().name() : "-") + "</white></gray>"
                        + "<newline><gray>热更新：<white>" + hotfixes + "</white> 个，加成：<white>"
                        + boosts + "</white> 项</gray>"
                        + "<newline><gray>周期数据保留：<white>"
                        + (plugin.settings().periodRetentionDays() <= 0
                        ? "不清理"
                        : plugin.settings().periodRetentionDays() + " 天")
                        + "</white></gray>"
                        + "<newline><gray>TPS：<white>"
                        + String.format(Locale.ROOT, "%.1f", Bukkit.getTPS()[0]) + "</white></gray>");
    }

    /** /play <服务器>：通过代理跳服。 */
    private void play(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.proxy().enabled()) {
            msg(sender, "<red>跨服未开启（config.yml 的 proxy.mode 改成 BUNGEE）</red>");
            return;
        }
        String server = args.length > 0 ? args[0] : plugin.proxy().defaultServer();
        String error = plugin.proxy().connect(player, server);
        if (error != null) {
            msg(sender, "<red>%error%</red>", "error", error);
        }
    }

    private void team(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length == 0) {
            plugin.teams().openGui(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                String error = plugin.teams().create(player);
                msg(sender, error == null ? "<green>队伍已创建</green>" : "<red>%error%</red>",
                        "error", error == null ? "" : error);
            }
            case "accept" -> {
                String error = plugin.teams().accept(player);
                msg(sender, error == null ? "<green>已加入队伍</green>" : "<red>%error%</red>",
                        "error", error == null ? "" : error);
            }
            case "leave" -> {
                String error = plugin.teams().leave(player);
                msg(sender, error == null ? "<gray>你已离开队伍</gray>" : "<red>%error%</red>",
                        "error", error == null ? "" : error);
            }
            case "invite" -> {
                if (args.length < 2) {
                    msg(sender, "<gray>/team invite <玩家></gray>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    msg(sender, "<red>找不到在线玩家: %target%</red>", "target", args[1]);
                    return;
                }
                String error = plugin.teams().invite(player, target);
                if (error != null) {
                    msg(sender, "<red>%error%</red>", "error", error);
                }
            }
            default -> msg(sender, "<gray>/team <create|accept|leave|invite <玩家>></gray>");
        }
    }

    private void cosmetic(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            var owned = plugin.cosmetics().ownedOf(player);
            if (owned.isEmpty()) {
                msg(sender, "<gray>你还没有任何外观，去 <white>/shop</white> 看看。</gray>");
                return;
            }
            StringBuilder builder = new StringBuilder("<gray>—— 我的外观 ——</gray>");
            for (var cosmetic : owned) {
                builder.append("<newline><white>").append(cosmetic.id()).append("</white>")
                        .append(" <gray>(").append(cosmetic.type().label()).append(")</gray>")
                        .append(" <dark_gray>").append(cosmetic.name()).append("</dark_gray>");
            }
            builder.append("<newline><gray>装备：<white>/cosmetic equip <id></white>"
                    + " · 卸下：<white>/cosmetic off <类型></white></gray>");
            msg(sender, builder.toString());
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "equip" -> {
                if (args.length < 2) {
                    msg(sender, "<red>用法: /cosmetic equip <id></red>");
                    return;
                }
                String error = plugin.cosmetics().equip(player, args[1]);
                msg(sender, error == null ? "<green>已装备</green>" : "<red>%error%</red>",
                        "error", error == null ? "" : error);
            }
            case "off" -> {
                if (args.length < 2) {
                    msg(sender, "<red>用法: /cosmetic off <KILL_MESSAGE|DEATH_MESSAGE|KILL_EFFECT|CHAT_EFFECT></red>");
                    return;
                }
                var type = com.litebrawl.cosmetic.CosmeticType.byName(args[1]);
                if (type == null) {
                    msg(sender, "<red>未知类型。</red>");
                    return;
                }
                plugin.cosmetics().unequip(player, type);
                msg(sender, "<gray>已卸下 %type%</gray>", "type", type.label());
            }
            default -> msg(sender, "<gray>/cosmetic <list|equip <id>|off <类型>></gray>");
        }
    }

    private void hotfix(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "<gray>/lb hotfix <文件名> <dark_gray>|</dark_gray> list <dark_gray>|</dark_gray> reload <id> <dark_gray>|</dark_gray> unload <id></gray>"
                    + "<newline><dark_gray>文件放在 " + plugin.hotfixes().folder().getAbsolutePath() + "</dark_gray>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                var lines = plugin.hotfixes().describe();
                if (lines.isEmpty()) {
                    msg(sender, "<gray>当前没有已加载的热更新。</gray>");
                    return;
                }
                StringBuilder builder = new StringBuilder("<gray>—— 已加载热更新 ——</gray>");
                for (String line : lines) {
                    builder.append("<newline><white>").append(line).append("</white>");
                }
                msg(sender, builder.toString());
            }
            case "reload" -> {
                if (args.length < 3) {
                    msg(sender, "<red>用法: /lb hotfix reload <id></red>");
                    return;
                }
                String error = plugin.hotfixes().reload(args[2]);
                msg(sender, error == null ? "<green>已重新加载 %id%</green>" : "<red>%error%</red>",
                        "id", args[2], "error", error == null ? "" : error);
            }
            case "reloadall" -> {
                int count = plugin.hotfixes().reloadAll();
                msg(sender, "<green>已重新加载 %count% 个热更新</green>", "count", String.valueOf(count));
            }
            case "info" -> {
                if (args.length < 3) {
                    msg(sender, "<red>用法: /lb hotfix info <id></red>");
                    return;
                }
                var lines = plugin.hotfixes().info(args[2]);
                StringBuilder builder = new StringBuilder("<gray>—— 热更新详情 ——</gray>");
                for (String line : lines) {
                    builder.append("<newline>").append(line);
                }
                msg(sender, builder.toString());
            }
            case "check" -> {
                if (args.length < 3) {
                    msg(sender, "<red>用法: /lb hotfix check <文件名></red>");
                    return;
                }
                var lines = plugin.hotfixes().check(args[2]);
                StringBuilder builder = new StringBuilder("<gray>—— 校验结果 ——</gray>");
                for (String line : lines) {
                    builder.append("<newline>").append(line);
                }
                msg(sender, builder.toString());
            }
            case "export" -> {
                if (args.length < 3) {
                    msg(sender, "<red>用法: /lb hotfix export <id></red>");
                    return;
                }
                String error = plugin.hotfixes().export(args[2]);
                msg(sender, error == null
                                ? "<green>已导出到 HotFix/exports/%id%-export.hotfix</green>"
                                : "<red>%error%</red>",
                        "id", args[2], "error", error == null ? "" : error);
            }
            case "unload" -> {
                if (args.length < 3) {
                    msg(sender, "<red>用法: /lb hotfix unload <id></red>");
                    return;
                }
                String error = plugin.hotfixes().unload(args[2]);
                msg(sender, error == null ? "<green>已卸载 %id%</green>" : "<red>%error%</red>",
                        "id", args[2], "error", error == null ? "" : error);
            }
            default -> {
                String error = plugin.hotfixes().apply(args[1]);
                msg(sender, error == null
                                ? "<green>热更新已应用：<white>%id%</white></green>"
                                : "<red>热更新失败：%error%</red>",
                        "id", args[1], "error", error == null ? "" : error);
            }
        }
    }

    private void boost(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "<gray>/lb boost start <coins|exp|both> <秒> [倍率] [玩家,...]</gray>"
                    + "<newline><gray>/lb boost stop <coins|exp|both></gray>"
                    + "<newline><gray>/lb boost info</gray>"
                    + "<newline><gray>/lb boost schedule add <HH:mm> <coins|exp|both> <秒> [倍率]</gray>"
                    + "<newline><gray>/lb boost schedule list|remove <id></gray>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (args.length < 4) {
                    msg(sender, "<red>用法: /lb boost start <coins|exp|both> <秒> [倍率] [玩家,...]</red>");
                    return;
                }
                var types = com.litebrawl.boost.BoostService.parseTypes(args[2]);
                if (types.isEmpty()) {
                    msg(sender, "<red>类型只能是 coins / exp / both</red>");
                    return;
                }
                int seconds;
                double multiplier = 2.0D;
                try {
                    seconds = Integer.parseInt(args[3]);
                    if (args.length > 4) {
                        multiplier = Double.parseDouble(args[4]);
                    }
                } catch (NumberFormatException ex) {
                    msg(sender, "<red>秒数/倍率必须是数字</red>");
                    return;
                }
                java.util.Set<String> targets = new java.util.LinkedHashSet<>();
                if (args.length > 5 && !args[5].equalsIgnoreCase("all")) {
                    for (String name : args[5].split(",")) {
                        if (!name.isBlank()) {
                            targets.add(name.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                }
                String error = plugin.boost().start(types, seconds, multiplier, targets);
                msg(sender, error == null
                                ? "<green>已开启加成</green> <dark_gray>·</dark_gray> <gray>%detail%</gray>"
                                : "<red>%error%</red>",
                        "detail", detailOf(types, seconds, multiplier, targets),
                        "error", error == null ? "" : error);
            }
            case "stop" -> {
                if (args.length < 3) {
                    msg(sender, "<red>用法: /lb boost stop <coins|exp|both></red>");
                    return;
                }
                var types = com.litebrawl.boost.BoostService.parseTypes(args[2]);
                String error = plugin.boost().stop(types);
                msg(sender, error == null ? "<green>已关闭加成</green>" : "<red>%error%</red>",
                        "error", error == null ? "" : error);
            }
            case "info" -> {
                var active = plugin.boost().activeBoosts();
                if (active.isEmpty()) {
                    msg(sender, "<gray>当前没有生效中的加成。</gray>");
                    return;
                }
                StringBuilder builder = new StringBuilder("<gray>—— 生效中的加成 ——</gray>");
                for (var entry : active.entrySet()) {
                    var boost = entry.getValue();
                    builder.append("<newline><white>").append(entry.getKey().label()).append("</white>")
                            .append(" <gold>x").append(boost.multiplier()).append("</gold>")
                            .append(" <gray>剩余 <white>").append(boost.remainingSeconds()).append("</white> 秒</gray>");
                }
                msg(sender, builder.toString());
            }
            case "schedule" -> boostSchedule(sender, args);
            default -> msg(sender, "<red>未知子命令。</red>");
        }
    }

    private void boostSchedule(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "<gray>/lb boost schedule add <HH:mm> <coins|exp|both> <秒> [倍率]</gray>"
                    + "<newline><gray>/lb boost schedule list</gray>"
                    + "<newline><gray>/lb boost schedule remove <id></gray>");
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 6) {
                    msg(sender, "<red>用法: /lb boost schedule add <HH:mm> <coins|exp|both> <秒> [倍率]</red>");
                    return;
                }
                String time = args[3];
                var types = com.litebrawl.boost.BoostService.parseTypes(args[4]);
                int seconds;
                double multiplier = 2.0D;
                try {
                    seconds = Integer.parseInt(args[5]);
                    if (args.length > 6) {
                        multiplier = Double.parseDouble(args[6]);
                    }
                } catch (NumberFormatException ex) {
                    msg(sender, "<red>秒数/倍率必须是数字</red>");
                    return;
                }
                String error = plugin.boost().addSchedule(time, seconds, multiplier, types,
                        java.util.Set.of());
                msg(sender, error == null
                                ? "<green>已添加定时加成：每天 <white>%time%</white> 开 %seconds% 秒</green>"
                                : "<red>%error%</red>",
                        "time", time, "seconds", String.valueOf(seconds),
                        "error", error == null ? "" : error);
            }
            case "list" -> {
                var schedules = plugin.boost().schedules();
                if (schedules.isEmpty()) {
                    msg(sender, "<gray>还没有定时加成。</gray>");
                    return;
                }
                StringBuilder builder = new StringBuilder("<gray>—— 定时加成 ——</gray>");
                for (var schedule : schedules) {
                    builder.append("<newline><white>").append(schedule.id()).append("</white>")
                            .append(" <gray>每天</gray> <yellow>").append(schedule.time()).append("</yellow>");
                }
                msg(sender, builder.toString());
            }
            case "remove" -> {
                if (args.length < 4) {
                    msg(sender, "<red>用法: /lb boost schedule remove <id></red>");
                    return;
                }
                String error = plugin.boost().removeSchedule(args[3]);
                msg(sender, error == null ? "<green>已删除定时加成 %id%</green>" : "<red>%error%</red>",
                        "id", args[3], "error", error == null ? "" : error);
            }
            default -> msg(sender, "<red>未知子命令。</red>");
        }
    }

    private String detailOf(java.util.Set<com.litebrawl.boost.BoostService.BoostType> types,
                            int seconds, double multiplier, java.util.Set<String> targets) {
        StringBuilder sb = new StringBuilder();
        for (var type : types) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(type.label());
        }
        sb.append(" x").append(multiplier).append(" · ").append(seconds).append(" 秒 · ");
        sb.append(targets.isEmpty() ? "全服" : String.join(",", targets));
        return sb.toString();
    }

    private void npc(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "<gray>/lb npc set <shop|cosmetic> <dark_gray>|</dark_gray> remove <dark_gray>|</dark_gray> skin <玩家> <dark_gray>|</dark_gray> list</gray>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return;
                }
                String raw = args.length > 2 ? args[2] : "shop";
                var type = com.litebrawl.npc.NpcService.NpcType.byId(raw);
                if (type == null) {
                    msg(sender, "<red>未知类型，可用 shop / cosmetic</red>");
                    return;
                }
                String id = plugin.npcs().create(type, player.getLocation(), player);
                msg(sender, "<green>已创建 NPC <white>%id%</white>（%type%）</green>",
                        "id", id, "type", type.id());
            }
            case "remove" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return;
                }
                int removed = plugin.npcs().removeNearest(player.getLocation(), 6.0D);
                msg(sender, removed > 0 ? "<green>已删除最近的 NPC</green>"
                        : "<red>附近 6 格内没有 NPC</red>");
            }
            case "move" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return;
                }
                boolean ok = plugin.npcs().moveNearest(player.getLocation(), 6.0D);
                msg(sender, ok ? "<green>已把最近的 NPC 移到这里</green>"
                        : "<red>附近 6 格内没有 NPC</red>");
            }
            case "name" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return;
                }
                if (args.length < 3) {
                    msg(sender, "<red>用法: /lb npc name <显示名></red>");
                    return;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                boolean ok = plugin.npcs().renameNearest(player.getLocation(), name, 6.0D);
                msg(sender, ok ? "<green>已把最近的 NPC 改名为 <white>%name%</white></green>"
                        : "<red>附近没有 NPC</red>", "name", name);
            }
            case "skin" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    return;
                }
                if (args.length < 3) {
                    msg(sender, "<red>用法: /lb npc skin <玩家名></red>");
                    return;
                }
                boolean ok = plugin.npcs().setSkinNearest(player.getLocation(), args[2], 6.0D);
                msg(sender, ok ? "<green>已把最近的 NPC 皮肤设为 <white>%name%</white></green>"
                        : "<red>附近没有 NPC（先 /lb npc set）</red>", "name", args[2]);
            }
            case "list" -> {
                var all = plugin.npcs().list();
                if (all.isEmpty()) {
                    msg(sender, "<gray>还没有 NPC。</gray>");
                    return;
                }
                StringBuilder builder = new StringBuilder("<gray>—— NPC 列表 ——</gray>");
                for (String line : all) {
                    builder.append("<newline><white>").append(line).append("</white>");
                }
                msg(sender, builder.toString());
            }
            default -> msg(sender, "<red>未知子命令。</red>");
        }
    }

    private void bot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "<gray>/lb bot <spawn [数量]|clear|count></gray>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                int amount = plugin.settings().botsCount();
                if (args.length > 2) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ex) {
                        msg(sender, "<red>数量必须是整数。</red>");
                        return;
                    }
                }
                int created = plugin.bots().spawnBots(amount);
                if (created == 0) {
                    // 以前这里只报「已生成 0 个」，玩家不知道是地图没有出生点
                    var active = plugin.maps().active().orElse(null);
                    msg(sender, active == null
                            ? "<red>没有生成假人：还没有注册地图（/maps create）</red>"
                            : "<red>没有生成假人：地图没有可用出生点，先 /map scan 扫出绿宝石块</red>");
                    return;
                }
                msg(sender, "<green>已生成 %count% 个假人</green> <dark_gray>(当前 %total%)</dark_gray>",
                        "count", String.valueOf(created),
                        "total", String.valueOf(plugin.bots().count()));
            }
            case "clear" -> {
                plugin.bots().removeAll();
                msg(sender, "<gray>已清空所有假人。</gray>");
            }
            case "count" -> msg(sender, "<gray>当前假人数量：<white>%count%</white></gray>",
                    "count", String.valueOf(plugin.bots().count()));
            default -> msg(sender, "<red>未知子命令。</red>");
        }
    }

    private Player asPlayer(CommandSender sender) {        if (sender instanceof Player player) {
            return player;
        }
        msg(sender, "<red>该命令只能由玩家执行。</red>");
        return null;
    }

    // ---------------- tab ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("maps")) {
            if (args.length == 1) {
                return filter(List.of("create", "list", "select", "delete"), args[0]);
            }
            if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
                return filter(mapIds(), args[1]);
            }
        }
        if (name.equals("map")) {
            if (args.length == 1) {
                return filter(List.of("setspawn", "scan", "info", "bar"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("bar")) {
                return filter(List.of("set", "remove", "list"), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("bar")) {
                return filter(boardIds(), args[2]);
            }
        }
        if (name.equals("litebrawl")) {
            if (args.length == 1) {
                return filter(List.of("reload", "coins", "exp", "help"), args[0]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("coins") || args[0].equalsIgnoreCase("exp"))) {
                return filter(List.of("give", "take", "set"), args[1]);
            }
        }
        if (name.equals("leaderboard")) {
            if (args.length == 1) {
                return filter(boardIds(), args[0]);
            }
            if (args.length == 2) {
                return filter(List.of("all", "day", "week", "month", "year"), args[1]);
            }
        }
        return List.of();
    }

    private List<String> mapIds() {
        return new ArrayList<>(plugin.maps().byIdView().keySet());
    }

    private List<String> boardIds() {
        List<String> ids = new ArrayList<>();
        for (BoardType type : BoardType.values()) {
            ids.add(type.id());
        }
        return ids;
    }

    private List<String> filter(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return options;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
