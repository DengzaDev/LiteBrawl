package com.litebrawl.hotfix;

import com.litebrawl.LiteBrawlPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 热更新模块的入口。
 *
 * <p>模块类必须实现这个接口、并且是一个**从未被加载过的新类名**，
 * 这样才能由独立 ClassLoader 载入而不需要重启服务器。</p>
 */
public interface HotfixModule {

    void enable(LiteBrawlPlugin plugin, FileConfiguration manifest) throws Exception;

    default void disable() {
    }
}
