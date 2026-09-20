# MoveEarth-Addtional license and third-party notices

Copyright (C) 2026 Lunar_prototype

## Primary project license

Unless a file or directory is identified below, the Java source code and
original assets of MoveEarth-Addtional are licensed under the GNU General
Public License version 3 only (`GPL-3.0-only`).

The complete GPLv3 text is provided as `LICENSE` in the source repository and
as
`META-INF/licenses/moveearth_addtional/GPL-3.0-only.txt` in release JARs.

Previously distributed copies that were received under `LGPL-3.0-only` remain
available to their recipients under that license. This notice applies to the
current source tree and distributions built from it.

## WARLORD announcer audio

Files under:

`assets/moveearth_addtional/sounds/pvp/warlord/`

are from **WARLORD - Announcer Audio Pack**, created by **VoiceBosch**, and are
distributed under the Creative Commons Attribution-ShareAlike 4.0
International license (`CC BY-SA 4.0`).

- Source: https://voicebosch.itch.io/warlord-announcer-audio-pack
- License: https://creativecommons.org/licenses/by-sa/4.0/
- Changes: the supplied WAV files were converted to Ogg Vorbis for Minecraft;
  the spoken content was not edited.

See `META-INF/NOTICE-WARLORD-AUDIO.txt` for the bundled attribution notice.

## Stonecutter compatibility implementation

The stonecutter compatibility implementation was adapted from the standalone
`moveearth_patch_unti` artifact created by **iesuok**, which declared the MIT
License.

See `META-INF/NOTICE-moveearth_patch_unti.txt` for provenance and
`LICENSES/MIT.txt` for the declared license terms.

## FirstDark Discord RPC

Selected classes from FirstDark Discord RPC version 1.0.4 are incorporated
into release JARs. FirstDark Discord RPC is licensed under the MIT License.

- Source: https://github.com/firstdarkdev/discord-rpc
- Copyright: Copyright (c) 2024 HypherionSA and Contributors

See `META-INF/NOTICE-FIRSTDARK-DISCORD-RPC.txt` for provenance and
`LICENSES/MIT-FirstDark-Discord-RPC.txt` for the applicable license terms.

## NotEnoughBandwidth delayed chunk cache design

The Delayed Chunk Cache tracking-view design was adapted from
**NotEnoughBandwidth**, created by **USS_Shenzhou**.

- Source: https://github.com/USS-Shenzhou/NotEnoughBandwidth
- Upstream files: `CachedChunkTrackingView.java` and `ChunkMapMixin.java`
- Copyright: Copyright (C) 2025 USS_Shenzhou
- License: GNU General Public License version 3 or later
- Changes: backported from Minecraft 26.1 to 1.21.1; integrated with the
  NeoForge server config; and revised distance, capacity, timeout, runtime
  disable, and long-distance eviction behavior.

The incorporated implementation is distributed by this project under the
GNU General Public License version 3.

## SQLite JDBC Driver

The SQLite JDBC driver (`org.xerial:sqlite-jdbc`) is licensed under the
Apache License, Version 2.0.

- Source: https://github.com/xerial/sqlite-jdbc
- License: http://www.apache.org/licenses/LICENSE-2.0

## JDA Discord API library

Release JARs include JDA and its required runtime libraries for the optional
dedicated-server Discord Bot integration. Voice/Opus support is excluded.

- Component: `net.dv8tion:JDA:6.6.0`
- Source: https://github.com/discord-jda/JDA
- License: Apache License, Version 2.0

The generated nested runtime JAR retains the upstream license and notice files
supplied by JDA and its runtime dependencies. Runtime dependency packages are
relocated into a MoveEarth-private namespace to prevent module conflicts with
libraries supplied by NeoForge or other mods.

## Cold Sweat API

The optional client temperature HUD integrates with the public API of Cold
Sweat. Cold Sweat code and assets are not copied into or bundled with this
project.

- Component: Cold Sweat 2.4.x for Minecraft 1.21.1
- Source: https://github.com/Momo-Softworks/Cold-Sweat
- Copyright: Momo Softworks / Mikul
- License: GNU General Public License version 3 with the upstream additional
  API/library-use permission

## Create: Rock & Stone

This project compiles against Create: Rock & Stone so that ore deposits can be
confined to a region. Only its public classes are referenced at build time; the
mod is an optional runtime dependency supplied by the target modpack, and no
Rock & Stone code or assets are copied into or bundled with this project.

- Component: Create: Rock & Stone v1.3.1 for Minecraft 1.21.1
- Source: https://github.com/BMasta/create-rns
- Copyright: BMasta and contributors
- License: GNU Lesser General Public License version 3

## Ponder

This project compiles against Ponder because Create's block entities implement
one of its interfaces. Ponder is a required dependency of Create itself and is
supplied by the target modpack; no Ponder code or assets are bundled here.

- Component: Ponder 2.4.0 for Minecraft 1.21.1
- License: as published by the Create team

## Third-party dependencies

Minecraft, NeoForge, TaCZ, FMIC, CIBR, Create and other third-party libraries,
mods, and data packs retain their respective licenses. They are not
relicensed by this project. Bundling a separately licensed component does not
change that component's license or attribution requirements.

The FMIC-WolfeinRace, Charge into Battle: Reboot, and TaCZ: Classics Reborn
GunPack archives are not distributed in this repository or in this project's
release JAR. The client prompt only links to their selected official
CurseForge file pages and copies archives the user supplies locally.
