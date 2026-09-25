# 木の生成

## 目的

バニラの木は「小道具」に見える。広葉樹を実寸に近い比率で生成し、森が森に見えるようにする。

## 参考にしたもの

[Midgard (Forge 1.20.1)](https://github.com/SbeevYT/Midgard-Forge-1.20.1) — MIT。
スクリーンショットの評価が高かったため構造を確認した。

分かったこと:

| 要素 | Midgard の実装 |
|---|---|
| 木の本体 | **手作りの NBT ストラクチャ 252個 (2.3MB)**。`Oh The Trees You'll Grow` の feature で設置 |
| 細い枝 | `BranchFenceBlock` — フェンスを継承した独自ブロック |
| 幹 | `oak_log` ではなく `oak_wood`（全面樹皮） |
| 大オークの樹冠 | 21×24×24（バニラの fancy oak は 7×7 程度） |

見栄えの構成要素は「①細枝ブロック ②巨大で不規則な樹冠 ③全面樹皮の幹」の3つ。
②③は独自ブロックなしで実現できる。

## 方針 (user 判断 2026-09-18)

**細枝ブロックは作らない。** 枝も丸太で作り、樹冠の内側に収めて
シルエットに出さない。クライアント資材がゼロなので player jar が太らない。

細枝が欲しくなったら後から枝ブロックを足せる。手戻りは枝の配置ロジックだけ。

## 実装

NBT ストラクチャではなく**手続き的生成**を選んだ。バニラの `TreeFeature` に
そのまま乗るため、苗木からの成長・バイオーム配置・葉の崩壊・装飾がすべて
無料で付いてくる。252個のモデルを作る必要もない。

| ファイル | 役割 |
|---|---|
| `worldgen/tree/TreeShape.java` | 幾何のみ。Minecraft 非依存でテスト可能 |
| `worldgen/tree/BroadTrunkPlacer.java` | 根張り→テーパー幹→枝。枝先ごとに foliage attachment を返す |
| `worldgen/tree/BroadFoliagePlacer.java` | 扁平楕円体の樹冠。表面だけを不規則に間引く |
| `worldgen/WorldgenRegistration.java` | placer type の登録 |

データパック: `data/moveearth_addtional/worldgen/{configured_feature,placed_feature}/`、
バイオーム差し替えは `data/moveearth_addtional/neoforge/biome_modifier/`。

対象バイオーム: forest / flower_forest / birch_forest / old_growth_birch_forest / plains。
バニラの木 feature は `neoforge:remove_features` で外す（残すと大木の中に小木が生える）。
dark_forest は巨大キノコを含む feature なので今回は触っていない。針葉樹も未着手。

### 設計上の判断

- **幹は `oak_wood`**: 横向きの丸太は木口（切り株の断面）が出る。全面樹皮なら出ない
- **枝の丸太は軸を向ける**: ブロックごとに自分のオフセットから支配軸を決めるので、
  外へ伸びながら登る枝が実際に立ち上がる位置で横→縦に変わる
- **枝の方位は等分＋ジッタ**: 完全等分は電柱、完全ランダムは片側に寄って木が傾く
- **樹冠は表面だけ間引く**: 全体を間引くと天井に穴が開いて陽が差す
- **`min_clipped_height`**: 崖下などで満高が取れない時に低く生えることを許す。
  これが無いと密な森が疎になる

### 失敗記録

**データパックの形式ミスでサーバーを落とした (2026-09-18)。**
`IntProvider` を `{"type":"minecraft:uniform","value":{"min_inclusive":3,...}}` と
書いたが、これは loot の number provider の形。worldgen の int provider は
`value` で包まず `{"type":"minecraft:uniform","min_inclusive":3,"max_inclusive":4}`
と平らに書く。レジストリ読み込みで落ちるため、サーバーは起動すらしない。

**書いてからサーバーが起動を拒否するまで、誰もこのファイルを見ていなかった。**
テストソースセットには意図的に Minecraft が入っていないので実コーデックを
呼べない。代わりに `tools/validate_worldgen.py` を追加した。NeoForge の
sources jar から

- dispatch id → クラス (`register("uniform", UniformInt.CODEC)` を拾う)
- そのクラスと親クラスの `fieldOf` 名

を読み、我々の JSON のキーと突き合わせる。自前の placer も同じ方法で、
`WorldgenRegistration` の登録から辿って検証する（親クラス由来の
`radius`/`offset` も解決する）。

最初はバニラの**データ**を正解として比較したが、これは駄目だった。
既定値で常に省略される optional フィールドと、存在しないフィールドを
区別できず、`would_survive` の `offset`（実在する optional）を誤検出した。
**宣言を読む**方式に変えて解決。

導入後の確認として `value` 包みを一時的に戻し、検出することを確認済み。

幹のテーパーに `sqrt(t)` を使っていた。`sqrt` は小さい t で急激に増えるので
**根元から数ブロックで細くなり**、girth 3 が根張り以外どこにも現れなかった。
コメントには「現実の幹は最初の数mで太さを失いその後ほぼ平行」と書いてあり、
式はその逆を実装していた。正しくは指数 > 1（現在は `t*t`）。

テストのシルエット出力で発見。数値の assertion だけでは通っていた
(`>= 2` を満たしていたため)。`TreeShapeTest.silhouette` はこのために残してある。

## 未着手

- 針葉樹（トウヒ・マツ）: 現在の樹冠は広葉樹のドームなので別の foliage placer が要る
- dark_forest、savanna、jungle
- 細枝ブロック（user が必要と判断した場合のみ）
