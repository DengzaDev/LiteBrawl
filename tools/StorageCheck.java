import java.sql.*;

/**
 * 验证两件事：
 *   1. 旧的错位写法会写坏数据
 *   2. 修复 SQL 能把三个值轮换回去
 *   3. 新的正确顺序写读一致
 */
public class StorageCheck {

    private static final String COLUMNS = "uuid,name,coins,experience,kills,deaths,assists,updated_at,cosmetics,settings";

    public static void main(String[] args) throws Exception {
        String url = "jdbc:sqlite:" + System.getProperty("java.io.tmpdir") + "/litebrawl-check.db";
        new java.io.File(url.substring("jdbc:sqlite:".length())).delete();

        String cosmetics = "kill_blood,title_slayer|TITLE=title_slayer;NAME_STYLE=name_gold";
        String settings = "sidebar=true;tab=false;layout=archer";
        long now = 1793512345000L;

        try (Connection c = DriverManager.getConnection(url)) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE t (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(32),"
                        + " coins BIGINT, experience BIGINT, kills INT, deaths INT, assists INT,"
                        + " updated_at BIGINT, cosmetics VARCHAR(512), settings VARCHAR(512))");
            }

            // === 旧代码的错误绑定顺序：assists -> cosmetics -> settings -> updatedAt ===
            insert(c, "old", cosmetics, settings, now);

            System.out.println("错位后读取:");
            dump(c, "old");

            // === 修复 SQL：三个值轮换回去 ===
            try (Statement st = c.createStatement()) {
                int fixed = st.executeUpdate("UPDATE t SET cosmetics = updated_at, settings = cosmetics,"
                        + " updated_at = settings WHERE typeof(updated_at) = 'text'");
                System.out.println("修复行数: " + fixed);
            }

            System.out.println("修复后读取:");
            String[] after = dump(c, "old");
            System.out.println("cosmetics 还原正确: " + cosmetics.equals(after[0]));
            System.out.println("settings  还原正确: " + settings.equals(after[1]));

            // === 新代码的正确顺序 ===
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO t (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?)"
                            + " ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,coins=excluded.coins,"
                            + "experience=excluded.experience,kills=excluded.kills,deaths=excluded.deaths,"
                            + "assists=excluded.assists,cosmetics=excluded.cosmetics,"
                            + "settings=excluded.settings,updated_at=excluded.updated_at")) {
                ps.setString(1, "new");
                ps.setString(2, "dengza");
                ps.setLong(3, 100);
                ps.setLong(4, 500);
                ps.setInt(5, 7);
                ps.setInt(6, 2);
                ps.setInt(7, 1);
                ps.setLong(8, now);
                ps.setString(9, cosmetics);
                ps.setString(10, settings);
                ps.executeUpdate();
            }
            System.out.println("新写法读取:");
            String[] fresh = dump(c, "new");
            System.out.println("cosmetics 正确: " + cosmetics.equals(fresh[0]));
            System.out.println("settings  正确: " + settings.equals(fresh[1]));
        }
    }

    private static void insert(Connection c, String uuid, String cosmetics, String settings, long now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO t (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, uuid);
            ps.setString(2, "dengza");
            ps.setLong(3, 100);
            ps.setLong(4, 500);
            ps.setInt(5, 7);
            ps.setInt(6, 2);
            ps.setInt(7, 1);
            // 旧代码：这里先塞 cosmetics，再 settings，最后才是时间戳
            ps.setString(8, cosmetics);
            ps.setString(9, settings);
            ps.setLong(10, now);
            ps.executeUpdate();
        }
    }

    private static String[] dump(Connection c, String uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT updated_at, cosmetics, settings FROM t WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                String updated = String.valueOf(rs.getObject("updated_at"));
                String cos = rs.getString("cosmetics");
                String set = rs.getString("settings");
                System.out.println("  updated_at=" + updated);
                System.out.println("  cosmetics=" + cos);
                System.out.println("  settings =" + set);
                return new String[]{cos, set};
            }
        }
    }
}
