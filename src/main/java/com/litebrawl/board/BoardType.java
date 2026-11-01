package com.litebrawl.board;

import com.litebrawl.data.Period;

import java.util.List;
import java.util.Locale;

/** 可展示的榜单类型（含时间维度）。 */
public enum BoardType {

    KILLS("kills", "击杀总榜", "kills", Period.ALL),
    KILLS_DAY("kills_day", "击杀日榜", "kills", Period.DAY),
    KILLS_WEEK("kills_week", "击杀周榜", "kills", Period.WEEK),
    KILLS_MONTH("kills_month", "击杀月榜", "kills", Period.MONTH),
    KILLS_YEAR("kills_year", "击杀年榜", "kills", Period.YEAR),

    DEATHS("deaths", "死亡总榜", "deaths", Period.ALL),
    DEATHS_DAY("deaths_day", "死亡日榜", "deaths", Period.DAY),
    DEATHS_WEEK("deaths_week", "死亡周榜", "deaths", Period.WEEK),
    DEATHS_MONTH("deaths_month", "死亡月榜", "deaths", Period.MONTH),
    DEATHS_YEAR("deaths_year", "死亡年榜", "deaths", Period.YEAR),

    LEVEL("level", "等级榜", "experience", Period.ALL),
    COINS("coins", "硬币榜", "coins", Period.ALL);

    private final String id;
    private final String defaultLabel;
    private final String column;
    private final Period period;

    BoardType(String id, String defaultLabel, String column, Period period) {
        this.id = id;
        this.defaultLabel = defaultLabel;
        this.column = column;
        this.period = period;
    }

    public String id() {
        return id;
    }

    public String defaultLabel() {
        return defaultLabel;
    }

    /** 对应的 SQL 排序列（白名单在 SqlStorage 里再次校验）。 */
    public String column() {
        return column;
    }

    public Period period() {
        return period;
    }

    /** 榜单家族：击杀/死亡有 5 个时间维度，等级/硬币只有总榜。 */
    public String family() {
        return switch (this) {
            case KILLS, KILLS_DAY, KILLS_WEEK, KILLS_MONTH, KILLS_YEAR -> "kills";
            case DEATHS, DEATHS_DAY, DEATHS_WEEK, DEATHS_MONTH, DEATHS_YEAR -> "deaths";
            case LEVEL -> "level";
            case COINS -> "coins";
        };
    }

    /** 点击时循环的顺序（自己所在的家族）。 */
    public List<BoardType> cycle() {
        return switch (family()) {
            case "kills" -> List.of(KILLS, KILLS_DAY, KILLS_WEEK, KILLS_MONTH, KILLS_YEAR);
            case "deaths" -> List.of(DEATHS, DEATHS_DAY, DEATHS_WEEK, DEATHS_MONTH, DEATHS_YEAR);
            default -> List.of(this);
        };
    }

    public static BoardType byId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (BoardType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
