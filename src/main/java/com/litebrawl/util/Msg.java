package com.litebrawl.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * MiniMessage 文本工具。
 * 所有面向玩家的文本都从这个类出口，避免各处重复解析逻辑。
 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {
    }

    /** 解析 MiniMessage；格式错误时降级为纯文本，避免整条消息丢失。 */
    public static Component mini(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        try {
            return MM.deserialize(text);
        } catch (RuntimeException ex) {
            return Component.text(text);
        }
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** 发送一条 MiniMessage，并按 key/value 顺序替换 %key% 占位符。 */
    public static void send(CommandSender to, String miniText, String... placeholders) {
        if (to == null) {
            return;
        }
        to.sendMessage(mini(replace(miniText, placeholders)));
    }

    /** 发送带插件前缀的消息。 */
    public static void info(CommandSender to, String prefix, String miniText, String... placeholders) {
        send(to, prefix + miniText, placeholders);
    }

    public static String replace(String text, String... placeholders) {
        if (text == null) {
            return "";
        }
        String out = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String value = placeholders[i + 1];
            out = out.replace("%" + placeholders[i] + "%", value == null ? "" : value);
        }
        return out;
    }
}
