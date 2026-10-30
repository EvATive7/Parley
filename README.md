# Parley

开源安卓语音翻译 App：按住说话，云端 ASR 实时转写，再由 LLM 翻译，译文流式显示。

> **当前阶段：多 ASR 服务接入。** 已对齐 Voxaia 的 DashScope、OpenAI 与火山引擎转写服务；翻译使用 OpenAI 兼容接口。应用不内置任何凭据。

## 使用

1. 打开 App，授予麦克风权限。
2. 进入首页的「服务设置」：
   - **语音识别**：只需选择预设服务并填写该提供商的凭据。DashScope 需要地域与 API Key；OpenAI 需要 API Key；火山引擎需要 AppID 与 Access Token。同一提供商的凭据在其服务之间共用。
   - **翻译**：填写 OpenAI 兼容 Base URL（例如 `https://api.openai.com/v1`）、模型与 API Key。App 自动追加 `/chat/completions`。
3. 选择双方语言，按住按钮说话，松开发送；上滑后松开取消。

可选 ASR 包括：Fun-ASR-RealTime（流式输入）、Fun-ASR-RealTime（非流式输入）、GPT Realtime Whisper、GPT-4o Transcribe（非流式输入）、GPT-4o Mini Transcribe（非流式输入）、Doubao Seed ASR 2.0（流式输入）、Doubao Seed ASR 2.0（非流式输入）和 Doubao Seed ASR 2.0（非流式输入极速版）。服务名称及描述与 Voxaia 对齐；服务地址、模型和资源 ID 均为内置预设，不能手工修改。

DashScope Key 必须开通 Fun-ASR 实时语音识别权限，并选择与 Key 一致的北京或新加坡地域；OpenAI Key 必须有权调用所选转写模型；火山引擎账号必须开通所选服务对应的 `volc.seedasr.sauc.duration`、`volc.seedasr.auc` 或 `volc.bigasr.auc_turbo` 资源。

**语言限制**：语言栏列出的是翻译目标语言，不代表当前 ASR 模型全部支持。默认 Fun-ASR 的语种范围见 [参考文档](docs/APIs/transcriber/aliyun_dashscope/docs/实时语音识别-Fun-ASR_Gummy_Paraformer.md)。ASR 未返回语言时使用本地 ML Kit 文字语言识别；短句、混合语言可能无法可靠判断，未知语言按语言 2 作为翻译目标。

## 已有能力

- M1 的 Material 3 Expressive 主题、形变按钮（录音时随音量起伏）、双语提示、权限引导与结果卡片动画
- 触碰录音、误触丢弃、上滑取消、60 秒限时、后台丢弃录音
- DashScope：Fun-ASR-RealTime（流式输入）和 Fun-ASR-RealTime（非流式输入），支持北京/新加坡地域
- OpenAI：GPT Realtime Whisper，以及 GPT-4o Transcribe（非流式输入）和 GPT-4o Mini Transcribe（非流式输入）
- 火山引擎：Doubao Seed ASR 2.0（流式输入）、Doubao Seed ASR 2.0（非流式输入）和 Doubao Seed ASR 2.0（非流式输入极速版）
- ASR 路由按预设自动选择协议、固定地址、模型与资源 ID；切换同一提供商的服务会复用已保存凭据
- OpenAI 兼容 Chat Completions + SSE：译文片段流式显示、取消时终止 HTTP 请求
- 配置、鉴权、限流、网络、超时、服务结果异常的分类提示
- 按指定卡片重试翻译；只有最终识别文本可以重试，ASR 的未完成片段不能冒充完整原文
- DataStore 保存设置，API Key 由 Android Keystore AES-GCM 加密；备份与设备迁移排除应用数据

尚未实现的范围：Azure ASR、持久化历史、更完整的设置/本地化。TTS 仍只保留接口，V1 无播放入口。

## 构建与检查

需要 JDK 17+ 与 Android SDK Platform 37，最低支持 Android 8.0（API 26）。

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

产物：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

**Release 当前仍使用开发用 debug 签名，不可作为正式发行签名。**

会话按钮提示有 15 种语言；其余界面目前为英文、简体中文、繁体中文，其他区域回退英文。`app/lint.xml` 将这项已知的缺译工作保留为 warning，而非隐藏报告。

## 结构

```
app/src/main/java/io/github/evative7/parley/
├─ ui/        主界面、按住说话按钮、卡片、权限引导、服务设置
├─ state/     TranslateViewModel、SettingsViewModel、PTT 状态机
├─ audio/     AudioSource、AudioRecorder、VolumeMeter、WAV/重采样工具
├─ asr/       服务预设、路由，以及 DashScope/OpenAI/火山引擎识别器
├─ translate/ Translator、OpenAiCompatibleTranslator
├─ network/   SSE 分帧、脱敏服务错误
├─ model/     语言、离线文字语言识别
├─ data/      SettingsRepository、ServiceSettings、KeyVault
├─ tts/       TextToSpeaker（仅接口）
└─ di/        Hilt 绑定与网络配置
```

## 文档

- [文档索引与来源](docs/README.md)
- [现有设计文档 PDF](docs/Parley-设计文档.pdf)
- [M2 实施记录与验收清单](docs/M2-实施与验收.md)
- [2026-10-30 云端故障修复与真机回归](docs/M2-云端回归-2026-10-30.md)
- [从 Voxaia 复制的 transcriber 文档](docs/APIs/transcriber/README.md)

## 隐私

语音与文本直接发给你设置的服务商，不经过 Parley 自建中转。真实流式识别会在按住期间发送语音；取消只能停止后续发送，不能撤回服务商已经收到的音频。录音始终只在内存处理；M2 对话卡片同样只保留在内存，尚无本地历史数据库。

API Key 不进入源码、APK、日志、URL 或明文 DataStore。设置页中的 Key 被遮挡，表单不写入 Android 保存的界面状态；应用数据禁止备份与设备迁移。设备卸载或 Keystore 密钥失效后，可能需要重新输入 Key。
