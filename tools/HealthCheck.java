import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** 验证血量条格式：只做字符串替换 + MiniMessage 解析，看纯文本结果。 */
public class HealthCheck {
    public static void main(String[] args) {
        String format = "<red>%full%</red><dark_gray>%empty%</dark_gray>";
        String separator = " ";

        // 模拟 14 点血（20 血上限 → 10 心，7 满 3 空）
        double health = 14.0;
        double maxHealth = 20.0;
        int maxHearts = 10;
        int total = Math.min(maxHearts, (int) Math.ceil(maxHealth / 2.0));
        int full = Math.min(total, (int) Math.ceil(health / 2.0));

        String rendered = format.replace("%full%", "❤".repeat(full))
                .replace("%empty%", "♡".repeat(total - full));
        Component component = MiniMessage.miniMessage().deserialize(separator + rendered);
        String plain = PlainTextComponentSerializer.plainText().serialize(component);

        System.out.println("full=" + full + " total=" + total);
        System.out.println("纯文本: [" + plain + "]");
        System.out.println("心数正确: " + plain.equals(" ❤❤❤❤❤❤❤♡♡♡"));
        System.out.println("含未解析标签: " + plain.contains("<"));
    }
}
