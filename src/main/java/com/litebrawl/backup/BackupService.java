package com.litebrawl.backup;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 完整备份：把配置文件与数据库打成一个 zip，并保留最近 N 份。
 *
 * <p>之前的「备份」只复制 config.yml，玩家数据和地图配置完全没保护，
 * 误删或改坏配置就等于丢档。这里覆盖：</p>
 * <ul>
 *   <li>所有 yml（config / messages / maps / boards / boost / npcs）</li>
 *   <li>SQLite 数据库（用 {@code VACUUM INTO} 导出，运行中也能拿到一致副本）</li>
 * </ul>
 *
 * <p>MySQL 模式不导出数据库（无法在进程内安全导出），会在结果里说明，
 * 需要的话请用 mysqldump。</p>
 */
public final class BackupService {

    /** 会被打进备份的配置文件（不存在就跳过）。 */
    private static final List<String> CONFIG_FILES = List.of(
            "config.yml", "messages.yml", "maps.yml",
            "boards.yml", "boost.yml", "npcs.yml");

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final LiteBrawlPlugin plugin;
    private File dir;
    private BukkitTask task;

    public BackupService(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.dir = new File(plugin.getDataFolder(), "backups");
        if (!plugin.settings().backupEnabled()) {
            return;
        }
        int hours = plugin.settings().backupIntervalHours();
        if (hours <= 0) {
            return;
        }
        long ticks = hours * 60L * 60L * 20L;
        // 备份是纯 IO，放异步；第一次延后一个周期再跑，避免和启动抢资源
        task = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, () -> create("定时"), ticks, ticks);
        plugin.getLogger().info("自动备份已启用：每 " + hours + " 小时一次，保留最近 "
                + plugin.settings().backupKeep() + " 份");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public File directory() {
        if (dir == null) {
            dir = new File(plugin.getDataFolder(), "backups");
        }
        return dir;
    }

    /**
     * 生成一份备份。同步执行，调用方自己决定放不放到异步线程。
     *
     * @param reason 触发来源（定时 / 手动 / 网页），会写进日志
     * @return 给人看的结果文本
     */
    public synchronized String create(String reason) {
        File target = prepareTarget();
        if (target == null) {
            return "备份失败：无法创建 backups 目录";
        }
        int files = 0;
        boolean dbIncluded = false;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target.toPath()))) {
            for (String name : CONFIG_FILES) {
                File file = new File(plugin.getDataFolder(), name);
                if (!file.exists() || file.length() == 0L) {
                    continue;
                }
                put(zip, name, Files.readAllBytes(file.toPath()));
                files++;
            }
            if (plugin.settings().backupIncludeDatabase()) {
                dbIncluded = appendDatabase(zip);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("备份失败: " + ex.getMessage());
            target.delete();
            return "备份失败: " + ex.getMessage();
        }

        int removed = prune();
        String text = "已备份到 backups/" + target.getName()
                + "（" + files + " 个配置文件" + (dbIncluded ? " + 数据库" : "")
                + "，清理旧备份 " + removed + " 份）";
        plugin.getLogger().info("(" + reason + ") " + text);
        return text;
    }

    /** 列出已有备份，最新的在前。 */
    public List<String> list() {
        File[] files = directory().listFiles((d, name) -> name.endsWith(".zip"));
        if (files == null || files.length == 0) {
            return List.of();
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        List<String> result = new ArrayList<>(files.length);
        for (File file : files) {
            result.add(file.getName() + "  " + Math.max(1L, file.length() / 1024) + " KB");
        }
        return result;
    }

    private File prepareTarget() {
        File folder = directory();
        if (!folder.exists() && !folder.mkdirs()) {
            return null;
        }
        String stamp = LocalDateTime.now().format(STAMP);
        File target = new File(folder, "backup-" + stamp + ".zip");
        int index = 2;
        while (target.exists()) {
            // 同一秒内连续备份时加序号，避免互相覆盖
            target = new File(folder, "backup-" + stamp + "-" + index + ".zip");
            index++;
        }
        return target;
    }

    /** 数据库：SQLite 用 VACUUM INTO 导出一致副本，其他后端跳过。 */
    private boolean appendDatabase(ZipOutputStream zip) throws IOException {
        File temp = new File(directory(), "db-" + System.nanoTime() + ".tmp");
        try {
            if (!plugin.storage().backupTo(temp) || !temp.exists()) {
                return false;
            }
            put(zip, "litebrawl.db", Files.readAllBytes(temp.toPath()));
            return true;
        } finally {
            if (temp.exists() && !temp.delete()) {
                temp.deleteOnExit();
            }
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        OutputStream out = zip;
        out.write(data);
        zip.closeEntry();
    }

    /** 只保留最近 N 份，返回删除份数。 */
    private int prune() {
        int keep = plugin.settings().backupKeep();
        if (keep <= 0) {
            return 0;
        }
        File[] files = directory().listFiles((d, name) -> name.endsWith(".zip"));
        if (files == null || files.length <= keep) {
            return 0;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        int removed = 0;
        for (int i = keep; i < files.length; i++) {
            if (files[i].delete()) {
                removed++;
            }
        }
        return removed;
    }
}
