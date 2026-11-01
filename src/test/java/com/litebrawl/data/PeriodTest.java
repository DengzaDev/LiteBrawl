package com.litebrawl.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 周期键：格式、解析、以及排行榜清理依赖的"结束日期"换算。 */
class PeriodTest {

    @Test
    @DisplayName("key() 的四种粒度前缀正确，ALL 不落库")
    void keyFormat() {
        LocalDate now = LocalDate.now();
        assertTrue(Period.DAY.key().startsWith("d:"), "日榜应为 d: 前缀");
        assertEquals("d:" + now.format(DateTimeFormatter.ISO_LOCAL_DATE), Period.DAY.key());

        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        int weekYear = now.get(WeekFields.ISO.weekBasedYear());
        assertEquals(String.format(Locale.ROOT, "w:%d-W%02d", weekYear, week), Period.WEEK.key());

        assertTrue(Period.MONTH.key().startsWith("m:"));
        assertEquals("y:" + now.getYear(), Period.YEAR.key());
        assertNull(Period.ALL.key(), "总榜走总数表，不该有周期键");
    }

    @Test
    @DisplayName("四种粒度的键互不相同")
    void keysAreDistinct() {
        String day = Period.DAY.key();
        String week = Period.WEEK.key();
        String month = Period.MONTH.key();
        String year = Period.YEAR.key();
        assertNotEquals(day, week);
        assertNotEquals(day, month);
        assertNotEquals(week, year);
        assertEquals(4, Period.tracked().length);
    }

    @Test
    @DisplayName("byId 兼容中英文与别名，未知值回退到 ALL")
    void byIdParsing() {
        assertEquals(Period.DAY, Period.byId("day"));
        assertEquals(Period.DAY, Period.byId("日"));
        assertEquals(Period.WEEK, Period.byId("WEEKLY"));
        assertEquals(Period.MONTH, Period.byId("月"));
        assertEquals(Period.YEAR, Period.byId("yearly"));
        assertEquals(Period.ALL, Period.byId("whatever"));
        assertEquals(Period.ALL, Period.byId(null));
    }

    @Test
    @DisplayName("periodEnd：各粒度的结束日期换算")
    void periodEndValues() {
        assertEquals(LocalDate.of(2026, 11, 1), SqlStorage.periodEnd("d:2026-11-01"));
        assertEquals(LocalDate.of(2026, 11, 30), SqlStorage.periodEnd("m:2026-11"));
        assertEquals(LocalDate.of(2026, 12, 31), SqlStorage.periodEnd("y:2026"));

        // 周：W44 的周一到周日
        LocalDate monday = LocalDate.of(2026, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), 44)
                .with(WeekFields.ISO.dayOfWeek(), 1);
        assertEquals(monday.plusDays(6), SqlStorage.periodEnd("w:2026-W44"));
    }

    @Test
    @DisplayName("periodEnd：脏数据返回 null（调用方据此保留、不删）")
    void periodEndRejectsGarbage() {
        assertNull(SqlStorage.periodEnd(null));
        assertNull(SqlStorage.periodEnd(""));
        assertNull(SqlStorage.periodEnd("x:2026"));
        assertNull(SqlStorage.periodEnd("d:不是日期"));
        assertNull(SqlStorage.periodEnd("w:2026W44"), "缺少 -W 分隔符应判为脏数据");
        assertNull(SqlStorage.periodEnd("y:abc"));
    }

    @Test
    @DisplayName("当年年榜不会被判过期（按结束日比较的核心用例）")
    void currentYearSurvivesRetention() {
        // 场景：今天 11 月，保留 180 天（cutoff 大约 5 月）
        LocalDate today = LocalDate.of(2026, 11, 1);
        LocalDate cutoff = today.minusDays(180);

        // 若按"起始日期"，2026 年榜（1 月 1 日）会被误删
        assertTrue(LocalDate.of(2026, 1, 1).isBefore(cutoff), "起始日期确实早于 cutoff");
        // 按"结束日期"，它必须保留
        assertFalse(SqlStorage.periodEnd("y:2026").isBefore(cutoff), "按结束日期应保留当年年榜");

        // 去年年榜应当被清掉
        assertTrue(SqlStorage.periodEnd("y:2025").isBefore(cutoff));
        // 昨天的日榜应保留，远古日榜应删
        assertFalse(SqlStorage.periodEnd("d:2026-10-31").isBefore(cutoff));
        assertTrue(SqlStorage.periodEnd("d:2020-01-01").isBefore(cutoff));
    }
}
