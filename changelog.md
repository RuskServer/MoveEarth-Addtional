# v3.2 — Unreleased

## Interface

- **Vanilla MoveEarth Advancements**: Replaced the custom guide GUI, tracking HUD, JEI screen bridge, item tooltips, and guide network synchronization with a 37-entry vanilla advancement tree opened through the standard `L` screen. The tree covers nation entry, industry, engineering, Siege roles, vehicles, prisoners, and Warehouse PvE; MoveEarth-only actions award criteria only after server-authoritative success. Existing schema-3 guide accomplishments migrate once per player while the old data remains read-only for the transition release.
- **Local Nation Chat**: Replaced the Localized Chat dependency with signed, server-routed proximity chat. Messages reach players within 100 blocks in the same dimension and show the recipient's block distance, the sender's nation and role, and MoveEarth-style colors. The console records the sender and the players who heard each message; the radius is server-configurable.
- **Disconnect-Safe Loading Screens**: Custom loading presentation now ends immediately when a connection closes, preserves the vanilla disconnect reason and error screen, and includes a terrain-receive fallback for disconnects during the level hand-off. Generic screens from other mods are no longer mistaken for loading screens.
- **Analytics Performance Diagnostics**: Persisted the Web API token across restarts and now distinguish missing from stale credentials. The dashboard records one-minute TPS/MSPT samples and lightweight entity/block-entity chunk load estimates, with time-series graphs, hot-chunk rankings, dimension filters, and CSV/JSONL exports. Repeated TPS/MSPT degradation now starts a bounded 10-second sampled profiler for the estimated top chunks, separating entity, block-entity, and scheduled-tick CPU time while avoiding timing and coordinate work outside sampled ticks; operators can also start, inspect, stop, and export profiles manually.
- **Nation Hub Action Layout**: Split the overview actions into two responsive rows so recovery dispatch and vault configuration no longer overlap, including at compact GUI widths.
- **Startup Safety and First-Run Setup**: Added a silent photosensitivity/audio notice, a skippable MoveEarth logo ident, and a first-run custom setup for narrator, subtitles, master/music/effect volume, and reduced UI motion. The vanilla narrator-only onboarding is replaced without skipping later startup checks, and the title menu now enters with subtle logo, panel, and button-highlight motion.
- **Expanded Guide Canvas**: Removed the chapter sidebar so the node canvas can use the full left side of the guide. Added a framed canvas, stronger node cards, wider spacing, and shorter edge-to-edge dependency lines for clearer progression at a glance.
- **Unified Loading Screens**: Server connection, world data and resource preparation, level reception, chunk generation, and world saving now use the main menu's starfield and meteor presentation. A compact translucent bottom HUD combines rotating gameplay tips, live status, a progress bar, and the connection cancel action while leaving room for a larger central logo.
- **Roomier Main Menu**: Increased outer, panel, control, and changelog spacing at normal resolutions. The navigation panel now fits its contents and floats vertically beside the changelog instead of stretching across all available height.

## Gameplay

- **19:00–23:00 JST Opening Hours**: Shortened the dedicated-server play window to 19:00 through 22:59 JST. Closing notices now run at 22:30, 22:50, 22:55, and 22:59, followed by disconnect at 23:00. TPA/PvP opening-day resets, analytics day boundaries, and all server-open-time Siege, recovery, healing, and captivity clocks use the same 19:00 boundary.
- **Reinforced Wrench Protection**: Create's wrench and other tools in the common wrench tag can no longer rotate, pick up, or reconfigure reinforced blocks. The server-side guard covers both territory reinforcement and Sable vehicle armor, including reinforcement that is curing, damaged, or temporarily disabled.
- **Owned Reinforcement Removal**: Sneak-use the welding tool to remove reinforcement from a 1x1, 3x3, or 5x5 area without mining the underlying blocks. Only authorized members can strip their own territory or vehicle armor; removal consumes tool durability, returns no material, and preserves combat repair delays.
- **Hydraulic Power Balance**: Create water wheels now provide 75% base capacity. Source quality applies at half penalty strength, leaving standalone wheels at roughly 51–75% rather than multiplying weak sources down twice. Player-made or vanilla flow has 50% quality, generated MoveEarth rivers scale from 35% to full quality, and nearby active wheels share a configurable 32-block budget after six small-wheel equivalents. Large wheels consume four equivalents, unloaded chunks are never forced, and all values are exposed through a dedicated server config.
- **Portable Engine Balance**: Create: Simulated portable engines are capped at 32 stress capacity per RPM, producing at most 1,024 SU on ordinary fuel and 2,048 SU while superheated. The cap covers every dyed variant, preserves lower operator-configured values, and can be disabled or adjusted in the existing Aeronautics server config. The original inexpensive crafting recipe is replaced with a 3x3 Mechanical Crafter assembly using precision mechanisms, a flywheel, brass casings, the engine assembly, sturdy sheets, and a blast furnace.
- **Industrial TaCZ Workbenches**: The gun, ammunition, and attachment workbenches now require appropriate Create components as permanent factory investments. The gun table uses a precision mechanism and brass casing, the ammunition table consumes a mechanical press and copper sheets, and the attachment table uses an electron tube and precision mechanism; individual weapon and ammunition recipes remain unchanged for this first integration step.
- **Counter-Capture Eligibility**: Only able regular defenders can advance or hold counter-capture progress. Defending mercenaries alone no longer freeze the gauge; downed and captive players are excluded from both sides of presence checks.
- **Recovery Wall Quality**: New Siege records persist the opening wall-health target and block count. Recovery scores actual HP within the same block limit, preventing cheap block spam or late-war destruction from reducing the requirement. Existing recovery episodes retain their legacy count-based targets.
- **Transport Landing and Cargo Access**: Removed Bastion dismount cancellation and forced-return triggers, clearing legacy pending returns on login. Building and pearl restrictions remain. Members can access and place storage on their own connected Sable vehicle outside home territory.
- **Siege Loot Rights**: Added server-open-time loot windows: no enemy access before fall stage 2, outer-area access for the regular attacker during stages 2–3, and a configurable 30-minute post-settlement window. Core-center storage is excluded before settlement and the defender's vault is always excluded. Solo attacks remain personal; third parties and mercenaries receive no ownership rights. Peace, withdrawal and successful counteroffensives revoke the matching entitlement.
- **Storage Wreckage**: Exploded tagged containers now move their contents into restart-safe, non-automatable wreckage instead of scattering or deleting items. Recovery rechecks ownership and Siege rights on every action; repeat explosions, hoppers, Create arms, funnels and chutes cannot bypass the boundary. Unsupported mod inventories are preserved and logged instead of being destroyed unsafely.
- **Vehicle Loss and Prisoner Transport**: Vehicle HP transitions to zero now create one public war-history record and a time-limited cargo entitlement for the attributed attacker. Escorting players can load one captive into an active owned vehicle with restraints and sneak-use on its core; the existing three-open-hour deadline is unchanged, bindings survive restart, and vehicle destruction rescues the captive.

- **Vehicle Core Repairs**: Sneak-right-click your vehicle core with a welder and offhand iron to repair it. Core or reinforced-body hits impose a persistent 120-second emergency window (5 HP every five seconds, capped at 50%); otherwise repairs restore 30 HP every two seconds. Successful repairs consume one iron ingot and one tool durability. Vehicle-wide deadlines survive movement, assembly and restart, and multiple repairers share the same cooldown. Zero-HP cores can recover only after the quiet period. Values are server-configurable; heavy-cannon deployment timers are not implemented.

- **Breach and Engineering Combat**: Added an exposed-core sabotage interaction using a welder and four offhand TNT: interruptible 20-second planting, a 40-second audible fuse, nearby bossbar warnings, and a five-second friendly defuse. Successful sabotage deals 20% maximum core HP before normal defense scaling. Attempts cancel on shutdown, disconnection or invalid combat conditions; consumed materials are not refunded. Heavy CBC point hits gain a configurable territory-core multiplier without increasing entity or vehicle-core damage. Damaged reinforcement now retains a persistent repair cooldown across block replacement and Sable assembly; damage during construction stops automatic HP restoration. See `archive/plans/mobile_artillery_plan.md` for controls, test cases and the remaining deployment/vehicle-repair stage.

- **Connected River Geometry**: New terrain tiles preserve downstream channel connectivity and export spatially indexed river segments, avoiding narrow diagonal gaps from interpolated distance fields. River water levels are constrained downstream and channel beds use the same water datum; regenerated tiles and a new test world are required to assess in-game aquifer and terrain-noise behavior.
- **Prison Intake Territory Diagnostics**: Prison intakes now validate against the viewer's nation before an escort starts and switch to the recorded holding nation during an escort, preventing valid home-territory intakes from being reported as outside active territory.
- **Worldgen-Safe Random Spawning**: Wilderness and nation onboarding now prefer a persistent pool of safe positions observed in loaded chunks and asynchronously verify that any sampled fallback is already stored at full status before loading it. Login no longer generates arbitrary unknown chunks or repeatedly restarts an active search, preventing expensive Terrano-style terrain generation from stalling the server; an exhausted or timed-out search falls back cleanly to the normal spawn with a retry cooldown.

- **Post-War Recovery**: Capital defeat now opens a persistent recovery episode measured in server-opening time. The nation hub shows resealing, restored reinforcement, upkeep, support eligibility, rebuilding-protection waiver, optional rival designation, and bounded public or nation-only war history.
- **Recovery Fund**: Added a server-controlled recovery fund with admin allocation, treasury donations, objective-gated and cooldown-limited aid, upkeep subsidies, dispatch subsidies, global/nation/episode caps, reserved-balance accounting, and conservative review handling for interrupted external transactions. Fund and money movement remain disabled by default until configured.
- **Dispatch Contracts**: Nations can prepare offensive or defensive mercenary contracts through a custom GUI with bilateral approval, participant consent, optional subsidy review, pre-funded maximum cost, server-open-time billing, battlefield and activity checks, cancellation, expiry, and settlement. Dispatch remains disabled by default for staged rollout.
- **Scoped Combat Affiliation**: Active mercenaries receive contract-scoped combat allegiance and a dispatch nameplate marker only for the bound Siege. CBC projectiles, Warnautics bombs and C4, generic delayed explosions, prisoners, and combat-log flows preserve launch/capture-time Siege and contract attribution; unrelated targets never inherit employer rights.
- **Mercenary Siege Rules**: Attacking mercenaries can contest counteroffensives. Defending mercenaries cannot advance or preserve capture progress without an able regular defender. Contracts never grant storage, treasury, building, diplomacy, or territorial ownership rights.
- **Recovery and Dispatch Records**: Siege starts, core falls, counteroffensive victories, peace, recovery milestones, rivals, and contract lifecycle changes are retained in a privacy-filtered history and use the existing embed-first Discord notification path without publishing treasury or live-location details.

- **Dedicated-Server Vehicle Assembly**: Moved the assembly snapshot out of the reserved mixin package and use public accessors from transformed Sable code, fixing an IllegalClassLoadError when assembling swivel-bearing vehicles.

- **Mobile Welding Overlay Fix**: Nearby vehicle armor now synchronizes for players standing outside the craft. Moving armor faces and selection outlines render in local coordinates to avoid precision loss at remote Sable plots, and progress labels follow the interpolated world position.

- **Sable Vehicle Cores and Mobile Reinforcement**: Added nation-owned vehicle cores with configurable core health and recurring nation upkeep. Reinforcement metadata now follows Sable assembly into moving plots, uses Sable-aware reach and damage checks, and recognizes hinge/swivel-connected body chains as one vehicle while detached coreless fragments lose protection. A dedicated vehicle console reports HP, upkeep, connected bodies, and armor count; direct CBC and Warnautics core hits now damage vehicle-core HP without allowing blast-radius damage through intact armor.
- **Vehicle-Core Placement Coordinates**: Placing a core directly on an assembled Sable craft now validates the craft's physical world position instead of its remote plot coordinates, then immediately binds that body to the new vehicle identity.
- **Ender Chests Disabled**: Ender Chests can no longer be opened, placed, used, or crafted. Existing blocks and inventory items are preserved so administrators can remove them safely.
- **Territory-Gated Storage**: Tagged persistent storage, including vanilla containers, storage minecarts and chest boats, can only be placed or used inside the owning nation's effective territory or its initial configuring reservation. Operators retain an emergency bypass, existing contents are never deleted, and modded storage can be extended through data tags.
- **Capital-First Nation Creation**: The custom nation screen now includes an in-world capital selector with a green 3×3-chunk reservation preview. The server validates support, range, overlap, and moving sub-levels, then creates the nation, configuring capital core, and reserved territory as one rollback-safe operation.

# v3.1 — Test Play Release

This release establishes the Season 2 feature set as the v3.1 test-play baseline. Live testing will focus on gameplay balance, performance, compatibility, and operational reliability; the resulting fixes and refinements will target completion in v3.2.

## Test Play Fixes

- **Reliable Starter Supplies**: Automatic starter supplies no longer depend on the retired CIB GunPack. Eligible players receive the kit on login, the rifle uses TaCZ's built-in Kar98 data, and armor and food are still granted if the weapon data is unavailable.
- **Configuring-Core Reinforcement**: A newly placed core's reserved area now permits its nation to create the reinforcement required for initial activation. Active-core outer reservations remain unavailable when upkeep shrinkage removes control.
- **CBC Mortar Consistency**: Source-less CBC custom explosions are now classified from their explosion type. Mortar block transformations are rolled back while reinforcement durability remains, and placing a block into a cleared position removes any stale reinforcement record.
- **Limited Rest Healing**: Beds and lit normal campfires now restore health through one persistent 20 HP allowance per 30 server-open minutes. Beds recover faster after a short settling delay, campfires provide slower stationary field recovery, and combat, movement, downed state, escort, or imprisonment interrupts recovery.

## Season 2 Nations and Unified Interface

- **Server-Authoritative Nations**: Added persistent nation creation, membership, invitations, ownership transfer, disbanding, customizable roles, and fine-grained permissions for members, territory, reinforcement, treasury, diplomacy, Siege, and notifications.
- **Nation Applications**: New players can browse nations and submit one persistent join application. Applications require approval from a member with the member-management permission; approving an online applicant begins a safe national spawn search, while offline approvals resume on the applicant's next login.
- **Diplomacy**: Added persistent inter-nation relations, alliance handling, hostile status, and relation-aware access and combat presentation.
- **Dynamic Nameplates**: Nation tags and relation-dependent name colors now update dynamically for allies, enemies, and other players.
- **Unified MoveEarth GUI**: Nation administration uses the same custom visual and interaction system as `/pvp`, including tabbed management screens, cards, confirmation states, scrollable lists, and chat-overlay suppression. Routine play no longer depends on entering Season 2-prefixed commands or vanilla container screens.
- **Rich In-Game Messages**: Standardized system feedback around the green-gradient `[MoveEarth]` prefix and muted `>>>` separator.

## First-Join Onboarding and Safe Spawning

- **First-Join Choice**: Genuinely new players choose between a wilderness random spawn and applying to an existing nation. Existing players are migrated without being teleported or forced through onboarding.
- **Approval-Only Nation Entry**: Nation applications are persistent and fully server-authoritative. Managers receive an in-game notification and, when configured, a Discord notification.
- **Protected Waiting Screen**: Applicants remain in a locked custom screen instead of a physical lobby. Movement, damage, building, item use, and item dropping are blocked while the server preserves their position, food, and protection state.
- **Waiting Minigame**: Added a reward-free, client-only Flappy-style minigame to the application waiting screen.
- **Nation Spawn Search**: Approved players are placed through the non-blocking chunk search pipeline near an active national core or eligible online member. Unsafe, besieged, or unavailable destinations fall back to wilderness search without synchronously generating chunks.
- **Persistent Recovery**: Pending onboarding, applications, and searches recover safely across reconnects and server restarts.

## Territory Cores, Upkeep, and Presence

- **Territory Core Block**: Added persistent capital and outpost cores with configurable square claim radii, placement validation, health, regeneration delays, exposed and fallen states, and a dedicated configuration GUI.
- **Ground-Level Placement**: Territory cores may be installed at ground level; their activation depends on the reinforced enclosure rather than an arbitrary air-gap requirement.
- **Reinforced Closure Validation**: Core activation uses a bounded, resumable flood-fill search distributed across multiple ticks. Reinforced doors and hatches count as sealed while open, while both halves of a two-block door must be reinforced.
- **Closure Diagnostics**: Core managers can display likely leaks, unreinforced boundary blocks, and external paths through a world overlay instead of diagnosing failures by trial and error.
- **Territory Presence Display**: Entering and leaving controlled land presents the nation name or wilderness state without relying on chat messages.
- **Treasury Upkeep**: Nations can select an accessible Lightman's Currency bank account and pay configurable territory and outpost upkeep through the nation treasury GUI.
- **Overdue Penalties**: Progressive non-payment penalties weaken defenses. Long-term non-payment stops core regeneration and shrinks effective controlled territory to a configurable percentage while retaining the underlying reserved claim and core chunk.
- **Effective-Area Enforcement**: Protection, reinforcement management, Siege checks, Bastion behavior, presence tracking, and maps use the effective upkeep-adjusted radius. Shrunk outer territory cannot be used to install or manage reinforcement.
- **Bastion and Offline Defense**: Added relation-aware territory restrictions, safe mounted-player recovery, offline-defense scaling, and long-absence degradation for abandoned nations.

## Reinforcement and Welding

- **Welding Tool**: Added the welding tool and material-based reinforcement using cobblestone, copper, iron, gold, and diamond tiers.
- **Area Welding**: Mouse-wheel selection changes the welding brush size, allowing authorized players to reinforce or repair multiple blocks in one operation with server-side material and territory validation.
- **Construction Period**: Newly reinforced blocks require an audible activation period and gain durability progressively instead of becoming fully effective immediately.
- **Readable World State**: Reinforced, unreinforced, damaged, disabled, under-construction, and invalid blocks use distinct world overlays and visual treatment. The selected brush has a clearly defined green perimeter.
- **Jade Integration**: Jade displays reinforcement state, durability, construction progress, and Siege-disabled status without exposing the removed directional debug value.
- **Optimized Rendering**: Added an OpenGL 4.5 batched renderer with a compatible fallback, state-grouped geometry, greedy meshing, distance limits, delta synchronization, and cached scan signatures to substantially reduce overlay draw calls and network traffic.
- **Bounded Maintenance**: Construction and stale-entry cleanup operate only on indexed pending entries with per-tick or per-second budgets rather than scanning every reinforced block.

## Siege, Peace, and Territory Capture

- **Two-Stage Siege**: An initial hostile attempt starts a short preparation lock. Only real reinforcement or core damage advances it into the rolling Siege timer and refreshes the active battle.
- **Nation and Solo Attackers**: Nationless attackers can begin a personal Siege instead of being ignored. Their identity, timers, surrender behavior, and settlement outcome are persisted separately from nation attackers.
- **Core Damage Rules**: Core health is not damaged through an intact reinforced wall. Rapid autocannon impacts are rate-limited and cannot bypass reinforcement to delete the wall or core in the same damage sequence.
- **Weapon-Aware Balance**: Create Big Cannons and supported modded munitions use weapon-class damage, distance falloff, exposure multipliers, and per-impact limits, keeping heavy weapons useful without allowing machine-gun fire to erase fortifications instantly.
- **Blast Occlusion**: CBC and Warnautics explosions are now absorbed by the first active reinforced wall along the blast path. Reinforcement and territory or vehicle cores behind that wall no longer receive durability or core-HP damage from the same explosion.
- **Fallen-Core Phase**: Core depletion enters a persistent fallen phase with counteroffensive and capture progress, staged reinforcement disablement, territory loss or occupation settlement, and capital-specific recovery behavior.
- **Peace and Prisoners**: Added peace terms, compensation transfer, surrender and withdrawal operations, retry cooldowns, prisoner state, release handling, and server-authoritative validation.
- **Physical Prisoner Transport**: Downed PlayerRevive combatants now receive a short rescue window before enemies can restrain them. Captives must be physically escorted to a prison intake in valid holding-nation territory; an intake remains usable during an active Siege and can itself become a rescue target.
- **Three-Hour Captivity Limit**: Escort and imprisonment share a non-resettable three-hour maximum measured only during the 18:00–00:00 JST server opening window. Offline captives continue consuming time while the server is open, while server shutdown and closed hours pause it.
- **Combat Log Protection**: PvP combat tags are persistent and shared with TPA restrictions. Logging out leaves an attributable combat body that can be damaged, downed, restrained, rescued, and escorted, with the result restored on reconnect.
- **Captivity Equipment and HUD**: Added craftable restraints and a prison-intake block, action-bar restraint/escort/captivity progress, movement and weapon restrictions, rescues, jail-loss release, and remaining-time display in the nation Siege GUI.
- **Notifications**: Important Siege transitions, core damage thresholds, exposure changes, upkeep warnings, capture results, and conflict endings are delivered only to the appropriate participants.

## Create Warnautics Integration

- **C4 and Land Mines**: Added attributable C4 and mine handling tuned to support infantry attacks without replacing Create Big Cannons as the primary heavy breaching system.
- **Aerial Bombs**: Added size-aware aerial-bomb damage, placement attribution, safe falloff, nation and actor ownership, and Sable sub-level-aware tracking hooks.
- **Cruise Missiles Disabled**: Removed the cruise-missile recipe and block/item use, and reject cruise-missile entities at runtime because their current power and operating model are unsuitable for the Season 2 balance.
- **Optional Compatibility**: Integration resolves Warnautics registry objects only when present and does not require a hard runtime dependency.

## Territory Maps

- **Vanilla Map Overlay**: Added colored territory fills, status borders, capital and outpost markers, and relation-aware labels between vanilla map pixels and decorations.
- **Map Atlases Compatibility**: Map Atlases receives the same territory display through its reuse of the vanilla map renderer, without bundling or directly depending on its API.
- **Map Recipe Adjustment**: Replaced the vanilla map recipe so players can craft maps without a compass.
- **Efficient Synchronization**: Clients request only the dimension of the map being rendered. Core entries reference a packet-local nation index, unchanged snapshots are not resent, and request throttling remains transient instead of being written to player NBT.

## Embedded Discord Bot

- **Integrated JDA Runtime**: The Discord bot is embedded in the mod and configured from the automatically generated dedicated-server configuration. It is disabled by default until a token and settings are supplied.
- **Nation-Scoped Linking**: One-time codes pair a Discord server and Minecraft nation and separately verify Discord users against Minecraft UUIDs. Management operations revalidate both Discord and in-game permissions.
- **Embed-First Messages**: Status, linking, configuration, test, audit, success, warning, error, and nation-event responses use the shared MoveEarth embed design.
- **Persistent Delivery Outbox**: Notifications use bounded persistence, deduplication, acknowledgement, retry limits, exponential backoff, expiry, audit history, and per-nation delivery settings.
- **Safe Discord Content**: User-controlled text is length-limited and neutralizes Markdown and mentions. Allowed mentions are restricted explicitly, and notification channels must grant view, send-message, and embed-link permissions.
- **Failure Isolation**: Synchronous embed or request-construction failures are contained per delivery, in-flight IDs are released correctly, and Discord failures cannot escape into the Minecraft server tick loop.
- **Dependency Isolation**: JDA and its transitive runtime are relocated and verified during the build to prevent Java module package collisions with NeoForge or other installed mods.
- **Split Player/Server Builds**: Release builds now produce a lightweight player JAR without JDA and a dedicated-server JAR with the isolated JDA runtime. Common packet handlers communicate through a JDA-free boundary so both artifacts retain the same mod ID, version, and network protocol safely.
- **Cold Sweat Temperature HUD**: Optional Cold Sweat integration adds an adaptive top-left Celsius display. Comfortable conditions remain visible as a subdued single line, while cold, heat, and extreme conditions expand into increasingly prominent localized warnings using each player's synced temperature and personal survival thresholds.

## Player Guidance

- **Native MoveEarth Main Menu**: Replaced the vanilla title screen with a responsive MoveEarth interface using the `/pvp` visual language, a centered aspect-correct title above translucent control panels, direct server access, a scrollable bundled changelog, and a Discord invitation. A dependency-free static starfield and deterministic batched meteor shower replace video decoding while preserving the original atmosphere at minimal runtime cost.
- **Periodic Chat Tips**: Players receive one unread-first localized tip after five online minutes and then every 30 online minutes. Due tips wait until combat, downed, escort, and imprisonment states have ended.
- **Persistent Tip Controls**: Read state, recent history, opt-out preference, and the next-tip countdown persist across logins. `/tip` provides clickable history, paginated browsing, and enable/disable controls, while server owners can tune pacing in `moveearth_addtional-tips.toml`.
- **Combat Timer Boss Bar**: Replaced the repeated action-bar countdown with a per-player CombatLogX-style boss bar. The bar tracks refreshed and extended tags, turns red for the final ten seconds, disappears during captivity, and restores from persistent combat state after reconnecting.
- **Smooth Gas-Mask Lenses**: Replaced the hard rectangular mask corners with a single-pass GLSL 1.50 lens vignette featuring soft elliptical edges, low-filter condensation, low-oxygen pulsing, and frame-rate-independent equip fades. The lens renders before readable HUD elements so it no longer covers boss bars, crosshairs, temperature information, or chat; shader failures automatically fall back to the legacy overlay.
- **Chat-Front Reinforcement HUD**: The right-side welding and reinforcement panel is redrawn after the chat screen without changing its position, preventing chat rendering from covering its status and progress information.

## Performance, Reliability, and Compatibility

- **Sable Void Failsafe**: Added a configurable server-side recovery guard for Sable collision failures. If any connected body tunnels below the world floor, the whole hinge/swivel chain is lifted above loaded terrain without changing its relative poses and all linear/angular velocity is cleared.
- **Responsive Horizontal Swivels**: Up/down-facing Create: Simulated swivel bearings can now use an experimental Absolute Kinematics-style generic hinge constraint and a base-side servo refresh fallback. This prevents a temporarily unresolved moving plate from leaving the motor on a stale target and then rotating late while retaining Simulated's original joint-face alignment. Side-facing bearings retain the original rotary constraint; server configuration and tested-version guards remain available.
- **GunPack Startup Prompt Removed**: The client no longer scans for previously required TaCZ GunPacks or replaces the main menu with the missing-pack installer screen.
- **Indexed Territory Lookups**: Added indexes for reserved chunks, controlled chunks, core positions, vault chunks, and core IDs. State-only core transitions update affected index entries instead of rebuilding all territory indexes.
- **Tick-Level Upkeep Cache**: Repeated territory checks share one upkeep-penalty result per nation and server tick.
- **Resumable Heavy Work**: Closure searches, chunk generation, cleanup, map synchronization, and reinforcement updates are bounded or split across ticks to avoid large server-thread spikes.
- **Analytics Shutdown Fix**: Removed a shutdown lock inversion between `stopAndFlush`, `Thread.join`, and the storage worker's final flush. Remaining analytics events are flushed in bounded batches and the database is closed by the worker without holding the lifecycle monitor.
- **Expanded Regression Coverage**: Added policy and rendering tests for nations, roles, applications, territory, upkeep, closure searches, reinforcement, Siege, Warnautics damage, Discord text and delivery, maps, and analytics shutdown.
- **Network Compatibility**: Updated the network protocol to `3.0-detector-admin1-oxygen1-s2ui37-prisoners1-recovery1`. Servers and clients must update together because nation, onboarding, reinforcement, prisoner, territory-map, recovery, and dispatch packet schemas have changed.

# v3.0

## Server Opening Schedule

- **18:00–00:00 JST Opening Hours**: Shifted the dedicated-server login window to 18:00 through 23:59 JST. Closing notices now run at 23:30, 23:50, 23:55, and 23:59, followed by the normal-player disconnect at midnight.
- **Aligned Opening-Day Resets**: TPA usage, PvP daily tasks, and analytics opening-day boundaries now roll over at 18:00 JST.

## Voting Reward Variety

- **Create Material Rewards**: Retained all six existing voting rewards and added equally weighted Andesite Alloy, Brass Ingot, Electron Tube, Copper Sheet, Precision Mechanism, and Sturdy Sheet rewards.
- **Missing-Mod Fallback**: If a configured Create reward item is unavailable, that roll falls back to two Gold Coins instead of failing the reward command.

## TPA Travel Balance

- **Role-Based Limits and Cooldowns**: Normal TPA is limited to two successful teleports per opening-day cycle. A success applies a persistent 60-minute cooldown to the traveler and a 15-minute receiving cooldown to the destination host, preventing relay-style mass transport. Cooldowns survive relogging and server restarts.
- **Beginner Rendezvous Allowance**: Players below six hours of total play time receive three lifetime rendezvous teleports that bypass the normal daily use and traveler cooldown. The receiving host receives a shorter three-minute cooldown.
- **Safer Warmup**: Increased warmup from 5 to 20 seconds. Either player moving, changing dimension, entering a vehicle, taking/dealing damage, or entering an invalid PvP state cancels the teleport without consuming uses or cooldowns.
- **Configurable Balance**: Added `moveearth_addtional-tpa.toml` settings for both cooldowns, beginner eligibility and allowance, warmup duration, and combat lock duration.

## Player Detector Names and GUI

- **Per-Detector Names**: Owners can assign a persistent name of up to 32 characters to each player detector from a new GUI tab. Names are validated by the server and retained in block-entity NBT.
- **Delegated Base Managers**: Owners can grant up to 20 UUID-backed base managers permission to edit the shared detector whitelist. Managers cannot rename detectors, configure payment accounts, or grant further permissions, and all whitelist changes are recorded in the server log.
- **Readable Alerts Without Coordinates**: Intrusion and payment-failure messages identify the detector by its configured name without exposing block coordinates.
- **Detector-Level Analytics**: SQLite schema version 4 stores the detector name while retaining the internal position hash as its stable identity. The web dashboard can expand each base into named detector summaries, with legacy databases migrated automatically.
- **Unblurred Detector GUI**: Disabled the vanilla world-background blur for the detector screen while retaining its translucent backdrop, keeping the surrounding area visible during configuration.
- **Loaded-Detector Tick Registry**: Replaced the every-tick scan and copy of every saved detector position with a lifecycle-managed registry of loaded detector block entities. Dummy maintenance now runs once at the end of each server tick instead of once in the block-entity tick and again in the global handler.

## Compatibility

- Updated the mod version to `3.0` and the network protocol to `3.0-detector-admin1` because detector GUI packets now carry block positions, configured names, and delegated access state.
- v3.0 clients and servers must use the same network protocol; older clients are rejected cleanly instead of decoding the changed packet schema.

## Non-Blocking Random Spawn

- **Tick-Sliced Chunk Search**: Random spawn no longer calls synchronous `ServerLevel#getChunk` from login or respawn events. It requests at most two candidate chunks server-wide and polls completed chunks on later ticks, preventing chunk generation waits from blocking the server thread.
- **Bounded Load and Cleanup**: Reduced each search to 24 candidates with a 20-second deadline. Search tickets are released after every candidate and on success, timeout, logout, replacement, or server shutdown; the ticket type also has a defensive automatic expiry.
- **Safe Fallback Loading**: The best distance fallback is reloaded and revalidated asynchronously before teleporting, so fallback behavior cannot reintroduce a synchronous chunk wait.

## TaCZ Gun Disassembly

- **Working 1.21.1 Recipe Injection**: Replaced the obsolete reflective `RecipeMap` lookup with the public `RecipeManager` replacement API, fixing the misleading state where hundreds of recipes were reported as injected although none were registered.
- **Gun-Specific Crushing Inputs**: Generate recipes only for actual TaCZ gun outputs and match the partial `GunId` NBT value, allowing used or customized guns to work without treating every gun, ammunition item, and attachment as the same crushing input.
- **Datapack Reload Support**: Regenerate disassembly recipes after a full datapack reload before recipes are synchronized to clients, while keeping injection idempotent by recipe ID.

## Phantom Rest Protection

- **Immediate Rest Credit**: A successful bed entry resets the player's insomnia timer immediately, without requiring the whole server to skip the night.
- **Protected Target Cleanup**: A phantom that attempts to target a player who is not yet eligible for phantom spawning is discarded without drops or a death animation.

# v2.2

## Delayed Chunk Cache (DCC / Bandwidth Optimization)

- **Authoritative Delayed Chunk Tracking**: Reworked DCC around a `ChunkTrackingView` that is the union of the normal player view and recently departed chunks, following NotEnoughBandwidth's current design. Chunk load/unload decisions, block and light updates, entity tracking, and NeoForge watch events now share the same source of truth.
- **Redundant Resend Suppression**: When a player moves back into a recently departed chunk before cache expiry, DCC recognizes the client-side cached state and skips resending the full `ClientboundLevelChunkWithLightPacket`, drastically reducing bandwidth consumption during back-and-forth movement.
- **3-Dimensional Eviction Policy**: Enforced eviction triggers across **capacity limit** (default: 64 chunks/player, oldest first), **extra distance threshold** (default: View Distance + 2 chunks), and **timeout expiration** (default: 30 seconds, including while stationary).
- **Transport-Layer Independence**: DCC makes server-authoritative chunk delivery decisions without intercepting or rewriting packet transport, allowing packet compression and templating mods to operate at their own layer.
- **Configurable Settings**: Added `DelayedChunkCacheConfig` to customize `sizeLimit`, `extraDistance`, `timeoutSeconds`, and `checkIntervalTicks`.

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
- **JST 18:00 Open Day Cycle Alignment**: Aligned all retention and aggregation windows to JST 18:00 (`(bucket_at - 32400) / 86400`) and enforced per-open-day 10-minute active thresholds (`HAVING SUM(active_seconds) >= 600`) for individual `activeDays` and server-wide `activeUniquePlayers`.
- **Realtime Session & Intrusion Tracking**: Integrated non-blocking `SessionTracker` measuring active vs. AFK duration, combining online player states into realtime queries, alongside `IntrusionTracker` for detector block entry-to-exit intrusion sessions.
- **Offline Player Analytics Commands**: Replaced `EntityArgument.player()` with `GameProfileArgument.gameProfile()` in `/analytics` command to inspect historical activity for offline players and base owners.

## Jobs Compatibility

- **Farmer's Delight 1.3.3 Support**: Added optional Farmer's Delight support to the Farmer job. Mature cabbages, onions, tomatoes, rope-grown tomatoes, and rice panicles now grant Farmer XP.
- **Verified Right-Click Harvest Rewards**: XP is awarded only after the server confirms that the mature crop was successfully harvested and reset to an immature state.

# v2.3

## PvP Dynamic Loadouts, Multi-Map Support & Voting System

- **PvP HUD Upgrade (Hardpoint Zone Control)**: Added visual zone capture states ("RED 占領中", "争奪中", etc.) to the `S2C_PvpHudPacket` and `PvpClientState` rendering.
- **Cinematic Killcam Replay System (`PvpReplayTracker` & `PvpReplayManager`)**: Implemented a true Call of Duty-style death replay engine. Servers record a 60-tick (3-second) circular trajectory ring buffer for all combatants; upon elimination, the victim's POV rewinds to the killer's exact position and perspective, replaying their movement, aim, and final shots with slow-motion impact and rich killer info cards (weapon, distance, HP, streak, HS badge, and `[SPACE]` skip).
- **Multi-Map Management Engine (`PvpMapSavedData` & `PvpMapDefinition`)**: Migrated fixed single-arena coordinates into a data-driven multi-map storage system (`moveearth_pvp_maps.dat`), supporting an arbitrary number of maps with individual RED/BLUE spawns, capture hills, custom descriptions, and UI accent colors.
- **Dynamic Multi-Respawn & Smart Spawn Selector (`PvpSpawnSelector`)**: Added support for optional multiple respawn points per team (`addredspawn` / `addbluespawn`). Implemented a real-time situational scoring engine that evaluates proximity to enemies (spawn-kill prevention penalty), proximity to living allies (reinforcement bonus), distance to the hill, and recent spawn history to dynamically select the safest and most strategic respawn location.
- **Real-Time Map Voting Phase (`PvpMapVoteManager` & `PvpMapVoteScreen`)**: When 2 or more configured maps are available, match initiation enters a 15-second map voting phase with a sleek pop-up GUI, allowing all participants to cast/switch votes with live tally synchronization.
- **Administrative Map Commands (`/pvp admin map ...`)**: Added comprehensive commands for map creation (`create`), coordinate setup (`setredspawn`, `setbluespawn`, `addredspawn`, `addbluespawn`, `clearspawns`, `info`, `sethill1`, `sethill2`), descriptions (`setdesc`), preview teleportation (`tp`), list inspection (`list`), and deletion (`delete`).
- **In-Game Loadout Editor GUI (`/pvp admin loadout`)**: Added an intuitive, full-featured in-game editor screen for administrators (permission level 2+) to create, duplicate, modify, reorder, and delete PvP loadouts dynamically at runtime.
- **One-Click Inventory Gun & Attachment Capture**: Integrated an automatic hotbar analyzer button into the editor. Administrators can configure custom TaCZ weapons and attachments in their inventory and capture them into the loadout definition with a single click without manual ID entry.
- **Dynamic Loadout Storage (`PvpLoadoutSavedData`)**: Migrated fixed preset enums into a World `SavedData` persistence engine (`moveearth_pvp_loadouts.dat`), supporting arbitrary numbers of loadouts beyond the initial 4 templates.
- **Dynamic Scrollable Grid UI (`PvpScreen`)**: Redesigned the player-facing `/pvp` selection screen into a scrollable 2-column card grid, seamlessly supporting 5, 10, or more loadout presets with real-time server synchronization.
- **Administrative Command Enhancements**: Added `/pvp admin loadout`, `/pvp admin loadout editor`, `/pvp admin loadout list`, and `/pvp admin loadout reset` commands.
- **PvP Reward & Weapon Crate Air Bug Fix**:
  - Filtered out internal/dummy IDs (`tacz:dummy` and unrendered gun indices) from `WeaponCrateItem` random rewards to prevent generating invisible/air weapons.
  - Upgraded `PvpPlayerSnapshot` to safely preserve full item Data Components in memory and through `HolderLookup.Provider` serialization, preventing previous inventory corruption/air-loss upon match restoration.
  - Enforced client-side inventory synchronization (`player.inventoryMenu.broadcastChanges()`) across task claiming and crate unboxing.

## PvP Loadouts and Combat Balance

- Replaced the FMIC PvP presets with TaCZ standard modern firearms: SCAR-L, MP5A5, AA12, and the semi-auto-only SKS Tactical, with a P320 sidearm for every role.
- Retuned close-range body-shot damage for full Protection IV iron armor, 20 health, 1.5x headshots, and zero armor penetration. Target TTK is approximately 343-400 ms depending on weapon cadence.
- Kept standard magazine capacities and installed only compatible sights and lasers; AA12 and SKS Tactical use sights without lasers.

## TPA

- Explicitly declared permission level 0 on the player-facing `/tpa`, `/tpaccept`, `/tpdeny`, and `/tpcancel` command roots, matching `/stats` for hybrid server command-permission compatibility. The previous `/tpacancel` spelling remains as an alias, and `/tpa admin` remains restricted to permission level 2.
- Added the collision-resistant `/moveearthtpa` command tree with `request`, `accept`, `deny`, `cancel`, and `status` operations, and changed player-facing target arguments to online player names so they do not depend on privileged entity selectors.

## Compatibility

- Updated the mod version to `2.3` and the network protocol to `2.0-jobs1-hardpoint1` for the expanded Hardpoint HUD packet schema.
- v2.3 clients and servers must use the same network protocol; older clients are rejected cleanly instead of decoding the changed packet schema.

## Deep Underground Oxygen Depletion and Gas Mask Survival System

- Added deep underground dead zone mechanics where oxygen is completely depleted at depths below Y<=0, inflicting percentage-based suffocation damage when unprotected.
- Added `GasMaskItem` and `CarbonFilterItem` with real-time filter degradation, durability consumption, and right-click filter reload functionality.
- Added dynamic filter consumption scaling based on player physical load: sprinting (1.5x), mining (2.0x), and combat (2.5x), plus extreme depth multiplier (2.0x at Y<=-32).
- Added torch extinguishing mechanics in oxygen-depleted zones: placing torches at Y<=0 consumes them immediately with smoke and sound effects.
- Added client HUD overlays: gas mask lens frame, filter fogging effects, real-time filter/oxygen meters, load rate indicators, and low-oxygen red pulse warning.
- Added server configuration (`OxygenConfig`) to customize danger depth thresholds, base filter duration, multiplier rates, and torch extinguishing toggle.

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
