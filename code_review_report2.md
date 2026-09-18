# MoveEarth-Addtional 国家システム コードレビューレポート #2

- **対象**: 前回レビュー基点 `29b753a` 以降の全差分 (4 コミット + 作業ツリー、103 ファイル / +7,609 行)
- **ブランチ**: `feat/tpu-feasibility-v5e8`
- **HEAD**: `14ec1ec feat: add solo sieges and territory map overlays`
- **レビュー日**: 2026-09-14
- **前回レポート**: [code_review_report.md](code_review_report.md) (2026-09-12)
- **重点**: 前回指摘の対応確認 + 新規サブシステム (Discord 連携 / Warnautics 統合 / 領土マップ / オンボーディング)

> **ビルド検証について**: 前回同様、本環境には JDK 25 のみがインストールされており
> Gradle 8.8 が `Unsupported class file major version 69` で起動できないため、
> コンパイル・テスト実行による検証は行っていません。以下はすべて静的レビューの結果です。
> JDK 21 を導入すれば `./gradlew test` まで通せます。

---

## 目次

1. [レビュー範囲](#1-レビュー範囲)
2. [前回指摘の対応状況](#2-前回指摘の対応状況)
3. [重大: Discord 送信が server tick を落としうる](#3-重大-discord-送信が-server-tick-を落としうる)
4. [パフォーマンス](#4-パフォーマンス)
5. [正しさ](#5-正しさ)
6. [良かった点](#6-良かった点)
7. [優先度つき修正順](#7-優先度つき修正順)

---

## 1. レビュー範囲

| コミット | 内容 | 規模 |
|---|---|---|
| `8c5649c` | feat(s2): optimize territory systems and nation controls | +2,138 / -186 |
| `73fcd08` | feat(s2): complete reinforcement and Discord integration | +2,764 / -124 |
| `64da305` | feat: add Warnautics siege integration | +1,200 / -7 |
| `14ec1ec` | feat: add solo sieges and territory map overlays | +1,138 / -120 |
| 作業ツリー | オンボーディング / 加入申請システム、維持費ペナルティ拡張 | +456 / -40 |

`8c5649c` は前回レポートの指摘に対応したコミットとして読み、残りを新規コードとしてレビューしました。

---

## 2. 前回指摘の対応状況

**ほぼすべて解消されています。** 対応の質も高く、単なる対症療法ではなく構造から直されています。

### パフォーマンス指摘

| # | 前回指摘 | 状態 | 実装 |
|---|---|---|---|
| 1 | 補強の毎秒フルスキャン | ✅ 解決 | `constructionEntries` Set で建設中のみ走査 + `CLEANUP_BUDGET_PER_SECOND = 512` のチャンク単位ラウンドロビン掃除 |
| 2 | ブロック破壊ごとの 8192 件フル再送 | ✅ 解決 | `S2C_ReinforcementDeltaPacket` 新設 + `flushPendingScans` による tick 末コアレス。距離判定も `canManage` より前に移動済 |
| 3 | 砲撃時の全コア stream 数千回 | ✅ 解決 | インデックス化により候補コアが数件に |
| 4 | 領土判定の O(コア数) | ✅ 解決 | `reservedChunkIndex` / `controlledChunkIndex` / `corePositionIndex` / `vaultChunkIndex` を導入 |
| 5 | Bastion の毎 tick 判定 | △ 部分 | `TerritoryPresenceEvents` は UUID ハッシュで位相分散 + 10 tick 間引き。ただし `BastionPlayerRecovery.tick` は**毎 tick のまま** |
| 6 | getter の `Map.copyOf` | △ 部分 | `NationSavedData` は `Collections.unmodifiableMap` 化 ✅ / `TerritorySavedData.cores()` は **`List.copyOf` のまま** (→ [P4](#p4-territorysaveddatacores-の-listcopyof-がループ内に残存)) |
| 7 | ネームプレート O(N^2) | ✅ 解決 | 国単位キャッシュ化 |
| 8 | tick 位相の重なり | ✅ 解決 | `% 20 == 3` / `== 7` / `== 11`、`% 1200 == 17` に分散 |
| 9 | 封鎖スキャンの 1 tick 65,000 セル | ✅ 解決 | tick をまたぐ再開可能スキャンに変更 |
| 10 | mesher の boxing / GL45 6 パス | ✅ 解決 | `ReinforcementGreedyMesher` を刷新 (テスト付き) |

### メモリリーク指摘

| 指摘 | 状態 | 実装 |
|---|---|---|
| `SiegeService.RECENT_LOGS` 無制限増加 | ✅ 解決 | 毎秒の retention 掃除 + `MAX_RECENT_LOGS = 65_536` の上限クリア |
| `NationUpkeepService.LAST_NOTIFIED_PENALTY` | ✅ 解決 | `NationUpkeepService.removeNation` を追加し `disband` から呼び出し |

### 正しさ指摘

| 指摘 | 状態 | 実装 |
|---|---|---|
| `NationSettingsScreen` の STALE 詰み | ✅ 解決 | `handleResult` が `.stale` を検出したら `returnToHub()` |
| `disband` の破壊順序 | ✅ 解決 | `nations.disband()` を先に実行し、失敗なら即 return |
| `coveredBy` の O(エントリ数 × コア数) | ✅ 解決 | `coveredChunks()` で `Map<ResourceLocation, Set<Long>>` を事前構築 |
| `writeUtf(ownerName, 16)` | — | 未変更 (実害は低い) |

### インデックスと維持費ペナルティの整合性 (確認済み)

新しく入った `NationUpkeepService.effectiveTerritoryRadius` は
`UpkeepPenaltyPolicy.effectiveTerritoryRadius` を通して **radius を縮小する方向にしか働きません**
(`percent` は 0-100 にクランプ)。一方インデックスは**格納 radius** で構築されます。

したがってインデックスは常に実効領域のスーパーセットであり、
`effectivelyContains` による後段フィルタと組み合わせても**取りこぼしは発生しません**。
この設計は正しいです。

---

## 3. 重大: Discord 送信が server tick を落としうる

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/notification/discord/DiscordBotService.java:87-92, 348-376`

```java
public void tick(MinecraftServer minecraftServer) {
    if (minecraftServer != server || !isReady()) return;
    if (++deliveryTicks < DiscordBotConfig.deliveryIntervalTicks()) return;
    deliveryTicks = 0;
    dispatchReady(minecraftServer);   // ← 例外がそのまま tick に抜ける
}
```

`dispatchReady` は `ServerTickEvent.Post` の中で実行されますが、**try/catch がありません**。
そしてここには同期的に例外が飛びうる経路が 2 つあります。

### (a) EMBED_LINKS 権限不足

`channel(link)` のガードは `channel.canTalk()` ですが、これは **VIEW_CHANNEL と MESSAGE_SEND
しか確認しません**。埋め込みメッセージの送信には `MESSAGE_EMBED_LINKS` が別途必要で、
JDA は `sendMessageEmbeds()` の呼び出し時点で `InsufficientPermissionException` を投げます。

「Bot にメッセージ送信権限だけ付与し、埋め込みリンクは付けていない」チャンネルは
現実の Discord サーバーでよくある構成です。

### (b) 埋め込みサイズ超過

`EmbedBuilder.build()` は埋め込み合計 6,000 文字超で `IllegalStateException` を投げます。

- `MoveEarthDiscordEmbeds.audit()`: 最大 10 フィールド × `DiscordText.MAX_LENGTH = 512`
  ≒ 5,400 文字。**余裕がほとんどありません**
- `MoveEarthDiscordEmbeds.notification()`: 説明 + Dimension + Position + 引数 8 件 × 512
  = 4,096 文字超

### 影響

いずれも **1 件の不正な通知でサーバーの tick ループが停止**します。
`replyTest` の `target.sendMessageEmbeds(...)` も `activeServer.execute(...)` の中、
すなわち server thread なので同じ経路です。

### (c) 付随: `inFlight` の永久リーク

```java
if (!inFlight.add(delivery.id())) continue;
...
action.queue(ignored -> finish(activeServer, delivery.id(), true),
             failure -> finish(activeServer, delivery.id(), false));
```

`sendMessageEmbeds` が同期的に投げると `finish()` に到達しないため、
`inFlight` からその ID が**永久に消えません**。該当デリバリは二度と再送されず、
`acknowledge` も `fail` もされないまま outbox に残り続けます。

### 修正案

```java
public void tick(MinecraftServer minecraftServer) {
    if (minecraftServer != server || !isReady()) return;
    if (++deliveryTicks < DiscordBotConfig.deliveryIntervalTicks()) return;
    deliveryTicks = 0;
    try {
        dispatchReady(minecraftServer);
    } catch (RuntimeException exception) {
        Moveearth_addtional.LOGGER.error("[MoveEarth] Discord dispatch failed", exception);
    }
}
```

加えて:

1. `channel()` のガードに EMBED_LINKS を追加する
   ```java
   return channel != null && channel.canTalk()
           && guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)
           ? channel : null;
   ```
2. `DiscordText.MAX_LENGTH` を 512 → 256 程度に下げる、または
   `MoveEarthDiscordEmbeds` 側で合計長を集計して打ち切る
3. デリバリ単位で try/finally を掛け、`inFlight.remove(delivery.id())` を保証する

---

## 4. パフォーマンス

### P1. `rebuildIndexes()` が状態変化のたびに全再構築

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/territory/TerritorySavedData.java:486-508`

`register` / `updateRadius` / `updateState` / `markCoreFallen` / `recoverCore` /
`settleFallenCore` / `remove` / `setVaultChunk` / `removeNation` の**すべて**が
`rebuildIndexes()` を呼びます。内容は「全コア × 最大 9x9 = 81 チャンク × 2 インデックス」の
全消去 + 全再構築です。

問題は **`updateState` が高頻度**であることです。

```
補強ブロック破壊
  -> TerritoryClosureRecheckManager.markPotentialOpening
  -> updateState(ACTIVE -> EXPOSED)
  -> rebuildIndexes()
```

コア 200 個なら 1 回あたり約 32,000 回の Map / List 挿入。
攻城戦で壁を削るたびにこれが走ります。

前回の「全コア stream」を潰した代わりに、**コストが読み込み側から書き込み側へ移動した**形です。
総合的には大幅な改善ですが、ここは詰めきれます。

**修正案** (軽い順):

1. `updateState` / `markCoreFallen` / `recoverCore` は **radius も pos も変化しない**ので、
   `controlledChunkIndex` の該当コアだけを追加 / 削除する差分更新にする
2. `rebuildIndexes()` を遅延化する (dirty フラグを立て、次の参照時に 1 回だけ再構築)。
   同一 tick に複数回状態が変わるケースで効きます

### P2. `effectivelyContains` が候補ごとに SavedData 取得 + `currentTimeMillis()`

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/territory/TerritorySavedData.java:301-306`

```java
private static boolean effectivelyContains(MinecraftServer server, CoreRecord core, ChunkPos chunk) {
    int radius = server == null ? core.radius
            : NationUpkeepService.effectiveTerritoryRadius(server, core.nationId, core.radius);
    return area(core.pos, radius).containsChunk(chunk.x, chunk.z);
}
```

`effectiveTerritoryRadius` の内訳:

- `NationUpkeepSavedData.get(server)` — DataStorage の文字列キー検索
- `.state(nationId)`
- `System.currentTimeMillis()`
- `S2TerritoryConfig.overdueTerritoryRadiusPercent()` — config 読み
- `new ChunkPos` + `new TerritoryPreviewArea`

候補コア数が少ないため前回ほどの深刻さはありませんが、
`controllingNation` は爆発処理でブロック単位に呼ばれます。

**修正案**: 国ごとの `UpkeepPenalty` を 1 tick キャッシュする
(`Map<UUID, UpkeepPenalty>` + `lastTick` を `NationUpkeepService` に持たせる)。

### P3. オンボーディング画面が 2 秒ごとに全国家リストを再送

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/nation/NationOnboardingService.java:244`

```java
if (player.tickCount % 40 == 1) sendOnboarding(player, "", true);
```

`sendOnboarding` は最大 512 国をシリアライズし、各国について
`territories.controlledCoreCount(nation.id())` = **全コア stream** を実行します。

国 50 個 × コア 200 個 = 10,000 回の走査を、**ホールド中プレイヤー 1 人あたり 2 秒ごと**。
しかも内容が変化していなくても毎回フル送信します。

**修正案**:

- `nations.revision()` と `SEARCHING` 状態を前回値と比較し、**変化時のみ送信**する
- 「探索中」の演出はクライアント側の状態として持たせる
- `controlledCoreCount` は 1 パスで全国分を集計してから使う

### P4. `TerritorySavedData.cores()` の `List.copyOf` がループ内に残存

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/S2NationViewService.java:91, 118` ほか

```java
var core = territories.cores().stream()
        .filter(candidate -> candidate.id().equals(siege.coreId()))
        .findFirst().orElse(null);
```

siege 1 件ごとに全コアをコピーして線形検索します。
`individualSieges` (ソロ Siege) の追加により、呼び出し回数はむしろ増えました。

前回レポート #6 の積み残しです。

**修正案**: `TerritorySavedData` に `Optional<CoreRecord> coreById(UUID)` を生やす
(`rebuildIndexes` のタイミングで `Map<UUID, CoreKey>` も作る)。
あるいは呼び出し側で `Map<UUID, CoreRecord>` を 1 回だけ作って共有する。

### P5. 領土マップパケットが最大約 230KB

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/network/C2S_RequestTerritoryMapPacket.java:20`
(`MAX_CORES = 4096`)

1 エントリあたり約 56 バイト (UUID 16 + ResourceLocation + int x2 + byte x2 + boolean)。
4096 件で **約 230KB**。

レート制限はクライアント側 `REFRESH_MILLIS = 5000` + サーバー側 40 tick なので、
50 人が地図を持ち歩くと約 2.3 MB/s になります。

**修正案**:

- リクエストに次元を載せ、**viewer のいる次元のコアだけ**返す
- 国家 ID を UUID (16B) ではなく**パケット内インデックス (varint)** にする
  (`nations` リストの添字を参照する)。1 エントリあたり約 15B 削減
- サーバー側でも署名比較し、未変更なら送らない

### P6. `EntityJoinLevelEvent` を全エンティティで受けている

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/compat/warnautics/WarnauticsWeaponEvents.java:105-124`

```java
static String entityPath(Entity entity) {
    var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    return id != null && MOD_ID.equals(id.getNamespace()) ? id.getPath() : "";
}
```

namespace を先に判定しているのは良い設計ですが、
`BuiltInRegistries.ENTITY_TYPE.getKey()` 自体は**すべてのエンティティ生成**で実行されます。
mob spawn / item drop / 投射物で毎秒数千回走る経路です。

**修正案**: `ServerStartedEvent` で `Set<EntityType<?>>` を 1 回解決し、
`AERIAL_BOMB_TYPES.contains(entity.getType())` の参照 1 回にする。

### P7. `advance()` の `List.copyOf(constructionEntries)`

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/reinforcement/ReinforcementSavedData.java:93`

毎秒フルコピーしています。大量一括補強の直後は建設中エントリが数万件になり得ます。

**修正案**: `Iterator` + 削除予定リスト、または処理待ちを `ArrayDeque` で持つ。

---

## 5. 正しさ

### C1. `persistedData()` の型チェック漏れ

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/nation/NationOnboardingService.java:296-302`

```java
CompoundTag root = player.getPersistentData();
if (!root.contains(Player.PERSISTED_NBT_TAG)) {
    root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
}
return root.getCompound(Player.PERSISTED_NBT_TAG);
```

`contains(String)` は型を確認しません。他 MOD が同じキーに非 Compound を格納していた場合、
`getCompound` は**挿入されない使い捨ての空 CompoundTag** を返すため、
`NBT_PENDING` が静かに永続化されなくなります。

結果として「オンボーディングが毎回やり直しになる」または
「ホールドから抜けられない」状態が発生し得ます。

**修正案**: `contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)` にする。
`RandomSpawnHandler.persistedData` も同じ形なら同様に修正。

### C2. `ownsChunk` だけ維持費ペナルティ radius が効かない

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/territory/TerritorySavedData.java:357-365`

`controllingNation` / `controllingCore` / `controlsChunk` は `server` を渡すオーバーロードで
**縮小 radius** を適用しますが、`ownsChunk` (呼び出し元は
`ReinforcementService.java:135, 199, 278` の 3 箇所) には適用されません。

結果、維持費滞納 (DISABLED) の国では次のようなチャンク帯ができます。

> 補強は張れる (`ownsChunk` = true) が、その領域は誰の支配下でもない
> (`controllingNation` = empty) ため、Bastion 保護も Siege 判定も効かない

**意図した仕様である可能性が高い** (「予約地には建てられるが保護されない」) ので、
その場合は `ownsChunk` にその旨のコメントを入れてください。
意図していない場合は `ownsChunk` にも server オーバーロードが必要です。

### C3. マップ要求のレート制限を `getPersistentData()` に書いている

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/network/C2S_RequestTerritoryMapPacket.java:38-41`

```java
long previous = player.getPersistentData().getLong(LAST_REQUEST_TAG);
if (previous > 0L && now >= previous && now - previous < 40L) return;
player.getPersistentData().putLong(LAST_REQUEST_TAG, now);
```

2 点あります。

1. `getPersistentData()` はプレイヤー NBT に**永続保存**されます。
   一時的なレート制限値をセーブデータへ書くのは避けたいところで、
   `Map<UUID, Long>` を `PlayerLoggedOutEvent` で掃除する形が適切です
2. `now >= previous` の条件により、gameTime が巻き戻る状況
   (バックアップからのロールバック等) でレート制限が無効化されます

### C4. `DiscordText.safe()` の境界処理

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/notification/discord/DiscordText.java:11-19`

```java
if ("\\*_~`[]()>@".indexOf(character) >= 0 && result.length() + 1 < MAX_LENGTH) {
    result.append('\\');
}
result.append(character);
```

512 文字の境界付近で第 2 条件が偽になると、**エスケープなしで特殊文字がそのまま追加**されます。
`setAllowedMentions(none)` があるため実害は装飾崩れ程度ですが、
エスケープできない場合は `continue` で打ち切る方が一貫します。

また `@` を `\@` にしても Discord では**バックスラッシュがそのまま表示**されます
(`@` は Markdown のエスケープ対象外)。`@` はゼロ幅スペース挿入か削除が一般的です。

### C5. 細かい点

| 場所 | 内容 |
|---|---|
| `DiscordLinkCodeRegistry.java` | 32 文字 x 8 桁 = 40 bit、`SecureRandom`、1 guild 1 コード、`MAX_PENDING = 1024` で総当たりは非現実的。設計は妥当。ただし `consume` 側に**試行回数制限がありません**。in-game 側パケットにレート制限があるか確認しておくと安全 |
| `WarnauticsBombSavedData.claimNearest:48-52` | `min(Comparator.comparingDouble(...))` で `distanceSquared` を 2 回計算 (filter でも計算済)。件数が少ないため実害は小さいが 1 パスで済む |
| `NationOnboardingService.onPlayerTick` | ホールド中は毎 tick `teleportTo` を呼ぶ。オンボーディング中のみなので許容範囲だが、`distanceToSqr > 0.01` の閾値はサーバー側の丸め誤差で常時真になり得る |
| `S2HubScreen.java:170` | 前回指摘のオーナー名 `content.x() + 210` 固定位置は未修正。ロール名が長いと重なる |

---

## 6. 良かった点

### 前回指摘への対応品質

対症療法ではなく構造から直されています。特に評価できるのは:

- **補強システム**: 「毎秒全走査」を `constructionEntries` + チャンク単位ラウンドロビンに分解し、
  さらに差分パケット (`S2C_ReinforcementDeltaPacket`) と tick 末コアレスまで入れた
- **領土インデックス**: 4 種類のインデックスを用途別に分け、
  維持費ペナルティによる radius 縮小と「インデックスはスーパーセット」という
  正しい不変条件で組み合わせた
- **tick 位相分散**: `% 20 == 3 / 7 / 11` という単純だが効果的な対応
- **`TerritoryPresenceEvents`**: UUID ハッシュによるプレイヤー単位の位相分散は
  こちらが提案した以上に洗練されている

### Discord 連携の設計

セキュリティ面が丁寧です。

- guild-only ガード (`isFromGuild` + `getGuild() != null` + `getMember() != null`)
- `Permission.MANAGE_SERVER` によるコマンドゲート、mention 変更は追加で `MANAGE_ROLES`
- `setAllowedMentions(EnumSet.noneOf(...))` による **@everyone 注入対策**
- slash コマンドのみで `MESSAGE_CONTENT` intent を要求しない (`JDABuilder.createLight`)
- `server.execute()` による**正しいスレッドホップ** (SavedData は常に server thread)
- 例外メッセージごとログに出さない配慮 (`exception.getClass().getSimpleName()`)
- `build.gradle` で **JDA の推移依存をリロケート**し、埋め込みスモークテストまで実施
- config に `Never commit this file.` コメント + `enabled` デフォルト false
- `DiscordText` による JDA 非依存のエスケープ境界

### アウトボックスパターン

`NationNotificationSavedData` の outbox + `acknowledge` / `fail` / retry / dedup / 監査ログは、
Minecraft MOD としては過剰なくらい真面目な実装です。
`NotificationDeliveryPolicy` を純粋関数に切り出してテストしている点も一貫しています。

### その他

- `TerritoryMapRenderer` の `PROJECTION_CACHE` (LRU 1024、キーに `version` を含む) は
  無効化が正しく効く設計
- `NationApplicationPolicy` / `SiegeAttackerPolicy` / `TerritoryMapProjection` /
  `TerritoryTransitionTracker` を純粋クラスに分離してテストを付けた点は
  プロジェクトの一貫した方針が守られている
- `WarnauticsWeaponEvents.blockPath` / `entityPath` が **namespace を先に判定**している

---

## 7. 優先度つき修正順

| 順 | 内容 | 工数 | 影響 |
|---|---|---|---|
| 1 | `dispatchReady` / `replyTest` を try/catch、`inFlight` を finally で解放 | 15 分 | **サーバー停止回避** |
| 2 | `channel()` に EMBED_LINKS チェック、埋め込み合計長クランプ | 30 分 | **サーバー停止回避** |
| 3 | `persistedData` を `contains(key, TAG_COMPOUND)` に | 5 分 | オンボーディング破綻回避 |
| 4 | `updateState` 系の `rebuildIndexes()` を差分更新 or 遅延化 | 半日 | 大 (攻城戦時) |
| 5 | `sendOnboarding` を revision 変化時のみ送信 | 1 時間 | 中 |
| 6 | `effectiveTerritoryRadius` の penalty を tick キャッシュ | 1 時間 | 中 |
| 7 | `coreById` 索引で `cores()` のコピーを排除 | 30 分 | 中 |
| 8 | `ownsChunk` の penalty 適用方針を決定・明文化 | — | 仕様確認 |
| 9 | マップパケットの次元フィルタ + 国家インデックス化 | 半日 | 中 (帯域) |
| 10 | `EntityJoinLevelEvent` を `EntityType` 集合判定に | 30 分 | 中 |

**1 〜 3 は即座に適用可能**です。
特に **1 と 2 は Discord 連携を本番サーバーで有効化する前に必須**です
(`DiscordBotConfig.enabled` のデフォルトが false なので、現時点では未発火のはずです)。
