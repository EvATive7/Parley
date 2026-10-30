# 项目文档

## 设计文档

- 在线来源：https://claude.ai/artifact/UEjtN5tGJ2CLHyS95uBpMv
- [Parley-设计文档.pdf](Parley-设计文档.pdf)：从本项目原有的 `docs.local/Parley-设计文档.pdf` 复制，纳入版本管理。
- **导出状态**：本次已用 browser-skill 打开并确认 Claude 文档页面，但未能重新导出。首次尝试时浏览器扩展为 v0.3.3，CLI/daemon 为 v0.4.0；`cooperate sync` 报 `tool.cooperate not implemented in extension`。没有绕过协作保护、修改文档或共享权限。浏览器会话已关闭。随后用户已将扩展升级至 v0.4.0，浏览器协作已恢复；仍需重新导出并核对在线版本。
- PDF 的文本层存在中文字形映射问题，不应将自动抽出的残缺文本当成完整 Markdown 原文。本目录不提供伪造的“全文转换版”。

## 开发阶段

| 阶段 | 范围 | 状态 |
| --- | --- | --- |
| M1 | M3E 交互、假 ASR/翻译、录音与手势 | 已有原型，用户确认完成 |
| M2 | 一家真实 ASR + OpenAI 兼容流式翻译 | 本地测试及 K90PM 双向固定音频云端回归通过，实录与完整异常验收待补 |
| M3 | 补齐其余 ASR、历史与完整设置等 | 未开始；M2 已前置最小服务设置与密钥保护 |

详见 [M2 实施与验收](M2-实施与验收.md) 和 [2026-10-30 云端回归记录](M2-云端回归-2026-10-30.md)。

## API 参考

[APIs/transcriber](APIs/transcriber/README.md) 来自 Voxaia 的 transcriber 文档。它们是提供商参考资料，不代表 Parley 已接入全部提供商或已验证全部示例。
