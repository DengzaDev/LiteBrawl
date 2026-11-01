package com.litebrawl.data;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.Settings;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * 单连接的 JDBC 实现，同时支持 SQLite 与 MySQL。
 *
 * <p>小玩法服务器并发极低，用一条连接 + synchronized 串行化足够，
 * 好处是不用额外引入连接池依赖。</p>
 */
public final class SqlStorage implements Storage {

    private static final String COLUMNS = "uuid,name,coins,experience,kills,deaths,assists,updated_at,cosmetics,settings";

    private final LiteBrawlPlugin plugin;
    private final Settings settings;

    private Connection connection;
    private boolean mysql;
    private String table;
    private String periodTable;

    public SqlStorage(LiteBrawlPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @Override
    public synchronized void init() throws Exception {
        this.mysql = "MYSQL".equals(settings.storageType());
        this.table = settings.mysqlTable();
        this.periodTable = settings.mysqlTable() + "_period";
        this.connection = open();
        createTable();
        createPeriodTable();
        repairColumnShift();
        plugin.getLogger().info("存储已连接: " + describe());
    }

    private Connection open() throws Exception {
        if (mysql) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://" + settings.mysqlHost() + ":" + settings.mysqlPort()
                    + "/" + settings.mysqlDatabase()
                    + "?useSSL=" + settings.mysqlUseSsl()
                    + "&allowPublicKeyRetrieval=true"
                    + "&createDatabaseIfNotExist=true"
                    + "&characterEncoding=utf8"
                    + "&serverTimezone=UTC";
            Properties props = new Properties();
            props.setProperty("user", settings.mysqlUser());
            props.setProperty("password", settings.mysqlPassword());
            return DriverManager.getConnection(url, props);
        }

        Class.forName("org.sqlite.JDBC");
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("无法创建插件数据目录: " + folder.getAbsolutePath());
        }
        File db = new File(folder, "litebrawl.db");
        return DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
    }

    private void createTable() throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "name VARCHAR(32) NOT NULL,"
                + "coins BIGINT NOT NULL DEFAULT 0,"
                + "experience BIGINT NOT NULL DEFAULT 0,"
                + "kills INT NOT NULL DEFAULT 0,"
                + "deaths INT NOT NULL DEFAULT 0,"
                + "assists INT NOT NULL DEFAULT 0,"
                + "cosmetics VARCHAR(512) NOT NULL DEFAULT '',"
                + "settings VARCHAR(512) NOT NULL DEFAULT '',"
                + "updated_at BIGINT NOT NULL DEFAULT 0)";
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(ddl);
        }
        // 老版本数据库补列（已经存在会抛错，忽略即可）
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table
                    + " ADD COLUMN cosmetics VARCHAR(512) NOT NULL DEFAULT ''");
        } catch (SQLException ignored) {
            // 列已存在
        }
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table
                    + " ADD COLUMN settings VARCHAR(512) NOT NULL DEFAULT ''");
        } catch (SQLException ignored) {
            // 列已存在
        }
    }

    /**
     * 修 1.9.2 及更早版本写错列顺序留下的脏数据。
     *
     * <p>那时候参数顺序是 updated_at/cosmetics/settings 错位的，
     * 导致 cosmetics 值写进了 updated_at、settings 值写进了 cosmetics、时间戳写进了 settings。
     * SQLite 里这三个值正好是一次轮换，可以一条 SQL 原地还原（右侧全部取旧值）。</p>
     */
    private void repairColumnShift() {
        if (mysql) {
            // MySQL 的 BIGINT 列会直接拒绝字符串，写不进去，不需要修
            return;
        }
        String sql = "UPDATE " + table + " SET"
                + " cosmetics = updated_at,"
                + " settings = cosmetics,"
                + " updated_at = settings"
                + " WHERE typeof(updated_at) = 'text'";
        try (Statement st = connection.createStatement()) {
            int fixed = st.executeUpdate(sql);
            if (fixed > 0) {
                plugin.getLogger().info("修复了 " + fixed + " 条错位的历史数据（外观/设置列）");
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("修复错位数据失败: " + ex.getMessage());
        }
    }

    private void createPeriodTable() throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS " + periodTable + " ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "name VARCHAR(32) NOT NULL,"
                + "period_key VARCHAR(24) NOT NULL,"
                + "kills INT NOT NULL DEFAULT 0,"
                + "deaths INT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (uuid, period_key))";
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(ddl);
        }
    }

    private void ensureOpen() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                connection = open();
            } catch (Exception e) {
                throw new SQLException("重新连接数据库失败", e);
            }
        }
    }

    @Override
    public synchronized Optional<Profile> find(UUID uuid) {
        String sql = "SELECT name,coins,experience,kills,deaths,assists,cosmetics,settings FROM " + table + " WHERE uuid = ?";
        try {
            ensureOpen();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    Profile profile = new Profile(uuid, rs.getString("name"));
                    profile.setCoins(rs.getLong("coins"));
                    profile.setExperience(rs.getLong("experience"));
                    profile.setKills(rs.getInt("kills"));
                    profile.setDeaths(rs.getInt("deaths"));
                    profile.setAssists(rs.getInt("assists"));
                    profile.loadCosmetics(rs.getString("cosmetics"));
                    profile.loadSettings(rs.getString("settings"));
                    profile.setDirty(false);
                    return Optional.of(profile);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取玩家数据失败 (" + uuid + "): " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public synchronized void save(Profile profile) {
        String sql = mysql
                ? "INSERT INTO " + table + " (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE name=VALUES(name),coins=VALUES(coins),"
                + "experience=VALUES(experience),kills=VALUES(kills),deaths=VALUES(deaths),"
                + "assists=VALUES(assists),cosmetics=VALUES(cosmetics),settings=VALUES(settings),updated_at=VALUES(updated_at)"
                : "INSERT INTO " + table + " (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?)"
                + " ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,coins=excluded.coins,"
                + "experience=excluded.experience,kills=excluded.kills,deaths=excluded.deaths,"
                + "assists=excluded.assists,cosmetics=excluded.cosmetics,settings=excluded.settings,updated_at=excluded.updated_at";
        try {
            ensureOpen();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                ps.setString(i++, profile.getUuid().toString());
                ps.setString(i++, profile.getName());
                ps.setLong(i++, profile.getCoins());
                ps.setLong(i++, profile.getExperience());
                ps.setInt(i++, profile.getKills());
                ps.setInt(i++, profile.getDeaths());
                ps.setInt(i++, profile.getAssists());
                // 顺序必须和 COLUMNS 完全一致：assists -> updated_at -> cosmetics -> settings
                ps.setLong(i++, System.currentTimeMillis());
                ps.setString(i++, profile.serializeCosmetics());
                ps.setString(i, profile.serializeSettings());
                ps.executeUpdate();
                profile.setDirty(false);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("保存玩家数据失败 (" + profile.getUuid() + "): " + e.getMessage());
        }
    }

    @Override
    public synchronized List<Profile> top(String column, int limit) {
        String order = switch (column == null ? "" : column.toLowerCase(Locale.ROOT)) {
            case "coins" -> "coins";
            case "experience", "level" -> "experience";
            case "deaths" -> "deaths";
            case "assists" -> "assists";
            default -> "kills";
        };
        String sql = "SELECT name,coins,experience,kills,deaths,assists FROM " + table
                + " ORDER BY " + order + " DESC, name ASC LIMIT ?";
        List<Profile> result = new ArrayList<>();
        try {
            ensureOpen();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    int index = 0;
                    while (rs.next()) {
                        Profile profile = new Profile(new UUID(0L, ++index), rs.getString("name"));
                        profile.setCoins(rs.getLong("coins"));
                        profile.setExperience(rs.getLong("experience"));
                        profile.setKills(rs.getInt("kills"));
                        profile.setDeaths(rs.getInt("deaths"));
                        profile.setAssists(rs.getInt("assists"));
                        profile.setDirty(false);
                        result.add(profile);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取排行榜失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public synchronized void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        connection = null;
    }

    @Override
    public synchronized void recordStat(UUID uuid, String name, String periodKey, String column, int delta) {
        if (periodKey == null || delta == 0) {
            return;
        }
        String col = "deaths".equalsIgnoreCase(column) ? "deaths" : "kills";
        String sql = mysql
                ? "INSERT INTO " + periodTable + " (uuid,name,period_key,kills,deaths) VALUES (?,?,?,?,?)"
                + " ON DUPLICATE KEY UPDATE name=VALUES(name), "
                + col + "=" + col + "+VALUES(" + col + ")"
                : "INSERT INTO " + periodTable + " (uuid,name,period_key,kills,deaths) VALUES (?,?,?,?,?)"
                + " ON CONFLICT(uuid,period_key) DO UPDATE SET name=excluded.name, "
                + col + "=" + col + "+excluded." + col;
        try {
            ensureOpen();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, periodKey);
                ps.setInt(4, col.equals("kills") ? delta : 0);
                ps.setInt(5, col.equals("deaths") ? delta : 0);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("写入周期统计失败: " + e.getMessage());
        }
    }

    @Override
    public synchronized List<Profile> topPeriod(String periodKey, String column, int limit) {
        String order = "deaths".equalsIgnoreCase(column) ? "deaths" : "kills";
        String sql = "SELECT name, " + order + " AS value FROM " + periodTable
                + " WHERE period_key = ? ORDER BY value DESC, name ASC LIMIT ?";
        List<Profile> result = new ArrayList<>();
        try {
            ensureOpen();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, periodKey);
                ps.setInt(2, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    int index = 0;
                    while (rs.next()) {
                        Profile profile = new Profile(new UUID(0L, ++index), rs.getString("name"));
                        int value = rs.getInt("value");
                        if (order.equals("kills")) {
                            profile.setKills(value);
                        } else {
                            profile.setDeaths(value);
                        }
                        profile.setDirty(false);
                        result.add(profile);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取周期榜失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public String describe() {
        if (mysql) {
            return "MySQL " + settings.mysqlHost() + ":" + settings.mysqlPort()
                    + "/" + settings.mysqlDatabase() + " (表 " + table + ")";
        }
        return "SQLite " + new File(plugin.getDataFolder(), "litebrawl.db").getAbsolutePath();
    }
}
