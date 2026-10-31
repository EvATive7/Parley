<div align="center">

# 🗣️ Parley

対面で使えるオープンソースの Android 翻訳アプリです。

[![最新リリース](https://img.shields.io/github/v/release/EvATive7/Parley.svg?label=%E6%9C%80%E6%96%B0%E7%89%88)](https://github.com/EvATive7/Parley/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?logo=android&logoColor=white)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | **日本語** | [한국어](README.ko.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

[**APK をダウンロード**](https://github.com/EvATive7/Parley/releases/latest)

</div>

---

押している間に話し、離すと翻訳します。相手も同じように操作し、それぞれが翻訳結果を確認できます。海外で道を尋ねるときや、多言語の会議など、すぐに意思疎通したい場面に適しています。

<div align="center">
<table>
<tr>
<td><img src="docs/screenshots/translate-zh-ja.jpg" width="240" alt="中国語と日本語の翻訳"></td>
<td><img src="docs/screenshots/recording.jpg" width="240" alt="録音中"></td>
<td><img src="docs/screenshots/translate-zh-en.jpg" width="240" alt="中国語と英語の翻訳"></td>
</tr>
<tr>
<td align="center">翻訳結果</td>
<td align="center">長押しして録音</td>
<td align="center">2 言語の案内</td>
</tr>
</table>

<img src="docs/screenshots/face-to-face.jpg" width="600" alt="対面モード">

*対面モード：画面を 2 つに分け、それぞれを向かい合う人の方向へ表示します。*

</div>

## ✨ 機能

- **翻訳方向を自動判定**：2 つの言語を選ぶだけで、どちらを話しても自動的にもう一方へ翻訳します。それ以外の言語や判定できない場合は、両方の言語へ翻訳します。
- **ストリーミング出力**：文全体の完了を待たず、翻訳結果を順次表示します。
- **対面モード**：スマートフォンをテーブルに置くと画面が 2 つに分かれ、各側が一人ずつに向き、相手の言語だけを表示します。
- **長押しして話す**：長押しで録音し、離して送信します。上にスライドするとキャンセルできます。
- **2 言語の案内**：ボタンの操作案内を両方の言語で表示するため、どちらの人にも伝わります。
- **カード操作**：カードをタップまたは長押しして、全文の表示、原文や翻訳のコピー、削除ができます。認識された原文を修正して再翻訳することもでき、翻訳に失敗した場合は再試行できます。
- **エンドツーエンドの直接接続**：中継サーバーはありません。音声とテキストは設定したサービスへ直接送信され、API Key は Android Keystore で暗号化して保存されます。
- **設定を記憶**：言語の選択は次回起動時も保持されます。サービス設定はファイルに書き出し、別の端末で読み込めます。

## 📦 使い始める

1. [Releases](https://github.com/EvATive7/Parley/releases/latest) から APK をダウンロードしてインストールします（Android 8.0 以上が必要です）。
2. 右上の歯車から設定を開き、音声認識サービスと OpenAI 互換の翻訳サービスを設定します。
3. 2 つの言語を選び、ボタンを押したまま話します。

> Parley 自体は無料ですが、アカウントや利用枠は提供しません。音声認識と翻訳の料金は、選択したサービス事業者から請求されます。

## 🎙️ 音声認識サービス

| 事業者 | サービス | 必要な設定 |
| --- | --- | --- |
| Alibaba Cloud DashScope | Fun-ASR-RealTime（ストリーミング／非ストリーミング） | API Key、リージョン（北京またはシンガポール） |
| OpenAI | GPT Realtime Whisper、GPT-4o Transcribe、GPT-4o Mini Transcribe | API Key |
| Volcengine | Doubao Seed ASR 2.0（ストリーミング／非ストリーミング／非ストリーミング Turbo） | AppID、Access Token、およびコンソールでの対応リソースの有効化 |

ストリーミングサービスは発話中に結果を返します。非ストリーミングサービスは話し終えた後にまとめて結果を返し、一般に精度が高くなります。同じ事業者の各サービスでは、1 組の認証情報を共有します。

## 🌏 対応言語

**翻訳とインターフェース**：英語、簡体字中国語、繁体字中国語、日本語、韓国語、フランス語、ドイツ語、スペイン語、イタリア語、ポルトガル語、ロシア語、アラビア語、タイ語、ベトナム語、インドネシア語。

実際に認識できる言語は、選択した音声認識サービスによって異なります。たとえば、Volcengine のストリーミングサービスは中国語と英語だけに対応しています。

## 🔒 プライバシー

- 音声とテキストは設定したサービスにのみ送信され、中継サーバーを経由しません。
- 録音はメモリ内だけで処理されます。会話は永続化されず、アプリを閉じると失われます。
- API Key は Android Keystore で暗号化され、ログに書き込まれず、バックアップにも含まれません。

## ❓ よくある質問

<details>
<summary><b>翻訳方向が間違うことがあるのはなぜですか？</b></summary>

音声認識サービスが言語情報を返さない場合、Parley が端末上で言語を判定します。短すぎる文や中国語と英語が混在した内容では誤判定することがあります。判定できない場合や、選択した 2 つの言語のどちらでもない場合は、両方の言語へ翻訳します。

</details>

<details>
<summary><b>上にスライドしてキャンセルしたとき、すでに送信された音声はどうなりますか？</b></summary>

ストリーミングサービスでは、ボタンを押している間に音声がリアルタイムで送信されます。キャンセルすると以後の送信を直ちに停止して結果を破棄しますが、すでに送信された音声は取り消せません。

</details>

<details>
<summary><b>再インストール後にキーを再入力する必要があるのはなぜですか？</b></summary>

キーは端末上の暗号化ストレージに関連付けられ、バックアップされません。アプリを再インストールした場合や、システムが暗号化キーをリセットした場合は、再設定が必要です。

</details>

<details>
<summary><b>問題を調査するにはどうすればよいですか？</b></summary>

設定で「デバッグモード」を有効にすると、アプリ内にフローティングログウィンドウが表示されます。ログをコピーし、[Issues](https://github.com/EvATive7/Parley/issues) から報告してください。

</details>

## 🛠️ 開発

<details>
<summary>ビルド、リリース、ディレクトリ構成</summary>

### ビルド

JDK 17+ と Android SDK Platform 37 が必要です。

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

出力先は `app/build/outputs/apk/{debug,release}/` です。Debug 版のパッケージ名は `io.github.evative7.parley.debug`、アプリ名は「Parley Debug」で、琥珀色のアイコンを使用し、リリース版と同時にインストールできます。ローカルビルドのバージョンは `0.1.0` に固定されています。`app/release.jks` がない場合、release APK は debug キーで署名されます。

### リリース

Git tag がバージョン番号の唯一の情報源です。annotated tag を push すると `.github/workflows/release.yml` が起動します。

```sh
git tag -a v0.5.0 -m "Release v0.5.0"
git push origin v0.5.0
```

CI は tag から `versionName` を取得し、`major*10000 + minor*100 + patch` で `versionCode` を計算します。その後、単体テストを実行し、APK に署名して GitHub Release へアップロードします。リポジトリには `KEYSTORE_BASE64`（`release.jks` の base64）、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` の secrets が必要です。

Volcengine の各サービスに対応するリソース ID：`volc.seedasr.sauc.duration`、`volc.seedasr.auc`、`volc.bigasr.auc_turbo`。

### ディレクトリ構成

```
app/src/main/java/io/github/evative7/parley/
├─ ui/        UI、会話ボタン、カード、対面レイアウト、サービス設定
├─ state/     TranslateViewModel、SettingsViewModel、会話ボタンの状態マシン
├─ audio/     録音、音量、WAV、リサンプリング
├─ asr/       音声サービスのプリセットとルーティング、DashScope/OpenAI/Volcengine 認識器
├─ translate/ OpenAI 互換のストリーミング翻訳
├─ network/   SSE 解析、接続のウォームアップ、機密情報を除いたサービスエラー
├─ model/     言語と端末上の言語識別
├─ data/      設定、翻訳サービス、Keystore によるキー保管
├─ debug/     アプリ内デバッグログ
├─ tts/       音声読み上げ（インターフェースのみ）
└─ di/        Hilt バインディングとネットワーク設定
```

### ドキュメント

- [音声サービス API リファレンス](docs/APIs/transcriber/README.md)

</details>
