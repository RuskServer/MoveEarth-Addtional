# README.md 更新計画

更新日: 2026-09-19
対象: リポジトリ直下の `README.md`
状態: 計画。README本文は未変更。

## 1. 更新目的

現行READMEは初期機能を中心に書かれており、現在のMoveEarthを正しく案内できていない。
READMEを次の3者が迷わず使える入口へ作り直す。

1. **プレイヤー**: このMODで何が変わるか、どのJARを使うかが分かる。
2. **サーバー管理者**: 必須・任意依存、server JAR、設定ファイル、導入上の注意が分かる。
3. **開発者**: Java/Gradle環境、ローカル依存、ビルド・テスト・成果物が分かる。

READMEには現行の安定した概要だけを載せる。詳細仕様、全コマンド、全config項目、変更履歴、
未完成計画は専用文書へリンクし、README自体をリリースごとに全面改稿しなくて済む構成にする。

## 2. 現行READMEで直す内容

| 現在の記述 | 問題 | 更新方針 |
|---|---|---|
| 主機能がPvP、Anti-ESP、分析中心 | 国家・領土・補強・Siege・捕虜・車両・導線が欠落 | 現在の中核機能を5群程度に再編 |
| 必須GunPack不足画面が出る | changelogでは起動時チェックを廃止済み | GunPack導入画面の説明を削除。TaCZ自体の依存は残す |
| `develop/territory-v2`は停止中 | 国家・領土システムはmainで大規模実装済み | 古いブランチ案内を削除し、main/PR方針だけ記載 |
| 配布JARを単数で説明 | player/serverの2成果物を生成する | 用途と同時更新条件を表で説明 |
| 自動テストがない | 現在は104テストクラスが存在 | テスト、assemble、既知の8080番ポート競合を記載 |
| `lib/`一覧が古い | Sable、各種API、Maven依存との区別がない | 必須ランタイム・任意連携・ローカルcompile依存を分離 |
| Farmer's Delightだけ任意依存として説明 | Jade、JEI、Cold Sweat、Curios、CBC等の任意連携がある | `neoforge.mods.toml`と実装を基準に表へ整理 |
| `clean build`だけ案内 | リポジトリ内JDK/Gradleキャッシュ用スクリプトがある | 標準コマンドを`tools/gradle-local.sh`へ更新 |
| 地形生成の導入説明がない | server JARにのみworldgenデータを含み、タイルが必要 | 実験/運用中であること、詳細文書、既存ワールド注意を簡潔に記載 |
| ライセンス説明が概略のみ | JDA、SQLite、Discord RPC、音声等の個別条件がある | 本文は概要に留め、`THIRD_PARTY_NOTICES.md`へ明確に誘導 |

GunPack関連クラスがソースに残っていても、現在の画面差し替え経路から呼ばれない限り
READMEでは有効機能として紹介しない。実装の残骸と利用者向け仕様を区別する。

## 3. 新しいREADME構成

### 3.1 タイトルと冒頭

- 既存のGPL / Minecraft / NeoForgeバッジを維持する。
- 「RuskServer専用の寄せ集め機能」ではなく、国家運営・領土戦・移動体・サーバー導線を
  一つに統合するNeoForge MODであることを2～3文で説明する。
- 対象バージョンを明記する。
  - Minecraft 1.21.1
  - NeoForge 21.1.238
  - Java 21
  - MOD version 3.1.0（`gradle.properties`を正とする）
- `Addtional`という既存のプロジェクト名・mod IDは互換性のためそのまま使う。

### 3.2 主な機能

変更履歴の箇条書きをコピーせず、次の単位に圧縮する。

1. **国家・領土**
   - 国家設立、役職、外交、領土コア、維持費、地図、初回参加申請。
2. **補強・Siege・捕虜**
   - 溶接、段階的HP、閉鎖判定、CBC/Warnautics連携、陥落、復興、捕虜護送、Combat Timer。
3. **車両・Create連携**
   - 車両コア、Sable移動体、Aeronautics/Simulated系互換、安全対策。
4. **プレイヤー体験**
   - 独自メニュー、ローディング画面、ガイド、Tips、Cold Sweat HUD、Jade/JEI連携。
5. **サーバー運用**
   - Discord Bot、分析、開放時間、ランダムスポーン、TPA、通知、パフォーマンス対策。

各群は2～4行に留め、細部は以下へ誘導する。

- 現在の変更: `changelog.md`
- 国家・領土・Siege仕様: `s2_system_plan.md`
- 捕虜UI: `prisoner_experience_plan.md`
- 復興・派遣: `recovery_dispatch_plan.md`
- 地形生成: `terrain_generation_plan.md`
- 地方別資源: `resource_region_plan.md`

計画書には未実装項目も含まれるため、リンク文言で「実装済み機能一覧」と誤認させない。

### 3.3 配布物の選び方

| 成果物 | 配置先 | 内容 |
|---|---|---|
| `moveearth_addtional-<version>-player.jar` | 各プレイヤーの`mods/` | JDAを除外。サーバー専用worldgenデータも除外 |
| `moveearth_addtional-<version>-server.jar` | 専用サーバーの`mods/` | 分離済みJDA runtimeとサーバーworldgenデータを含む |

- client/serverは同じバージョンへ同時更新する。
- server JARをクライアント配布物として案内しない。
- player JARを専用サーバーの正式成果物として案内しない。
- JARを自分で改名・結合しない。
- 生成物をGitへコミットしない。

### 3.4 必要環境と依存MOD

`src/main/templates/META-INF/neoforge.mods.toml`を正として、少なくとも以下を記載する。

**必須ランタイム**

- Minecraft 1.21.1
- NeoForge 21+
- TaCZ 1.1.8+
- Create 6.0.10+
- Sable 2.0.3+
- Create Aeronautics 1.3.0+

**任意連携**

- TaCZ Tweaks
- Farmer's Delight
- Curios
- Jade
- JEI（client）
- Cold Sweat 2.4+（client）
- Create Big Cannons / Create Warnautics（導入時のみ補強・爆発連携）

Lightman's Currencyは現在compile APIとして残り、国家金庫・維持費に関係するため、
完全撤去または正式必須化の結論が出るまで「経済機能で使用中」と注記する。
`discord-rpc`はクライアント用クラスをビルド時に取り込むローカル依存であり、
サーバー管理者が別MODとして入れるものではないことを明記する。

ローカル`lib/`の具体的ファイル名は実際のビルドが要求するものから再生成し、推測で増やさない。
Mavenから取得するcompileOnly APIと`lib/`手動配置を同じ一覧に混ぜない。

### 3.5 サーバー導入と設定

- server JARと必須依存を`mods/`へ配置する最小手順。
- 初回起動でconfigが生成されることを説明する。
- 主なconfigファイルだけ用途別に表へまとめる。
  - `moveearth_addtional-s2-territory.toml`
  - `moveearth_addtional-tpa.toml`
  - `moveearth_addtional-discord.toml`
  - `moveearth_addtional-dcc.toml`
  - `moveearth_addtional-aeronautics.toml`
  - `moveearth_addtional-tips.toml`
  - `moveearth_addtional-recovery-dispatch.toml`
- Discord tokenをREADME例へ直書きしない。環境ごとのconfigへ設定し、公開ログやIssueへ貼らない。
- server JARに地形生成データがあることと、専用タイルが必要な構成では
  `terrain_generation_plan.md` / `tools/terrain/README.md`へ従うことを記載する。
- 地形生成を既存ワールドへ途中導入すると新旧チャンク境界が生じ得るため、新規テストワールドを推奨する。

configの全キーはREADMEへ複製しない。既定値が変わるたびにREADMEが陳腐化するため、
用途、生成場所、特に危険な設定だけを扱う。

### 3.6 プレイヤー導入

- player JARと必須のclient側依存を`mods/`へ配置する。
- serverと同じMoveEarthバージョンが必要。
- 初回起動時に音量・アクセシビリティ設定画面が出ることを説明する。
- 旧GunPack強制導入画面の説明は削除する。
- 任意のJade / JEI / Cold Sweatがある場合に追加表示が有効になることを短く記載する。

### 3.7 開発環境・ビルド・検証

前提:

- Java 21
- Git
- 初回依存取得時のみネットワーク
- `lib/`へ必要な再配布不可JARを利用者自身が配置

推奨コマンド:

```shell
tools/gradle-local.sh test --offline
tools/gradle-local.sh assemble --offline
tools/gradle-local.sh buildPlayerJar --offline
tools/gradle-local.sh buildServerJar --offline
```

成果物名と役割を続けて説明する。通常の`./gradlew`も利用可能だが、
このリポジトリでは`.local-tools/jdk-21`と`.local-tools/gradle-home`を使うスクリプトを標準とする。

「自動テストなし」は削除し、テストが存在すること、変更範囲の単体テストとassembleを実行することを記載する。
`AnalyticsWebServerTest`がcode-serverの`127.0.0.1:8080`使用時に環境由来で失敗する既知事項も、
開発者向けの注記として短く残す。

### 3.8 貢献、セキュリティ、ライセンス

- PR前に変更範囲のテスト、`assemble --offline`、`git diff --check`を要求する。
- server-authoritative、日英翻訳、外部コードの出典記録という既存方針を維持する。
- 秘密情報、生成JAR、`lib/`、ワールドデータ、ログをコミットしない。
- コミット作者名など個人環境専用のAGENTSルールは公開READMEへ載せない。
- GPL-3.0-onlyと第三者コンポーネントの扱いは現行説明を維持し、
  `THIRD_PARTY_NOTICES.md`と`LICENSES/`へ誘導する。
- README更新時に、GunPack説明が残る`THIRD_PARTY_NOTICES.md`との不一致を別課題として記録する。
  READMEだけを直して第三者帰属を不用意に削除しない。

## 4. 実施順序

1. `gradle.properties`、`neoforge.mods.toml`、`build.gradle`からバージョン・依存・成果物を抽出する。
2. `changelog.md`と主要パッケージから、現在実装済みの機能だけを5群へ分類する。
3. READMEを新構成へ全面更新する。既存の有効なライセンス・貢献方針は残す。
4. README内の相対リンク、コマンド、成果物名を検査する。
5. `buildPlayerJar`と`buildServerJar`を実行し、説明した成果物が実際に生成されることを確認する。
6. `test`と`assemble --offline`を実行する。8080番ポート競合はAGENTSルールに従って判定する。
7. READMEだけの差分を読み直し、未実装機能を現在機能として書いていないか確認する。

## 5. README更新時に確定すべき事項

実装前にコードから確定できないものだけをユーザー判断へ回す。

- READMEの主要言語を日本語のみとするか、冒頭に短い英語説明を併記するか。
- 公開READMEへMoveEarthのDiscord招待リンクを掲載するか。
- Lightman's Currencyを正式依存として残すか、将来撤去予定と明示するか。
- サーバーアドレスをREADMEへ掲載するか。コード内の値を無断で公開情報扱いしない。
- スクリーンショットを今回追加するか。追加する場合は現在のUIを実機撮影し、古い画像を使わない。

判断待ちでも、依存・ビルド・機能説明・古い記述削除までは先に実施できる。

## 6. 完了条件

- README内のMinecraft、NeoForge、Java、MODバージョンがビルド設定と一致する。
- 必須/任意/ビルド専用依存の区別が`neoforge.mods.toml`と`build.gradle`に反しない。
- player/server JARの用途と、同一バージョン必須が明記されている。
- GunPack強制画面、自動テストなし、停止中領土ブランチなどの古い説明が残っていない。
- 国家、領土、補強、Siege、捕虜、車両、Discord、導線の主要機能が簡潔に見つかる。
- config、地形生成、変更履歴、仕様書、ライセンスへの相対リンクがすべて存在する。
- README掲載コマンドでテストと両配布物のビルドが成功する。
- `git diff --check`が成功し、生成JARやローカル依存を変更対象へ含めていない。
- READMEの分量は機能仕様書の代替にならない範囲に保つ。目安は現在と同程度から1.5倍以内。
