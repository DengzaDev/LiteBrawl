import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

/** 删掉 DbSeed 塞进去的测试记录。用法：java -cp sqlite-jdbc.jar DbPurge.java <db> <表名> <名字前缀> */
public final class DbPurge {
    public static void main(String[] args) throws Exception {
        String db = args.length > 0 ? args[0] : "litebrawl.db";
        String table = args.length > 1 ? args[1] : "litebrawl_profiles";
        String like = args.length > 2 ? args[2] : "网页测试%";
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE name LIKE ?")) {
            ps.setString(1, like);
            System.out.println("deleted " + ps.executeUpdate() + " row(s) matching " + like);
        }
    }
}
