package com.litebrawl.hotfix;

import com.litebrawl.LiteBrawlPlugin;
import com.litebrawl.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 热更新加载器（v2）。
 *
 * <p>一个 .hotfix 就是 zip（jar 也行），至少包含 hotfix.yml：</p>
 *
 * <pre>
 * id: balance-tweak
 * api: 2
 * description: 运行时微调
 * enabled: true
 * requires: "1.3"            # 主版本号匹配才加载
 * load-after: [other-id]     # 依赖的其他热更新
 * rules:                     # 覆盖 config.yml 的键，立即生效
 *   bounty.base: "+10"       # 前缀 + / - 表示在现有数值上增减
 *   kit.arrow-regen-seconds: 3
 *   some.key: remove         # 删掉这个键
 * actions:                   # 加载后执行一次
 *   - "broadcast:<gold>平衡热更新已生效</gold>"
 *   - "console:say hotfix applied"
 * module: com.example.MyPatch   # 可选：新类名模块
 * </pre>
 *
 * <p>能力边界（JVM 决定的）：能热改配置/执行动作/热加载<b>新类名</b>的模块；
 * 不能替换已经加载过的类。</p>
 */
public final class HotfixLoader {

    /** 当前支持的最高格式版本。 */
    public static final int API_VERSION = 2;

    private final LiteBrawlPlugin plugin;
    private final File folder;
    private final Map<String, Loaded> loaded = new LinkedHashMap<>();

    private static final class Loaded {
        private final String id;
        private final String description;
        private final HotfixModule module;
        private final URLClassLoader loader;
        private final Map<String, Object> rules;
        private final Map<String, Object> previous;
        private final String moduleName;
        private final String source;
        private final long loadedAt;

        private Loaded(String id, String description, HotfixModule module, URLClassLoader loader,
                       Map<String, Object> rules, Map<String, Object> previous,
                       String moduleName, String source) {
            this.id = id;
            this.description = description;
            this.module = module;
            this.loader = loader;
            this.rules = rules;
            this.previous = previous;
            this.moduleName = moduleName;
            this.source = source;
            this.loadedAt = System.currentTimeMillis();
        }
    }

    public HotfixLoader(LiteBrawlPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "HotFix");
    }

    public File folder() {
        return folder;
    }

    public void ensureFolder() {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建 HotFix 目录: " + folder.getAbsolutePath());
        }
    }

    public void autoLoad() {
        ensureFolder();
        if (!plugin.settings().hotfixAutoLoad()) {
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".hotfix"));
        if (files == null || files.length == 0) {
            return;
        }
        List<String> sorted = new ArrayList<>();
        for (File file : files) {
            sorted.add(file.getName());
        }
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        for (String name : sorted) {
            String error = apply(name);
            if (error != null) {
                plugin.getLogger().warning("自动加载热更新失败 " + name + ": " + error);
            }
        }
    }

    /**
     * 应用一个热更新文件（不带扩展名也可以）。
     *
     * @return null 表示成功，否则是失败原因
     */
    public String apply(String fileName) {
        ensureFolder();
        File file = resolve(fileName);
        if (file == null) {
            return "找不到文件: " + fileName + "（应放在 " + folder.getAbsolutePath() + "）";
        }

        String id = null;
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry manifestEntry = zip.getEntry("hotfix.yml");
            if (manifestEntry == null) {
                return "不是有效的 .hotfix：缺少 hotfix.yml";
            }
            String manifestText;
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                manifestText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            FileConfiguration manifest = YamlConfiguration.loadConfiguration(new StringReader(manifestText));

            int api = manifest.getInt("api", 1);
            if (api > API_VERSION || api < 1) {
                return "hotfix.yml 的 api 是 " + api + "，当前插件支持 1.." + API_VERSION;
            }
            if (!manifest.getBoolean("enabled", true)) {
                return "这个热更新被标记为 enabled: false，已跳过";
            }

            String requires = manifest.getString("requires", "");
            if (!requires.isBlank() && !matchesVersion(requires, pluginVersion())) {
                return "这个热更新要求插件版本 " + requires + "，当前是 " + pluginVersion();
            }

            id = manifest.getString("id", stripExtension(file.getName()));
            if (loaded.containsKey(id)) {
                return "热更新 " + id + " 已经加载过了，先 /lb hotfix unload " + id;
            }
            for (String dependency : manifest.getStringList("load-after")) {
                if (!loaded.containsKey(dependency)) {
                    return "缺少依赖热更新: " + dependency + "（先加载它）";
                }
            }

            // 1) 规则（支持 +N / -N / remove）
            Map<String, Object> rules = new LinkedHashMap<>();
            Map<String, Object> previous = new LinkedHashMap<>();
            ConfigurationSection ruleSection = manifest.getConfigurationSection("rules");
            if (ruleSection != null) {
                for (String key : ruleSection.getKeys(true)) {
                    Object value = ruleSection.get(key);
                    if (value instanceof ConfigurationSection) {
                        continue;
                    }
                    previous.put(key, plugin.getConfig().get(key));
                    rules.put(key, resolveRuleValue(key, value));
                }
            }
            if (!rules.isEmpty()) {
                plugin.applyRuntimeOverrides(rules);
            }

            // 2) 动作（加载后执行一次）
            for (String action : manifest.getStringList("actions")) {
                runAction(action);
            }

            // 3) 可选模块
            HotfixModule module = null;
            URLClassLoader loader = null;
            String moduleName = manifest.getString("module");
            if (moduleName != null && !moduleName.isBlank()) {
                File cacheDir = new File(folder, ".cache/" + sanitize(id));
                int extracted = extractClasses(zip, cacheDir);
                if (extracted == 0) {
                    return "manifest 声明了 module，但压缩包里没有任何 .class 文件";
                }
                loader = new URLClassLoader(new URL[]{cacheDir.toURI().toURL()},
                        plugin.getClass().getClassLoader());
                Class<?> raw = Class.forName(moduleName, true, loader);
                if (!HotfixModule.class.isAssignableFrom(raw)) {
                    loader.close();
                    return moduleName + " 没有实现 " + HotfixModule.class.getName();
                }
                module = (HotfixModule) raw.getDeclaredConstructor().newInstance();
                module.enable(plugin, manifest);
            }

            String description = manifest.getString("description", "");
            loaded.put(id, new Loaded(id, description, module, loader, rules, previous,
                    moduleName == null ? "" : moduleName, file.getName()));
            plugin.getLogger().info("热更新已应用: " + id
                    + (rules.isEmpty() ? "" : "（覆盖 " + rules.size() + " 项配置）")
                    + (module == null ? "" : "（模块 " + moduleName + "）"));
            return null;
        } catch (Exception ex) {
            plugin.getLogger().warning("应用热更新 " + id + " 失败: " + ex);
            if (id != null) {
                loaded.remove(id);
            }
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    /** 卸载并还原被覆盖的配置。 */
    public String unload(String id) {
        Loaded entry = loaded.remove(id);
        if (entry == null) {
            return "没有加载过热更新: " + id;
        }
        try {
            if (entry.module != null) {
                entry.module.disable();
            }
            if (entry.loader != null) {
                entry.loader.close();
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("关闭热更新 ClassLoader 失败: " + ex.getMessage());
        }
        if (!entry.previous.isEmpty()) {
            for (Map.Entry<String, Object> restore : entry.previous.entrySet()) {
                plugin.getConfig().set(restore.getKey(), restore.getValue());
            }
            plugin.reloadRuntime();
        }
        return null;
    }

    /** 重新加载目录下所有已加载的热更新（改了文件不用一个个 reload）。 */
    public int reloadAll() {
        java.util.List<String> ids = new java.util.ArrayList<>(loaded.keySet());
        int count = 0;
        for (String id : ids) {
            if (reload(id) == null) {
                count++;
            }
        }
        return count;
    }

    /** 显示某个热更新的详情。 */
    public java.util.List<String> info(String id) {
        Loaded entry = loaded.get(id);
        if (entry == null) {
            return java.util.List.of("没有加载过热更新: " + id);
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("<white>" + entry.id + "</white> <dark_gray>(" + entry.source + ")</dark_gray>");
        if (entry.description != null && !entry.description.isBlank()) {
            lines.add("<gray>" + entry.description + "</gray>");
        }
        lines.add("<gray>加载于 <white>" + java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                .format(java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(entry.loadedAt), java.time.ZoneId.systemDefault()))
                + "</white></gray>");
        lines.add("<gray>模块：" + (entry.moduleName == null || entry.moduleName.isBlank()
                ? "<dark_gray>无</dark_gray>" : "<white>" + entry.moduleName + "</white>") + "</gray>");
        if (entry.rules.isEmpty()) {
            lines.add("<gray>覆盖配置：<dark_gray>无</dark_gray></gray>");
        } else {
            lines.add("<gray>覆盖配置：</gray>");
            for (var rule : entry.rules.entrySet()) {
                lines.add("<dark_gray> · </dark_gray><white>" + rule.getKey() + "</white> <gray>= "
                        + String.valueOf(rule.getValue()) + "</gray>");
            }
        }
        return lines;
    }

    /**
     * 只校验文件结构，不加载。
     *
     * @return 检查结果（每条一行）；结构错误会放在第一行
     */
    public java.util.List<String> check(String fileName) {
        File file = resolve(fileName);
        if (file == null) {
            return java.util.List.of("<red>找不到文件: " + fileName + "</red>");
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry("hotfix.yml");
            if (entry == null) {
                return java.util.List.of("<red>缺少根目录下的 hotfix.yml</red>");
            }
            String text;
            try (InputStream in = zip.getInputStream(entry)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            FileConfiguration manifest = YamlConfiguration.loadConfiguration(new StringReader(text));
            int api = manifest.getInt("api", 1);
            boolean apiOk = api >= 1 && api <= API_VERSION;
            lines.add((apiOk ? "<green>✔ api = " + api + "</green>" : "<red>✘ api = " + api + "</red>"));
            lines.add("<gray>id = <white>" + manifest.getString("id", "(未填，将用文件名)") + "</white></gray>");
            String requires = manifest.getString("requires", "");
            lines.add("<gray>requires = "
                    + (requires.isBlank() ? "<dark_gray>不限</dark_gray>"
                    : (matchesVersion(requires, pluginVersion()) ? "<green>" : "<red>") + requires + "</gray>")
                    + " <dark_gray>(当前 " + pluginVersion() + ")</dark_gray></gray>");
            ConfigurationSection rules = manifest.getConfigurationSection("rules");
            // 只数叶子键：rules 里的 "bounty.base" 会被 YAML 解析成 bounty -> base 的嵌套结构，
            // 直接用 getKeys(true).size() 会把中间节点也算进去，显示成 5 而实际只覆盖 3 项
            int ruleCount = 0;
            if (rules != null) {
                for (String key : rules.getKeys(true)) {
                    if (!(rules.get(key) instanceof ConfigurationSection)) {
                        ruleCount++;
                    }
                }
            }
            lines.add("<gray>rules = <white>" + ruleCount + "</white> 项</gray>");
            lines.add("<gray>actions = <white>" + manifest.getStringList("actions").size() + "</white> 条</gray>");
            String module = manifest.getString("module");
            if (module != null && !module.isBlank()) {
                int classes = 0;
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    if (entries.nextElement().getName().endsWith(".class")) {
                        classes++;
                    }
                }
                lines.add("<gray>module = <white>" + module + "</white> <dark_gray>(" + classes
                        + " 个 class)</dark_gray></gray>");
                if (classes == 0) {
                    lines.add("<red>声明了 module 但包里没有 .class</red>");
                }
            }
        } catch (Exception ex) {
            return java.util.List.of("<red>读取失败: " + ex.getMessage() + "</red>");
        }
        return lines;
    }

    /** 把某个热更新当前覆盖的配置导出成一个新的 .hotfix，方便固化下来。 */
    public String export(String id) {
        Loaded entry = loaded.get(id);
        if (entry == null) {
            return "没有加载过热更新: " + id;
        }
        if (entry.rules.isEmpty()) {
            return "这个热更新没有任何配置覆盖，没什么可导出的";
        }
        File outDir = new File(folder, "exports");
        if (!outDir.exists() && !outDir.mkdirs()) {
            return "无法创建导出目录: " + outDir.getAbsolutePath();
        }
        File target = new File(outDir, id + "-export.hotfix");
        StringBuilder yaml = new StringBuilder();
        yaml.append("id: ").append(id).append("-export\n");
        yaml.append("api: ").append(API_VERSION).append('\n');
        yaml.append("description: 由 /lb hotfix export 导出\n");
        yaml.append("rules:\n");
        for (var rule : entry.rules.entrySet()) {
            yaml.append("  ").append(rule.getKey()).append(": ")
                    .append(formatYamlValue(rule.getValue())).append('\n');
        }
        try (var zipOut = new java.util.zip.ZipOutputStream(Files.newOutputStream(target.toPath()))) {
            zipOut.putNextEntry(new ZipEntry("hotfix.yml"));
            zipOut.write(yaml.toString().getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
        } catch (IOException ex) {
            return "导出失败: " + ex.getMessage();
        }
        return null;
    }

    private static String formatYamlValue(Object value) {
        if (value == null) {
            return "remove";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return '"' + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /** 卸载后重新加载同一个文件（改完 .hotfix 不用重启）。 */
    public String reload(String id) {
        Loaded entry = loaded.get(id);
        if (entry == null) {
            return apply(id);
        }
        String error = unload(id);
        if (error != null) {
            return error;
        }
        return apply(id);
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Loaded entry : loaded.values()) {
            StringBuilder sb = new StringBuilder(entry.id);
            if (entry.description != null && !entry.description.isBlank()) {
                sb.append(" — ").append(entry.description);
            }
            sb.append(" <dark_gray>(规则 ").append(entry.rules.size()).append(" 项");
            if (entry.module != null) {
                sb.append("，含模块");
            }
            sb.append(")</dark_gray>");
            lines.add(sb.toString());
        }
        return lines;
    }

    public List<String> loadedIds() {
        return new ArrayList<>(loaded.keySet());
    }

    public void unloadAll() {
        for (String id : new ArrayList<>(loaded.keySet())) {
            unload(id);
        }
    }

    // ---------------- 规则与动作 ----------------

    /**
     * 支持三种写法：
     * <pre>
     * key: 10         覆盖
     * key: "+10"      在当前值上加 10
     * key: "-5"       在当前值上减 5
     * key: remove     删除这个键
     * </pre>
     */
    private Object resolveRuleValue(String key, Object value) {
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.equalsIgnoreCase("remove")) {
                return null;
            }
            if (trimmed.length() > 1 && (trimmed.startsWith("+") || trimmed.startsWith("-"))) {
                String number = trimmed.substring(1);
                if (isNumber(number)) {
                    double current = plugin.getConfig().getDouble(key, 0.0D);
                    double delta = Double.parseDouble(number);
                    double result = trimmed.startsWith("+") ? current + delta : current - delta;
                    return isIntegral(result) ? (Object) (long) result : (Object) result;
                }
            }
        }
        return value;
    }

    private void runAction(String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        String trimmed = action.trim();
        if (trimmed.startsWith("console:")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed.substring("console:".length()).trim());
            return;
        }
        if (trimmed.startsWith("broadcast:")) {
            var component = Msg.mini(trimmed.substring("broadcast:".length()).trim());
            Bukkit.getConsoleSender().sendMessage(component);
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(component);
            }
            return;
        }
        if (trimmed.startsWith("title:")) {
            // title:<主标题>|<副标题>
            String[] parts = trimmed.substring("title:".length()).split("\\|", 2);
            var main = Msg.mini(parts[0].trim());
            var sub = parts.length > 1 ? Msg.mini(parts[1].trim()) : net.kyori.adventure.text.Component.empty();
            for (var player : Bukkit.getOnlinePlayers()) {
                player.showTitle(net.kyori.adventure.title.Title.title(main, sub));
            }
            return;
        }
        if (trimmed.startsWith("sound:")) {
            String raw = trimmed.substring("sound:".length()).trim();
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
                for (var player : Bukkit.getOnlinePlayers()) {
                    player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("无效的音效名: " + raw);
            }
            return;
        }
        plugin.getLogger().warning("未知的热更新动作: " + trimmed);
    }

    private static boolean isNumber(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isIntegral(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value);
    }

    private String pluginVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** 只看主版本号：1.3 与 1.3.0 视为匹配。 */
    private static boolean matchesVersion(String required, String current) {
        String[] want = required.split("\\.");
        String[] have = current.split("\\.");
        int compare = Math.min(Math.min(want.length, have.length), 2);
        for (int i = 0; i < compare; i++) {
            if (!want[i].trim().equals(have[i].trim())) {
                return false;
            }
        }
        return compare > 0;
    }

    // ---------------- 内部 ----------------

    private File resolve(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        File direct = new File(folder, fileName);
        if (direct.isFile()) {
            return direct;
        }
        File withExt = new File(folder, fileName + ".hotfix");
        return withExt.isFile() ? withExt : null;
    }

    private int extractClasses(ZipFile zip, File cacheDir) throws IOException {
        Path root = cacheDir.toPath();
        Files.createDirectories(root);
        int count = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.endsWith(".class") || entry.isDirectory()) {
                continue;
            }
            String relative = name.startsWith("classes/") ? name.substring("classes/".length()) : name;
            Path target = root.resolve(relative);
            Files.createDirectories(target.getParent());
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            count++;
        }
        return count;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
