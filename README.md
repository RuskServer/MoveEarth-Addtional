# MoveEarth-Addtional

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.238-E6A756)
![Java](https://img.shields.io/badge/Java-21-ED8B00)

MoveEarth-Addtionalは、RuskServerの国家運営、領土戦、補強、移動体、プレイヤー導線を
一つにまとめるMinecraft 1.21.1用NeoForge MODです。複数のCreate系MODやTaCZ、
PlayerReviveなどを、MoveEarth独自のゲーム進行へ統合します。

> [!NOTE]
> プロジェクト名とMOD IDの`Addtional`という綴りは、既存環境との互換性維持のため変更していません。

現在の宣言バージョンは`3.1.0`です。`main`には次期バージョン向けの開発中変更も含まれます。
変更内容は[変更履歴](changelog.md)を参照してください。

## 主な機能

- **国家・領土** — 国家設立、役職と権限、外交、領土コア、維持費、国家金庫、領土地図、初回参加申請。
- **補強・Siege・捕虜** — 溶接機による段階的な補強、閉鎖判定、攻城戦、陥落と復興、ダウンしたプレイヤーの護送・収監、Combat Timer。
- **車両・兵器連携** — 車両コアと移動体補強、Sable、Create Aeronautics、Create Big Cannons、Warnauticsとの連携、移動体の安全対策。
- **プレイヤー体験** — 独自メインメニューとローディング画面、初心者ガイド、定期Tips、Cold Sweat HUD、Jade・JEI連携。
- **サーバー運用** — Discord Bot、プレイヤー行動分析、開放時間、ランダムスポーン、TPA、投票報酬、Anti-ESPと負荷対策。

仕様書には開発中・検討中の内容も含まれます。現在利用できる機能との差分は
[変更履歴](changelog.md)と実装を基準にしてください。

- [国家・領土・Siege計画](s2_system_plan.md)
- [捕虜体験改善計画](prisoner_experience_plan.md)
- [復興・派遣計画](recovery_dispatch_plan.md)
- [地形生成計画](terrain_generation_plan.md)
- [地方別資源計画](resource_region_plan.md)

## 動作環境

| 項目 | バージョン |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.238（依存定義は21以上） |
| Java | 21 |
| MoveEarth-Addtional | 3.1.0 |

### 必須MOD

クライアントとサーバーの両方で、同じMoveEarthバージョンと次の依存MODを使用してください。

| MOD | 対応バージョン |
|---|---|
| TaCZ | 1.1.8以上 |
| Create | 6.0.10以上 |
| Sable | 2.0.3以上 |
| Create Aeronautics | 1.3.0以上 |

現行の国家金庫、維持費、検知ブロックの料金、投票報酬などの経済機能では
**Lightman's Currencyを使用しています**。今後も正式依存として残すか、別方式へ置き換えるかは
検討中ですが、現在の経済機能を使うサーバーでは導入してください。

### 任意連携

| MOD | 追加される連携 |
|---|---|
| TaCZ Tweaks | TaCZ関連の互換処理 |
| Farmer's Delight 1.3.3以上 | 農業・収穫処理 |
| Curios 9.5.1以上 | 装備スロット連携 |
| Jade 15.1.0以上 | 補強状態やHPなどの表示 |
| JEI 19.21.1.248以上 | ガイド・アイテム閲覧連携（クライアント） |
| Cold Sweat 2.4以上 | 温度警告HUD（クライアント） |
| PlayerRevive | ダウン、護送、捕虜体験の連携 |
| LocalizedChat | チャット連携 |
| Create Big Cannons | 砲撃と補強ダメージの連携 |
| Create Warnautics | 爆発物・航空兵器と補強の連携 |

## 導入

### 配布物の選び方

| 成果物 | 配置先 | 内容 |
|---|---|---|
| `moveearth_addtional-<version>-player.jar` | 各プレイヤーの`mods/` | Discord Bot実装とサーバー専用地形データを除いたクライアント向けJAR |
| `moveearth_addtional-<version>-server.jar` | 専用サーバーの`mods/` | Discord Botランタイムとサーバー用地形データを含むJAR |

player/server JARは同じバージョンへ同時に更新してください。JARの結合や内容の入れ替えは
サポート対象外です。Discord Bot用のJDAとクライアント用Discord RPCは成果物側で処理されるため、
サーバー管理者やプレイヤーがそれらを別MODとして追加する必要はありません。

### プレイヤー

1. Java 21対応のMinecraft 1.21.1 / NeoForge環境を用意します。
2. player JARと必須MODを`mods/`へ配置します。
3. サーバー側とMoveEarthおよび依存MODのバージョンを合わせます。
4. 初回起動時の音量・アクセシビリティ設定を確認します。

Jade、JEI、Cold Sweatを導入している場合は、それぞれ補強情報、ガイド、温度警告の追加表示が
有効になります。旧版に存在したGunPack強制導入画面は現在使用していません。

### サーバー

1. server JARと必須MODを専用サーバーの`mods/`へ配置します。
2. 現行の経済機能を使う場合はLightman's Currencyを導入します。
3. 地形タイルをワールドの`moveearth_terrain/`、または`config/moveearth_terrain/`へ配置します。
4. サーバーを起動し、自動生成された設定を確認してから再起動します。

> [!IMPORTANT]
> server JARは起動時にMoveEarth地形タイルを読み込みます。有効なタイルが1件もない場合は
> 起動を中止するため、事前に[地形ツールの手順](tools/terrain/README.md)を確認してください。
> 地形生成を既存ワールドへ途中導入すると新旧チャンクの境界が生じるため、新規テストワールドでの
> 事前確認を推奨します。

Discord Botのトークンはサーバー側設定へ記入し、ログ、Issue、コミットへ含めないでください。
Botの導入と運用は[Discord Bot運用ガイド](DISCORD_BOT_OPERATIONS.md)を参照してください。

MoveEarthコミュニティへの参加: [Discord](https://discord.gg/QNquTTTdZh)

## 主な設定ファイル

設定ファイルは初回起動時に自動生成されます。全項目と既定値は生成されたファイルを基準にしてください。

| ファイル | 用途 |
|---|---|
| `moveearth_addtional-s2-territory.toml` | 国家、領土、補強、Siege、維持費 |
| `moveearth_addtional-tpa.toml` | TPAと利用制限 |
| `moveearth_addtional-discord.toml` | Discord Bot、トークン、チャンネル連携 |
| `moveearth_addtional-dcc.toml` | 開放時間などのサーバー制御 |
| `moveearth_addtional-aeronautics.toml` | 移動体・Aeronautics連携 |
| `moveearth_addtional-tips.toml` | 定期Tips |
| `moveearth_addtional-recovery-dispatch.toml` | 復興・派遣システム |
| `moveearth_addtional-startup.toml` | 初回起動とクライアント表示 |

## 開発環境

必要な環境はJava 21、Git、初回の依存取得が可能なGradle環境です。

```shell
git clone https://github.com/RuskServer/MoveEarth-Addtional.git
cd MoveEarth-Addtional
```

### ローカル依存JAR

再配布条件やファイルサイズの都合により、`lib/`はGit管理外です。各MODを正規の配布元から入手し、
プロジェクト直下の`lib/`へ配置してください。現在の開発環境で使用するファイルは次のとおりです。

```text
lib/
├─ FarmersDelight-1.21.1-1.3.3.jar
├─ LocalizedChat-neoforge-1.21.1-5.2.1.jar
├─ PlayerRevive_NEOFORGE_v2.1.2_mc1.21.1.jar
├─ create-1.21.1-6.0.10.jar
├─ create-aeronautics-bundled-1.21.1-1.3.2.jar
├─ discord-rpc-1.0.4.jar
└─ tacz-neoforge-1.21.1-1.1.8-hotfix-r6.jar
```

Sable、Jade、JEI、Cold Sweat、Curios、Lightman's Currencyなどの開発用APIはGradleが取得します。
`discord-rpc`はクライアント機能をコンパイルするためのローカルライブラリであり、単体の導入MODでは
ありません。依存JARをコミットしたりPull Requestへ添付したりしないでください。

### ビルドとテスト

このリポジトリでは、`.local-tools/jdk-21`と`.local-tools/gradle-home`を利用するスクリプトを
標準手順としています。

```shell
tools/gradle-local.sh test --offline
tools/gradle-local.sh assemble --offline
```

個別の成果物だけを作る場合は次を使用します。

```shell
tools/gradle-local.sh buildPlayerJar --offline
tools/gradle-local.sh buildServerJar --offline
```

成果物は`build/libs/`へ生成されます。自動テストには国家・領土、補強、Jade、UI、地形生成、
サーバー機能などの単体テストが含まれます。

code-serverが`127.0.0.1:8080`を使用している環境では、`AnalyticsWebServerTest`がテスト用HTTP
サーバーを開始できず、環境由来で失敗する場合があります。既存サービスを停止せず、ポート占有を
確認したうえで、対象テストと`assemble`を別途検証してください。

## 貢献方法

1. このリポジトリをForkします。
2. 最新の`main`から作業ブランチを作成します。
3. 変更範囲に対応するテストと`tools/gradle-local.sh assemble --offline`を実行します。
4. `git diff --check`を通し、必要なゲーム内確認を行います。
5. 変更理由、確認方法、影響するクライアント・サーバー範囲を記載してPull Requestを作成します。

実装時は次の方針を守ってください。

- ゲーム進行や権限判定はサーバー側を正とし、クライアントからの値を信用しない。
- プレイヤー向けメッセージを追加する場合は、原則として日本語と英語を用意する。
- 生成JAR、`lib/`、ワールドデータ、ログ、秘密情報をコミットしない。
- 外部コードや素材を取り込む場合は、出典、著作者、ライセンス、変更内容を記録する。

不具合報告や大きな仕様提案は、実装を始める前にIssueを作成してください。

## ライセンス

このプロジェクトのオリジナルコードと素材は
[GNU General Public License version 3 only](LICENSE)で提供されます。
Pull Requestを提出することで、提出者は自身の貢献部分を`GPL-3.0-only`で
提供することに同意するものとします。

一部のコード、ライブラリ、音声、画像には別のライセンスが適用されます。詳細は
[第三者ライセンスと帰属表示](THIRD_PARTY_NOTICES.md)と[個別ライセンス](LICENSES/)を参照してください。
