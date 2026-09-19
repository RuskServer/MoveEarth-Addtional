# MoveEarth 地方別資源システム実装計画

更新日: 2026-09-17
対象: Minecraft 1.21.1 / NeoForge 21.1.238 / MoveEarth-Addtional
連携先: Create Ore Excavation (modid `createoreexcavation`, 1.21.1-1.6.8, MIT)
関連: [terrain_generation_plan.md](terrain_generation_plan.md)（自作地形生成。成立すれば本計画のL1を置き換える）

## 1. 目的と境界

土地ごとに採れる資源へ特色を付け、資源の偏在を国家間の交易・外交・戦争の動機にする。
Create Ore ExcavationのOre Vein（以下COE鉱脈）、バニラおよびCreateの通常鉱石生成、
Create: Diesel Generators（以下C:DG）の石油埋蔵量の3系統を対象とする。

- 大陸を自動検出し、面積に応じて1〜6の地方へ分割する。
- 戦略資源は地方排他とし、その地方でしか採れない。
- 基礎資源は全地方へ最低限配置し、序盤の詰みを防ぐ。量の多寡で地方色を出す。
- 対象ディメンションはオーバーワールドのみとする。ネザーとエンドは従来どおり全域一様とする。
- 新規ワールドを前提とする。既存ワールドへの遡及適用は範囲外とする。
- 資源の消費側（レシピ改変、技術ツリーによる解禁）は本計画の範囲外とする。

**全体を貫く設計原則として、特定のワールド生成MODや特定の鉱石追加MODに固有の固定テーブルを一切持たない。**
判定に必要な事実は全て実行時にレジストリ・生成器・規約タグから導出する。MOD構成が変わっても、
worldgenを差し替えても、鉱石MODを足しても、本システムのコードとデータは書き換えない。

## 2. 確定した前提

| 項目 | 決定 | 影響 |
|---|---|---|
| 地形生成 | 自作タイル地形（[terrain_generation_plan.md](terrain_generation_plan.md)、実装済み） | タイルが大陸IDと地方IDを事前計算済みで持つ。L1を走査で作る必要がない |
| 適用先 | 新規ワールドから | 通常鉱石生成へも制限なく介入できる |
| 特色の強度 | 完全排他を主軸。基礎鉱石のみ全地方にベースラインを確保 | 資源を3ティアに分ける |

### 2.1 自作地形の扱い

Terralithは使わない。地形は`terrain_generation_plan.md`の自作タイルへ移行済みで、本番タイルが稼働している。
これは本計画にとって単なる前提の変更ではなく、**L1という層がほぼ丸ごと不要になる**ことを意味する。

タイルは1セル8ブロックの格子で次を持つ。生成器を走査して求める必要があったものが、既にファイルにある。

| レイヤー | 型 | 内容 |
|---|---|---|
| `continent` | u1 | 大陸ID。0は海 |
| `region` | u2 | 地方ID。0は地方なし |

面積規則も既に実装済みである。`region_unit_area_blocks`（既定4,000,000）、`max_regions_per_continent`（6）、
`min_region_area_blocks`（900,000）、`islet_threshold_blocks`（250,000）は`tools/terrain/terraingen/config.py`にあり、
5.2の分類表と同じ値で動いている。本番タイルの実績は大陸4・地方7である。

**地方の切り方はk-meansではなく流域である。** `merge_to_target`が、各セルが最終的に注ぐ海岸の流出点で
まとめた流域（basin）を、面積目標に達するまで「最小の流域を、最も長く接している隣へ吸収させる」形でマージする。
結果として地方境界は**分水嶺に沿う**。これは当初k-meansで受け入れるつもりだった
「Voronoi境界が直線的で地形と無関係に資源が切り替わる」という欠点が、そもそも発生しないことを意味する。

## 3. Create Ore Excavationの実機構

ソース（`main`ブランチ）を読んで確認した事実である。設計はこの制約の上に組む。

### 3.1 鉱脈定義

鉱脈は`RecipeType`として定義される。`data/<ns>/recipe/ore_vein_type/<name>.json`が鉱脈そのもの、
`data/<ns>/recipe/drilling/<name>.json`が`veinId`でそれを参照し、産出物・応力・所要tickを決める。

```json
{
  "type": "createoreexcavation:vein",
  "amountMultiplierMin": 10.0,
  "amountMultiplierMax": 30.0,
  "biomeWhitelist": "minecraft:is_overworld",
  "finite": "default",
  "icon": { "id": "minecraft:raw_iron", "count": 1 },
  "name": "{\"translate\":\"item.minecraft.raw_iron\"}",
  "placement": { "salt": 1544847576, "separation": 8, "spacing": 128 },
  "priority": 0
}
```

### 3.2 チャンクへの割り当て

`RandomSpreadGenerator#pick(LevelChunk)`が次の手順で決める。

1. 全鉱脈レシピを`priority`降順（同値はレシピID順）で走査する。
2. `RandomSpreadStructurePlacement#getPotentialStructureChunk(seed, x, z)`が当該チャンクを指すかを見る。
3. 当たった鉱脈について、シード固定RNGで1点だけノイズバイオームを取り、`canGenerate`を評価する。
4. 最初に通った鉱脈を返す。どれも通らなければ鉱脈なしとする。

結果は`OreDataAttachment`（チャンクのデータアタッチメント）へ遅延保存される。
`OreData#populate`が呼ばれるのは、サンプルドリル・抽出機・Vein Finderなどが実際にそのチャンクへ触れた時である。

### 3.3 本計画にとって決定的な制約

- **フィルタはバイオームタグのみである。** `biomeWhitelist` / `biomeBlacklist`しか持たず、座標・ディメンション・構造物によるフィルタは存在しない。したがってデータパックだけでは地方別化は原理的に不可能で、コード介入が必須である。
- `RandomSpreadGenerator`には`pick`が2つある。公開の`pick(LevelChunk)`が実際の確定に、privateの`pick(ServerLevel, ChunkPos, RecipeHolder)`がVein Finder・Vein Atlas・JourneyMapオーバーレイの予測に使われる。**両方へ同じ判定を通さないと、探知結果と実際の鉱脈が食い違う。**
- `getNearestGenerated`はprivate `pick`の戻り値を元レシピと参照同一性で比較する。鉱脈を別種へ「差し替える」実装はこの探索を壊すため採用しない。
- 鉱脈が拒否された場合、COEは走査を続けるだけである。そのチャンクに他の鉱脈のグリッドが当たっていなければ鉱脈なしになる。排他化は必然的に鉱脈密度を下げるため、データ側で補う必要がある（7.3）。

## 4. 全体像

4層に分ける。下位層は上位層を知らない。

| 層 | 役割 | 成果物 |
|---|---|---|
| L1 地域マップ | ワールドを大陸と地方へ分割する | タイルの`continent`/`region`レイヤー（生成済み） |
| L2 資源プロファイル | 地方に「何が採れるか」を割り当てる | データパック定義＋割り当て結果（SavedData） |
| L3 生成介入 | COE鉱脈・通常鉱石生成・C:DG石油へ地方判定を効かせる | Mixin 2つ＋PlacementModifier 1つ |
| L4 可視化 | プレイヤーが地方と資源を知れるようにする | 境界通知・地図オーバーレイ・Hubタブ |

## 5. L1 地域マップ

### 5.0 L1はタイルから読むだけになった

当初この層は、worldgenが何であれ動くよう「生成器を走査し、陸海マスクを作り、連結成分で大陸を求め、
k-meansで地方へ分ける」という手順を定義していた。自作地形の採用でその全てが不要になる。
タイルが同じものを事前計算済みで持っているからである。

| 当初の手順 | 現在 |
|---|---|
| `getBaseHeight`を32ブロック間隔で全域走査 | 不要。タイルの`height`が既に格子上にある |
| モルフォロジー補正で川・湖を陸へ倒す | 不要。`continents`が陸マスクから直接ラベリング済み |
| 連結成分ラベリングで大陸ID | `continent`レイヤー |
| 島嶼の併合 | `islet_threshold_blocks`で生成時に実施済み |
| k-meansで地方分割 | `region`レイヤー。流域マージによる分割（2.1） |
| 小地方の併合 | `min_region_area_blocks`で生成時に実施済み |

**`RegionSource`インターフェースは設けない。** 当初は「自作地形が成立したら実装を1つ足して差し替える」ための
抽象として置く予定だったが、差し替える相手が消えた以上、実装が1つしかない抽象になる。
地形を再び入れ替えることになったらその時に導入すればよく、影響は`RegionResolver`1クラスに留まる。

### 5.1 実行時の判定

`TerrainTileStore`が既にサーバー起動前（`ServerAboutToStartEvent`）にタイルを読み込んでいる。
地域マップはそこへ相乗りする。**初期化順序の問題は発生しない。**
地形の密度関数がタイルを必要とする時点より前に読み終わっていることが、実機で確認済みだからである。

```
regionAt(x, z):
  tile = TerrainTileStore.active().tileAt(x, z)
  if tile == null: return 0            // タイル外は外洋
  return tile.sample("region", x, z)   // 最近傍。境界を補間してはならない
```

大陸IDも同様に`continent`レイヤーから読む。**この2つのレイヤーだけは双一次補間してはならない。**
IDは数値の大小に意味がない離散ラベルであり、地方3と地方7の間を補間すると地方5になる。
既存の`TerrainTile#sample`は双一次補間なので、ID用に最近傍で読む経路を足す。

海上と海底は`region`が0になる。沿岸の資源をどう扱うかは2つの選択肢がある。

| 案 | 内容 | 判定 |
|---|---|---|
| A | 0のまま「地方なし」とし、倍率は既定値を使う | 海底に資源地方の特色が出ない。海底採掘が地方制を回避する抜け道になる |
| B | 最寄りの陸セルの地方を引き継ぐ | **採用**。沿岸と本土で資源が一致し、抜け道も塞がる |

Bの実装は、タイル生成時に`region`レイヤーを海側へ拡張しておく（最近傍陸セルのIDで埋める）のが安い。
実行時に探索するより、生成時に1回解く方が確実である。これはタイル生成側の変更になるため、
Phase 1で`tools/terrain/terraingen/regions.py`へ加える。

### 5.2 地方数と面積分類

タイル生成側で既に実装済みである。`merge_to_target`が大陸ごとに
`target = clamp(round(area / region_unit_area_blocks), 1, max_regions_per_continent)` を求め、
流域をその数までマージする。

| 分類 | 陸地面積の目安 | 地方数 | 扱い |
|---|---|---|---|
| 島嶼 | 25万未満 | — | 大陸として数えない（`islet_threshold_blocks`） |
| 小大陸 | 25万〜225万 | 1 | 単一プロファイル |
| 中規模大陸 | 225万〜900万 | 2 | 流域境界で分かれる |
| 大型大陸 | 900万〜 | 3〜6 | 地方ごとに異なるプロファイル |

本計画側でこれらの値を再実装しない。タイルが何地方を持っているかは`region`レイヤーを数えれば分かる。
地方数を変えたければタイルを再生成する。**ワールド生成後に地方の切り方は変えられない。**

### 5.3 決定性

タイルは固定アセットである。同一シードで同じ結果が出るという以上に、**同じファイルを配れば同じ世界になる**。
地域マップの再現性はタイルの同一性そのものであり、別途保証する仕組みを持たない。

### 5.4 永続化と運用

地域マップそのものはタイルであり、SavedDataへ保存しない。保存するのは**プロファイル割り当てだけ**である。

- `RegionMapSavedData`へ「地方ID→プロファイルID」の対応と、割り当て時のタイル識別子を保存する。
- 起動時にタイルの地方IDが保存済みの対応と食い違う場合（タイルを差し替えた場合）は警告を出し、
  **自動で再割り当てしない**。既に確定した鉱脈・採掘済みチャンクと矛盾するためである。
- 運用コマンドを設ける。いずれもOP限定とする。

| コマンド | 機能 |
|---|---|
| `/moveearth region info [<x> <z>]` | 現在地または指定座標の大陸ID・地方ID・プロファイル・採れる資源一覧 |
| `/moveearth region list` | 全大陸と地方の一覧、面積、プロファイル、セントロイド座標 |
| `/moveearth region export` | 地域マップをPNGへ書き出す。タイル生成側の`regions.png`と一致するはずで、食い違えば読み取りの不具合 |
| `/moveearth region reassign <regionId> <profileId>` | 地方のプロファイルを手動で差し替える |
| `/moveearth region reassign-all --confirm` | プロファイル割り当てをやり直す。鉱脈確定済みチャンクとの矛盾を警告した上で実行する |
| `/moveearth region probe` | 鉱石feature・COE鉱脈・C:DG石油・規約タグの解決結果をダンプする診断コマンド |
| `/moveearth region materials` | 6.4の素材解決結果と、解決に失敗した対象の一覧 |
| `/moveearth region features <biome>` | そのバイオームでゲートを挿入したfeatureと素通ししたfeatureの一覧 |

自動生成の結果が運営の意図と合わないことは必ず起きるため、手動上書き経路は最初から用意する。

## 6. L2 資源プロファイル

### 6.1 ティア設計

| ティア | 対象 | 配置方針 |
|---|---|---|
| 基礎 | 石炭、鉄、銅 | 全地方に配置する。地方ごとの密度倍率は0.4〜2.5で変動させる |
| 準基礎 | レッドストーン、ラピスラズリ、亜鉛 | 全地方に微量（専門地方の15〜25%）配置し、専門地方で通常量とする |
| 戦略 | 金、ダイヤモンド、エメラルド | 完全排他とする。専門地方以外では通常鉱石もCOE鉱脈も一切生成しない |

準基礎ティアを設ける理由は次のとおりである。判断はユーザーに委ねるが、既定では準基礎を推奨する。

- 亜鉛を完全排他にすると真鍮が作れず、Createの中盤以降が地方単位で丸ごと死ぬ。国家に属さない個人が詰む。
- レッドストーンとラピスを完全排他にすると、エンチャント台とCreateの制御系が同様に死ぬ。
- 一方で金・ダイヤ・エメラルドは、無くても生存と基本的な工業化が成立する。ここを排他にすれば交易と戦争の動機として機能し、詰みは生まない。

ネザー資源（ネザライト、ネザークォーツ、グロウストーン）はオーバーワールドの地方制の対象外とする。
ネザーを地方制にすると、オーバーワールドの地方とネザー座標の対応を定義する必要が生じ、複雑さに見合わない。

### 6.2 プロファイル定義

データパックで定義する。`data/moveearth_addtional/region_profile/<id>.json`。

```json
{
  "display": { "ja": "金鉱地帯", "en": "Gold Belt" },
  "weight": 10,
  "materials": {
    "gold": 1.0,
    "iron": 0.8,
    "coal": 1.2,
    "redstone": 0.2
  }
}
```

キーは鉱石名でもfeature IDでも鉱脈レシピIDでもなく、**素材名**である。COE鉱脈と通常鉱石はどちらも
6.4の手順で素材名へ解決されるため、プロファイルは両系統を1つの表で制御する。
キーに現れない素材は倍率0、すなわち非生成である。値は密度倍率で、COE鉱脈側は「出現を許可するか」と
`amountMultiplier`への乗数、通常鉱石側は生成試行の通過率として使う。

この形にすると、Createや他MODの鉱石を後から足してもプロファイル定義もコードも書き換える必要がない。
新しい素材は既定倍率（`defaultMaterialMultiplier`、既定1.0）で全地方に出るため、
運営が意図的に`materials`へ書くまでは現状維持となり、MOD追加で世界が壊れることがない。

### 6.3 割り当てと詰み防止制約

地方へのプロファイル割り当ては、ワールドシードから決定論的に行う。ただし純粋なランダムは使わない。

1. **全プロファイル出現の保証**: 戦略ティアのプロファイルを先にラウンドロビンで各地方へ配る。地方数がプロファイル数を下回る場合は、1地方に複数プロファイルを持たせる。世界に1つも存在しない戦略資源が出る状態は、無条件で生成失敗として扱い再割り当てする。
2. **大陸内の分散**: 同一大陸内で同じ戦略プロファイルが重複しないことを優先する。重複が避けられない場合のみ許可する。
3. **初期スポーン地点の配慮**: ワールドスポーンを含む地方には、戦略ティアのうち最も希少なプロファイルを割り当てない。序盤に偶然強い資源を独占する開幕差を抑える。
4. **結果の永続化**: 割り当て結果は`RegionMapSavedData`へ保存し、以後変わらない。プロファイル定義のリロードで倍率は変えられるが、どの地方が何かは変えない。

### 6.4 資源識別の一般化

「この鉱石／この鉱脈は何の素材か」を、固定テーブルを持たずに実行時へ解決する。
MOD横断の規約タグ（`c:`名前空間）を唯一の語彙として使う。

**通常鉱石の場合**

1. `ConfiguredFeature`の`FeatureConfiguration`が`OreConfiguration`かを見る。バニラの`minecraft:ore`と`minecraft:scattered_ore`はどちらもこれを使い、Createを含む多くのMODの鉱石もこれに従う。
2. `OreConfiguration#targetStates`から生成先ブロックを取る。
3. そのブロックが属する`c:ores/<material>`タグを引き、`<material>`を素材名とする。

**COE鉱脈の場合**

1. 鉱脈レシピIDを`veinId`で参照している`drilling`・`excavating`レシピを引く。
2. それらの出力アイテムを取り、`c:raw_materials/<m>`・`c:gems/<m>`・`c:ingots/<m>`・`c:dusts/<m>`の順にタグを引いて`<m>`を素材名とする。
3. 1つの鉱脈が複数素材を産出する場合、全素材が許可されている地方でのみ出現を許可する。

**流体資源の場合**

1. その系統が産出する流体を取る（C:DGの石油なら`createdieselgenerators:crude_oil`）。
2. `c:<material>`流体タグを引き、`<material>`を素材名とする。C:DGは`c:crude_oil`を自前で出荷しているため、
   石油は素材名`crude_oil`として解決される。
3. 鉱石・鉱脈と同じ表で扱う。プロファイルに`"crude_oil": 1.0`と書けば、それが石油の地方倍率になる。

流体を1本足すだけで済むのは、6.4が素材名という単一の語彙に寄せてあるからである。
C:DG専用のテーブルもプロファイル形式の分岐も生じない。

**解決できなかった場合は常に許可する。** 規約タグを付けていないMODの鉱石を、こちらの都合で生成停止させない。
分類不能だった対象は起動時にWARNでログへ列挙し、運営が気付けるようにする。必要ならconfigで素材名を手動指定できる
上書きマップを用意するが、既定は空とする。

解決結果は起動時とデータパックリロード時に一度だけ構築し、以後は不変マップとして参照する。
`/moveearth region materials`で解決結果の全体を一覧でき、「何が何として扱われているか」を運営が確認できるようにする。

## 7. L3-a COE鉱脈への適用

### 7.1 介入点の選定

| 案 | 内容 | 判定 |
|---|---|---|
| A | `RandomSpreadGenerator`の2つの`pick`内で`VeinRecipe#canGenerate`呼び出しをラップし、地方ゲートをANDする | **採用**。優先度ループ・グリッド配置・バイオーム判定といったCOEの既存挙動を完全に温存でき、予測経路と確定経路が自動的に一致する |
| B | `OreData#populate`を差し替える | 却下。Vein Finderが使う`RandomSpreadGenerator#locate`を通らないため、探知結果と実際が食い違う |
| C | 選ばれた鉱脈を地方許可鉱脈へ差し替える | 却下。密度は保たれるが、`getNearestGenerated`の参照同一性比較を壊しVein Finderの探索が機能しなくなる |

### 7.2 Mixin仕様

```java
@Pseudo
@Mixin(targets = "com.tom.createores.util.RandomSpreadGenerator", remap = false)
```

既存の`WarnauticsDropBombAssemblyMixin`と同じく`@Pseudo` + `targets`指定とし、COE未導入時もMOD全体が正常に起動するようにする。

- `pick(LevelChunk)`と`pick(ServerLevel, ChunkPos, RecipeHolder)`の両方で、`VeinRecipe#canGenerate`の呼び出しを
  MixinExtrasの`@WrapOperation`でラップする。チャンク座標は`@Local`で捕捉する。
- ラップ後の判定は `original.call(...) && RegionVeinGate.allows(level, recipeId, chunkX, chunkZ)` とする。
- `RegionVeinGate`は地域マップ未構築時・COE側の想定外変更検出時に無条件`true`を返し、素のCOE挙動へ落ちる。
- `moveearth_addtional.mixins.json`の`injectors.defaultRequire`は現在1である。COEは任意依存なので、この1つだけ
  `@Inject`側で`require = 0`相当の扱いにするか、mixin configを分離してrequireを緩める。Phase 0で方式を確定する。

`amountMultiplier`への地方倍率の適用は`OreData#populate`後の`setRandomMul`経由でも可能だが、Phase 2では扱わない。
まず「出るか出ないか」を正しくし、量の調整はPhase 5のバランス調整へ回す。

**自前で追加する鉱脈定義には`biomeWhitelist`も`biomeBlacklist`も置かない。** 3.2のとおりCOEは
ランダムなY座標で1点だけバイオームを標本化するため、地上のチャンクでも洞窟バイオームが引かれうる。
バイオームを増やすworldgen MODが`minecraft:is_overworld`へ自分のバイオームを入れているかは保証できず、
ここに依存すると特定MOD構成でのみ鉱脈が激減する事故が起きる。地方ゲートはX/Zのみで判定するため、
バイオームフィルタを外せば鉱脈配置は完全にX/Zの決定論になり、どのworldgenでも同一に振る舞う。
既存のCOE標準鉱脈（`biomeWhitelist`を持つ）をそのまま使うか、自前定義へ置き換えるかはPhase 2で判断する。

### 7.3 鉱脈密度の穴とその対処

排他化すると、戦略鉱脈のグリッドが当たったチャンクはその地方以外で「鉱脈なし」になる。
戦略プロファイルが6種あれば、単純計算で鉱脈総数が目に見えて減る。対処は3つを併用する。

1. **基礎鉱脈のグリッドを詰める**: 石炭・鉄・銅の`placement.spacing`を既定の128から64程度へ縮め、`priority`を低く（走査順で後ろに）しておく。戦略鉱脈が弾かれたチャンクを基礎鉱脈が拾う。
2. **専用の充填鉱脈を追加する**: `moveearth_addtional:ore_vein_type/gravel`のような低価値・高密度の鉱脈を最低優先度で用意し、鉱脈ゼロ地帯を減らす。
3. **実測で検証する**: Phase 2の完了条件として、地方ごとの「1024×1024ブロックあたりの鉱脈数」と「鉱脈種別の内訳」を計測するデバッグコマンドを作り、地方間で総密度が概ね揃っていることを数値で確認する。

### 7.4 探知系との整合

`pick`の両方へ同じゲートを通すため、次はいずれも地方制と整合する。実装後に実機確認する。

- Vein Finder（近傍探索とレシピ指定探索）
- Vein Atlas（記録済み鉱脈一覧）
- JourneyMapオーバーレイ
- ComputerCraftのVein Finder Turtle
- JEI/EMI/REIの鉱脈カテゴリ表示。ただしこれらはクライアント側で全鉱脈を一覧するため、「この地方で採れるか」は表示できない。L4の地方情報UIで補う。

## 8. L3-b 通常鉱石生成への適用

### 8.1 PlacementModifier

座標に応じて生成可否を決められるバニラの仕組みは`PlacementModifier`である。これを1つ実装して登録する。

```json
{ "type": "moveearth_addtional:region_gate", "material": "gold" }
```

`material`は6.4で解決した素材名であり、8.2のBiomeModifierが実行時に埋める。
データパックへ手書きする機会は通常ない。

`getPositions`で地方の密度倍率を引き、0なら空のストリームを返して生成を止め、
1未満なら`RandomSource`でその確率だけ通す。**倍率は0.0〜1.0の減衰のみを扱う。**
増加方向は扱わず、上流のfeature側の`count`を「最も濃い地方の値」にしておき、他地方は減衰で表現する。
こうすると挙動が読みやすく、featureの複製も不要になる。

### 8.2 鉱石featureの動的なゲート挿入

除去対象のplaced featureを名前で列挙する方式は採らない。バニラの鉱石だけで十数個あり、Createの亜鉛があり、
worldgen MODやMOD構成が変わるたびに一覧の保守が必要になる。これは「これ専用の処理」そのものである。

代わりに、**自前のBiomeModifier型を1つ登録し、そのなかで鉱石featureを実行時に見つけて包む。**

```json
{ "type": "moveearth_addtional:region_gate_ores" }
```

`modify(biome, Phase.MODIFY, builder)`で次を行う。

1. `builder.getGenerationSettings().getFeatures(Decoration.UNDERGROUND_ORES)`を走査する。
2. 各`PlacedFeature`の`ConfiguredFeature`を辿り、6.4の手順で素材名を解決する。`random_selector`や`simple_random_selector`のような入れ子は再帰的に辿る。
3. 解決できたものだけ、`PlacementModifier`列の末尾に`region_gate`（引数は解決した素材名）を足した`PlacedFeature`を`Holder.direct`で組み直し、元の要素と差し替える。
4. 解決できなかったものは一切触らない。

1つのJSONだけで、バニラ鉱石・Createの亜鉛・未知のMODの鉱石を同じ経路で処理できる。列挙表は存在せず、
MOD構成が変わっても書き換える箇所がない。`underground_ores`ステップに限定するため、
構造物・植生・鉱石以外のfeatureには触れない。

副作用の確認として、`/moveearth region features <biome>`で「どのfeatureが包まれ、どれが素通しされたか」を
一覧できるようにする。8.2の網羅性はこのコマンドの出力で検証する。

### 8.3 スレッド安全性と初期化順序

`PlacementModifier`はチャンク生成ワーカースレッドから呼ばれる。ここが本計画で最も壊れやすい箇所である。

- 地域マップは構築完了後に不変とし、`volatile`な参照1つで公開する。ワーカースレッドは読むだけにする。
- **地域マップの構築は、最初のチャンクが生成されるより前に完了していなければならない。** 構築自体が`ChunkGenerator`と`RandomState`を必要とするため、`ServerAboutToStartEvent`では早すぎ、`ServerStartedEvent`ではスポーンチャンク生成が終わっていて遅い。
- 構築点は`ServerAboutToStartEvent`とする（`initServer`が`loadLevel`を呼ぶ直前に発火する）。**当初想定した`LevelEvent.Load`では遅すぎる**: `ServerLevel`のコンストラクタが`ensureStructuresGenerated()`から要塞リング配置を共通プールへ投げ、そのタスクがコンストラクタ実行中にバイオームソースを叩く。地形側で実機の例外により判明した。地域マップの構築も同じ制約を受けるため、同じ初期化点を使う。
- 保険として、`RegionResolver`は未構築時に「全資源を通す」中立値を返し、ワーカースレッドからの構築は絶対に行わない。中立値が返った場合はエラーログを出し、その状態でワールドが進行したことを運営が検知できるようにする。

## 9. L3-c C:DG石油埋蔵量への適用

### 9.1 C:DGの実機構

`OilChunksSavedData`（MIT、1.21.1ブランチで確認）が全てを持つ。

- 石油は**ブロックではなくチャンク単位のmB量**である。鉱石featureでもCOE鉱脈でもない第3の系統になる。
- `getChunkOilAmount(ChunkPos)`は、汲み取り済み・コマンドで上書き済みのチャンクなら記録値を返し、
  記録が無いチャンクでだけ`getBaseOilAmount`を呼ぶ。
- `getBaseOilAmount(ServerLevel, ChunkPos)`は**public static**で、シード・チャンク座標・チャンク内バイオーム・
  configのみに依存する純関数である。`PerlinNoise`の値を2乗して最大量へ掛け、閾値未満を0、上限超えを
  `Integer.MAX_VALUE`（無限）として返す。
- 制御手段は`createdieselgenerators:oil_biomes`（増量）と`deny_oil_biomes`（禁止）の**バイオームタグのみ**である。
  3.3のCOEと同じ制約であり、地方はバイオームと一致しないため、**コード介入が必須**という結論も同じになる。

### 9.2 介入点の選定

| 案 | 内容 | 判定 |
|---|---|---|
| A | `getBaseOilAmount`の戻り値へ地方倍率を掛け、倍率0ならHEADで打ち切る | **採用**。採掘・探知・コマンドの3経路が全てここへ収束するため、1箇所で整合が取れる |
| B | `deny_oil_biomes`タグで制御する | 却下。地方はバイオーム境界と一致しない。3.3のCOEと同じ理由 |
| C | KubeJSフック`CDGKubeJSPlugin.calculateOilChunks`を使う | 却下。公式の拡張点ではあるが、KubeJSが必須依存になる |
| D | `getChunkOilAmount`を差し替える | 却下。**記録済みの残量まで書き換えてしまう**。既に汲んだチャンクの状態を壊す |

**L3-aより素直である。** COEは`pick`の2つのオーバーロードを両方塞がないとVein Finderの表示が実際とズレるため
7.4の整合確認が要ったが、石油は`PumpjackHoleBlockEntity`・`OilScannerItem`・`CDGCommands`のいずれも
`getChunkOilAmount`経由で単一の関数へ落ちるため、**探知系との整合が自動的に取れる**。

### 9.3 Mixin仕様

```java
@Pseudo
@Mixin(targets = "com.jesz.createdieselgenerators.world.OilChunksSavedData", remap = false)
```

7.2のCOE Mixinと同じく`@Pseudo` + `targets`とし、C:DG未導入時もMOD全体が正常に起動するようにする。
注入は2つに分ける。

1. `@Inject(method = "getBaseOilAmount", at = @At("HEAD"), cancellable = true)`
   地方倍率が0なら`0`を返して打ち切る。
2. `@ModifyReturnValue(method = "getBaseOilAmount", at = @At("RETURN"))`
   倍率が0でないときに戻り値へ掛ける。MixinExtrasはPhase 0で利用可能と確認済みである。

**HEADで打ち切る形にするのは性能のためでもある。** `getBaseOilAmount`は`getBiomesInChunk`を呼び、これは
16×50×16＝**12,800回の`getBiome`**をチャンクごとに舐める。石油が出ない地方ではこれを丸ごと省けるため、
地方ゲートを入れると該当地方ではむしろ軽くなる。実測はPhase 3bの完了条件に含める。

**`Integer.MAX_VALUE`を倍率で掛けてはならない。** C:DGはこの値を「無限埋蔵」の番兵として使っており、
掛ければ即座にオーバーフローする。倍率適用前に番兵かどうかを判定し、番兵はそのまま通すか、
倍率0の地方でのみ0へ落とす。どちらにするかはPhase 3bで決める（既定は後者）。

`RegionOilGate`は、地域マップ未構築時・C:DG側の想定外変更検出時に倍率1.0を返し、素のC:DG挙動へ落ちる。
7.2の`RegionVeinGate`と同じ方針である。

### 9.4 ティアと運用

石油は**戦略ティア**とする。6.1の「無くても生存と基本的な工業化が成立する」という条件を満たし、
ディーゼル動力は完全に上位の選択肢であるため、排他にしても詰みを生まない。
一方で燃料は継続的に消費されるため、一度きりの交易では足りず**恒常的な取引関係**を要求する。
金・ダイヤ・エメラルドより交易と戦争の動機として強く働く可能性がある。

- ポンプジャックはパイプ列が`createdieselgenerators:oil_deposit`タグのブロックへ届くことを要求するが、
  これは深さの検査であり埋蔵量とは無関係である。本計画は触らない。
- C:DG自身の`OIL_MULTIPLIER`・`OIL_CHUNK_THRESHOLD`等のconfigはそのまま生きる。地方倍率はその上に乗る。
- Oil Scannerは倍率0の地方で0を表示する。これがプレイヤーへの直接の手掛かりになるが、
  「なぜ0なのか」はL4の可視化で説明する。

## 10. L4 可視化

「なぜここでは金が掘れないのか」が説明されないと、プレイヤーには不具合と区別がつかない。可視化は必須機能として扱う。

| 手段 | 内容 | 再利用する既存実装 |
|---|---|---|
| 境界通知 | 地方を跨いだ時にアクションバーへ地方名と主要資源を表示する | `TerritoryTransitionTracker`と同型の遷移検出 |
| 地図オーバーレイ | バニラ地図に地方境界と地方色を描く | `client/MapInstanceTerritoryOverlayMixin` |
| 地方情報タブ | S2 Hub画面に「地方」タブを足し、採れる資源・密度・近隣地方の資源を一覧する | `S2HubScreen` |
| コマンド | `/moveearth region info` を一般プレイヤーにも開放する（他地方の情報は伏せる） | 新規 |

他地方で何が採れるかをどこまで公開するかは運営方針の選択になる。既定は「自分が訪れたことのある地方のみ既知」とし、
未探索地方は伏せる。これが交易と探索の動機になる。全公開への切り替えはconfigで可能にする。

日本語と英語の両方の文言を用意する（README記載の方針に従う）。

## 11. S2連携

本計画の単体完了後に着手する。地方資源はS2の国家システムと噛み合う。

- 領土コアが置かれた地方の資源を、その国家の「産出資源」として集計し、国家情報へ表示する。
- 外交画面に「相手国が持つ資源」を表示し、交易交渉の材料にする。
- 戦争目的として「特定地方の占領」を設定できるようにする。Siegeの動機付けが地理に紐づく。
- `technology_tree`の各項目に資源要件を持たせるかは、詰みリスクが大きいため慎重に判断する。既定では持たせない。

## 12. 実装アーキテクチャ

```
com.ruskserver.moveearth_addtional
├─ region
│  ├─ RegionMapSavedData.java      プロファイル割り当ての永続化（5.4）
│  ├─ RegionResolver.java          タイルの region/continent レイヤー読み取り（5.1）
│  ├─ RegionProfile.java           プロファイル定義のレコード
│  ├─ RegionProfileLoader.java     データパックリロード対応
│  ├─ RegionProfileAssigner.java   地方へのプロファイル割り当て（純関数、テスト対象）
│  ├─ MaterialResolver.java        feature/鉱脈→素材名の実行時解決（6.4）
│  └─ RegionCommands.java
├─ region.worldgen
│  ├─ RegionGatePlacementModifier.java
│  ├─ RegionGateOresBiomeModifier.java  UNDERGROUND_ORESを走査してゲートを挿入（8.2）
│  └─ RegionWorldgenRegistration.java
├─ compat.coe
│  ├─ RegionVeinGate.java          鉱脈IDと地方の突き合わせ
│  └─ CoeVeinDensityDebug.java     密度計測コマンド
├─ compat.cdg
│  └─ RegionOilGate.java           チャンク座標→地方→石油倍率（9.3）
├─ mixin
│  ├─ CoeRandomSpreadGeneratorMixin.java
│  └─ CdgOilChunksMixin.java
└─ config
   └─ RegionResourceConfig.java
```

純関数として切り出した`RegionProfileAssigner`と`MaterialResolver`の解決規則は、
既存の`RandomSpawnPolicy`や`TreeShape`と同じくMinecraftに依存しない形にし、単体テストの主対象とする。
大陸検出と地方分割はタイル生成側（`tools/terrain/terraingen/regions.py`）にあり、こちらでは持たない。

## 13. 依存関係とビルド

- `build.gradle`へ `compileOnly 'maven.modrinth:create-ore-excavation:1.21.1-1.6.8'` を追加する。modrinth mavenは既に設定済みである。
- COEはMITライセンスである。`THIRD_PARTY_NOTICES.md`へ出典・著作者・ライセンスを追記する。
- 配布jarへCOEを同梱しない。`lib/`へ置く方式ではなくmaven解決とするため、READMEのローカル依存MOD一覧への追記は不要である。
- COEは任意依存とする。`neoforge.mods.toml`へ`type = "optional"`の依存として宣言し、未導入時はCOE連携部分だけが無効になるようにする。
- C:DGも同様に`compileOnly`の任意依存として追加する。MITライセンスであり、`THIRD_PARTY_NOTICES.md`へ追記する。
  現在`lib/`にも`build.gradle`にも存在しないため、**新規依存の追加になる**。modrinth maven（`maven.modrinth:create-diesel-generators`）
  で解決できるかをPhase 0で確認し、できなければ`lib/`へjarを置く方式へ落とす（既存のCreate本体と同じ扱い）。
- C:DGを実際にmodpackへ入れるかは本計画とは別の判断である。入れない場合、L3-cは死にコードにならないよう
  実装ごと見送る（第4条の「無効化したまま実装しない」に相当）。
- `gradlew assemble --offline`を通すため、依存追加後に一度オンラインでキャッシュを作る。

## 14. フェーズと完了条件

### Phase 0 前提確定

固定テーブルを作る作業はない。**実機のレジストリから事実を採取する診断コマンドを先に作る。**

- `/moveearth region probe` を実装する。実行中のサーバーから、`UNDERGROUND_ORES`の全featureと6.4による素材解決結果、COE鉱脈と`drilling`レシピの対応、`c:ores/*`タグの中身、C:DG導入時は`c:crude_oil`の解決結果をダンプする。
- そのダンプで、実際のMOD構成における鉱石featureの網羅率と、素材解決に失敗する対象を確認する。失敗が多ければ6.4の解決手順を一般化する方向で見直す（個別のMOD名を足す方向では直さない）。
- COEの`@Pseudo`Mixinが、COE未導入時にMOD起動を妨げないことを確認する。C:DGを入れるなら同じ確認をする。
- C:DGをmodpackへ入れるかを決める。入れないならL3-cは実装ごと見送る。
- 完了条件: `probe`の出力が記録され、本計画の既定値と該当箇所が実測に基づいて更新されている。

**一部実施済み (2026-09-18)**。`build/moddev/artifacts/neoforge-21.1.238-sources.jar`での照合結果。

| 確認項目 | 結果 |
|---|---|
| 初期化順序 | **解消**。自作地形の`TerrainTileStore`が`ServerAboutToStartEvent`でタイルを読み込み済みであり、地域マップはそこへ相乗りする。なお地形側の実機検証で、`LevelEvent.Load`は密度関数には**間に合わない**ことが判明している（`ServerLevel`のコンストラクタ自身が要塞配置を非同期に投げ、バイオームを標本化する）。地域マップ単体なら間に合うが、相乗りする以上この論点はもう無い |
| サーバー分離型: レジストリ照合 | **問題なし**。`DENSITY_FUNCTION_TYPE`はNeoForgeの`VANILLA_SYNC_REGISTRIES`に含まれない。サーバーにしかない型をクライアントが照合することはない |
| サーバー分離型: バイオーム同期 | **問題なし**。同期に使われる`Biome.NETWORK_CODEC`は気候と視覚効果のみで、`BiomeGenerationSettings.EMPTY`で再構築する。**feature改変はクライアントから不可視** |
| MixinExtras | NeoForgeに`mixinextras-neoforge-0.5.3`がjarjarで同梱済み。§7.2の`@WrapOperation`・`@Local`が使える |

残りの項目（`probe`コマンドの実装と実機ダンプ、COE未導入時の起動確認、C:DG採否の判断）は未実施。

### Phase 1 地域マップの読み取り

自作地形の採用で、この層は**作る作業から読む作業へ縮んだ**。

- `RegionResolver`がタイルの`region`/`continent`レイヤーを最近傍で読む経路を実装する（5.1）。
- タイル生成側の`regions.py`で、`region`レイヤーを海側へ最近傍拡張する（5.1のB案）。タイルの再生成が要る。
- `RegionMapSavedData`（プロファイル割り当てのみ）とコマンドを実装する。
- 完了条件: `/moveearth region info`が任意座標の地方を返す。`/moveearth region export`のPNGがタイル生成側の`regions.png`と一致する。沿岸・海底で最寄り陸地の地方が返る。IDが補間されていない（地方3と7の境界に地方5が現れない）ことをテストで固定する。

### Phase 2 COE鉱脈の地方適用

- Mixin、`RegionVeinGate`、プロファイル定義データパック、割り当て、密度計測コマンドを実装する。
- 完了条件: 戦略鉱脈が専門地方以外に生成されない。Vein Finderの探知結果と実際の鉱脈が全地方で一致する。地方ごとの鉱脈密度の差が基準値内に収まる。COE未導入でサーバーが正常起動する。

### Phase 3 通常鉱石の地方適用

- `PlacementModifier`、BiomeModifier、鉱石種別対応表を実装する。
- 完了条件: 戦略鉱石が専門地方以外に生成されない。基礎鉱石が全地方に存在する。チャンク生成中に例外・デッドロック・地域マップ未構築警告が発生しない。生成速度の劣化が許容範囲に収まる。

### Phase 3b C:DG石油の地方適用

Phase 3と独立しており、C:DGをmodpackへ入れると決めた場合にのみ実施する。

- `CdgOilChunksMixin`、`RegionOilGate`、6.4の流体解決、プロファイルへの`crude_oil`を実装する。
- 完了条件: 石油が専門地方以外のチャンクで0になる。既に汲み取り済みのチャンクの残量が変化しない。
  Oil Scannerの表示と実際のポンプジャックの産出が一致する。`Integer.MAX_VALUE`の無限埋蔵チャンクで
  オーバーフローが起きない。倍率0の地方で`getBaseOilAmount`の所要時間が短縮されることを実測する。
  C:DG未導入でサーバーが正常起動する。

### Phase 4 可視化

- 境界通知、地図オーバーレイ、Hubタブ、一般向けコマンドを実装する。
- 完了条件: 地方を跨いだ時に通知が出る。地図で境界が見える。Hubタブで採れる資源が分かる。日本語と英語の文言が揃っている。

### Phase 5 バランス調整

- COE鉱脈の`amountMultiplier`への地方倍率適用、通常鉱石の密度倍率の実測調整、準基礎ティアの最終判断を行う。
- 完了条件: 単独プレイヤーが任意の地方スタートでCreateの中盤（真鍮とエンチャント台）へ到達できる。戦略資源は必ず他地方との接触を要する。

各フェーズの完了時にJSON検証、対象テスト、Java 21での`assemble --offline`を通す（AGENTS.mdの検証手順に従う）。

## 15. リスクと未確定事項

| 項目 | 内容 | 対応 |
|---|---|---|
| COEの内部実装への依存 | `RandomSpreadGenerator`はCOEの内部クラスであり、更新で構造が変わりうる | `@Pseudo`で失敗を許容し、ゲートが機能しない場合はログを出して素のCOE挙動へ落とす。COEのバージョンを固定し、更新時は必ず再検証する |
| 規約タグ非準拠の鉱石 | `c:ores/*`を付けないMODの鉱石は素材解決できない | 解決不能な対象は常に許可し、生成を止めない。起動時WARNと`/moveearth region materials`で可視化し、必要ならconfigの上書きマップで個別対応する |
| 素材解決の誤り | 複数素材を産出する鉱脈や、複数タグに属するブロックの分類を誤りうる | 鉱脈は全素材が許可された地方でのみ出現させる。誤りは`probe`と`materials`の出力で検出する |
| C:DGの内部実装への依存 | `OilChunksSavedData`はC:DGの内部クラスであり、更新で構造が変わりうる | `@Pseudo`で失敗を許容し、ゲートが機能しない場合はログを出して素のC:DG挙動へ落とす。COEと同じ方針 |
| 無限埋蔵の番兵 | C:DGは`Integer.MAX_VALUE`を無限埋蔵として使う。倍率を掛けるとオーバーフローする | 倍率適用前に番兵を判定し、掛けない。Phase 3bの完了条件に含める |
| C:DGの未導入 | 現在modpackに入っていない | 任意依存とし、未導入時はL3-cだけが無効になる。入れないと決めたらL3-cは実装ごと見送る |
| 鉱脈密度の不均一 | 排他化で地方間の鉱脈総数に差が出る | Phase 2で数値計測を完了条件に含め、データ側で調整する |
| 地方境界の唐突さ | 境界を跨いだ途端に資源が変わる | **大幅に緩和済み**。地方は流域マージで切られるため境界が分水嶺に沿う（2.1）。当初懸念していたVoronoiの直線境界は発生しない。それでも唐突なら遷移帯をPhase 5で検討する |
| 完全排他による孤立 | 少人数サーバーや過疎地方で交易相手が存在しない場合、戦略資源へ永久に到達できない | 準基礎ティアで最低限を担保する。加えて「他地方の資源を得る非交易手段」（探索報酬、遠征）の要否をPhase 5で判断する |
| 既存ワールド | 本計画は新規ワールド前提である | 既存ワールドへの適用は行わない。ワールドリセット時期と足並みを揃える |
| タイルの差し替え | タイルを再生成すると地方IDの意味が変わり、割り当て済みプロファイルと食い違う | 起動時に検出して警告する。自動再割り当てはしない（5.4）。地方の切り方はワールド生成後に変えられないものとして運用する |

本計画に、特定の鉱石ブロック名・特定のfeature ID・特定の鉱脈レシピIDを書いた固定テーブルは存在しない。
資源の識別は規約タグ（`c:`）からの実行時解決だけで行うため、鉱石MODを足しても書き換えは要らない。
Phase 0以降で新たな固定テーブルを追加したくなった場合、それは一般化の失敗を意味するため、
テーブルを足す前に一般化の余地を検討する。

地形については、当初の「どのworldgenでも動く」という一般性は**意図的に手放した**。自作タイル地形を
前提に据えたことで、L1は走査・大陸検出・地方分割を持たずタイルを読むだけになり、
`getBaseHeight`の所要時間もモルフォロジー補正もk-meansの収束も論点から消えた。
worldgenを再び差し替えるなら、失うのは`RegionResolver`1クラスである。
