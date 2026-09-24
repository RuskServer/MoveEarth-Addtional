# MoveEarth バニラ進捗再実装計画

更新日: 2026-09-23
対象: Minecraft 1.21.1 / NeoForge / MoveEarth-Addtional v3.2

実装状況: 第1〜3段階を実装済み。初期37項目、固有criterion、主要ゲームイベント連携、
旧schema 3からの一度限りの移行、旧GUI/同期の無効化を完了した。第4段階のうちREADMEとTipsの
導線変更も完了。旧データクラスと旧JSONの物理削除だけは、既存ワールドの移行期間終了後に行う。

## 1. 方針

現行の独自ガイドシステムを廃止し、Minecraft標準の進捗システムへ完全に置き換える。

- 表示、ツリー、アイコン、説明、達成トースト、チャット通知はバニラ進捗を使用する。
- 保存もプレイヤーごとのバニラ進捗データを使用する。
- MoveEarthは標準条件で判定できない行動のカスタムcriterion triggerだけを提供する。
- 独自GUI、同期パケット、追跡HUD、検索、JEIボタンは作らない。
- 進捗は案内と実績記録に使い、建国、レシピ、装備、Siege参加の解禁条件にはしない。

既定キーの `L`、ポーズメニューの進捗ボタン、`/advancement`、データパックによる追加・変更を
そのまま利用する。

## 2. 廃止対象

### クライアント

- `TechnologyScreen`、`TechnologyClientState`、`JeiTechnologyBridge`
- 技術状態をアイテムツールチップへ追加する処理
- ガイド用の要求、追跡、操作、同期パケット
- 独自ガイドを開くキー、ボタン、翻訳

### サーバー

- `TechnologyDefinitions`、`TechnologyViewService`、`TechnologySnapshot`
- `TechnologyProgressPolicy`、`TechnologyProgressApi`
- `NationTechnologySavedData` の独自進捗保存
- `data/moveearth_addtional/technology/` のschema 2定義
- 国家進捗、独自前提判定、独自完了通知

旧ワールドを移行するため、最初のリリースでは `NationTechnologySavedData` を読み取り専用の
移行元として残す。移行完了後のメジャー更新で旧クラスと旧データを削除する。

## 3. データ構成

Minecraft 1.21.1のデータパスを使う。

```text
data/moveearth_addtional/advancement/
├── root.json
├── getting_started/
├── nation/
├── industry/
├── engineering/
├── warfare/
└── exploration/
```

MoveEarthのルート進捗は1つとし、参加直後の導線として国家Hubを開く進捗を置く。
6分野はその先から枝分かれさせる。

```text
MoveEarth
└─ 国家への入口（国家Hubを開く）
   ├─ はじめの一歩
   ├─ 国家と領土
   ├─ 産業と物流
   ├─ 防衛工兵
   ├─ 戦闘とSiege
   └─ 探索とPvE
```

表示規則:

- `task`: 通常の体験。
- `goal`: 複数機能を組み合わせる中級目標。
- `challenge`: 難しい実績。
- `show_toast`: 原則有効。
- `announce_to_chat`: 重要なgoalとchallengeだけ有効。
- `hidden`: 秘密進捗だけ有効。
- 背景画像はルートだけに設定する。

親は表示上の案内に使う。独自コードで前提ロックを追加しない。

## 4. 初期進捗

初回は、サーバー側で確実に判定できる37項目に絞る。

### はじめの一歩

| ID | 表示名 | 条件 | 判定 |
|---|---|---|---|
| `root` | MoveEarth | サーバーへ参加 | `minecraft:tick` |
| `getting_started/open_nation_hub` | 国家への入口 | 国家Hubを開く（既定はNキー） | カスタム |
| `getting_started/check_recipe` | 作り方を調べる | 作業台を製作 | バニラ条件 |
| `getting_started/cold_protection` | 寒さに備える | 防寒対象装備を着用 | バニラ条件 |
| `getting_started/rest` | 束の間の休息 | MoveEarthの休息回復を受ける | カスタム |

`getting_started/open_nation_hub` は `root` の唯一の直下進捗にし、ほかの主要分野はこれを
表示上の親にする。参加後、最初に国家Hubへ触れさせ、無所属なら加入申請・建国・領土確認へ、
所属済みなら国庫・役職・外交・Siegeへ自然に進める構造にする。

説明文のキー表示は固定文字列の `N` にせず、JSONテキストコンポーネントの `keybind` に
`key.moveearth_addtional.s2_hub` を指定する。これによりキーを変更したプレイヤーには現在の
割り当てが表示される。達成判定も物理Nキーの入力ではなく、サーバーがHub要求を受理して
`S2C_S2HubSnapshotPacket` を返す成功地点で `nation_hub_opened` を発火させる。キー変更時や
別画面からHubへ戻った場合でも正しく達成でき、無効なパケット受信だけでは達成しない。

JEIを開くこと自体は条件にしない。バニラ進捗画面からJEIを直接開けず、導入有無でも達成可否が
変わるためである。

### 国家と領土

| ID | 表示名 | 条件 | 判定 |
|---|---|---|---|
| `nation/view_territory` | 土地を読む | 領土地図またはプレビューを開く | カスタム |
| `nation/apply` | 国への扉 | 国家へ加入申請を送る | カスタム |
| `nation/citizen` | 一国の市民 | 国家へ加入または建国 | カスタム |
| `nation/found` | 建国者 | 国家設立と首都コア登録を完了 | カスタム、goal |
| `nation/treasury` | 国庫への貢献 | 自分の資金を国庫へ入金 | カスタム |
| `nation/upkeep` | 国を維持する | 国庫から維持費を支払う | カスタム、goal |

規則確認や説明の読了を条件にしない。必要な確認は建国画面内へ残す。

### 産業と物流

| ID | 表示名 | 条件 | 判定 |
|---|---|---|---|
| `industry/andesite_alloy` | 回転力への入口 | 安山岩合金を入手 | バニラ条件 |
| `industry/rotation` | 回り始めた工房 | Create動力源を設置 | バニラ条件 |
| `industry/iron_sheet` | 基本加工 | 鉄板を入手 | バニラ条件 |
| `industry/steam` | 蒸気時代 | 蒸気設備を建設 | バニラ条件、goal |
| `industry/electricity` | 電力時代 | CEEの基礎設備を建設 | バニラ条件、goal |
| `industry/mekanism` | 工業化 | Mekanismの基礎機械を入手 | バニラ条件、goal |
| `industry/freight` | 物流網 | 列車または対象輸送を完了 | カスタム |

任意Modを参照する進捗にはNeoForgeのmod-loaded条件を付け、未導入時は読み込まない。

### 防衛工兵

| ID | 表示名 | 条件 | 判定 |
|---|---|---|---|
| `engineering/welding_tool` | 溶接機 | 補強用溶接機を入手 | バニラ条件 |
| `engineering/reinforce` | 最初の補強 | 補強を有効化まで完了 | カスタム |
| `engineering/seal` | 防壁を閉じる | 漏出領土を閉鎖状態へ戻す | カスタム、goal |
| `engineering/vehicle_core` | 車両の心臓 | 車両コアを正常登録 | カスタム |
| `engineering/vehicle_repair` | 野戦修理 | 損傷車両を修理 | カスタム |
| `engineering/siege_repair` | 砲火の後で | Siege被害を受けた補強を修復 | カスタム、goal |

補強は設置開始時ではなく有効化完了時に発火させる。

### 戦闘とSiege

| ID | 表示名 | 条件 | 判定 |
|---|---|---|---|
| `warfare/combat` | 実戦配備 | 正規のCombat Timerが開始 | カスタム |
| `warfare/siege_participant` | 戦線へ | Siegeへ有効参加 | カスタム |
| `warfare/artillery` | 砲兵支援 | CBCで敵補強へ有効ダメージ | カスタム |
| `warfare/mobile_force` | 機動戦力 | 車両コア付き移動体で戦闘参加 | カスタム、goal |
| `warfare/revive` | 戦闘救護 | Siege中に味方を蘇生 | カスタム |
| `warfare/imprison` | 捕虜確保 | 敵を収監口へ収監 | カスタム、goal |
| `warfare/free_prisoner` | 奪還 | 敵国の捕虜を解放 | カスタム、goal |
| `warfare/core_sabotage` | 最後の工作 | コア工作の設置または解除を完了 | カスタム、challenge |
| `warfare/defend` | 国土防衛 | 防衛側でSiege終了まで戦う | カスタム、challenge |

キル数だけを重ねる進捗は作らず、兵站、工兵、救護、修理も戦闘の枝へ含める。

### 探索とPvE

| ID | 表示名 | 条件 | 判定 |
|---|---|---|---|
| `exploration/warehouse` | 放棄された倉庫 | Warehouse保護範囲へ進入 | カスタム |
| `exploration/warehouse_raid` | 警備隊との交戦 | Warehouse襲撃へ有効参加 | カスタム |
| `exploration/warehouse_boss` | 警備隊長撃破 | ボス撃破へ有効参加 | カスタム、goal |
| `exploration/warehouse_loot` | 戦利品回収 | 輸送コンテナから報酬回収 | カスタム |

ボス進捗はラストヒットだけで判定せず、既存の参加者判定結果を使用する。

## 5. criterion trigger

アイテム入手、作成、設置、討伐などは既存の `minecraft:*` criterionをJSONから使う。
MoveEarth固有行動には汎用トリガーを1つ登録する。

```text
moveearth_addtional:event
```

```json
{
  "trigger": "moveearth_addtional:event",
  "conditions": {
    "event": "reinforcement_activated"
  }
}
```

必要な条件だけ追加する。

最初の固有イベントは `nation_hub_opened` とする。これは
`C2S_RequestS2HubPacket` の受信そのものではなく、`S2NationViewService.sendHub` が有効な
スナップショットをプレイヤーへ送信した時点で発火する。

- `side`: attacker / defender
- `minimum_amount`: 修理量や有効ダメージ
- `weapon_family`: cbc / warnautics / infantry
- `vehicle_required`: 車両コア付き移動体か
- `result`: victory / defeat / completed

各サービスは処理成功後に `MoveEarthCriteria.EVENT.trigger(player, context)` を呼ぶ。
操作開始時、受信パケット上、キャンセル済み処理では発火しない。

## 6. 回数と累計

バニラ進捗は任意の数値ゲージ表示には向かないため、独自カウンター画面は復活させない。

- 意味のある初回完了を進捗にする。
- 回数を表したい場合は `初回`、`熟練`、`達人`の段階別進捗にする。
- 累計が必要ならMinecraftのカスタム統計へ保存する。
- 統計更新時に閾値対応のcriterion triggerを発火させる。

独自SavedDataへ同じ回数を二重保存しない。

## 7. 通知と報酬

通知はバニラ進捗のトーストとチャット通知だけを使う。独自トースト、BossBar、進捗HUDは
追加しない。

- task: トーストのみ。
- goal: トースト、必要なものだけチャット通知。
- challenge: トースト、チャット通知、標準の進捗音。

初回実装では経験値、アイテム、通貨を報酬にしない。将来追加する場合は進捗JSONの
`rewards` からloot tableまたはfunctionを呼ぶ。

## 8. 旧ガイドからの移行

旧schema 3で実行動を記録していたものだけ、Advancement APIから対応するcriterionを付与する。

| 旧記録 | 新進捗 |
|---|---|
| 国家加入・建国 | `nation/citizen` |
| 領土プレビュー | `nation/view_territory` |
| 安山岩合金 | `industry/andesite_alloy` |
| 水車設置 | `industry/rotation` |
| 鉄板加工 | `industry/iron_sheet` |
| TaCZ装備・標的命中 | 採用する対応進捗 |

画面を開く、規則確認、建国説明確認は移行しない。

- ログイン時に一度だけ旧データを読む。
- プレイヤー単位の移行済みフラグを持つ。
- 再ログイン、reload、再起動で二重実行しない。
- 移行による付与では報酬を発生させない。
- 旧データは移行リリース中だけ読み取り専用で保持する。

## 9. データパックと管理

- 進捗JSONはサーバーデータパックで上書き・追加可能にする。
- `/reload` 後はバニラの進捗再読込を使用する。
- 管理者は `/advancement grant|revoke` を使用する。
- 独自のgrant、revoke、resetコマンドは追加しない。
- 進捗IDは公開仕様とし、リリース後は可能な限り変更しない。

## 10. 実装段階

### 第1段階: バニラ進捗への置換

- ルートと6分野の進捗JSONを作る。
- `moveearth_addtional:event` criterion triggerを登録する。
- 国家Hubの表示成功地点へ `nation_hub_opened` を接続し、ルート直後の必須導線にする。
- 説明文には `key.moveearth_addtional.s2_hub` の現在のキー割り当てを表示する。
- 国家、領土、補強、Warehouseの成功地点へ接続する。
- 旧ガイドからの一度限りの移行を実装する。
- 独自ガイドGUIとネットワーク同期を無効化する。

この段階で `L` からMoveEarthタブを開き、主要項目を達成できる状態にする。

### 第2段階: Siegeと役割別進捗

- Siege、砲撃、救護、捕虜、コア工作、防衛結果を接続する。
- 車両コアと車両修理を接続する。
- 無効な攻撃や同一戦争での不正な判定を防ぐ。
- goalとchallengeの通知範囲を実機調整する。

### 第3段階: 外部Modと産業

- Create、CEE、Mekanism、CBC、TaCZ、PlayerReviveで既存条件が使える範囲を確認する。
- 標準条件で取れない成功イベントだけMixinまたは連携APIから発火する。
- 任意Modの進捗へNeoForgeの読込条件を付ける。
- 必要な累計だけカスタム統計と段階別進捗へ追加する。

### 第4段階: 旧システム撤去

- 移行実績を確認後、`technology` パッケージ、旧JSON、旧翻訳、旧テストを削除する。
- READMEの初心者ガイド表記をMoveEarth進捗へ変更する。
- Tipsから既定キー `L` を案内する。

## 11. テスト

- 全進捗JSONがMinecraft 1.21.1で読み込める。
- 親ID、アイテムID、criterion IDの欠落を検出できる。
- 任意Modなしでは対応進捗を読み込まない。
- 成功確定後だけカスタムトリガーが発火する。
- キャンセル、失敗、味方攻撃、保護対象では達成しない。
- Warehouse参加者へ付与され、周辺にいただけの人へ付与されない。
- 旧schema 3の移行は一度だけで、報酬を重複させない。
- `/advancement grant`、`revoke`、`/reload` が標準通り動く。
- 専用サーバーとプレイヤー用jarの双方で動作する。
- Java 21で関連テストとoffline assembleが成功する。

## 12. 完了条件

- `L` のバニラ進捗画面にMoveEarthタブが表示される。
- サーバー参加の次に国家Hubを開く進捗が表示され、実際の割り当てキーで案内される。
- 独自ガイド画面、同期、進捗保存を使わない。
- 初期37項目がバニラ進捗として定義される。
- MoveEarth固有行動は成功確定後にカスタムcriterionから達成される。
- 建国、産業、防衛、Siege、捕虜、車両、Warehouseを網羅する。
- 進捗未達がゲーム機能を妨げない。
- 旧ガイドの信頼できる実行動だけが安全に移行される。

## 13. バニラ進捗の制約

- 画面内検索、章別達成率、任意の詳細パネルは使えない。
- アイコンからJEIを直接開けない。
- 任意の数値を常時進捗バーへ表示できない。
- 項目が多すぎるとツリーが横長になる。

この制約を独自GUIで補わず、短い分岐、簡潔な説明、段階別進捗でバニラ画面内へ収める。
