<div align="center">

# 🗣️ Parley

오픈 소스 Android 대면 번역 앱입니다.

[![최신 릴리스](https://img.shields.io/github/v/release/EvATive7/Parley.svg?label=%EC%B5%9C%EC%8B%A0)](https://github.com/EvATive7/Parley/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?logo=android&logoColor=white)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | **한국어** | [Français](README.fr.md) | [Deutsch](README.de.md) | [Español](README.es.md)

[**APK 다운로드**](https://github.com/EvATive7/Parley/releases/latest)

</div>

---

버튼을 누른 채 말하고 떼면 번역됩니다. 상대방도 같은 방식으로 말하며, 각자 번역문을 볼 수 있습니다. 해외에서 길을 묻거나 다국어 회의에 참여하는 등 즉시 소통이 필요한 상황에 적합합니다.

<div align="center">
<table>
<tr>
<td><img src="docs/screenshots/translate-zh-ja.jpg" width="240" alt="중국어와 일본어 번역"></td>
<td><img src="docs/screenshots/recording.jpg" width="240" alt="녹음 중"></td>
<td><img src="docs/screenshots/translate-zh-en.jpg" width="240" alt="중국어와 영어 번역"></td>
</tr>
<tr>
<td align="center">번역 결과</td>
<td align="center">길게 눌러 녹음</td>
<td align="center">이중 언어 안내</td>
</tr>
</table>

<img src="docs/screenshots/face-to-face.jpg" width="600" alt="대면 모드">

*대면 모드: 화면을 둘로 나누어 각 부분이 한 사람을 향하도록 표시합니다.*

</div>

## ✨ 기능

- **번역 방향 자동 감지**: 두 언어를 선택하면 수동으로 전환할 필요 없이, 어느 언어로 말하든 다른 언어로 자동 번역합니다. 그 밖의 언어이거나 언어를 판별할 수 없으면 두 언어로 모두 번역합니다.
- **스트리밍 출력**: 문장 전체가 끝날 때까지 기다리지 않고 번역 결과를 차례로 표시합니다.
- **대면 모드**: 휴대전화를 테이블에 놓으면 화면이 둘로 나뉘어 각 부분이 한 사람을 향하고 상대방의 언어만 표시합니다.
- **길게 눌러 말하기**: 길게 눌러 녹음하고, 떼면 전송하며, 위로 밀면 취소합니다.
- **이중 언어 안내**: 버튼 안내를 두 언어로 함께 표시해 양쪽 모두 이해할 수 있습니다.
- **카드 작업**: 카드를 누르거나 길게 눌러 전체 텍스트를 보고, 원문이나 번역을 복사하고, 삭제할 수 있습니다. 인식된 원문을 고쳐 다시 번역할 수도 있으며, 실패한 번역은 다시 시도할 수 있습니다.
- **종단 간 직접 연결**: 중간 서버가 없습니다. 음성과 텍스트는 설정한 서비스 제공업체로 직접 전송되며, API Key는 Android Keystore로 암호화되어 저장됩니다.
- **대화와 설정 기억**: 완료된 카드와 선택한 언어는 다음 실행 때도 유지됩니다. 메뉴에서 대화 전체를 지울 수 있으며, 서비스 설정은 파일로 내보내 다른 기기에서 가져올 수 있습니다.

## 📦 시작하기

1. [Releases](https://github.com/EvATive7/Parley/releases/latest)에서 APK를 다운로드해 설치합니다(Android 8.0 이상 필요).
2. 오른쪽 위의 톱니바퀴를 눌러 설정을 열고, 음성 인식 서비스와 OpenAI 호환 번역 서비스를 구성합니다.
3. 두 언어를 선택한 다음 버튼을 누른 채 말합니다.

> Parley 자체는 무료이지만 계정이나 사용량을 제공하지 않습니다. 음성 인식과 번역 비용은 선택한 서비스 제공업체에서 청구합니다.

## 🎙️ 음성 인식 서비스

| 제공업체 | 서비스 | 필요한 설정 |
| --- | --- | --- |
| Alibaba Cloud DashScope | Fun-ASR-RealTime(스트리밍 / 비스트리밍) | API Key, 리전(베이징 또는 싱가포르) |
| OpenAI | GPT Realtime Whisper, GPT-4o Transcribe, GPT-4o Mini Transcribe | API Key |
| Volcengine | Doubao Seed ASR 2.0(스트리밍 / 비스트리밍 / 비스트리밍 Turbo) | AppID, Access Token 및 콘솔에서 해당 리소스 활성화 |

스트리밍 서비스는 말하는 동안 결과를 실시간으로 반환합니다. 비스트리밍 서비스는 말이 끝난 후 한 번에 결과를 반환하며 일반적으로 더 정확합니다. 같은 제공업체의 여러 서비스는 하나의 인증 정보 세트를 공유합니다.

## 🔊 음성 합성 서비스

| 제공업체 | 서비스 | 필요한 설정 | 참고 가격 및 문자 계산 |
| --- | --- | --- | --- |
| Android | 시스템 음성 | 없음 | 무료 |
| Alibaba Cloud DashScope | CosyVoice 3.5 Flash / Plus | 공용 DashScope API Key와 리전 | Flash: 1만 자당 ¥0.8, Plus: ¥1.5 |
| Volcengine | Doubao Seed TTS 2.0 | 공용 AppID와 Access Token, 음성 합성 2.0 활성화 | 1만 자당 ¥3, 중국어 한자는 한 자로 계산 |
| MiniMax | Speech 2.8 Turbo / HD | API Key, 중국 또는 국제 리전 | Turbo: 중국 ¥2/1만, 국제 $60/100만. HD: 중국 ¥3.5/1만, 국제 $100/100만. 중국어 한자는 두 자로 계산 |

카드는 수동으로 읽거나 번역 후 자동으로 읽을 수 있으며 대면 모드도 지원합니다. 클라우드 음성은 24 kHz PCM으로 스트리밍 재생되고 최대 20개 결과만 메모리에 캐시됩니다. 녹음을 시작하면 읽기가 중단됩니다. 검증된 클라우드 음성이 없는 언어는 기기 음성을 사용하며 `SYS`가 표시됩니다.

## 🌏 지원 언어

**번역 및 인터페이스**: 영어, 중국어 간체, 중국어 번체, 일본어, 한국어, 프랑스어, 독일어, 스페인어, 이탈리아어, 포르투갈어, 러시아어, 아랍어, 태국어, 베트남어, 인도네시아어.

실제로 인식할 수 있는 언어는 선택한 음성 인식 서비스에 따라 달라집니다. 예를 들어 Volcengine의 스트리밍 서비스는 중국어와 영어만 지원합니다.

## 🔒 개인정보 보호

- 음성과 텍스트는 설정한 서비스 제공업체로만 전송되며 중간 서버를 거치지 않습니다.
- 녹음은 메모리에서만 처리되며 저장되지 않습니다. 완료된 카드의 원문과 번역은 Android Keystore로 암호화되어 기기에 저장되며, 카드를 삭제하거나 **대화 지우기**를 선택할 때까지 다음 실행에도 유지됩니다.
- API Key는 Android Keystore로 암호화되고 로그에 기록되지 않으며 백업에도 포함되지 않습니다.

## ❓ 자주 묻는 질문

<details>
<summary><b>번역 방향이 가끔 잘못되는 이유는 무엇인가요?</b></summary>

음성 인식 서비스가 언어 정보를 반환하지 않으면 Parley가 기기에서 언어를 판별합니다. 너무 짧은 문장이나 중국어와 영어가 섞인 내용은 잘못 판별될 수 있습니다. 언어를 판별할 수 없거나 선택한 두 언어 중 어느 것도 아니면 두 언어로 모두 번역합니다.

</details>

<details>
<summary><b>위로 밀어 취소했을 때 이미 전송된 음성은 어떻게 되나요?</b></summary>

스트리밍 서비스에서는 버튼을 누르고 있는 동안 음성이 실시간으로 전송됩니다. 취소하면 이후 전송을 즉시 중단하고 결과를 버리지만, 이미 전송된 음성은 되돌릴 수 없습니다.

</details>

<details>
<summary><b>재설치 후 키를 다시 입력해야 하는 이유는 무엇인가요?</b></summary>

키는 기기의 암호화된 저장소에 연결되어 있으며 백업되지 않습니다. 앱을 다시 설치하거나 시스템이 암호화 키를 초기화하면 다시 설정해야 합니다.

</details>

<details>
<summary><b>문제를 어떻게 진단할 수 있나요?</b></summary>

설정에서 **디버그 모드**를 켜면 앱 안에 플로팅 로그 창이 표시됩니다. 로그를 복사한 뒤 [Issues](https://github.com/EvATive7/Parley/issues)에 문제를 제보하세요.

</details>

## 🛠️ 개발

<details>
<summary>빌드, 릴리스 및 디렉터리 구조</summary>

### 빌드

JDK 17+와 Android SDK Platform 37이 필요합니다.

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

출력 파일은 `app/build/outputs/apk/{debug,release}/`에 생성됩니다. Debug 패키지 이름은 `io.github.evative7.parley.debug`, 앱 이름은 “Parley Debug”이며 호박색 아이콘을 사용하므로 정식 버전과 함께 설치할 수 있습니다. 로컬 빌드 버전은 `0.1.0`으로 고정됩니다. `app/release.jks`가 없으면 release APK가 debug 키로 서명됩니다.

### 릴리스

Git tag가 버전 번호의 유일한 기준입니다. annotated tag를 푸시하면 `.github/workflows/release.yml`이 실행됩니다.

```sh
git tag -a v0.5.0 -m "Release v0.5.0"
git push origin v0.5.0
```

CI는 tag에서 `versionName`을 추출하고 `major*10000 + minor*100 + patch` 공식으로 `versionCode`를 계산합니다. 그런 다음 단위 테스트를 실행하고 APK에 서명한 뒤 GitHub Release에 업로드합니다. 저장소에는 `KEYSTORE_BASE64`(`release.jks`의 base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets가 필요합니다.

Volcengine 서비스별 리소스 ID: `volc.seedasr.sauc.duration`, `volc.seedasr.auc`, `volc.bigasr.auc_turbo`.

### 디렉터리 구조

```
app/src/main/java/io/github/evative7/parley/
├─ ui/        UI, 말하기 버튼, 카드, 대면 레이아웃, 서비스 설정
├─ state/     TranslateViewModel, SettingsViewModel, 말하기 버튼 상태 머신
├─ audio/     녹음, 음량, WAV 및 리샘플링
├─ asr/       음성 서비스 프리셋과 라우팅, DashScope/OpenAI/Volcengine 인식기
├─ translate/ OpenAI 호환 스트리밍 번역
├─ network/   SSE 파싱, 연결 예열, 민감 정보를 제거한 서비스 오류
├─ model/     언어 및 기기 내 언어 식별
├─ data/      설정, 번역 서비스, Keystore 기반 키 보관
├─ debug/     앱 내 디버그 로그
├─ tts/       시스템/클라우드 음성 합성, PCM 재생, 라우팅 및 메모리 캐시
└─ di/        Hilt 바인딩 및 네트워크 설정
```

### 문서

- [음성 서비스 API 참고 문서](docs/APIs/transcriber/README.md)

</details>
