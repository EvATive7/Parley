# Transcriber API 参考文档

本目录的四个子目录原样复制自参考项目 `Voxaia/docs/APIs/transcriber`，共 25 个原始文件；源项目未移动、删除或修改。

| 子目录 | 内容 | Parley M2 状态 |
| --- | --- | --- |
| [aliyun_dashscope](aliyun_dashscope/) | Fun-ASR、Paraformer、Gummy、Qwen 实时/录音文件识别与 SDK 资料 | 已接入 Fun-ASR-RealTime（流式输入）和 Fun-ASR-RealTime（非流式输入）；北京/新加坡地域预设 |
| [azure](azure/) | Fast Transcription、MAI-Transcribe、实时识别示例 | 未接入 |
| [openai](openai/) | 2026.4.16 / 2026.5.18 的 Audio、Realtime 转写资料 | 已接入 GPT Realtime Whisper、GPT-4o Transcribe（非流式输入）和 GPT-4o Mini Transcribe（非流式输入）；翻译另用 Chat Completions |
| [volcengine](volcengine/) | 流式与录音文件识别资料、Python 示例 | 已接入 Doubao Seed ASR 2.0（流式输入）、Doubao Seed ASR 2.0（非流式输入）和 Doubao Seed ASR 2.0（非流式输入极速版） |

版本号、原文链接、模型名与示例代码保留源项目内容。文档快照可能落后于提供商实际接口；示例脚本不会由构建或测试自动执行。Parley 只支持设置页列出的预设，不能通过修改模型名称临时切换到其他协议或服务。
