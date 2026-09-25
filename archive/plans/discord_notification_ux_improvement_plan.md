# Discord通知システム UX改善計画

## 実装状況（2026-09-24）

第1〜5段階を実装済み。通知センター、権限別の個人/国家リンク、カテゴリ設定とプリセット、型付き表示モデル、
HP帯通知、digest、メンションcooldown、配送履歴、`/moveearth setup`、Minecraft管理診断、player/serverビルド
分離検証までコードと単体テストへ反映した。実Discordでの権限削除・API障害・モバイル表示確認は、実運用環境で
行う受け入れ試験として残す。

## 1. 目的

Discord連携を「手順書を読みながら複数コマンドを打つ管理機能」から、次の状態へ改修する。

- ゲーム内だけで、現在の接続状態・次に必要な操作・通知内容を理解できる。
- Discord側は案内に従って連携でき、通常操作は埋め込み＋ボタン/選択肢で完結する。
- 受信通知は内部データの羅列ではなく、「何が起きたか」「どこか」「何をすべきか」が読める。
- 緊急通知は即時に届き、低優先度イベントや反復イベントは集約して通知疲れを防ぐ。
- Minecraftアカウント本人確認と、国家の通知先管理を明確に分離する。
- Botトークン、Discord ID、内部UUIDを一般ユーザー向け画面や通知本文へ露出しない。

国家権限、通知outbox、指数バックオフ、重複抑止、JDAのサーバー専用ビルド分離は維持する。

## 2. 現状監査

調査対象:

- `NationNotificationsScreen`
- `DiscordBotConfig`
- `NationNotificationSavedData` / `NationNotificationService`
- Discord連携packet群
- `DiscordBotService` / `DiscordCommandListener`
- `MoveEarthDiscordEmbeds` / `DiscordText`
- `DISCORD_BOT_OPERATIONS.md`

### 2.1 ゲーム内画面

- 「ゲーム内通知」「Discord配送」「座標」「Siegeメンション」の4トグル、リンクコード入力、
  国家リンク、本人確認、配送待ち件数が1画面に同列で並ぶ。
- 国家リンク用コードと本人確認用コードが同じ入力欄を共有し、コード種別を画面上で判別できない。
- 国家リンクと本人確認の違い、Discordで先に実行すべきコマンド、完了後の次操作が画面だけでは分からない。
- `linked` は国家とDiscordサーバーの接続だけを表し、Bot接続、本人確認、通知チャンネル、
  メンション役職、最終配送結果を区別できない。
- 配送待ち件数だけでは「正常に待機」「再試行中」「送信不能」を区別できない。
- 保存前の変更表示、破棄確認、処理中表示、応答タイムアウト、テスト送信がない。
- 520×390前提の固定配置で、狭いGUIや長い翻訳への対応が弱い。
- 通知設定画面は国家設定画面の奥にあり、現S2 Hubでは所有者以外が国家設定へ到達しにくい。
  `MANAGE_NOTIFICATIONS` を持つ非所有者の導線が不自然。

### 2.2 明確な機能上の不整合

- `C2S_LinkDiscordAccountPacket` は通常プレイヤーの本人確認を許可している。
- 一方、`NationNotificationsScreen` は入力欄と「本人確認」ボタンを `canManage` で無効化する。
- そのため通知管理権限を持たない一般メンバーは、設計上可能な本人確認をGUIから実行できない。

これは第一段階で修正する。本人確認は個人操作、国家リンクは国家管理操作として別々に認可・表示する。

### 2.3 設定粒度

現在は25種のイベントに対し、国家単位で次の4値しか持たない。

- ゲーム内通知の全体ON/OFF
- Discord配送の全体ON/OFF
- 座標の全体ON/OFF
- Siege開始時のメンションON/OFF

このため、加入申請は欲しいが派遣契約は不要、Siegeは即時だが復興進捗はまとめて欲しい、といった
現実的な運用ができない。結果として全通知を切るか、ノイズを受け入れるかの二択になる。

### 2.4 Discord上の受信表示

- 通知タイトルと色はイベント別だが、本文は常に「国家通知です」。
- `externalArguments` を `Detail 1`〜`Detail 8` として表示するため、UUID、enum名、理由コード、
  HP値などの意味が受信者に伝わらない。
- 攻撃側/防衛側、対象コア、HP、Siege段階、残り時間、派遣契約の相手などが名前付き項目になっていない。
- 通知IDを常時footerへ出しているが、一般利用者には用途がない。
- 同じコアへの反復攻撃や漏出再判定を「一つの事案」としてまとめる仕組みがない。
- 失敗理由と復旧方法は `/moveearth audit` を読まないと分からず、監査項目も内部action名中心。

### 2.5 Discord上の操作

- 初期連携は `nation link` → ゲーム内入力 → `account link` → ゲーム内入力 →
  `channel` → `mention` → `settings` → `test` と分散している。
- slash commandの説明は英語、応答埋め込みは日本語中心で、言語と用語が統一されていない。
- 設定確認と設定変更が分離されず、`nation settings` はオプションを最低1つ覚えて指定する必要がある。
- unlinkなどの破壊的操作にDiscordコンポーネントによる確認段階がない。
- Botがオフラインの場合、Discordコマンド自体を利用できないため、Minecraft側の運営診断が不足する。

## 3. 目標となる情報構造

ゲーム内の入口をS2 Hubの独立した「通知」入口へ移す。所有者専用の国家設定に埋め込まない。

```text
国家Hub
  └ 通知センター
      ├ 状態          接続・配送状態、要対応、テスト
      ├ 通知内容      プリセット、カテゴリ別配送先、重要度
      ├ Discord連携   個人本人確認 / 国家通知先 / メンション
      └ 配送履歴      最近の成功・再試行・失敗（権限別）
```

### 権限別の表示

| 利用者 | 利用可能な操作 |
| --- | --- |
| 一般メンバー | 自分のDiscord本人確認・解除、自国の読み取り可能な接続状態 |
| `MANAGE_NOTIFICATIONS` | 国家リンク、通知設定、チャンネル・役職設定、テスト配送、国家監査 |
| 国家所有者 | 上記と同じ。所有者専用機能にはしない |
| サーバー管理者 | Botプロセス状態、全体配送統計、設定診断、再接続/再読込 |

権限不足の項目は単に消さず、「閲覧のみ」「必要権限: 通知・Discord連携管理」を表示する。

## 4. ゲーム内通知センター

### 4.1 状態ページ

最上部に一つの総合状態カードを置く。

- 正常: Bot接続済み、国家リンク済み、チャンネル送信可能、直近配送成功。
- 要設定: Botは稼働しているが国家未リンク、本人未確認、通知先未設定など。
- 障害: Bot停止、権限不足、チャンネル消失、再試行上限、期限切れイベントあり。

カードには内部IDではなく次を表示する。

- Bot: 稼働中 / 無効 / 接続中 / 認証失敗 / 利用不可
- 個人本人確認: 確認済み / 未確認
- 国家リンク: Discordサーバー名、または未接続
- 通知先: `#channel-name`、または送信不可
- Siegeメンション: `@role-name`、なし、権限不足
- 配送: 待機、再試行、直近成功時刻、直近失敗の簡潔な理由

主操作は状態に応じて一つだけ強調する。

- 未設定なら「連携を開始」
- 設定済みなら「テスト通知を送信」
- 障害中なら「問題を確認」

### 4.2 通知内容ページ

25個のイベントをそのまま25トグルにせず、利用者の判断単位で5カテゴリへまとめる。

| カテゴリ | 対象イベント | 既定重要度 |
| --- | --- | --- |
| 防衛・Siege | Siege開始/終了、コア被害/陥落、反攻、領土喪失/占領 | 緊急 |
| 領土・維持 | 漏出/再封鎖、維持費警告 | 警告 |
| 国民 | 加入申請 | 通常 |
| 復興 | 復興開始、目標、完了、期限終了、宿敵更新 | 通常 |
| 派遣契約 | 作成、発効、完了、取消 | 通常 |

各カテゴリで以下を選べるようにする。

- ゲーム内: 即時 / OFF
- Discord: 即時 / まとめ / OFF
- メンション: なし / 緊急時のみ（メンション可能カテゴリだけ）

最初にプリセットを用意する。

- **重要のみ**: Siege・コア陥落・領土喪失・重大な維持費警告
- **標準（推奨）**: 緊急は即時、国民/復興/派遣はまとめ、回復通知も含む
- **すべて即時**: 全イベントを即時配送
- **カスタム**: 個別変更時に自動表示

座標は全体トグルではなく、防衛・領土カテゴリの詳細設定に置く。ON時には
「Discordを閲覧できる全員へ拠点座標が見える」ことを警告する。

### 4.3 Discord連携ページ

「個人」と「国家」を明確に分ける。

#### 個人本人確認

1. Discordで `/moveearth account link` を実行。
2. 表示されたコードを「個人本人確認」欄へ入力。
3. 成功後、Discord表示名と確認済み状態を表示。

一般メンバーでも利用可能にする。国家通知管理権限を要求しない。

#### 国家通知先

1. Discordでセットアップを開始。
2. Discord側で通知チャンネルを選択。
3. 発行された「国家リンク」コードを専用欄へ入力。
4. Botが送信・埋め込み・メンション権限を検査。
5. 成功後にテスト通知を自動送信し、設定完了画面へ進む。

国家リンク入力欄は `MANAGE_NOTIFICATIONS` 保持者だけ操作可能にする。
個人コードと国家コードは別欄または明示的なモード選択とし、同じボタン列へ置かない。

リンク解除、通知先変更、本人確認解除は結果を説明する確認ダイアログを通す。

### 4.4 配送履歴ページ

一般的な監査ログではなく、利用者向けの配送履歴として表示する。

- 時刻
- 通知名
- 状態: 配送済み / 待機 / 再試行中 / 破棄
- 送信先
- 失敗理由と対処: 権限不足、チャンネル消失、Bot停止、Discord API一時障害など

生のexception名、action名、Discord ID、UUIDはサーバー管理者の詳細表示だけに限定する。
「再送」は冪等性を保証できるイベントだけに用意し、通常は「テスト」「状態更新」を案内する。

## 5. Discord側のセットアップUX

既存slash commandは互換用に残しつつ、通常導線を `/moveearth setup` に集約する。
Botの通常応答は従来方針どおり、原則すべて埋め込みを使用する。

### セットアップウィザード

1. 現在状態をephemeral埋め込みで表示。
2. 「個人本人確認」「国家をリンク」「通知先を変更」「メンションを設定」ボタンを表示。
3. チャンネル/役職はDiscordのselect menuで選択する。
4. 必要なときだけワンタイムコードを発行する。
5. ゲーム内確認を待つ間は、コード、有効期限、次の操作を一画面に表示する。
6. ゲーム内で消費されたら、可能なら元のephemeral応答を「連携完了」へ更新する。
7. 最後に権限診断とテスト通知を実行し、完了/不足権限を明示する。

ワンタイムコード、一回限り、有効期限、試行回数制限という現在の安全設計は維持する。

### コマンド整理

- `/moveearth setup`: 推奨の対話式入口
- `/moveearth status`: 接続・設定・配送ヘルスの読み取り
- `/moveearth test`: 明示的なテスト配送
- `/moveearth audit`: 管理者向け詳細。通常利用者には配送履歴を案内
- 既存 `account` / `nation` サブコマンド: 互換と自動化用途として残す

unlinkは即時実行せず、ephemeral確認ボタンを必須にする。

## 6. 受信通知の再設計

### 6.1 型付き通知モデル

`List<String> externalArguments` を表示層まで持ち回る方式を廃止し、イベントごとの型付きpayloadへ移す。

例:

```text
SiegeStarted(attackerName, defenderName, role, phase, remainingTicks)
CoreDamaged(coreName, currentHp, maximumHp, damageBand)
JoinApplication(playerName)
DispatchCompleted(contractId, partnerNation, earned, reason)
```

保存時は `schemaVersion + eventType + named fields` とし、旧outboxは読み込み時に安全に変換する。
変換不能な旧イベントは「詳細不明の旧形式通知」として送るか、監査へ理由を残して破棄する。

### 6.2 埋め込み内容

共通構造:

- タイトル: 事象を一文で表す
- 説明: 自国が攻撃側/防衛側/依頼側のどれかを含める
- 名前付きfield: 相手国、対象、段階、HP、残り時間、座標など
- footer: カテゴリと重要度。内部通知UUIDは通常非表示
- timestamp: 発生時刻

例:

```text
領土コアが攻撃されています
北部第2コアが被弾しました。防衛状況を確認してください。

耐久値       1,820 / 3,000（61%）
地点         Overworld • 124, 71, -530
Siege        Rusk vs Example • 防衛側

防衛・Siege • 緊急
```

内部enumやreason codeは日本語/英語の表示辞書を通す。未知値は安全な一般表現へfallbackする。

### 6.3 ノイズ抑制と事案集約

- `CORE_DAMAGED`: 毎回ではなくHP帯（75/50/25/10%）を下回ったときに通知。
- 同一Siege/コアの反復イベントはincident IDでまとめる。
- 漏出→再封鎖は同一事案として回復通知を紐付ける。
- 緊急イベントは即時、通常イベントは設定に応じて15〜30分のdigestへ集約。
- digestは「加入申請3件」「復興目標2件」のように要約し、全件をfieldへ詰め込まない。
- メンションには国家・カテゴリ単位のcooldownを設ける。コア被弾ごとの連続メンションを禁止する。
- `SIEGE_STARTED`、`CORE_FALLEN`、`TERRITORY_LOST` は重複抑止で消えない必達イベントとして扱う。

現在の同一国家・種別・座標による15分重複抑止は、新しいincident/digest方式へ移行するまで維持する。

## 7. 状態・通信モデルの変更

### 7.1 クライアント向けsnapshot

現行packetの真偽値列を、バージョン付きの通知センターsnapshotへ置き換える。

- revision / schemaVersion
- manage権限
- Bot状態と安全な診断コード
- 個人本人確認状態
- 国家リンク状態
- Discordサーバー/チャンネル/役職の表示名
- カテゴリ別設定と選択中プリセット
- pending / retrying / dropped / expired
- lastSuccessAt / lastFailureAt / lastFailureReason
- 権限に応じた配送履歴

Discordの生IDは操作packet内部では使用しても、通常snapshotには表示名だけを載せる。

### 7.2 永続データ

`Settings` をバージョン付き構造へ拡張する。

- categoryごとのin-game mode
- categoryごとのDiscord mode
- categoryごとのmention policy
- coordinate policy
- digest interval
- mention cooldown

既存4値からの移行:

- `inGame=true`: 全カテゴリをゲーム内即時
- `discord=true`: 全カテゴリをDiscord即時。ただし初回画面で標準プリセットを提案
- `includeCoordinates`: 防衛・領土カテゴリの座標設定へ移す
- `mentionOnSiege`: 防衛・Siegeカテゴリの緊急メンションへ移す

移行は一度だけ行い、旧キーを読める期間を設ける。設定を黙って変更しない。

### 7.3 操作要求

設定保存、リンク、解除、テストには独立したrequest IDと処理状態を持たせる。

- 連打防止
- 10秒程度のクライアント側タイムアウト
- タイムアウト時は結果不明として自動再送しない
- 最新snapshotを再取得して結果を確認する
- stale時は入力内容を失わず、差分を示して再適用を選べる

## 8. サーバー運営UX

### 起動時診断

トークン値を一切出さず、起動ログを一つの要約へまとめる。

- Bot無効
- token未設定
- Discord接続中/接続済み
- command登録成功/失敗
- JDAなしのplayer buildを誤配置

### Minecraft側管理診断

BotがDiscordへ接続できない場合にも使える、サーバー管理者向け状態確認を追加する。

- Bot lifecycle状態
- 最終接続/切断理由
- 接続国家数
- outbox総数、再試行、破棄、期限切れ
- 最古の待機時間
- チャンネル/権限不良の国家数
- 設定再読込または安全な再接続

トークンの設定・表示・コピーはゲーム内から行わない。ローテーションは引き続きサーバーconfigと
Discord Developer Portalで行う。

## 9. コード構成案

```text
s2/notification/
  NotificationCategory
  NotificationSeverity
  NotificationPreference
  NotificationPayload（sealed interface）
  NotificationPresentation
  NotificationIncidentPolicy
  NotificationDigestService

client/notification/
  NotificationCenterScreen
  NotificationCenterNavigation
  NotificationCenterLayout
  NotificationCenterViewModel
  pages/StatusPage, PreferencesPage, DiscordLinkPage, DeliveryHistoryPage

s2/notification/discord/
  DiscordSetupFlow
  DiscordComponentListener
  DiscordNotificationRenderer
  DiscordHealthView
```

`MoveEarthDiscordEmbeds` は色と共通frameだけを担当し、イベント解釈を分離する。
ゲーム内ComponentとDiscord文字列の生成元を可能な範囲で共通の表示モデルへ寄せる。

## 10. 実装順序

### 第1段階: 導線と明確な不具合の修正

1. 一般メンバーの本人確認をGUIから可能にする。
2. S2 Hubへ権限対応の「通知」入口を追加する。
3. 個人本人確認と国家リンクを別ページ/別入力欄へ分ける。
4. 状態カード、処理中、タイムアウト、変更あり、破棄確認を追加する。
5. 現行設定・packet・保存形式は維持し、回帰リスクを抑える。

### 第2段階: 接続状態とセットアップ

1. クライアントsnapshotへBot、個人、国家、チャンネル、役職、配送ヘルスを追加する。
2. ゲーム内セットアップウィザードを実装する。
3. Discordへ `/moveearth setup` とボタン/select menuを追加する。
4. unlink確認、権限診断、自動テスト配送を追加する。
5. 運用ドキュメントを新導線へ更新する。

### 第3段階: 通知内容の改善

1. 型付きpayloadと表示辞書を導入する。
2. 全イベントを名前付きfieldの埋め込みへ移行する。
3. 内部UUID、enum、`Detail N`、reason codeの直接表示を廃止する。
4. 旧outboxの読み込み移行と未知値fallbackを実装する。
5. 日英表現、長い国家名、メンション無害化をテストする。

### 第4段階: 通知疲れ対策

1. 5カテゴリとプリセットを実装する。
2. カテゴリ別のゲーム内/Discord/メンション設定へ移行する。
3. コアHP閾値、incident集約、digest、メンションcooldownを実装する。
4. 必達イベントが重複抑止で欠落しないことを保証する。

### 第5段階: 診断と仕上げ

1. ゲーム内配送履歴と対処案を追加する。
2. Minecraft管理者向けBot診断を追加する。
3. 狭幅・低画面、読み上げ、キーボード操作、チャット非重畳を確認する。
4. server/player両buildでJDA隔離と起動を検証する。
5. 実Discordで権限不足、チャンネル削除、役職削除、Bot再起動、API失敗を通す。

## 11. テスト計画

### 単体テスト

- 旧設定からカテゴリ設定への移行
- プリセットとカスタム判定
- イベント→表示モデル→埋め込みfieldの対応
- HP閾値、incident key、digest、mention cooldown
- 必達/集約可能イベントの分類
- link codeの用途分離、有効期限、一回性、試行制限
- 権限別snapshotの情報削減
- レイアウト内包、クリック領域、ページ別状態保持
- タイムアウトとstale応答

### 統合テスト

- 一般メンバーの本人確認
- 通知管理者の国家リンクと設定
- 権限喪失・脱退後のDiscord操作拒否
- Discord配送成功/429/権限不足/チャンネル消失/再試行上限
- サーバー再起動を跨ぐoutbox、digest、incident復元
- 旧world dataと旧outboxの移行
- server buildにJDAあり、player buildにJDAなし

### 実機確認

- GUIスケールと320×180以上の各解像度
- 日本語/英語、長い国家・チャンネル・役職名
- Discordデスクトップ/モバイルの埋め込み表示
- setup開始からゲーム内確認、テスト通知まで迷わず完了できるか
- 緊急イベント連続発生時の通知量とメンション量

## 12. 完了条件

- 一般メンバーが通知管理権限なしで本人確認できる。
- 通知管理権限保持者が所有者でなくてもS2 Hubから管理画面へ到達できる。
- 未連携状態から画面内の案内だけで国家リンクとテスト配送を完了できる。
- 国家リンク、個人本人確認、チャンネル、役職、配送障害を別状態として識別できる。
- 受信通知に `Detail N`、内部UUID、生enum/reason codeが表示されない。
- 緊急イベントは即時、低優先度/反復イベントは設定に従って集約される。
- 破壊的操作には確認があり、処理中・成功・失敗・タイムアウトが区別される。
- Bot停止中でもMinecraft側から原因と対処を確認できる。
- 既存world data、旧リンク、outboxを失わず移行できる。
- player buildへJDA依存が混入せず、server buildの起動・停止・配送が成功する。

## 13. 非目標

- Botトークンをゲーム内GUIで編集または表示する。
- Discordを国家データの正本にする。
- Discord権限だけでゲーム内国家権限を上書きする。
- 通知失敗時にサーバースレッドを待機させる。
- 初期段階から全イベントを自由記述テンプレート化する。
