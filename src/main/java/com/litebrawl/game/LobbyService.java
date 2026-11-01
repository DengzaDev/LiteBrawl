package com.litebrawl.game;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 返回大厅。
 *
 * <p>配置了 lobby-items.return.server 时，通过 BungeeCord 插件消息把玩家送到大厅服
 * （Velocity 需要在 velocity.toml 打开 bungee-plugin-message-channel）；
 * 没配就直接回到当前地图出生点。</p>
 */
public final class LobbyService {

    private final LiteBrawlPlugin plugin;

    public LobbyService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(Player player) {
        String server = plugin.settings().lobbyServer();
        if (server == null || server.isBlank()) {
            plugin.game().prepareLobby(player);
            Msg.info(player, plugin.settings().prefix(), "<gray>已回到大厅出生点。</gray>");
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "bungee:BungeeCord", bytes.toByteArray());
            Msg.info(player, plugin.settings().prefix(),
                    "<gray>正在前往大厅服务器</gray> <white>%server%</white><gray>…</gray>",
                    "server", server);
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().warning("发送跨服消息失败: " + ex.getMessage());
            plugin.game().prepareLobby(player);
            Msg.info(player, plugin.settings().prefix(),
                    "<red>跨服发送失败，已回到本地出生点。</red>");
        }
    }
}
