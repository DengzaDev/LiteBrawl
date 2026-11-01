import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * 周期表清理的端到端验证工具。
 *
 * <p>用法：</p>
 * <pre>
 *   java -cp sqlite-jdbc.jar PeriodSeed.java seed  &lt;db&gt; &lt;表名&gt;   # 插入边界用例
 *   java -cp sqlite-jdbc.jar PeriodSeed.java list  &lt;db&gt; &lt;表名&gt;   # 打印现有周期键
 * </pre>
 *
 * <p>插入的用例覆盖四种粒度的「该删 / 不该删」两侧，其中 {@code y:<今年>} 是最关键的一条：
 * 按起始日期判断会误删当年年榜，按结束日期判断必须保留。</p>
 */
public final class PeriodSeed {

    /** 键, 说明（说明只用于控制台打印，不入库） */
    private static final String[][] CASES = {
            {"d:2020-01-01", "远古日榜 → 应删"},
            {"d:2026-10-31", "昨天 → 应留"},
            {"w:2019-W01", "远古周榜 → 应删"},
            {"w:2026-W44", "本周 → 应留"},
            {"m:2025-01", "去年的月榜 → 应删"},
            {"m:2026-10", "上个月 → 应留"},
            {"y:2025", "去年年榜 → 应删"},
            {"y:2026", "今年年榜 → 必须保留"},
    };

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "list";
        String db = args.length > 1 ? args[1] : "litebrawl.db";
        String table = args.length > 2 ? args[2] : "litebrawl_profiles_period";

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            if ("seed".equals(mode)) {
                String sql = "INSERT INTO " + table
                        + " (uuid,name,period_key,kills,deaths) VALUES (?,?,?,?,?)";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    for (String[] item : CASES) {
                        ps.setString(1, UUID.randomUUID().toString());
                        ps.setString(2, item[1]);
                        ps.setString(3, item[0]);
                        ps.setInt(4, 7);
                        ps.setInt(5, 2);
                        ps.executeUpdate();
                    }
                }
                System.out.println("已插入 " + CASES.length + " 条测试周期数据");
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT period_key, name FROM " + table + " ORDER BY period_key")) {
                int count = 0;
                System.out.println("--- " + table + " 现有周期键 ---");
                while (rs.next()) {
                    System.out.println("  " + rs.getString(1) + "   " + rs.getString(2));
                    count++;
                }
                System.out.println("共 " + count + " 条");
            }
        }
    }
}
