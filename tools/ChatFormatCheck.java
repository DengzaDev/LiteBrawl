import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** 复现并验证聊天格式的标签闭合问题。 */
public class ChatFormatCheck {

    private static final String MESSAGE_MARKER = "\u0001MSG\u0001";
    private static final String DISPLAY_MARKER = "\u0001DISP\u0001";

    public static void main(String[] args) {
        // 与 config.yml 默认一致
        String format = "%team%%display%<gray>:</gray> <white>%message%</white>";

        String levelText = "<gray>[</gray><gradient:#f6d365:#fda085>Lv.1</gradient><gray>]</gray>";
        String name = "dengza";
        Component display = MiniMessage.miniMessage().deserialize(levelText + name);

        String template = format
                .replace("%message%", MESSAGE_MARKER)
                .replace("%display%", DISPLAY_MARKER);
        String filled = template
                .replace("%level%", levelText)
                .replace("%player%", name)
                .replace("%effect%", "")
                .replace("%team%", "");

        Component rendered = MiniMessage.miniMessage().deserialize(filled);
        Component line = rendered
                .replaceText(b -> b.matchLiteral(DISPLAY_MARKER).replacement(display))
                .replaceText(b -> b.matchLiteral(MESSAGE_MARKER).replacement(Component.text("2")));

        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        System.out.println("输出: [" + plain + "]");
        System.out.println("含未闭合标签: " + plain.contains("</") + " / 含尖括号: " + plain.contains("<"));
        System.out.println("等于期望: " + plain.equals("[Lv.1]dengza: 2"));

        // 对照：老写法（把格式切成两半分别解析）会怎样
        String oldTail = "</white>";
        String oldPlain = PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(oldTail));
        System.out.println("老写法尾段解析结果: [" + oldPlain + "]");
    }
}
