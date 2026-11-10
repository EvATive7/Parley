<div align="center">

# 🗣️ Parley

Application Android open source de traduction en face à face.

[![Dernière version](https://img.shields.io/github/v/release/EvATive7/Parley.svg?label=derni%C3%A8re%20version)](https://github.com/EvATive7/Parley/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?logo=android&logoColor=white)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | **Français** | [Deutsch](README.de.md) | [Español](README.es.md)

[**Télécharger l’APK**](https://github.com/EvATive7/Parley/releases/latest)

</div>

---

Maintenez le bouton pour parler, puis relâchez-le pour traduire. Votre interlocuteur fait de même et chacun voit la traduction. Parley convient aux situations qui exigent une communication immédiate, comme demander son chemin à l’étranger ou participer à une réunion multilingue.

<div align="center">
<table>
<tr>
<td><img src="docs/screenshots/translate-zh-ja.jpg" width="240" alt="Traduction chinois-japonais"></td>
<td><img src="docs/screenshots/recording.jpg" width="240" alt="Enregistrement"></td>
<td><img src="docs/screenshots/translate-zh-en.jpg" width="240" alt="Traduction chinois-anglais"></td>
</tr>
<tr>
<td align="center">Résultat de la traduction</td>
<td align="center">Maintenir pour enregistrer</td>
<td align="center">Instructions bilingues</td>
</tr>
</table>

<img src="docs/screenshots/face-to-face.jpg" width="600" alt="Mode face à face">

*Mode face à face : l’écran est divisé en deux, chaque partie étant orientée vers une personne.*

</div>

## ✨ Fonctionnalités

- **Direction de traduction automatique** : choisissez deux langues ; parler dans l’une traduit automatiquement vers l’autre, sans changement manuel. Toute autre langue, ou une langue non identifiée, est traduite vers les deux.
- **Affichage en continu** : la traduction apparaît progressivement, sans attendre la fin de la phrase.
- **Mode face à face** : posez le téléphone à plat sur une table. L’écran se divise en deux, chaque partie faisant face à une personne et affichant uniquement la langue de l’autre.
- **Maintenir pour parler** : maintenez pour enregistrer, relâchez pour envoyer ou faites glisser vers le haut pour annuler.
- **Instructions bilingues** : les indications du bouton apparaissent dans les deux langues afin que chacun puisse les comprendre.
- **Actions sur les cartes** : touchez une carte ou appuyez longuement dessus pour afficher le texte complet, copier l’original ou la traduction, la supprimer, ou corriger le texte reconnu et le retraduire ; une traduction échouée peut être relancée.
- **Connexion directe de bout en bout** : aucun serveur intermédiaire. L’audio et le texte sont envoyés directement aux services configurés, et les clés API sont chiffrées avec Android Keystore.
- **Mémorise la conversation et vos réglages** : les cartes terminées et la paire de langues sont conservées d’un lancement à l’autre ; toute la conversation peut être effacée depuis le menu, et les réglages des services peuvent être exportés puis importés sur un autre appareil.

## 📦 Premiers pas

1. Téléchargez et installez l’APK depuis [Releases](https://github.com/EvATive7/Parley/releases/latest) (Android 8.0 ou version ultérieure requis).
2. Ouvrez les paramètres avec l’icône d’engrenage en haut à droite, puis configurez un service de reconnaissance vocale et un service de traduction compatible avec OpenAI.
3. Choisissez les deux langues, maintenez le bouton et parlez.

> Parley est gratuit, mais ne fournit ni compte ni crédit d’utilisation. Les services sélectionnés facturent la reconnaissance vocale et la traduction.

## 🎙️ Services de reconnaissance vocale

| Fournisseur | Service | Configuration requise |
| --- | --- | --- |
| Alibaba Cloud DashScope | Fun-ASR-RealTime (streaming / non-streaming) | Clé API et région (Pékin ou Singapour) |
| OpenAI | GPT Realtime Whisper, GPT-4o Transcribe, GPT-4o Mini Transcribe | Clé API |
| Volcengine | Doubao Seed ASR 2.0 (streaming / non-streaming / non-streaming turbo) | AppID et Access Token ; la ressource correspondante doit être activée dans la console |

Les services en streaming renvoient les résultats pendant que vous parlez. Les services non-streaming renvoient le résultat après la fin de la phrase et sont généralement plus précis. Les différents services d’un même fournisseur utilisent les mêmes identifiants.

## 🔊 Services de synthèse vocale

| Fournisseur | Service | Configuration requise | Tarif indicatif et comptage |
| --- | --- | --- | --- |
| Android | Voix du système | Aucune | Gratuit |
| Alibaba Cloud DashScope | CosyVoice 3.5 Flash / Plus | Clé API et région DashScope partagées | Flash : 0,8 ¥/10 000 caractères ; Plus : 1,5 ¥/10 000 |
| Volcengine | Doubao Seed TTS 2.0 | AppID et Access Token partagés ; activer Speech Synthesis 2.0 | 3 ¥/10 000 caractères ; un caractère chinois compte pour un |
| MiniMax | Speech 2.8 Turbo / HD | Clé API et région Chine ou internationale | Turbo : 2 ¥/10 000 (Chine) ou 60 $/million ; HD : 3,5 ¥/10 000 ou 100 $/million ; un caractère chinois compte double |

Les cartes peuvent être lues manuellement ou automatiquement après traduction, y compris en mode face à face. L’audio cloud est diffusé en PCM 24 kHz, avec un cache uniquement en mémoire de 20 résultats maximum, et s’interrompt au début d’un enregistrement. Une langue sans voix cloud vérifiée utilise la voix de l’appareil et affiche `SYS`.

## 🌏 Langues prises en charge

**Traduction et interface** : anglais, chinois simplifié, chinois traditionnel, japonais, coréen, français, allemand, espagnol, italien, portugais, russe, arabe, thaï, vietnamien et indonésien.

Les langues réellement reconnues dépendent du service de reconnaissance vocale sélectionné. Par exemple, le service en streaming de Volcengine ne prend en charge que le chinois et l’anglais.

## 🔒 Confidentialité

- L’audio et le texte sont envoyés uniquement aux fournisseurs configurés et ne passent par aucun serveur intermédiaire.
- Les enregistrements sont traités uniquement en mémoire et ne sont jamais sauvegardés. L’original et la traduction des cartes terminées sont chiffrés avec Android Keystore sur cet appareil et conservés jusqu’à la suppression de la carte ou l’utilisation de **Effacer la conversation**.
- Les clés API sont chiffrées avec Android Keystore, ne sont pas écrites dans les journaux et ne sont pas incluses dans les sauvegardes.

## ❓ Questions fréquentes

<details>
<summary><b>Pourquoi la direction de traduction est-elle parfois incorrecte ?</b></summary>

Lorsque le service de reconnaissance vocale ne renvoie pas la langue, Parley l’identifie localement. Les phrases très courtes ou les contenus mélangeant chinois et anglais peuvent être mal identifiés. Si la langue ne peut pas être déterminée, ou n’est aucune des deux, Parley traduit vers les deux.

</details>

<details>
<summary><b>Que devient l’audio déjà envoyé lorsque je fais glisser vers le haut pour annuler ?</b></summary>

Avec un service en streaming, l’audio est envoyé en temps réel tant que le bouton est maintenu. L’annulation interrompt immédiatement les nouveaux envois et ignore le résultat, mais l’audio déjà transmis ne peut pas être rappelé.

</details>

<details>
<summary><b>Pourquoi dois-je saisir à nouveau mes clés après une réinstallation ?</b></summary>

Les clés sont liées au stockage chiffré de l’appareil et ne sont pas sauvegardées. Vous devez les reconfigurer après avoir réinstallé l’application ou après une réinitialisation des clés de chiffrement par le système.

</details>

<details>
<summary><b>Comment diagnostiquer un problème ?</b></summary>

Activez le **mode débogage** dans les paramètres pour afficher une fenêtre flottante contenant les journaux de l’application. Copiez le journal et signalez le problème dans [Issues](https://github.com/EvATive7/Parley/issues).

</details>

## 🛠️ Développement

<details>
<summary>Compilation, publication et structure du projet</summary>

### Compilation

JDK 17+ et Android SDK Platform 37 sont requis.

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Les fichiers produits se trouvent dans `app/build/outputs/apk/{debug,release}/`. Le paquet Debug est `io.github.evative7.parley.debug`, porte le nom « Parley Debug » et utilise une icône ambrée ; il peut être installé en même temps que la version officielle. Les compilations locales utilisent la version fixe `0.1.0`. En l’absence de `app/release.jks`, l’APK release est signé avec la clé debug.

### Publication

Le tag Git est l’unique source du numéro de version. L’envoi d’un tag annoté déclenche `.github/workflows/release.yml` :

```sh
git tag -a v0.5.0 -m "Release v0.5.0"
git push origin v0.5.0
```

La CI extrait `versionName` du tag, calcule `versionCode` avec la formule `major*10000 + minor*100 + patch`, exécute les tests unitaires, signe l’APK et le publie dans une GitHub Release. Le dépôt doit définir les secrets suivants : `KEYSTORE_BASE64` (base64 de `release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`.

Identifiants de ressources Volcengine : `volc.seedasr.sauc.duration`, `volc.seedasr.auc` et `volc.bigasr.auc_turbo`.

### Structure du projet

```
app/src/main/java/io/github/evative7/parley/
├─ ui/        Interface, bouton de parole, cartes, disposition face à face, paramètres des services
├─ state/     TranslateViewModel, SettingsViewModel, machine à états du bouton de parole
├─ audio/     Enregistrement, volume, WAV et rééchantillonnage
├─ asr/       Préréglages et routage vocal ; reconnaissance DashScope, OpenAI et Volcengine
├─ translate/ Traduction en streaming compatible avec OpenAI
├─ network/   Analyse SSE, préchauffage des connexions, erreurs de service expurgées
├─ model/     Langues et identification locale de la langue
├─ data/      Paramètres, services de traduction, stockage des clés avec Keystore
├─ debug/     Journal de débogage intégré
├─ tts/       Synthèse système/cloud, lecture PCM, routage et cache mémoire
└─ di/        Liaisons Hilt et configuration réseau
```

### Documentation

- [Référence API des services vocaux](docs/APIs/transcriber/README.md)

</details>
