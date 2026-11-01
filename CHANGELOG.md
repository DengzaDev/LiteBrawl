# 更新日志

本文件同时用作 GitHub Release 的发布说明。

---

## 1.0.2-Release

发布日期：2026-11-01

包含 1.0.1 发布后的全部补丁，另外新增工程质量、数据安全与开源配套。

### 新增：完整备份

- `/litebrawl backup now` 生成一份 zip：6 个配置文件 + 数据库
- 数据库用 `VACUUM INTO` 导出一致副本，**服务器运行中也能安全备份**
- 自动备份：`backup.interval-hours`（默认 24 小时一次）、`backup.keep`（默认保留 10 份）
- `/litebrawl backup list` 查看已有备份；网页控制台的「备份」按钮也走同一套逻辑
- MySQL 模式不会导出数据库（进程内无法安全导出），需要时请用 mysqldump

### 新增：工程保障

- **构建 CI**：每次 push / PR 自动跑 `mvn -B verify`，编译不过或测试挂了会直接标红
- **17 个单元测试**：覆盖周期键与清理判定、外观/设置序列化往返、占位符替换
  （都不依赖服务端，`mvn -B test` 就能跑）
- 其中一条 `y:<今年>` 的用例专门守住「周期清理不能误删当年年榜」

### 新增：开源配套

- `CONTRIBUTING.md`：环境、代码结构、约定、提 PR 前的检查清单
- Issue 模板（Bug 报告 / 功能建议）与 PR 模板
- `README.en.md`：英文说明，海外用户能看懂项目是什么

### 改动

- 网页控制台的「网络/存储」分区补上 **MySQL 密码**输入框
  （显示 `***` 表示不修改，留空即清空）
- `/litebrawl backup` 加入管理命令帮助

### 修复

1.0.1 发布后累计修复 9 项，详见下方 1.0.1 的「发布后补丁 / 自检修复」两段。

---

## 1.0.1-Release · 首个公开发布

发布日期：2026-11-01

> **发布后补丁（不改变版本号）**
>
> - 周期榜历史数据自动清理：新增 `storage.period-retention-days`（默认 180 天），
>   每 6 小时按周期**结束**时间清理一次，当前累积中的那一期不受影响
> - 网页配置接口对 `storage.mysql.password`、`web.token` 等敏感项返回 `***`；
>   把 `***` 提交回来不会覆盖真实值（提示「N 项敏感字段保持不变」）
> - 网页绑定码与玩家令牌增加每分钟过期清扫，此前只在被使用时才删除，长期开服会缓慢涨内存
> - `/litebrawl status` 新增「周期数据保留」一行，方便确认清理是否生效
>
> **自检修复**
>
> - 绑定码与玩家令牌改成「先写过期时间、再写值」，
>   修复清扫任务可能把刚发出去的码当成孤儿删掉的竞态
> - 玩家数据加载改为「已有缓存优先」，避免异步加载与主线程并发时
>   用数据库旧值覆盖内存里刚加的硬币 / 经验
> - 击杀与死亡的周期统计改为后台写库（四个周期键仍在主线程算好，
>   跨午夜不会分裂到两天），不再阻塞主线程
> - `/litebrawl bot spawn` 在地图未注册或没有出生点时给出具体原因，
>   不再只报「已生成 0 个假人」
> - `/litebrawl hotfix check` 的规则数改为只统计叶子键，与 `hotfix list` 一致
> - `/api/action` 遇到未知动作返回 400，不再用 200 冒充成功

面向 **Paper 1.21.1 / Java 21** 的小型大乱斗插件。玩家点一下就开始，随机丢到地图里一颗绿宝石块上，
人人同一套铁装、4 支会自动补充的箭，踩到竹马赛克直接死。击杀攒硬币和经验，连杀会被挂赏金，
攒够了买药水和外观，地图到点自动轮换。

自带侧边栏、Tab、全息排行榜、陪练假人、功能 NPC，以及一个跑在本机的网页控制台——
改配置、翻数据库、玩家查战绩都不用进游戏。**单服设计，不依赖任何第三方插件。**

---

### 一、环境要求

| 项 | 要求 | 说明 |
|---|---|---|
| 服务端 | Paper 1.21.1 | 需要 Paper 系（含 Purpur）；原版 Spigot 未验证 |
| Java | 21 | 必须 |
| 其他插件 | 无 | 不依赖 Vault / PlaceholderAPI / ProtocolLib |
| 数据库 | SQLite（内置）或 MySQL 5.7+ | 默认 SQLite，零配置 |

jar 内已 shade `sqlite-jdbc` 与 `mysql-connector-j`，服务器端不需要额外下载驱动。
`Adventure`（MiniMessage）与 `snakeyaml` 由 Paper 自带，不重复打包。

---

### 二、下载与安装

1. 下载本页附件 `LiteBrawl-1.0.1-Release.jar`
2. 放进服务器的 `plugins/` 目录
3. 重启服务器
4. 打开 `plugins/LiteBrawl/config.yml` 按需修改，执行 `/litebrawl reload`

**首次启动会自动完成这些事：**

- 生成 `config.yml`（每一段都有中文注释）、`messages.yml`、`快速上手.txt`
- 生成 `web.token`，在控制台打印网页控制台地址，并尝试自动打开浏览器
- 建好 SQLite 数据库 `plugins/LiteBrawl/litebrawl.db` 与表结构

**升级提示**：Paper 会把插件 remap 后缓存到 `plugins/.paper-remapped/`。
如果新 jar 用的是**同一个文件名**，服务端可能直接复用旧缓存（日志里不会再出现
`Remapping plugin ...`），表现是「换了 jar 但行为没变」。要么改文件名，要么先停服删掉
`plugins/.paper-remapped/<插件名>-<版本>.jar`。

---

### 三、开图三步

地图必须是服务器目录里**真实存在的世界文件夹**，插件不会凭空生成地形。

| 步骤 | 命令 | 说明 |
|---|---|---|
| 1 | `/maps create arena1 第一竞技场` | 目录名 `arena1`，显示名「第一竞技场」 |
| 2 | `/map setspawn` | 站到出生点，把脚下设为出生点 |
| 3 | `/map scan` | 扫描地图里**已有**的绿宝石块作为随机出生点 |

`/map scan` **不会放置任何方块**，它只读取。扫描中心是出生点，范围 `spawn.scan-radius`
（默认 160 格），高度默认覆盖 -64 ~ 320，所以天空图、洞穴图也能扫到。地图更大时临时加大半径：

```
/map scan 400
```

结果缓存在 `maps.yml`，控制台会打印进度。之后玩家进服会拿到大厅物品，
右键 8 号槽的下界之星即可进入战场。

---

### 四、玩法机制

#### 出生与入场

- 进服自动发放「开始游戏」物品，右键它或打 `/start` 进场
- 从扫描到的出生点里**随机**取一个，传送到方块上方 `spawn.y-offset`（默认 2 格）
- 装备、食物、箭全部在进场时自动发放

#### 初始装备

默认配置（`kit` 段，可改）：

| 部位 / 物品 | 默认值 |
|---|---|
| 头盔 | `NONE`（不戴） |
| 胸甲 / 护腿 / 靴子 | 铁质 |
| 剑 | 铁剑（无附魔） |
| 弓 | 普通弓 + **力量 I** |
| 箭 | 4 支 |
| 食物 | 64 熟牛排 |
| 耐久 | 全部不可损坏（`unbreakable: true`） |

#### 箭袋

- 箭是**真实物品**，射出后按玩家单独计时，每 5 秒补回一支
- 上限 4 支；商店「箭袋扩容」最多再 +2（`kit.max-arrow-bonus`），**死亡后失效**
- 满袋时禁止拾取地上的箭，避免刷箭
- 落地箭矢 3 秒后自动清除（`combat.arrow-ground-seconds`）

#### 即死方块

- 默认踩到竹马赛克死亡，包含 `BAMBOO_MOSAIC_SLAB` / `BAMBOO_MOSAIC_STAIRS` 变体
- 每 0.25 秒轮询脚下与脚部方块，**站桩不动也会判定**
- 创造模式会被强制切回生存再判定，不能用创造模式躲
- 对陪练假人同样生效（`spawn.lethal-affects-bots`）
- 免疫需要单独的 `litebrawl.bypass.lethal` 权限（默认没人有）

#### 世界规则

| 规则 | 默认 | 可配置项 |
|---|---|---|
| 破坏 / 放置方块 | 禁止 | `rules.allow-build` |
| 液体流动、火焰蔓延 | 禁止 | `rules.allow-liquid-flow` |
| 岩浆 / 火焰伤害 | 免伤 | `rules.lava-damage` |
| 除玩家外生物生成 | 禁止 | `rules.mob-spawn` |
| 存档里的旧怪 | 启动时清理 + 每 10 秒兜底 | `rules.remove-existing-mobs` |
| 空玻璃瓶 | 自动没收 | `rules.confiscate-glass-bottles` |
| 掉落物品 | 禁止 | `rules.allow-item-drop` |
| 摔落伤害 | 开启 | `rules.fall-damage` |

#### 战斗手感

- `combat.no-attack-cooldown: true` 时进战场把攻击速度拉到 1024，**没有 1.9+ 的攻击间隔**，
  就是 1.8 的连点手感；回大厅自动还原
- 击退倍率可调（`combat.knockback-multiplier`，1.0 = 原版）
- 互相造成伤害后进入**战斗标记**（默认 15 秒），期间禁止 `/spawn`、回大厅、开商店
- 玩家之间可以互相伤害，但**队友之间免伤**（含箭矢）

#### 死亡与复活

- 死亡不扣硬币，只累加 `deaths`
- 重生回到地图出生点（不是随机点），需要重新点「开始」
- 进场后有 `protection.seconds`（默认 3 秒）免伤，保护期内也打不出伤害

---

### 五、经济与成长

#### 收益

| 行为 | 硬币 | 经验 | 其他 |
|---|---|---|---|
| 击杀 | +5 | +5 | +1 金苹果 |
| 助杀 | +2 | +2 | — |
| 死亡 | 0 | 0 | 不扣钱，只记 `deaths` |

助杀判定：8 秒内对目标造成 ≥ 4.0 点伤害，且不是最后一击的人。

#### 反刷分

短时间内反复击杀同一个对手，收益递减：`factor = 0.5^n`，最低保留 10%。

- 统计窗口默认 600 秒（10 分钟）
- 第 2 次 50%、第 3 次 25%、第 4 次 12.5%… 到 10% 为止

#### 连杀赏金

- 连杀达到 5 次（`bounty.min-killstreak`）开始被悬赏
- 第一次挂 20 硬币（`bounty.base`），之后每多杀一人再加 10（`bounty.per-kill`）
- 被击杀时**赏金全额转给击杀者**，连杀与赏金一起清零
- 上赏金、赏金被拿走时全服广播（文案可配）
- 存在内存里，重启清零——刻意不落库，避免悬赏变成长期负担
- 掉线不清空（防「重连躲悬赏」），但下线期间不能被击杀

#### 等级

- 升至下一级所需经验 = `base-exp × growth^(等级-1)`，默认 100 × 1.35^(n-1)，上限 50 级
- **等级不入库**，由累计经验纯函数换算，避免两处数值漂移
- 聊天前缀、头顶铭牌 `[Lv.n]`、Tab 都读同一个等级值

---

### 六、排行榜

#### 全息排行榜

- `/map bar set <kills|level|coins|deaths>` 在当前位置生成
- 用 TextDisplay + 隐形盔甲架实现，**左右键**分别切换时间维度
- 每 30 秒刷新（`boards.update-seconds`），最多 10 行
- 时间维度：日榜 / 周榜 / 月榜 / 年榜 / 总榜

#### 榜单面板

- `/board` 打开 GUI 面板，背包能精确区分左右键
- 聊天版：`/top <kills|level|coins|deaths>`

#### 周期数据

日 / 周 / 月 / 年榜的数据存在独立表 `litebrawl_profiles_period`，按 `period_key` 分区，
不会污染总榜数据。

---

### 七、社交与外观

#### 组队

- 大厅 7 号槽指南针右键打开队伍界面，或 `/team`
- 邀请制：`/team invite <玩家>`，对方收到可点击的 [点击接受]
- 上限 8 人（`team.max-size`），邀请 60 秒有效
- **队友之间互相免伤**（含箭矢）
- 队长退出自动转交给下一个人
- 队伍只存在内存里，重启清空

#### 复活保护

- 进场后默认 3 秒免伤
- `protection.block-dealing: true` 时保护期内也打不出伤害，防止贴脸输出
- 侧边栏可用 `%protection%` 显示剩余秒数

#### 外观（6 类）

| 类型 | 作用 | 默认外观 |
|---|---|---|
| `KILL_MESSAGE` | 击杀播报换成自己的文案 | 撕裂播报 |
| `DEATH_MESSAGE` | 阵亡播报换成自己的文案 | 灵魂播报 |
| `KILL_EFFECT` | 击杀时在死者位置放粒子 + 音效 | 不死图腾特效 |
| `CHAT_EFFECT` | 聊天名字前的专属前缀 | 金色聊天前缀 |
| `NAME_STYLE` | Tab / 聊天名字样式 | 金色名字、渐变名字 |
| `TITLE` | 名字后的 `[称号]` | 杀戮之王、富可敌国 |

- 全部在商店用硬币购买（40 ~ 120 硬币），买完自动装备
- `/cosmetic` 可切换或卸下，也可以在个人设置界面里点选
- 已购买的外观落在玩家数据的 `cosmetics` 列，**换服、重登都还在**
- 头顶铭牌只能显示队伍颜色（客户端限制），渐变只在 Tab 和聊天里生效

#### 名字与血量条

- 头顶铭牌格式：`[Lv.n]名字[称号]`，每 20 tick 刷新
- 血量条同时显示在头顶和 Tab 列表，`%full%` 是满格心、`%empty%` 是空心
- 聊天格式 `chat.format` 支持 `%team%%display%`，队伍标签自动拼接

---

### 八、运营工具

#### 多地图轮换

- 按 `/maps list` 的顺序循环，默认每 600 秒一次，提前 30 秒广播
- 换图时所有在线玩家回到新地图出生点并回到大厅状态，需要重新点「开始」，
  避免带着上一张图的装备被传送
- 注册少于 2 张图时自动不启用
- `/map next` 立刻切换

#### 地图票选

```yaml
rotation:
  mode: VOTE    # ORDER = 按顺序；VOTE = 切到票数最高的图
```

玩家 `/vote <地图id>` 投票，`/vote` 看票数。轮换时如果有票就切到得票最高的地图并清空票数。

#### 陪练假人

- 在线玩家少于 3 人时自动上线，人够了自动撤掉（`bots.auto`）
- 默认 4 个，一半「大师」一半「新手」（`master-percent`）
- **大师**：主动追人，攻击力 4.0；击杀 +2 硬币 +2 经验
- **新手**：不设目标、伤害被取消；击杀 +1 硬币 +1 经验
- 用带玩家皮肤的僵尸冒充，装备与真人一致
- 可静音（默认开）、被打死 5 秒后自动复活
- 手动控制：`/lb bot spawn [数量]` / `clear` / `count`

> 假人需要世界难度 ≥ easy。难度是 `peaceful` 时僵尸会被立刻清除，控制台会给出警告。

#### 功能 NPC

```
/lb npc set <shop|cosmetic|board>   # 在脚下创建
/lb npc skin <玩家名>               # 换最近 NPC 的皮肤
/lb npc name <显示名>               # 改名
/lb npc move / remove               # 移动 / 删除最近的
/lb npc list                        # 列出全部
```

持久化在 `npcs.yml`，重启自动重生。NPC 是隐形盔甲架 + 玩家头，右键打开对应界面。

#### 双倍加成

三种开启方式：

1. 手动：`/lb boost start <coins|exp|both> <秒> [倍率] [玩家,...]`
2. 定时：`/lb boost schedule add <HH:mm> <coins|exp|both> <秒> [倍率]`（每天循环）
3. 触发器：连杀达到 10 次、或在线人数达到阈值时自动开（`boost.triggers`）

`/lb boost info` 看剩余时间，`/lb boost stop` 手动关闭。

#### 大厅快捷栏

| 槽位 | 物品 | 作用 |
|---|---|---|
| 8 | 下界之星 | 开始游戏 |
| 7 | 指南针 | 组队界面 |
| 6 | 箱子 | 物品商店（仅大厅可用） |
| 5 | 绿宝石 | 外观商城 |
| 4 | 红石 | 返回大厅 / 跨服跳转 |
| 3 | 比较器 | 设置（记分板、Tab、战斗提示） |
| 2 | 命名牌 | 个人设置（物品栏排列、假人开关、外观切换） |

#### 商店

默认 14 件商品，价格按「一次击杀 = 5 硬币」定的：

| 道具 | 价格 | 说明 |
|---|---|---|
| 金苹果 ×1 | 5 | 回血 + 抗性 |
| 迅捷药水 | 8 | 速度 I · 20 秒 |
| 治疗药水 | 10 | 立即回 4 颗心 |
| 末影珍珠 ×1 | 15 | 位移 |
| 力量药水 | 18 | 力量 I · 8 秒 |
| 箭袋扩容 | 20 | 箭上限 +1，死亡后失效 |
| 金色聊天前缀 | 40 | 外观 |
| 灵魂播报 | 40 | 外观 |
| 金色名字 | 50 | 外观 |
| 撕裂播报 | 60 | 外观 |
| 富可敌国（称号） | 60 | 外观 |
| 不死图腾特效 | 80 | 外观 |
| 杀戮之王（称号） | 80 | 外观 |
| 渐变名字 | 120 | 外观 |

**「先下单、下场发放」**：大厅买的东西不进背包，等下次点「开始」时和装备一起发，
避免在大厅囤货、也避免买了立刻送人。

---

### 九、网页控制台

内置 HTTP 服务（JDK 自带 `com.sun.net.httpserver`，无第三方依赖），默认只监听 `127.0.0.1:8787`。

#### 管理端

地址：`http://127.0.0.1:8787/?token=<web.token>`（首次启动自动生成 token 并打印）

| 功能 | 说明 |
|---|---|
| 开服向导 | 5 步填完就能开玩，每步都能随时回来改 |
| 表单分区 | 约 60 个常用配置项，不用手写 YAML |
| YAML 编辑器 | 行号、语法高亮、搜索、大纲、多文件切换 |
| 多文件编辑 | `config.yml` / `messages.yml` / `maps.yml` / `boards.yml` / `boost.yml` / `npcs.yml` |
| 保存保护 | 保存前先校验 YAML，写坏不会覆盖；保存后自动触发对应重载 |
| 自动保存 | 勾上后停手 1.2 秒自动写盘 |
| 数据库浏览 | 按击杀 / 硬币 / 经验 / 死亡 / 助杀翻排行（**列名白名单，不接受任意 SQL**） |
| 实时仪表盘 | 在线人数、当前地图、假人数、TPS、生效中的加成 |

所有 `/api/*` 都要 token。要外网访问就把 `web.bind` 改成 `0.0.0.0`，并自己做好防火墙。

#### 玩家面板

1. 玩家在游戏里打 `/link`，拿到 6 位绑定码（5 分钟有效）
2. 打开 `http://<服务器>:8787/me` 输码
3. 换到 12 小时有效的玩家 token，之后可以查自己的战绩、装备或卸下已拥有的外观

管理员可以用 `/litebrawl link <玩家名>` 替玩家代发码（**玩家不在线也行**），
适合「玩家说绑不上」的排查场景。

> 玩家 token 与管理端 token 是两套东西：玩家只能读自己的数据、只能动自己的外观，
> 碰不到服务器配置和数据库。

---

### 十、热更新

`plugins/LiteBrawl/HotFix/*.hotfix`（本质是 zip）可以在**不重启**的情况下改变行为。
本版本是 v2 格式：

```yaml
id: balance-tweak
api: 2
description: 平衡性调整
enabled: true
requires: "1.4"              # 主版本不匹配就拒绝加载
load-after: [other-id]       # 依赖：这些热更新必须先加载
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
| `/lb hotfix info <id>` | 详情：来源文件、加载时间、每一条覆盖的键值 |
| `/lb hotfix check <文件名>` | **只校验不加载**：api / requires / rules / actions |
| `/lb hotfix reload <id>` | 卸载后重新加载（改完文件不用重启） |
| `/lb hotfix reloadall` | 重新加载全部 |
| `/lb hotfix export <id>` | 把当前覆盖导出成新 `.hotfix`（固化调好的数值） |
| `/lb hotfix unload <id>` | 卸载并**还原被覆盖的配置** |

支持的动作：`console:<命令>`、`broadcast:<文本>`、`title:<主>|<副>`、`sound:<音效名>`。

**能力边界（JVM 限制，不是实现取巧）**

- 能：热改配置、执行命令与广播、热加载**新类名**的模块（可直接调插件和 Bukkit API）
- 不能：替换已经加载过的类（比如改 `GameService` 的逻辑），那种必须重启

---

### 十一、命令速查

#### 玩家

| 命令 | 别名 | 说明 |
|---|---|---|
| `/start` | | 进入战场 |
| `/shop` | | 打开商店（仅大厅） |
| `/stats [玩家]` | | 查看战绩 |
| `/top <kills\|level\|coins\|deaths>` | `/leaderboard` | 聊天版排行榜 |
| `/board` | `/boards`、`/rank` | 排行榜面板，左右键切换 |
| `/sb` | | 开关侧边栏 |
| `/team [create\|accept\|leave\|invite <玩家>]` | | 不带参数打开组队界面 |
| `/cosmetic [list\|equip <id>\|off <类型>]` | `/cos` | 管理已购买外观 |
| `/spawn` | | 返回出生点（战斗中不可用） |
| `/vote [地图id]` | `/mapvote` | 给下一张地图投票 |
| `/play [服务器名]` | `/hub` | 通过代理跳服 |
| `/link` | `/bind` | 生成网页绑定码 |

#### 管理员（`litebrawl.admin`，默认 op）

| 命令 | 说明 |
|---|---|
| `/maps create <目录名> <显示名>` | 把世界注册为地图 |
| `/maps list` / `select <id>` / `delete <id>` | 列出 / 切换 / 删除注册（**不删世界文件**） |
| `/map setspawn` / `scan [半径]` / `info` | 出生点与扫描 |
| `/map bar set\|remove\|list <榜单>` | 全息排行榜 |
| `/map next` | 立刻切图 |
| `/litebrawl reload` | 重载配置 |
| `/litebrawl coins\|exp give\|take\|set <玩家> <数量>` | 调整经济 |
| `/litebrawl status` | 一次性看清服务端状态（排查用） |
| `/litebrawl link <玩家名>` | 代发网页绑定码 |
| `/litebrawl bot spawn\|clear\|count` | 手动控制假人 |
| `/litebrawl npc ...` | 功能 NPC |
| `/litebrawl boost ...` | 双倍加成 |
| `/litebrawl hotfix ...` | 热更新 |

---

### 十二、配置索引

`config.yml` 每一项都有中文注释，这里只列段落用途：

| 段落 | 内容 |
|---|---|
| `prefix` | 全局消息前缀 |
| `start-item` | 大厅「开始游戏」物品 |
| `kit` / `kit-layouts` | 初始装备与三种物品栏排列 |
| `spawn` | 出生方块、即死方块、扫描范围 |
| `rules` | 破坏 / 放置 / 流动 / 岩浆 / 刷怪 / 瓶子 / 摔落 |
| `rewards` | 击杀与助杀奖励、助杀窗口 |
| `bounty` | 连杀赏金 |
| `rotation` | 地图轮换与票选 |
| `level` / `display` | 等级曲线、铭牌与血量条 |
| `chat` | 聊天格式 |
| `shop` | 商店标题、行数、商品 |
| `cosmetics` | 8 个默认外观定义 |
| `boards` | 全息榜刷新频率与格式 |
| `scoreboard` / `tab` | 侧边栏与 Tab 列表 |
| `combat` | 攻击速度、击退、战斗标记 |
| `team` | 组队上限与界面 |
| `lobby-items` | 大厅快捷栏 7 个槽位 |
| `protection` | 复活保护 |
| `anti-farm` | 反刷分 |
| `boost` | 双倍加成与触发器 |
| `bots` | 陪练假人 |
| `hotfix` | 自动加载热更新 |
| `web` | 网页控制台 |
| `storage` | SQLite / MySQL |
| `proxy` | 跨服跳转 |

`messages.yml` 单独存放击杀 / 死亡 / 连杀播报文案和武器名，改完 `/litebrawl reload` 生效。

---

### 十三、数据存储

```yaml
storage:
  type: SQLITE              # 或 MYSQL
  save-interval-seconds: 300
```

**切换存储类型需要重启**，`/litebrawl reload` 不会重建连接。

表结构（表名可配，默认 `litebrawl_profiles`）：

```sql
uuid        VARCHAR(36) PRIMARY KEY,
name        VARCHAR(32),
coins       BIGINT,
experience  BIGINT,
kills       INT,
deaths      INT,
assists     INT,
updated_at  BIGINT,
cosmetics   VARCHAR(512) NOT NULL DEFAULT '',
settings    VARCHAR(512) NOT NULL DEFAULT ''
```

周期榜另用一张 `litebrawl_profiles_period` 表，字段为 `uuid / name / period_key / kills / deaths`。

---

### 十四、安装后自检清单

装完按这个走一遍，能发现 90% 的配置问题：

| 检查 | 命令 / 操作 | 期望结果 |
|---|---|---|
| 插件是否正常加载 | `/litebrawl status` | 打印版本、存储路径、地图数、在线数、TPS |
| 地图是否注册 | `/maps list` | 至少一张，且当前地图有显示 |
| 出生点是否有效 | `/map info` | 「出生方块」数量 > 0；为 0 就重新 `/map scan` |
| 大厅物品是否发放 | 玩家进服 | 8 号槽有下界之星 |
| 进场是否正常 | 右键下界之星 | 传送到绿宝石块上方，装备齐全 |
| 商店是否可开 | 大厅 `/shop` | 14 件商品，点一个能扣钱 |
| 网页控制台 | 浏览器打开控制台打印的地址 | 能看到向导和状态面板 |
| 假人是否出现 | 人数不足时观察 | 能看到僵尸假人；没有就查世界难度 |

---

### 十五、常见问题

**Q：出生点一直是 0，进不去战场？**
地图里没有绿宝石块。插件**不会**替你放方块，需要你自己在地图里铺好 `EMERALD_BLOCK`，
然后 `/map scan`。天空图记得确认 `spawn.scan-y-min` / `scan-y-max` 覆盖到。

**Q：假人看不见，或者一出来就消失？**
世界难度是 `peaceful`。僵尸会被立刻清除。把 `server.properties` 的 `difficulty` 改成 `easy`
或更高，重启服务端。控制台对这种情况有明确警告。

**Q：换了新 jar，但行为没变化？**
Paper 的 remap 缓存。停服删掉 `plugins/.paper-remapped/<插件名>-<版本>.jar`，
或者直接给新 jar 改个文件名。

**Q：商店买的东西没进背包？**
设计如此——「先下单、下场发放」。下次点「开始」进场时和装备一起发。

**Q：网页打不开 / 提示 token 无效？**
默认只监听 `127.0.0.1`，只能从本机访问；地址里的 token 要和
`plugins/LiteBrawl/config.yml` 的 `web.token` 一致（或 `web.token` 文件）。改端口后记得重启。

**Q：玩家 `/link` 拿不到码？**
先确认 `web.enabled: true`。管理员可以用 `/litebrawl link <玩家名>` 代发。

**Q：头顶名字能做渐变色吗？**
不能。客户端只接受队伍单一颜色。渐变样式只作用于 Tab 和聊天。

**Q：改了配置没生效？**
大部分键 `/litebrawl reload` 即可；`storage.type` 必须重启服务器。

---

### 十六、已知限制

- `/map scan` 是主线程分帧扫描（每 tick 3 个 chunk），半径开很大时会占用 tick 时间，
  建议单次不超过 300
- 方块保护对所有人生效，运维建造请用 `litebrawl.bypass.build`；
  **这个权限不会**让你免疫即死方块，免疫要单独给 `litebrawl.bypass.lethal`
- 头顶铭牌无法渐变（客户端限制）
- 热更新不能替换已加载的类
- 组队、赏金、票数存在内存里，重启清空（玩家数据与外观不受影响）
- 跨服只实现了 Bungee 插件消息通道，Velocity 需要开 `bungee-plugin-message-channel`

---

### 十七、许可

[MIT License](https://github.com/DengzaDev/LiteBrawl/blob/main/LICENSE)。

完整文档见 [README](https://github.com/DengzaDev/LiteBrawl#readme)。
