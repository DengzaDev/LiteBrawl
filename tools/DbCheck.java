import java.sql.*;

/** 检查老库迁移：cosmetics 列是否已经补上。 */
public class DbCheck {
    public static void main(String[] args) throws Exception {
        String db = args[0];
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(litebrawl_profiles)")) {
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                sb.append(rs.getString("name")).append(' ');
            }
            System.out.println("列: " + sb.toString().trim());
            System.out.println("含 cosmetics: "
                    + sb.toString().contains("cosmetics"));
        }
    }
}
