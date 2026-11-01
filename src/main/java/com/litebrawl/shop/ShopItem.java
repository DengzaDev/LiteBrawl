package com.litebrawl.shop;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** config.yml 中 shop.items 下的一项商品。 */
public final class ShopItem {

    /** 特殊商品类型。 */
    public enum Special {
        NONE,
        QUIVER,
        COSMETIC
    }

    private final String key;
    private final int slot;
    private final Material material;
    private final int amount;
    private final long price;
    private final String name;
    private final List<String> lore;
    private final PotionEffectType potionEffect;
    private final int duration;
    private final int amplifier;
    private final Special special;
    private final String cosmeticId;

    public ShopItem(String key, int slot, Material material, int amount, long price,
                    String name, List<String> lore,
                    PotionEffectType potionEffect, int duration, int amplifier,
                    Special special, String cosmeticId) {
        this.key = key;
        this.slot = slot;
        this.material = material;
        this.amount = Math.max(1, amount);
        this.price = Math.max(0L, price);
        this.name = name == null || name.isBlank() ? key : name;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.potionEffect = potionEffect;
        this.duration = Math.max(1, duration);
        this.amplifier = Math.max(0, amplifier);
        this.special = special == null ? Special.NONE : special;
        this.cosmeticId = cosmeticId == null ? "" : cosmeticId;
    }

    public String key() {
        return key;
    }

    public int slot() {
        return slot;
    }

    public Material material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public long price() {
        return price;
    }

    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    public PotionEffectType potionEffect() {
        return potionEffect;
    }

    public int duration() {
        return duration;
    }

    public int amplifier() {
        return amplifier;
    }

    public Special special() {
        return special;
    }

    public String cosmeticId() {
        return cosmeticId;
    }
}
