package com.litebrawl.board;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.data.Period;
import com.litebrawl.data.Profile;
import com.litebrawl.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全息排行榜（可点击）。
 *
 * <p>每个「榜单家族」（击杀 / 死亡 / 等级 / 硬币）只有一块板：上面是 TextDisplay 显示文字，
 * 同位置放一个隐形盔甲架当命中判定——右键切下一个时间维度，左键切上一个。
 * 击杀/死亡家族会在 总榜→日→周→月→年 之间循环。</p>
 */
public final class BoardService implements Listener {

    private static final String DISPLAY_TAG = "litebrawl:board:";
    private static final String CLICK_TAG = "litebrawl:panel:";

    private final LiteBrawlPlugin plugin;
    private final File file;
    private final Map<String, Panel> panels = new LinkedHashMap<>();

    private BukkitTask task;

    /** 一块榜单面板。 */
    private static final class Panel {
        private BoardType type;
        private Location location;

        private Panel(BoardType type, Location location) {
            this.type = type;
            this.location = location;
        }
    }

    public BoardService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boards.yml");
    }

    // ---------------- 持久化 ----------------

    public void load() {
        panels.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("boards");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            // 新格式：boards.<family>.{type,location}；老格式：boards.<type>: "world,x,y,z,..."
            String rawLocation;
            BoardType type;
            if (root.isConfigurationSection(key)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                type = BoardType.byId(section.getString("type", key));
                rawLocation = section.getString("location");
            } else {
                type = BoardType.byId(key);
                rawLocation = root.getString(key);
            }
            if (type == null) {
                plugin.getLogger().warning("boards.yml 中存在未知榜单: " + key);
                continue;
            }
            Location location = parse(rawLocation);
            if (location == null) {
                continue;
            }
            panels.put(type.family(), new Panel(type, location));
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Panel> entry : panels.entrySet()) {
            String base = "boards." + entry.getKey() + ".";
            yml.set(base + "type", entry.getValue().type.id());
            yml.set(base + "location", serialize(entry.getValue().location));
        }
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                return;
            }
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 boards.yml 失败: " + ex.getMessage());
        }
    }

    private static String serialize(Location location) {
        return location.getWorld().getName() + "," + location.getX() + "," + location.getY()
                + "," + location.getZ() + "," + location.getYaw() + "," + location.getPitch();
    }

    private static Location parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0.0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0.0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void reload() {
        removeAllEntities();
        load();
        refreshAll();
    }

    // ---------------- 生命周期 ----------------

    public void start() {
        refreshAll();
        long ticks = plugin.settings().boardUpdateSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, ticks, ticks);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAllEntities();
    }

    // ---------------- 增删改 ----------------

    public boolean isPlaced(BoardType type) {
        return type != null && panels.containsKey(type.family());
    }

    /** 已放置的榜单（返回各自当前显示的类型）。 */
    public List<BoardType> placed() {
        List<BoardType> result = new ArrayList<>();
        for (Panel panel : panels.values()) {
            result.add(panel.type);
        }
        return result;
    }

    /** 在玩家位置放置/移动榜单。 */
    public void place(BoardType type, Player player) {
        String family = type.family();
        Location location = player.getLocation().clone();
        Panel previous = panels.get(family);
        if (previous != null) {
            removeEntities(family, previous.location);
        }
        panels.put(family, new Panel(type, location));
        save();
        refresh(family);
    }

    public boolean remove(BoardType type) {
        if (type == null) {
            return false;
        }
        Panel panel = panels.remove(type.family());
        if (panel == null) {
            return false;
        }
        removeEntities(type.family(), panel.location);
        save();
        return true;
    }

    /** 点一下切换时间维度。 */
    private void cycle(String family, int direction) {
        Panel panel = panels.get(family);
        if (panel == null) {
            return;
        }
        List<BoardType> cycle = panel.type.cycle();
        if (cycle.size() <= 1) {
            return;
        }
        int index = cycle.indexOf(panel.type);
        int next = Math.floorMod(index + direction, cycle.size());
        panel.type = cycle.get(next);
        save();
        refresh(family);
    }

    // ---------------- 刷新 ----------------

    public void refreshAll() {
        for (String family : panels.keySet()) {
            refresh(family);
        }
    }

    private void refresh(String family) {
        Panel panel = panels.get(family);
        if (panel == null || panel.location.getWorld() == null) {
            return;
        }
        Component text = buildText(panel.type);

        TextDisplay display = findEntity(panel.location, DISPLAY_TAG + family, TextDisplay.class);
        if (display == null) {
            display = panel.location.getWorld().spawn(panel.location, TextDisplay.class);
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setSilent(true);
            display.addScoreboardTag(DISPLAY_TAG + family);
        }
        display.text(text);

        // 隐形盔甲架做点击命中（TextDisplay 本身收不到点击）
        ArmorStand stand = findEntity(panel.location, CLICK_TAG + family, ArmorStand.class);
        if (stand == null) {
            stand = panel.location.getWorld().spawn(panel.location, ArmorStand.class);
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setMarker(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setGravity(false);
            // 不能设 invulnerable：那样左键不会触发伤害事件，就没法用左键切换了
            stand.setInvulnerable(false);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setPersistent(true);
            stand.getEquipment().setHelmet(new ItemStack(Material.AIR));
            stand.addScoreboardTag(CLICK_TAG + family);
        }
    }

    private <T extends Entity> T findEntity(Location location, String tag, Class<T> type) {
        for (Entity entity : location.getWorld().getNearbyEntities(location, 1.5D, 1.5D, 1.5D)) {
            if (type.isInstance(entity) && entity.getScoreboardTags().contains(tag)) {
                return type.cast(entity);
            }
        }
        return null;
    }

    private Component buildText(BoardType type) {
        String label = label(type);
        List<Profile> top = plugin.profiles().topPeriod(type.period(), type.column(),
                plugin.settings().boardMaxLines());

        Component result = Msg.mini(Msg.replace(plugin.settings().boardTitle(), "board", label));
        if (top.isEmpty()) {
            return result.append(Component.newline()).append(Msg.mini(plugin.settings().boardEmpty()));
        }
        int rank = 1;
        for (Profile profile : top) {
            String value = switch (type.column()) {
                case "experience" -> String.valueOf(plugin.profiles().levelOf(profile.getExperience()));
                case "coins" -> String.valueOf(profile.getCoins());
                case "deaths" -> String.valueOf(profile.getDeaths());
                default -> String.valueOf(profile.getKills());
            };
            String line = Msg.replace(plugin.settings().boardLine(),
                    "rank", String.valueOf(rank++),
                    "player", profile.getName(),
                    "value", value);
            result = result.append(Component.newline()).append(Msg.mini(line));
        }
        if (type.cycle().size() > 1) {
            result = result.append(Component.newline())
                    .append(Msg.mini("<dark_gray>左键 / 右键切换时间维度</dark_gray>"));
        }
        return result;
    }

    private String label(BoardType type) {
        ConfigurationSection section = plugin.settings().boardTypesSection();
        if (section != null) {
            String configured = section.getString(type.id());
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
        }
        Period period = type.period();
        return period == Period.ALL ? type.defaultLabel()
                : type.defaultLabel() + " · " + period.label();
    }

    private void removeEntities(String family, Location location) {
        if (location.getWorld() == null) {
            return;
        }
        String displayTag = DISPLAY_TAG + family;
        String clickTag = CLICK_TAG + family;
        for (Entity entity : location.getWorld().getNearbyEntities(location, 3.0D, 3.0D, 3.0D)) {
            if (entity.getScoreboardTags().contains(displayTag) || entity.getScoreboardTags().contains(clickTag)) {
                entity.remove();
            }
        }
    }

    private void removeAllEntities() {
        for (Map.Entry<String, Panel> entry : panels.entrySet()) {
            removeEntities(entry.getKey(), entry.getValue().location);
        }
    }

    // ---------------- 点击 ----------------

    /** 右键：下一个时间维度。 */
    @EventHandler
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        String family = familyOf(event.getRightClicked());
        if (family == null) {
            return;
        }
        event.setCancelled(true);
        cycle(family, 1);
    }

    /** 左键：上一个时间维度。 */
    @EventHandler
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        String family = familyOf(event.getEntity());
        if (family == null) {
            return;
        }
        event.setCancelled(true);
        cycle(family, -1);
    }

    /** 别的伤害来源（火、爆炸、箭）也别把点击用的盔甲架打掉。 */
    @EventHandler
    public void onAnyDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (familyOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private String familyOf(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(CLICK_TAG)) {
                return tag.substring(CLICK_TAG.length());
            }
        }
        return null;
    }
}
