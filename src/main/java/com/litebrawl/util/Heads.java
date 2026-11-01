package com.litebrawl.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

/**
 * 玩家头颅工具。
 *
 * <p><b>不要用 {@code SkullMeta#setOwningPlayer}，也尽量别在离线服碰 PlayerProfile。</b>
 * 这两条路径都会让 Paper 去 {@code sessionserver.mojang.com} 补齐皮肤属性：
 * 离线模式或没外网时，每次调用都要等 20 秒超时，然后刷一整段
 * {@code Couldn't look up profile properties} 堆栈。</p>
 *
 * <p>现在的策略：</p>
 * <ul>
 *   <li>离线服（online-mode=false）直接返回默认头颅——这里本来就没有皮肤数据</li>
 *   <li>在线服只在 profile **已经完整**（登录时已拿到纹理）时复用，不触发任何补齐</li>
 * </ul>
 */
public final class Heads {

    private Heads() {
    }

    public static ItemStack of(Player player) {
        ItemStack plain = new ItemStack(Material.PLAYER_HEAD);
        if (player == null || !Bukkit.getOnlineMode()) {
            return plain;
        }
        try {
            PlayerProfile profile = player.getPlayerProfile();
            if (profile == null || !profile.isComplete()) {
                // 不完整就说明还没纹理，任何进一步操作都会触发联网补齐
                return plain;
            }
            PlayerTextures textures = profile.getTextures();
            if (textures == null || textures.getSkin() == null) {
                return plain;
            }
            var copy = Bukkit.createProfile(profile.getUniqueId(), profile.getName());
            copy.setTextures(textures);
            ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
            if (stack.getItemMeta() instanceof SkullMeta meta) {
                meta.setPlayerProfile(copy);
                stack.setItemMeta(meta);
            }
            return stack;
        } catch (RuntimeException ex) {
            return plain;
        }
    }

    /** 按名字取头颅；离线服或玩家不在线时给默认头颅。 */
    public static ItemStack ofName(String name) {
        if (name == null || name.isBlank() || !Bukkit.getOnlineMode()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        Player online = Bukkit.getPlayerExact(name);
        return online == null ? new ItemStack(Material.PLAYER_HEAD) : of(online);
    }
}
