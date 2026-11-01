package com.litebrawl.map;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** 地图注册表 + maps.yml 持久化。 */
public final class MapService {

    private final LiteBrawlPlugin plugin;
    private final File file;
    private final Map<String, LiteMap> maps = new LinkedHashMap<>();
    private final EmeraldScanner scanner;

    private String activeId;

    public MapService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "maps.yml");
        this.scanner = new EmeraldScanner(plugin);
    }

    public void load() {
        maps.clear();
        activeId = null;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        activeId = yml.getString("active", null);
        ConfigurationSection root = yml.getConfigurationSection("maps");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String id = section.getString("id", key);
            LiteMap map = new LiteMap(id, section.getString("display-name", id),
                    section.getString("world", id));

            String spawn = section.getString("spawn");
            if (spawn != null && !spawn.isBlank()) {
                String[] parts = spawn.split(",");
                if (parts.length >= 5) {
                    try {
                        map.setSpawnRaw(Double.parseDouble(parts[0].trim()),
                                Double.parseDouble(parts[1].trim()),
                                Double.parseDouble(parts[2].trim()),
                                Float.parseFloat(parts[3].trim()),
                                Float.parseFloat(parts[4].trim()));
                    } catch (NumberFormatException ex) {
                        plugin.getLogger().warning("地图 " + id + " 的出生点格式错误: " + spawn);
                    }
                }
            }

            List<int[]> points = new ArrayList<>();
            for (String raw : section.getStringList("spawn-points")) {
                String[] parts = raw.split(",");
                if (parts.length != 3) {
                    continue;
                }
                try {
                    points.add(new int[]{Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim())});
                } catch (NumberFormatException ignored) {
                    // 忽略损坏的坐标行
                }
            }
            map.setSpawnPoints(points);
            maps.put(id, map);
        }

        if (activeId != null && !maps.containsKey(activeId)) {
            activeId = maps.isEmpty() ? null : maps.keySet().iterator().next();
        }
        if (activeId == null && !maps.isEmpty()) {
            activeId = maps.keySet().iterator().next();
        }
        plugin.getLogger().info("已加载 " + maps.size() + " 张地图，当前地图: " + (activeId == null ? "无" : activeId));
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("active", activeId);
        for (LiteMap map : maps.values()) {
            String base = "maps." + map.id().replace('.', '_') + ".";
            yml.set(base + "id", map.id());
            yml.set(base + "display-name", map.displayName());
            yml.set(base + "world", map.worldName());
            if (map.hasSpawn()) {
                yml.set(base + "spawn", map.spawnX() + "," + map.spawnY() + "," + map.spawnZ()
                        + "," + map.spawnYaw() + "," + map.spawnPitch());
            }
            List<String> points = new ArrayList<>(map.spawnPoints().size());
            for (int[] p : map.spawnPoints()) {
                points.add(p[0] + "," + p[1] + "," + p[2]);
            }
            yml.set(base + "spawn-points", points);
        }
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                plugin.getLogger().warning("无法创建插件数据目录");
                return;
            }
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 maps.yml 失败: " + ex.getMessage());
        }
    }

    public Collection<LiteMap> all() {
        return maps.values();
    }

    public Map<String, LiteMap> byIdView() {
        return maps;
    }

    public Optional<LiteMap> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return maps.values().stream()
                .filter(m -> m.id().equalsIgnoreCase(id))
                .findFirst();
    }

    public Optional<LiteMap> active() {
        if (activeId == null) {
            return Optional.empty();
        }
        return byId(activeId);
    }

    public String activeId() {
        return activeId;
    }

    public boolean setActive(String id) {
        if (byId(id).isEmpty()) {
            return false;
        }
        this.activeId = byId(id).get().id();
        save();
        return true;
    }

    /**
     * 创建地图。
     *
     * @return null 表示成功，否则为给管理员的错误说明
     */
    public String create(String dir, String displayName) {
        if (dir == null || dir.isBlank()) {
            return "目录名不能为空";
        }
        File folder = new File(Bukkit.getWorldContainer(), dir);
        if (!folder.isDirectory()) {
            return "服务器目录下没有这个世界文件夹: " + dir;
        }
        if (byId(dir).isPresent()) {
            return "该世界已经创建过地图了: " + dir;
        }

        World world = Bukkit.getWorld(dir);
        if (world == null) {
            try {
                world = Bukkit.createWorld(WorldCreator.name(dir));
            } catch (RuntimeException ex) {
                return "加载世界失败: " + ex.getMessage();
            }
        }
        if (world == null) {
            return "加载世界失败，可能不是有效的 Bukkit 世界: " + dir;
        }

        LiteMap map = new LiteMap(dir, displayName == null || displayName.isBlank() ? dir : displayName,
                world.getName());
        maps.put(map.id(), map);
        if (activeId == null) {
            activeId = map.id();
        }
        save();
        return null;
    }

    public String delete(String id) {
        LiteMap map = byId(id).orElse(null);
        if (map == null) {
            return "找不到地图: " + id;
        }
        maps.remove(map.id());
        if (map.id().equals(activeId)) {
            activeId = maps.isEmpty() ? null : maps.keySet().iterator().next();
        }
        save();
        return null;
    }

    public String setActiveChecked(String id) {
        if (byId(id).isEmpty()) {
            return "找不到地图: " + id;
        }
        setActive(id);
        return null;
    }

    public void setSpawn(Location location) {
        LiteMap map = active().orElse(null);
        if (map == null || location == null) {
            return;
        }
        map.setSpawn(location);
        save();
    }

    public Location activeSpawn() {
        return active().map(LiteMap::spawn).orElse(null);
    }

    /** 随机出生点：优先绿宝石块，未扫描时退回地图出生点。 */
    public Location randomSpawn() {
        LiteMap map = active().orElse(null);
        if (map == null) {
            return null;
        }
        World world = map.world();
        if (world == null) {
            return null;
        }
        List<int[]> points = map.spawnPoints();
        if (points.isEmpty()) {
            return map.spawn();
        }
        int[] point = points.get(ThreadLocalRandom.current().nextInt(points.size()));
        Location fallback = map.spawn();
        float yaw = fallback == null ? 0.0F : fallback.getYaw();
        return new Location(world, point[0] + 0.5D,
                point[1] + plugin.settings().spawnYOffset(), point[2] + 0.5D, yaw, 0.0F);
    }

    public String selectedWorldHint() {
        return active().map(LiteMap::worldName).orElse("(无)");
    }

    public boolean isScanning() {
        return scanner.isRunning();
    }

    public void resetScanner() {
        scanner.reset();
    }

    public void scanActive(EmeraldScanner.Callback callback) {
        scanActive(0, callback);
    }

    public void scanActive(int radiusOverride, EmeraldScanner.Callback callback) {
        LiteMap map = active().orElse(null);
        if (map == null) {
            callback.failed("没有激活的地图");
            return;
        }
        scanner.scan(map, radiusOverride, callback);
    }

    public String describeActive() {
        LiteMap map = active().orElse(null);
        if (map == null) {
            return "<gray>当前没有激活地图</gray>";
        }
        return "<gray>地图: <white>" + map.displayName() + "</white> <dark_gray>(" + map.id() + ")</dark_gray>"
                + "<newline><gray>世界: <white>" + map.worldName() + "</white>"
                + "<newline><gray>出生点: <white>" + (map.hasSpawn() ? "已设置" : "未设置") + "</white>"
                + "<newline><gray>出生方块: <white>" + map.spawnPoints().size() + "</white> 个";
    }
}
