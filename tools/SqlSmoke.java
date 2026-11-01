import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/** 用与 SqlStorage 完全相同的 DDL / upsert / 查询语句跑一遍 SQLite。 */
public class SqlSmoke {

    private static final String TABLE = "litebrawl_profiles";
    private static final String COLUMNS = "uuid,name,coins,experience,kills,deaths,assists,updated_at";

    public static void main(String[] args) throws Exception {
        Path db = Files.createTempFile("litebrawl-smoke", ".db");
        Files.deleteIfExists(db);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                        + "name VARCHAR(32) NOT NULL,"
                        + "coins BIGINT NOT NULL DEFAULT 0,"
                        + "experience BIGINT NOT NULL DEFAULT 0,"
                        + "kills INT NOT NULL DEFAULT 0,"
                        + "deaths INT NOT NULL DEFAULT 0,"
                        + "assists INT NOT NULL DEFAULT 0,"
                        + "updated_at BIGINT NOT NULL DEFAULT 0)");
            }

            upsert(c, "uuid-a", "Alice", 30, 500, 6, 1, 2);
            upsert(c, "uuid-b", "Bob", 10, 900, 2, 5, 0);
            // 同一 uuid 再写一次，验证 ON CONFLICT 分支
            upsert(c, "uuid-a", "Alice", 45, 620, 7, 1, 3);

            System.out.println("rows=" + count(c));
            System.out.println("byKills=" + top(c, "kills"));
            System.out.println("byExperience=" + top(c, "experience"));
            System.out.println("byCoins=" + top(c, "coins"));
            System.out.println("aliceCoins=" + query(c, "uuid-a"));
        } finally {
            Files.deleteIfExists(db);
        }
    }

    private static void upsert(Connection c, String uuid, String name, long coins, long exp,
                               int kills, int deaths, int assists) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?)"
                + " ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,coins=excluded.coins,"
                + "experience=excluded.experience,kills=excluded.kills,deaths=excluded.deaths,"
                + "assists=excluded.assists,updated_at=excluded.updated_at";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, uuid);
            ps.setString(i++, name);
            ps.setLong(i++, coins);
            ps.setLong(i++, exp);
            ps.setInt(i++, kills);
            ps.setInt(i++, deaths);
            ps.setInt(i++, assists);
            ps.setLong(i, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private static int count(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String top(Connection c, String column) throws SQLException {
        String sql = "SELECT name,coins,experience,kills,deaths,assists FROM " + TABLE
                + " ORDER BY " + column + " DESC, name ASC LIMIT ?";
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, 10);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getString("name")).append('=').append(rs.getLong(column)).append(' ');
                }
            }
        }
        return sb.toString().trim();
    }

    private static String query(Connection c, String uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name,coins FROM " + TABLE + " WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("name") + "/" + rs.getLong("coins") : "missing";
            }
        }
    }
}
