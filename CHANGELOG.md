# Changelog

## 2.1.1 (2026-04-24)

- 非JSONレスポンスで誤ってログアウト扱いされるバグを修正
- ページ読み込み時にキャッシュ期限切れでもタブ色分け・通知バッジを即座に適用するよう修正

## 2.1.0 (2026-04-24)

- FileViewerActivity 新規追加: PDF等のファイルリンクを別Activityで開く
  - shouldInterceptRequest で Content-Type を HEAD リクエストで判定
  - PDFなどHTML以外のファイルは自動ダウンロード→Intent.createChooser で外部アプリに渡す
  - HTMLコンテンツはそのままWebViewで表示
  - TopAppBar に閉じるボタンとダウンロードボタン付き
- WebViewManager: target="_blank" 対応（onCreateWindow で一時WebView経由でURL捕捉→FileViewerActivity起動）
- WebViewManager: /access/ URL のインターセプト（shouldOverrideUrlLoading）
- AndroidManifest.xml に FileViewerActivity を登録

## 2.0.1 (2026-04-20)

- 拡張機能の画面幅制限を撤廃し、モバイルでも全機能を表示
- 教科書タブの科目順を曜日・時限順にソート
- NOW/NEXT バッジのテキストが科目名に混入するバグを修正

## 2.0.0 (2026-04-19)

- WebView版として再構築
- 拡張機能スクリプト全機能対応

## 1.0.0 (2026-04-19)

- 初回リリース
- WebView ベースの LMS 表示
- 拡張機能スクリプト注入 (kulms-shim)
- ECS-ID / SPS-ID ログイン（2FA 対応）
- パスワード暗号化保存
- 課題の締切通知
