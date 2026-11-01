import java.sql.*;

/** 列出 SQLite 里的表和周期表的行数。 */
public class DbTables {
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + args[0])) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                while (rs.next()) {
                    System.out.println("表: " + rs.getString("name"));
                }
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM litebrawl_profiles_period")) {
                rs.next();
                System.out.println("周期表行数: " + rs.getInt(1));
            }
        }
    }
}
