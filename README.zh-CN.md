<div align="center">

# 🗣️ Parley

面对面翻译 Android 应用，开源。

[![最新版本](https://img.shields.io/github/v/release/EvATive7/Parley.svg?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC)](https://github.com/EvATive7/Parley/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?logo=android&logoColor=white)

[English](README.md) | **简体中文** | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

[**下载 APK**](https://github.com/EvATive7/Parley/releases/latest)

</div>

---

按住说话，松手翻译。对方同样操作，各自看到译文。适用于出国问路、跨语言会议等需要即时沟通的场景。

<div align="center">
<table>
<tr>
<td><img src="docs/screenshots/translate-zh-ja.jpg" width="240" alt="中日翻译"></td>
<td><img src="docs/screenshots/recording.jpg" width="240" alt="录音中"></td>
<td><img src="docs/screenshots/translate-zh-en.jpg" width="240" alt="中英翻译"></td>
</tr>
<tr>
<td align="center">翻译结果</td>
<td align="center">按住录音</td>
<td align="center">双语提示</td>
</tr>
</table>

<img src="docs/screenshots/face-to-face.jpg" width="600" alt="面对面模式">

*面对面模式：屏幕一分为二，两侧各朝一人*

</div>

## ✨ 功能

- **自动识别语向**：选好两种语言后无需手动切换，说哪种就翻成另一种；说其他语言或无法识别时，两种语言都翻。
- **流式输出**：翻译结果逐词显示，不必等整句翻完。
- **面对面模式**：手机平放桌上，屏幕一分为二，两侧各朝一人，只显示对方的语言。
- **按住说话**：按住录音，松手发送，上滑取消。
- **双语提示**：按钮提示同时以两种语言显示，双方都能看懂。
- **卡片操作**：点击或长按卡片查看全文、复制原文或译文、删除，也可以修改识别出的原文后重新翻译；翻译失败的可以重试。
- **端到端直连**：没有中间服务器，语音和文字直接发往你配置的服务商。API Key 使用 Android Keystore 加密存储。
- **记住对话与设置**：已完成的卡片和语言选择会在下次打开时保留；可从菜单清空整段对话，服务设置也可以导出为文件并在其他设备上导入。

## 📦 开始使用

1. 从 [Releases](https://github.com/EvATive7/Parley/releases/latest) 下载并安装 APK（需要 Android 8.0+）。
2. 打开设置（右上角齿轮），配置语音识别服务和翻译服务（OpenAI 兼容接口）。
3. 选好双方语言，按住按钮说话即可。

> Parley 本身免费，不提供任何账号或额度。语音识别和翻译的费用由你所选的服务商收取。

## 🎙️ 语音识别服务

| 服务商 | 服务 | 需要配置 |
| --- | --- | --- |
| 阿里云 DashScope | Fun-ASR-RealTime（流式 / 非流式） | API Key、地域（北京或新加坡） |
| OpenAI | GPT Realtime Whisper、GPT-4o Transcribe、GPT-4o Mini Transcribe | API Key |
| 火山引擎 | Doubao Seed ASR 2.0（流式 / 非流式 / 非流式极速版） | AppID、Access Token，需在控制台开通对应资源 |

流式服务在说话过程中实时返回结果，非流式服务在说完后一次性返回，通常更准确。同一服务商的不同服务共用一套凭证。

## 🔊 语音合成服务

| 服务商 | 服务 | 需要配置 | 参考价格与计数规则 |
| --- | --- | --- | --- |
| 阿里云 DashScope | Qwen Audio 3.1 TTS Flash、CosyVoice 3 Flash | 共用 DashScope API Key 与地域 | Qwen Audio：输入 ¥1.5、输出 ¥12/百万 token（约 ¥0.3/万字符）；CosyVoice：¥1/万字符，一个汉字计两个字符 |
| 火山引擎 | 豆包 Seed TTS 2.0 | 共用 AppID 与 Access Token，需开通语音合成 2.0 | ¥3/万字符；一个汉字计一个字符 |
| MiniMax | Speech 2.8 Turbo / HD | API Key、中国区或国际区 | Turbo：中国区 ¥2/万、国际区 $60/百万；HD：中国区 ¥3.5/万、国际区 $100/百万；一个汉字计两个字符 |

卡片支持手动朗读，也可在翻译完成后自动朗读（包括面对面模式）。云端音频以 24 kHz PCM 流式播放，仅在内存中缓存最多 20 条；开始录音会立即中断朗读。所选服务没有内置音色的语言无法朗读。

## 🌏 支持的语言

**翻译与界面**：英语、简体中文、繁体中文、日语、韩语、法语、德语、西班牙语、意大利语、葡萄牙语、俄语、阿拉伯语、泰语、越南语、印尼语。

实际能识别哪些语言取决于所选的语音识别服务。例如火山引擎的流式服务仅支持中文和英文。

## 🔒 隐私

- 语音和文字只发送给你配置的服务商，不经过任何中间服务器。
- 录音仅在内存中处理，不会保存。已完成卡片中的原文和译文使用 Android Keystore 加密保存在本机，直到你删除卡片或选择“清空对话”。
- API Key 使用 Android Keystore 加密保存，不写入日志，不参与备份。

## ❓ 常见问题

<details>
<summary><b>翻译方向有时不对？</b></summary>

当语音识别服务未返回语种信息时，Parley 会在本地判断。过短的句子或中英混杂的内容可能导致误判；无法判断或不属于所选的两种语言时，会同时翻译成两种语言。

</details>

<details>
<summary><b>上滑取消后已发送的语音怎么办？</b></summary>

使用流式服务时，语音在按住期间实时发送。取消会立即停止发送并丢弃结果，但已发出的部分无法撤回。

</details>

<details>
<summary><b>重装后需要重新填写密钥？</b></summary>

密钥绑定本机加密存储，不参与备份。重装应用或系统重置加密密钥后需要重新配置。

</details>

<details>
<summary><b>如何排查问题？</b></summary>

在设置中开启「调试模式」，应用内会显示悬浮日志窗口。复制日志后可到 [Issues](https://github.com/EvATive7/Parley/issues) 反馈。

</details>

## 🛠️ 开发

<details>
<summary>构建、发版与目录结构</summary>

### 构建

需要 JDK 17+ 和 Android SDK Platform 37。

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

产物在 `app/build/outputs/apk/{debug,release}/`。Debug 包名为 `io.github.evative7.parley.debug`，应用名「Parley Debug」，琥珀色图标，可与正式版共存。本地构建的版本号固定为 `0.1.0`；没有 `app/release.jks` 时，release 包使用 debug 密钥签名。

### 发版

Git tag 是版本号的唯一来源。推送 annotated tag 触发 `.github/workflows/release.yml`：

```sh
git tag -a v0.5.0 -m "Release v0.5.0"
git push origin v0.5.0
```

CI 从 tag 提取 `versionName`，按 `major*10000 + minor*100 + patch` 计算 `versionCode`，运行单元测试，签名后上传 APK 到 GitHub Release。仓库需配置 secrets：`KEYSTORE_BASE64`（`release.jks` 的 base64）、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

火山引擎各服务对应的资源 ID：`volc.seedasr.sauc.duration`、`volc.seedasr.auc`、`volc.bigasr.auc_turbo`。

### 目录结构

```
app/src/main/java/io/github/evative7/parley/
├─ ui/        界面、说话按钮、卡片、面对面布局、服务设置
├─ state/     TranslateViewModel、SettingsViewModel、说话按钮状态机
├─ audio/     录音、音量、WAV 与重采样
├─ asr/       语音服务预设、路由，DashScope/OpenAI/火山引擎识别器
├─ translate/ OpenAI 兼容流式翻译
├─ network/   SSE 解析、连接预热、脱敏的服务错误
├─ model/     语言、本机语种识别
├─ data/      设置、翻译服务、基于 Keystore 的密钥保管
├─ debug/     应用内调试日志
├─ tts/       云端语音合成、PCM 播放、路由与内存缓存
└─ di/        Hilt 绑定与网络配置
```

### 文档

- [语音服务 API 参考](docs/APIs/transcriber/README.md)

</details>
