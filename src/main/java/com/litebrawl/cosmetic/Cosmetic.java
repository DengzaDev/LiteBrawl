package com.litebrawl.cosmetic;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;

/** cosmetics.yml 里的一条外观。 */
public final class Cosmetic {

    private final String id;
    private final CosmeticType type;
    private final String name;
    private final long price;
    /** 播报模板（KILL_MESSAGE / DEATH_MESSAGE）或聊天前缀（CHAT_EFFECT） */
    private final String payload;
    /** 击杀特效用的粒子与数量 */
    private final Particle particle;
    private final int count;
    /** NAME_STYLE 用的头顶颜色（渐变做不到，只能退化成纯色） */
    private final NamedTextColor color;

    public Cosmetic(String id, CosmeticType type, String name, long price,
                    String payload, Particle particle, int count, NamedTextColor color) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.price = Math.max(0L, price);
        this.payload = payload == null ? "" : payload;
        this.particle = particle;
        this.count = Math.max(1, count);
        this.color = color;
    }

    public String id() {
        return id;
    }

    public CosmeticType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public long price() {
        return price;
    }

    public String payload() {
        return payload;
    }

    public Particle particle() {
        return particle;
    }

    public int count() {
        return count;
    }

    public NamedTextColor color() {
        return color;
    }
}
