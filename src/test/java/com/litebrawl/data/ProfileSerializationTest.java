package com.litebrawl.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 玩家数据的序列化往返：外观与个人设置都存在单列里，写错顺序就会丢。 */
class ProfileSerializationTest {

    @Test
    @DisplayName("外观：拥有 + 装备状态能原样还原")
    void cosmeticsRoundTrip() {
        Profile profile = new Profile(UUID.randomUUID(), "测试玩家");
        profile.addCosmetic("kill_blood");
        profile.addCosmetic("title_slayer");
        profile.equipCosmetic("TITLE", "title_slayer");
        profile.equipCosmetic("KILL_MESSAGE", "kill_blood");

        String raw = profile.serializeCosmetics();

        Profile loaded = new Profile(profile.getUuid(), "测试玩家");
        loaded.loadCosmetics(raw);

        assertTrue(loaded.ownsCosmetic("kill_blood"));
        assertTrue(loaded.ownsCosmetic("title_slayer"));
        assertEquals("title_slayer", loaded.equippedCosmetic("TITLE"));
        assertEquals("kill_blood", loaded.equippedCosmetic("KILL_MESSAGE"));
        assertNull(loaded.equippedCosmetic("CHAT_EFFECT"), "没装备的类别应为 null");
    }

    @Test
    @DisplayName("外观：卸下后序列化里不再包含该类别")
    void unequipIsPersisted() {
        Profile profile = new Profile(UUID.randomUUID(), "测试玩家");
        profile.addCosmetic("title_slayer");
        profile.equipCosmetic("TITLE", "title_slayer");

        profile.equipCosmetic("TITLE", null);

        Profile loaded = new Profile(profile.getUuid(), "测试玩家");
        loaded.loadCosmetics(profile.serializeCosmetics());
        assertNull(loaded.equippedCosmetic("TITLE"));
        assertTrue(loaded.ownsCosmetic("title_slayer"), "卸下不等于失去，拥有关系要保留");
    }

    @Test
    @DisplayName("个人设置：往返一致")
    void settingsRoundTrip() {
        Profile profile = new Profile(UUID.randomUUID(), "测试玩家");
        profile.setSetting("sidebar", "true");
        profile.setSetting("layout", "archer");

        Profile loaded = new Profile(profile.getUuid(), "测试玩家");
        loaded.loadSettings(profile.serializeSettings());

        assertEquals("true", loaded.setting("sidebar", null));
        assertEquals("archer", loaded.setting("layout", null));
        assertNull(loaded.setting("tab", null), "没设置过的键应为 null");
    }

    @Test
    @DisplayName("空的列不会炸，也不会伪造数据")
    void emptyColumnsAreSafe() {
        Profile profile = new Profile(UUID.randomUUID(), "测试玩家");
        profile.loadCosmetics("");
        profile.loadSettings(null);
        assertFalse(profile.ownsCosmetic("kill_blood"));
        assertNull(profile.equippedCosmetic("TITLE"));
    }
}
