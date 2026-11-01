package com.litebrawl.cosmetic;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.data.Profile;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 外观系统：击杀播报 / 死亡播报 / 击杀特效 / 聊天特效。
 *
 * <p>定义在 cosmetics.yml，用硬币在商店购买（商品里配 special: cosmetic），
 * 买完自动装备；也可以 /cosmetic 手动切换。拥有的列表存进玩家数据。</p>
 */
public final class CosmeticService {

    private final LiteBrawlPlugin plugin;
    private final Map<String, Cosmetic> byId = new LinkedHashMap<>();

    public CosmeticService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        byId.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("cosmetics.items");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            CosmeticType type = CosmeticType.byName(section.getString("type"));
            if (type == null) {
                plugin.getLogger().warning("cosmetics.items." + id + " 的 type 无效: "
                        + section.getString("type"));
                continue;
            }
            Particle particle = null;
            String rawParticle = section.getString("particle");
            if (rawParticle != null && !rawParticle.isBlank()) {
                try {
                    particle = Particle.valueOf(rawParticle.trim().toUpperCase(Locale.ROOT));
                    if (particle.getDataType() != Void.class) {
                        plugin.getLogger().warning("cosmetics.items." + id
                                + " 的粒子需要额外数据，不能用于简单特效，已忽略: " + rawParticle);
                        particle = null;
                    }
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("cosmetics.items." + id + " 的粒子无效: " + rawParticle);
                }
            }
            String payload = type == CosmeticType.KILL_EFFECT
                    ? section.getString("prefix", "")
                    : section.getString("message", section.getString("prefix", ""));
            NamedTextColor color = null;
            String rawColor = section.getString("color");
            if (rawColor != null && !rawColor.isBlank()) {
                color = NamedTextColor.NAMES.value(rawColor.trim().toLowerCase(Locale.ROOT));
                if (color == null) {
                    plugin.getLogger().warning("cosmetics.items." + id + " 的 color 无效: " + rawColor);
                }
            }
            byId.put(id.toLowerCase(Locale.ROOT), new Cosmetic(id, type,
                    section.getString("name", id),
                    section.getLong("price", 0L),
                    payload,
                    particle,
                    section.getInt("count", 30),
                    color));
        }
        plugin.getLogger().info("已加载 " + byId.size() + " 个外观");
    }

    public Cosmetic get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Cosmetic> all() {
        return new ArrayList<>(byId.values());
    }

    // ---------------- 查询玩家已装备 ----------------

    public Cosmetic equipped(Player player, CosmeticType type) {
        Profile profile = plugin.profiles().getIfLoaded(player.getUniqueId());
        if (profile == null) {
            return null;
        }
        String id = profile.equippedCosmetic(type.name());
        Cosmetic cosmetic = get(id);
        return cosmetic != null && cosmetic.type() == type ? cosmetic : null;
    }

    /** 击杀播报模板；没买就返回 null 走默认文案。 */
    public String killMessage(Player killer) {
        Cosmetic cosmetic = equipped(killer, CosmeticType.KILL_MESSAGE);
        return cosmetic == null ? null : cosmetic.payload();
    }

    public String deathMessage(Player victim) {
        Cosmetic cosmetic = equipped(victim, CosmeticType.DEATH_MESSAGE);
        return cosmetic == null ? null : cosmetic.payload();
    }

    /** 聊天里的额外前缀（例如金色名字）。 */
    public String chatPrefix(Player player) {
        Cosmetic cosmetic = equipped(player, CosmeticType.CHAT_EFFECT);
        return cosmetic == null ? "" : cosmetic.payload();
    }

    /** 击杀特效：在死者位置放粒子 + 音效。 */
    public void playKillEffect(Player killer, Location where) {
        Cosmetic cosmetic = equipped(killer, CosmeticType.KILL_EFFECT);
        if (cosmetic == null || cosmetic.particle() == null || where.getWorld() == null) {
            return;
        }
        where.getWorld().spawnParticle(cosmetic.particle(), where.clone().add(0, 1, 0),
                cosmetic.count(), 0.4D, 0.6D, 0.4D, 0.02D);
        where.getWorld().playSound(where, Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.6F);
    }

    // ---------------- 购买与装备 ----------------

    public boolean owns(Player player, Cosmetic cosmetic) {
        Profile profile = plugin.profiles().get(player);
        return profile.ownsCosmetic(cosmetic.id());
    }

    public void unlockAndEquip(Player player, Cosmetic cosmetic) {
        Profile profile = plugin.profiles().get(player);
        profile.addCosmetic(cosmetic.id());
        profile.equipCosmetic(cosmetic.type().name(), cosmetic.id());
        plugin.profiles().saveAsync(profile);
    }

    /** @return null 表示成功 */
    public String equip(Player player, String cosmeticId) {
        Cosmetic cosmetic = get(cosmeticId);
        if (cosmetic == null) {
            return "找不到外观: " + cosmeticId;
        }
        Profile profile = plugin.profiles().get(player);
        if (!profile.ownsCosmetic(cosmetic.id())) {
            return "你还没有购买这个外观";
        }
        profile.equipCosmetic(cosmetic.type().name(), cosmetic.id());
        plugin.profiles().saveAsync(profile);
        return null;
    }

    /** @return null 表示成功 */
    public String unequip(Player player, CosmeticType type) {
        Profile profile = plugin.profiles().get(player);
        profile.equipCosmetic(type.name(), null);
        plugin.profiles().saveAsync(profile);
        return null;
    }

    public List<Cosmetic> ownedOf(Player player) {
        Profile profile = plugin.profiles().get(player);
        List<Cosmetic> result = new ArrayList<>();
        for (String id : profile.ownedCosmetics()) {
            Cosmetic cosmetic = get(id);
            if (cosmetic != null) {
                result.add(cosmetic);
            }
        }
        return result;
    }
}
