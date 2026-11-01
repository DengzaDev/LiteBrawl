package com.litebrawl.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 单个玩家的持久化数据。 */
public final class Profile {

    private final UUID uuid;
    private String name;
    private long coins;
    private long experience;
    private int kills;
    private int deaths;
    private int assists;
    private volatile boolean dirty;

    /** 已购买的外观 id 与「类别 -> 已装备 id」 */
    private final Set<String> ownedCosmetics = new LinkedHashSet<>();
    private final Map<String, String> equippedCosmetics = new LinkedHashMap<>();
    /** 个人设置：sidebar/tab/combat-notify/sounds/layout ... */
    private final Map<String, String> settings = new LinkedHashMap<>();

    public Profile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "unknown" : name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            this.dirty = true;
        }
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = Math.max(0L, coins);
        this.dirty = true;
    }

    public void addCoins(long amount) {
        setCoins(this.coins + amount);
    }

    public boolean takeCoins(long amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.coins < amount) {
            return false;
        }
        setCoins(this.coins - amount);
        return true;
    }

    public long getExperience() {
        return experience;
    }

    public void setExperience(long experience) {
        this.experience = Math.max(0L, experience);
        this.dirty = true;
    }

    public void addExperience(long amount) {
        setExperience(this.experience + amount);
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = Math.max(0, kills);
        this.dirty = true;
    }

    public void addKills(int amount) {
        setKills(this.kills + amount);
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = Math.max(0, deaths);
        this.dirty = true;
    }

    public void addDeaths(int amount) {
        setDeaths(this.deaths + amount);
    }

    public int getAssists() {
        return assists;
    }

    public void setAssists(int assists) {
        this.assists = Math.max(0, assists);
        this.dirty = true;
    }

    public void addAssists(int amount) {
        setAssists(this.assists + amount);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public double kd() {
        return deaths <= 0 ? kills : (double) kills / (double) deaths;
    }

    // ---------------- 外观 ----------------

    public Set<String> ownedCosmetics() {
        return ownedCosmetics;
    }

    public boolean ownsCosmetic(String id) {
        return id != null && ownedCosmetics.contains(id);
    }

    public void addCosmetic(String id) {
        if (id != null && ownedCosmetics.add(id)) {
            dirty = true;
        }
    }

    public String equippedCosmetic(String category) {
        return equippedCosmetics.get(category);
    }

    public void equipCosmetic(String category, String id) {
        if (category == null) {
            return;
        }
        if (id == null) {
            equippedCosmetics.remove(category);
        } else {
            equippedCosmetics.put(category, id);
        }
        dirty = true;
    }

    /** 序列化成 "id1,id2|KILL_MESSAGE=a;CHAT_EFFECT=b" 存一列。 */
    public String serializeCosmetics() {
        StringBuilder sb = new StringBuilder(String.join(",", ownedCosmetics));
        sb.append('|');
        boolean first = true;
        for (Map.Entry<String, String> entry : equippedCosmetics.entrySet()) {
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    // ---------------- 个人设置 ----------------

    public String setting(String key, String fallback) {
        String value = settings.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public boolean settingBool(String key, boolean fallback) {
        String value = settings.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public void setSetting(String key, String value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            settings.remove(key);
        } else {
            settings.put(key, value);
        }
        dirty = true;
    }

    public void setSetting(String key, boolean value) {
        setSetting(key, String.valueOf(value));
    }

    public String serializeSettings() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    public void loadSettings(String raw) {
        settings.clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String pair : raw.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                settings.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        dirty = false;
    }

    public void loadCosmetics(String raw) {
        ownedCosmetics.clear();
        equippedCosmetics.clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length > 0 && !parts[0].isBlank()) {
            for (String id : parts[0].split(",")) {
                if (!id.isBlank()) {
                    ownedCosmetics.add(id.trim());
                }
            }
        }
        if (parts.length > 1 && !parts[1].isBlank()) {
            for (String pair : parts[1].split(";")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    equippedCosmetics.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
        dirty = false;
    }
}
