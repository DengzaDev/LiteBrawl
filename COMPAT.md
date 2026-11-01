# 版本兼容方案（1.8 ~ 1.21）

> 当前状态：**只支持 Paper 1.21.1**。本文是把它做到 1.8 ~ 最新的路线图与降级矩阵。
> 目标读者是维护者，不是服主。

## 为什么这不是"改个 version 号"

插件现在直接用了大量只有高版本才存在的 API。按代码统计：

| 依赖 | 出现次数 | 首次可用的版本 |
|---|---|---|
| `net.kyori.adventure.*`（Component / MiniMessage） | 93 | Paper 1.16+ 内置 |
| `Material.*` 常量 | 92 | 逐版本新增 |
| `PersistentDataContainer` | 7 | 1.14 |
| `TextDisplay` / `Display` 实体 | 7 | 1.19.4 |
| `Attribute.GENERIC_*` | 6 | 1.9 起名称变过多次 |
| `setPersistent` | 4 | 1.15 |
| `PlayerProfile` / `setPlayerProfile` | 4 | 1.13 |
| `getMinHeight` / `getMaxHeight` | 2 | 1.17 |
| `getPluginMeta` | 3 | 1.20.6（本项目已在用） |
| `org.bukkit.Registry` | 1 | 1.20 |

**最关键的一条：核心玩法依赖 `BAMBOO_MOSAIC`（1.20 的竹子方块）。** 1.8 没有竹子，
所以「踩竹马赛克即死」在 1.8 上物理不可实现，只能换默认方块——玩法描述会变。

## 降级矩阵

| 功能 | 1.8 – 1.12 | 1.13 – 1.15 | 1.16 – 1.18 | 1.19 – 1.20.5 | 1.20.6+ / 1.21 |
|---|---|---|---|---|---|
| 进场 / 装备 / 箭袋 / 奖励 | 可用 | 可用 | 可用 | 可用 | 可用 |
| 即死方块 | **默认方块必须替换**（无竹子） | 同左 | 同左 | 同左 | 竹子可用 |
| 文本渲染 | `ChatColor` + `§` 降级 | 同左 | Adventure 原生 | Adventure | Adventure |
| 全息排行榜 | 盔甲架 + 自定义名 | 同左 | 同左 | TextDisplay | TextDisplay |
| 铭牌血量条 | 队伍前缀（近似） | 同左 | 同左 | 同左 | 同左 |
| 名字样式（渐变） | 不可用 | 不可用 | 可用 | 可用 | 可用 |
| Tab 列表 | 老 API（`setHeader` 不存在） | `setHeader/Footer` | 同左 | 同左 | 同左 |
| 外观粒子 | 部分粒子不存在 | 大部分 | 可用 | 可用 | 可用 |
| 头颅皮肤 | `setOwner(String)` | `PlayerProfile` | 同左 | 同左 | 同左 |
| 网页控制台 | 可用 | 可用 | 可用 | 可用 | 可用 |
| 热更新 | 可用 | 可用 | 可用 | 可用 | 可用 |
| 备份 | 可用 | 可用 | 可用 | 可用 | 可用 |

## 实施阶段

每个阶段单独可交付、可回归，不要求一次全绿。

### 阶段 1：文本层抽象（基础，改动最大）

- **目标**：`Msg` 不再把 `Component` 暴露给调用方，改为返回 `Object`（或引入 `Text` 接口），
  所有 73 处调用点跟着改
- **做法**：`Msg.mini()` 内部判断 `hasAdventure()`：
  - 有 → 走 MiniMessage，返回 Component
  - 没有 → 把 MiniMessage 标签转成 `§` 传统颜色码，返回 String
- **验收**：1.21.1 上行为完全不变（现有 17 个单测 + 真机播报不回归）；
  1.8 上聊天不再抛 `NoClassDefFoundError`
- **风险点**：`player.sendMessage(Component)` 在没 Adventure 的版本上要改走 String 重载

### 阶段 2：实体层降级

- 全息榜：`TextDisplay` 不可用时退回 `ArmorStand`（`setCustomNameVisible`）
- 假人：僵尸 → 1.8 的 `EntityType.ZOMBIE` 名称一致，无需改
- **验收**：1.19.4 以下能放榜、能点（盔甲架点击用现有交互）

### 阶段 3：材质与方块名

- 新增 `MaterialCompat.resolve(String name)`：先按原名查，失败再按映射表回退
  （例如 `BAMBOO_MOSAIC` → `SLIME_BLOCK`，`NETHER_STAR` 在 1.8 叫 `NETHER_STAR` 不变）
- 配置里的材质名允许写多个候选：`lethal-blocks: [BAMBOO_MOSAIC, SLIME_BLOCK]`，取第一个存在的
- **验收**：1.8 上开图不报"未知方块"，即死方块按回退值生效

### 阶段 4：属性 / PDC / 头颅 用反射封装

- `Attribute`：1.9 起叫 `GENERIC_ATTACK_SPEED`，1.8 无此概念 → 直接跳过攻击速度调整
- `PersistentDataContainer`：1.8 没有 → 用 lore 里的不可见标记替代
- `SkullMeta`：`setPlayerProfile` 不存在 → `setOwner(String)`
- **验收**：每个封装都要有"能力探测 + 回退"，不允许直接 `import` 高版本类到主路径

### 阶段 5：构建策略

两条路，二选一：

1. **单 jar + 全反射**（简单，但 1.8 与 1.21 的 API 差异会让代码里到处是反射，可读性差）
2. **多模块**（`core` + `v1_8` + `v1_21`，运行时按版本选实现；工程量大但干净）

建议先做 1 把功能跑通，稳定后再抽 2。

## 不可实现 / 必须砍掉

- **1.8 上的竹子方块**：产品层面无解，只能换默认即死方块并在文档里说明
- **渐变名字**：客户端渲染能力问题，任何版本都不行
- **1.9 之前的攻击速度**：根本没有这个概念，"1.8 连点手感"在 1.8 上是原生的，不需要插件干预
- **1.16 之前的 Adventure**：只能降级到传统颜色码，Minimessage 语法会丢失（含 `<gradient>`）

## 现实预期

按上面的阶段拆，每个阶段大致对应一次独立交付；阶段 1 是硬骨头（要动 73 处调用点），
但它做完之后，后面几个阶段都是"加回退分支"，风险逐级下降。

在阶段 1 完成前，**不要**在 `plugin.yml` 里放宽 `api-version`——否则插件会在低版本上
加载成功然后到处抛 `NoClassDefFoundError`，比直接拒绝加载更糟。
