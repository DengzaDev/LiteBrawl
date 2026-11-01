package com.litebrawl.bot;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.data.Profile;
import com.litebrawl.map.LiteMap;
import com.litebrawl.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 假人（陪练）。
 *
 * <p>用带玩家皮肤头的僵尸冒充玩家：装备与真人一致、会在地图里走动。
 * 「大师」会主动攻击玩家、攻击力由配置控制；「新手」不加目标也打不出伤害。
 * 玩家少于 N 个时自动上假人，超过就撤掉。</p>
 */
public final class BotService implements Listener {

    private final LiteBrawlPlugin plugin;
    private final Map<UUID, Boolean> bots = new HashMap<>();
    private volatile boolean spawning;
    /** 正在等待复活的假人数量（避免自动补员和复活重复生成） */
    private int pendingRespawns;
    private int botCounter;
    private BukkitTask task;

    public BotService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    /** 和平难度会把僵尸立刻清除，假人会「凭空消失」，这里提前提醒服主。 */
    private void warnIfPeaceful() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            if (world.getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
                plugin.getLogger().warning("世界 " + world.getName()
                        + " 的难度是和平（peaceful），僵尸假人会被立即清除；"
                        + "请把 difficulty 改成 easy 或更高，假人才会出现。");
                return;
            }
        }
    }

    public boolean isSpawning() {
        return spawning;
    }

    public int count() {
        return bots.size();
    }

    /** 当前存活的假人实体（给即死方块这类规则用）。 */
    public java.util.List<org.bukkit.entity.Entity> entities() {
        java.util.List<org.bukkit.entity.Entity> list = new ArrayList<>();
        for (UUID id : bots.keySet()) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                list.add(entity);
            }
        }
        return list;
    }

    public void start() {
        if (!plugin.settings().botsEnabled()) {
            return;
        }
        warnIfPeaceful();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAll();
    }

    public void restart() {
        stop();
        start();
    }

    private void tick() {
        if (!plugin.settings().botsEnabled()) {
            return;
        }
        reconcile();
        if (plugin.settings().botsAuto()) {
            int online = Bukkit.getOnlinePlayers().size();
            int threshold = plugin.settings().botsMinPlayers();
            int wanted = plugin.settings().botsCount();
            // 一个人都没有时不需要假人：非持久实体在区块卸载时会被抹掉
            if (online > 0 && online < threshold && count() + pendingRespawns < wanted) {
                // 有假人被杀了就补到配置数量，而不是只在 0 个时才补
                spawnBots(wanted - count());
            } else if (online >= threshold && count() > 0) {
                removeAll();
            }
        }
        targetPlayers();
    }

    // ---------------- 生成 ----------------

    public int spawnBots(int amount) {
        if (amount <= 0) {
            return 0;
        }
        LiteMap map = plugin.maps().active().orElse(null);
        if (map == null || map.world() == null) {
            return 0;
        }
        int created = 0;
        spawning = true;
        try {
            for (int i = 0; i < amount; i++) {
                Location location = plugin.maps().randomSpawn();
                if (location == null || location.getWorld() == null) {
                    continue;
                }
                boolean master = ThreadLocalRandom.current().nextInt(100)
                        < plugin.settings().botsMasterPercent();
                if (spawnOne(location, master, i + 1)) {
                    created++;
                }
            }
        } finally {
            spawning = false;
        }
        if (created > 0) {
            plugin.getLogger().info("已生成 " + created + " 个假人");
        }
        return created;
    }

    private boolean spawnOne(Location location, boolean master, int index) {
        Zombie zombie;
        try {
            zombie = location.getWorld().spawn(location, Zombie.class);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("生成假人失败: " + ex.getMessage());
            return false;
        }
        zombie.setShouldBurnInDay(false);
        zombie.setBaby(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setPersistent(false);
        zombie.setCanPickupItems(false);
        // 怪物被屏蔽后仍会听到僵尸叫声，声源就是假人本体，直接静音
        zombie.setSilent(plugin.settings().botsSilent());
        zombie.customName(Msg.mini(master
                ? "<red>大师</red> <dark_gray>#" + index + "</dark_gray>"
                : "<gray>新手</gray> <dark_gray>#" + index + "</dark_gray>"));
        zombie.setCustomNameVisible(true);
        zombie.addScoreboardTag("litebrawl:bot");

        // 装备与真人一致
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        zombie.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        zombie.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        zombie.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
        zombie.getEquipment().setHelmet(playerHead());
        zombie.getEquipment().setItemInMainHandDropChance(0.0F);
        zombie.getEquipment().setHelmetDropChance(0.0F);
        zombie.getEquipment().setChestplateDropChance(0.0F);
        zombie.getEquipment().setLeggingsDropChance(0.0F);
        zombie.getEquipment().setBootsDropChance(0.0F);

        var damage = zombie.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(master ? plugin.settings().botMasterDamage() : 0.0D);
        }
        var speed = zombie.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(master ? 0.24D : 0.20D);
        }

        bots.put(zombie.getUniqueId(), master);
        hideFromOptOut(zombie);
        return true;
    }

    private ItemStack playerHead() {
        // 只复用在线玩家已有 profile，避免离线模式下联网拉皮肤导致超时刷屏
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        return com.litebrawl.util.Heads.of(online.get(ThreadLocalRandom.current().nextInt(online.size())));
    }

    /** 对「关掉假人」的玩家隐藏某个假人。 */
    private void hideFromOptOut(Zombie zombie) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.playerSettings().bots(player)) {
                player.hideEntity(plugin, zombie);
            }
        }
    }

    /** 玩家切换开关后刷新可见性。 */
    public void refreshVisibility(Player player) {
        boolean visible = plugin.playerSettings().bots(player);
        for (org.bukkit.entity.Entity entity : entities()) {
            if (visible) {
                player.showEntity(plugin, entity);
            } else {
                player.hideEntity(plugin, entity);
            }
        }
    }

    public void removeAll() {
        for (UUID id : new ArrayList<>(bots.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        bots.clear();
    }

    // ---------------- 行为 ----------------

    /** 大师追最近的玩家；新手不设目标。 */
    private void targetPlayers() {
        for (Map.Entry<UUID, Boolean> entry : bots.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Zombie zombie) || !zombie.isValid()) {
                continue;
            }
            if (!entry.getValue()) {
                zombie.setTarget(null);
                continue;
            }
            if (zombie.getTarget() != null && zombie.getTarget().isValid()
                    && zombie.getTarget() instanceof Player target && plugin.game().isPlaying(target)) {
                continue;
            }
            Player nearest = null;
            double best = 24 * 24;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.game().isPlaying(player)) {
                    continue;
                }
                // 玩家在个人设置里关掉假人后，不再被锁定
                if (!plugin.playerSettings().bots(player)) {
                    continue;
                }
                if (!player.getWorld().equals(zombie.getWorld())) {
                    continue;
                }
                double distance = player.getLocation().distanceSquared(zombie.getLocation());
                if (distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
            zombie.setTarget(nearest);
        }
    }

    /**
     * 对齐内存表和实际实体。
     *
     * <p>假人是非持久实体：区块卸载时会被服务端直接抹掉，且不会触发死亡事件，
     * 所以必须定期核对，把「已经不存在的」清出表并按需复活。</p>
     */
    private void reconcile() {
        for (UUID id : new ArrayList<>(bots.keySet())) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                continue;
            }
            Boolean master = bots.remove(id);
            if (master != null && isActive()) {
                scheduleRespawn(master);
            }
        }
    }

    /** 假人死亡后延迟复活（保留原类型：大师/新手）。 */
    private void scheduleRespawn(boolean master) {
        if (!plugin.settings().botsRespawn()) {
            return;
        }
        int seconds = plugin.settings().botsRespawnSeconds();
        pendingRespawns++;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!isActive()) {
                    return;
                }
                org.bukkit.Location location = plugin.maps().randomSpawn();
                if (location == null || location.getWorld() == null) {
                    return;
                }
                spawning = true;
                try {
                    spawnOne(location, master, ++botCounter);
                } finally {
                    spawning = false;
                }
            } finally {
                pendingRespawns = Math.max(0, pendingRespawns - 1);
            }
        }, seconds * 20L);
    }

    /** 当前是否应该存在假人（手动模式下一直存在）。 */
    private boolean isActive() {
        if (!plugin.settings().botsEnabled()) {
            return false;
        }
        if (!plugin.settings().botsAuto()) {
            return true;
        }
        return Bukkit.getOnlinePlayers().size() < plugin.settings().botsMinPlayers();
    }

    // ---------------- 事件 ----------------

    /** 新手假人打不出伤害。 */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof LivingEntity living)) {
            return;
        }
        Boolean master = bots.get(living.getUniqueId());
        if (master == null) {
            return;
        }
        // 新手本来就不打人；被打的玩家如果关了假人，也一律免伤
        boolean victimDisabled = event.getEntity() instanceof Player player
                && !plugin.playerSettings().bots(player);
        if (!master || victimDisabled) {
            event.setCancelled(true);
        }
    }

    /** 假人被杀：给击杀者奖励，并从列表里移除（自动模式会再补）。 */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Boolean master = bots.remove(event.getEntity().getUniqueId());
        if (master == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            rewardKiller(killer, master);
        }
        // 假人死了要能在原地图复活，而不是直接消失
        scheduleRespawn(master);
    }

    /** 给击杀者发奖励（含双倍加成与升级提示）。 */
    private void rewardKiller(Player killer, boolean master) {
        Profile profile = plugin.profiles().get(killer);
        long coins = master ? plugin.settings().botMasterCoins() : plugin.settings().botNoviceCoins();
        long exp = master ? plugin.settings().botMasterExp() : plugin.settings().botNoviceExp();
        int before = plugin.profiles().levelOf(profile.getExperience());
        coins = plugin.boost().applyCoins(killer, coins);
        exp = plugin.boost().applyExp(killer, exp);
        profile.addKills(1);
        plugin.profiles().recordKill(profile);
        profile.addCoins(coins);
        profile.addExperience(exp);
        plugin.profiles().saveAsync(profile);

        Msg.info(killer, plugin.settings().prefix(),
                "<gray>击杀</gray> %tier% <dark_gray>·</dark_gray> <green>+%coins% 硬币</green> <dark_gray>·</dark_gray> <aqua>+%exp% 经验</aqua>",
                "tier", master ? "<red>大师假人</red>" : "<gray>新手假人</gray>",
                "coins", String.valueOf(coins),
                "exp", String.valueOf(exp));

        int after = plugin.profiles().levelOf(profile.getExperience());
        if (after > before) {
            Msg.info(killer, plugin.settings().prefix(),
                    "<gradient:#f6d365:#fda085>等级提升</gradient> <white>Lv.%level%</white>",
                    "level", String.valueOf(after));
        }
    }
}
