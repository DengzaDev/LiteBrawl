package com.litebrawl.proxy;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 跨服跳转。
 *
 * <p>用 BungeeCord 插件消息通道（{@code bungee:BungeeCord} 的 {@code Connect} 子通道）。
 * Velocity 侧需要在 {@code velocity.toml} 打开：</p>
 *
 * <pre>
 * [advanced]
 * bungee-plugin-message-channel = true
 * </pre>
 *
 * <p>玩家信息怎么转发（modern / legacy）是服务端配置的事，和这里的跳转无关。</p>
 */
public final class ProxyService {

    private final LiteBrawlPlugin plugin;

    public ProxyService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return "BUNGEE".equals(plugin.settings().proxyMode());
    }

    public List<String> servers() {
        return plugin.settings().proxyServers();
    }

    public String defaultServer() {
        return plugin.settings().proxyDefaultServer();
    }

    /** @return null 表示已发送，否则是失败原因 */
    public String connect(Player player, String server) {
        if (!enabled()) {
            return "跨服没开启（config.yml 的 proxy.mode 设为 BUNGEE）";
        }
        if (server == null || server.isBlank()) {
            return "服务器名不能为空";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "bungee:BungeeCord", buffer.toByteArray());
            Msg.info(player, plugin.settings().prefix(),
                    "<gray>正在前往</gray> <white>%server%</white><gray>…</gray>",
                    "server", server.toLowerCase(Locale.ROOT));
            return null;
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().warning("发送跨服消息失败: " + ex.getMessage());
            return "发送失败：" + ex.getMessage();
        }
    }
}
