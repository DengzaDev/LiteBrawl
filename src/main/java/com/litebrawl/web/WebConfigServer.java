package com.litebrawl.web;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.board.BoardType;
import com.litebrawl.data.Period;
import com.litebrawl.map.LiteMap;
import com.litebrawl.util.Msg;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 内置网页管理后台。
 *
 * <p>路由：</p>
 * <ul>
 *   <li>{@code GET /} 管理页面</li>
 *   <li>{@code GET /api/state} 运行状态（JSON）</li>
 *   <li>{@code GET /api/config-flat} 配置的扁平键值（JSON，只含标量）</li>
 *   <li>{@code POST /api/set} 表单提交（x-www-form-urlencoded，key=配置路径）</li>
 *   <li>{@code GET/POST /api/config} 原始 YAML 读写（高级）</li>
 *   <li>{@code POST /api/action?name=xxx} 动作：reload / backup / map_next / boost_stop / scan</li>
 * </ul>
 *
 * <p>所有 {@code /api/*} 都要 token；默认只绑定 127.0.0.1。</p>
 */
public final class WebConfigServer {

    private final LiteBrawlPlugin plugin;
    private HttpServer server;

    public WebConfigServer(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.settings().webEnabled()) {
            return;
        }
        String bind = plugin.settings().webBind();
        int port = plugin.settings().webPort();
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        } catch (IOException ex) {
            plugin.getLogger().warning("网页管理启动失败（端口 " + port + "）: " + ex.getMessage());
            return;
        }
        server.createContext("/", this::handleIndex);
        server.createContext("/api/state", this::handleState);
        server.createContext("/api/config-flat", this::handleConfigFlat);
        server.createContext("/api/set", this::handleSet);
        server.createContext("/api/config", this::handleConfig);
        server.createContext("/api/save", this::handleSave);
        server.createContext("/api/reload", this::handleReload);
        server.createContext("/api/action", this::handleAction);
        server.createContext("/api/files", this::handleFiles);
        server.createContext("/api/file", this::handleFile);
        server.createContext("/api/db/top", this::handleDbTop);
        server.createContext("/api/link", this::handleLink);
        server.createContext("/api/me", this::handleMe);
        server.createContext("/api/me/equip", this::handleEquip);
        server.createContext("/api/me/unequip", this::handleUnequip);
        server.createContext("/me", this::handleIndex);
        server.createContext("/api/status", this::handleStatus);
        server.setExecutor(null);
        server.start();
        plugin.getLogger().info("网页管理已启动: " + url());
        // 绑定码 5 分钟、玩家令牌 12 小时，过期后只要没人再访问就永远留在内存里；
        // 每分钟扫一次，避免长服上这两个表只增不减。
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::purgeExpired, 20L * 60, 20L * 60);
    }

    /** 允许网页编辑的配置文件白名单。 */
    private static final List<String> EDITABLE_FILES = List.of(
            "config.yml", "messages.yml", "maps.yml", "boards.yml",
            "boost.yml", "npcs.yml", "快速上手.txt");

    /** 玩家绑定用的临时码：code -> uuid */
    private final Map<String, UUID> linkCodes = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> linkExpiry = new java.util.concurrent.ConcurrentHashMap<>();
    /** 玩家令牌：token -> uuid */
    private final Map<String, UUID> playerTokens = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> playerTokenExpiry = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 清理过期的绑定码与玩家令牌。
     *
     * <p>这两个表平时只在「被使用」时才删条目，没人用的过期项会一直留着，
     * 长服就是缓慢的内存增长，所以由定时任务每分钟扫一遍。</p>
     */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        int before = linkCodes.size() + playerTokens.size();
        linkExpiry.entrySet().removeIf(entry -> entry.getValue() < now);
        linkCodes.keySet().removeIf(code -> !linkExpiry.containsKey(code));
        playerTokenExpiry.entrySet().removeIf(entry -> entry.getValue() < now);
        playerTokens.keySet().removeIf(token -> !playerTokenExpiry.containsKey(token));
        int removed = before - (linkCodes.size() + playerTokens.size());
        if (removed > 0) {
            plugin.getLogger().info("清理了 " + removed + " 条过期的网页绑定码/令牌");
        }
    }

    public void stop() {        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public String url() {
        return "http://" + plugin.settings().webBind() + ":" + plugin.settings().webPort()
                + "/?token=" + plugin.settings().webToken();
    }

    // ---------------- 页面 ----------------

    private void handleIndex(HttpExchange exchange) throws IOException {
        try (InputStream in = plugin.getResource("web/index.html")) {
            if (in == null) {
                send(exchange, 500, "text/plain; charset=utf-8", "缺少 web/index.html");
                return;
            }
            send(exchange, 200, "text/html; charset=utf-8",
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    // ---------------- 状态 ----------------

    private void handleState(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        String json = callSync(this::buildStateJson);
        send(exchange, 200, "application/json; charset=utf-8",
                json == null ? "{\"error\":\"state timeout\"}" : json);
    }

    private String buildStateJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"version\":").append(q(plugin.getPluginMeta().getVersion()));
        sb.append(",\"storage\":").append(q(plugin.storage().describe()));
        sb.append(",\"online\":").append(Bukkit.getOnlinePlayers().size());
        sb.append(",\"playing\":").append(Bukkit.getOnlinePlayers().stream()
                .filter(plugin.game()::isPlaying).count());
        sb.append(",\"bots\":").append(plugin.bots().count());
        sb.append(",\"tps\":").append(String.format(Locale.ROOT, "%.1f", Bukkit.getTPS()[0]));
        sb.append(",\"activeMap\":").append(q(plugin.maps().activeId() == null ? "" : plugin.maps().activeId()));

        sb.append(",\"maps\":[");
        boolean first = true;
        for (LiteMap map : plugin.maps().all()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{')
                    .append("\"id\":").append(q(map.id())).append(',')
                    .append("\"name\":").append(q(map.displayName())).append(',')
                    .append("\"world\":").append(q(map.worldName())).append(',')
                    .append("\"hasSpawn\":").append(map.hasSpawn()).append(',')
                    .append("\"points\":").append(map.spawnPoints().size()).append(',')
                    .append("\"active\":").append(map.id().equals(plugin.maps().activeId()))
                    .append('}');
        }
        sb.append(']');

        sb.append(",\"boosts\":[");
        first = true;
        for (var entry : plugin.boost().activeBoosts().entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{')
                    .append("\"type\":").append(q(entry.getKey().label())).append(',')
                    .append("\"multiplier\":").append(entry.getValue().multiplier()).append(',')
                    .append("\"remaining\":").append(entry.getValue().remainingSeconds())
                    .append('}');
        }
        sb.append(']');

        sb.append(",\"hotfixes\":").append(q(String.join(",", plugin.hotfixes().loadedIds())));
        sb.append(",\"rotation\":{")
                .append("\"mode\":").append(q(plugin.settings().rotationMode())).append(',')
                .append("\"remaining\":").append(plugin.rotation().remainingSeconds()).append(',')
                .append("\"votes\":").append(plugin.votes().totalVotes())
                .append('}');
        sb.append('}');
        return sb.toString();
    }

    // ---------------- 配置读写 ----------------

    /** 敏感配置项在网页上一律显示成这个；页面把这个值提交回来表示「不要改」。 */
    private static final String SECRET_MASK = "***";

    /** 不该明文回显的键：数据库密码、网页 token、代理密钥等。 */
    private static boolean isSecretKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("password") || k.endsWith(".token") || k.contains("secret");
    }

    private void handleConfigFlat(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        String json = callSync(() -> {
            var config = plugin.getConfig();
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (String key : config.getKeys(true)) {
                if (config.isConfigurationSection(key)) {
                    continue;
                }
                Object value = config.get(key);
                if (value == null || value instanceof ConfigurationSection) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(q(key)).append(':');
                if (isSecretKey(key)) {
                    // 密码 / token 只回显掩码，避免截图、录屏、共享屏幕时泄露
                    sb.append(q(SECRET_MASK));
                } else if (value instanceof Number || value instanceof Boolean) {
                    sb.append(value);
                } else if (value instanceof List<?> list) {
                    sb.append('[');
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) {
                            sb.append(',');
                        }
                        sb.append(q(String.valueOf(list.get(i))));
                    }
                    sb.append(']');
                } else {
                    sb.append(q(String.valueOf(value)));
                }
            }
            return sb.append('}').toString();
        });
        send(exchange, 200, "application/json; charset=utf-8", json == null ? "{}" : json);
    }

    /** 表单提交：path=value&path2=value2，类型按原配置值推断。 */
    private void handleSet(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=utf-8", "只接受 POST");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = parseForm(body);
        String result = callSync(() -> {
            int applied = 0;
            int skipped = 0;
            var config = plugin.getConfig();
            for (var entry : values.entrySet()) {
                String path = entry.getKey();
                String raw = entry.getValue();
                if (SECRET_MASK.equals(raw)) {
                    // 页面没改这一项，保持数据库密码 / token 原值，不能被掩码覆盖
                    skipped++;
                    continue;
                }
                Object current = config.get(path);
                try {
                    if (current instanceof Boolean) {
                        config.set(path, Boolean.parseBoolean(raw) || "on".equalsIgnoreCase(raw));
                    } else if (current instanceof Integer) {
                        config.set(path, Integer.parseInt(raw.trim()));
                    } else if (current instanceof Long) {
                        config.set(path, Long.parseLong(raw.trim()));
                    } else if (current instanceof Double) {
                        config.set(path, Double.parseDouble(raw.trim()));
                    } else if (current instanceof List<?>) {
                        List<String> list = new ArrayList<>();
                        for (String item : raw.split("\n")) {
                            if (!item.isBlank()) {
                                list.add(item.trim());
                            }
                        }
                        config.set(path, list);
                    } else {
                        config.set(path, raw);
                    }
                    applied++;
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("网页提交的 " + path + " 不是数字: " + raw);
                }
            }
            plugin.saveConfig();
            plugin.reload();
            return applied + " 项已保存"
                    + (skipped > 0 ? "（" + skipped + " 项敏感字段保持不变）" : "");
        });
        send(exchange, 200, "text/plain; charset=utf-8", result == null ? "超时" : result);
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            send(exchange, 404, "text/plain; charset=utf-8", "config.yml 不存在");
            return;
        }
        send(exchange, 200, "text/plain; charset=utf-8",
                Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    private void handleSave(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=utf-8", "只接受 POST");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(body);
        } catch (RuntimeException | org.bukkit.configuration.InvalidConfigurationException ex) {
            send(exchange, 400, "text/plain; charset=utf-8", "YAML 解析失败: " + ex.getMessage());
            return;
        }
        File file = new File(plugin.getDataFolder(), "config.yml");
        Files.writeString(file.toPath(), body, StandardCharsets.UTF_8);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                plugin.reload();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("网页保存后重载失败: " + ex.getMessage());
            }
        });
        send(exchange, 200, "text/plain; charset=utf-8", "已保存并重载");
    }

    private void handleReload(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.reload());
        send(exchange, 200, "text/plain; charset=utf-8", "已重载");
    }

    // ---------------- 动作 ----------------

    private void handleAction(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String name = query.getOrDefault("name", "");
        String result = callSync(() -> switch (name) {
            case "backup" -> {
                // 完整备份（配置 + 数据库）是 IO 活，丢后台跑，别卡主线程
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                        () -> plugin.backups().create("网页"));
                yield "已在后台开始备份（结果看控制台日志，或用 /lb backup list 查看）";
            }
            case "map_next" -> {
                plugin.rotation().rotate();
                yield "已切到下一张地图";
            }
            case "boost_stop" -> {
                plugin.boost().stop(java.util.EnumSet.allOf(com.litebrawl.boost.BoostService.BoostType.class));
                yield "已关闭所有加成";
            }
            case "scan" -> {
                plugin.maps().scanActive(new com.litebrawl.map.EmeraldScanner.Callback() {
                    @Override
                    public void done(List<int[]> points) {
                        LiteMap map = plugin.maps().active().orElse(null);
                        if (map != null) {
                            map.setSpawnPoints(points);
                            plugin.maps().save();
                        }
                        plugin.getLogger().info("网页触发扫描完成: " + points.size() + " 个出生点");
                    }

                    @Override
                    public void failed(String reason) {
                        plugin.getLogger().warning("网页触发扫描失败: " + reason);
                    }
                });
                yield "已开始扫描出生方块（看控制台进度）";
            }
            case "boards" -> {
                StringBuilder sb = new StringBuilder();
                for (BoardType type : plugin.boards().placed()) {
                    sb.append(type.id()).append(' ');
                }
                yield sb.length() == 0 ? "还没有放置全息榜" : ("已放置: " + sb);
            }
            case "periods" -> {
                StringBuilder sb = new StringBuilder();
                for (Period period : Period.values()) {
                    sb.append(period.name()).append('=').append(period.key()).append("  ");
                }
                yield sb.toString();
            }
            default -> "未知动作: " + name;
        });
        String text = result == null ? "超时" : result;
        // 未知动作按 4xx 返回，避免调用方把「没这个动作」当成成功
        int code = text.startsWith("未知动作") ? 400 : 200;
        send(exchange, code, "text/plain; charset=utf-8", text);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        handleState(exchange);
    }

    // ---------------- 多文件管理 ----------------

    private void handleFiles(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        File dir = plugin.getDataFolder();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String name : EDITABLE_FILES) {
            File file = new File(dir, name);
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{').append("\"name\":").append(q(name))
                    .append(",\"exists\":").append(file.exists())
                    .append(",\"size\":").append(file.exists() ? file.length() : 0)
                    .append(",\"modified\":").append(file.exists() ? file.lastModified() : 0)
                    .append('}');
        }
        send(exchange, 200, "application/json; charset=utf-8", sb.append(']').toString());
    }

    private void handleFile(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        String name = parseQuery(exchange.getRequestURI().getRawQuery()).getOrDefault("name", "");
        if (!EDITABLE_FILES.contains(name)) {
            send(exchange, 400, "text/plain; charset=utf-8", "不在可编辑白名单: " + name);
            return;
        }
        File file = new File(plugin.getDataFolder(), name);

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (name.endsWith(".yml")) {
                try {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.loadFromString(body);
                } catch (RuntimeException | org.bukkit.configuration.InvalidConfigurationException ex) {
                    send(exchange, 400, "text/plain; charset=utf-8", "YAML 解析失败: " + ex.getMessage());
                    return;
                }
            }
            Files.writeString(file.toPath(), body, StandardCharsets.UTF_8);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.reload();
                if (name.equals("maps.yml")) {
                    plugin.maps().load();
                } else if (name.equals("boards.yml")) {
                    plugin.boards().reload();
                } else if (name.equals("npcs.yml")) {
                    plugin.npcs().load();
                } else if (name.equals("boost.yml")) {
                    plugin.boost().restart();
                }
            });
            send(exchange, 200, "text/plain; charset=utf-8", "已保存 " + name);
            return;
        }

        if (!file.exists()) {
            send(exchange, 404, "text/plain; charset=utf-8", "文件不存在: " + name);
            return;
        }
        send(exchange, 200, "text/plain; charset=utf-8",
                Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    // ---------------- 数据库阅读 ----------------

    private void handleDbTop(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            unauthorized(exchange);
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String column = query.getOrDefault("column", "kills").toLowerCase(Locale.ROOT);
        int limit;
        try {
            limit = Math.max(1, Math.min(100, Integer.parseInt(query.getOrDefault("limit", "20"))));
        } catch (NumberFormatException ex) {
            limit = 20;
        }
        final int finalLimit = limit;
        String json = callSync(() -> {
            StringBuilder sb = new StringBuilder("{\"column\":").append(q(column))
                    .append(",\"rows\":[");
            boolean first = true;
            for (var profile : plugin.storage().top(column, finalLimit)) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('{')
                        .append("\"name\":").append(q(profile.getName())).append(',')
                        .append("\"coins\":").append(profile.getCoins()).append(',')
                        .append("\"experience\":").append(profile.getExperience()).append(',')
                        .append("\"level\":").append(plugin.profiles().levelOf(profile.getExperience())).append(',')
                        .append("\"kills\":").append(profile.getKills()).append(',')
                        .append("\"deaths\":").append(profile.getDeaths()).append(',')
                        .append("\"assists\":").append(profile.getAssists())
                        .append('}');
            }
            return sb.append("]}").toString();
        });
        send(exchange, 200, "application/json; charset=utf-8", json == null ? "{\"rows\":[]}" : json);
    }

    // ---------------- 玩家面板 ----------------

    /** 给玩家生成 6 位绑定码（5 分钟有效）。 */
    public String issueLinkCode(org.bukkit.entity.Player player) {
        return issueLinkCode(player.getUniqueId());
    }

    /** 给指定 UUID 生成绑定码：管理员可以替离线玩家发码。 */
    public String issueLinkCode(java.util.UUID uuid) {
        String code = String.format(Locale.ROOT, "%06d", java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(1_000_000));
        // 先写过期时间，再写值：清扫任务是「删没有 expiry 的孤儿 code」，
        // 顺序反过来的话，清扫插在两次 put 之间会把刚发出去的码删掉。
        linkExpiry.put(code, System.currentTimeMillis() + 5 * 60 * 1000L);
        linkCodes.put(code, uuid);
        return code;
    }

    private void handleLink(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String code = query.getOrDefault("code", "").trim();
        UUID uuid = linkCodes.remove(code);
        Long expiry = linkExpiry.remove(code);
        if (uuid == null || expiry == null || expiry < System.currentTimeMillis()) {
            send(exchange, 401, "text/plain; charset=utf-8", "绑定码无效或已过期，回游戏里再打一次 /link");
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        // 同样先写过期时间再写值，避免被清扫当成孤儿令牌删掉
        playerTokenExpiry.put(token, System.currentTimeMillis() + 12 * 60 * 60 * 1000L);
        playerTokens.put(token, uuid);
        var offline = Bukkit.getOfflinePlayer(uuid);
        send(exchange, 200, "application/json; charset=utf-8",
                "{\"token\":" + q(token) + ",\"name\":" + q(offline.getName() == null ? "?" : offline.getName()) + "}");
    }

    private UUID resolvePlayer(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-Player-Token");
        if (token == null || token.isBlank()) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            token = query.get("ptoken");
        }
        if (token == null) {
            return null;
        }
        Long expiry = playerTokenExpiry.get(token);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            playerTokens.remove(token);
            playerTokenExpiry.remove(token);
            return null;
        }
        return playerTokens.get(token);
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        UUID uuid = resolvePlayer(exchange);
        if (uuid == null) {
            send(exchange, 401, "text/plain; charset=utf-8", "未绑定，先在游戏里打 /link");
            return;
        }
        String json = callSync(() -> {
            com.litebrawl.data.Profile profile = profileOf(uuid);
            if (profile == null) {
                return "{\"error\":\"没有数据\"}";
            }
            Player online = Bukkit.getPlayer(uuid);
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"name\":").append(q(profile.getName()))
                    .append(",\"online\":").append(online != null)
                    .append(",\"coins\":").append(profile.getCoins())
                    .append(",\"experience\":").append(profile.getExperience())
                    .append(",\"level\":").append(plugin.profiles().levelOf(profile.getExperience()))
                    .append(",\"kills\":").append(profile.getKills())
                    .append(",\"deaths\":").append(profile.getDeaths())
                    .append(",\"assists\":").append(profile.getAssists());
            sb.append(",\"owned\":[");
            boolean first = true;
            for (String id : profile.ownedCosmetics()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                var cosmetic = plugin.cosmetics().get(id);
                if (cosmetic == null) {
                    sb.append('{').append("\"id\":").append(q(id))
                            .append(",\"name\":").append(q(plain(id)))
                            .append(",\"type\":").append(q("?")).append('}');
                } else {
                    sb.append('{').append("\"id\":").append(q(cosmetic.id()))
                            .append(",\"name\":").append(q(plain(cosmetic.name())))
                            .append(",\"type\":").append(q(cosmetic.type().name())).append('}');
                }
            }
            sb.append(']');
            sb.append(",\"equipped\":{");
            first = true;
            for (var type : com.litebrawl.cosmetic.CosmeticType.values()) {
                String id = profile.equippedCosmetic(type.name());
                if (id == null) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(q(type.name())).append(':').append(q(id));
            }
            sb.append('}');
            return sb.append('}').toString();
        });
        send(exchange, 200, "application/json; charset=utf-8", json == null ? "{}" : json);
    }

    private void handleEquip(HttpExchange exchange) throws IOException {
        UUID uuid = resolvePlayer(exchange);
        if (uuid == null) {
            unauthorizedPlayer(exchange);
            return;
        }
        String id = parseQuery(exchange.getRequestURI().getRawQuery()).getOrDefault("id", "");
        String result = callSync(() -> {
            var cosmetic = plugin.cosmetics().get(id);
            if (cosmetic == null) {
                return "外观不存在: " + id;
            }
            com.litebrawl.data.Profile profile = profileOf(uuid);
            if (profile == null || !profile.ownsCosmetic(cosmetic.id())) {
                return "你还没有购买这个外观";
            }
            profile.equipCosmetic(cosmetic.type().name(), cosmetic.id());
            saveProfile(uuid, profile);
            return "已装备 " + cosmetic.id();
        });
        send(exchange, 200, "text/plain; charset=utf-8", result == null ? "超时" : result);
    }

    private void handleUnequip(HttpExchange exchange) throws IOException {
        UUID uuid = resolvePlayer(exchange);
        if (uuid == null) {
            unauthorizedPlayer(exchange);
            return;
        }
        String typeName = parseQuery(exchange.getRequestURI().getRawQuery()).getOrDefault("type", "");
        String result = callSync(() -> {
            var type = com.litebrawl.cosmetic.CosmeticType.byName(typeName);
            if (type == null) {
                return "未知类型: " + typeName;
            }
            com.litebrawl.data.Profile profile = profileOf(uuid);
            if (profile == null) {
                return "没有数据";
            }
            profile.equipCosmetic(type.name(), null);
            saveProfile(uuid, profile);
            return "已卸下 " + type.label();
        });
        send(exchange, 200, "text/plain; charset=utf-8", result == null ? "超时" : result);
    }

    private void unauthorizedPlayer(HttpExchange exchange) throws IOException {
        send(exchange, 401, "text/plain; charset=utf-8", "未绑定，先在游戏里打 /link");
    }

    private com.litebrawl.data.Profile profileOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return plugin.profiles().get(online);
        }
        return plugin.storage().find(uuid).orElse(null);
    }

    private void saveProfile(UUID uuid, com.litebrawl.data.Profile profile) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.profiles().saveAsync(profile);
        } else {
            plugin.storage().save(profile);
        }
    }

    // ---------------- 工具 ----------------

    private String callSync(java.util.function.Supplier<String> supplier) {
        try {
            Future<String> future = Bukkit.getScheduler().callSyncMethod(plugin, supplier::get);
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            plugin.getLogger().warning("网页请求在主线程执行失败: " + ex.getMessage());
            return null;
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String token = plugin.settings().webToken();
        if (token == null || token.isBlank()) {
            return true;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String provided = exchange.getRequestHeaders().getFirst("X-Token");
        if (provided == null || provided.isBlank()) {
            provided = query.get("token");
        }
        return token.equals(provided);
    }

    private void unauthorized(HttpExchange exchange) throws IOException {
        send(exchange, 401, "text/plain; charset=utf-8", "token 无效");
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) {
            return map;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
        return map;
    }

    private static Map<String, String> parseQuery(String raw) {
        return parseForm(raw);
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            return value;
        }
    }

    /** 去掉 MiniMessage 标签，网页端只显示纯文本（配置里的外观名带颜色标签）。 */
    private static String plain(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("<[^<>]{1,80}>", "");
    }

    private static String q(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 给页面提示用：格式化一个颜色值（保留给后续扩展）。 */
    static String hint(String text) {
        return Msg.plain(Msg.mini(text));
    }
}
