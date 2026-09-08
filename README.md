# MoveEarth-Addtional

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.238-E6A756)

MoveEarth-Addtionalは、RuskServer向けにゲーム進行、PvP、プレイヤー保護、
テレポート、他MODとの互換機能を追加するMinecraft 1.21.1用NeoForge MODです。

現在の主な機能は次のとおりです。

- TaCZ標準の現代銃を使用したプリセット式PvPとチーム識別、戦績表示、勝敗演出
- 必須TaCZ GunPackの不足検知と、公式配布ページからのドラッグ＆ドロップ導入画面
- サブチャンク透過グラフ（VisGraph）を用いた視界外エンティティ（ドロップアイテム等）パケット制御（Anti-ESP・通信削減）
- Webダッシュボード（2D空間ヒートマップ）付きプレイヤー行動分析・アクティビティ集約システム
- プレイ時間8時間未満のプレイヤーを対象とする初心者装備
- プレイヤー検知ブロックと連携した、開放日ごとに回数制限のあるTPA
- 投票報酬、管理通知、ランダムスポーンなどのサーバー運営機能
- Stonecutter、LocalizedChat、PlayerRevive、Farmer's Delightなどとの互換処理

詳しい変更内容は[変更履歴](changelog.md)を参照してください。

## プロジェクトの状態

- `main`: 現行の本線です。通常の修正・機能追加はこのブランチを基準にします。
- `develop/territory-v2`: 停止中の新領土システム試作です。現時点では`main`に含まれません。
- `feat/<topic>`: 機能追加用ブランチです。
- `fix/<topic>`: 不具合修正用ブランチです。

`main`への変更はPull Request経由で受け付けます。リポジトリ管理者の
`RuskLabo`のみ、緊急対応やリリース作業のため直接pushできます。

## 開発環境

必要な環境は次のとおりです。

- JDK 21
- Git
- インターネットへ接続できるGradle環境
- Minecraft 1.21.1 / NeoForge 21.1.238

リポジトリを取得します。

```shell
git clone https://github.com/RuskServer/MoveEarth-Addtional.git
cd MoveEarth-Addtional
```

### ローカル依存MOD

再配布条件やファイルサイズの都合により、`lib/`はGit管理外です。各MODを
正規の配布元から入手し、プロジェクト直下の`lib/`へ配置してください。

現在の開発環境で使用しているファイルは次のとおりです。

```text
lib/
├─ create-1.21.1-6.0.10.jar
├─ create-aeronautics-bundled-1.21.1-1.3.0.jar
├─ createdieselgenerators-1.21.1-1.3.15.jar
├─ discord-rpc-1.0.4.jar
├─ FarmersDelight-1.21.1-1.3.3.jar
├─ LocalizedChat-neoforge-1.21.1-5.2.1.jar
├─ PlayerRevive_NEOFORGE_v2.1.2_mc1.21.1.jar
└─ tacz-neoforge-1.21.1-1.1.8-hotfix-r3.jar
```

依存JARをコミットしたり、Pull Requestへ添付したりしないでください。

Farmer's Delightは任意依存です。導入時は農家ジョブがキャベツ、タマネギ、トマト、稲穂の成熟収穫に対応します。

### TaCZ GunPack

GunPack本体はこのMODおよびリポジトリへ同梱しません。クライアントでは不足しているパックがあるとメインメニューを開く前に案内が表示され、必要な公式配布ページを開けます。ダウンロードしたZIPを案内画面へドラッグ＆ドロップすると、内容を検証し、元のファイル名を変えずにMinecraftの`tacz/`へコピーします。反映を確実にするため、導入後にMinecraftを再起動してください。

専用サーバーにはこのクライアント画面が表示されません。サーバー管理者が同じ3つのZIPをサーバーの`tacz/`へ別途配置し、再起動してください。

### ビルド

Windows:

```powershell
.\gradlew.bat clean build
```

Linux / macOS:

```shell
./gradlew clean build
```

成功すると、配布JARが`build/libs/`に生成されます。現在は自動テストがないため、
ゲーム挙動を変更した場合はNeoForgeの開発クライアントと専用サーバーの両方で
関係する操作を確認してください。

## 貢献方法

1. このリポジトリをForkします。
2. 最新の`main`から`feat/<topic>`または`fix/<topic>`を作成します。
3. 変更を小さく保ち、関連しない修正を同じコミットへ混ぜないようにします。
4. `clean build`を実行し、必要なゲーム内確認を行います。
5. 変更理由、確認方法、影響するクライアント・サーバー範囲を記載してPull Requestを作成します。

実装時は次の方針を守ってください。

- ゲーム進行や権限判定はサーバー側を正とし、クライアントからの値を信用しない。
- プレイヤー向けメッセージを追加する場合は、原則として日本語と英語を用意する。
- 生のWAV、ビルド済みJAR、実行データ、秘密情報をコミットしない。
- 外部コードや素材を取り込む場合は、出典、著作者、ライセンス、変更内容を残す。
- 停止中の領土システムを変更する場合は、事前にIssueで再開方針を確認する。

不具合報告や大きな仕様提案は、実装を始める前にIssueを作成してください。

## ライセンス

このプロジェクトのオリジナルコードと素材は
[GNU General Public License version 3 only](LICENSE)で提供されます。
Pull Requestを提出することで、提出者は自身の貢献部分を`GPL-3.0-only`で
提供することに同意するものとします。

WARLORD音声など、同梱される一部素材・コードには別のライセンスが適用されます。
詳細は[第三者ライセンスと帰属表示](THIRD_PARTY_NOTICES.md)を参照してください。
