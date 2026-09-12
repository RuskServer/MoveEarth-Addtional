# MoveEarth Discord Bot 運用手順

Botは専用サーバー起動時に `config/moveearth_addtional-discord.toml` を自動生成します。`enabled = true` と
`botToken` を設定し、サーバーを再起動してください。このファイルはリポジトリへコミットせず、OS上では
サーバープロセスの実行ユーザーだけが読める権限にします。

## 初期連携

1. Discordで `/moveearth nation link` を実行する（Discordの「サーバー管理」が必要）。
2. ゲーム内の国家通知GUIでコードを「国家へリンク」する（国家の通知管理権限が必要）。この操作で実行者の
   Minecraft UUIDとDiscord IDも一対一で本人確認される。
3. 他の担当者は `/moveearth account link` のコードをゲーム内GUIの「本人確認」で入力する。
4. `/moveearth nation channel`、`mention`、`settings` で通知先を調整し、`/moveearth test` で確認する。

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

`/moveearth status` は待機数、成功数、失敗試行数、上限破棄数を表示します。
`/moveearth audit` は連携変更、権限拒否、配送失敗、再試行上限到達を含む直近の監査記録を表示します。
