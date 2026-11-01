package com.litebrawl.data;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

/** 排行榜的时间维度。 */
public enum Period {

    DAY("日榜"),
    WEEK("周榜"),
    MONTH("月榜"),
    YEAR("年榜"),
    ALL("总榜");

    private final String label;

    Period(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 该周期在数据库里的键；ALL 返回 null（走总数表）。
     *
     * <p>用前缀区分粒度，避免不同周期撞键：d:2026-11-01 / w:2026-W44 / m:2026-11 / y:2026</p>
     */
    public String key() {
        LocalDate now = LocalDate.now();
        return switch (this) {
            case DAY -> "d:" + now.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case WEEK -> {
                int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
                int year = now.get(WeekFields.ISO.weekBasedYear());
                yield String.format(Locale.ROOT, "w:%d-W%02d", year, week);
            }
            case MONTH -> "m:" + YearMonth.now();
            case YEAR -> "y:" + now.getYear();
            case ALL -> null;
        };
    }

    /** 需要落库的周期（不含总榜）。 */
    public static Period[] tracked() {
        return new Period[]{DAY, WEEK, MONTH, YEAR};
    }

    public static Period byId(String raw) {
        if (raw == null) {
            return ALL;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "day", "daily", "日" -> DAY;
            case "week", "weekly", "周" -> WEEK;
            case "month", "monthly", "月" -> MONTH;
            case "year", "yearly", "年" -> YEAR;
            default -> ALL;
        };
    }
}
