package com.litebrawl.listener;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.data.Profile;
import com.litebrawl.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 聊天格式：[Lv.n]玩家名[称号]: 内容
 *
 * <p>关键点：整行必须一次解析。以前把格式按 %message% 切成两半分别解析，
 * 后半段的 <code>&lt;/white&gt;</code> 没有配对的开始标签，MiniMessage 会把它当普通字符
 * 原样显示出来（就是聊天里出现 <code>&lt;/white></code> 的那个 bug）。</p>
 *
 * <p>现在的做法：用不可见标记占位，整行解析完再把玩家的消息组件替换进占位符，
 * 这样标签永远成对，玩家输入也始终是纯文本组件（不会被当成格式）。</p>
 */
public final class ChatListener implements Listener {

    /** 用控制字符做占位，正常聊天里不可能出现 */
    private static final String MESSAGE_MARKER = "\u0001MSG\u0001";
    private static final String DISPLAY_MARKER = "\u0001DISP\u0001";
    private static final String MESSAGE_TOKEN = "%message%";
    private static final String DISPLAY_TOKEN = "%display%";

    private final LiteBrawlPlugin plugin;

    public ChatListener(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.settings().chatEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        Profile profile = plugin.profiles().getIfLoaded(player.getUniqueId());
        int level = profile == null ? 1 : plugin.profiles().levelOf(profile.getExperience());

        String format = plugin.settings().chatFormat();
        String levelText = Msg.replace(plugin.settings().levelFormat(), "level", String.valueOf(level));
        String effect = plugin.cosmetics().chatPrefix(player);
        String teamTag = plugin.teams().teamNameOf(player.getUniqueId());
        Component display = plugin.display().displayName(player);

        // 先换成占位标记，保证整行标签成对
        String template = format
                .replace(MESSAGE_TOKEN, MESSAGE_MARKER)
                .replace(DISPLAY_TOKEN, DISPLAY_MARKER);

        Component rendered = Msg.mini(Msg.replace(template,
                "level", levelText,
                "player", player.getName(),
                "effect", effect,
                "team", teamTag.isEmpty() ? "" : "<yellow>[" + teamTag + "]</yellow> "));

        Component line = rendered
                .replaceText(builder -> builder.matchLiteral(DISPLAY_MARKER).replacement(display))
                .replaceText(builder -> builder.matchLiteral(MESSAGE_MARKER).replacement(event.message()));

        event.renderer((source, sourceDisplayName, message, viewer) -> line);
    }
}
