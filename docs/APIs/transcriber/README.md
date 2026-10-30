# Transcriber API 参考文档

本目录的四个子目录原样复制自参考项目 `Voxaia/docs/APIs/transcriber`，共 25 个原始文件；源项目未移动、删除或修改。

| 子目录 | 内容 | Parley M2 状态 |
| --- | --- | --- |
| [aliyun_dashscope](aliyun_dashscope/) | Fun-ASR、Paraformer、Gummy、Qwen 实时/录音文件识别与 SDK 资料 | 接入 Fun-ASR 实时识别所用的 DashScope 任务型 WebSocket 协议 |
| [azure](azure/) | Fast Transcription、MAI-Transcribe、实时识别示例 | 未接入 |
| [openai](openai/) | 2026.4.16 / 2026.5.18 的 Audio、Realtime 转写资料 | ASR 未接入；翻译使用独立的 OpenAI 兼容 Chat Completions 协议 |
| [volcengine](volcengine/) | 流式与录音文件识别资料、Python 示例 | 未接入 |

版本号、原文链接、模型名与示例代码保留源项目内容。文档快照可能落后于提供商实际接口；示例脚本不会由构建或测试自动执行。Qwen Realtime、Gummy 翻译等接口不能仅通过修改 M2 的模型名称就视为已受支持。
