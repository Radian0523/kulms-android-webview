# KULMS+ for Android (WebView)

京都大学の学習支援システム (KULMS) を拡張する Android アプリ。
WebView ベースで [kulms-extension](https://github.com/Radian0523/kulms-extension) の機能をネイティブアプリとして提供します。

## 機能

- LMS の WebView 表示 + 拡張機能スクリプト注入
- ECS-ID / SPS-ID によるログイン（パスキー / 多要素認証対応）
- 課題の締切通知
- パスワードの暗号化保存（Android Keystore）

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
