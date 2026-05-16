# KULMS+ for Android (WebView)

京都大学の学習支援システム (KULMS) を拡張する Android アプリ。
WebView ベースで [kulms-extension](https://github.com/Radian0523/kulms-extension) の機能をネイティブアプリとして提供します。

## 機能

- LMS の WebView 表示 + 拡張機能スクリプト注入
- ECS-ID / SPS-ID によるログイン（パスキー / 多要素認証対応）
- TOTP 自動入力（シークレットキーを登録すると OTP を自動入力、QR スキャン対応）
- TOTP コード確認（現在の 6 桁コードとシークレットキーを表示）
- 課題の締切通知（タイミングカスタマイズ対応）
- 新着課題の即時通知
- ホーム画面アプリショートカット（アイコン長押しで課題表示）
- 端末再起動後の通知自動再スケジュール
- 設定画面（通知カスタマイズ、セキュリティ説明、アプリ情報）
- パスワードの暗号化保存（Android Keystore）
- PDF等のファイルリンクを別画面で表示・ダウンロード（FileViewerActivity）
- Tips タブ（日替わりランダム2件で使い方・アップデート情報を表示）
- target="_blank" リンクおよび /access/ URL の自動インターセプト

## 構成

- **Kotlin** + Jetpack Compose
- WebView + `kulms-shim.js` で chrome.storage API をエミュレート
- 拡張機能のスクリプトを assets から注入

## ビルド

```bash
./gradlew assembleDebug
```

## ライセンス

MIT
