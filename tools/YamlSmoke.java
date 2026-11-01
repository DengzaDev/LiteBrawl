import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 校验 config.yml 的语法与关键节点，避免打包出一个坏配置。 */
public class YamlSmoke {

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args.length > 0 ? args[0] : "src/main/resources/config.yml");
        try (InputStream in = Files.newInputStream(path)) {
            Map<String, Object> root = new Yaml().load(in);
            System.out.println("顶层键: " + root.keySet());

            Map<String, Object> shop = (Map<String, Object>) root.get("shop");
            Map<String, Object> items = (Map<String, Object>) shop.get("items");
            System.out.println("商店商品数: " + items.size() + " -> " + items.keySet());
            for (Map.Entry<String, Object> entry : items.entrySet()) {
                Map<String, Object> item = (Map<String, Object>) entry.getValue();
                System.out.printf("  %-12s slot=%-4s price=%-4s effect=%-10s special=%s%n",
                        entry.getKey(), item.get("slot"), item.get("price"),
                        item.getOrDefault("potion-effect", "-"),
                        item.getOrDefault("special", "-"));
            }

            Map<String, Object> bounty = (Map<String, Object>) root.get("bounty");
            System.out.println("赏金: " + bounty);

            Map<String, Object> rotation = (Map<String, Object>) root.get("rotation");
            System.out.println("轮换: enabled=" + rotation.get("enabled")
                    + " seconds=" + rotation.get("seconds")
                    + " announce=" + rotation.get("announce-seconds"));

            List<String> lethal = (List<String>) ((Map<String, Object>) root.get("spawn")).get("lethal-blocks");
            System.out.println("即死方块: " + lethal);

            Map<String, Object> sidebar = (Map<String, Object>) root.get("scoreboard");
            List<String> lines = (List<String>) sidebar.get("lines");
            System.out.println("记分板行数: " + lines.size());
        }
    }
}
