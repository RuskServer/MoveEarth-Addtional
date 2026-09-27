# TaCZ PIPスコープ試作

更新: 2026-09-27。Minecraft 1.21.1 / NeoForge、手元のTaCZ
`1.1.8-hotfix-r6`を対象にした独自実装。外部アドオンのコード・素材はコピーしない。

## 現在の試作範囲

- ELCAN限定を解除。TaCZのクライアント定義でscopeとして登録され、既存のレンズマスクを
  持つ瞄具を対象とする。標準パックのACOG・ELCAN・可変倍率・高倍率スコープや、
  同じモデル描画方式を使う外部ガンパック、銃の内蔵スコープも対象候補になる。
- 現在選択中の倍率を毎フレーム取得してレンズFOVへ反映する。
  `minimumMagnification` 初期値2倍未満は従来描画。ELCANの1.25倍側や等倍ドットサイトは対象外。
- 複数レンズではTaCZのレンズごとのステンシル番号を使って合成する。
  複合サイトはscope用レンズのみ対象とし、識別できないモデルは従来描画。
- 一人称でADS進行が1%を超えた時からPIPへ切り替える。
  レンズ倍率はADS進行に合わせて1倍から選択倍率へ変化する。
  旧版の98%判定はADS途中に全画面ズームが残るため廃止。
- FOVイベントの先頭でTaCZの倍率変更前の通常FOVを記録し、GameRendererの世界FOV返却時に
  外側は通常FOV、レンズ側はその通常FOVから求めた倍率FOVへ確定する。
  手・銃のモデル用FOVはTaCZのまま維持する。
- ワールドを狭い視野角でもう一度描画し、TaCZの既存レンズのステンシルへ合成。
  外側は全画面ズームを抑止し、レンズ内だけ倍率を反映する。レティクル・黒縁はTaCZの描画を維持。
- 追加パスでは手・銃を描画しない。通常パスで手を描画する。
- レンズ描画解像度は画面の縦横各50%が初期値。追加のワールド描画分の負荷が発生する。
- 初期OFF。クライアントだけの機能で、パケット・命中判定・弾道・サーバー側処理は変更しない。
- Iris導入だけを理由とした除外は廃止。パックOFFなら従来のPIP経路を使用する。
  パックONは `irisExperimental` を有効にした場合のみ試験対応する。
  Oculus / Accelerated Rendering、Fabulous、バニラのポストエフェクト使用時は通常描画へ戻す。
  Iris + Distant Horizonsはグローバル描画状態の共有を避けるため試験対象外。
- 描画例外時はログを残してその接続中は無効化。ログアウトと画面サイズ変更、非使用時に
  レンズ描画先を解放する。ADS解除だけではキャッシュを保持し、照準のたびに再コンパイルしない。

### Iris試験互換

- Iris 1.8.14-beta.1 / NeoForge 1.21.1の実jarで内部メソッド・Sodium経路を確認した。
  パック名による分岐やIrisの同梱・必須依存は追加しない。
- レンズ用の `IrisRenderingPipeline` を別に生成し、追加パス中のみ
  `PipelineManager.pipeline` と `preparePipeline` の返却値を切り替える。
  通常パスのcolortex、履歴バッファ、影描画先はレンズ用と共有しない。
- Irisの手描画はGameRendererのrenderHandフラグだけでは止まらないため、
  `HandRenderer.renderSolid` / `renderTranslucent` も追加パス中だけ抑止する。
- CapturedRenderingState、WorldRenderingSettings、ShadowRendererの変更可能フィールドを退避し、
  例外時も通常パイプラインと状態を復元する。行列・霧ベクトルは値をコピーする。
- 次元・メインパイプラインの変更、サイズ変更、機能OFF、ログアウトでレンズ用を解放する。
  フック・APIが使えない版では診断理由を出し、通常描画へ戻す。
- **合成はまだTaCZの手描画段階。** レンズ側で最終処理済みの画像に通常側の最終処理が
  再適用される可能性があり、トーンマップ、露出、TAA、被写界深度、屈折を含む
  任意パックでの完成互換は保証しない。最終処理後の合成とレティクル保持は次段階。
  シェーダー有効環境でゲームを起動しておらず、この経路は初期OFFの実験実装である。

## 有効化と試験

クライアントコマンド `/moveearthpip on` / `off` で切替・保存できる。
`/moveearthpip debug` は診断表示を切替。チャットを閉じ、ADS中に確認する。
表示はON/OFF、無効理由、倍率、通常側/レンズ側FOV、合成処理実行の有無。
`disabled_in_config` は設定OFF、`iris_experimental_disabled` はIris試験モードOFF。
`iris_hooks_not_ready` は描画フックの初回待機または接続不成立、`iris_api_unsupported` はAPI不一致。
`/moveearthpip shaders` でIris試験モードを切替・保存できる。PIP本体のON/OFFとは別設定。
`lens_accessor_missing` はモデルのMixin接続不成立、`composite false` は合成が実行されていない。
合成処理実行は実画面上の見た目まで保証するものではない。
ステンシルの深度確認はCore Profile対応の描画先attachment問い合わせを使用する。
旧 `glGetInteger(GL_STENCIL_BITS)` はCore ProfileでGL_INVALID_ENUMになるため使用しない。
停止時の理由は保持し、`stencil_buffer_unavailable` / `stencil_test_disabled` と
`lens_render_failed_例外型` / `lens_composite_failed_例外型` を区別する。
修正版では `/moveearthpip on` を再実行すると安全停止状態を解除して再試行できる。

起動後に生成される `config/moveearth_addtional-scope-pip.toml`:

```toml
enabled = true
irisExperimental = false
resolutionScale = 0.5
minimumMagnification = 2.0
```

編集後はクライアントを再起動する。元へ戻す場合は `enabled = false`。
対応する同じTaCZ版を使い、まず標準ガンパックで確認する。
外部パックの専用レンダラーや独自シェーダー、モデル構造まで互換を保証するものではない。

1. シェーダー関連MODなし・FancyまたはFastで、OFF時に既存のズームが維持されること。
2. ON時にレンズ外が通常視野、レンズ内だけ拡大され、黒縁・レティクルが残ること。
3. 高低倍率切替、ADS解除、左右の利き手、銃持替え、リロード、死亡、三人称切替。
4. 遠距離標的への命中、反動中のレティクルと弾道、カメラ揺れとの整合性。
5. 解像度・GUIスケール変更、リソース再読込、再接続、次元移動。
6. 地形・エンティティ・半透明・水面・Sable車体と車上からの照準、他のHUDへの影響。
7. OFFとのFPS比較。高倍率化や描画間引きは、この試作が成立してから検討する。
8. ACOG、ELCAN、8倍、可変倍率を比較し、倍率変更が鏡内へ反映されること。
   2倍未満の設定・等倍サイトへの切替では元の描画へ戻ること。
9. 内蔵スコープと外付けスコープの切替、複合サイトの別レンズ、外部ガンパックの対応モデル。

## 実装境界と未検証事項

`client/scope`が設定、レンズFOV計算、描画先管理と合成を担当する。
クライアントMixinでGameRendererの追加パス、Minecraftの描画先、TaCZの世界FOVとレンズ合成へ接続する。
追加パス後に通常ワールドを描画し直し、通常パスのカメラ・射線選択を最後に更新する。
Mixin対象の変更は別TaCZ版では保証しない。

JUnitとビルドは実施するが、ゲームはユーザー指示により起動しない。
GL描画、レンズ位置、ステンシル合成、他MOD描画との整合性は実機未確認。
コンパイル成功はPIPの見た目・照準一致の動作保証ではない。
真の別カメラ位置（レンズの物理位置）による視差、アイレリーフ、完成したシェーダー互換、
ガンパック全対応はこの段階に含めない。複数レンズへの合成は実装済みだが、
異なるレンズ位置・独自レティクルでの視差と照準一致は実機未確認。
