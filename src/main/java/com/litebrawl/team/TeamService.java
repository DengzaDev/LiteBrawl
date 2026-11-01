package com.litebrawl.team;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 组队。
 *
 * <p>队伍只存在内存里（一局的事），人数上限由 team.max-size 控制（默认 8）。
 * 队友之间免伤；邀请通过可点击的聊天消息确认，或在大厅快捷栏的组队界面里点人邀请。</p>
 */
public final class TeamService implements Listener {

    private static final int GUI_SIZE = 27;
    private static final int SLOT_INFO = 4;
    private static final int HEAD_START = 9;
    private static final int HEAD_END = 17;
    private static final int SLOT_KICK = 20;
    private static final int SLOT_DISBAND = 23;
    private static final int SLOT_LEAVE = 22;

    private final LiteBrawlPlugin plugin;
    private final NamespacedKey teamKey;
    private final Map<UUID, Team> byPlayer = new LinkedHashMap<>();
    private final Map<UUID, Invite> invites = new LinkedHashMap<>();

    public TeamService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        this.teamKey = new NamespacedKey(plugin, "team_item");
    }

    /** 一支队伍。 */
    public static final class Team {
        private final String name;
        private final UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();

        private Team(String name, UUID leader) {
            this.name = name;
            this.leader = leader;
            this.members.add(leader);
        }

        public String name() {
            return name;
        }

        public UUID leader() {
            return leader;
        }

        public Set<UUID> members() {
            return members;
        }

        public int size() {
            return members.size();
        }
    }

    private static final class Invite {
        private final String teamName;
        private final long expiresAt;

        private Invite(String teamName, long expiresAt) {
            this.teamName = teamName;
            this.expiresAt = expiresAt;
        }
    }

    /** 组队 GUI 的持有者。 */
    private static final class TeamHolder implements InventoryHolder {
        private Inventory inventory;
        private boolean memberMode;
        /** 主界面：每个头颅槽位对应的玩家 */
        private final List<UUID> candidates = new ArrayList<>();
        /** 踢人界面：每个头颅槽位对应的队员 */
        private final List<UUID> members = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------------- 查询 ----------------

    public Team teamOf(UUID uuid) {
        return byPlayer.get(uuid);
    }

    public boolean sameTeam(UUID a, UUID b) {
        Team team = byPlayer.get(a);
        return team != null && team.members.contains(b);
    }

    public String teamNameOf(UUID uuid) {
        Team team = byPlayer.get(uuid);
        return team == null ? "" : team.name;
    }

    public int sizeOf(UUID uuid) {
        Team team = byPlayer.get(uuid);
        return team == null ? 0 : team.size();
    }

    // ---------------- 操作 ----------------

    /** @return null 表示成功，否则是失败原因 */
    public String create(Player player) {
        if (byPlayer.containsKey(player.getUniqueId())) {
            return "你已经在队伍里了";
        }
        String name = player.getName();
        if (findByName(name) != null) {
            name = name + "的队伍";
        }
        Team team = new Team(name, player.getUniqueId());
        byPlayer.put(player.getUniqueId(), team);
        return null;
    }

    public String invite(Player from, Player target) {
        Team team = byPlayer.get(from.getUniqueId());
        if (team == null) {
            return "你还没有队伍";
        }
        if (!team.leader.equals(from.getUniqueId())) {
            return "只有队长可以邀请";
        }
        if (team.size() >= plugin.settings().teamMaxSize()) {
            return "队伍已满（上限 " + plugin.settings().teamMaxSize() + " 人）";
        }
        if (team.members.contains(target.getUniqueId())) {
            return "对方已经在你的队伍里了";
        }
        if (byPlayer.containsKey(target.getUniqueId())) {
            return "对方已经有队伍了";
        }

        invites.put(target.getUniqueId(), new Invite(team.name,
                System.currentTimeMillis() + plugin.settings().teamInviteSeconds() * 1000L));

        Msg.info(from, plugin.settings().prefix(), "<gray>已邀请</gray> <white>%target%</white>",
                "target", target.getName());

        Component message = Msg.mini(plugin.settings().prefix()
                        + "<white>" + from.getName() + "</white> <gray>邀请你加入队伍</gray> <yellow>"
                        + team.name + "</yellow> ")
                .append(Msg.mini("<green><bold>[点击接受]</bold></green>")
                        .clickEvent(ClickEvent.runCommand("/team accept")));
        target.sendMessage(message);
        return null;
    }

    public String accept(Player player) {
        Invite invite = invites.get(player.getUniqueId());
        if (invite == null) {
            return "没有待处理的邀请";
        }
        if (System.currentTimeMillis() > invite.expiresAt) {
            invites.remove(player.getUniqueId());
            return "邀请已过期";
        }
        Team team = findByName(invite.teamName);
        if (team == null) {
            invites.remove(player.getUniqueId());
            return "该队伍已经不存在了";
        }
        if (team.size() >= plugin.settings().teamMaxSize()) {
            invites.remove(player.getUniqueId());
            return "队伍已满";
        }
        if (byPlayer.containsKey(player.getUniqueId())) {
            return "你已经在队伍里了";
        }
        team.members.add(player.getUniqueId());
        byPlayer.put(player.getUniqueId(), team);
        invites.remove(player.getUniqueId());
        broadcastTeam(team, "<green>%player% 加入了队伍</green>", "player", player.getName());
        return null;
    }

    public String leave(Player player) {
        Team team = byPlayer.get(player.getUniqueId());
        if (team == null) {
            return "你不在队伍里";
        }
        team.members.remove(player.getUniqueId());
        byPlayer.remove(player.getUniqueId());

        if (team.members.isEmpty()) {
            return null;
        }
        if (team.leader.equals(player.getUniqueId())) {
            UUID nextLeader = team.members.iterator().next();
            Team promoted = new Team(team.name, nextLeader);
            promoted.members.addAll(team.members);
            for (UUID member : promoted.members) {
                byPlayer.put(member, promoted);
            }
            broadcastTeam(promoted, "<gray>队长已转交给</gray> <white>%player%</white>",
                    "player", nameOf(nextLeader));
        } else {
            broadcastTeam(team, "<gray>%player% 离开了队伍</gray>", "player", player.getName());
        }
        return null;
    }

    public void quit(Player player) {
        invites.remove(player.getUniqueId());
        leave(player);
    }

    public void clearAll() {
        byPlayer.clear();
        invites.clear();
    }

    // ---------------- 大厅物品与界面 ----------------

    public ItemStack teamItem() {
        ItemStack stack = new ItemStack(plugin.settings().teamItemMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini(plugin.settings().teamItemName()));
            meta.lore(plugin.settings().teamItemLore().stream().map(Msg::mini).toList());
            meta.getPersistentDataContainer().set(teamKey, PersistentDataType.STRING, "true");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isTeamItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(teamKey, PersistentDataType.STRING);
    }

    public void openGui(Player player) {
        TeamHolder holder = new TeamHolder();
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE,
                Msg.mini(plugin.settings().teamGuiTitle()));
        holder.inventory = inventory;

        Team team = teamOf(player.getUniqueId());
        inventory.setItem(SLOT_INFO, infoItem(team));
        inventory.setItem(SLOT_LEAVE, simple(Material.BARRIER, "<red>离开队伍</red>"));
        if (team != null && team.leader.equals(player.getUniqueId())) {
            inventory.setItem(SLOT_KICK, simple(Material.IRON_DOOR, "<yellow>踢出队员</yellow>"));
            inventory.setItem(SLOT_DISBAND, simple(Material.TNT, "<dark_red>解散队伍</dark_red>"));
        }

        int slot = HEAD_START;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot > HEAD_END) {
                break;
            }
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (team != null && team.members.contains(online.getUniqueId())) {
                continue;
            }
            inventory.setItem(slot++, head(online));
            holder.candidates.add(online.getUniqueId());
        }
        player.openInventory(inventory);
    }

    /** 踢人界面：列出所有队员。 */
    public void openMemberGui(Player player) {
        Team team = teamOf(player.getUniqueId());
        if (team == null || !team.leader.equals(player.getUniqueId())) {
            return;
        }
        TeamHolder holder = new TeamHolder();
        holder.memberMode = true;
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE,
                Msg.mini("<dark_gray>踢出队员"));
        holder.inventory = inventory;

        int slot = 9;
        for (UUID member : team.members()) {
            if (member.equals(player.getUniqueId())) {
                continue;
            }
            Player online = Bukkit.getPlayer(member);
            if (online == null) {
                continue;
            }
            inventory.setItem(slot++, head(online));
            holder.members.add(member);
        }
        player.openInventory(inventory);
    }

    /** 队长踢人。 */
    public String kick(Player leader, UUID target) {
        Team team = teamOf(leader.getUniqueId());
        if (team == null) {
            return "你还没有队伍";
        }
        if (!team.leader.equals(leader.getUniqueId())) {
            return "只有队长可以踢人";
        }
        if (!team.members.contains(target)) {
            return "对方不在队伍里";
        }
        team.members.remove(target);
        byPlayer.remove(target);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            Msg.info(online, plugin.settings().prefix(), "<red>你被移出了队伍。</red>");
            broadcastTeam(team, "<gray>%player% 被移出队伍</gray>",
                    "player", online.getName());
        }
        return null;
    }

    /** 队长解散队伍。 */
    public String disband(Player leader) {
        Team team = teamOf(leader.getUniqueId());
        if (team == null) {
            return "你还没有队伍";
        }
        if (!team.leader.equals(leader.getUniqueId())) {
            return "只有队长可以解散";
        }
        for (UUID member : new ArrayList<>(team.members)) {
            byPlayer.remove(member);
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                Msg.info(online, plugin.settings().prefix(), "<gray>队伍已解散。</gray>");
            }
        }
        return null;
    }

    private ItemStack infoItem(Team team) {
        if (team == null) {
            return simple(Material.CHEST, "<green>点击创建队伍</green>");
        }
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini("<gold>队伍 " + team.name + "</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.mini("<gray>人数：<white>" + team.size() + "/"
                    + plugin.settings().teamMaxSize() + "</white></gray>"));
            lore.add(Msg.mini("<gray>成员：</gray>"));
            for (UUID member : team.members()) {
                lore.add(Msg.mini("<dark_gray> · </dark_gray><white>" + nameOf(member) + "</white>"
                        + (member.equals(team.leader) ? "<gold> (队长)</gold>" : "")));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack head(Player target) {
        ItemStack stack = com.litebrawl.util.Heads.of(target);
        if (stack.getItemMeta() instanceof SkullMeta skull) {
            skull.displayName(Msg.mini("<white>" + target.getName() + "</white>"));
            skull.lore(List.of(Msg.mini("<gray>点击邀请加入队伍</gray>")));
            stack.setItemMeta(skull);
        }
        return stack;
    }

    private ItemStack simple(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.mini(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ---------------- 事件 ----------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TeamHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == SLOT_INFO) {
            if (teamOf(player.getUniqueId()) == null) {
                String error = create(player);
                Msg.info(player, plugin.settings().prefix(), error == null
                        ? "<green>队伍已创建，点头颅邀请队友</green>"
                        : "<red>%error%</red>", "error", error == null ? "" : error);
            }
            player.closeInventory();
            return;
        }
        if (slot == SLOT_LEAVE) {
            String error = leave(player);
            Msg.info(player, plugin.settings().prefix(), error == null
                    ? "<gray>你已离开队伍</gray>"
                    : "<red>%error%</red>", "error", error == null ? "" : error);
            player.closeInventory();
            return;
        }

        if (slot == SLOT_KICK) {
            player.closeInventory();
            openMemberGui(player);
            return;
        }
        if (slot == SLOT_DISBAND) {
            String error = disband(player);
            Msg.info(player, plugin.settings().prefix(), error == null
                    ? "<gray>队伍已解散</gray>"
                    : "<red>%error%</red>", "error", error == null ? "" : error);
            player.closeInventory();
            return;
        }

        // 用槽位映射取目标：头颅皮肤在离线服拿不到 owner，不能靠 metadata 识别
        UUID targetId = null;
        if (holder.memberMode) {
            int index = slot - HEAD_START;
            if (index >= 0 && index < holder.members.size()) {
                targetId = holder.members.get(index);
            }
        } else {
            int index = slot - HEAD_START;
            if (index >= 0 && index < holder.candidates.size()) {
                targetId = holder.candidates.get(index);
            }
        }
        if (targetId == null) {
            return;
        }
        if (holder.memberMode) {
            String error = kick(player, targetId);
            if (error == null) {
                player.closeInventory();
                openMemberGui(player);
                return;
            }
            Msg.info(player, plugin.settings().prefix(), "<red>%error%</red>", "error", error);
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            Msg.info(player, plugin.settings().prefix(), "<gray>对方已经下线了。</gray>");
            player.closeInventory();
            return;
        }
        String error = invite(player, target);
        if (error != null) {
            Msg.info(player, plugin.settings().prefix(), "<red>%error%</red>", "error", error);
        }
        player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TeamHolder) {
            event.setCancelled(true);
        }
    }

    // ---------------- 内部 ----------------

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        return uuid.toString().substring(0, 8);
    }

    private Team findByName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (Team team : byPlayer.values()) {
            if (team.name.toLowerCase(Locale.ROOT).equals(lower)) {
                return team;
            }
        }
        return null;
    }

    private void broadcastTeam(Team team, String mini, String... placeholders) {
        Component component = Msg.mini(plugin.settings().prefix() + Msg.replace(mini, placeholders));
        for (UUID member : team.members()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                online.sendMessage(component);
            }
        }
    }
}
