# 参与开发

先谢谢你看这份文档。这是一个单服小游戏插件，代码量不大（约 1.1 万行 Java），
认领一个 bug 或一项小功能都不需要先读完所有代码。

## 环境

| 项 | 要求 |
|---|---|
| JDK | 21 |
| Maven | 3.8+ |
| 服务端（联调用） | Paper 1.21.1 |

```bash
git clone https://github.com/DengzaDev/LiteBrawl.git
cd LiteBrawl
mvn -B package          # 产物在 target/
mvn -B test             # 只跑单元测试（不需要服务端）
```

## 代码结构

```
src/main/java/com/litebrawl/
├── LiteBrawlPlugin.java     装配、生命周期、reload
├── Settings.java            全部配置读取（一个类集中，改配置项先看这里）
├── game/                    进场、装备、死亡结算、即死方块、箭袋、赏金
├── combat/                  伤害、反刷分、战斗标记
├── data/                    存储抽象 + SQLite/MySQL 实现、周期键
├── board/ display/ ui/      排行榜、铭牌、Tab、记分板
├── team/ cosmetic/ settings/组队、外观、个人设置界面
├── map/                     地图注册、出生点扫描、轮换
├── bot/ npc/ boost/         陪练假人、功能 NPC、双倍加成
├── web/                     内置 HTTP 控制台（含玩家面板）
├── backup/                  完整备份（配置 + 数据库）
└── hotfix/                  热更新加载器
```

约定：

- 所有玩家可见文本用 **MiniMessage**（`<gold>` 这类），不要用 `§` 颜色码
- 配置项要在 `config.yml` 里写**中文注释**，读取统一走 `Settings.java`
- 数据库列顺序在 `SqlStorage.COLUMNS` 里，**绑定参数必须严格按同一顺序**，否则数据会错位
- 主线程不要做数据库 IO，用 `runTaskAsynchronously`

## 测试

单元测试只覆盖**不依赖服务端运行时的纯逻辑**（周期键、序列化往返、占位符替换等），
这样不用起服务器也能跑：

```bash
mvn -B test
```

涉及 Bukkit 运行时的行为（GUI、事件、实体）没法用单测覆盖，改动这类代码请在 PR 里
写清楚你**怎么手工验证的**。

`tools/` 下还有一批离线校验脚本（`YamlSmoke`、`StorageCheck`、`WebCheck`、`ChatFormatCheck`），
用法见 [README](README.md#开发与验证脚本)。

## 提 PR 之前

1. `mvn -B package` 必须通过（含测试）
2. **不要把 `test-server/`、`target/`、数据库文件、web.token 提交上来**（`.gitignore` 已挡住，但自己确认一下）
3. 改了配置项要保证旧配置能继续用（新键读不到时走代码默认值）
4. PR 描述里写清「怎么验证的」，不要只写「已测试」

## 本地联调

测试服目录不进仓库。自己准备一份 Paper 1.21.1：

```
# 把 target/LiteBrawl-x.y.z-Release.jar 放进 plugins/
# 改完代码重新 package 时，注意 Paper 的 remap 缓存：
# 同名 jar 会被复用旧缓存，改文件名或先删 plugins/.paper-remapped/
```

## 提交信息

没有强制格式，说清改了什么就行。建议一句话说明动机：

```
修复热更新 check 与 list 的规则计数不一致
周期榜清理改为按周期结束时间判断，避免误删当年年榜
```

## 关于国际化

目前界面与指令反馈是**中文硬编码**，`messages.yml` 只覆盖播报文案。
完整的 i18n（抽语言包）是一项大改动，暂时没排期；
如果你愿意做，欢迎先开 issue 讨论方案，不要一份 PR 直接重写全部字符串。
