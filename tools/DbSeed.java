import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * 往 litebrawl.db 的真实表里塞一条假记录，用来验证网页端「数据库浏览 / 玩家面板」。
 * 用法：
 *   java -cp sqlite-jdbc.jar DbSeed.java <db路径> <表名> <玩家名>
 * UUID 按离线模式规则由名字推导（OfflinePlayer:名字），和 /litebrawl link <名字> 对得上。
 * 同名记录先删后插，可重复运行。
 */
public final class DbSeed {

    private static final String COLS =
            "uuid,name,coins,experience,kills,deaths,assists,updated_at,cosmetics,settings";

    public static void main(String[] args) throws Exception {
        String db = args.length > 0 ? args[0] : "litebrawl.db";
        String table = args.length > 1 ? args[1] : "litebrawl_profiles";
        String name = args.length > 2 ? args[2] : "网页测试甲";

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM " + table + " WHERE name = ?")) {
                del.setString(1, name);
                del.executeUpdate();
            }
            String sql = "INSERT INTO " + table + " (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setInt(3, 1234);
                ps.setInt(4, 9000);
                ps.setInt(5, 42);
                ps.setInt(6, 7);
                ps.setInt(7, 3);
                ps.setLong(8, System.currentTimeMillis());
                // 已拥有的外观 + 已装备（格式：id,id|TYPE=id;TYPE=id）
                ps.setString(9, "title_slayer,kill_blood,chat_gold|TITLE=title_slayer");
                ps.setString(10, "");
                ps.executeUpdate();
            }
            System.out.println("seeded " + name + " uuid=" + uuid + " into " + table);
        }
    }
}
