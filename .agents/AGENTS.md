# MoveEarth-Addtional 開発規約 (AGENTS.md)

このドキュメントは、本プロジェクト（`MoveEarth-Addtional` / NeoForge 1.21.1）における実装ルール、アーキテクチャ方針、および固有の設計規約を定めたものです。以降の開発・改修時は必ずこれらのルールに従ってください。

---

## 1. 基本行動・コミュニケーション規約
- **日本語のみの徹底**: 思考プロセス（Chain of Thought）、設計計画、ユーザーへの報告・応答、コード内コメント（必要な場合）のすべての工程において日本語のみを使用してください。
- **確実なコンパイルと品質保証**: 変更を行った際は、必ず Gradle (`.\gradlew classes`) によるコンパイル検証を行い、エラーや警告が生じていないことを確認してください。

---

## 2. NeoForge (1.21.1) コーディング規約

### 2.1 レジストリ登録 (`DeferredRegister` / `DeferredHolder`)
- ブロック、アイテム、BlockEntity、クリエイティブタブ、サウンドイベントなどの登録は、すべて `DeferredRegister` を使用し、メインクラス (`Moveearth_addtional`) のコンストラクタ内でモジュールイベントバス (`modEventBus`) に登録してください。
- レジストリ変数は専用のレジストリ保持クラス（例: `ModBlocks`, `ModItems`, `ModBlockEntities`, `ModCreativeModeTabs`）に整理・集約してください。

### 2.2 保護フィールドやバニラ内部への安全なアクセス
- バニラクラスの `protected` なフィールド（例: `Entity.DATA_SHARED_FLAGS_ID` など）にアクセスする必要がある場合は、直接アクセスしようとせず、必ず以下のように **Java のリフレクションを静的初期化子 (`static {}`) 内で安全に使用** してください。
  ```java
  private static EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID_REF;
  static {
      try {
          Field field = Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
          field.setAccessible(true);
          DATA_SHARED_FLAGS_ID_REF = (EntityDataAccessor<Byte>) field.get(null);
      } catch (Exception e) {
          // 適切なフォールバックまたはログ出力
      }
  }
  ```

### 2.3 ネットワーク通信 (CustomPacketPayload & StreamCodec)
- すべてのカスタムパケットは Java の `record` 型を用いて定義し、`CustomPacketPayload` と `StreamCodec` を実装してください。
- **サーバー / クライアント環境の厳密な分離**:
  - クライアント UI の更新、サウンド再生、画面のオープン等を処理するクライアント側の受信処理は、必ず `@OnlyIn(Dist.CLIENT)` を付与した別クラス（例: `ClientPacketHandler`）の静的メソッドへ分離・委任してください。
  - サーバー側のコードパスからクライアント専用クラスが直接読み出され、専用サーバー環境 (`Dedicated Server`) でのクラスロードクラッシュが発生しないよう注意してください。

---

## 3. サードパーティライブラリとの連携・安全設計

### 3.1 外部ライブラリ (Discord RPC 等) の管理
- 外部 JAR を導入する際、Java モジュールシステムの Split Package 問題などによるコンパイル・実行時の不具合を避けるため、プロジェクトでは依存関係を `compileOnly` とし、ビルド時にクラスを展開・マージする構成（例: `copyDiscordRpcClasses`）を標準としています。
- ライブラリ内部で JDK のリフレクション制限に抵触する箇所（例: Windows レジストリ操作等）が存在する場合は、互換性を担保するダミークラスのシャドウイング等で安全に回避してください。

### 3.2 Lightman's Currency 連携ルール (決済機能実装時の必須規約)
- **口座残高の事前検証の徹底 (`containsValue`)**:
  - `BankAPI.getApi().BankWithdrawFromServer(...)` は無条件で引き落とし処理を試行するため、呼び出しを行う前に必ず **口座の残高事前検証** を実装してください。
  - 下記のように `account.getStoredMoney().containsValue(fee)` で残高が存在するかを確認し、残高0での無料決済やエラーを防止してください。
    ```java
    IBankAccount account = bankReference.get();
    if (account != null && account.getStoredMoney().containsValue(fee)) {
        var result = BankAPI.getApi().BankWithdrawFromServer(account, fee);
        // 成功処理
    }
    ```
- **二重決済・過剰引き落とし防止ガード**:
  - 既に有効化されて稼働中のブロックに対して、クライアント UI およびサーバー側のパケット処理の両方で「稼働中かつ期限内であれば新たな費用決済を行わない」安全ガードを設けてください。
  - GUI 上では稼働中の「支払 / 有効化」ボタンを無効化（グレーアウト）させる UX を心がけてください。

---

## 4. マルチプレイとシングルプレイの動作環境判定
- **専用マルチサーバー限定 (`isDedicatedServer()`) のルール適用**:
  - サーバー開放時間の制限（日本時間 19:00〜01:00 チェック、時間外自動キック、シャットダウン予告アナウンス等）の機能は、必ず `MinecraftServer#isDedicatedServer()` でチェックしてください。
  - シングルプレイやローカルLAN環境（統合サーバー環境）でプレイしている際に誤ってキックやタイマー警告が発動しないよう、完全な制御分離を行ってください。

---

## 5. GUI (`Screen`) および UI/UX デザイン規約
- **コンパクトで整頓されたサイズ設定**:
  - カスタム画面は標準的な幅（例: `windowWidth = 400`, `windowHeight = 230` など）で構成し、画面サイズに依存しない中央配置を行ってください。
- **タブ切り替えとページ送り (`Pagination`) の採用**:
  - 1つの画面に設定項目が収まらない場合（例: 「ホワイトリスト設定」と「維持費・決済設定」）、上部に切り替え用タブボタン (`[ ▶ ホワイトリスト ◀ ]` など) を設けてタブ切替式の UI としてください。
  - メンバーリストなどの複数件項目は、5件ごと等のページネーションボタン (`<`, `>`) で制御し、スクロールバー不要の快適な操作性を維持してください。
- **視認性とフォーカスの配慮**:
  - 背景は透明度を持たせた暗転色（例: `0x70000000`, ウィンドウ背景は `0xDD111111`）とし、入力欄 (`EditBox`) を開いた直後に自動フォーカスの枠線が目立たないよう `setFocused(false)` などの調整を行ってください。
