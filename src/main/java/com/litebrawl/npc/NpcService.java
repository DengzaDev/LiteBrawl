package com.litebrawl.npc;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 功能 NPC：站在地图里的盔甲架，头上顶着玩家皮肤，右键打开对应界面。
 *
 * <p>/lb npc set shop|cosmetic 在脚下创建，/lb npc skin <玩家> 换皮肤，
 * /lb npc remove 删掉最近的，全部持久化在 npcs.yml。</p>
 */
public final class NpcService implements Listener {

    private static final String TAG_PREFIX = "litebrawl:npc:";

    public enum NpcType {
        SHOP("shop", "物品商店"),
        COSMETIC("cosmetic", "外观商城"),
        BOARD("board", "排行榜");

        public String defaultName() {
            return label;
        }

        private final String id;
        private final String label;

        NpcType(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public static NpcType byId(String raw) {
            if (raw == null) {
                return null;
            }
            for (NpcType type : values()) {
                if (type.id.equalsIgnoreCase(raw.trim())) {
                    return type;
                }
            }
            return null;
        }
    }

    private static final class Npc {
        private final String id;
        private NpcType type;
        private Location location;
        private String skin;
        private String name;

        private Npc(String id, NpcType type, Location location, String skin, String name) {
            this.id = id;
            this.type = type;
            this.location = location;
            this.skin = skin;
            this.name = name;
        }
    }

    private final LiteBrawlPlugin plugin;
    private final File file;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();

    public NpcService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    // ---------------- 持久化 ----------------

    public void load() {
        npcs.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("npcs");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            NpcType type = NpcType.byId(section.getString("type"));
            World world = Bukkit.getWorld(section.getString("world", ""));
            if (type == null || world == null) {
                continue;
            }
            Location location = new Location(world,
                    section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw"), 0.0F);
            npcs.put(id, new Npc(id, type, location,
                    section.getString("skin", ""), section.getString("name", "")));
        }
        spawnAll();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Npc npc : npcs.values()) {
            String base = "npcs." + npc.id + ".";
            yml.set(base + "type", npc.type.id());
            yml.set(base + "world", npc.location.getWorld() == null ? "" : npc.location.getWorld().getName());
            yml.set(base + "x", npc.location.getX());
            yml.set(base + "y", npc.location.getY());
            yml.set(base + "z", npc.location.getZ());
            yml.set(base + "yaw", npc.location.getYaw());
            yml.set(base + "skin", npc.skin);
            yml.set(base + "name", npc.name);
        }
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                return;
            }
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 npcs.yml 失败: " + ex.getMessage());
        }
    }

    // ---------------- 创建 / 删除 ----------------

    public String create(NpcType type, Location location, Player creator) {
        String id = type.id() + "_" + (npcs.size() + 1);
        String skin = creator == null ? "" : creator.getName();
        Npc npc = new Npc(id, type, location.clone(), skin, type.label());
        npcs.put(id, npc);
        save();
        spawn(npc);
        return id;
    }

    public int removeNearest(Location location, double radius) {
        Npc best = null;
        double bestDistance = radius * radius;
        for (Npc npc : npcs.values()) {
            if (npc.location.getWorld() == null || location.getWorld() == null
                    || !npc.location.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = npc.location.distanceSquared(location);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = npc;
            }
        }
        if (best == null) {
            return 0;
        }
        npcs.remove(best.id);
        save();
        despawn(best.id);
        return 1;
    }

    public boolean setSkinNearest(Location location, String skin, double radius) {
        Npc best = null;
        double bestDistance = radius * radius;
        for (Npc npc : npcs.values()) {
            if (npc.location.getWorld() == null || location.getWorld() == null
                    || !npc.location.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = npc.location.distanceSquared(location);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = npc;
            }
        }
        if (best == null) {
            return false;
        }
        best.skin = skin;
        save();
        despawn(best.id);
        spawn(best);
        return true;
    }

    /** 把最近的 NPC 挪到当前位置。 */
    public boolean moveNearest(Location location, double radius) {
        Npc best = nearest(location, radius);
        if (best == null) {
            return false;
        }
        despawn(best.id);
        best.location = location.clone();
        save();
        spawn(best);
        return true;
    }

    /** 改最近 NPC 的显示名。 */
    public boolean renameNearest(Location location, String name, double radius) {
        Npc best = nearest(location, radius);
        if (best == null) {
            return false;
        }
        best.name = name;
        save();
        despawn(best.id);
        spawn(best);
        return true;
    }

    private Npc nearest(Location location, double radius) {
        Npc best = null;
        double bestDistance = radius * radius;
        for (Npc npc : npcs.values()) {
            if (npc.location.getWorld() == null || location.getWorld() == null
                    || !npc.location.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = npc.location.distanceSquared(location);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = npc;
            }
        }
        return best;
    }

    public List<String> list() {
        List<String> result = new ArrayList<>();
        for (Npc npc : npcs.values()) {
            result.add(npc.id + " " + npc.type.id() + " @ "
                    + (npc.location.getWorld() == null ? "?" : npc.location.getWorld().getName())
                    + " " + Math.round(npc.location.getX()) + "," + Math.round(npc.location.getY())
                    + "," + Math.round(npc.location.getZ())
                    + (npc.skin == null || npc.skin.isBlank() ? "" : " skin=" + npc.skin));
        }
        return result;
    }

    // ---------------- 实体 ----------------

    private org.bukkit.scheduler.BukkitTask guard;

    /**
     * 定时检查 NPC 是否还在。
     *
     * <p>盔甲架是非持久实体，区块卸载时会被服务端直接抹掉（不会触发任何事件），
     * 所以每 10 秒核对一次，缺了就原地补回来。</p>
     */
    public void start() {
        guard = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Npc npc : npcs.values()) {
                if (!exists(npc)) {
                    spawn(npc);
                }
            }
        }, 200L, 200L);
    }

    public void stop() {
        if (guard != null) {
            guard.cancel();
            guard = null;
        }
    }

    private boolean exists(Npc npc) {
        if (npc.location.getWorld() == null) {
            return true;
        }
        String tag = TAG_PREFIX + npc.id;
        for (Entity entity : npc.location.getWorld().getNearbyEntities(npc.location, 3, 3, 3)) {
            if (entity instanceof ArmorStand stand && stand.isValid()
                    && stand.getScoreboardTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    public void spawnAll() {
        for (Npc npc : npcs.values()) {
            spawn(npc);
        }
    }

    public void removeAll() {
        for (String id : new ArrayList<>(npcs.keySet())) {
            despawn(id);
        }
    }

    private void spawn(Npc npc) {
        World world = npc.location.getWorld();
        if (world == null) {
            return;
        }
        despawn(npc.id);
        ArmorStand stand = world.spawn(npc.location, ArmorStand.class);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setPersistent(false);
        stand.customName(Msg.mini("<gold>" + npc.name + "</gold>"));
        stand.setCustomNameVisible(true);
        stand.addScoreboardTag(TAG_PREFIX + npc.id);
        stand.getEquipment().setHelmet(head(npc.skin));
        stand.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
    }

    private ItemStack head(String skin) {
        if (skin == null || skin.isBlank()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        // 不联网拉皮肤：只用在线的、已有 profile 的玩家
        return com.litebrawl.util.Heads.ofName(skin);
    }

    private void despawn(String id) {
        Npc npc = npcs.get(id);
        if (npc == null || npc.location.getWorld() == null) {
            return;
        }
        String tag = TAG_PREFIX + id;
        for (Entity entity : npc.location.getWorld().getNearbyEntities(npc.location, 3, 3, 3)) {
            if (entity instanceof ArmorStand stand && stand.getScoreboardTags().contains(tag)) {
                stand.remove();
            }
        }
    }

    // ---------------- 点击 ----------------

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        String tag = entity.getScoreboardTags().stream()
                .filter(t -> t.startsWith(TAG_PREFIX))
                .findFirst().orElse(null);
        if (tag == null) {
            return;
        }
        event.setCancelled(true);
        Npc npc = npcs.get(tag.substring(TAG_PREFIX.length()));
        if (npc == null) {
            return;
        }
        Player player = event.getPlayer();
        switch (npc.type) {
            case SHOP -> plugin.shop().open(player);
            case COSMETIC -> plugin.cosmeticGui().open(player);
            case BOARD -> plugin.boardPanel().open(player);
        }
    }

    static Component empty() {
        return Component.empty();
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
