# v2.2

## Entity Occlusion Culling (SubChunk VisGraph)

- **SubChunk VisGraph Packet Control**: Integrated a sub-chunk (16×16×16) visibility graph and view frustum culling engine into `ChunkMap$TrackedEntity` via Mixin. Dynamically pauses packet broadcasting (`ItemEntity` and `ExperienceOrb`) for occluded or out-of-view entities, eliminating ESP exploitation and drastically reducing client-server network traffic.
- **Ultra-Low Overhead & O(1) Evaluation**: Pre-calculates 6-face inter-connectivity bitmasks per section on block changes, allowing BFS exploration and entity visibility checks to execute in constant $O(1)$ set lookup time without ticking voxel raycasts.
- **Pop-in Prevention & Near-Distance Bypass**: Enforced an unconditional 3.5m near-distance bypass around players and a +30-degree FOV margin to guarantee zero pop-in latency upon turning corners and preserve full compatibility with item magnet / auto-collector mods.
- **Broad Mod Compatibility & Fail-Safe**: Leveraged vanilla `BlockState.canOcclude()` and `isSolidRender()` to automatically recognize third-party mod blocks (pipes, machines, glass, fences) while failing safe to visible upon unrendered or exceptional states.
- **Configurable Control Engine**: Added `SubChunkOcclusionConfig` with toggles for feature enablement, bypass radius, FOV margin, search depth, tick intervals, and entity type filters.

## Player Analytics & Web Dashboard

- **Interactive 2D Spatial Heatmap Canvas Viewer**: Integrated a rich HTML5 Canvas 2D grid map into the web dashboard featuring pan/drag, mouse wheel zooming, origin centering, data autofit, dynamic coordinate/axis rendering, thermographic density coloration, hover inspection tooltips, altitude (YBand) / relationship (Relation) filtering, and bidirectional focus synchronization with the top density ranking table.
- **Web Dashboard & REST API**: Provided `/api/summary`, `/api/heatmap`, `/api/top-players`, `/api/groups`, `/api/health`, and single-player inspection endpoints, with export archiving and configurable authentication (`config/moveearth_analytics.properties`).
- **High-Throughput SQLite Storage Engine**: Implemented `SqliteAnalyticsStorageEngine` operating in SQLite WAL mode (`PRAGMA journal_mode = WAL`) under `<world>/moveearth/analytics/analytics.db` with background daemon transaction batching, automated retention purges, and auto-healing schema migrations (Version 3).
- **JST 19:00 Open Day Cycle Alignment**: Aligned all retention and aggregation windows to JST 19:00 (`(bucket_at - 36000) / 86400`) and enforced per-open-day 10-minute active thresholds (`HAVING SUM(active_seconds) >= 600`) for individual `activeDays` and server-wide `activeUniquePlayers`.
- **Realtime Session & Intrusion Tracking**: Integrated non-blocking `SessionTracker` measuring active vs. AFK duration, combining online player states into realtime queries, alongside `IntrusionTracker` for detector block entry-to-exit intrusion sessions.
- **Offline Player Analytics Commands**: Replaced `EntityArgument.player()` with `GameProfileArgument.gameProfile()` in `/analytics` command to inspect historical activity for offline players and base owners.

## Jobs Compatibility

- **Farmer's Delight 1.3.3 Support**: Added optional Farmer's Delight support to the Farmer job. Mature cabbages, onions, tomatoes, rope-grown tomatoes, and rice panicles now grant Farmer XP.
- **Verified Right-Click Harvest Rewards**: XP is awarded only after the server confirms that the mature crop was successfully harvested and reset to an immature state.

## PvP Loadouts and Combat Balance

- Replaced the FMIC PvP presets with TaCZ standard modern firearms: SCAR-L, MP5A5, AA12, and the semi-auto-only SKS Tactical, with a P320 sidearm for every role.
- Retuned close-range body-shot damage for full Protection IV iron armor, 20 health, 1.5x headshots, and zero armor penetration. Target TTK is approximately 343-400 ms depending on weapon cadence.
- Kept standard magazine capacities and installed only compatible sights and lasers; AA12 and SKS Tactical use sights without lasers.

## TPA

- Explicitly declared permission level 0 on the player-facing `/tpa`, `/tpaccept`, `/tpdeny`, and `/tpcancel` command roots, matching `/stats` for hybrid server command-permission compatibility. The previous `/tpacancel` spelling remains as an alias, and `/tpa admin` remains restricted to permission level 2.
- Added the collision-resistant `/moveearthtpa` command tree with `request`, `accept`, `deny`, `cancel`, and `status` operations, and changed player-facing target arguments to online player names so they do not depend on privileged entity selectors.

## Compatibility

- Updated the mod version to `2.2` while keeping the network protocol at `2.0-jobs1` because no packet schema changed.
- Kept v2.2 network-compatible with v2.0 and v2.1 clients. Older clients continue to display the previous FMIC preset names, while the v2.2 server authoritatively issues the new TaCZ loadouts.

# v2.1

## GunPack Distribution and Setup

- Stopped distributing the required TaCZ GunPacks as bundled project content. FMIC-WolfeinRace, Charge into Battle: Reboot, and TaCZ: Classics Reborn are now obtained separately from their selected official CurseForge file pages.
- Added a pre-main-menu prompt when the `fmic`, `cib`, or `ccrp` GunPack namespace is missing. It is shown before `TitleScreen` opens so menu customization mods such as FancyMenu do not render underneath it, and each missing pack has a button that opens its selected official download page.
- Added drag-and-drop installation for downloaded GunPack ZIP files. Archives are checked for a valid root `gunpack.meta.json` and an expected namespace before being copied to the client `tacz` directory without extraction or filename changes.
- Added manual GunPack-folder access, installation rechecking, copy-through-temporary-file behavior, rejection of unrelated or oversized archives, and a restart reminder.
- Documented that dedicated-server administrators must install the same three GunPacks separately because the title-screen installer is client-only.

## Compatibility

- Updated the mod version to `2.1`.
- Kept the network protocol at `2.0-jobs1` because this release does not change packets or server-authoritative gameplay. v2.1 remains network-compatible with v2.0.

# v2.0

## Jobs and Progression

- Added five server-authoritative jobs: Miner, Lumberjack, Farmer, Hunter, and Crafter. Players can keep progress in every job and have up to two active jobs at once.
- Added per-job levels from 1 to 50, quadratic XP curves, one shared Job Point per level-up, fractional XP, and persistent UUID-based progress.
- Made job definitions, XP curves, block rewards, entity rewards, crafting rewards, and activity descriptions data-pack configurable.
- Added renewable Job Point income. Every 500 effective job XP grants 1 shared point, capped at 4 renewable points per player per one-hour window; level-up points remain separate bonuses.
- Persisted partial renewable-point XP, the hourly count, and the window timer across reconnects and restarts. XP beyond the hourly cap is not banked for the next window.
- Continued accepting effective XP at level 50 for renewable points and lifetime-XP rankings.
- Removed missing job definitions from active selections without deleting their saved progress. If every definition fails to load, existing selections are preserved instead of being erased.

## Jobs and Anti-Abuse Rules

- Added individually balanced Miner rewards for stone variants, Nether geology, dripstone, and vanilla ores.
- Prevented XP from player-placed reward blocks, invalid tools, creative or spectator play, and activity inside the PvP arena.
- Added compact per-section placement tracking with migration from the previous coordinate format, piston movement tracking, and explosion cleanup.
- Added a per-job one-minute XP soft cap: the first 500 XP is awarded at full rate and overflow is reduced to 10%.
- Applied the per-minute XP soft cap only to jobs the player has actually selected, so activity before joining a job does not reduce later rewards.
- Excluded Hunter targets created by spawners, spawn eggs, dispensers, or commands, and excluded automated or unlisted reversible crafting from Crafter rewards.

## Jobs Interface and Administration

- Added the unified `/jobs` screen for job selection, levels, XP progress, shared points, renewable-point progress, and activity descriptions.
- Added a four-second vanilla boss bar after Jobs actions showing the job level, name, current XP, next-level XP, and latest XP gain. Repeated actions update the bar and extend its display time.
- Added `/jobs status`, `/jobs list`, `/jobs join`, `/jobs leave`, `/jobs info`, and `/jobs top` as permission-level-0 commands.
- Added a permission-level-2 management panel for inspecting online players, granting XP, changing points, and resetting job data with confirmation and audit logging.
- Cleared destructive reset confirmation whenever an administrator changes the target player, preventing accidental one-click resets of the new target.

## Rankings

- Added per-job leaderboards to the Jobs screen and `/jobs top <job>`.
- Included offline players using their persisted last-known name and ranked entries by level, current XP, lifetime XP, name, and UUID.

## Job Point Shop

- Added a persistent Job Point shop to the Jobs screen with exact ItemStack templates, including stack count, enchantments, durability, and modded gun customization.
- Added permission-level-2 product management. Administrators can register their current main-hand stack, set its price and per-player purchase limit, suspend or resume sales, update products, and remove products.
- Added server-side permission, product, price, purchase-limit, point-balance, and inventory-capacity validation. Failed capacity checks do not consume points.
- Blocked purchasing and product management during active PvP sessions, persisted purchase counts, and added audit logs for product changes and purchases.
- Preserved the selected product by UUID when switching between purchase and management views, preventing updates, toggles, or deletion from targeting a different product.

## Compatibility

- Updated the mod version to `2.0` and the network protocol to `2.0-jobs1`.
- v2.0 clients and servers must use the same updated JAR; v1.9 clients are not network-compatible.

# v1.9

## Beginner Protection

- Added a one-time beginner kit for players with less than eight hours of play time. It is granted automatically on the first login and can also be claimed through `/starterkit`.
- The kit contains a loaded CIBR Type 38 rifle, eight reserve 6.5x50 rounds, sixteen cooked beef, and a full set of enchanted iron armor.
- Kit armor has Protection II and Unbreaking I; the boots also have Feather Falling II.
- Persisted claim status prevents repeated claims. Administrators can inspect players, grant a kit regardless of play time or prior claims, and reset claim status through permission-level-2 command subcommands.
- Explicitly assigned permission level 0 to player kit claims and permission level 2 to administrative kit operations.

## PvP Loadouts and Combat Balance

- Replaced hotbar-based weapon selection with a server-authoritative loadout selector available through `/pvp`.
- Added four FMIC loadouts: Assault with RA39, Rusher with EF_SMG, Breacher with EF_SG, and Marksman with NSR20. Every loadout also includes a customized G45 sidearm.
- Added loadout cards showing each role, primary and secondary weapon, attachments, intended range, and estimated body-shot TTK.
- Players can change loadouts while queued. Loadouts are locked after the match starts, and forged or unknown preset IDs are rejected by the server.
- Players may join an active match from the loadout screen. Late entrants are assigned to the smaller team and receive the same protected inventory snapshot, fixed loadout, combat health, HUD, and teammate markers as starting participants.
- The loadout screen now shows the live number of registered or active participants and updates while the screen remains open.
- Fixed sights and lasers are installed on issued guns and locked against removal. Magazine extensions are not used, preserving each weapon's standard magazine capacity.
- Match equipment is now limited to the selected primary and G45 sidearm instead of issuing every FMIC preset weapon.
- Added PvP-only FMIC damage scaling for Protection IV iron armor, a fixed 1.5x headshot multiplier, and no armor bypass. Target body-shot TTK is approximately 200-300 ms, with NSR20 at approximately 333 ms due to its native 360 RPM cadence.
- PvP now temporarily uses 20 maximum health so the configured weapon multipliers match their TTK target. The player's original maximum-health base value is restored after the match and retained by crash recovery.
- Preserved native distance falloff while applying the PvP damage scaling.
- Reduced passive regeneration by maintaining hunger at 18 with zero saturation instead of continuously restoring a full hunger and saturation bar.
- PvP loadouts contain no launcher-class weapons. Active PvP players are also protected from the vanilla floating-too-long kick.

## Team Identification

- Added a green `ALLY` name marker and glowing outline to teammates during active PvP matches.
- Enemy name tags remain hidden, making ally and enemy identification explicit without revealing enemy positions through markers.

## Announcer and Match Presentation

- Added WARLORD announcer cues for match start, first blood, multikills, kill streaks, revenge kills, objective control, final stand, victory, defeat, and match end.
- Added three-second full-screen result effects for `VICTORY`, `DEFEAT`, and `DRAW`, including the final RED and BLUE scores.
- If every member of one team leaves, the remaining team now receives the victory result instead of deriving the outcome from the unfinished objective score.
- Match-result effects replace a remaining final-kill killcam and are cleared immediately if the player exits the match.
- WARLORD audio is provided by VoiceBosch under CC BY-SA 4.0. Full attribution is included in `META-INF/NOTICE-WARLORD-AUDIO.txt`.

## Match Statistics

- Added a chat result table at the end of every PvP match showing team, kill rank, kills, deaths, and damage dealt for each remaining participant.
- Rankings are sorted by kills, then damage dealt, then fewer deaths, and finally player name.
- Damage statistics use damage after armor, enchantment, and other reductions. Friendly damage, environmental damage, and overkill beyond the target's remaining health are excluded from damage dealt.

## Server Administration and Compatibility

- Added `/notice <message>` for permission-level-2 command sources. It displays a custom message and notification sound to every online player and accepts up to 256 characters.
- Relicensed the current source tree and future distributions of the original code and assets from LGPL-3.0-only to GPL-3.0-only. Previously received LGPL-3.0-only copies retain their existing rights.
- Preserved the separate CC BY-SA 4.0 license and VoiceBosch attribution for WARLORD audio, and the MIT terms for the stonecutter compatibility implementation and bundled FirstDark Discord RPC classes. All applicable license texts and notices are bundled in the JAR.
- Updated the PvP network protocol to `1.9-pvp2`. v1.9 servers and clients must use the same v1.9 JAR; older clients are not network-compatible.

# v1.8

## Player Detector

- Fixed bank account names displaying an account-retrieval error on dedicated multiplayer servers. Account names are now resolved on the server and synchronized with their bank references.
- Continued to validate bank-account access and payment configuration on the server. Invalid or stale account references are excluded from the selection list.
- Prevented the detector's invisible dummy shulker from being moved by transport, pushing, mounting, gravity, or other mod mechanics.
- The dummy is now returned to the detector position at the end of every server tick, and common entity-interaction attempts are cancelled.
- Added ownership position and dimension data to detector dummies. Captured, duplicated, displaced, or cross-dimensional stale dummies are discarded when loaded.
- Fixed cleanup potentially removing a dummy belonging to another nearby detector.

## Voting Rewards

- Added the server-side `/moveearthvotereward <player>` command for permission-level-2 command sources.
- Added six equally likely rewards: 2 Gold Coins, 3 Gold Coins, 8 End Stone, 8 Gunpowder, an Efficiency V Diamond Pickaxe, or a Mending Diamond Pickaxe.
- Rewards that do not fit in the target player's inventory are dropped at their position.
- Added a server-wide broadcast announcing the voting player and the reward received.
- Added server logging for successful vote-reward grants.

## Compatibility and Fixes

- Fixed existing players being mistaken for first-time players and receiving an unintended first-login random teleport after random-spawn tracking was introduced.
- Added integration with LocalizedChat NeoForge 5.2.1 to record the list of players who received each localized chat message in the server log.
- Integrated the stonecutter crash fix from `moveearth_patch_unti-1.0-SNAPSHOT`. Stale stonecutter recipes are cleared when the input is removed, preventing the server-environment crash.
- Preserved attribution to iesuok, the original stonecutter patch author, and the implementation provenance in `META-INF/NOTICE-moveearth_patch_unti.txt`.
- Updated the network protocol to `1.8-pvp3`. The server and clients must use the same v1.8 JAR.

# v1.7

## KOTH PvP Event

- Added a custom interface available through `/pvp` and a queue system that does not require a physical lobby.
- Players can continue playing normally while queued. Inventories are stored, match equipment is issued, and players are transferred to the dedicated dimension only when an administrator starts the match.
- Added a dedicated PvP dimension, RED and BLUE spawn points, and configurable KOTH capture-zone boundaries.
- Added event hosting and match controls through `/pvp admin open`, `close`, `start`, and `stop`.
- Added a custom HUD displaying team scores, the score target, remaining time, and capture status.
- Player name tags are hidden during PvP matches, while allies are highlighted using their team color.
- Defeated players enter Spectator mode and receive a four-second killcam that highlights their killer.
- Added kill notifications and a respawn delay. PvP deaths now use the dedicated respawn flow without entering PlayerRevive's downed state.
- Match results remain visible for five seconds before players are restored to their pre-match state.

## Equipment and Match Rules

- Fixed iron armor being removed and re-equipped every tick, which repeatedly played armor equip sounds during PvP matches.
- PvP guns now have an automatically replenished dummy-ammo reserve, providing unlimited ammunition while preserving magazine reloads.
- Limited brought-in equipment to one TaCZ gun selected from the player's hotbar through the participation interface.
- Players receive a full set of iron armor with Protection IV, a filled magazine, and supplied dummy ammunition.
- Health, hunger, air supply, and fire state are reset when the match starts and after each respawn.
- Hunger is kept full throughout the match.
- Disabled friendly fire.
- Disabled block breaking and placement, container access, item dropping and pickup, and offhand swapping during matches.
- Prevented explosions and TaCZ projectiles from damaging arena terrain.
- TaCZ block-hit events are no longer cancelled. Damaged blocks are restored instead, preserving normal projectile disposal and explosion behavior.
- Blocked combat between PvP participants and outsiders. Non-participants entering the arena are returned to the Overworld.
- Disabled natural mob spawning in the arena and periodically remove mobs already present there.

## Inventory Protection and Recovery

- Stored each player's inventory, position, dimension, game mode, health, hunger, experience, potion effects, selected slot, and scoreboard team before entering a match.
- Persisted PvP session snapshots in world SavedData.
- If a match is interrupted by a server crash or restart, the player's pre-match state is restored automatically on their next login.
- Prevented item duplication through dropping, moving, or extracting temporary match equipment.
- Fixed PvP HUD, ally lists, killcam data, and glowing states remaining on the client after disconnecting.

## Tasks and Rewards

- Added a dedicated task screen available through `/pvp tasks` or the Tasks button in the participation interface.
- Added Daily and Event tabs, progress bars, pagination, reward icons, and manual claim buttons.
- Added the following tasks:
  - 5 kills: 25 Weapon Points and 16 Iron Ingots
  - 3 kills within 8 blocks: 35 Weapon Points and 16 Gunpowder
  - Control the zone for a total of 120 seconds: 30 Weapon Points and 24 Nether Quartz
  - Complete 3 reward-eligible matches: 50 Weapon Points and 8 Gold Ingots
  - Win 2 reward-eligible matches: 100 Weapon Points and 1 Netherite Ingot
  - Win 5 reward-eligible matches: 250 Weapon Points and 1 Nether Star
- Daily tasks reset every day at 19:00 JST. Event tasks reset when a new PvP hosting period begins.
- Existing Weapon Points and unfinished kill, close-range kill, and zone-control progress are migrated from the previous fixed task format.
- Task completion and claim status are validated server-side to prevent duplicate claims and forged packet requests.
- If the complete material reward cannot fit in the player's inventory, neither the materials nor the Weapon Points are awarded.
- Task rewards, weapon-crate exchanges, and weapon-crate opening are disabled during matches.
- Reward-eligible matches require at least two active players on each team. Repeated kills against the same opponent have a 60-second reward cooldown.

## Weapon Crates

- Added weapon crates that can be exchanged for 100 Weapon Points.
- Each crate awards one TaCZ gun and attempts to install up to two compatible attachments.
- Rewards are selected from the guns and attachments actually loaded by TaCZ instead of a hard-coded list.
- Prevented invalid rewards caused by missing gun-pack IDs or incompatible attachments.
- Initial ammunition now uses the selected gun's actual magazine capacity.

## Standard Random Respawning

- Redesigned random respawning after ordinary deaths when the player has no bed or respawn anchor set.
- Removed the behavior that selected another online player as the spawn center. Players are now distributed within a 750-to-4,000-block annulus around the world's shared spawn.
- Spawn candidates at least 384 blocks from other players and 768 blocks from the player's previous random spawn are preferred.
- Up to 96 locations are checked. If no candidate meets the strict distance requirements, the safest and most distant valid candidate is selected.
- Candidate validation checks the floor, headroom, fluids, powder snow, cacti, campfires, other hazardous blocks, and the world border.
- Players receive ten seconds of Damage Resistance and Fire Resistance after a random respawn.
- The same safety and distribution rules are applied to first-login random spawning.

## Fixes and Compatibility

- Explicitly assigned permission level 0 to player-facing `/pvp` commands and permission level 2 to `/pvp admin` hosting and arena-management commands.
- Fixed random respawn surface detection for unloaded chunks; heightmaps are now queried after explicitly loading the target chunk.
- Random-spawn searching now stops at the first fully valid candidate to avoid generating dozens of chunks during one respawn.
- Fixed random respawn teleports being rejected while the respawn event still referenced a removed player entity.
- Random respawn teleports are now deferred until respawn finalization, resolve the current player by UUID, and only record success after verifying the destination.
- Fixed PvP fatal-hit detection to use damage after armor, enchantment, and other reductions.
- Fixed match results disappearing immediately after the match ended.
- Added validation for the RED spawn, BLUE spawn, and capture-zone settings before a match can start.
- Fixed the weapon-crate item model.
- Updated the PvP network protocol to `1.7-pvp3`. Both the server and clients must use the same v1.7 JAR.

# v1.4

## Changes

- Fixed PlayerRevive downed-state detection.
- Disabled TACZ shooting, melee attacks, and gun item interaction while downed.
- Replaced the standard death screen with a VHS-style visual effect.
- Added several randomized messages shown on death.
- Added automatic respawn after approximately five seconds.
- Press `Esc` to skip the waiting time and respawn immediately.
- Added calm menu music that plays only while the death screen is shown.
- Added server-side bank account access validation when configuring payments.
- Fixed dummy shulker cleanup so normal shulkers are not removed.
- Added whitelist name validation, entry limits, and online-player verification.

## v1.5

- Set the maximum health of all players to 40.
- Enhanced the death screen with stronger VHS tracking distortion and glitch effects.
- Added typewriter-style text reveal and message fade-in animation.

## v1.6

### Player statistics

- Added the `/stats` command and a custom statistics screen.
- Added a 3D player preview and card-based statistics for play time, player kills, deaths, damage, and movement.
- Removed the vanilla screen blur that was incorrectly drawn over the statistics interface.
- Explicitly set `/stats` to permission level 0 so non-operator players can use it on server software that requires a declared level.

### Airship raids

- Added Sable-powered hostile airship raids with manual start, stop, status, and automatic-raid controls.
- Automatic raids are disabled by default and can be enabled with `/airshipraid auto on`.
- Automatic raids check every 30 minutes with a 10% chance and apply a 12-hour cooldown per targeted player.
- Added normal, elite, and large raid difficulties.
- Added raid announcements, warning sounds, a ten-minute combat limit, and chunk-unload-safe raid tracking.
- Added armed NPC raiders using customized TaCZ firearms, enchanted iron or diamond armor, and guaranteed equipment drops.
- Added rifleman, flanker, and heavy roles with squad memory, cover selection, leading shots, reloading, retreat behavior, strafing, and separation.
- Improved NPC gun accuracy, descent speed, persistence, and raid completion tracking.

### Airship destruction and salvage

- Enlarged the raid airship to approximately 29 x 13 x 16 blocks.
- Added Create Aeronautics envelopes, levitite cores, propellers, a gyroscopic bearing, a burner, and mounted weapon blocks to the airship.
- Added TaCZ projectile damage for the airship hull and levitite cores before troop deployment.
- Destroying four levitite cores or depleting the hull integrity now cancels deployment and sends the airship into a Sable physics crash.
- Added crash survivors that guard the wreck based on raid difficulty.
- Added a fifteen-minute salvage phase, a two-minute cleanup warning, and delayed cleanup while players remain near the wreck.
- Added salvage barrels containing materials and Create Aeronautics components.
- Added levitite recovery: guaranteed with Silk Touch or a 25% chance without it; explosion destruction does not drop it.

### Compatibility and fixes

- Added required compatibility metadata for Sable 2.0.3+, Create Aeronautics 1.3.0+, and TaCZ 1.1.8+.
- Fixed cargo generation writing into non-barrel machine inventories.
- Fixed raid NPCs naturally despawning and leaving raids permanently incomplete.
- Preserved raid NPC tracking across chunk unloads while correctly removing killed or discarded NPCs.
- Changed raider guns and armor from guaranteed drops to fixed difficulty-based drop chances.
- Added a 60-80% chance for raiders to drop TaCZ 5.56x45 ammunition, with larger stacks on higher difficulties.
