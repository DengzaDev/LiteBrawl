package com.litebrawl.game;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.data.Profile;
import com.litebrawl.map.LiteMap;
import com.litebrawl.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 一局的流程：进大厅、开始、死亡结算、重生。 */
public final class GameService {

    private final LiteBrawlPlugin plugin;
    private final Set<UUID> playing = ConcurrentHashMap.newKeySet();

    public GameService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPlaying(Player player) {
        return playing.contains(player.getUniqueId());
    }

    public void markPlaying(Player player) {
        playing.add(player.getUniqueId());
    }

    /** 回到大厅：清空状态、传送到地图出生点、发开始物品。 */
    public void prepareLobby(Player player) {
        playing.remove(player.getUniqueId());
        plugin.quivers().clear(player.getUniqueId());

        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[]{null, null, null, null});
        player.getInventory().setItemInOffHand(null);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setHealth(player.getMaxHealth());
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        Location spawn = plugin.maps().activeSpawn();
        if (spawn != null) {
            player.teleportAsync(spawn);
        }
        plugin.kits().giveLobbyItems(player);
        applyAttackSpeed(player, false);
        plugin.protection().clear(player.getUniqueId());
    }

    /** 右键「开始」或 /start。 */
    public void start(Player player) {
        LiteMap map = plugin.maps().active().orElse(null);
        if (map == null) {
            Msg.info(player, plugin.settings().prefix(),
                    "<red>当前服务器还没有激活的地图，请管理员使用 </red><white>/maps create <目录> <显示名></white>");
            return;
        }
        if (!map.hasSpawn()) {
            Msg.info(player, plugin.settings().prefix(),
                    "<red>地图出生点尚未设置，请管理员使用 </red><white>/map setspawn</white>");
            return;
        }
        if (map.spawnPoints().isEmpty()) {
            Msg.info(player, plugin.settings().prefix(),
                    "<red>地图还没有扫描出生方块，请管理员使用 </red><white>/map scan</white>");
            return;
        }

        Location target = plugin.maps().randomSpawn();
        if (target == null) {
            Msg.info(player, plugin.settings().prefix(), "<red>出生点无效，世界可能未加载。</red>");
            return;
        }

        markPlaying(player);
        plugin.combat().clear(player.getUniqueId());
        plugin.quivers().clear(player.getUniqueId());
        player.closeInventory();
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setHealth(player.getMaxHealth());
        plugin.kits().giveKit(player);
        applyAttackSpeed(player, true);
        givePendingPurchases(player);
        player.teleportAsync(target);
        plugin.protection().protect(player);

        Msg.info(player, plugin.settings().prefix(),
                "<green>已投入战场</green> <dark_gray>·</dark_gray> <gray>祝你活久一点。</gray>");
    }

    /**
     * 攻击冷却开关。
     *
     * <p>原版 1.9+ 用 GENERIC_ATTACK_SPEED 控制两次满伤之间的间隔，
     * 把它拉到很高就是「没有攻击冷却」（1.8 手感）。</p>
     */
    private void applyAttackSpeed(Player player, boolean inBattle) {
        var attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        boolean faster = inBattle && plugin.settings().combatNoAttackCooldown();
        attribute.setBaseValue(faster ? plugin.settings().combatAttackSpeed() : 4.0D);
    }

    /** 把商店里买好、等着下场发放的东西放进背包。 */
    private void givePendingPurchases(Player player) {
        java.util.List<ItemStack> pending = plugin.shop().consumePending(player);
        if (pending.isEmpty()) {
            return;
        }
        for (ItemStack item : pending) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        Msg.info(player, plugin.settings().prefix(),
                "<green>已发放 %count% 件商店购买的道具。</green>",
                "count", String.valueOf(pending.size()));
    }

    /** 死亡结算：奖励、死亡数、死亡信息。 */
    public void handleDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && killer.getUniqueId().equals(victim.getUniqueId())) {
            killer = null;
        }

        event.setKeepInventory(plugin.settings().keepInventory());
        event.getDrops().clear();
        event.setDroppedExp(0);

        String distance = "-";
        if (killer != null && victim.getWorld().equals(killer.getWorld())) {
            distance = String.valueOf(Math.round(killer.getLocation().distance(victim.getLocation())));
        }
        String weapon = killer == null ? "-" : plugin.messages().weaponName(
                killer.getInventory().getItemInMainHand().getType());

        if (killer != null) {
            String template = plugin.cosmetics().killMessage(killer);
            if (template == null) {
                template = plugin.messages().killMessage();
            }
            event.deathMessage(Msg.mini(Msg.replace(template,
                    "killer", killer.getName(),
                    "victim", victim.getName(),
                    "distance", distance,
                    "weapon", weapon)));
            plugin.cosmetics().playKillEffect(killer, victim.getLocation());
        } else {
            String template = plugin.cosmetics().deathMessage(victim);
            if (template == null) {
                template = plugin.messages().deathMessage();
            }
            event.deathMessage(Msg.mini(Msg.replace(template, "victim", victim.getName())));
        }

        Profile victimProfile = plugin.profiles().get(victim);
        victimProfile.addDeaths(1);
        plugin.profiles().saveAsync(victimProfile);
        plugin.profiles().recordDeath(victimProfile);

        List<UUID> assists = plugin.combat().consumeAssists(victim, killer);
        playing.remove(victim.getUniqueId());
        plugin.quivers().clear(victim.getUniqueId());
        plugin.quivers().resetBonus(victim.getUniqueId());
        long stolenBounty = plugin.bounty().claim(victim);

        if (killer != null && killer.isOnline()) {
            Profile killerProfile = plugin.profiles().get(killer);
            int before = plugin.profiles().levelOf(killerProfile.getExperience());
            // 反刷分：短时间内反复杀同一个人，收益按 decay^n 递减
            double farmFactor = plugin.antiFarm().factor(killer, victim);
            long killCoins = Math.round(
                    plugin.boost().applyCoins(killer, plugin.settings().killCoins()) * farmFactor);
            long killExp = Math.round(
                    plugin.boost().applyExp(killer, plugin.settings().killExp()) * farmFactor);

            killerProfile.addKills(1);
            plugin.profiles().recordKill(killerProfile);
            killerProfile.addCoins(killCoins);
            killerProfile.addExperience(killExp);
            plugin.profiles().saveAsync(killerProfile);
            plugin.antiFarm().record(killer, victim);

            if (plugin.settings().killGoldenApples() > 0) {
                giveOrDrop(killer, new ItemStack(Material.GOLDEN_APPLE, plugin.settings().killGoldenApples()));
            }
            Msg.info(killer, plugin.settings().prefix(),
                    "<green>+%coins% 硬币</green> <dark_gray>·</dark_gray> <aqua>+%exp% 经验</aqua>",
                    "coins", String.valueOf(killCoins),
                    "exp", String.valueOf(killExp));
            if (farmFactor < 0.999D) {
                Msg.info(killer, plugin.settings().prefix(),
                        "<gray>重复击杀同一玩家，本次收益按</gray> <yellow>%percent%%</yellow> <gray>结算</gray>",
                        "percent", String.valueOf(Math.round(farmFactor * 100)));
            }
            announceLevelUp(killer, before, plugin.profiles().levelOf(killerProfile.getExperience()));

            if (stolenBounty > 0L) {
                killerProfile.addCoins(plugin.boost().applyCoins(killer, stolenBounty));
                plugin.profiles().saveAsync(killerProfile);
                broadcast(plugin.settings().bountyAnnounceClaim(),
                        "killer", killer.getName(),
                        "victim", victim.getName(),
                        "amount", String.valueOf(stolenBounty));
            }

            long added = plugin.bounty().onKill(killer);
            if (added > 0L) {
                broadcast(plugin.settings().bountyAnnounceNew(),
                        "player", killer.getName(),
                        "streak", String.valueOf(plugin.bounty().streak(killer.getUniqueId())),
                        "bounty", String.valueOf(plugin.bounty().bounty(killer.getUniqueId())));
            }

            int streak = plugin.bounty().streak(killer.getUniqueId());
            plugin.boost().onKillStreak(killer, streak);
            if (plugin.messages().streakEnabled()
                    && streak > 1
                    && streak % plugin.messages().streakEvery() == 0) {
                broadcast(plugin.messages().streakMessage(),
                        "player", killer.getName(),
                        "streak", String.valueOf(streak));
            }
        }

        for (UUID id : assists) {
            Player assistant = Bukkit.getPlayer(id);
            if (assistant == null || !assistant.isOnline()) {
                continue;
            }
            Profile profile = plugin.profiles().get(assistant);
            int before = plugin.profiles().levelOf(profile.getExperience());
            profile.addAssists(1);
            profile.addCoins(plugin.boost().applyCoins(assistant, plugin.settings().assistCoins()));
            profile.addExperience(plugin.boost().applyExp(assistant, plugin.settings().assistExp()));
            plugin.profiles().saveAsync(profile);

            Msg.info(assistant, plugin.settings().prefix(),
                    "<gray>助杀</gray> <green>+%coins% 硬币</green> <dark_gray>·</dark_gray> <aqua>+%exp% 经验</aqua>",
                    "coins", String.valueOf(plugin.settings().assistCoins()),
                    "exp", String.valueOf(plugin.settings().assistExp()));
            announceLevelUp(assistant, before, plugin.profiles().levelOf(profile.getExperience()));
        }
    }

    /** 重生：回到地图出生点，然后回大厅状态。 */
    public void onRespawn(Player player) {
        // 重生点已由 PlayerRespawnEvent 设定，这里只补一次落地后的状态整理。
        Bukkit.getScheduler().runTask(plugin, () -> prepareLobby(player));
    }

    public void quit(Player player) {
        playing.remove(player.getUniqueId());
        plugin.quivers().clear(player.getUniqueId());
        plugin.combat().clear(player.getUniqueId());
    }

    private void broadcast(String mini, String... placeholders) {
        var component = Msg.mini(plugin.settings().prefix() + Msg.replace(mini, placeholders));
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    private void announceLevelUp(Player player, int before, int after) {
        if (after <= before) {
            return;
        }
        Msg.info(player, plugin.settings().prefix(),
                "<gradient:#f6d365:#fda085><bold>等级提升</bold></gradient> <white>Lv.%before%</white> <dark_gray>→</dark_gray> <white>Lv.%after%</white>",
                "before", String.valueOf(before), "after", String.valueOf(after));
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }
}
