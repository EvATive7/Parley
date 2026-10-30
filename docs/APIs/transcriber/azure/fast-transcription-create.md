---
layout: Conceptual
title: Use the fast transcription API - Speech service - Foundry Tools | Microsoft Learn
canonicalUrl: https://learn.microsoft.com/en-us/azure/ai-services/speech-service/fast-transcription-create
breadcrumb_path: ../../breadcrumb/azure-ai/toc.json
feedback_help_link_url: https://learn.microsoft.com/answers/tags/55/azure-speech/
feedback_help_link_type: get-help-at-qna
feedback_product_url: https://feedback.azure.com/d365community/forum/09041fae-0b25-ec11-b6e6-000d3a4f0858?c=21041fae-0b25-ec11-b6e6-000d3a4f0858
feedback_system: Standard
permissioned-type: public
recommendations: true
recommendation_types:
- Training
- Certification
uhfHeaderId: azure-ai-foundry
ms.suite: office
zone_pivot_group_filename: zone-pivots/azure-ai/zone-pivot-groups.json
author: PatrickFarley
learn_banner_products:
- azure
manager: nitinme
ms.author: pafarley
ms.collection: ce-skilling-ai-copilot
ms.update-cycle: 180-days
ms.service: azure-ai-speech
description: Learn how to use Azure Speech in Foundry Tools for fast transcriptions, where you submit audio get the transcription results faster than real-time.
reviewer: patrickfarley
ms.reviewer: pafarley
ms.topic: how-to
ms.date: 2026-03-10T00:00:00.0000000Z
zone_pivot_groups: fast-transcription-quickstart
ai-usage: ai-assisted
locale: en-us
document_id: 6076a22c-7646-0702-073f-d81d51dc1e81
document_version_independent_id: 1b658e2d-a791-c035-cde7-ac6fd010672a
updated_at: 2026-04-08T17:10:00.0000000Z
original_content_git_url: https://github.com/MicrosoftDocs/azure-ai-docs-pr/blob/live/articles/ai-services/speech-service/fast-transcription-create.md
gitcommit: https://github.com/MicrosoftDocs/azure-ai-docs-pr/blob/7afa4742dfd2c0e2417a1601a304087d9017958b/articles/ai-services/speech-service/fast-transcription-create.md
git_commit_id: 7afa4742dfd2c0e2417a1601a304087d9017958b
site_name: Docs
depot_name: Learn.azure-ai
page_type: conceptual
interactive_type: azurecli
toc_rel: toc.json
word_count: 10639
asset_id: ai-services/speech-service/fast-transcription-create
moniker_range_name: 
monikers: []
item_type: Content
source_path: articles/ai-services/speech-service/fast-transcription-create.md
cmProducts:
- https://authoring-docs-microsoft.poolparty.biz/devrel/a8711e05-df51-442a-970f-935304535b39
- https://microsoft-devrel.poolparty.biz/DevRelOfferingOntology/c6f99e62-1cf6-4b71-af9b-649b05f80cce
- https://authoring-docs-microsoft.poolparty.biz/devrel/a3955c7b-f5ee-420d-aff5-d7119738f38b
spProducts:
- https://authoring-docs-microsoft.poolparty.biz/devrel/3d3c20d8-79ed-4203-aee0-ffb9c9bafe72
- https://microsoft-devrel.poolparty.biz/DevRelOfferingOntology/3f56b378-07a9-4fa1-afe8-9889fdc77628
- https://authoring-docs-microsoft.poolparty.biz/devrel/b31948f4-2f38-404b-ac93-c3c8c5b3ae33
platformId: 0d5ed0b4-1f85-6839-ba8f-d19788dbdbc6
---

# Use the fast transcription API - Speech service - Foundry Tools | Microsoft Learn

Fast transcription API is used to transcribe audio files with returning results synchronously and faster than real-time. Use fast transcription in the scenarios that you need the transcript of an audio recording as quickly as possible with predictable latency, such as:

- Quick audio or video transcription, subtitles, and edit.
- Meeting notes
- Voicemail

Unlike the batch transcription API, fast transcription API only produces transcriptions in the display (not lexical) form. The display form is a more human-readable form of the transcription that includes punctuation and capitalization.

Tip

You can also use the latest LLM-powered speech transcription and speech translation with [LLM speech](llm-speech).

## Feature availability

This table shows which transcription features are supported by the fast transcription API, with and without LLM speech (enhanced mode):

| Feature | Fast Transcription (default) | LLM Speech (enhanced) |
| --- | --- | --- |
| Transcription | ✅ (transcription Speech models) | ✅ (multimodal model) |
| Translation | ❌ | ✅ (multimodal model) |
| Diarization | ✅ | ✅ |
| Channel (stereo) | ✅ | ✅ |
| Profanity filtering | ✅ | ✅ |
| Specify locale | ✅ | ❌ (use prompting to implement) |
| Custom prompting | ❌ | ✅ |
| Phrase list | ✅ | ❌ (use prompting to implement) |

For LLM speech (enhanced mode), use prompting to guide the output style instead of using explicit locale or phrase lists.

::: zone pivot="ai-foundry"

You can try fast transcription in [Microsoft Foundry](https://ai.azure.com/?cid=learnDocs) without writing any code.

## Prerequisites

- An Azure subscription. [Create one for free](https://azure.microsoft.com/pricing/purchase-options/azure-account?cid=msft_learn).
- A Foundry project. If you need to create a project, see [Create a Microsoft Foundry project](../../foundry/how-to/create-projects).

## Try fast transcription

# [Foundry (new) portal](#tab/new-foundry)
1. Sign in to [Microsoft Foundry](https://ai.azure.com/?cid=learnDocs). Make sure the **New Foundry** toggle is on. These steps refer to **Foundry (new)**.![](../../foundry/media/version-banner/new-foundry.png)
2. Select **Build** from the top right menu.
3. Select **Models** on the left pane.
4. The **AI Services** tab shows the Foundry models that can be used out of the box in the Foundry portal. Select **Azure Speech - Speech to text** to open the Speech to Text playground.
5. In the top dropdown, select **Fast transcription**.
6. Optionally use the **Parameters** section to change the language, profanity policy, and other settings.
7. Use the **Upload files** section to select your audio file. Then select **Start**.
8. View the transcription output in the **Transcript** tab. Optionally view the raw API response output in the **JSON** tab.
9. Switch to the **Code** tab to get sample code for using fast transcription in your application.

# [Foundry (classic) portal](#tab/classic-foundry)
Fast transcription isn't available in the Foundry (classic) portal. Use the Foundry (new) portal instead.

---

::: zone-end

::: zone pivot="programming-language-rest"

## Prerequisites

- An Azure Speech resource in one of the regions where the fast transcription API is available. For the current list of supported regions, see the [Speech service regions table](regions?tabs=stt).
- An audio file (less than 5 hours long and less than 500 MB in size) in one of the formats and codecs supported by the batch transcription API: WAV, MP3, OPUS/OGG, FLAC, WMA, AAC, ALAW in WAV container, MULAW in WAV container, AMR, WebM, and SPEEX. For more information about supported audio formats, see [supported audio formats](batch-transcription-audio-data#supported-input-formats-and-codecs).

## Upload audio

You can provide audio data to fast transcription in the following ways:

- Inline audio upload

```
--form 'audio=@"YourAudioFile"'
```

- Audio from a public URL

```
--form 'definition="{"audioUrl": "https://crbn.us/hello.wav"}"'
```

Tip

For long audio files, uploading from a public URL is recommended.

In the sections below, inline audio upload is used as an example.

## Use the fast transcription API

Tip

Try out fast transcription in the [Microsoft Foundry portal](https://aka.ms/fasttranscription/studio).

We learn how to use the fast transcription API (via [Transcriptions - Transcribe](/en-us/rest/api/speechtotext/transcriptions/transcribe)) with the following scenarios:

- [Known locale specified](?tabs=locale-specified): Transcribe an audio file with a specified locale. If you know the locale of the audio file, you can specify it to improve transcription accuracy and minimize the latency.
- [Language identification on](?tabs=language-identification-on): Transcribe an audio file with language identification on. If you're not sure about the locale of the audio file, you can turn on language identification to let the Speech service identify the locale (one locale per audio).
- [Multi-lingual transcription](?tabs=multilingual-transcription-on): Transcribe an audio file with the latest multi-lingual speech transcription model. If your audio contains multi-lingual contents that you want to transcribe continuously and accurately, you can use the latest multi-lingual speech transcription model without specifying the locale codes.
- [Diarization on](?tabs=diarization-on): Transcribe an audio file with diarization on. Diarization distinguishes between different speakers in the conversation. The Speech service provides information about which speaker was speaking a particular part of the transcribed speech.
- [Multi-channel on](?tabs=multi-channel-on): Transcribe an audio file that has one or two channels. Multi-channel transcriptions are useful for audio files with multiple channels, such as audio files with multiple speakers or audio files with background noise. By default, the fast transcription API merges all input channels into a single channel and then performs the transcription. If this isn't desirable, channels can be transcribed independently without merging.

# [Known locale specified](#tab/locale-specified)
Make a multipart/form-data POST request to the `transcriptions` endpoint with the audio file and the request body properties.

The following example shows how to transcribe an audio file with a specified locale. If you know the locale of the audio file, you can specify it to improve transcription accuracy and minimize the latency.

- Replace `YourSpeechResourceKey` with your Speech resource key.
- Replace `YourServiceRegion` with your Speech resource region.
- Replace `YourAudioFile` with the path to your audio file.

Important

For the recommended keyless authentication with Microsoft Entra ID, replace `--header 'Ocp-Apim-Subscription-Key: YourSpeechResourceKey'` with `--header "Authorization: Bearer YourAccessToken"`. For more information about keyless authentication, see the [role-based access control](role-based-access-control#authentication-with-keys-and-tokens) how-to guide.

```azurecli
curl --location 'https://YourServiceRegion.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2025-10-15' \
--header 'Content-Type: multipart/form-data' \
--header 'Ocp-Apim-Subscription-Key: YourSpeechResourceKey' \
--form 'audio=@"YourAudioFile"' \
--form 'definition="{
    "locales":["en-US"]}"'
```

Construct the form definition according to the following instructions:

- Set the optional (but recommended) `locales` property that should match the expected locale of the audio data to transcribe. In this example, the locale is set to `en-US`. For more information about the supported locales, see [speech to text supported languages](language-support?tabs=stt).

For more information about `locales` and other properties for the fast transcription API, see the request configuration options section later in this guide.

The response includes `durationMilliseconds`, `offsetMilliseconds`, and more. The `combinedPhrases` property contains the full transcriptions for all speakers.

```json
{
    "durationMilliseconds": 182439,
    "combinedPhrases": [
        {
            "text": "Good afternoon. This is Sam. Thank you for calling Contoso. How can I help? Hi there. My name is Mary. I'm currently living in Los Angeles, but I'm planning to move to Las Vegas. I would like to apply for a loan. Okay. I see you're currently living in California. Let me make sure I understand you correctly. Uh You'd like to apply for a loan even though you'll be moving soon. Is that right? Yes, exactly. So I'm planning to relocate soon, but I would like to apply for the loan first so that I can purchase a new home once I move there. And are you planning to sell your current home? Yes, I will be listing it on the market soon and hopefully it'll sell quickly. That's why I'm applying for a loan now, so that I can purchase a new house in Nevada and close on it quickly as well once my current home sells. I see. Would you mind holding for a moment while I take your information down? Yeah, no problem. Thank you for your help. Mm-hmm. Just one moment. All right. Thank you for your patience, ma'am. May I have your first and last name, please? Yes, my name is Mary Smith. Thank you, Ms. Smith. May I have your current address, please? Yes. So my address is 123 Main Street in Los Angeles, California, and the zip code is 90923. Sorry, that was a 90 what? 90923. 90923 on Main Street. Got it. Thank you. May I have your phone number as well, please? Uh Yes, my phone number is 504-529-2351 and then yeah. 2351. Got it. And do you have an e-mail address we I can associate with this application? uh Yes, so my e-mail address is mary.a.sm78@gmail.com. Mary.a, was that a S-N as in November or M as in Mike? M as in Mike. Mike78, got it. Thank you. Ms. Smith, do you currently have any other loans? Uh Yes, so I currently have two other loans through Contoso. So my first one is my car loan and then my other is my student loan. They total about 1400 per month combined and my interest rate is 8%. I see. And you're currently paying those loans off monthly, is that right? Yes, of course I do. OK, thank you. Here's what I suggest we do. Let me place you on a brief hold again so that I can talk with one of our loan officers and get this started for you immediately. In the meantime, it would be great if you could take a few minutes and complete the remainder of the secure application online at www.contosoloans.com. Yeah, that sounds good. I can go ahead and get started. Thank you for your help. Thank you."
        }
    ],
    "phrases": [
        {
            "offsetMilliseconds": 960,
            "durationMilliseconds": 640,
            "text": "Good afternoon.",
            "words": [
                {
                    "text": "Good",
                    "offsetMilliseconds": 960,
                    "durationMilliseconds": 240
                },
                {
                    "text": "afternoon.",
                    "offsetMilliseconds": 1200,
                    "durationMilliseconds": 400
                }
            ],
            "locale": "en-US",
            "confidence": 0.93554276
        },
        {
            "offsetMilliseconds": 1600,
            "durationMilliseconds": 640,
            "text": "This is Sam.",
            "words": [
                {
                    "text": "This",
                    "offsetMilliseconds": 1600,
                    "durationMilliseconds": 240
                },
                {
                    "text": "is",
                    "offsetMilliseconds": 1840,
                    "durationMilliseconds": 120
                },
                {
                    "text": "Sam.",
                    "offsetMilliseconds": 1960,
                    "durationMilliseconds": 280
                }
            ],
            "locale": "en-US",
            "confidence": 0.93554276
        },
        {
            "offsetMilliseconds": 2240,
            "durationMilliseconds": 1040,
            "text": "Thank you for calling Contoso.",
            "words": [
                {
                    "text": "Thank",
                    "offsetMilliseconds": 2240,
                    "durationMilliseconds": 200
                },
                {
                    "text": "you",
                    "offsetMilliseconds": 2440,
                    "durationMilliseconds": 80
                },
                {
                    "text": "for",
                    "offsetMilliseconds": 2520,
                    "durationMilliseconds": 120
                },
                {
                    "text": "calling",
                    "offsetMilliseconds": 2640,
                    "durationMilliseconds": 200
                },
                {
                    "text": "Contoso.",
                    "offsetMilliseconds": 2840,
                    "durationMilliseconds": 440
                }
            ],
            "locale": "en-US",
            "confidence": 0.93554276
        },
        {
            "offsetMilliseconds": 3280,
            "durationMilliseconds": 640,
            "text": "How can I help?",
            "words": [
                {
                    "text": "How",
                    "offsetMilliseconds": 3280,
                    "durationMilliseconds": 120
                },
                {
                    "text": "can",
                    "offsetMilliseconds": 3440,
                    "durationMilliseconds": 120
                },
                {
                    "text": "I",
                    "offsetMilliseconds": 3560,
                    "durationMilliseconds": 40
                },
                {
                    "text": "help?",
                    "offsetMilliseconds": 3600,
                    "durationMilliseconds": 320
                }
            ],
            "locale": "en-US",
            "confidence": 0.93554276
        },
        {
            "offsetMilliseconds": 5040,
            "durationMilliseconds": 400,
            "text": "Hi there.",
            "words": [
                {
                    "text": "Hi",
                    "offsetMilliseconds": 5040,
                    "durationMilliseconds": 240
                },
                {
                    "text": "there.",
                    "offsetMilliseconds": 5280,
                    "durationMilliseconds": 160
                }
            ],
            "locale": "en-US",
            "confidence": 0.93554276
        },
        {
            "offsetMilliseconds": 5440,
            "durationMilliseconds": 800,
            "text": "My name is Mary.",
            "words": [
                {
                    "text": "My",
                    "offsetMilliseconds": 5440,
                    "durationMilliseconds": 80
                },
                {
                    "text": "name",
                    "offsetMilliseconds": 5520,
                    "durationMilliseconds": 120
                },
                {
                    "text": "is",
                    "offsetMilliseconds": 5640,
                    "durationMilliseconds": 80
                },
                {
                    "text": "Mary.",
                    "offsetMilliseconds": 5720,
                    "durationMilliseconds": 520
                }
            ],
            "locale": "en-US",
            "confidence": 0.93554276
        },
        // More transcription results...
        // Redacted for brevity
        {
            "offsetMilliseconds": 180320,
            "durationMilliseconds": 680,
            "text": "Thank you for your help.",
            "words": [
                {
                    "text": "Thank",
                    "offsetMilliseconds": 180320,
                    "durationMilliseconds": 160
                },
                {
                    "text": "you",
                    "offsetMilliseconds": 180480,
                    "durationMilliseconds": 80
                },
                {
                    "text": "for",
                    "offsetMilliseconds": 180560,
                    "durationMilliseconds": 120
                },
                {
                    "text": "your",
                    "offsetMilliseconds": 180680,
                    "durationMilliseconds": 120
                },
                {
                    "text": "help.",
                    "offsetMilliseconds": 180800,
                    "durationMilliseconds": 200
                }
            ],
            "locale": "en-US",
            "confidence": 0.92022026
        },
        {
            "offsetMilliseconds": 181960,
            "durationMilliseconds": 280,
            "text": "Thank you.",
            "words": [
                {
                    "text": "Thank",
                    "offsetMilliseconds": 181960,
                    "durationMilliseconds": 200
                },
                {
                    "text": "you.",
                    "offsetMilliseconds": 182160,
                    "durationMilliseconds": 80
                }
            ],
            "locale": "en-US",
            "confidence": 0.92022026
        }
    ]
}
```

# [Language identification on](#tab/language-identification-on)
Make a multipart/form-data POST request to the `transcriptions` endpoint with the audio file and the request body properties.

The following example shows how to transcribe an audio file with language identification on. If you're not sure about the locale, you can specify multiple locales. If you don't specify any locale, or if the locales that you specify aren't in the audio file, then the Speech service tries to identify the locale.

Note

The language identification in fast transcription is designed to identify one main language locale per audio file. If you need to transcribe multi-lingual contents in the audio, please consider [multi-lingual transcription](?tabs=multilingual-transcription-on).

- Replace `YourSpeechResoureKey` with your Speech resource key.
- Replace `YourServiceRegion` with your Speech resource region.
- Replace `YourAudioFile` with the path to your audio file.

Important

For the recommended keyless authentication with Microsoft Entra ID, replace `--header 'Ocp-Apim-Subscription-Key: YourSpeechResoureKey'` with `--header "Authorization: Bearer YourAccessToken"`. For more information about keyless authentication, see the [role-based access control](role-based-access-control#authentication-with-keys-and-tokens) how-to guide.

```azurecli
curl --location 'https://YourServiceRegion.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2025-10-15' \
--header 'Content-Type: multipart/form-data' \
--header 'Ocp-Apim-Subscription-Key: YourSpeechResoureKey' \
--form 'audio=@"YourAudioFile"' \
--form 'definition="{
    "locales":["en-US","ja-JP"]}"'
```

Construct the form definition according to the following instructions:

- Set the optional (but recommended) `locales` property that should match the expected locale of the audio data to transcribe. In this example, the locales are set to `en-US` and `ja-JP`. The supported locales that you can specify are within all the supported languages.

For more information about `locales` and other properties for the fast transcription API, see the request configuration options section later in this guide.

The response includes `durationMilliseconds`, `offsetMilliseconds`, and more. The `combinedPhrases` property contains the full transcriptions for all speakers.

```json
{
    "durationMilliseconds": 185079,
    "combinedPhrases": [
        {
            "text": "Hello, thank you for calling Contoso. Who am I speaking with today? Hi, my name is Mary Rondo. I'm trying to enroll myself with Contoso. Hi, Mary. Are you calling because you need health insurance? Yes. Yeah, I'm calling to sign up for insurance. Great. Uh If you can answer a few questions, we can get you signed up in a Jiffy. Okay. So what's your full name? uh So Mary Beth Rondo, last name is R like Romeo, O like Ocean, N like Nancy D, D like Dog, and O like Ocean again. Rondo. Got it. And what's the best callback number in case we get disconnected? I only have a cell phone, so I can give you that. Yep, that'll be fine. Sure. So it's 234-554 and then 9312. Got it. So to confirm, it's 234-554-9312. Yep, that's right. Excellent. Let's get some additional information for your application. Do you have a job? Uh Yes, I am self-employed. Okay, so then you have a social security number as well? Uh Yes, I do. Okay, and what is your social security number, please? Uh Sure, so it's 412-253-4931. 6789. Sorry, was that a 25 or a 225? You cut out for a bit. It's double two, so 412, then another two, then five. Thank you so much. And could I have your e-mail address, please? Yeah, it's maryrondo@gmail.com. So my first and last name at gmail.com. No periods, no dashes. Great. Uh That is the last question. So let me take your information and I'll be able to get you signed up right away. Thank you for calling Contoso and I'll be able to get you signed up immediately. One of our agents will call you back in about 24 hours or so to confirm your application. That sounds good. Thank you. Absolutely. If you need anything else, please give us a call at 1-800-555-5564, extension 123. Thank you very much for calling Contoso. Actually, so I have one more question. Yes, of course. I'm curious, will I be getting a physical card as proof of coverage? So the default is a digital membership card, but we can send you a physical card if you prefer. Uh Yes. Could you please mail it to me when it's ready? I'd like to have it shipped to, are you ready for my address? Uh Yeah. uh So it's 2660 Unit A on Maple Avenue, Southeast Lansing, and then zip code is 48823. Absolutely. I've made a note on your file. Awesome. Thanks so much. You're very welcome. Thank you for calling Contoso and have a great day."
        }
    ],
    "phrases": [
        {
            "offsetMilliseconds": 720,
            "durationMilliseconds": 1600,
            "text": "Hello, thank you for calling Contoso.",
            "words": [
                {
                    "text": "Hello,",
                    "offsetMilliseconds": 720,
                    "durationMilliseconds": 480
                },
                {
                    "text": "thank",
                    "offsetMilliseconds": 1200,
                    "durationMilliseconds": 200
                },
                {
                    "text": "you",
                    "offsetMilliseconds": 1400,
                    "durationMilliseconds": 80
                },
                {
                    "text": "for",
                    "offsetMilliseconds": 1480,
                    "durationMilliseconds": 120
                },
                {
                    "text": "calling",
                    "offsetMilliseconds": 1600,
                    "durationMilliseconds": 240
                },
                {
                    "text": "Contoso.",
                    "offsetMilliseconds": 1840,
                    "durationMilliseconds": 480
                }
            ],
            "locale": "en-US",
            "confidence": 0.93265927
        },
        {
            "offsetMilliseconds": 2320,
            "durationMilliseconds": 1120,
            "text": "Who am I speaking with today?",
            "words": [
                {
                    "text": "Who",
                    "offsetMilliseconds": 2320,
                    "durationMilliseconds": 160
                },
                {
                    "text": "am",
                    "offsetMilliseconds": 2480,
                    "durationMilliseconds": 80
                },
                {
                    "text": "I",
                    "offsetMilliseconds": 2560,
                    "durationMilliseconds": 80
                },
                {
                    "text": "speaking",
                    "offsetMilliseconds": 2640,
                    "durationMilliseconds": 320
                },
                {
                    "text": "with",
                    "offsetMilliseconds": 2960,
                    "durationMilliseconds": 160
                },
                {
                    "text": "today?",
                    "offsetMilliseconds": 3120,
                    "durationMilliseconds": 320
                }
            ],
            "locale": "en-US",
            "confidence": 0.93265927
        },
        {
            "offsetMilliseconds": 4480,
            "durationMilliseconds": 1600,
            "text": "Hi, my name is Mary Rondo.",
            "words": [
                {
                    "text": "Hi,",
                    "offsetMilliseconds": 4480,
                    "durationMilliseconds": 400
                },
                {
                    "text": "my",
                    "offsetMilliseconds": 4880,
                    "durationMilliseconds": 120
                },
                {
                    "text": "name",
                    "offsetMilliseconds": 5000,
                    "durationMilliseconds": 120
                },
                {
                    "text": "is",
                    "offsetMilliseconds": 5120,
                    "durationMilliseconds": 160
                },
                {
                    "text": "Mary",
                    "offsetMilliseconds": 5280,
                    "durationMilliseconds": 240
                },
                {
                    "text": "Rondo.",
                    "offsetMilliseconds": 5520,
                    "durationMilliseconds": 560
                }
            ],
            "locale": "en-US",
            "confidence": 0.93265927
        },
        {
            "offsetMilliseconds": 6120,
            "durationMilliseconds": 1800,
            "text": "I'm trying to enroll myself with Contoso.",
            "words": [
                {
                    "text": "I'm",
                    "offsetMilliseconds": 6120,
                    "durationMilliseconds": 120
                },
                {
                    "text": "trying",
                    "offsetMilliseconds": 6240,
                    "durationMilliseconds": 200
                },
                {
                    "text": "to",
                    "offsetMilliseconds": 6440,
                    "durationMilliseconds": 80
                },
                {
                    "text": "enroll",
                    "offsetMilliseconds": 6520,
                    "durationMilliseconds": 200
                },
                {
                    "text": "myself",
                    "offsetMilliseconds": 6720,
                    "durationMilliseconds": 360
                },
                {
                    "text": "with",
                    "offsetMilliseconds": 7080,
                    "durationMilliseconds": 120
                },
                {
                    "text": "Contoso.",
                    "offsetMilliseconds": 7200,
                    "durationMilliseconds": 720
                }
            ],
            "locale": "en-US",
            "confidence": 0.93265927
        },
        // More transcription results...
        // Redacted for brevity
        {
            "offsetMilliseconds": 181520,
            "durationMilliseconds": 720,
            "text": "You're very welcome.",
            "words": [
                {
                    "text": "You're",
                    "offsetMilliseconds": 181520,
                    "durationMilliseconds": 160
                },
                {
                    "text": "very",
                    "offsetMilliseconds": 181680,
                    "durationMilliseconds": 200
                },
                {
                    "text": "welcome.",
                    "offsetMilliseconds": 181880,
                    "durationMilliseconds": 360
                }
            ],
            "locale": "en-US",
            "confidence": 0.90571773
        },
        {
            "offsetMilliseconds": 182320,
            "durationMilliseconds": 1840,
            "text": "Thank you for calling Contoso and have a great day.",
            "words": [
                {
                    "text": "Thank",
                    "offsetMilliseconds": 182320,
                    "durationMilliseconds": 200
                },
                {
                    "text": "you",
                    "offsetMilliseconds": 182520,
                    "durationMilliseconds": 80
                },
                {
                    "text": "for",
                    "offsetMilliseconds": 182600,
                    "durationMilliseconds": 120
                },
                {
                    "text": "calling",
                    "offsetMilliseconds": 182720,
                    "durationMilliseconds": 280
                },
                {
                    "text": "Contoso",
                    "offsetMilliseconds": 183000,
                    "durationMilliseconds": 520
                },
                {
                    "text": "and",
                    "offsetMilliseconds": 183520,
                    "durationMilliseconds": 160
                },
                {
                    "text": "have",
                    "offsetMilliseconds": 183680,
                    "durationMilliseconds": 120
                },
                {
                    "text": "a",
                    "offsetMilliseconds": 183800,
                    "durationMilliseconds": 40
                },
                {
                    "text": "great",
                    "offsetMilliseconds": 183840,
                    "durationMilliseconds": 200
                },
                {
                    "text": "day.",
                    "offsetMilliseconds": 184040,
                    "durationMilliseconds": 120
                }
            ],
            "locale": "en-US",
            "confidence": 0.90571773
        }
    ]
}
```

# [Multi-lingual transcription](#tab/multilingual-transcription-on)
Make a multipart/form-data POST request to the `transcriptions` endpoint with the audio file and the request body properties.

The following example shows how to transcribe an audio file with the latest multi-lingual speech transcription model. If your audio contains multi-lingual contents that you want to transcribe continuously and accurately, you can use the latest multi-lingual speech transcription model without specifying the locale codes.

- Replace `YourSpeechResoureKey` with your Speech resource key.
- Replace `YourServiceRegion` with your Speech resource region.
- Replace `YourAudioFile` with the path to your audio file.

Important

For the recommended keyless authentication with Microsoft Entra ID, replace `--header 'Ocp-Apim-Subscription-Key: YourSpeechResoureKey'` with `--header "Authorization: Bearer YourAccessToken"`. For more information about keyless authentication, see the [role-based access control](role-based-access-control#authentication-with-keys-and-tokens) how-to guide.

```azurecli
curl --location 'https://YourServiceRegion.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2025-10-15' \
--header 'Content-Type: multipart/form-data' \
--header 'Ocp-Apim-Subscription-Key: YourSpeechResoureKey' \
--form 'audio=@"YourAudioFile"' \
--form 'definition="{
    "locales":[]}"'
```

Construct the form definition according to the following instructions:

- You can either leave the `locales` property empty (as shown in the previous example) or omit it.
- The supported audio input locales with current multi-lingual model are: **de-DE**, **en-AU**, **en-CA**, **en-GB**, **en-IN**, **en-US**, **es-ES**, **es-MX**, **fr-CA**, **fr-FR**, **it-IT**, **ja-JP**, **ko-KR**, **pt-BR**, and **zh-CN**.
- The transcription result is distinguished at the language level and will follow the "major locale of this language" (e.g., it will always output "en-US" locale code even if the audio has a British English or Indian English accent).

For more information about `locales` and other properties for the fast transcription API, see the request configuration options section later in this guide.

The response includes `durationMilliseconds`, `offsetMilliseconds`, and more. The `combinedPhrases` property contains the full transcriptions for all speakers.

```json
{
    "durationMilliseconds": 57187,
    "combinedPhrases": [
        {
            "text": "With custom speech,you can evaluate and improve the microsoft speech to text accuracy for your applications and products 现成的语音转文本,利用通用语言模型作为一个基本模型,使用microsoft自有数据进行训练,并反映常用的口语。此基础模型使用那些代表各常见领域的方言和发音进行了预先训练。 Quand vous effectuez une demande de reconnaissance vocale, le modèle de base le plus récent pour chaque langue prise en charge est utilisé par défaut. Le modèle de base fonctionne très bien dans la plupart des scénarios de reconnaissance vocale. A custom model can be used to augment the base model to improve recognition of domain specific vocabulary specified to the application by providing text data to train the model. It can also be used to improve recognition based for the specific audio conditions of the application by providing audio data with reference transcriptions."
        }
    ],
    "phrases": [
        {
            "offsetMilliseconds": 80,
            "durationMilliseconds": 6960,
            "text": "With custom speech,you can evaluate and improve the microsoft speech to text accuracy for your applications and products.",
            "words": [
                {
                    "text": "with",
                    "offsetMilliseconds": 80,
                    "durationMilliseconds": 160
                },
                {
                    "text": "custom",
                    "offsetMilliseconds": 240,
                    "durationMilliseconds": 480
                },
                {
                    "text": "speech",
                    "offsetMilliseconds": 720,
                    "durationMilliseconds": 360
                },
                {
                    "text": ",",
                    "offsetMilliseconds": 1080,
                    "durationMilliseconds": 10
                },
                {
                    "text": "you",
                    "offsetMilliseconds": 1200,
                    "durationMilliseconds": 240
                },
                {
                    "text": "can",
                    "offsetMilliseconds": 1440,
                    "durationMilliseconds": 160
                },
                {
                    "text": "evaluate",
                    "offsetMilliseconds": 1600,
                    "durationMilliseconds": 640
                },
                {
                    "text": "and",
                    "offsetMilliseconds": 2240,
                    "durationMilliseconds": 200
                },
                {
                    "text": "improve",
                    "offsetMilliseconds": 2440,
                    "durationMilliseconds": 280
                },
                {
                    "text": "the",
                    "offsetMilliseconds": 2720,
                    "durationMilliseconds": 160
                },
                {
                    "text": "microsoft",
                    "offsetMilliseconds": 2880,
                    "durationMilliseconds": 640
                },
                {
                    "text": "speech",
                    "offsetMilliseconds": 3520,
                    "durationMilliseconds": 320
                },
                {
                    "text": "to",
                    "offsetMilliseconds": 3840,
                    "durationMilliseconds": 200
                },
                {
                    "text": "text",
                    "offsetMilliseconds": 4040,
                    "durationMilliseconds": 360
                },
                {
                    "text": "accuracy",
                    "offsetMilliseconds": 4400,
                    "durationMilliseconds": 560
                },
                {
                    "text": "for",
                    "offsetMilliseconds": 4960,
                    "durationMilliseconds": 160
                },
                {
                    "text": "your",
                    "offsetMilliseconds": 5120,
                    "durationMilliseconds": 200
                },
                {
                    "text": "applications",
                    "offsetMilliseconds": 5320,
                    "durationMilliseconds": 760
                },
                {
                    "text": "and",
                    "offsetMilliseconds": 6080,
                    "durationMilliseconds": 200
                },
                {
                    "text": "products",
                    "offsetMilliseconds": 6280,
                    "durationMilliseconds": 680
                },
            ],
            "locale": "en-us",
            "confidence": 0.9539559
        },
        {
            "offsetMilliseconds": 8000,
            "durationMilliseconds": 8600,
            "text": "现成的语音转文本,利用通用语言模型作为一个基本模型,使用microsoft自有数据进行训练,并反映常用的口语。此基础模型使用那些代表各常见领域的方言和发音进行了预先训练。",
            "words": [
                {
                    "text": "现",
                    "offsetMilliseconds": 8000,
                    "durationMilliseconds": 40
                },
                {
                    "text": "成",
                    "offsetMilliseconds": 8040,
                    "durationMilliseconds": 40
                },
                {
                    "text": "的",
                    "offsetMilliseconds": 8160,
                    "durationMilliseconds": 40
                },
                {
                    "text": "语",
                    "offsetMilliseconds": 8200,
                    "durationMilliseconds": 40
                },
                {
                    "text": "音",
                    "offsetMilliseconds": 8240,
                    "durationMilliseconds": 40
                },
                {
                    "text": "转",
                    "offsetMilliseconds": 8280,
                    "durationMilliseconds": 40
                },
                {
                    "text": "文",
                    "offsetMilliseconds": 8320,
                    "durationMilliseconds": 40
                },
                {
                    "text": "本,",
                    "offsetMilliseconds": 8360,
                    "durationMilliseconds": 40
                },
                {
                    "text": "利",
                    "offsetMilliseconds": 8400,
                    "durationMilliseconds": 40
                },
                {
                    "text": "用",
                    "offsetMilliseconds": 8440,
                    "durationMilliseconds": 40
                },
                {
                    "text": "通",
                    "offsetMilliseconds": 8480,
                    "durationMilliseconds": 40
                },
                {
                    "text": "用",
                    "offsetMilliseconds": 8520,
                    "durationMilliseconds": 40
                },
                {
                    "text": "语",
                    "offsetMilliseconds": 8560,
                    "durationMilliseconds": 40
                },
                {
                    "text": "言",
                    "offsetMilliseconds": 8600,
                    "durationMilliseconds": 40
                },
                {
                    "text": "模",
                    "offsetMilliseconds": 8640,
                    "durationMilliseconds": 40
                },
                {
                    "text": "型",
                    "offsetMilliseconds": 8680,
                    "durationMilliseconds": 40
                },
                {
                    "text": "作",
                    "offsetMilliseconds": 8800,
                    "durationMilliseconds": 40
                },
                {
                    "text": "为",
                    "offsetMilliseconds": 8840,
                    "durationMilliseconds": 40
                },
                {
                    "text": "一",
                    "offsetMilliseconds": 9520,
                    "durationMilliseconds": 40
                },
                {
                    "text": "个",
                    "offsetMilliseconds": 9560,
                    "durationMilliseconds": 40
                },
                {
                    "text": "基",
                    "offsetMilliseconds": 9600,
                    "durationMilliseconds": 40
                },
                {
                    "text": "本",
                    "offsetMilliseconds": 9640,
                    "durationMilliseconds": 40
                },
                {
                    "text": "模",
                    "offsetMilliseconds": 9680,
                    "durationMilliseconds": 40
                },
                {
                    "text": "型,",
                    "offsetMilliseconds": 9720,
                    "durationMilliseconds": 40
                },
                {
                    "text": "使",
                    "offsetMilliseconds": 9760,
                    "durationMilliseconds": 40
                },
                {
                    "text": "用",
                    "offsetMilliseconds": 10080,
                    "durationMilliseconds": 320
                },
                {
                    "text": "microsoft",
                    "offsetMilliseconds": 10400,
                    "durationMilliseconds": 3600
                },
                {
                    "text": "自",
                    "offsetMilliseconds": 14000,
                    "durationMilliseconds": 40
                },
                {
                    "text": "有",
                    "offsetMilliseconds": 14040,
                    "durationMilliseconds": 40
                },
                {
                    "text": "数",
                    "offsetMilliseconds": 14160,
                    "durationMilliseconds": 40
                },
                {
                    "text": "据",
                    "offsetMilliseconds": 14200,
                    "durationMilliseconds": 40
                },
                {
                    "text": "进",
                    "offsetMilliseconds": 14320,
                    "durationMilliseconds": 40
                },
                {
                    "text": "行",
                    "offsetMilliseconds": 14360,
                    "durationMilliseconds": 40
                },
                {
                    "text": "训",
                    "offsetMilliseconds": 14400,
                    "durationMilliseconds": 40
                },
                {
                    "text": "练,",
                    "offsetMilliseconds": 14440,
                    "durationMilliseconds": 40
                },
                {
                    "text": "并",
                    "offsetMilliseconds": 14480,
                    "durationMilliseconds": 40
                },
                {
                    "text": "反",
                    "offsetMilliseconds": 14520,
                    "durationMilliseconds": 40
                },
                {
                    "text": "映",
                    "offsetMilliseconds": 14560,
                    "durationMilliseconds": 40
                },
                {
                    "text": "常",
                    "offsetMilliseconds": 14600,
                    "durationMilliseconds": 40
                },
                {
                    "text": "用",
                    "offsetMilliseconds": 14640,
                    "durationMilliseconds": 40
                },
                {
                    "text": "的",
                    "offsetMilliseconds": 14680,
                    "durationMilliseconds": 40
                },
                {
                    "text": "口",
                    "offsetMilliseconds": 14720,
                    "durationMilliseconds": 40
                },
                {
                    "text": "语",
                    "offsetMilliseconds": 14760,
                    "durationMilliseconds": 40
                },
                {
                    "text": "。",
                    "offsetMilliseconds": 14800,
                    "durationMilliseconds": 40
                },
                {
                    "text": "此",
                    "offsetMilliseconds": 14840,
                    "durationMilliseconds": 40
                },
                {
                    "text": "基",
                    "offsetMilliseconds": 14880,
                    "durationMilliseconds": 40
                },
                {
                    "text": "础",
                    "offsetMilliseconds": 14920,
                    "durationMilliseconds": 40
                },
                {
                    "text": "模",
                    "offsetMilliseconds": 14960,
                    "durationMilliseconds": 40
                },
                {
                    "text": "型",
                    "offsetMilliseconds": 15000,
                    "durationMilliseconds": 40
                },
                {
                    "text": "使",
                    "offsetMilliseconds": 15040,
                    "durationMilliseconds": 40
                },
                {
                    "text": "用",
                    "offsetMilliseconds": 15080,
                    "durationMilliseconds": 40
                },
                {
                    "text": "那",
                    "offsetMilliseconds": 15120,
                    "durationMilliseconds": 40
                },
                {
                    "text": "些",
                    "offsetMilliseconds": 15160,
                    "durationMilliseconds": 40
                },
                {
                    "text": "代",
                    "offsetMilliseconds": 15200,
                    "durationMilliseconds": 40
                },
                {
                    "text": "表",
                    "offsetMilliseconds": 15240,
                    "durationMilliseconds": 40
                },
                {
                    "text": "各",
                    "offsetMilliseconds": 15280,
                    "durationMilliseconds": 40
                },
                {
                    "text": "常",
                    "offsetMilliseconds": 15320,
                    "durationMilliseconds": 40
                },
                {
                    "text": "见",
                    "offsetMilliseconds": 15360,
                    "durationMilliseconds": 40
                },
                {
                    "text": "领",
                    "offsetMilliseconds": 15400,
                    "durationMilliseconds": 40
                },
                {
                    "text": "域",
                    "offsetMilliseconds": 15760,
                    "durationMilliseconds": 40
                },
                {
                    "text": "的",
                    "offsetMilliseconds": 15800,
                    "durationMilliseconds": 40
                },
                {
                    "text": "方",
                    "offsetMilliseconds": 15920,
                    "durationMilliseconds": 40
                },
                {
                    "text": "言",
                    "offsetMilliseconds": 15960,
                    "durationMilliseconds": 40
                },
                {
                    "text": "和",
                    "offsetMilliseconds": 16000,
                    "durationMilliseconds": 40
                },
                {
                    "text": "发",
                    "offsetMilliseconds": 16040,
                    "durationMilliseconds": 40
                },
                {
                    "text": "音",
                    "offsetMilliseconds": 16080,
                    "durationMilliseconds": 40
                },
                {
                    "text": "进",
                    "offsetMilliseconds": 16120,
                    "durationMilliseconds": 40
                },
                {
                    "text": "行",
                    "offsetMilliseconds": 16160,
                    "durationMilliseconds": 40
                },
                {
                    "text": "了",
                    "offsetMilliseconds": 16200,
                    "durationMilliseconds": 40
                },
                {
                    "text": "预",
                    "offsetMilliseconds": 16320,
                    "durationMilliseconds": 40
                },
                {
                    "text": "先",
                    "offsetMilliseconds": 16360,
                    "durationMilliseconds": 40
                },
                {
                    "text": "训",
                    "offsetMilliseconds": 16400,
                    "durationMilliseconds": 40
                },
                {
                    "text": "练",
                    "offsetMilliseconds": 16560,
                    "durationMilliseconds": 40
                },
            ],
            "locale": "zh-cn",
            "confidence": 0.9241725
        },
        {
            "offsetMilliseconds": 24320,
            "durationMilliseconds": 6640,
            "text": "Quand vous effectuez une demande de reconnaissance vocale, le modèle de base le plus récent pour chaque langue prise en charge est utilisé par défaut.",
            "words": [
                {
                    "text": "Quand",
                    "offsetMilliseconds": 24320,
                    "durationMilliseconds": 160
                },
                {
                    "text": "vous",
                    "offsetMilliseconds": 24480,
                    "durationMilliseconds": 80
                },
        // More transcription results...
        // Redacted for brevity
                {
                    "text": "scénarios",
                    "offsetMilliseconds": 34200,
                    "durationMilliseconds": 400
                },
                {
                    "text": "de",
                    "offsetMilliseconds": 34600,
                    "durationMilliseconds": 120
                },
                {
                    "text": "reconnaissance",
                    "offsetMilliseconds": 34720,
                    "durationMilliseconds": 640
                },
                {
                    "text": "vocale.",
                    "offsetMilliseconds": 35360,
                    "durationMilliseconds": 480
                }
            ],
            "locale": "fr-fr",
            "confidence": 0.9308314
        },
        {
            "offsetMilliseconds": 36720,
            "durationMilliseconds": 10320,
            "text": "A custom model can be used to augment the base model to improve recognition of domain specific vocabulary spécifique to the application by providing text data to train the model.",
            "words": [
                {
                    "text": "A",
                    "offsetMilliseconds": 36720,
                    "durationMilliseconds": 80
                },
                {
                    "text": "custom",
                    "offsetMilliseconds": 36880,
                    "durationMilliseconds": 400
                },
                {
                    "text": "model",
                    "offsetMilliseconds": 37280,
                    "durationMilliseconds": 480
                },

        // More transcription results...
        // Redacted for brevity
                {
                    "text": "with",
                    "offsetMilliseconds": 54720,
                    "durationMilliseconds": 200
                },
                {
                    "text": "reference",
                    "offsetMilliseconds": 54920,
                    "durationMilliseconds": 360
                },
                {
                    "text": "transcriptions.",
                    "offsetMilliseconds": 55280,
                    "durationMilliseconds": 1200
                }
            ],
            "locale": "en-us",
            "confidence": 0.92155737
        }
    ]
}
```

# [Diarization on](#tab/diarization-on)
Make a multipart/form-data POST request to the `transcriptions` endpoint with the audio file and the request body properties.

The following example shows how to transcribe an audio file with diarization enabled. Diarization distinguishes between different speakers in the conversation. The Speech service provides information about which speaker was speaking a particular part of the transcribed speech.

- Replace `YourSpeechResoureKey` with your Speech resource key.
- Replace `YourServiceRegion` with your Speech resource region.
- Replace `YourAudioFile` with the path to your audio file.

Note

When diarization is enabled, the audio file should be less than 2 hours long

Important

For the recommended keyless authentication with Microsoft Entra ID, replace `--header 'Ocp-Apim-Subscription-Key: YourSpeechResoureKey'` with `--header "Authorization: Bearer YourAccessToken"`. For more information about keyless authentication, see the [role-based access control](role-based-access-control#authentication-with-keys-and-tokens) how-to guide.

```azurecli
curl --location 'https://YourServiceRegion.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2025-10-15' \
--header 'Content-Type: multipart/form-data' \
--header 'Ocp-Apim-Subscription-Key: YourSpeechResoureKey' \
--form 'audio=@"YourAudioFile"' \
--form 'definition="{
    "locales":["en-US"], 
    "diarization": {"maxSpeakers": 2,"enabled": true}}"'
```

Construct the form definition according to the following instructions:

1. Set the optional (but recommended) `locales` property that should match the expected locale of the audio data to transcribe. In this example, the locale is set to `en-US`.
2. Set the `diarization` property to recognize and separate multiple speakers in one audio channel. For example, specify `"diarization": {"maxSpeakers": 2, "enabled": true}`. Then the transcription file contains `speaker` entries for each transcribed phrase.

For more information about `locales`, `diarization`, and other properties for the fast transcription API, see the request configuration options section later in this guide.

The response includes `durationMilliseconds`, `offsetMilliseconds`, and more. In this example, diarization is enabled, so the response includes `speaker` information for each transcribed phrase. The `combinedPhrases` property contains the full transcriptions for all speakers in a single channel.

```json
{
    "durationMilliseconds": 182439,
    "combinedPhrases": [
        {
            "channel": 0,
            "text": "Good afternoon. This is Sam. Thank you for calling Contoso. How can I help? Hi there. My name is Mary. I'm currently living in Los Angeles, but I'm planning to move to Las Vegas. I would like to apply for a loan. Okay. I see you're currently living in California. Let me make sure I understand you correctly. Uh You'd like to apply for a loan even though you'll be moving soon. Is that right? Yes, exactly. So I'm planning to relocate soon, but I would like to apply for the loan first so that I can purchase a new home once I move there. And are you planning to sell your current home? Yes, I will be listing it on the market soon and hopefully it'll sell quickly. That's why I'm applying for a loan now, so that I can purchase a new house in Nevada and close on it quickly as well once my current home sells. I see. Would you mind holding for a moment while I take your information down? Yeah, no problem. Thank you for your help. Mm-hmm. Just one moment. All right. Thank you for your patience, ma'am. May I have your first and last name, please? Yes, my name is Mary Smith. Thank you, Ms. Smith. May I have your current address, please? Yes. So my address is 123 Main Street in Los Angeles, California, and the zip code is 90923. Sorry, that was a 90 what? 90923. 90923 on Main Street. Got it. Thank you. May I have your phone number as well, please? Uh. Yes, my phone number is 504-529-2351 and then yeah. 2351. Got it. And do you have an e-mail address we I can associate with this application? Uh Yes, so my e-mail address is mary.a.sm78@gmail.com. Mary.a, was that a S-N as in November or M as in Mike? M as in Mike. Mike78, got it. Thank you. Ms. Smith, do you currently have any other loans? Uh Yes, so I currently have two other loans through Contoso. So my first one is my car loan and then my other is my student loan. They total about 1400 per month combined and my interest rate is 8%. I see. And. You're currently paying those loans off monthly, is that right? Yes, of course I do. OK, thank you. Here's what I suggest we do. Let me place you on a brief hold again so that I can talk with one of our loan officers and get this started for you immediately. In the meantime, it would be great if you could take a few minutes and complete the remainder of the secure application online at www.contosoloans.com. Yeah, that sounds good. I can go ahead and get started. Thank you for your help. Thank you."
        }
    ],
    "phrases": [
        {
            "channel": 0,
            "speaker": 1,
            "offsetMilliseconds": 960,
            "durationMilliseconds": 640,
            "text": "Good afternoon.",
            "words": [
                {
                    "text": "Good",
                    "offsetMilliseconds": 960,
                    "durationMilliseconds": 240
                },
                {
                    "text": "afternoon.",
                    "offsetMilliseconds": 1200,
                    "durationMilliseconds": 400
                }
            ],
            "locale": "en-US",
            "confidence": 0.93616915
        },
        {
            "channel": 0,
            "speaker": 1,
            "offsetMilliseconds": 1600,
            "durationMilliseconds": 640,
            "text": "This is Sam.",
            "words": [
                {
                    "text": "This",
                    "offsetMilliseconds": 1600,
                    "durationMilliseconds": 240
                },
                {
                    "text": "is",
                    "offsetMilliseconds": 1840,
                    "durationMilliseconds": 120
                },
                {
                    "text": "Sam.",
                    "offsetMilliseconds": 1960,
                    "durationMilliseconds": 280
                }
            ],
            "locale": "en-US",
            "confidence": 0.93616915
        },
        {
            "channel": 0,
            "speaker": 1,
            "offsetMilliseconds": 2240,
            "durationMilliseconds": 1040,
            "text": "Thank you for calling Contoso.",
            "words": [
                {
                    "text": "Thank",
                    "offsetMilliseconds": 2240,
                    "durationMilliseconds": 200
                },
                {
                    "text": "you",
                    "offsetMilliseconds": 2440,
                    "durationMilliseconds": 80
                },
                {
                    "text": "for",
                    "offsetMilliseconds": 2520,
                    "durationMilliseconds": 120
                },
                {
                    "text": "calling",
                    "offsetMilliseconds": 2640,
                    "durationMilliseconds": 200
                },
                {
                    "text": "Contoso.",
                    "offsetMilliseconds": 2840,
                    "durationMilliseconds": 440
                }
            ],
            "locale": "en-US",
            "confidence": 0.93616915
        },
        {
            "channel": 0,
            "speaker": 1,
            "offsetMilliseconds": 3280,
            "durationMilliseconds": 640,
            "text": "How can I help?",
            "words": [
                {
                    "text": "How",
                    "offsetMilliseconds": 3280,
                    "durationMilliseconds": 120
                },
                {
                    "text": "can",
                    "offsetMilliseconds": 3440,
                    "durationMilliseconds": 120
                },
                {
                    "text": "I",
                    "offsetMilliseconds": 3560,
                    "durationMilliseconds": 40
                },
                {
                    "text": "help?",
                    "offsetMilliseconds": 3600,
                    "durationMilliseconds": 320
                }
            ],
            "locale": "en-US",
            "confidence": 0.93616915
        },
        {
            "channel": 0,
            "speaker": 0,
            "offsetMilliseconds": 5040,
            "durationMilliseconds": 400,
            "text": "Hi there.",
            "words": [
                {
                    "text": "Hi",
                    "offsetMilliseconds": 5040,
                    "durationMilliseconds": 240
                },
                {
                    "text": "there.",
                    "offsetMilliseconds": 5280,
                    "durationMilliseconds": 160
                }
            ],
            "locale": "en-US",
            "confidence": 0.93616915
        },
        {
            "channel": 0,
            "speaker": 0,
            "offsetMilliseconds": 5440,
            "durationMilliseconds": 800,
            "text": "My name is Mary.",
            "words": [
                {
                    "text": "My",
                    "offsetMilliseconds": 5440,
                    "durationMilliseconds": 80
                },
                {
                    "text": "name",
                    "offsetMilliseconds": 5520,
                    "durationMilliseconds": 120
                },
                {
                    "text": "is",
                    "offsetMilliseconds": 5640,
                    "durationMilliseconds": 80
                },
                {
                    "text": "Mary.",
                    "offsetMilliseconds": 5720,
                    "durationMilliseconds": 520
                }
            ],
            "locale": "en-US",
            "confidence": 0.93616915
        },
        // More transcription results...
        // Redacted for brevity
        {
            "channel": 0,
            "speaker": 0,
            "offsetMilliseconds": 180320,
            "durationMilliseconds": 680,
            "text": "Thank you for your help.",
            "words": [
                {
                    "text": "Thank",
                    "offsetMilliseconds": 180320,
                    "durationMilliseconds": 160
                },
                {
                    "text": "you",
                    "offsetMilliseconds": 180480,
                    "durationMilliseconds": 80
                },
                {
                    "text": "for",
                    "offsetMilliseconds": 180560,
                    "durationMilliseconds": 120
                },
                {
                    "text": "your",
                    "offsetMilliseconds": 180680,
                    "durationMilliseconds": 120
                },
                {
                    "text": "help.",
                    "offsetMilliseconds": 180800,
                    "durationMilliseconds": 200
                }
            ],
            "locale": "en-US",
            "confidence": 0.9314801
        },
        {
            "channel": 0,
            "speaker": 1,
            "offsetMilliseconds": 181960,
            "durationMilliseconds": 280,
            "text": "Thank you.",
            "words": [
                {
                    "text": "Thank",
                    "offsetMilliseconds": 181960,
                    "durationMilliseconds": 200
                },
                {
                    "text": "you.",
                    "offsetMilliseconds": 182160,
                    "durationMilliseconds": 80
                }
            ],
            "locale": "en-US",
            "confidence": 0.9314801
        }
    ]
}
```

# [Multi-channel on](#tab/multi-channel)
Make a multipart/form-data POST request to the `transcriptions` endpoint with the audio file and the request body properties.

The following example shows how to transcribe an audio file that has one or two channels. Multi-channel transcriptions are useful for audio files with multiple channels, such as audio files with multiple speakers or audio files with background noise. By default, the fast transcription API merges all input channels into a single channel and then performs the transcription. If this isn't desirable, channels can be transcribed independently without merging.

- Replace `YourSpeechResoureKey` with your Speech resource key.
- Replace `YourServiceRegion` with your Speech resource region.
- Replace `YourAudioFile` with the path to your audio file.

Important

For the recommended keyless authentication with Microsoft Entra ID, replace `--header 'Ocp-Apim-Subscription-Key: YourSpeechResoureKey'` with `--header "Authorization: Bearer YourAccessToken"`. For more information about keyless authentication, see the [role-based access control](role-based-access-control#authentication-with-keys-and-tokens) how-to guide.

```azurecli
curl --location 'https://YourServiceRegion.api.cognitive.microsoft.com/speechtotext/transcriptions:transcribe?api-version=2025-10-15' \
--header 'Content-Type: multipart/form-data' \
--header 'Ocp-Apim-Subscription-Key: YourSpeechResoureKey' \
--form 'audio=@"YourAudioFile"' \
--form 'definition="{
    "locales":["en-US"], 
    "channels": [0,1]}"'
```

Construct the form definition according to the following instructions:

1. Set the optional (but recommended) `locales` property that should match the expected locale of the audio data to transcribe. In this example, the locale is set to `en-US`. The supported locales that you can specify are: de-DE, en-GB, en-IN, en-US, es-ES, es-MX, fr-FR, hi-IN, it-IT, ja-JP, ko-KR, pt-BR, and zh-CN.
2. Set the `channels` property to specify the zero-based indices of the channels to be transcribed separately. Up to two channels are supported unless diarization is enabled. In this example, channels 0 and 1 are specified.

For more information about `locales`, `channels`, and other properties for the fast transcription API, see the request configuration options section later in this guide.

The response includes `durationMilliseconds`, `offsetMilliseconds`, and more. The `channel` property identifies the channel if the audio file contains multiple channels. The `combinedPhrases` property contains full transcriptions separate per audio channel. Look for `"channel": 0,"text"` and `"channel": 1,"text"` to identify the full transcriptions for each channel.

```json
{
    "durationMilliseconds": 185079,
    "combinedPhrases": [
        {
            "channel": 0,
            "text": "Hello. Thank you for calling Contoso. Who am I speaking with today? Hi, Mary. Are you calling because you need health insurance? Great. If you can answer a few questions, we can get you signed up in the Jiffy. So what's your full name? Got it. And what's the best callback number in case we get disconnected? Yep, that'll be fine. Got it. So to confirm, it's 234-554-9312. Excellent. Let's get some additional information for your application. Do you have a job? OK, so then you have a Social Security number as well. OK, and what is your Social Security number please? Sorry, what was that, a 25 or a 225? You cut out for a bit. Alright, thank you so much. And could I have your e-mail address please? Great. Uh That is the last question. So let me take your information and I'll be able to get you signed up right away. Thank you for calling Contoso and I'll be able to get you signed up immediately. One of our agents will call you back in about 24 hours or so to confirm your application. Absolutely. If you need anything else, please give us a call at 1-800-555-5564, extension 123. Thank you very much for calling Contoso. Uh Yes, of course. So the default is a digital membership card, but we can send you a physical card if you prefer. Uh, yeah. Absolutely. I've made a note on your file. You're very welcome. Thank you for calling Contoso and have a great day."
        },
        {
            "channel": 1,
            "text": "Hi, my name is Mary Rondo. I'm trying to enroll myself with Contuso. Yes, yeah, I'm calling to sign up for insurance. Okay. So Mary Beth Rondo, last name is R like Romeo, O like Ocean, N like Nancy D, D like Dog, and O like Ocean again. Rondo. I only have a cell phone so I can give you that. Sure, so it's 234-554 and then 9312. Yep, that's right. Uh Yes, I am self-employed. Yes, I do. Uh Sure, so it's 412256789. It's double two, so 412, then another two, then five. Yeah, it's maryrondo@gmail.com. So my first and last name at gmail.com. No periods, no dashes. That was quick. Thank you. Actually, so I have one more question. I'm curious, will I be getting a physical card as proof of coverage? uh Yes. Could you please mail it to me when it's ready? I'd like to have it shipped to, are you ready for my address? So it's 2660 Unit A on Maple Avenue SE, Lansing, and then zip code is 48823. Awesome. Thanks so much."
        }
    ],
    "phrases": [
        {
            "channel": 0,
            "offsetMilliseconds": 720,
            "durationMilliseconds": 480,
            "text": "Hello.",
            "words": [
                {
                    "text": "Hello.",
                    "offsetMilliseconds": 720,
                    "durationMilliseconds": 480
                }
            ],
            "locale": "en-US",
            "confidence": 0.9177142
        },
        {
            "channel": 0,
            "offsetMilliseconds": 1200,
            "durationMilliseconds": 1120,
            "text": "Thank you for calling Contoso.",
            "words": [
                {
                    "text": "Thank",
                    "offsetMilliseconds": 1200,
                    "durationMilliseconds": 200
                },
                {
                    "text": "you",
                    "offsetMilliseconds": 1400,
                    "durationMilliseconds": 80
                },
                {
                    "text": "for",
                    "offsetMilliseconds": 1480,
                    "durationMilliseconds": 120
                },
                {
                    "text": "calling",
                    "offsetMilliseconds": 1600,
                    "durationMilliseconds": 240
                },
                {
                    "text": "Contoso.",
                    "offsetMilliseconds": 1840,
                    "durationMilliseconds": 480
                }
            ],
            "locale": "en-US",
            "confidence": 0.9177142
        },
        {
            "channel": 0,
            "offsetMilliseconds": 2320,
            "durationMilliseconds": 1120,
            "text": "Who am I speaking with today?",
            "words": [
                {
                    "text": "Who",
                    "offsetMilliseconds": 2320,
                    "durationMilliseconds": 160
                },
                {
                    "text": "am",
                    "offsetMilliseconds": 2480,
                    "durationMilliseconds": 80
                },
                {
                    "text": "I",
                    "offsetMilliseconds": 2560,
                    "durationMilliseconds": 80
                },
                {
                    "text": "speaking",
                    "offsetMilliseconds": 2640,
                    "durationMilliseconds": 320
                },
                {
                    "text": "with",
                    "offsetMilliseconds": 2960,
                    "durationMilliseconds": 160
                },
                {
                    "text": "today?",
                    "offsetMilliseconds": 3120,
                    "durationMilliseconds": 320
                }
            ],
            "locale": "en-US",
            "confidence": 0.9177142
        },
        {
            "channel": 0,
            "offsetMilliseconds": 9520,
            "durationMilliseconds": 400,
            "text": "Hi, Mary.",
            "words": [
                {
                    "text": "Hi,",
                    "offsetMilliseconds": 9520,
                    "durationMilliseconds": 80
                },
                {
                    "text": "Mary.",
                    "offsetMilliseconds": 9600,
                    "durationMilliseconds": 320
                }
            ],
            "locale": "en-US",
            "confidence": 0.9177142
        },
        // More transcription results...
        // Redacted for brevity
        {
            "channel": 1,
            "offsetMilliseconds": 4480,
            "durationMilliseconds": 1600,
            "text": "Hi, my name is Mary Rondo.",
            "words": [
                {
                    "text": "Hi,",
                    "offsetMilliseconds": 4480,
                    "durationMilliseconds": 400
                },
                {
                    "text": "my",
                    "offsetMilliseconds": 4880,
                    "durationMilliseconds": 120
                },
                {
                    "text": "name",
                    "offsetMilliseconds": 5000,
                    "durationMilliseconds": 120
                },
                {
                    "text": "is",
                    "offsetMilliseconds": 5120,
                    "durationMilliseconds": 160
                },
                {
                    "text": "Mary",
                    "offsetMilliseconds": 5280,
                    "durationMilliseconds": 240
                },
                {
                    "text": "Rondo.",
                    "offsetMilliseconds": 5520,
                    "durationMilliseconds": 560
                }
            ],
            "locale": "en-US",
            "confidence": 0.8989456
        },
        {
            "channel": 1,
            "offsetMilliseconds": 6080,
            "durationMilliseconds": 1920,
            "text": "I'm trying to enroll myself with Contuso.",
            "words": [
                {
                    "text": "I'm",
                    "offsetMilliseconds": 6080,
                    "durationMilliseconds": 160
                },
                {
                    "text": "trying",
                    "offsetMilliseconds": 6240,
                    "durationMilliseconds": 200
                },
                {
                    "text": "to",
                    "offsetMilliseconds": 6440,
                    "durationMilliseconds": 80
                },
                {
                    "text": "enroll",
                    "offsetMilliseconds": 6520,
                    "durationMilliseconds": 200
                },
                {
                    "text": "myself",
                    "offsetMilliseconds": 6720,
                    "durationMilliseconds": 360
                },
                {
                    "text": "with",
                    "offsetMilliseconds": 7080,
                    "durationMilliseconds": 120
                },
                {
                    "text": "Contuso.",
                    "offsetMilliseconds": 7200,
                    "durationMilliseconds": 800
                }
            ],
            "locale": "en-US",
            "confidence": 0.8989456
        },
        // More transcription results...
        // Redacted for brevity
    ]
}
```

---

Note

Speech service is an elastic service. If you receive 429 error code (too many requests), please follow the [best practices to mitigate throttling during autoscaling](speech-services-quotas-and-limits#general-best-practices-to-mitigate-throttling-during-autoscaling).

## Request configuration options

Here are some property options to configure a transcription when you call the [Transcriptions - Transcribe](/en-us/rest/api/speechtotext/transcriptions/transcribe) operation.

| Property | Description | Required or optional |
| --- | --- | --- |
| `channels` | The list of zero-based indices of the channels to be transcribed separately. Up to two channels are supported unless diarization is enabled. By default, the fast transcription API merges all input channels into a single channel and then performs the transcription. If this isn't desirable, channels can be transcribed independently without merging.If you want to transcribe the channels from a stereo audio file separately, you need to specify `[0,1]`, `[0]`, or `[1]`. Otherwise, stereo audio is merged to mono and only a single channel is transcribed.If the audio is stereo and diarization is enabled, then you can't set the `channels` property to `[0,1]`. The Speech service doesn't support diarization of multiple channels.For mono audio, the `channels` property is ignored, and the audio is always transcribed as a single channel. | Optional |
| `diarization` | The diarization configuration. Diarization is the process of recognizing and separating multiple speakers in one audio channel. For example, specify `"diarization": {"maxSpeakers": 2, "enabled": true}`. Then the transcription file contains `speaker` entries (such as `"speaker": 0` or `"speaker": 1`) for each transcribed phrase. | Optional |
| `locales` | The list of locales that should match the expected locale of the audio data to transcribe.If you know the locale of the audio file, you can specify it to improve transcription accuracy and minimize the latency. If a single locale is specified, that locale is used for transcription.But if you're not sure about the locale, you can specify multiple locales to use language identification. Language identification might be more accurate with a more precise list of candidate locales.If you don't specify any locale, then the Speech service will use the latest multi-lingual model to identify the locale and transcribe continuously. You can get the latest supported languages via the [Transcriptions - List Supported Locales](/en-us/rest/api/speechtotext/transcriptions/list-supported-locales) REST API (API version 2024-11-15 or later). For more information about locales, see the [Speech service language support](language-support?tabs=stt) documentation. | Optional but recommended if you know the expected locale. |
| `phraseList` | Phrase list is a list of words or phrases provided ahead of time to help improve their recognition. Adding a phrase to a phrase list increases its importance, thus making it more likely to be recognized. For example, specify `phraseList":{"phrases":["Contoso","Jessie","Rehaan"]}`. Phrase List is supported via API version 2025-10-15. For more information, see [Improve recognition accuracy with phrase list](improve-accuracy-phrase-list#implement-phrase-list-in-fast-transcription). | Optional |
| `profanityFilterMode` | Specifies how to handle profanity in recognition results. Accepted values are `None` to disable profanity filtering, `Masked` to replace profanity with asterisks, `Removed` to remove all profanity from the result, or `Tags` to add profanity tags. The default value is `Masked`. | Optional |

::: zone-end

::: zone pivot="programming-language-python"

[Reference documentation](/en-us/python/api/overview/azure/ai-transcription-readme) | [Package (PyPi)](https://pypi.org/project/azure-ai-transcription/) | [GitHub Samples](https://github.com/Azure/azure-sdk-for-python/tree/azure-ai-transcription_1.0.0b2/sdk/cognitiveservices/azure-ai-transcription/samples)

## Prerequisites

- An Azure subscription. [Create one for free](https://azure.microsoft.com/pricing/purchase-options/azure-account?cid=msft_learn).
- [Python 3.9 or later version](https://www.python.org/). If you don't have a suitable version of Python installed, you can follow the instructions in the [VS Code Python Tutorial](https://code.visualstudio.com/docs/python/python-tutorial#_install-a-python-interpreter) for the easiest way of installing Python on your operating system.
- A [Microsoft Foundry resource](/en-us/azure/ai-services/multi-service-resource) created in one of the supported regions. For more information about region availability, see [Region support](/en-us/azure/ai-services/speech-service/regions?tabs=stt).
- A sample `.wav` audio file to transcribe.

### Microsoft Entra ID prerequisites

For the recommended keyless authentication with Microsoft Entra ID, you need to:

- Install the [Azure CLI](/en-us/cli/azure/install-azure-cli) used for keyless authentication with Microsoft Entra ID.
- Assign the `Cognitive Services User` role to your user account. You can assign roles in the Azure portal under **Access control (IAM)** &gt; **Add role assignment**.

## Setup

1. Create a new folder named `transcription-quickstart` and go to the quickstart folder with the following command:

    ```shell
    mkdir transcription-quickstart && cd transcription-quickstart
    ```
2. Create and activate a virtual Python environment to install the packages you need for this tutorial. We recommend you always use a virtual or conda environment when installing Python packages. Otherwise, you can break your global installation of Python. If you already have Python 3.9 or higher installed, create a virtual environment by using the following commands:

# [Windows](#tab/windows)
```powershell
    py -3 -m venv .venv
    .venv\Scripts\Activate.ps1
    ```

# [Linux](#tab/linux)
```bash
    python3 -m venv .venv
    source .venv/bin/activate
    ```

# [macOS](#tab/macos)
```bash
    python3 -m venv .venv
    source .venv/bin/activate
    ```

---

    When you activate the Python environment, running `python` or `pip` from the command line uses the Python interpreter in the `.venv` folder of your application. Use the `deactivate` command to exit the Python virtual environment. You can reactivate it later when needed.
3. Create a file named **requirements.txt**. Add the following packages to the file:

    ```txt
    azure-ai-transcription
    azure-identity
    ```
4. Install the packages:

    ```bash
    pip install -r requirements.txt
    ```

## Retrieve resource information

You need to retrieve your resource endpoint and API key for authentication.

1. Sign in to [Foundry portal](https://ai.azure.com).
2. Select **Management center** from the left menu. Under **Connected resources**, select your Speech or multi-service resource.
3. Select **Keys and Endpoint**.
4. Copy the **Endpoint** and **Key** values. Use these values to set environment variables.
5. Set the following environment variables:

# [Windows](#tab/windows)
```powershell
    $env:AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
    $env:AZURE_SPEECH_API_KEY="<your-api-key>"
    ```

# [Linux](#tab/linux)
```bash
    export AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
    export AZURE_SPEECH_API_KEY="<your-api-key>"
    ```

# [macOS](#tab/macos)
```bash
    export AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
    export AZURE_SPEECH_API_KEY="<your-api-key>"
    ```

---

Note

For Microsoft Entra ID authentication (recommended for production), install `azure-identity` and configure authentication as described in the Microsoft Entra ID prerequisites section.

## Code

1. Create a file named `transcribe_audio_file.py` with the following code:

    ```python
    import os
    from azure.core.credentials import AzureKeyCredential
    from azure.ai.transcription import TranscriptionClient
    from azure.ai.transcription.models import TranscriptionContent, TranscriptionOptions
    
    # Get configuration from environment variables
    endpoint = os.environ["AZURE_SPEECH_ENDPOINT"]
    api_key = os.environ["AZURE_SPEECH_API_KEY"]
    
    # Create the transcription client
    client = TranscriptionClient(endpoint=endpoint, credential=AzureKeyCredential(api_key))
    
    # Path to your audio file (replace with your own file path)
    audio_file_path = "<path-to-your-audio-file.wav>"
    
    # Open and read the audio file
    with open(audio_file_path, "rb") as audio_file:
        # Create transcription options
        options = TranscriptionOptions(locales=["en-US"])  # Specify the language
    
        # Create the request content
        request_content = TranscriptionContent(definition=options, audio=audio_file)
    
        # Transcribe the audio
        result = client.transcribe(request_content)
    
        # Print the transcription result
        print(f"Transcription: {result.combined_phrases[0].text}")
    
        # Print detailed phrase information
        if result.phrases:
            print("\nDetailed phrases:")
            for phrase in result.phrases:
                print(
                    f"  [{phrase.offset_milliseconds}ms - "
                    f"{phrase.offset_milliseconds + phrase.duration_milliseconds}ms]: "
                    f"{phrase.text}"
                )
    ```

    Reference: [TranscriptionClient](/en-us/python/api/azure-ai-transcription/azure.ai.transcription.transcriptionclient) | [TranscriptionContent](/en-us/python/api/azure-ai-transcription/azure.ai.transcription.models.transcriptioncontent) | [TranscriptionOptions](/en-us/python/api/azure-ai-transcription/azure.ai.transcription.models.transcriptionoptions) | [AzureKeyCredential](/en-us/python/api/azure-core/azure.core.credentials.azurekeycredential)
2. Replace `<path-to-your-audio-file.wav>` with the path to your audio file. The service supports WAV, MP3, FLAC, OGG, and other common audio formats.
3. Run the Python script:

    ```bash
    python transcribe_audio_file.py
    ```

## Output

The script prints the transcription result to the console:

```output
Transcription: Hi there! This is a sample voice recording created for speech synthesis testing. The quick brown fox jumps over the lazy dog. Just a fun way to include every letter of the alphabet. Numbers, like 1, 2, 3, are spoken clearly. Let's see how well this voice captures tone, timing, and natural rhythm. This audio is provided by samplefiles.com.

Detailed phrases:
  [40ms - 4880ms]: Hi there! This is a sample voice recording created for speech synthesis testing.
  [5440ms - 8400ms]: The quick brown fox jumps over the lazy dog.
  [9040ms - 12240ms]: Just a fun way to include every letter of the alphabet.
  [12720ms - 16720ms]: Numbers, like 1, 2, 3, are spoken clearly.
  [17200ms - 22000ms]: Let's see how well this voice captures tone, timing, and natural rhythm.
  [22480ms - 25920ms]: This audio is provided by samplefiles.com.
```

## Request configuration options

Use `TranscriptionOptions` to customize transcription behavior. The following sections describe each supported configuration and show how to apply it.

### Multi-language detection

Pass multiple locale candidates to `locales` to enable language identification across languages. The service detects which language is spoken and labels each phrase with the detected locale. Omit `locales` entirely to let the service auto-detect all languages without a candidate list.

```python
from azure.core.credentials import AzureKeyCredential
from azure.ai.transcription import TranscriptionClient
from azure.ai.transcription.models import TranscriptionContent, TranscriptionOptions

client = TranscriptionClient(
    endpoint=endpoint, credential=AzureKeyCredential(api_key)
)

with open(audio_file_path, "rb") as audio_file:
    # Provide candidate locales — the service selects the best match per phrase
    options = TranscriptionOptions(locales=["en-US", "es-ES", "fr-FR", "de-DE"])
    result = client.transcribe(TranscriptionContent(definition=options, audio=audio_file))

    for phrase in result.phrases:
        locale = phrase.locale if phrase.locale else "detected"
        print(f"[{locale}] {phrase.text}")
```

Reference: [`TranscriptionOptions`](/en-us/python/api/azure-ai-transcription/azure.ai.transcription.models.transcriptionoptions)

### Speaker diarization

Diarization detects and labels different speakers in a single audio channel. Create a `TranscriptionDiarizationOptions` object with the maximum expected number of speakers (2–35) and pass it to `TranscriptionOptions`. Each phrase in the result includes a `speaker` identifier.

```python
from azure.core.credentials import AzureKeyCredential
from azure.ai.transcription import TranscriptionClient
from azure.ai.transcription.models import (
    TranscriptionContent,
    TranscriptionOptions,
    TranscriptionDiarizationOptions,
)

client = TranscriptionClient(
    endpoint=endpoint, credential=AzureKeyCredential(api_key)
)

with open(audio_file_path, "rb") as audio_file:
    diarization_options = TranscriptionDiarizationOptions(
        max_speakers=5  # Hint for maximum number of speakers (2-35)
    )
    options = TranscriptionOptions(
        locales=["en-US"], diarization_options=diarization_options
    )
    result = client.transcribe(TranscriptionContent(definition=options, audio=audio_file))

    for phrase in result.phrases:
        speaker = phrase.speaker if phrase.speaker is not None else "Unknown"
        print(f"Speaker {speaker} [{phrase.offset_milliseconds}ms]: {phrase.text}")
```

Note

Diarization is only supported on single-channel (mono) audio. If your audio is stereo, don't set the `channels` property to `[0, 1]` when diarization is enabled.

Reference: [`TranscriptionDiarizationOptions`](/en-us/python/api/azure-ai-transcription/azure.ai.transcription.models.transcriptiondiarizationoptions), [`TranscriptionOptions`](/en-us/python/api/azure-ai-transcription/azure.ai.transcription.models.transcriptionoptions)

### Phrase list

A phrase list boosts recognition accuracy for domain-specific terms, proper nouns, and uncommon words. Set `biasing_weight` between `1.0` and `20.0` to control how strongly the phrases are favored (higher values increase the bias).

```python
from azure.core.credentials import AzureKeyCredential
from azure.ai.transcription import TranscriptionClient
from azure.ai.transcription.models import (
    TranscriptionContent,
    TranscriptionOptions,
    PhraseListProperties,
)

client = TranscriptionClient(
    endpoint=endpoint, credential=AzureKeyCredential(api_key)
)

with open(audio_file_path, "rb") as audio_file:
    phrase_list = PhraseListProperties(
        phrases=["Contoso", "Jessie", "Rehaan"],
        biasing_weight=5.0,  # Weight between 1.0 and 20.0
    )
    options = TranscriptionOptions(locales=["en-US"], phrase_list=phrase_list)
    result = client.transcribe(TranscriptionContent(definition=options, audio=audio_file))

    print(result.combined_phrases[0].text)
```

For more information, see [Improve recognition accuracy with phrase list](improve-accuracy-phrase-list#implement-phrase-list-in-fast-transcription).

Reference: [`PhraseListProperties`](/en-us/python/api/azure-ai-transcription/azure.ai.transcription.models.phraselistproperties), [`TranscriptionOptions`](/en-us/python/api/azure-ai-transcription/azure.ai.transcription.models.transcriptionoptions)

### Profanity filtering

Control how profanity appears in transcription output using the `profanity_filter_mode` parameter. The following modes are available:

| Mode | Behavior |
| --- | --- |
| `"None"` | Profanity passes through unchanged. |
| `"Masked"` | Profanity is replaced with asterisks (default). |
| `"Removed"` | Profanity is removed from the output entirely. |
| `"Tags"` | Profanity is wrapped in `<profanity>` XML tags. |

```python
from azure.core.credentials import AzureKeyCredential
from azure.ai.transcription import TranscriptionClient
from azure.ai.transcription.models import TranscriptionContent, TranscriptionOptions

client = TranscriptionClient(
    endpoint=endpoint, credential=AzureKeyCredential(api_key)
)

with open(audio_file_path, "rb") as audio_file:
    options = TranscriptionOptions(
        locales=["en-US"],
        profanity_filter_mode="Masked"  # Options: "None", "Removed", "Masked", "Tags"
    )
    result = client.transcribe(TranscriptionContent(definition=options, audio=audio_file))

    print(result.combined_phrases[0].text)
```

Reference: [`TranscriptionOptions`](/en-us/python/api/azure-ai-transcription/azure.ai.transcription.models.transcriptionoptions)

::: zone-end

::: zone pivot="programming-language-csharp"

[Reference documentation](/en-us/dotnet/api/overview/azure/ai.speech.transcription-readme) | [Package (NuGet)](https://www.nuget.org/packages/Azure.AI.Speech.Transcription/1.0.0-beta.1) | [GitHub samples](https://github.com/Azure/azure-sdk-for-net/tree/main/sdk/transcription/Azure.AI.Speech.Transcription/samples)

## Prerequisites

- An Azure subscription. [Create one for free](https://azure.microsoft.com/pricing/purchase-options/azure-account?cid=msft_learn).
- [.NET 8.0 SDK](https://dotnet.microsoft.com/download) or later.
- A [Microsoft Foundry resource](/en-us/azure/ai-services/multi-service-resource) created in one of the supported regions. For more information about region availability, see [Region support](/en-us/azure/ai-services/speech-service/regions?tabs=stt).
- A sample `.wav` audio file to transcribe.

### Microsoft Entra ID prerequisites

For the recommended keyless authentication with Microsoft Entra ID, you need to:

- Install the [Azure CLI](/en-us/cli/azure/install-azure-cli) used for keyless authentication with Microsoft Entra ID.
- Sign in with the Azure CLI by running `az login`.
- Assign the `Cognitive Services User` role to your user account. You can assign roles in the Azure portal under **Access control (IAM)** &gt; **Add role assignment**.

## Set up the project

1. Create a new console application with the .NET CLI:

    ```dotnetcli
    dotnet new console -n transcription-quickstart
    cd transcription-quickstart
    ```
2. Install the required packages:

    ```dotnetcli
    dotnet add package Azure.AI.Speech.Transcription --prerelease
    dotnet add package Azure.Identity
    ```

## Retrieve resource information

You need to retrieve your resource endpoint for authentication.

1. Sign in to [Foundry portal](https://ai.azure.com).
2. Select **Management center** from the left menu. Under **Connected resources**, select your Speech or multi-service resource.
3. Select **Keys and Endpoint**.
4. Copy the **Endpoint** value and set it as an environment variable:

    ```powershell
    $env:AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
    ```

## Transcribe audio

Replace the contents of `Program.cs` with the following code:

```csharp
using System;
using System.ClientModel;
using System.Linq;
using System.Threading.Tasks;
using Azure.AI.Speech.Transcription;
using Azure.Identity;

Uri endpoint = new Uri(Environment.GetEnvironmentVariable("AZURE_SPEECH_ENDPOINT")
    ?? throw new InvalidOperationException("Set the AZURE_SPEECH_ENDPOINT environment variable."));

// Use DefaultAzureCredential for keyless authentication (recommended).
// To use an API key instead, replace with:
// ApiKeyCredential credential = new ApiKeyCredential("<your-api-key>");
var credential = new DefaultAzureCredential();
TranscriptionClient client = new TranscriptionClient(endpoint, credential);

string audioFilePath = "<path-to-your-audio-file.wav>";
using FileStream audioStream = File.OpenRead(audioFilePath);

TranscriptionOptions options = new TranscriptionOptions(audioStream);
ClientResult<TranscriptionResult> response = await client.TranscribeAsync(options);

var channelPhrases = response.Value.PhrasesByChannel.First();
Console.WriteLine(channelPhrases.Text);
```

Run the application:

```dotnetcli
dotnet run
```

The transcribed text from your audio file prints to the console.

## Access word-level details

To access timestamps, confidence scores, and individual words, iterate over phrases:

```csharp
var channelPhrases = response.Value.PhrasesByChannel.First();

foreach (TranscribedPhrase phrase in channelPhrases.Phrases)
{
    Console.WriteLine($"\nPhrase: {phrase.Text}");
    Console.WriteLine($"  Offset: {phrase.Offset} | Duration: {phrase.Duration}");
    Console.WriteLine($"  Confidence: {phrase.Confidence:F2}");

    foreach (TranscribedWord word in phrase.Words)
    {
        Console.WriteLine(
            $"    Word: '{word.Text}' | " +
            $"Confidence: {word.Confidence:F2} | " +
            $"Offset: {word.Offset}");
    }
}
```

Reference: [`TranscribedPhrase`](/en-us/dotnet/api/azure.ai.speech.transcription.transcribedphrase), [`TranscribedWord`](/en-us/dotnet/api/azure.ai.speech.transcription.transcribedword)

## Identify speakers with diarization

Speaker diarization identifies who spoke when in multi-speaker audio:

```csharp
TranscriptionOptions options = new TranscriptionOptions(audioStream)
{
    DiarizationOptions = new TranscriptionDiarizationOptions
    {
        MaxSpeakers = 4
    }
};

ClientResult<TranscriptionResult> response = await client.TranscribeAsync(options);

var channelPhrases = response.Value.PhrasesByChannel.First();
foreach (TranscribedPhrase phrase in channelPhrases.Phrases)
{
    Console.WriteLine($"Speaker {phrase.Speaker}: {phrase.Text}");
}
```

Reference: [`TranscriptionDiarizationOptions`](/en-us/dotnet/api/azure.ai.speech.transcription.transcriptiondiarizationoptions)

::: zone-end

::: zone pivot="programming-language-javascript"

[Reference documentation](/en-us/javascript/api/overview/azure/ai-speech-transcription-readme) | [Package (npm)](https://www.npmjs.com/package/@azure/ai-speech-transcription) | [GitHub samples](https://github.com/Azure/azure-sdk-for-js/tree/main/sdk/transcription/ai-speech-transcription/samples)

## Prerequisites

- An Azure subscription. [Create one for free](https://azure.microsoft.com/pricing/purchase-options/azure-account?cid=msft_learn).
- [Node.js LTS](https://nodejs.org/).
- A [Microsoft Foundry resource](/en-us/azure/ai-services/multi-service-resource) created in one of the supported regions. For more information about region availability, see [Region support](/en-us/azure/ai-services/speech-service/regions?tabs=stt).
- A sample `.wav` audio file to transcribe.

### Microsoft Entra ID prerequisites

For the recommended keyless authentication with Microsoft Entra ID, you need to:

- Install the [Azure CLI](/en-us/cli/azure/install-azure-cli) used for keyless authentication with Microsoft Entra ID.
- Sign in with Azure CLI by running `az login`.
- Assign the `Cognitive Services User` role to your user account. You can assign roles in the Azure portal under **Access control (IAM)** &gt; **Add role assignment**.

## Set up the project

1. Create a new folder and initialize a Node.js project:

    ```bash
    mkdir transcription-quickstart
    cd transcription-quickstart
    npm init -y
    ```
2. Install the required packages:

    ```bash
    npm install @azure/ai-speech-transcription @azure/identity
    ```
3. Configure the project to use ES modules by adding the module type to your `package.json`:

    ```bash
    npm pkg set type=module
    ```

    Or manually add `"type": "module"` to your `package.json` file. This is required for the `import` statements in the sample code to work.

## Retrieve resource information

You need to retrieve your resource endpoint for authentication.

1. Sign in to [Foundry portal](https://ai.azure.com).
2. Select **Management center** from the left menu. Under **Connected resources**, select your Speech or multi-service resource.
3. Select **Keys and Endpoint**.
4. Copy the **Endpoint** value and set it as an environment variable:

# [Windows](#tab/windows)
```powershell
    $env:AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
    ```

# [Linux](#tab/linux)
```bash
    export AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
    ```

# [macOS](#tab/macos)
```bash
    export AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
    ```

---

## Transcribe audio

1. Create a file named `transcribe-audio-file.js` with the following code:

    ```javascript
    import { readFileSync } from "node:fs";
    import { DefaultAzureCredential } from "@azure/identity";
    import { TranscriptionClient } from "@azure/ai-speech-transcription";
    
    const endpoint = process.env.AZURE_SPEECH_ENDPOINT;
    if (!endpoint) {
      throw new Error("Set the AZURE_SPEECH_ENDPOINT environment variable.");
    }
    
    // Use DefaultAzureCredential for keyless authentication (recommended).
    const client = new TranscriptionClient(endpoint, new DefaultAzureCredential());
    
    const audioFile = readFileSync("<path-to-your-audio-file.wav>");
    
    const result = await client.transcribe(audioFile, {
      locales: ["en-US"],
    });
    
    console.log("Transcription:", result.combinedPhrases[0]?.text ?? "No text");
    ```

    Reference: [TranscriptionClient](/en-us/javascript/api/%40azure/ai-speech-transcription/transcriptionclient) | [DefaultAzureCredential](/en-us/javascript/api/%40azure/identity/defaultazurecredential)
2. Replace `<path-to-your-audio-file.wav>` with the path to your audio file.
3. Run the app:

    ```bash
    node transcribe-audio-file.js
    ```

## Output

The app prints the transcribed text to the console:

```output
Transcription: Hi there! This is a sample voice recording.
```

## Common request options

### Identify speakers with diarization

```javascript
const result = await client.transcribe(audioFile, {
  locales: ["en-US"],
  diarizationOptions: {
    maxSpeakers: 4,
  },
});

for (const phrase of result.phrases) {
  console.log(`Speaker ${phrase.speaker}: ${phrase.text}`);
}
```

Reference: [TranscriptionDiarizationOptions](/en-us/javascript/api/%40azure/ai-speech-transcription/transcriptiondiarizationoptions)

### Set profanity filtering

```javascript
import {
  KnownProfanityFilterModes,
} from "@azure/ai-speech-transcription";

const result = await client.transcribe(audioFile, {
  locales: ["en-US"],
  profanityFilterMode: KnownProfanityFilterModes.Masked,
});
```

Reference: [KnownProfanityFilterModes](/en-us/javascript/api/%40azure/ai-speech-transcription/knownprofanityfiltermodes)

### Add a phrase list

Use a phrase list to improve recognition for domain-specific terms, proper nouns, and acronyms:

```javascript
const result = await client.transcribe(audioFile, {
  locales: ["en-US"],
  phraseList: {
    phrases: ["Contoso", "Jessie", "Rehaan"],
  },
});

console.log("Transcription:", result.combinedPhrases[0]?.text ?? "No text");
```

Reference: [PhraseListProperties](/en-us/javascript/api/%40azure/ai-speech-transcription/phraselistproperties)

### Enable multi-language detection

When you're unsure which language is spoken, pass multiple locale candidates. The service detects the language and returns locale per phrase:

```javascript
const result = await client.transcribe(audioFile, {
  locales: ["en-US", "es-ES"],
});

for (const phrase of result.phrases) {
  console.log(`[${phrase.locale}] ${phrase.text}`);
}
```

Reference: [TranscribedPhrase](/en-us/javascript/api/%40azure/ai-speech-transcription/transcribedphrase)

::: zone-end

::: zone pivot="programming-language-java"

[Reference documentation](/en-us/java/api/overview/azure/ai-speech-transcription-readme) | [Package (Maven)](https://central.sonatype.com/artifact/com.azure/azure-ai-speech-transcription) | [GitHub Samples](https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/transcription/azure-ai-speech-transcription/src/samples/java/com/azure/ai/speech/transcription/README.md)

## Prerequisites

- An Azure subscription. [Create one for free](https://azure.microsoft.com/pricing/purchase-options/azure-account?cid=msft_learn).
- [Java Development Kit (JDK) 8 or later](https://www.oracle.com/java/technologies/downloads/).
- [Apache Maven](https://maven.apache.org/download.cgi) for dependency management and building the project.
- A [Microsoft Foundry resource](../multi-service-resource) in one of the supported regions. For more information about region availability, see [Speech service supported regions](regions).
- A sample `.wav` audio file to transcribe.

## Set up the environment

1. Create a new folder named `transcription-quickstart` and navigate to it:

    ```shell
    mkdir transcription-quickstart && cd transcription-quickstart
    ```
2. Create a `pom.xml` file in the root of your project directory with the following content:

    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <project xmlns="http://maven.apache.org/POM/4.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
    
        <groupId>com.example</groupId>
        <artifactId>transcription-quickstart</artifactId>
        <version>1.0.0</version>
        <packaging>jar</packaging>
    
        <name>Speech Transcription Quickstart</name>
        <description>Quickstart sample for Azure Speech Transcription client library.</description>
        <url>https://github.com/Azure/azure-sdk-for-java</url>
    
        <properties>
            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        </properties>
    
        <dependencies>
            <dependency>
                <groupId>com.azure</groupId>
                <artifactId>azure-ai-speech-transcription</artifactId>
                <version>1.0.0-beta.2</version>
            </dependency>
            <dependency>
                <groupId>com.azure</groupId>
                <artifactId>azure-identity</artifactId>
                <version>1.18.1</version>
            </dependency>
        </dependencies>
    
        <build>
            <sourceDirectory>.</sourceDirectory>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.11.0</version>
                    <configuration>
                        <source>1.8</source>
                        <target>1.8</target>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.codehaus.mojo</groupId>
                    <artifactId>exec-maven-plugin</artifactId>
                    <version>3.1.0</version>
                    <configuration>
                        <mainClass>TranscriptionQuickstart</mainClass>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </project>
    ```

    Note

    The `<sourceDirectory>.</sourceDirectory>` configuration tells Maven to look for Java source files in the current directory instead of the default `src/main/java` structure. This configuration change allows for a simpler flat project structure.
3. Install the dependencies:

    ```shell
    mvn clean install
    ```

## Set environment variables

Your application must be authenticated to access the Speech service. The SDK supports both API key and Microsoft Entra ID authentication. It automatically detects which method to use based on the environment variables you set.

First, set the endpoint for your Speech resource. Replace `<your-speech-endpoint>` with your actual resource name:

# [Windows](#tab/windows)
```powershell
$env:AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
```

# [Linux](#tab/linux)
```bash
export AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
```

# [macOS](#tab/macos)
```bash
export AZURE_SPEECH_ENDPOINT="<your-speech-endpoint>"
```

---

Then, choose one of the following authentication methods:

### Option 1: API key authentication (recommended for getting started)

Set the API key environment variable:

# [Windows](#tab/windows)
```powershell
$env:AZURE_SPEECH_API_KEY="<your-speech-key>"
```

# [Linux](#tab/linux)
```bash
export AZURE_SPEECH_API_KEY=<your-speech-key>
```

# [macOS](#tab/macos)
```bash
export AZURE_SPEECH_API_KEY=<your-speech-key>
```

---

### Option 2: Microsoft Entra ID authentication (recommended for production)

Instead of setting `AZURE_SPEECH_API_KEY`, configure one of the following credential sources:

- **Azure CLI**: Run `az login` on your development machine.
- **Managed Identity**: For apps running in Azure (App Service, Azure Functions, VMs).
- **Environment Variables**: Set `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, and `AZURE_CLIENT_SECRET`.
- **Visual Studio Code or IntelliJ**: Sign in through your IDE.

You also need to assign the **Cognitive Services User** role to your identity:

```azurecli
az role assignment create --assignee <your-identity> \
    --role "Cognitive Services User" \
    --scope /subscriptions/<subscription-id>/resourceGroups/<resource-group>/providers/Microsoft.CognitiveServices/accounts/<speech-resource-name>
```

Note

After setting environment variables on Windows, restart any running programs that need to read them, including the console window. On Linux or macOS, run `source ~/.bashrc` (or your equivalent shell configuration file) to make the changes effective.

## Create the application

Create a file named `TranscriptionQuickstart.java` in your project directory with the following code:

```java
import com.azure.ai.speech.transcription.TranscriptionClient;
import com.azure.ai.speech.transcription.TranscriptionClientBuilder;
import com.azure.ai.speech.transcription.models.AudioFileDetails;
import com.azure.ai.speech.transcription.models.TranscriptionOptions;
import com.azure.ai.speech.transcription.models.TranscriptionResult;
import com.azure.core.credential.KeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredentialBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TranscriptionQuickstart {
    public static void main(String[] args) {
        try {
            // Get credentials from environment variables
            String endpoint = System.getenv("AZURE_SPEECH_ENDPOINT");
            String apiKey = System.getenv("AZURE_SPEECH_API_KEY");

            // Create client with API key or Entra ID authentication
            TranscriptionClientBuilder builder = new TranscriptionClientBuilder()
                .endpoint(endpoint);

            TranscriptionClient client;
            if (apiKey != null && !apiKey.isEmpty()) {
                // Use API key authentication
                client = builder.credential(new KeyCredential(apiKey)).buildClient();
            } else {
                // Use Entra ID authentication
                client = builder.credential(new DefaultAzureCredentialBuilder().build()).buildClient();
            }

            // Load audio file
            String audioFilePath = "<path-to-your-audio-file.wav>";
            byte[] audioData = Files.readAllBytes(Paths.get(audioFilePath));

            // Create audio file details
            AudioFileDetails audioFileDetails = new AudioFileDetails(BinaryData.fromBytes(audioData));

            // Transcribe
            TranscriptionOptions options = new TranscriptionOptions(audioFileDetails);
            TranscriptionResult result = client.transcribe(options);

            // Print result
            System.out.println("Transcription:");
            result.getCombinedPhrases().forEach(phrase ->
                System.out.println(phrase.getText())
            );

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

Replace `<path-to-your-audio-file.wav>` with the path to your audio file.

## Run the application

Run the application using Maven:

```shell
mvn compile exec:java
```

## Request configuration options

Use `TranscriptionOptions` to customize transcription behavior. The following sections describe each supported configuration and show how to apply it.

### Multi-language detection

When you don't specify a locale, the service automatically detects and transcribes all languages present in the audio. Each returned phrase includes a `locale` field that identifies the detected language.

```java
// No locale specified — service auto-detects all languages in the audio
TranscriptionOptions options = new TranscriptionOptions(audioFileDetails);
TranscriptionResult result = client.transcribe(options);

// Each phrase reports the detected locale
result.getPhrases().forEach(phrase ->
    System.out.println(phrase.getLocale() + ": " + phrase.getText())
);
```

Note

When no locale is specified, the `locale` field on individual phrases might not always accurately reflect the exact language of that specific phrase. For highest accuracy, specify the expected locale when you know it.

Reference: [`TranscriptionOptions`](/en-us/java/api/com.azure.ai.speech.transcription.models.transcriptionoptions), [`TranscribedPhrase.getLocale()`](/en-us/java/api/com.azure.ai.speech.transcription.models.transcribedphrase)

### Speaker diarization

Diarization detects and labels different speakers in a single audio channel. Use `TranscriptionDiarizationOptions` to enable it and set the maximum expected number of speakers (2–36). Each phrase in the result includes a `speaker` identifier.

```java
import com.azure.ai.speech.transcription.models.TranscriptionDiarizationOptions;

// Configure diarization with a maximum of 5 speakers
TranscriptionDiarizationOptions diarizationOptions =
    new TranscriptionDiarizationOptions()
        .setMaxSpeakers(5);

TranscriptionOptions options = new TranscriptionOptions(audioFileDetails)
    .setDiarizationOptions(diarizationOptions);

TranscriptionResult result = client.transcribe(options);

// Each phrase includes the detected speaker ID
result.getPhrases().forEach(phrase ->
    System.out.println(
        "[Speaker " + phrase.getSpeaker() + "] " + phrase.getText()
    )
);
```

Note

Diarization is only supported on single-channel (mono) audio. If your audio is stereo, don't set the `channels` property to `[0,1]` when diarization is enabled.

Reference: [`TranscriptionDiarizationOptions`](/en-us/java/api/com.azure.ai.speech.transcription.models.transcriptiondiarizationoptions), [`TranscriptionOptions.setDiarizationOptions()`](/en-us/java/api/com.azure.ai.speech.transcription.models.transcriptionoptions), [`TranscribedPhrase.getSpeaker()`](/en-us/java/api/com.azure.ai.speech.transcription.models.transcribedphrase)

### Phrase list

A phrase list boosts recognition accuracy for domain-specific terms, proper nouns, and uncommon words. Phrases you add are weighted more heavily by the recognizer, making them more likely to be transcribed correctly.

```java
import com.azure.ai.speech.transcription.models.PhraseListOptions;
import java.util.Arrays;

// Add terms that appear in your audio to improve recognition
PhraseListOptions phraseListOptions = new PhraseListOptions()
    .setPhrases(Arrays.asList("Contoso", "Jessie", "Rehaan"));

TranscriptionOptions options = new TranscriptionOptions(audioFileDetails)
    .setPhraseListOptions(phraseListOptions);

TranscriptionResult result = client.transcribe(options);

result.getCombinedPhrases().forEach(phrase ->
    System.out.println(phrase.getText())
);
```

For more information, see [Improve recognition accuracy with phrase list](improve-accuracy-phrase-list#implement-phrase-list-in-fast-transcription).

Reference: [`PhraseListOptions`](/en-us/java/api/com.azure.ai.speech.transcription.models.phraselistoptions), [`TranscriptionOptions.setPhraseListOptions()`](/en-us/java/api/com.azure.ai.speech.transcription.models.transcriptionoptions)

### Profanity filtering

Control how profanity appears in the transcription output using `ProfanityFilterMode`. The following modes are available:

| Mode | Behavior |
| --- | --- |
| `NONE` | Profanity passes through unchanged. |
| `MASKED` | Profanity is replaced with asterisks (default). |
| `REMOVED` | Profanity is removed from the output entirely. |
| `TAGS` | Profanity is wrapped in XML tags. |

```java
import com.azure.ai.speech.transcription.models.ProfanityFilterMode;

TranscriptionOptions options = new TranscriptionOptions(audioFileDetails)
    .setProfanityFilterMode(ProfanityFilterMode.MASKED);

TranscriptionResult result = client.transcribe(options);

System.out.println(result.getCombinedPhrases().get(0).getText());
```

Reference: [`ProfanityFilterMode`](/en-us/java/api/com.azure.ai.speech.transcription.models.profanityfiltermode), [`TranscriptionOptions.setProfanityFilterMode()`](/en-us/java/api/com.azure.ai.speech.transcription.models.transcriptionoptions)

## Clean up resources

When you're done with the quickstart, you can delete the project folder:

```shell
rm -rf transcription-quickstart
```

::: zone-end

## Transcription error handling

### Implement retry logic with exponential backoff

When calling the fast transcription API, implement retry logic to handle transient errors and rate limiting. The API enforces rate limits, which can result in HTTP 429 responses during high-concurrency operations.

#### Recommended retry configuration

- Retry up to 5 times on transient errors.
- Use exponential backoff: 2s, 4s, 8s, 16s, 32s.
- Total backoff time: 62 seconds.

This configuration provides sufficient time for the API to recover during rate-limiting windows, especially when running batch operations with multiple concurrent workers.

#### When to use retry logic

Implement retry logic for the following error categories:

- **HTTP errors**- Retry on:
    - HTTP 429 (rate limit)
    - HTTP 500, 502, 503, 504 (server errors)
    - `status_code=None` (incomplete response downloads)
- **Azure SDK network errors**- Retry on:
    - `ServiceRequestError`
    - `ServiceResponseError` These errors wrap low-level network exceptions like `urllib3.exceptions.ReadTimeoutError`, connection resets, and TLS failures.
- **Python network exceptions**- Retry on:
    - `ConnectionError`
    - `TimeoutError`
    - `OSError`

Don't retry on the following errors, as they indicate client-side issues that require correction:

- HTTP 400 (bad request)
- HTTP 401 (unauthorized)
- HTTP 422 (unprocessable entity)
- Other client errors (4xx status codes)

#### Implementation notes

- Reset the audio file stream (`seek(0)`) before each retry attempt.
- When using concurrent workers, be aware that the default HTTP read timeout (300 seconds) might be exceeded under heavy rate limiting.
- Be aware the API might accept a request but time out while generating the response, which can appear as an SDK-wrapped network error rather than standard HTTP error.