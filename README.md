# Parley

开源安卓语音翻译 App：按住说话，云端 ASR 实时转写，再由 LLM 翻译，译文流式显示。

> **当前阶段：M2 真实服务接入（0.2.1）。** DashScope 实时识别与 OpenAI 兼容翻译已通过本地回归，以及 K90PM 上中英文固定音频的双向真实云端测试；麦克风实录、异常场景和 Release 真机验收仍待补。不内置 API Key。

## 使用

1. 打开 App，授予麦克风权限。
2. 进入首页的「服务设置」：
   - **语音识别**：DashScope WebSocket 地址、模型、对应地域的 API Key。默认北京地址为 `wss://dashscope.aliyuncs.com/api-ws/v1/inference`，模型为 `fun-asr-realtime`。百炼 Key 的模型限制必须授权 **Fun-ASR 实时语音识别**（`fun-asr-realtime`），不是录音文件识别（`fun-asr`）。
   - **翻译**：OpenAI 兼容 Base URL（例如 `https://api.openai.com/v1`）、模型与 API Key。App 自动追加 `/chat/completions`。
3. 选择双方语言，按住按钮说话，松开发送；上滑后松开取消。

未配置服务时会提示设置。所有识别与翻译请求都使用已配置的服务。只允许 `wss://` ASR 和 `https://` 翻译地址，不跟随重定向。

**语言限制**：语言栏列出的是翻译目标语言，不代表当前 ASR 模型全部支持。默认 Fun-ASR 的语种范围见 [参考文档](docs/APIs/transcriber/aliyun_dashscope/docs/实时语音识别-Fun-ASR_Gummy_Paraformer.md)。ASR 未返回语言时使用本地 ML Kit 文字语言识别；短句、混合语言可能无法可靠判断，未知语言按语言 2 作为翻译目标。

## 已有能力

- M1 的 Material 3 Expressive 主题、形变按钮（录音时随音量起伏）、双语提示、权限引导与结果卡片动画
- 触碰录音、误触丢弃、上滑取消、60 秒限时、后台丢弃录音
- DashScope WebSocket：16 kHz 单声道 PCM16LE、握手前有界缓冲、实时文本修订、松手后提交最终结果
- OpenAI 兼容 Chat Completions + SSE：译文片段流式显示、取消时终止 HTTP 请求
- 配置、鉴权、限流、网络、超时、服务结果异常的分类提示
- 按指定卡片重试翻译；只有最终识别文本可以重试，ASR 的未完成片段不能冒充完整原文
- DataStore 保存设置，API Key 由 Android Keystore AES-GCM 加密；备份与设备迁移排除应用数据

尚未实现的 M3 范围：其余 ASR 提供商、持久化历史、更完整的设置/本地化。TTS 仍只保留接口，V1 无播放入口。

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
├─ audio/     AudioSource、AudioRecorder、VolumeMeter
├─ asr/       SpeechRecognizer、DashScopeRecognizer
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
