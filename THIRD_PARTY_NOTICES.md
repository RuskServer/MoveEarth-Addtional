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

## SMAA (Enhanced Subpixel Morphological Antialiasing)

The anti-aliasing shaders include the SMAA reference implementation and its
precomputed lookup textures, licensed under the MIT License.

- Files: `assets/moveearth_addtional/shaders/include/smaa.glsl` (from
  `SMAA.hlsl`), `assets/moveearth_addtional/textures/upscale/smaa_area.png` and
  `smaa_search.png` (converted from `AreaTex.h` and `SearchTex.h`)
- Source: https://github.com/iryoku/smaa
- Copyright: Copyright (C) 2013 Jorge Jimenez, Jose I. Echevarria, Belen Masia,
  Fernando Navarro and Diego Gutierrez
- Changes: line endings normalized and trailing whitespace removed; one
  non-ASCII diagram character and the ASCII-art logo removed from comments;
  lookup tables stored as PNG images.

See `LICENSES/MIT-SMAA.txt` for the license terms.

## AMD FidelityFX Super Resolution 1.0

The upscaling shaders include the EASU and RCAS passes of AMD FidelityFX Super
Resolution 1.0 and the subset of `ffx_a.h` they use, licensed under the MIT
License.

- File: `assets/moveearth_addtional/shaders/include/ffx_fsr1.glsl`
- Source: https://github.com/GPUOpen-Effects/FidelityFX-FSR
- Copyright: Copyright (c) 2021 Advanced Micro Devices, Inc.
- Changes: only the 32-bit GLSL helper definitions needed by EASU and RCAS are
  kept, so the shaders compile as GLSL 3.30; the FSR passes are verbatim apart
  from trailing whitespace.

See `LICENSES/MIT-AMD-FidelityFX-FSR.txt` for the license terms.

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

The dedicated-server JAR contains an isolated nested JDA runtime. Its license
bundle is generated at `META-INF/licenses/discord-runtime/<group>/<module>/<version>/`
inside that nested JAR. Each dependency gets an `ABOUT.txt`, its applicable
license text, and any upstream legal files found in its original JAR under
`upstream/`. This namespace prevents identically named `LICENSE` and `NOTICE`
files from different dependencies overwriting each other. An unreviewed new
runtime dependency fails the build until its license is added to the inventory.
Runtime packages are relocated into a MoveEarth-private namespace to prevent
module conflicts with libraries supplied by NeoForge or other mods.

The current runtime inventory is JDA, Trove4j (`LGPL-2.1`), Jackson Core /
Databind / Annotations, Tink, JSR-305, Gson, Error Prone Annotations,
Protocol Buffers Java (`BSD-3-Clause`), nv-websocket-client, OkHttp, Kotlin
stdlib, JetBrains Annotations, Okio, and Apache Commons Collections 4. The
components other than Trove4j and Protocol Buffers use Apache-2.0 according
to their published Maven metadata. Their exact versions are recorded in the
generated `ABOUT.txt` files. The player JAR does not contain this JDA runtime.

Trove4j's original source for the bundled 3.1.0 version is available as the
[Maven Central source artifact](https://repo.maven.apache.org/maven2/net/sf/trove4j/core/3.1.0/core-3.1.0-sources.jar).
The bytecode package relocation applied to it is specified in this project's
`build.gradle` (`relocateDiscordBotRuntime`); the LGPL-2.1 text is included
in both release JARs and in Trove4j's directory in the nested server runtime.
Protocol Buffers' BSD-3-Clause notice is copied from the
[upstream v28.2 license](https://github.com/protocolbuffers/protobuf/blob/v28.2/LICENSE).

## Integration-only mods

The following mods are separately supplied by the target modpack. MoveEarth
references their APIs or hooks their runtime behavior but does **not** copy
their JARs, source code, models, textures, or sounds into its release JARs.
Their licenses apply to their own distributions; MoveEarth's GPL does not
relicense them.

| Mod | Upstream license | Source / license |
| --- | --- | --- |
| Create | MIT for code; upstream assets are All Rights Reserved | https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/LICENSE.md |
| Create Aeronautics / Simulated | MIT for code; upstream assets are All Rights Reserved | https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/main/LICENSE.md |
| Sable | PolyForm Shield License 1.0.0; **not MIT or an open-source license** | https://github.com/ryanhcode/sable/blob/main/LICENSE.md |
| Sable Companion | MIT | https://github.com/ryanhcode/sable-companion/blob/main/LICENSE |
| Create Big Cannons | MIT for source code and localization; CC BY-NC-SA 4.0 for handmade textures and models | https://github.com/Cannoneers-of-Create/CreateBigCannons/blob/create-v6-1.21.1/LICENSE.md |
| Mekanism / Mekanism Generators | MIT | https://github.com/mekanism/Mekanism/blob/1.21.x/LICENSE |
| TaCZ | GPL-3.0 for code; CC BY-NC-ND 4.0 for assets | https://modrinth.com/mod/timeless-and-classics-zero |
| PlayerRevive | LGPL-3.0-only (as declared by the supplied NeoForge JAR) | https://modrinth.com/mod/playerrevive |

Sable's PolyForm Shield terms are especially distinct from Sable Companion's
MIT terms. Integration with Sable is not permission to redistribute Sable or
to treat its code as MIT-licensed.

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

Minecraft, NeoForge, FMIC, CIBR and other third-party libraries,
mods, and data packs retain their respective licenses. They are not
relicensed by this project. Bundling a separately licensed component does not
change that component's license or attribution requirements.

The FMIC-WolfeinRace, Charge into Battle: Reboot, and TaCZ: Classics Reborn
GunPack archives are not distributed in this repository or in this project's
release JAR. The client prompt only links to their selected official
CurseForge file pages and copies archives the user supplies locally.
