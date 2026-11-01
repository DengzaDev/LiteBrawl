# LiteBrawl

<img src="assets/icon.png" alt="LiteBrawl" width="160" align="right" />

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/DengzaDev/LiteBrawl?label=release)](https://github.com/DengzaDev/LiteBrawl/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21.1-blue.svg)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)

小型「随机出生点大乱斗」插件，为 **Paper 1.21.1 / Java 21** 编写。

玩家点一下就开始，随机丢到地图里一颗绿宝石块上，人人同一套铁装、4 支会自动补充的箭，
踩到竹马赛克直接死。击杀攒硬币和经验，连杀会被挂赏金，攒够了去商店买药水和外观，
地图到点自动轮换。自带侧边栏、Tab、全息排行榜、陪练假人、功能 NPC，
以及一个跑在 8787 端口的本地网页控制台——改配置、看数据、玩家查战绩都不用进游戏。

> 单服设计，不依赖任何第三方插件。SQLite 开箱即用，也支持 MySQL。

## 目录

- [特性](#特性)
- [快速开始](#快速开始)
- [玩法规则](#玩法规则)
- [命令](#命令)
- [配置速查](#配置速查)
- [侧边栏与 Tab](#侧边栏与-tab)
- [网页控制台](#网页控制台)
- [热更新](#热更新)
- [加成与反刷分](#加成与反刷分)
- [数据存储](#数据存储)
- [跨服](#跨服)
- [已知限制](#已知限制)
- [开发与验证脚本](#开发与验证脚本)
- [许可证](#许可证)

## 特性

**玩法**

- 随机出生点：读地图里**已存在**的绿宝石块作为出生点，插件不放置任何方块
- 固定装备：铁剑 + 力量 I 的弓 + 4 箭 + 64 牛排 + 三件铁甲，全部不可损坏
- 箭袋：射出后 5 秒补回一支，上限 4 支；满袋时禁止拾取，落地 3 秒清除
- 即死方块：踩到竹马赛克（含台阶/楼梯变体）立刻死亡，站桩不动也会判定
- 全套保护：禁破坏、禁放置、禁流动、岩浆免伤、除玩家外不刷怪
- 攻击无冷却（1.8 连点手感）、击退倍率可调、战斗标记期间禁止回大厅

**经济与排行**

- 击杀 +5 硬币 +5 经验 +1 金苹果，助杀 +2/+2，死亡不扣钱
- 等级由累计经验纯函数换算，不另外存一份，避免数值漂移
- 连杀悬赏：连杀 5 次开始被挂赏金，被别人拿下时全额转给击杀者
- 反刷分：反复杀同一个人收益按 `0.5^n` 递减，最低 10%
- 全息排行榜（TextDisplay）左右键切换日/周/月/年/总榜，也可 `/board` 开面板

**社交与外观**

- 组队：邀请制、上限 8 人、队友互相免伤（含箭矢），队长退出自动转交
- 6 类外观：击杀播报 / 死亡播报 / 击杀特效 / 聊天前缀 / 名字样式 / 称号
- 头顶铭牌 `[Lv.n]名字[称号]` + 血量条，Tab 同步显示

**运营工具**

- 多地图轮换：ORDER 按顺序、VOTE 票选，换图全员回大厅
- 陪练假人：人数不够自动上线，大师会追人、新手打不出伤害，可静音
- 功能 NPC：商店 / 外观 / 排行榜，右键打开对应界面，重启自动重生
- 双倍加成：手动开、每天定时开、或由连杀/在线人数触发
- 热更新：不重启就能改配置、执行动作、加载新类名的模块

**网页控制台**

- 管理端：开服向导、约 60 项表单、带高亮的 YAML 编辑器、多文件编辑、自动保存、
  数据库只读浏览、实时仪表盘
- 玩家端：游戏里 `/link` 拿绑定码，网页上查自己的战绩、换外观

## 快速开始

### 环境要求

| 项 | 要求 |
|---|---|
| 服务端 | Paper 1.21.1 |
| Java | 21 |
| 编译 | JDK 21 + Maven |
| 数据库 | SQLite（内置，默认）或 MySQL（可选） |

### 下载

不想自己编译的话，直接从 [Releases](https://github.com/DengzaDev/LiteBrawl/releases/latest)
下载 `LiteBrawl-x.y.z-Release.jar`，丢进服务器的 `plugins/` 目录即可。

### 编译

需要 **JDK 21** 和 Maven：

```bash
git clone https://github.com/DengzaDev/LiteBrawl.git
cd LiteBrawl
mvn -B package
```

产物：`target/LiteBrawl-1.0.1-Release.jar`。
SQLite 与 MySQL 驱动已经 shade 进包里，服务器上不需要额外装依赖。

### 部署

1. 把 jar 放进服务器的 `plugins/` 目录
2. 重启服务器，生成 `plugins/LiteBrawl/config.yml`
3. 改完配置执行 `/litebrawl reload`（切换存储类型需要重启）

首次启动会自动生成 `web.token` 并把控制台地址打印出来。

### 三步开图

地图必须是服务器目录里**真实存在的世界文件夹**，插件不会凭空生成。

```
/maps create arena1 第一竞技场      # 目录名 arena1，显示名「第一竞技场」
/map setspawn                       # 站到出生点，把脚下设为出生点
/map scan                           # 扫描地图里已有的绿宝石块作为出生点
```

`/map scan` 只读不写。扫描中心是出生点，范围 `spawn.scan-radius`（默认 160 格），
高度默认覆盖 -64 ~ 320，天空图和洞穴图也能扫到。地图大时临时加大半径：

```
/map scan 400
```

结果缓存在 `maps.yml`。之后玩家进服会拿到大厅物品，右键下界之星（8 号槽）即可进战场。

## 玩法规则

| 规则 | 实现 |
|---|---|
| 右键快捷栏物品开始 | 进服自动发放「开始游戏」，右键或 `/start` |
| 随机传送到绿宝石块上方 | 从 `maps.yml` 缓存的出生点里随机取，高度 = 方块 Y + `spawn.y-offset` |
| 固定装备 | `kit` 段配置，进战场自动穿戴 |
| 装备不掉耐久 | `setUnbreakable(true)` + 隐藏标记 |
| 箭 5 秒补 1、上限 4 | 箭是真实物品，射出后按玩家计时补回；满袋禁止拾取 |
| 踩竹马赛克即死 | 每 0.25 秒轮询脚下与脚部方块，对假人也生效；创造模式会被切回生存再判定 |
| 禁破坏 / 放置 | 取消 `BlockBreakEvent` / `BlockPlaceEvent`，`litebrawl.bypass.build` 可绕过 |
| 岩浆不掉血 | `LAVA / FIRE / FIRE_TICK / HOT_FLOOR` 免伤 |
| 不刷怪 | 取消所有非玩家 `LivingEntity` 生成（盔甲架与 TextDisplay 豁免），启动时清理存档里的旧怪 |
| 液体不流动 | 取消 `BlockFromToEvent`，火焰蔓延与点燃一并取消 |
| 击杀奖励 | 死亡结算时给击杀者 `rewards.kill` |
| 助杀判定 | `assist-window-seconds` 内造成 ≥ `assist-min-damage` 伤害且不是击杀者 |
| 死亡不扣硬币 | 死亡只累加 `deaths` |
| 死亡回出生点 | 重设重生点并清空状态，需要重新点「开始」 |

## 命令

### 玩家

| 命令 | 别名 | 说明 |
|---|---|---|
| `/start` | | 进入战场 |
| `/shop` | | 打开商店（只能在大厅用） |
| `/stats [玩家]` | | 查看战绩 |
| `/top <kills\|level\|coins\|deaths>` | `/leaderboard`、`/lbboard` | 聊天版排行榜 |
| `/board` | `/boards`、`/rank` | 排行榜面板，左右键切换时间维度 |
| `/sb` | | 开关侧边栏记分板 |
| `/team [create\|accept\|leave\|invite <玩家>]` | | 不带参数打开组队界面 |
| `/cosmetic [list\|equip <id>\|off <类型>]` | `/cos` | 管理已购买的外观 |
| `/spawn` | | 返回出生点（战斗中不可用） |
| `/vote [地图id]` | `/mapvote` | 给下一张地图投票 |
| `/play [服务器名]` | `/hub` | 通过代理跳服（需配置 `proxy`） |
| `/link` | `/bind` | 生成网页面板绑定码 |

### 管理员

需要 `litebrawl.admin`（默认 op）。`/litebrawl` 可以简写为 `/lb`。

| 命令 | 说明 |
|---|---|
| `/maps create <目录名> <显示名>` | 把服务器里的世界注册为地图 |
| `/maps list` / `/maps select <id>` | 列出 / 切换当前地图 |
| `/maps delete <id>` | 删除地图注册（**不删世界文件**） |
| `/map setspawn` | 把脚下设为当前地图出生点 |
| `/map scan [半径]` | 重新扫描出生方块 |
| `/map info` | 当前地图信息 |
| `/map bar set\|remove\|list <榜单>` | 全息排行榜 |
| `/map next` | 立刻切到下一张地图 |
| `/litebrawl reload` | 重载配置 |
| `/litebrawl coins give\|take\|set <玩家> <数量>` | 调整硬币 |
| `/litebrawl exp give\|take\|set <玩家> <数量>` | 调整经验 |
| `/litebrawl status` | 一次性看清服务端状态（排查用） |
| `/litebrawl link <玩家名>` | 替玩家代发网页绑定码（离线也行） |
| `/lb bot spawn [数量]` / `clear` / `count` | 手动控制陪练假人 |
| `/lb npc set <shop\|cosmetic\|board>` | 在脚下创建功能 NPC |
| `/lb npc move` / `remove` / `name <名字>` / `skin <玩家>` / `list` | 操作最近的 NPC |
| `/lb boost start <coins\|exp\|both> <秒> [倍率] [玩家,...]` | 开启加成 |
| `/lb boost stop <coins\|exp\|both>` / `info` | 关闭 / 查看加成 |
| `/lb boost schedule add\|list\|remove` | 每天定时的加成 |
| `/lb hotfix ...` | 热更新，见[下文](#热更新) |

## 配置速查

配置分三个文件，改完都能用 `/litebrawl reload` 生效：

| 文件 | 内容 |
|---|---|
| `config.yml` | 全部玩法与系统参数，每一段都有中文注释 |
| `messages.yml` | 击杀 / 死亡 / 连杀播报文案与武器名 |
| `maps.yml`、`boards.yml`、`npcs.yml`、`boost.yml` | 由命令生成的数据文件 |

### 初始装备 `kit`

默认不戴头盔，铁甲 + 铁剑 + 力量 I 的弓 + 4 箭 + 64 熟牛排，全部不可损坏。

```yaml
kit:
  helmet: NONE
  arrows: 4
  arrow-regen-seconds: 5   # 每 5 秒补一支
  max-arrow-bonus: 2       # 商店箭袋扩容最多加到 4+2=6
  unbreakable: true
```

`kit-layouts` 提供 `default` / `archer` / `melee` 三种物品栏排列，玩家在个人设置界面里切换。

### 出生点与即死方块 `spawn`

```yaml
spawn:
  block: EMERALD_BLOCK     # 出生方块
  lethal-blocks: [BAMBOO_MOSAIC, BAMBOO_MOSAIC_SLAB, BAMBOO_MOSAIC_STAIRS]
  lethal-affects-bots: true
  y-offset: 2
  scan-radius: 160
  scan-y-min: -64
  scan-y-max: 320
```

写基础方块名时，它的台阶 / 楼梯变体会自动一起生效。

### 奖励与悬赏 `rewards` / `bounty`

```yaml
rewards:
  kill:   { coins: 5, exp: 5, golden-apples: 1 }
  assist: { coins: 2, exp: 2 }
  assist-window-seconds: 8
  assist-min-damage: 4.0

bounty:
  enabled: true
  min-killstreak: 5   # 连杀 5 次后开始被挂赏金
  base: 20            # 第一次悬赏 20
  per-kill: 10        # 之后每多杀一人 +10
```

赏金存在内存里，重启清零——刻意不落库，避免悬赏变成长期负担。掉线不清空（防止重连躲悬赏），
但下线期间不能被击杀。

### 地图轮换 `rotation`

```yaml
rotation:
  enabled: true
  seconds: 600
  announce-seconds: 30
  mode: ORDER   # ORDER 按注册顺序；VOTE 切到票数最高的地图
```

换图时所有在线玩家回到新地图出生点并回到大厅状态，需要重新点「开始」，
避免带着上一张图的装备被传送。注册少于 2 张图时不启用。

### 等级与名字 `level` / `display`

```yaml
level:
  base-exp: 100     # 升到 2 级需要 100 经验
  growth: 1.35      # 每级需求 = base-exp * growth^(等级-1)
  max-level: 50

display:
  nametag-level: true          # 头顶显示 [Lv.n]
  title-suffix: " <yellow>[%title%]</yellow>"
  health:
    enabled: true              # 头顶与 Tab 的血量条
    max-hearts: 10
```

等级不入库，由累计经验换算，避免两处数值漂移。

### 商店 `shop`

14 件默认商品，价格按「一次击杀 = 5 硬币」定的：

| 道具 | 价格 | 说明 |
|---|---|---|
| 金苹果 ×1 | 5 | 回血 + 抗性 |
| 迅捷药水 | 8 | 速度 I · 20 秒 |
| 治疗药水 | 10 | 立即回 4 颗心 |
| 末影珍珠 ×1 | 15 | 位移，追击 / 逃命 |
| 箭袋扩容 | 20 | 箭上限 +1，**死亡后失效** |
| 力量药水 | 18 | 力量 I · 8 秒 |
| 金色聊天前缀 | 40 | 聊天名前的金色标记 |
| 灵魂播报 | 40 | 阵亡文案 |
| 金色名字 | 50 | Tab 与聊天名字变金色 |
| 撕裂播报 | 60 | 击杀文案 |
| 富可敌国（称号） | 60 | 名字后显示 [富可敌国] |
| 不死图腾特效 | 80 | 击杀时绽放图腾粒子 |
| 杀戮之王（称号） | 80 | 名字后显示 [杀戮之王] |
| 渐变名字 | 120 | Tab 与聊天的渐变名字 |

加新道具不用改代码，往 `shop.items` 里加一段即可：

```yaml
shop:
  items:
    my_item:
      slot: 17
      material: GOLDEN_APPLE
      amount: 1
      price: 12
      name: "<gold>某个道具</gold>"
      lore: [ "<gray>价格：<gold>%price%</gold> 硬币</gray>" ]
      # potion-effect: SPEED / duration: 400 / amplifier: 0
```

**商店是「先下单、下场发放」**：大厅买的东西不进背包，等下次点「开始」时和装备一起发，
避免大厅囤货。

### 外观 `cosmetics`

6 类外观，定义在 `cosmetics.items`，用 `special: cosmetic` 在商店出售：

| 类型 | 作用 | 默认 id |
|---|---|---|
| `KILL_MESSAGE` | 击杀播报换成自己的文案 | `kill_blood` |
| `DEATH_MESSAGE` | 阵亡播报换成自己的文案 | `death_soul` |
| `KILL_EFFECT` | 击杀时放粒子 + 音效 | `effect_totem` |
| `CHAT_EFFECT` | 聊天名字前的专属前缀 | `chat_gold` |
| `NAME_STYLE` | Tab / 聊天名字样式（头顶只能纯色） | `name_gold`、`name_rainbow` |
| `TITLE` | 名字后的 `[称号]` | `title_slayer`、`title_tycoon` |

已购买的外观落在玩家数据的 `cosmetics` 列，换服、重登都还在。

### 复活保护 `protection`

```yaml
protection:
  seconds: 3          # 进场后 3 秒免伤，0 = 关闭
  block-dealing: true # 保护期内也打不出伤害，防止贴脸输出
```

侧边栏可用 `%protection%` 显示剩余秒数。

### 战斗手感 `combat`

```yaml
combat:
  no-attack-cooldown: true
  attack-speed: 1024.0        # 拉到很高 = 没有 1.9+ 攻击间隔
  knockback-multiplier: 1.0   # 0.5 减半、1.5 加强
  tag-seconds: 15             # 战斗标记时长，期间禁止 /spawn、回大厅、开商店
  arrow-ground-seconds: 3     # 落地箭矢清除时间
```

### 假人 `bots`

```yaml
bots:
  enabled: true
  auto: true
  min-players: 3      # 在线少于 3 人时自动上假人
  count: 4
  master-percent: 50  # 一半大师、一半新手
  master-damage: 4.0  # 新手固定 0
  silent: true
  respawn: true
  respawn-seconds: 5
```

假人是带玩家皮肤的僵尸，装备与真人一致。大师会主动追人；新手不设目标、伤害被取消。
击杀奖励：大师 +2/+2，新手 +1/+1。

### 组队与大厅物品 `team` / `lobby-items`

| 槽位 | 物品 | 作用 |
|---|---|---|
| 8 | 下界之星 | 开始游戏 |
| 7 | 指南针 | 组队界面 |
| 6 | 箱子 | 物品商店（仅大厅可用） |
| 5 | 绿宝石 | 外观商城 |
| 4 | 红石 | 返回大厅 / 跳服 |
| 3 | 比较器 | 设置（记分板、Tab、战斗提示） |
| 2 | 命名牌 | 个人设置（物品栏排列、假人开关、外观切换） |

## 侧边栏与 Tab

侧边栏每秒刷新，`/sb` 自己开关。每行都在 `scoreboard.lines` 里，支持这些占位符：

| 占位符 | 含义 | 占位符 | 含义 |
|---|---|---|---|
| `%player%` | 玩家名 | `%alive%` | 在战场人数 |
| `%status%` | 在场 / 待机 | `%health%` | 当前血量 |
| `%level%` `%exp%` | 等级 / 累计经验 | `%arrows%` `%arrows_max%` | 箭袋 |
| `%coins%` | 硬币 | `%bounty%` `%streak%` | 悬赏 / 连杀 |
| `%kills%` `%deaths%` `%assists%` `%kd%` | 战绩 | `%protection%` | 保护剩余秒数 |

最多 15 行，`" "`（一个空格）就是空行，`footer` 会接在 `lines` 后面。
Tab 列表支持 `%player% %online% %map% %countdown% %alive%`。

> 实现细节：每行用一个纯颜色码字符串当 entry（客户端看不见），真实文字放在 Team 前缀里，
> 刷新时只改前缀和分值，不会重建记分板、也不会闪。

## 网页控制台

插件内置一个 HTTP 服务（`com.sun.net.httpserver`，无第三方依赖），默认只监听 `127.0.0.1:8787`。

### 管理端

首次启动生成 `web.token`，控制台会打印完整地址并尝试自动打开浏览器：

```
http://127.0.0.1:8787/?token=<web.token>
```

| 功能 | 说明 |
|---|---|
| 开服向导 | 5 步填完即可开玩，每步都能回来改 |
| 表单分区 | 约 60 个常用配置项，不用手写 YAML |
| YAML 编辑器 | 行号、语法高亮、搜索、大纲 |
| 多文件编辑 | `config.yml` / `messages.yml` / `maps.yml` / `boards.yml` / `boost.yml` / `npcs.yml` |
| 保存保护 | 保存前先校验 YAML，写坏不会覆盖；保存后自动触发对应重载 |
| 自动保存 | 勾上后停手 1.2 秒自动写盘 |
| 数据库浏览 | 按击杀 / 硬币 / 经验 / 死亡 / 助杀翻排行（列名白名单，不接受任意 SQL） |
| 实时状态 | 在线人数、当前地图、假人、TPS、生效中的加成 |

所有 `/api/*` 都要 token。要外网访问就把 `web.bind` 改成 `0.0.0.0`，并自己做防火墙。

### 玩家面板

玩家在游戏里 `/link` 拿到 6 位绑定码（5 分钟有效），打开 `http://<服务器>:8787/me` 输码，
换一个 12 小时有效的玩家 token，之后可以查自己的战绩、装备或卸下已拥有的外观。

管理员用 `/litebrawl link <玩家名>` 可以替玩家代发码，适合「玩家说绑不上」的排查。

> 玩家 token 与管理端 token 是两套东西：玩家只能读自己的数据、只能动自己的外观，
> 碰不到服务器配置和数据库。

## 热更新

`plugins/LiteBrawl/HotFix/*.hotfix`（其实是 zip）可以在不重启的情况下改变行为。v2 格式：

```yaml
id: balance-tweak
api: 2
description: 平衡性调整
enabled: true
requires: "1.4"              # 主版本不匹配就拒绝加载
load-after: [other-id]       # 依赖
rules:                       # 覆盖 config.yml 的键，立刻生效
  bounty.base: "+15"         # +N / -N 在现有值上增减
  kit.arrow-regen-seconds: 3 # 直接写值 = 覆盖
  some.old.key: remove       # 删掉这个键
actions:                     # 加载后执行一次
  - "console:say hotfix applied"
  - "broadcast:<gold>热更新已生效</gold>"
module: com.example.MyPatch  # 可选：新类名的模块（独立 ClassLoader）
options:
  foo: bar
```

| 命令 | 作用 |
|---|---|
| `/lb hotfix <文件名>` | 应用（不带扩展名也行） |
| `/lb hotfix list` | 列出已加载的（描述、规则数、是否含模块） |
| `/lb hotfix info <id>` | 详情：来源、加载时间、每一条覆盖的键值 |
| `/lb hotfix check <文件名>` | **只校验不加载** |
| `/lb hotfix reload <id>` | 卸载后重新加载 |
| `/lb hotfix reloadall` | 重新加载全部 |
| `/lb hotfix export <id>` | 把当前覆盖导出成新 `.hotfix`（固化调好的数值） |
| `/lb hotfix unload <id>` | 卸载并**还原被覆盖的配置** |

支持的动作：`console:<命令>`、`broadcast:<文本>`、`title:<主>|<副>`、`sound:<音效名>`。

**能力边界（JVM 限制，不是实现取巧）**：能热改配置、执行命令、加载**新类名**的模块；
不能替换已经加载过的类（比如改 `GameService` 的逻辑），那种必须重启。

## 加成与反刷分

### 双倍加成 `boost`

三种开启方式：`/lb boost start` 手动、`/lb boost schedule add` 每天定时、以及配置里的触发器。

```yaml
boost:
  triggers:
    killstreak:
      enabled: true
      streak: 10        # 有人连杀 10 次时自动开
      seconds: 300
      multiplier: 2.0
      once: true
      types: [ coins, exp ]
    player-count:
      enabled: false
      count: 8          # 在线满 8 人时开
      seconds: 600
      multiplier: 2.0
      types: [ coins, exp ]
```

### 反刷分 `anti-farm`

```yaml
anti-farm:
  enabled: true
  window-seconds: 600   # 窗口内反复杀同一个人
  decay: 0.5            # 第 2 次 50%、第 3 次 25%
  min-factor: 0.1       # 最低保留 10% 收益
```

## 数据存储

```yaml
storage:
  type: SQLITE            # 或 MYSQL
  save-interval-seconds: 300
  mysql:
    host: 127.0.0.1
    port: 3306
    database: litebrawl
    table: litebrawl_profiles
```

- `SQLITE`（默认）：`plugins/LiteBrawl/litebrawl.db`，零配置
- `MYSQL`：表会自动 `CREATE TABLE IF NOT EXISTS`
- **切换存储类型需要重启**，`/litebrawl reload` 不会重建连接

表结构：

```sql
uuid        VARCHAR(36) PRIMARY KEY,
name        VARCHAR(32),
coins       BIGINT,
experience  BIGINT,
kills       INT,
deaths      INT,
assists     INT,
updated_at  BIGINT,
cosmetics   VARCHAR(512) NOT NULL DEFAULT '',   -- 已拥有 + 已装备
settings    VARCHAR(512) NOT NULL DEFAULT ''    -- 个人开关
```

周期榜（日 / 周 / 月 / 年）单独一张 `litebrawl_profiles_period` 表，按 `period_key` 分区，
不会污染总榜数据。

周期数据会不断累积，插件**每 6 小时自动清理一次**结束时间超过
`storage.period-retention-days`（默认 180 天）的历史周期；判断用的是周期的**结束**时间，
所以当前正在累积的那一期永远保留（年榜不会在年中把自己删掉）。设为 `0` 关闭清理，
`/litebrawl status` 能看到当前生效的保留天数。

## 跨服

当前默认 `proxy.mode: NONE`，即单服运行。跨服跳转已经实现 Bungee 插件消息通道：

```yaml
proxy:
  mode: BUNGEE          # NONE / BUNGEE
  default: lobby        # /play 不带参数时跳这里
  servers: [ lobby, brawl1 ]
```

Velocity 需要在 `velocity.toml` 里开启 `bungee-plugin-message-channel = true`。
玩家信息转发（`modern` 模式）是另一件事，配法见 PaperMC 文档。

## 已知限制

- `/map scan` 是主线程分帧扫描（每 tick 3 个 chunk），半径开很大时会占用 tick 时间，
  建议单次不要超过 300
- 方块保护对所有人生效，建造请用 `litebrawl.bypass.build`；**这个权限不会**让你免疫即死方块，
  免疫要单独给 `litebrawl.bypass.lethal`（默认关闭）
- 头顶铭牌无法做渐变（客户端只接受单个队伍颜色），`NAME_STYLE` 的渐变只在 Tab 和聊天里生效
- 热更新不能替换已加载的类
- 升级插件时注意 Paper 的 remap 缓存：新 jar 用**同一个文件名**时，服务端可能复用
  `plugins/.paper-remapped/` 里的旧缓存（日志不再出现 `Remapping plugin ...`），
  表现是「换了 jar 但行为没变」。改文件名或先停服删掉那个缓存

## 开发与验证脚本

`tools/` 里是开发期用的离线校验脚本，不参与打包：

| 脚本 | 作用 |
|---|---|
| `YamlSmoke.java` | 用 snakeyaml 校验 `config.yml`，检查商品数量与关键段 |
| `StorageCheck.java` | 校验 SQLite 列顺序与迁移 SQL |
| `SqlSmoke.java` | 造临时库跑一遍读写往返 |
| `DbSeed.java` / `DbPurge.java` / `DbTables.java` / `DbCheck.java` | 造测试数据、清测试数据、看表结构 |
| `ChatFormatCheck.java` | 离线渲染聊天格式，确认 MiniMessage 标签不泄漏 |
| `HealthCheck.java` | 离线渲染血量条 |
| `WebCheck.cjs` | 校验 `web/index.html` 的内联 JS 语法与高亮函数 |
| `rcon.ps1` | 极简 RCON 客户端，方便脚本化下发命令 |

跑法示例（不需要启动服务端）：

```bash
java -cp ~/.m2/repository/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar \
     tools/YamlSmoke.java src/main/resources/config.yml
node tools/WebCheck.cjs
```

## 许可证

[MIT](LICENSE)。

第三方依赖遵循各自的协议，本项目只在编译期引用 / 打包分发，不改动它们的源码：

| 依赖 | 用途 | 是否打包进 jar |
|---|---|---|
| Paper API | 服务端接口 | 否（编译期 `provided`） |
| Adventure | MiniMessage 文本渲染 | 否（Paper 自带） |
| snakeyaml | YAML 解析 | 否（Paper 自带，经 Bukkit API 使用） |
| sqlite-jdbc | SQLite 驱动 | 是 |
| mysql-connector-j | MySQL 驱动 | 是 |
