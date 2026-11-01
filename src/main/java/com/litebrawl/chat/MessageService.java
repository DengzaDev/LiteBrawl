package com.litebrawl.chat;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.Reader;

/**
 * 播报文案（messages.yml）。
 *
 * <p>击杀 / 死亡 / 连杀 / 武器名的文案单独放一个文件，改完 /litebrawl reload 就生效，
 * 不需要碰主配置。</p>
 */
public final class MessageService {

    private final LiteBrawlPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public MessageService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        mergeDefaults();
    }

    /** 版本升级时补上新增的默认键，不覆盖用户已有的值。 */
    private void mergeDefaults() {
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in == null) {
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("读取 messages.yml 默认值失败: " + ex.getMessage());
        }
    }

    public String killMessage() {
        return config.getString("kill.default", "<white>%killer%</white> <gray>击杀</gray> <white>%victim%</white>");
    }

    public String deathMessage() {
        return config.getString("death.default", "<gray>%victim% 阵亡了</gray>");
    }

    public boolean streakEnabled() {
        return config.getBoolean("streak.enabled", true);
    }

    public int streakEvery() {
        return Math.max(1, config.getInt("streak.every", 5));
    }

    public String streakMessage() {
        return config.getString("streak.message",
                "<gold>%player%</gold> <gray>已经连杀</gray> <red>%streak%</red> <gray>人！</gray>");
    }

    public String weaponName(Material material) {
        if (material == null) {
            return config.getString("weapon.other", "空手");
        }
        String name = material.name();
        if (name.endsWith("_SWORD") || name.equals("TRIDENT") || name.endsWith("_AXE")) {
            return config.getString("weapon.melee", "近战");
        }
        if (name.equals("BOW") || name.equals("CROSSBOW")) {
            return config.getString("weapon.bow", "弓箭");
        }
        if (name.endsWith("POTION") || name.endsWith("SPLASH_POTION")) {
            return config.getString("weapon.potion", "药水");
        }
        return config.getString("weapon.other", "空手");
    }

    public File file() {
        return file;
    }
}
