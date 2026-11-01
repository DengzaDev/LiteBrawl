package com.litebrawl.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 存储后端抽象：SQLite 与 MySQL 都实现这一层。 */
public interface Storage extends AutoCloseable {

    void init() throws Exception;

    Optional<Profile> find(UUID uuid);

    void save(Profile profile);

    /**
     * 取排行榜。
     *
     * @param column coins / experience / kills / deaths / assists
     */
    List<Profile> top(String column, int limit);

    /** 给某个周期的统计 +delta（column = kills / deaths）。 */
    void recordStat(java.util.UUID uuid, String name, String periodKey, String column, int delta);

    /** 按周期取榜（periodKey 见 {@link Period#key()}）。 */
    List<Profile> topPeriod(String periodKey, String column, int limit);

    /**
     * 删除过期的历史周期数据，防止周期表无限膨胀。
     *
     * @param keepDays 保留最近多少天；&lt;= 0 表示不清理
     * @return 实际删除的行数
     */
    int purgeOldPeriods(int keepDays);

    /**
     * 把数据库导出到指定文件（供备份使用）。
     *
     * @return 成功返回 true；不支持或失败返回 false
     */
    boolean backupTo(java.io.File target);

    @Override
    void close() throws Exception;

    String describe();
}
