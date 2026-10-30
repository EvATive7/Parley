# Parley

开源的安卓语音翻译 App：按住按钮说话，语音经云端 ASR 转成文字，再交给 LLM 翻译，译文逐字显示。

> **当前进度：M1 交互原型。** 识别与翻译结果均为假数据，不会联网。

## M1 包含

- Material 3 Expressive 主题（`MaterialExpressiveTheme` + `MotionScheme.expressive()`），Android 12+ 动态取色，8–11 使用固定配色，深浅色均支持
- 形变按钮：九瓣饼干形（空闲，12 s 慢转）→ 柔和放射形（录音，放大 1.15×）→ 红色圆形铺满进度环（准备取消）→ Loading Indicator（识别 / 翻译中）
- 波浪进度环：进度 = 已录 / 60 s，波幅 = 实时麦克风音量（RMS → dBFS）
- 手势：触碰即录音；按住 < 0.5 s 视为误触；上滑 > 80 dp 准备取消，滑回恢复；60 s 自动结束，剩 10 s 倒计时
- 结果卡片从按钮处弹出，原文流式出现，译文逐片段 150 ms 淡入；完成后显示复制 / 删除
- 麦克风权限引导页：永久拒绝时跳转系统设置，授权后插图形变为对勾
- 触感反馈、切到后台自动丢弃录音、系统“移除动画”时降级

## 构建

需要 JDK 17+ 与 Android SDK Platform 37。

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

最低支持 Android 8.0（API 26）。

## 结构

```
app/src/main/java/io/github/evative7/parley/
├─ ui/        TranslateScreen, PttButton, ResultCard, OnboardingScreen, ShapeMorph, StreamingText
├─ state/     TranslateViewModel, UiState, PttStateMachine
├─ audio/     AudioRecorder, VolumeMeter
├─ asr/       SpeechRecognizer（接口）, FakeRecognizer
├─ translate/ Translator（接口）, FakeTranslator
├─ tts/       TextToSpeaker（仅接口）
└─ di/        AppModule（M1 绑定假实现）
```

## 隐私

接入真实服务后（M2 起），语音和文字会直接发送给你在设置中选择的云服务。录音只在内存中处理，本机只保存文字。仓库和 APK 中不包含任何 API Key。
