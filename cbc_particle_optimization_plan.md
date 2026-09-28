# CBCパーティクル軽量化

## 調査結果（2026-09-28）

対象ソースはCBC公式 `create-v6-1.21.1` ブランチ。

- BigCannonPlumeParticleはNoRenderParticleだが、tickごとに砲煙を生成する。
  初回にはバニラCLOUD、数tickの間にはFLAMEも追加する。
- CannonSmokeParticleはBaseAshSmokeParticleの更新に加え、風によるmoveを行う。
  地形衝突を保持したまま単純なCompute更新へ移すことはできない。
- 通常砲煙は独自RenderType・頂点overlayに砲力を格納し、専用gradientテクスチャを使う。
  shader-compatible設定ではFallbackCannonSmokeParticleへ置換される。
- 発生器、CPU更新・衝突、頂点生成、透明ピクセルの重なりを別の問題として扱う。
- 小型砲6門のユーザー計測ではShellExplosionSmokeParticleがCPU更新の上位。
  密度制御ON/OFFとも破棄0で、旧砲煙予算は発動していなかった。
  更新回数・生成数が異なるため累計CPU時間の差を改善率として扱わない。
- ShellExplosionSmokeParticleも通常更新＋風moveの2経路で衝突判定を行う。
  TrailSmokeParticleは毎tickの乱数による揺れ・重力・移動と末尾60tickのフェードを持つ。
  この段階では既存更新・描画を呼び続け、Computeへ丸ごと置換しない。

ソース:
- https://github.com/Cannoneers-of-Create/CreateBigCannons/blob/create-v6-1.21.1/src/main/java/rbasamoyai/createbigcannons/effects/particles/plumes/BigCannonPlumeParticle.java
- https://github.com/Cannoneers-of-Create/CreateBigCannons/blob/create-v6-1.21.1/src/main/java/rbasamoyai/createbigcannons/effects/particles/smoke/CannonSmokeParticle.java
- https://github.com/Cannoneers-of-Create/CreateBigCannons/blob/create-v6-1.21.1/src/main/java/rbasamoyai/createbigcannons/effects/particles/smoke/FallbackCannonSmokeParticle.java

## 実装済み: 計測と任意の密度制御

- クライアント設定 `moveearth_addtional-particles.toml`。初期状態では両機能OFF。
- `/moveearthparticles profile on` で集計をリセットしてCPU更新計測開始、`profile off` で停止。
- `/moveearthparticles status` でCBCクラスごとの生成数、破棄数、更新回数、累計CPU更新時間を表示。
  tickコスト上位8種類。数は生存数ではなく集計開始からの累計。
  フレーム時間・GPU時間・描画時間・粒子生成時間は測っていない。
  発生器のtickは子粒子生成コストを含む。計測自体にも負荷がある。
  CBC粒子のtick中に生成されたバニラ粒子も、弱参照で発生元へ紐付けてCPU更新を計測する。
  例: `BigCannonPlumeParticle -> CloudParticle`。計測開始前のバニラ粒子や、
  別経路・別スレッドで生成されたバニラ粒子の発生元は推定しない。
- `/moveearthparticles limit on/off` で装飾砲煙・爆発煙の生成数制御を切り替える。
  CannonSmokeParticle、FallbackCannonSmokeParticle、QuickFiringBreechSmokeParticleと
  ShellExplosionSmokeParticleが対象。
  近距離48ブロック以内128個/tick、遠距離32個/tickの独立予算が初期値。
  全CBC粒子共有の予算で、各砲ごとの最低数は保証しない。
  爆発煙には別の近距離256個/tick・遠距離64個/tick予算を用意し、砲煙予算と競合させない。
  厳密なクラス一致により煙幕弾、ガス、軌跡、爆発の発生器、火花、未知の派生クラスは変更しない。
- makeParticleの返却時に判定し、providerがnullを返してfallbackを別生成する経路を二重課金しない。
  粒子のJavaオブジェクト生成は残るが、破棄分の以降の更新・描画を省く。
  直接addされた粒子、砲口から出るバニラCLOUD/FLAMEは密度制御の対象外。
- 次元移動・切断などParticleEngine.setLevelで集計・予算をリセット。
  PIP追加描画では予算消費や更新を追加しない。サーバー・命中・煙幕効果は変更しない。
- `/moveearthparticles collision on/off` は遠距離煙の衝突LODを切り替える。初期OFF。
  爆発煙・軌跡煙のみ、生成8tick以降かつカメラから64ブロック超で、
  元からhasPhysicsが有効な粒子の更新中だけhasPhysicsを無効にする。
  例外時も元のフラグを復元。近距離・新しい煙・煙幕弾・ガス・他粒子は対象外。
  位置・寿命・乱数・風・フェードの既存更新は継続し、軌跡の本数は削減しない。
  遠くの煙が地形を通り抜けるなど見た目の差があり、完全同一挙動の最適化ではない。
  statusの「衝突LOD更新」は対象更新回数で、削減した衝突呼出数や性能改善率ではない。

## 比較試験

同じ砲6門・同じ弾・同じ着弾点・同じ視点・射撃数・測定時間で比較する。
各測定前に既存煙が消えるまで待ち、`profile on` を再実行して集計をリセットする。
まず `limit off` / `collision off`、次に `limit on` / `collision off`、
最後に `limit off` / `collision on` を比べ、密度と衝突の効果を分離する。
collisionの確認には着弾点・軌跡まで64ブロック超の距離が必要。
FPSの比較は計測OFFでも行い、近距離・煙幕弾・ガス・PIP中の見た目も確認する。

## 次段階: GPU経路（未実装）

1. 同じ砲・砲数・射撃間隔で20秒程度計測し、上位タイプとFPS/フレーム時間を比較する。
   `profile on` 再実行でリセット。FPSの本比較は計測OFFでも行う。
   GPU時間は別途GPUプロファイラなどで確認し、CPU更新時間から推定で断定しない。
2. 描画側が主因なら、対象タイプの頂点生成をインスタンス化する。
   テクスチャ、砲力gradient、ライト、半透明、深度、風、sprite進行を保持する。
3. 更新側が主因なら、衝突不要なタイプだけSSBO＋Compute更新へ移す。
   GL4.5を推奨経路とし、利用機能を検出してCPU版へフォールバックする。
   発生データ以外を毎フレーム転送せず、GPUからの毎フレーム読み戻しも避ける。
4. 更新はゲームtick、描画は補間して通常視点とPIPの各視点で行う。
   レンズの追加描画で寿命や位置を二重更新しない。
5. Iris有効時の描画先・シェーダー・半透明順序を確認し、未対応の経路は従来描画に戻す。
6. 大きな煙のoverdrawが主因なら、煙専用の低解像度描画を別途検討する。

Compute・インスタンス描画・GPU時間計測はまだ追加していない。
単体テストとassembleは実施するが、ユーザー指示によりゲームは起動しない。
実機での見た目・フレーム時間改善は未確認であり、性能向上率は提示しない。
