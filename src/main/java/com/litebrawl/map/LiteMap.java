package com.litebrawl.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一张可玩地图：对应服务器目录下的一个世界文件夹。 */
public final class LiteMap {

    private final String id;
    private String displayName;
    private final String worldName;

    private boolean hasSpawn;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;

    private final List<int[]> spawnPoints = new ArrayList<>();

    public LiteMap(String id, String displayName, String worldName) {
        this.id = id;
        this.displayName = displayName;
        this.worldName = worldName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String worldName() {
        return worldName;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public boolean hasSpawn() {
        return hasSpawn;
    }

    public Location spawn() {
        World world = world();
        if (world == null || !hasSpawn) {
            return null;
        }
        return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    public void setSpawn(Location location) {
        if (location == null) {
            return;
        }
        this.hasSpawn = true;
        this.spawnX = location.getX();
        this.spawnY = location.getY();
        this.spawnZ = location.getZ();
        this.spawnYaw = location.getYaw();
        this.spawnPitch = location.getPitch();
    }

    /** 世界尚未加载时用原始坐标写入出生点。 */
    public void setSpawnRaw(double x, double y, double z, float yaw, float pitch) {
        this.hasSpawn = true;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.spawnYaw = yaw;
        this.spawnPitch = pitch;
    }

    public double spawnX() {
        return spawnX;
    }

    public double spawnY() {
        return spawnY;
    }

    public double spawnZ() {
        return spawnZ;
    }

    public float spawnYaw() {
        return spawnYaw;
    }

    public float spawnPitch() {
        return spawnPitch;
    }

    public void markHasSpawn(boolean hasSpawn) {
        this.hasSpawn = hasSpawn;
    }

    public List<int[]> spawnPoints() {
        return Collections.unmodifiableList(spawnPoints);
    }

    public void setSpawnPoints(List<int[]> points) {
        spawnPoints.clear();
        if (points != null) {
            spawnPoints.addAll(points);
        }
    }

    public void clearSpawnPoints() {
        spawnPoints.clear();
    }

    public boolean ready() {
        return world() != null && hasSpawn;
    }
}
