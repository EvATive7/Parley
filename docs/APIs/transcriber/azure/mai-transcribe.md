---
layout: Conceptual
title: LLM Speech API 中的 MAI-Transcribe-1 - Speech 服务 - Foundry Tools | Microsoft Learn
canonicalUrl: https://learn.microsoft.com/zh-cn/azure/ai-services/speech-service/mai-transcribe
schema: Conceptual
author: PatrickFarley
breadcrumb_path: ../../breadcrumb/azure-ai/toc.json
depot_name: Learn.azure-ai
description: 了解如何在 Azure 语音 LLM 语音 API 中使用 MAI-Transcribe-1 模型
document_id: ea2972e1-4495-7dab-6951-bf36ee17046b
document_version_independent_id: 8a5ca35d-b717-1520-305b-cdd2eaf0bcdd
feedback_help_link_type: get-help-at-qna
feedback_help_link_url: https://learn.microsoft.com/answers/tags/55/azure-speech/
feedback_product_url: https://feedback.azure.com/d365community/forum/09041fae-0b25-ec11-b6e6-000d3a4f0858?c=21041fae-0b25-ec11-b6e6-000d3a4f0858
feedback_system: Standard
git_commit_id: 844136e8700498c3a4b7596daae91748eb0db2af
gitcommit: https://github.com/MicrosoftDocs/azure-ai-docs-pr/blob/844136e8700498c3a4b7596daae91748eb0db2af/articles/ai-services/speech-service/mai-transcribe.md
learn_banner_products:
- azure
locale: zh-cn
manager: nitinme
ms.author: pafarley
ms.collection: ce-skilling-ai-copilot
ms.date: 2026-04-01T00:00:00.0000000Z
ms.service: azure-ai-speech
ms.suite: office
ms.topic: how-to
ms.update-cycle: 180-days
original_content_git_url: https://github.com/MicrosoftDocs/azure-ai-docs-pr/blob/live/articles/ai-services/speech-service/mai-transcribe.md
permissioned-type: public
recommendation_types:
- Training
- Certification
recommendations: true
site_name: Docs
uhfHeaderId: azure-ai-foundry
updated_at: 2026-04-09T18:27:00.0000000Z
ms.translationtype: MT
ms.contentlocale: zh-cn
loc_version: 2026-04-09T17:22:22.2819833Z
loc_source_id: Github-845157915#live
loc_file_id: Github-845157915.live.Learn.azure-ai.articles/ai-services/speech-service/mai-transcribe.md
page_type: conceptual
interactive_type: azurecli
toc_rel: toc.json
word_count: 607
asset_id: ai-services/speech-service/mai-transcribe
item_type: Content
cmProducts:
- https://authoring-docs-microsoft.poolparty.biz/devrel/a8711e05-df51-442a-970f-935304535b39
- https://authoring-docs-microsoft.poolparty.biz/devrel/68ec7f3a-2bc6-459f-b959-19beb729907d
- https://microsoft-devrel.poolparty.biz/DevRelOfferingOntology/c6f99e62-1cf6-4b71-af9b-649b05f80cce
spProducts:
- https://authoring-docs-microsoft.poolparty.biz/devrel/3d3c20d8-79ed-4203-aee0-ffb9c9bafe72
- https://authoring-docs-microsoft.poolparty.biz/devrel/90370425-aca4-4a39-9533-d52e5e002a5d
- https://microsoft-devrel.poolparty.biz/DevRelOfferingOntology/3f56b378-07a9-4fa1-afe8-9889fdc77628
platformId: aa2830c6-a594-baaa-fd50-0e31237bdbc3
---

# LLM Speech API 中的 MAI-Transcribe-1 - Speech 服务 - Foundry Tools | Microsoft Learn

注释

此功能目前处于公开预览状态。 此预览版没有附带服务级别协议，建议不要用于生产工作负载。 某些功能可能不受支持或者受限。 有关详细信息，请参阅 [Microsoft Azure 预览版的使用条款](https://azure.microsoft.com/support/legal/preview-supplemental-terms/)。

MAI-Transcribe-1 是由Microsoft AI （MAI） 超级智能团队开发的语音识别模型，具有双重重点：准确度高、效率高。 可以将 MAI-Transcribe-1 模型与 LLM 语音 API 配合使用。

## 先决条件

- 一个 Azure 订阅。 可以[免费创建一个帐户](https://azure.microsoft.com/pricing/purchase-options/azure-account?cid=msft_learn)。
- [在 Azure 门户中创建用于语音的 Foundry 资源](https://portal.azure.com/#create/Microsoft.CognitiveServicesAIFoundry)。
- 获取语音资源密钥和区域。 部署语音资源后，选择“转到资源”以查看和管理密钥。 有关受支持区域的当前列表，请参阅 [语音服务区域](regions?tabs=llmspeech)。
- 音频文件（大小小于 300 MB），格式之一：WAV、MP3 和 FLAC。

## 使用 MAI-Transcribe-1 模型

### 上传音频

可以通过以下方式提供音频数据：

- 传递内联音频数据。

```
  --form 'audio=@"YourAudioFile"'
```

- 从公共 `audioUrl`文件上传音频文件。

```
  --form 'definition": "{\"audioUrl\": \"https://crbn.us/hello.wav"}"'
```

在以下部分中，内联音频上传用作示例。

### 创建听录

若要使用 MAI-Transcribe-1 模型，请在请求中相应地设置 `model` 属性。

```azurecli
curl --location 'https://<YourServiceRegion>.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2025-10-15' \
--header 'Content-Type: multipart/form-data' \
--header 'Ocp-Apim-Subscription-Key: <YourSpeechResourceKey>' \
--form 'audio=@"YourAudioFile.wav"' \
--form 'definition={
  "locales": ["en"],
  "enhancedMode": {
    "enabled": true,
    "model":"mai-transcribe-1"
  }
}'
```

请注意以下使用 MAI-Transcribe-1 模型的限制：

- 不支持说话人分离。
- 不支持提示。

（可选）指定语言代码`locales`以强制使用单种语言进行识别（例如）。 `en` 如果未指定语言，服务会自动检测它。 mai-transcribe-1 模型目前支持以下语言：

| 语言代码 | 语言 |
| --- | --- |
| `ar` | 阿拉伯语 |
| `zh` | Chinese |
| `cs` | 捷克语 |
| `da` | 丹麦语 |
| `nl` | 荷兰语 |
| `en` | 英语 |
| `fi` | 芬兰语 |
| `fr` | 法语 |
| `de` | 德语 |
| `hi` | 印地语 |
| `hu` | 匈牙利语 |
| `id` | 印度尼西亚语 |
| `it` | 意大利语 |
| `ja` | 日语 |
| `ko` | Korean |
| `nb` | 书面挪威语 |
| `pl` | 波兰语 |
| `pt` | Portuguese |
| `ro` | 罗马尼亚语 |
| `ru` | 俄语 |
| `es` | 西班牙语 |
| `sv` | 瑞典语 |
| `th` | 泰语 |
| `tr` | 土耳其语 |
| `vi` | 越南语 |

小窍门

有关使用 LLM 语音 API 的详细信息，请参阅 [LLM 语音 API](llm-speech)