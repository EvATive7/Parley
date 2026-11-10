<div align="center">

# 🗣️ Parley

開源的 Android 面對面翻譯應用程式。

[![最新版本](https://img.shields.io/github/v/release/EvATive7/Parley.svg?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC)](https://github.com/EvATive7/Parley/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?logo=android&logoColor=white)

[English](README.md) | [简体中文](README.zh-CN.md) | **繁體中文** | [日本語](README.ja.md) | [한국어](README.ko.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

[**下載 APK**](https://github.com/EvATive7/Parley/releases/latest)

</div>

---

按住說話，放開翻譯。對方也以同樣方式操作，雙方各自看到譯文。適合出國問路、跨語言會議等需要即時溝通的情境。

<div align="center">
<table>
<tr>
<td><img src="docs/screenshots/translate-zh-ja.jpg" width="240" alt="中日翻譯"></td>
<td><img src="docs/screenshots/recording.jpg" width="240" alt="錄音中"></td>
<td><img src="docs/screenshots/translate-zh-en.jpg" width="240" alt="中英翻譯"></td>
</tr>
<tr>
<td align="center">翻譯結果</td>
<td align="center">按住錄音</td>
<td align="center">雙語提示</td>
</tr>
</table>

<img src="docs/screenshots/face-to-face.jpg" width="600" alt="面對面模式">

*面對面模式：畫面分為兩側，分別朝向一人。*

</div>

## ✨ 功能

- **自動判斷翻譯方向**：選好兩種語言後不必手動切換，說其中一種語言就會自動翻譯成另一種；說其他語言或無法辨識時，兩種語言都會翻譯。
- **串流輸出**：譯文逐字顯示，不必等待整句完成。
- **面對面模式**：將手機平放在桌上，畫面一分為二，兩側各朝向一人，並只顯示對方使用的語言。
- **按住說話**：按住錄音，放開傳送，上滑取消。
- **雙語提示**：按鈕提示同時以兩種語言顯示，雙方都能理解。
- **卡片操作**：點選或長按卡片可查看全文、複製原文或譯文、刪除，也可以修改辨識出的原文後重新翻譯；翻譯失敗時可重試。
- **端對端直連**：不經過中介伺服器，語音和文字直接傳送到你設定的服務商。API Key 使用 Android Keystore 加密儲存。
- **記住對話與設定**：已完成的卡片和語言選擇會在下次開啟時保留；可從選單清除整段對話，服務設定也可以匯出為檔案並在其他裝置上匯入。

## 📦 開始使用

1. 從 [Releases](https://github.com/EvATive7/Parley/releases/latest) 下載並安裝 APK（需要 Android 8.0 以上版本）。
2. 點選右上角齒輪開啟設定，配置語音辨識服務和翻譯服務（OpenAI 相容介面）。
3. 選好雙方語言，按住按鈕說話即可。

> Parley 本身免費，不提供任何帳號或額度。語音辨識和翻譯費用由你選擇的服務商收取。

## 🎙️ 語音辨識服務

| 服務商 | 服務 | 必要設定 |
| --- | --- | --- |
| 阿里雲 DashScope | Fun-ASR-RealTime（串流／非串流） | API Key、地域（北京或新加坡） |
| OpenAI | GPT Realtime Whisper、GPT-4o Transcribe、GPT-4o Mini Transcribe | API Key |
| 火山引擎 | Doubao Seed ASR 2.0（串流／非串流／非串流極速版） | AppID、Access Token，並須在主控台開通對應資源 |

串流服務會在說話時即時傳回結果；非串流服務會在說完後一次傳回，通常更加準確。同一服務商的不同服務共用一組憑證。

## 🔊 語音合成服務

| 服務商 | 服務 | 必要設定 | 參考價格與計數規則 |
| --- | --- | --- | --- |
| Android | 系統語音 | 無 | 免費 |
| 阿里雲 DashScope | Qwen Audio 3.1 TTS Flash、CosyVoice 3 Flash | 共用 DashScope API Key 與地域 | Qwen Audio：輸入 ¥1.5、輸出 ¥12/百萬 token（約 ¥0.3/萬字元）；CosyVoice：¥1/萬字元，一個漢字計兩個字元 |
| 火山引擎 | 豆包 Seed TTS 2.0 | 共用 AppID 與 Access Token，須開通語音合成 2.0 | ¥3/萬字元；一個漢字計一個字元 |
| MiniMax | Speech 2.8 Turbo / HD | API Key、中國區或國際區 | Turbo：中國區 ¥2/萬、國際區 $60/百萬；HD：中國區 ¥3.5/萬、國際區 $100/百萬；一個漢字計兩個字元 |

卡片可手動朗讀，也能在翻譯完成後自動朗讀（包括面對面模式）。雲端音訊以 24 kHz PCM 串流播放，只在記憶體快取最多 20 筆；開始錄音會立即中斷朗讀。沒有已驗證雲端音色的語言會改用系統語音並顯示 `SYS`。

## 🌏 支援的語言

**翻譯與介面**：英語、簡體中文、繁體中文、日語、韓語、法語、德語、西班牙語、義大利語、葡萄牙語、俄語、阿拉伯語、泰語、越南語、印尼語。

實際可辨識的語言取決於所選的語音辨識服務。例如，火山引擎的串流服務只支援中文和英文。

## 🔒 隱私

- 語音和文字只會傳送到你設定的服務商，不經過任何中介伺服器。
- 錄音只在記憶體中處理，不會儲存。已完成卡片中的原文與譯文會使用 Android Keystore 加密儲存在本機，直到你刪除卡片或選擇「清除對話」。
- API Key 使用 Android Keystore 加密儲存，不會寫入日誌，也不會包含在備份中。

## ❓ 常見問題

<details>
<summary><b>翻譯方向有時不正確？</b></summary>

當語音辨識服務沒有傳回語言資訊時，Parley 會在本機判斷。過短的句子或中英混合內容可能造成誤判；無法判斷或不屬於所選的兩種語言時，會同時翻譯成兩種語言。

</details>

<details>
<summary><b>上滑取消後，已經傳送的語音怎麼辦？</b></summary>

使用串流服務時，按住按鈕期間語音會即時傳送。取消會立刻停止繼續傳送並捨棄結果，但已經送出的部分無法撤回。

</details>

<details>
<summary><b>重新安裝後需要再次填寫金鑰？</b></summary>

金鑰綁定本機的加密儲存空間，不會參與備份。重新安裝應用程式或系統重設加密金鑰後，需要重新設定。

</details>

<details>
<summary><b>如何排查問題？</b></summary>

在設定中開啟「偵錯模式」，應用程式內會顯示懸浮日誌視窗。複製日誌後，可前往 [Issues](https://github.com/EvATive7/Parley/issues) 回報。

</details>

## 🛠️ 開發

<details>
<summary>建置、發行與目錄結構</summary>

### 建置

需要 JDK 17+ 和 Android SDK Platform 37。

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

產出檔位於 `app/build/outputs/apk/{debug,release}/`。Debug 版套件名稱為 `io.github.evative7.parley.debug`，應用程式名稱為「Parley Debug」，使用琥珀色圖示，可與正式版共存。本機建置的版本號固定為 `0.1.0`；若沒有 `app/release.jks`，release APK 會使用 debug 金鑰簽署。

### 發行

Git tag 是版本號的唯一來源。推送 annotated tag 會觸發 `.github/workflows/release.yml`：

```sh
git tag -a v0.5.0 -m "Release v0.5.0"
git push origin v0.5.0
```

CI 會從 tag 取得 `versionName`，依 `major*10000 + minor*100 + patch` 計算 `versionCode`，執行單元測試、簽署 APK，然後上傳到 GitHub Release。儲存庫必須設定以下 secrets：`KEYSTORE_BASE64`（`release.jks` 的 base64）、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

火山引擎各服務對應的資源 ID：`volc.seedasr.sauc.duration`、`volc.seedasr.auc`、`volc.bigasr.auc_turbo`。

### 目錄結構

```
app/src/main/java/io/github/evative7/parley/
├─ ui/        介面、說話按鈕、卡片、面對面版面、服務設定
├─ state/     TranslateViewModel、SettingsViewModel、說話按鈕狀態機
├─ audio/     錄音、音量、WAV 與重新取樣
├─ asr/       語音服務預設與路由，以及 DashScope/OpenAI/火山引擎辨識器
├─ translate/ OpenAI 相容串流翻譯
├─ network/   SSE 解析、連線預熱、已脫敏的服務錯誤
├─ model/     語言與本機語言辨識
├─ data/      設定、翻譯服務、以 Keystore 保管的金鑰
├─ debug/     應用程式內偵錯日誌
├─ tts/       系統/雲端語音合成、PCM 播放、路由與記憶體快取
└─ di/        Hilt 綁定與網路設定
```

### 文件

- [語音服務 API 參考](docs/APIs/transcriber/README.md)

</details>
