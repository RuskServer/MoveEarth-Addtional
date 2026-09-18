# MoveEarth-Addtional 国家システム コードレビューレポート

- **対象**: `s2/nation`, `s2/territory`, `s2/reinforcement`, `s2/siege` + 関連クライアント / ネットワーク層
- **ブランチ**: `feat/tpu-feasibility-v5e8` (作業ツリー、未コミット分を含む)
- **基点コミット**: `29b753a feat(s2): complete siege and peace systems`
- **レビュー日**: 2026-09-12
- **重点**: パフォーマンス (副次的に正しさ・メモリ)

> **ビルド検証について**: 本環境には JDK 25 のみインストールされており、Gradle 8.8 が
> `Unsupported class file major version 69` で起動できないため、コンパイル・テスト実行による
> 検証は行っていません。以下はすべて静的レビューの結果です。

---

## 目次

1. [総評](#総評)
2. [パフォーマンス](#パフォーマンス-影響が大きい順)
3. [メモリリーク](#メモリリーク)
4. [正しさ・設計](#正しさ設計まわり)
5. [良かった点](#良かった点)
6. [優先度つき修正順](#優先度つき修正順)

---

## 総評

設計そのものは綺麗です。policy クラス (`NationLifecyclePolicy`, `SiegeFallPolicy`,
`TerritoryClosureSearch`) を Minecraft 非依存に切り出してユニットテストしている点、
revision による楽観ロックが全 C2S パケットで一貫している点、SavedData を
用途ごとに分離している点は評価できます。

ただし **パフォーマンス面は、プレイヤー数と補強ブロック数が増えた瞬間に落ちる構造**に
なっています。根本原因はひとつに集約されます。

> `TerritorySavedData` に **チャンク → コアのインデックスが無く**、すべての領土判定が
> 「全コアを stream で舐める」実装になっている。そしてその判定が
> **ブロック単位・tick 単位**で呼ばれている。

ここから芋づる式にすべての重さが派生しています。

---

## パフォーマンス (影響が大きい順)

### 1. 【最重要】補強の毎秒フルスキャン

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/reinforcement/ReinforcementSavedData.java:79-109`

```java
public AdvanceResult advance(ServerLevel level, long gameTime) {
    var iterator = entries.entrySet().iterator();
    while (iterator.hasNext()) {
        if (!level.hasChunkAt(value.getKey())) continue;
        if (level.getBlockState(value.getKey()).isAir()) { ... }
```

`ReinforcementEvents.onServerTick` から **1 秒ごとに、そのワールドの全補強エントリに対して
`getBlockState`** が走ります。`ReinforcementEntry.advance()` は完成済みエントリなら即 `this`
を返すため、コストのほぼ全部が「空気チェック」に費やされます。

拠点が育って 30 万ブロック補強された場合、**毎秒 30 万回の `getBlockState`**。
しかも 20 tick に 1 回まとめて実行されるため、20 tick ごとに 1 回スパイクします。

`entriesByChunk` インデックスを保持しているにもかかわらず `advance` では使われていません。

**提案**

- **建設中エントリのみを別 Set で管理** (`activatesAt > 0` のもの)。`advance` はそれだけを回す。
  完成済みエントリは触らない。
- 空気チェックは polling をやめ、`BlockEvent.BreakEvent` / `ExplosionEvent` 側で削除する
  (既に実装済み)。保険として **チャンク単位のラウンドロビン** (毎 tick 数チャンクずつ) に変更する。
- どうしても全走査が必要なら `entriesByChunk` のキーでループし、未ロードチャンクは
  丸ごとスキップする (現状は entry ごとに `hasChunkAt`)。

---

### 2. ブロック 1 個破壊するたびに最大 8192 件フル再送

**場所**:
- `src/main/java/com/ruskserver/moveearth_addtional/s2/reinforcement/ReinforcementEvents.java:86`
- `src/main/java/com/ruskserver/moveearth_addtional/s2/reinforcement/ReinforcementService.java:115-195`

1 回の `sendScan` で実行される処理:

1. `around()` で 9x9 チャンク分の pos を収集し、**全件に `getBlockState`**、
   さらに `distSqr` で **ソート** (最大 8192 件)
2. 各件に `territories.ownsChunk(...)` = **全コア stream + `TerritoryPreviewArea` の new**
   (8192 x コア数)
3. 各件に `sieges.isReinforcementDisabled(...)` = 全 fallen レコード走査
4. 8192 件の署名ハッシュを計算
5. **そこで初めて**前回署名と比較し「変化なしなら送信しない」

つまり **署名キャッシュは送信しか防いでおらず、計算は毎回フルで走ります**。
しかもこれが「破壊地点周囲のプレイヤー全員」 x 「ブロック破壊ごと」に発生します。
TNT / CBC の爆発 1 発で `syncNearbyManagers` が呼ばれると、範囲内の管理者全員分これが走ります。

加えて:

```java
// ReinforcementService.java:191
if (canManage(player) && player.blockPosition().distSqr(pos) <= SCAN_RADIUS * SCAN_RADIUS)
```

**距離判定が後ろ**です。1 万ブロック離れたプレイヤーに対しても `NationSavedData.get` +
`ownsChunk` (全コア stream) が走ります。`&&` の順序を入れ替えるだけで解消します。

**提案**

- 距離判定を先に持ってくる (1 行修正、即効性あり)。
- **差分同期に変更する**。フルスナップショットではなく「変化した pos のみ」を送る
  `S2C_ReinforcementDeltaPacket` を追加する。ブロック破壊で変化するのは基本 1 〜 数件。
- 同一 tick 内の複数変更をまとめる (tick 末に 1 回だけフラッシュ)。
  現状、爆発の `removeIf` 中は最後に 1 回同期しているが `onBreak` は毎回同期している。

---

### 3. 砲撃 1 発で「全コア stream」が数千回

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/reinforcement/SiegeDamageService.java:160-170`

```java
public static UpkeepPenalty penaltyAt(ServerLevel level, BlockPos pos) {
    if (OfflineDefenseService.settlementProtected(level, pos)) ...   // reservedCores(全stream+sort) + controllingNation(全stream)
    if (sieges.isReinforcementDisabled(...)) ...                     // 全fallen走査
    if (!LongAbsenceService.tierAt(level, pos)...) ...               // controllingNation(全stream) + members() Map.copyOf x2
    return territories.controllingNation(...)                        // 全stream (3回目)
```

`interceptCbcProtectedArea` はこれを **半径内の補強ブロック全件**に対して呼び出し、
さらに `damageReinforcement` -> `OfflineDefenseService.scale` で `reservedCores` +
`controllingNation` + `controllingCore` + `LongAbsenceService.tier`
(`members()` の `Map.copyOf` が再度 2 回) が走ります。

壁 500 ブロックに着弾した場合 = **約 500 x 8 回の全コア stream + 500 x 4 回の members
マップ全コピー + プレイヤーリスト走査**。攻城戦のピーク時に TPS が落ちる構造です。

**提案** (効果の大きい順)

1. **`penaltyAt` / `scale` の結果をチャンク単位でキャッシュ**する。
   これらはすべてチャンク粒度の判定であり、ブロックごとに計算する必要がない。
   1 発の爆発なら `Long2ObjectMap<UpkeepPenalty>` をローカルに 1 個持って使い回すだけで
   500 回 -> 数回になる。
2. `NationSavedData.Nation#members()` / `roles()` / `nations()` の `Map.copyOf` を廃止し、
   `Collections.unmodifiableMap` の使い回しにする (第 6 項)。
3. `TerritorySavedData` にチャンクインデックスを導入する (第 4 項)。

---

### 4. 領土判定がすべて O(コア数) の stream

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/territory/TerritorySavedData.java:217-317`

`controlsChunk` / `controllingNation` / `controllingCore` / `reservedCores` / `ownsChunk`
がすべて同じ形です。

```java
return cores.values().stream()
        .filter(...)
        .map(core -> area(core.pos, core.radius))   // 毎回 new ChunkPos + new TerritoryPreviewArea
        .anyMatch(area -> area.containsChunk(chunk.x, chunk.z));
```

`reservedCores` に至っては `sorted(distSqr)` 付きです。
呼び出し側は前述の通りブロック単位です。

**提案**

- **`Map<ResourceLocation, Long2ObjectMap<List<CoreRecord>>>` のチャンク索引を保持する**。
  `register` / `updateRadius` / `remove` / `removeNation` / `settleFallenCore` でのみ
  再構築する (コア数は少ないので全再構築で十分)。
  これで `controllingNation` が O(1) になり、第 2 項・第 3 項の大半が解消します。
- `area()` の結果を `CoreRecord` に保持するか、レコード単位でキャッシュすれば
  `new` のコストも消えます。

---

### 5. Bastion 判定が毎 tick x 全プレイヤー

**場所**:
- `src/main/java/com/ruskserver/moveearth_addtional/s2/territory/BastionEvents.java:106-111`
- `src/main/java/com/ruskserver/moveearth_addtional/s2/territory/BastionPlayerRecovery.java:17-37`

`PlayerTickEvent.Post` のため **毎 tick 全プレイヤー**で実行されます。
1 回あたり `controllingNation` (全コア stream) + `NationUpkeepService.penalty`
(`System.currentTimeMillis()` 込み) + `nations.can` + SavedData 取得 3 回。

50 人 x 20 tps x コア 200 個 = **毎秒 20 万回のコア判定**。

**提案**

- 「前回と同じチャンクにいて、かつ territory / nation の revision が変化していない」なら
  即リターンするプレイヤー単位キャッシュを導入する。
- そもそも毎 tick 必要な判定ではない。`player.tickCount % 5` などで間引いても体感は変わらない
  (乗り物復帰が 5 tick 遅れても問題ない)。

---

### 6. `Map.copyOf` / `List.copyOf` を getter で実行している

```java
public Map<UUID, Member> members() { return Map.copyOf(members); }   // NationSavedData.java:649
public Map<String, Role> roles()   { return Map.copyOf(roles); }
public Map<UUID, Nation> nations() { return Map.copyOf(nations); }
public List<CoreRecord> cores()    { return List.copyOf(cores.values()); }  // TerritorySavedData.java:105
```

不変性を守る意図は理解できますが、**呼び出しごとに全要素コピー**が発生します。

ホットパスでの使用箇所:

| 場所 | 問題 |
|---|---|
| `OfflineDefenseService.baseDivisor:53-56` | **1 呼び出しで 2 回** (`keySet()` と `values()` で別々に呼ぶ)。しかもブロック単位で実行される |
| `LongAbsenceService.tier:23-26` | 同上 |
| `S2NationViewService.java:52-56` | ロール数 x メンバーマップ全コピーの二重ループ |
| `S2NationViewService.java:86, 108, 152` | siege 1 件ごとに `cores()` を全コピーして線形検索 |

**提案**

- フィールドに `Collections.unmodifiableMap(members)` を 1 個生成して返すだけで済みます。
  `Member` / `Role` は setter が package-private、`Nation` はフィールドが private なので
  外部からの破壊は防げます。
- `S2NationViewService` 側も、`nation.members()` をループ前に 1 回だけローカル変数へ取る /
  `cores()` を `Map<UUID, CoreRecord> byId` で引く形に変更する。

---

### 7. ネームプレート同期が O(N^2)

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/nation/NationNameplateSync.java:35-58`

revision またはオンライン集合が変化すると、**全プレイヤーに全プレイヤー分のエントリ**を
送信します。100 人なら 10,000 エントリ + 10,000 回の文字列連結 (`"[" + tag + "] "`) +
`nationFor` / `relation` 呼び出し。

「誰か 1 人がログイン」するたびにこれが走ります。

**提案**

- prefix は **国ごとに 1 回だけ**計算してキャッシュする (target 依存部分は国のみ)。
- relation も **国ペア単位**でメモ化する (国数は人数よりはるかに少ない)。
- 可能なら差分送信 (変化したプレイヤーのみ) にするのが最良。
- `online` Set の構築が revision 未変化時も実行されているため、`lastRevision` 比較を先に行う。

---

### 8. すべての `onServerTick` が同一 tick に集中

| クラス | ゲート条件 |
|---|---|
| `ReinforcementEvents` | `% 20 == 0` |
| `SiegeService` | `% 20 == 0` |
| `TerritoryCoreHealthService` | `% 20 == 0` |
| `NationUpkeepService` | `% 1200 == 0` |
| `NationNameplateSync` | `% 10 == 0` |

**すべて剰余 0 でオフセットが揃っている**ため、20 tick ごとに全処理のスパイクが 1 tick に
重なります。`% 20 == 3`, `== 7`, `== 11` のように位相をずらすだけでフレームタイムが平坦化します。
**最もコストパフォーマンスの良い修正**です。

---

### 9. 封鎖スキャンが 1 tick に最大 65,000 セル

**場所**:
- `src/main/java/com/ruskserver/moveearth_addtional/s2/territory/TerritoryClosureRecheckManager.java:18` (`MAX_SCANS_PER_TICK = 2`)
- `src/main/java/com/ruskserver/moveearth_addtional/s2/territory/TerritoryClosureScanner.java:10` (`MAX_VISITED = 32768`)

デバウンスキューでイベントパスから切り離した設計は正しいのですが、
**予算の単位が「スキャン数」であって「セル数」ではない**ため、
最悪ケースで 1 tick あたり 65,536 セルになります。

1 セルあたり `Cell` レコード生成 x 6、`HashSet` / `HashMap` 操作、`getBlockState`、
`isReinforcementDisabled` (fallen 全走査) が走るため、数十 ms のスパイクになり得ます。

**提案**

- セル予算制 (`MAX_CELLS_PER_TICK = 4096` 等) に変更し、**スキャンを tick をまたいで
  再開可能**にする (キュー + 途中状態保持)。
- 最低限、`MAX_SCANS_PER_TICK = 1` に下げる。
- `parents` マップが到達セル全部を保持しているが、使用するのは脱出経路のみ。
  親を `Cell` ではなく方向 (byte) で持てばメモリが 1/10 以下になる。

---

### 10. クライアント側

全体としては良く出来ています (パケット受信時にメッシュ化、静的配列の再利用、
フラスタムカリング、GL45 persistent-mapped triple buffering)。以下は改善余地です。

#### a. greedy mesher の boxing

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/reinforcement/ReinforcementGreedyMesher.java:21-62`

`TreeSet<Long>` + `Comparator<Long>` を使用しており、8192 エントリ x 6 面 = 約 5 万個の
`Long` boxing が発生します。さらに `remaining.contains(uv(...))` も毎回 boxing します。
`ReinforcementClientState` の `occupied` (`HashSet<Long>`) も同様に mesher の `Occupancy`
ラムダから約 5 万回参照されます。

**提案**: `(v << 32) | u` の **v-major パック**にすれば long の自然順序が (v, u) 順と
一致するため、`long[]` をソートして保持 + 別途 `LongOpenHashSet` (fastutil は MC 同梱) で
`contains` という構成にできます。これで boxing が完全に消えます。

#### b. GL45 のフェイス別バケット分け

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/client/ReinforcementGl45Renderer.java:88-103`

```java
for (int face = 0; face < FACE_COUNT; face++)
    for (int index = 0; index < count; index++)
        if (faces[index] != face) continue;
```

**毎フレーム O(6 x count)** = 最大 295,000 回のループ。
カウンティングソート (2 パス) にすれば 1/3 になります。
さらに言えば、`ReinforcementOverlayRenderer` が配列を詰める時点で面ごとに分けて書けば
ゼロにできます。

#### c. 毎フレームのバッファラッパ生成

**場所**: `ReinforcementGl45Renderer.java:85-86`

```java
mappedInstances.duplicate().order(...).position(...).slice().order(...).asFloatBuffer()
```

毎フレーム 3 〜 4 個のバッファラッパを生成しています。
初期化時にスロットごとの `FloatBuffer` を 3 本キャッシュすれば済みます。

#### d. `putIfAbsent` の引数先行評価

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/client/ReinforcementClientState.java:47`

```java
styles.putIfAbsent(state, visualStyle(entry));
```

`putIfAbsent` でも引数は先に評価されるため、**毎エントリ `Entry` を 1 個無駄に生成**します。
`computeIfAbsent` に変更してください。

#### e. 静的配列の常時確保 (優先度低)

`INSTANCE_POSITIONS` / `INSTANCE_SCALES` / `INSTANCE_COLORS` / `INSTANCE_FACES` で
常時約 2.2 MB を確保しています。機能を使わないクライアントでも支払うコストなので、
遅延確保にしても良いかもしれません。

---

## メモリリーク

### `SiegeService.RECENT_LOGS` が無制限に増加する

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/siege/SiegeService.java:30, 49-53`

```java
private static final Map<LogKey, Long> RECENT_LOGS = new HashMap<>();
...
LogKey logKey = new LogKey(attacker.getUUID(), level.dimension().location().toString(), target.asLong());
if (shouldLog) RECENT_LOGS.put(logKey, gameTick);
```

キーが **(プレイヤー, ディメンション文字列, ブロック座標)** であり、
**削除処理が `ServerStoppedEvent` にしか存在しません**。
攻城戦で砲撃するたびにブロック単位でエントリが増え続け、
長期稼働サーバーでは数百万エントリまで成長し得ます。

**修正案**

- 毎秒 tick で `gameTick - value > siegeDuplicateLogTicks * 4` を `removeIf` する。
- または `LinkedHashMap` の `removeEldestEntry` で上限を設定する。

### `NationUpkeepService.LAST_NOTIFIED_PENALTY`

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/territory/NationUpkeepService.java:29`

解散した国のエントリが残り続けます。国数が上限なので実害は小さいですが、
`disband` 時に削除するのが綺麗です。

---

## 正しさ・設計まわり

### 1. `NationSettingsScreen` がスナップショットを更新しない

**場所**:
- `src/main/java/com/ruskserver/moveearth_addtional/client/NationSettingsScreen.java:25`
- `src/main/java/com/ruskserver/moveearth_addtional/client/ClientPacketHandler.java:43`

`snapshot` フィールドは非 final にもかかわらず **コンストラクタ以外で代入されていません**。
`ClientPacketHandler` にも `NationSettingsScreen` へスナップショットを流すルートが
ありません (`handleS2HubSnapshot` は `S2HubScreen` のみを対象としている)。

**結果**: この画面を開いている間に誰かが加入 / 脱退 / ロール変更すると revision がズレ、
以降すべての操作が `STALE` で弾かれます。画面上は赤いトーストが出るだけで復帰手段がなく、
**オーナーが設定画面を開きっぱなしにしていると詰みます**。

**修正案**

- `S2C_S2HubSnapshotPacket` を `NationSettingsScreen` にも流し、`snapshot` を差し替える
  (`S2HubScreen.update` と同じ形)。
- または `STALE` を受信したら自動で `C2S_RequestS2HubPacket` を送信して再取得する。

### 2. `disband` が破壊を先に実行し、権威的な変更を後に実行している

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/nation/NationAdministrationService.java:54-77`

処理順序: `validateDisband` -> 補強削除 -> コア削除 (ブロックも `removeBlock`) ->
upkeep / peace / siege / prisoner 削除 -> **最後に** `nations.disband()`。

現状 `nations.disband` が失敗するパスは実質ありません (間に revision を動かす処理がないため)
が、**取り返しのつかない破壊を先に実行する順序**です。将来この間に処理が 1 つ挟まると
「領土だけ消えて国は残る」状態が発生します。

**修正案**: `nations.disband()` を先に実行し、成功を確認してからクリーンアップする順序にする。

### 3. `coveredBy` が補強エントリ数 x コア数

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/s2/nation/NationAdministrationService.java:88-97`

```java
ReinforcementSavedData.get(level).removeWhere(pos -> coveredBy(level, pos, cores, vault));
```

`coveredBy` は **エントリごとに** `new ChunkPos` + `cores.stream()` を生成します。
補強 30 万件 x コア 10 個で 300 万回の stream 生成。
解散操作は稀ですが、その 1 回でサーバーが数秒間フリーズします。

**修正案**: 先に対象チャンクの `LongSet` を構築し、`removeWhere` では `contains` するだけにする。

### 4. `writeUtf(ownerName, 16)`

**場所**: `src/main/java/com/ruskserver/moveearth_addtional/network/S2PacketCodec.java:31`

通常の MC ユーザー名は 16 文字以内なので現状は問題ありませんが、NBT が破損していたり
将来オフラインモード名が入ると **エンコード時に例外 -> パケット送信失敗**になります。
他フィールド (`nationName` は 64) と比べて余裕がないため、32 程度にしておくと安全です。

### 5. 細かい点

| 場所 | 内容 |
|---|---|
| `NationSettingsScreen.java:249` | `candidates()` が `"owner"` をハードコード。`NationSavedData.OWNER_ROLE` を参照すべき。さらに `render` / `mouseClicked` / `mouseScrolled` から毎回呼ばれ、毎フレーム stream + list を生成している。`init()` でキャッシュしたい |
| `NationSettingsScreen.java:221` | `handleResult` が成功 / 失敗にかかわらず `MoveEarthMessage.error` でトーストを出す。成功時は hub に戻るため通常は見えないが、パケット順序次第では成功メッセージが赤く表示される |
| `S2HubScreen.java:170` | オーナー名を `content.x() + 210` の固定位置に描画。ロール名が長いと重なる。`font.width(roleText)` から算出するか右寄せにする |
| `NationSavedData.java:55-68` | `relation()` / `isHostileFrom()` が `NationPair.of` で null を弾いていないため NPE の可能性がある (現状の呼び出し元はすべてガード済みなので実害なし)。`isAllied` と揃えておくと安心 |
| `NationSavedData.java:373-376` | `create()` は revision チェックより先に `ALREADY_MEMBER` を返す。他メソッドはすべて revision が先なので順序を揃えた方が一貫する |

---

## 良かった点

- **policy 層の分離とテスト**: `NationLifecyclePolicy` / `SiegeFallPolicy` /
  `TerritoryClosureSearch` / `TerritoryUpkeepPolicy` を Minecraft 非依存にしてユニットテスト
  している。この規模の MOD でここまで徹底しているのは珍しい。
- **revision による楽観ロック**が全 C2S パケットで一貫している。
- **`TerritoryRecheckQueue` によるデバウンス**: 重いスキャンをイベントパスから外す設計は
  正しい (予算単位だけ修正すれば完璧)。
- **`advanceFallen` のクラッシュ耐性**: finalized を永続化して再送することで 2 つの
  SavedData 更新の間でサーバーが停止しても復旧できる。よく考えられている。
- **`transferGold` のロールバック**: withdraw 成功 -> deposit 失敗時に払い戻す。
- **プロトコルバージョン管理**: `ModMessages` で `s2ui22` -> `s2ui23` をきちんと上げている。

---

## 優先度つき修正順

| 順 | 内容 | 工数 | 効果 |
|---|---|---|---|
| 1 | `onServerTick` の位相をずらす (`% 20 == 0/5/10/15`) | 5 分 | 大 |
| 2 | `syncNearbyManagers` の距離判定を先に | 1 分 | 大 |
| 3 | `TerritorySavedData` にチャンク -> コアインデックス | 半日 | 特大 |
| 4 | `members()` / `roles()` / `nations()` / `cores()` の copyOf 廃止 | 30 分 | 大 |
| 5 | `penaltyAt` / `scale` のチャンク単位キャッシュ | 半日 | 特大 (攻城戦時) |
| 6 | `advance` を建設中エントリのみに絞る | 半日 | 特大 |
| 7 | `RECENT_LOGS` の期限切れ掃除 | 15 分 | メモリリーク解消 |
| 8 | `NationSettingsScreen` のスナップショット更新 | 30 分 | バグ修正 |
| 9 | `sendScan` の差分同期化 | 1 日 | 大 |
| 10 | mesher の boxing 除去 / GL45 カウンティングソート | 半日 | 中 (クライアント) |

**1・2・7 は即座に適用可能**です。
**3 と 5 が本丸**で、ここを修正すると 2・3・5・第 5 項 (Bastion) がまとめて軽くなります。
