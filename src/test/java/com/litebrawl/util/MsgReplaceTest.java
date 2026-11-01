package com.litebrawl.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 占位符替换：所有播报 / 提示都走这里，替换错就会把 %xxx% 直接甩给玩家。 */
class MsgReplaceTest {

    @Test
    @DisplayName("成对的 key/value 会被替换")
    void replacesPairs() {
        assertEquals("张三 击杀了 李四",
                Msg.replace("%killer% 击杀了 %victim%",
                        "killer", "张三", "victim", "李四"));
    }

    @Test
    @DisplayName("值为 null 时替换成空串，而不是留下 %key% 或 null")
    void nullValueBecomesEmpty() {
        assertEquals("击杀 ", Msg.replace("击杀 %victim%", "victim", null));
    }

    @Test
    @DisplayName("没提供对应占位符时原样保留")
    void unknownPlaceholderStays() {
        assertEquals("连杀 %streak% 人", Msg.replace("连杀 %streak% 人", "killer", "张三"));
    }

    @Test
    @DisplayName("同一个占位符出现多次都会被替换")
    void replacesEveryOccurrence() {
        assertEquals("A-A-A", Msg.replace("%p%-%p%-%p%", "p", "A"));
    }

    @Test
    @DisplayName("参数个数为奇数时，多余的 key 被忽略、原样保留占位符（不越界）")
    void oddArgumentsAreSafe() {
        assertEquals("x-%a%", Msg.replace("x-%a%", "a"));
    }

    @Test
    @DisplayName("null 输入返回空串")
    void nullInput() {
        assertEquals("", Msg.replace(null, "a", "b"));
    }

    @Test
    @DisplayName("值里本身带 % 不会导致二次替换")
    void valueWithPercentIsSafe() {
        assertEquals("100% 完成", Msg.replace("%v% 完成", "v", "100%"));
    }
}
