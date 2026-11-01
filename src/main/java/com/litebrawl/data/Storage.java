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

    @Override
    void close() throws Exception;

    String describe();
}
