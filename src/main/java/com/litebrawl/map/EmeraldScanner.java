package com.litebrawl.map;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 在世界的指定范围内扫描出生方块（默认绿宝石块）。
 *
 * <p>按 chunk 分批在主线程处理，避免一次性卡死服务器；
 * 每 tick 处理 3 个 chunk，160 半径（约 441 个 chunk）大约 7 秒扫完。</p>
 */
public final class EmeraldScanner {

    public interface Callback {
        void done(List<int[]> points);

        void failed(String reason);
    }

    private static final int CHUNKS_PER_TICK = 3;

    private final LiteBrawlPlugin plugin;
    private volatile boolean running;

    public EmeraldScanner(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running;
    }

    public void reset() {
        running = false;
    }

    public void scan(LiteMap map, Callback callback) {
        scan(map, 0, callback);
    }

    /**
     * @param radiusOverride 大于 0 时临时覆盖配置里的扫描半径（单位：方块）
     */
    public void scan(LiteMap map, int radiusOverride, Callback callback) {
        if (running) {
            callback.failed("已有一个扫描任务正在进行");
            return;
        }

        World world = map.world();
        if (world == null) {
            callback.failed("地图世界未加载: " + map.worldName());
            return;
        }

        var center = map.spawn();
        if (center == null) {
            center = world.getSpawnLocation();
        }

        final int radius = radiusOverride > 0
                ? Math.max(16, Math.min(1024, radiusOverride))
                : plugin.settings().scanRadius();
        final int yMin = Math.max(world.getMinHeight(), plugin.settings().scanYMin());
        final int yMax = Math.min(world.getMaxHeight() - 2, plugin.settings().scanYMax());
        if (yMax <= yMin) {
            callback.failed("扫描高度范围无效：y-min=" + yMin + " y-max=" + yMax);
            return;
        }

        final Material target = plugin.settings().spawnBlock();
        final int chunkX0 = (center.getBlockX() - radius) >> 4;
        final int chunkX1 = (center.getBlockX() + radius) >> 4;
        final int chunkZ0 = (center.getBlockZ() - radius) >> 4;
        final int chunkZ1 = (center.getBlockZ() + radius) >> 4;

        final List<int[]> chunks = new ArrayList<>();
        for (int cx = chunkX0; cx <= chunkX1; cx++) {
            for (int cz = chunkZ0; cz <= chunkZ1; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }

        running = true;
        final List<int[]> found = new ArrayList<>();
        final int[] index = {0};
        final int[] done = {0};
        final int total = chunks.size();

        plugin.getLogger().info("开始扫描地图 " + map.id() + "：" + total + " 个 chunk，半径 " + radius);

        new BukkitRunnable() {
            @Override
            public void run() {
                int processed = 0;
                while (processed < CHUNKS_PER_TICK && index[0] < total) {
                    int[] coord = chunks.get(index[0]++);
                    processed++;
                    done[0]++;
                    try {
                        ChunkSnapshot snapshot = world.getChunkAt(coord[0], coord[1])
                                .getChunkSnapshot(false, false, false);
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                for (int y = yMin; y <= yMax; y++) {
                                    if (snapshot.getBlockType(x, y, z) != target) {
                                        continue;
                                    }
                                    if (!snapshot.getBlockType(x, y + 1, z).isAir()) {
                                        break;
                                    }
                                    if (y + 2 <= yMax && !snapshot.getBlockType(x, y + 2, z).isAir()) {
                                        break;
                                    }
                                    found.add(new int[]{(coord[0] << 4) + x, y, (coord[1] << 4) + z});
                                    break;
                                }
                            }
                        }
                    } catch (RuntimeException ex) {
                        plugin.getLogger().warning("扫描 chunk " + coord[0] + "," + coord[1] + " 失败: " + ex.getMessage());
                    }
                }

                if (done[0] % 150 < CHUNKS_PER_TICK) {
                    plugin.getLogger().info("扫描进度 " + done[0] + "/" + total + "，已找到 " + found.size() + " 个出生点");
                }

                if (index[0] >= total) {
                    cancel();
                    running = false;
                    plugin.getLogger().info("扫描完成：" + found.size() + " 个 " + target.name() + " 出生点");
                    callback.done(found);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
