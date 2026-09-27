# MoveEarth Discord Bot 運用手順

Botは専用サーバー起動時に `config/moveearth_addtional-discord.toml` を自動生成します。`enabled = true` と
`botToken` を設定し、サーバーを再起動してください。このファイルはリポジトリへコミットせず、OS上では
サーバープロセスの実行ユーザーだけが読める権限にします。

## 初期連携

通常はDiscordで `/moveearth setup` を実行し、ephemeralのセットアップ画面に従います。

1. 「個人本人確認」で発行したコードを、ゲーム内の国家Hub → 通知 → Discord連携 → 個人本人確認へ入力する。
   この操作は一般メンバーでも利用でき、国家管理権限を要求しない。
2. Discordの「サーバー管理」を持つ担当者が「国家をリンク」で発行したコードを、ゲーム内の国家通知先へ
   入力する。ゲーム内では `MANAGE_NOTIFICATIONS` が必要。
3. Discordのチャンネル/役職選択メニューで通知先と緊急メンションを設定する。
4. ゲーム内の状態ページ、または `/moveearth test` からテスト配送を行う。国家リンク直後にも自動テストが入る。

既存の `/moveearth account ...` と `/moveearth nation ...` は互換・自動化用途として残ります。
解除操作は `/moveearth setup` の確認ボタン、またはゲーム内の確認ダイアログを経由します。

Discord側の管理操作は毎回、本人確認済みUUIDが現在も対象国家に所属し、ゲーム内で
`MANAGE_NOTIFICATIONS` を持つか再検証します。脱退・追放・役職変更は次の操作から即時反映されます。
BotがDiscordサーバーから削除された場合や通知チャンネルが削除された場合は国家連携を自動解除し、
メンション役職だけが削除された場合は役職設定だけを解除します。

## トークンのローテーション

1. Minecraftサーバーを停止する。
2. Discord Developer Portalで新しいBotトークンを発行し、旧トークンを無効化する。
3. `moveearth_addtional-discord.toml` の `botToken` だけを新しい値へ置換する。
4. 設定ファイルの所有者・読み取り権限を再確認してサーバーを起動する。
5. `/moveearth status` と `/moveearth test` で接続・配送を確認する。

トークンはログ、コマンド、ゲームクライアント、監査データへ保存・表示しません。漏えいが疑われる場合は、
確認を待たず同じ手順で直ちに無効化してください。

## 配送と障害確認

通知はworld SavedDataのoutboxへ先に保存され、成功応答後に削除されます。失敗時は指数バックオフで最大8回
再試行し、標準では72時間を超えた通知を破棄します。同じ国家・種別・座標のイベントは標準15分間重複を
抑止します。保持時間と重複窓はDiscord configで変更できます。

ゲーム内の配送履歴は、配送済み・再試行・破棄と、人が読める復旧案だけを表示します。内部action名、UUID、
Discord IDは表示しません。管理者向けの `/moveearth audit` は連携変更、権限拒否、配送失敗、再試行上限到達を
含む直近の監査記録を表示します。MinecraftコンソールまたはOPは `/moveearth discord status` でBot lifecycle、
接続国家数、outbox統計を確認でき、`/moveearth discord reconnect` で安全な再接続を要求できます。

通常イベントは国家設定により15分または30分のまとめ通知へ集約できます。コア被害は75/50/25/10%のHP帯を
初めて下回った時だけ通知し、緊急メンションには国家・カテゴリ単位のcooldownを適用します。
`SIEGE_STARTED`、`CORE_FALLEN`、`TERRITORY_LOST` は必達扱いで通常の重複抑止から除外されます。
