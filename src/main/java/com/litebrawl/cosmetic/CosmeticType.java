package com.litebrawl.cosmetic;

/** 可购买的外观类别。 */
public enum CosmeticType {

    KILL_MESSAGE("击杀播报"),
    DEATH_MESSAGE("死亡播报"),
    KILL_EFFECT("击杀特效"),
    CHAT_EFFECT("聊天特效"),
    NAME_STYLE("名字样式"),
    TITLE("称号");

    private final String label;

    CosmeticType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static CosmeticType byName(String raw) {
        if (raw == null) {
            return null;
        }
        for (CosmeticType type : values()) {
            if (type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        return null;
    }
}
