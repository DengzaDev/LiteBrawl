# LiteBrawl

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/DengzaDev/LiteBrawl?label=release)](https://github.com/DengzaDev/LiteBrawl/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21.1-blue.svg)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)

> [中文文档](README.md)

A small-scale brawl minigame plugin for **Paper 1.21.1 / Java 21**.

Players join, click once, and get dropped on a random emerald block somewhere in the map.
Everyone gets the same kit (iron gear, a bow, 4 self-refilling arrows), and stepping on
bamboo mosaic kills you instantly. Kills earn coins and XP, killstreaks put a bounty on
your head, and the map rotates on a timer.

Ships with a sidebar scoreboard, tab list, holographic leaderboards, practice bots,
functional NPCs, and a built-in web console (config editor + player dashboard).

Single-server design. **No plugin dependencies.**

## Features

- **Random spawns** — reads emerald blocks already in the map; never places blocks
- **Fixed kit** — iron sword, bow with Power I, 4 arrows, 64 steak, three iron armor pieces, all unbreakable
- **Auto-refilling quiver** — one arrow every 5s up to 4; picking up arrows is blocked
- **Lethal blocks** — bamboo mosaic (and slabs/stairs) kills instantly, re-checked every 0.25s
- **World rules** — no building, no liquid flow, no lava damage, no mob spawning
- **Combat feel** — no 1.9+ attack cooldown, configurable knockback, combat tag
- **Economy** — coins/XP from kills and assists, anti-farm decay, killstreak bounty, level curve
- **Leaderboards** — holographic boards with day/week/month/year/all, `/board` GUI, chat `/top`
- **Teams** — up to 8, friendly fire off, invite-based
- **Cosmetics** — 6 categories (kill/death messages, kill effects, chat prefix, name style, title)
- **Map rotation** — ORDER or VOTE mode, fully automated
- **Practice bots** — zombie bots with player skins; "masters" chase you, "novices" can't deal damage
- **NPCs** — shop / cosmetics / leaderboard NPCs at your feet
- **Boosts** — manual, daily scheduled, or triggered by killstreak / player count
- **Web console** — wizard, ~60 config fields, syntax-highlighted YAML editor, multi-file editing,
  auto-save, read-only DB browser, live dashboard; players can `/link` to view their own stats
- **Hotfix** — change config values, run actions, or load new-class modules without restarting
- **Backups** — scheduled zip of all configs + database, with retention

## Quick start

**Requirements:** Paper 1.21.1, Java 21. SQLite is used by default (bundled); MySQL optional.

1. Download `LiteBrawl-x.y.z-Release.jar` from [Releases](https://github.com/DengzaDev/LiteBrawl/releases/latest)
2. Drop it into your server's `plugins/` directory
3. Restart the server
4. Set up a map:

```
/maps create arena1 First Arena     # register a world folder that already exists
/map setspawn                       # stand on the spawn point
/map scan                           # find existing emerald blocks in the map
```

`/map scan` never places blocks — it only reads. On first startup the plugin prints
the web console URL (`http://127.0.0.1:8787/?token=...`).

## Build from source

```bash
git clone https://github.com/DengzaDev/LiteBrawl.git
cd LiteBrawl
mvn -B package     # -> target/LiteBrawl-x.y.z-Release.jar
mvn -B test        # unit tests only, no server needed
```

## Commands

Players: `/start` `/shop` `/stats` `/top` `/board` `/sb` `/team` `/cosmetic` `/spawn`
`/vote` `/play` `/link`

Admins (`litebrawl.admin`): `/maps ...` `/map ...` `/litebrawl reload|coins|exp|status|link|backup`
`/litebrawl bot|npc|boost|hotfix ...`

Full command reference, configuration guide and UI details are in the
[Chinese README](README.md) — it is the authoritative document.

## Known limitations

- `/map scan` runs on the main thread in chunks; keep the radius under ~300
- Nameplates can't be gradient-colored (client limitation) — gradients only apply to tab/chat
- Hotfix cannot replace already-loaded classes
- UI and command feedback are currently **Chinese only** (see [CONTRIBUTING](CONTRIBUTING.md))

## License

[MIT](LICENSE)
