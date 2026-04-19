# Contributing

KULMS+ Android WebView への貢献を歓迎します。

## 開発環境のセットアップ

1. リポジトリをフォーク & クローン
   ```bash
   git clone https://github.com/<your-username>/kulms-android-webview.git
   ```
2. 拡張機能リポジトリもクローン（同じ親ディレクトリに配置）
   ```bash
   git clone https://github.com/Radian0523/kulms-extension.git
   ```
3. Android Studio でプロジェクトを開く
4. 実機またはエミュレータでビルド・実行（API 26+）
5. 初回起動時に SSO でログインして動作確認

## プロジェクト構成

```
data/
  CredentialStore.kt     # AES/GCM 暗号化によるパスワード保存
  WebViewManager.kt      # WebView管理 + StorageBridge + ScriptInjector
ui/
  LMSWebViewScreen.kt    # LMS表示画面
  login/                 # ログイン画面
  theme/                 # テーマ定義
notification/
  NotificationHelper.kt  # 通知スケジュール管理
  NotificationReceiver.kt # アラーム受信 → 通知表示
```

### アーキテクチャのポイント

- WebView で LMS を表示し、`kulms-shim.js` で `chrome.storage` API をエミュレート
- 拡張機能のスクリプト（assignments.js 等）を `assets` からページ完了時に注入
- `KulmsStorageBridge` が SharedPreferences に永続化し、JS にコールバック返却
- 課題データ更新時に `NotificationHelper` が AlarmManager で通知をスケジュール

## コーディング規約

- 外部依存は最小限に
- Kotlin + Jetpack Compose を使用
- コルーチンで非同期処理

## Pull Request の流れ

1. `main` から作業ブランチを作成
2. 変更を実装し、Android Studio でビルドが通ることを確認
3. コミットメッセージは変更内容を日本語で簡潔に記述
4. Pull Request を作成し、変更内容を説明

## Issue

- バグ報告・機能リクエストは [Issue テンプレート](https://github.com/Radian0523/kulms-android-webview/issues/new/choose) を使用してください
- フィードバックフォーム: [Google Forms](https://docs.google.com/forms/d/e/1FAIpQLScLn4G2IF1w0-QOWPKZ7R1LXjOq7OocYUmGJLoNA6JBuA20EA/viewform)
