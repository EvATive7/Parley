<div align="center">

<img src="docs/logo.svg" alt="Parley logo" width="128" height="128">

# Parley

Aplicación Android de código abierto para traducción cara a cara.

[![Última versión](https://img.shields.io/github/v/release/EvATive7/Parley.svg?label=%C3%BAltima%20versi%C3%B3n)](https://github.com/EvATive7/Parley/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?logo=android&logoColor=white)

[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Français](README.fr.md) | [Deutsch](README.de.md) | **Español**

[**Descargar APK**](https://github.com/EvATive7/Parley/releases/latest)

</div>

---

Mantén pulsado para hablar y suelta para traducir. La otra persona hace lo mismo y cada una ve la traducción. Parley está pensado para situaciones que requieren comunicación inmediata, como pedir indicaciones en el extranjero o participar en reuniones multilingües.

<div align="center">
<table>
<tr>
<td><img src="docs/screenshots/translate-zh-ja.jpg" width="240" alt="Traducción entre chino y japonés"></td>
<td><img src="docs/screenshots/recording.jpg" width="240" alt="Grabando"></td>
<td><img src="docs/screenshots/translate-zh-en.jpg" width="240" alt="Traducción entre chino e inglés"></td>
</tr>
<tr>
<td align="center">Resultado de la traducción</td>
<td align="center">Mantén pulsado para grabar</td>
<td align="center">Indicaciones bilingües</td>
</tr>
</table>

<img src="docs/screenshots/face-to-face.jpg" width="600" alt="Modo cara a cara">

*Modo cara a cara: la pantalla se divide en dos, con cada lado orientado hacia una persona.*

</div>

## ✨ Funciones

- **Dirección de traducción automática**: elige dos idiomas; al hablar cualquiera de ellos, se traduce automáticamente al otro sin cambiar nada manualmente. Cualquier otro idioma, o uno que no se pueda identificar, se traduce a ambos.
- **Salida en streaming**: la traducción aparece de forma progresiva sin esperar a que termine toda la frase.
- **Modo cara a cara**: coloca el teléfono sobre una mesa. La pantalla se divide en dos, cada lado mira hacia una persona y muestra únicamente el idioma de la otra.
- **Mantener pulsado para hablar**: mantén pulsado para grabar, suelta para enviar o desliza hacia arriba para cancelar.
- **Indicaciones bilingües**: las instrucciones del botón aparecen en ambos idiomas para que las dos personas puedan entenderlas.
- **Acciones de las tarjetas**: toca o mantén pulsada una tarjeta para ver el texto completo, copiar el original o la traducción, eliminarla o corregir el texto reconocido y volver a traducirlo; las traducciones fallidas se pueden reintentar.
- **Conexiones directas de extremo a extremo**: no hay ningún servidor intermediario. El audio y el texto se envían directamente a los proveedores configurados, y las API Key se cifran con Android Keystore.
- **Recuerda la conversación y la configuración**: las tarjetas terminadas y el par de idiomas se conservan entre aperturas; la conversación completa se puede borrar desde el menú, y los ajustes de los servicios se pueden exportar e importar en otro dispositivo.

## 📦 Primeros pasos

1. Descarga e instala el APK desde [Releases](https://github.com/EvATive7/Parley/releases/latest) (se requiere Android 8.0 o posterior).
2. Abre los ajustes con el engranaje de la esquina superior derecha y configura un servicio de reconocimiento de voz y un servicio de traducción compatible con OpenAI.
3. Elige los dos idiomas, mantén pulsado el botón y habla.

> Parley es gratuito, pero no proporciona cuentas ni créditos de uso. Los proveedores seleccionados cobran por el reconocimiento de voz y la traducción.

## 🎙️ Servicios de reconocimiento de voz

| Proveedor | Servicio | Configuración necesaria |
| --- | --- | --- |
| Alibaba Cloud DashScope | Fun-ASR-RealTime (streaming / sin streaming) | API Key y región (Pekín o Singapur) |
| OpenAI | GPT Realtime Whisper, GPT-4o Transcribe, GPT-4o Mini Transcribe | API Key |
| Volcengine | Doubao Seed ASR 2.0 (streaming / sin streaming / sin streaming turbo) | AppID y Access Token; debe activarse el recurso correspondiente en la consola |

Los servicios en streaming devuelven resultados mientras hablas. Los servicios sin streaming devuelven el resultado después de terminar y suelen ser más precisos. Los distintos servicios del mismo proveedor comparten un mismo conjunto de credenciales.

## 🔊 Servicios de síntesis de voz

| Proveedor | Servicio | Configuración necesaria | Precio orientativo y cómputo |
| --- | --- | --- | --- |
| Alibaba Cloud DashScope | Qwen Audio 3.1 TTS Flash, CosyVoice 3 Flash | API Key y región de DashScope compartidas | Qwen Audio: 1,5 ¥ de entrada / 12 ¥ de salida por millón de tokens (unos 0,3 ¥/10.000 caracteres); CosyVoice: 1 ¥/10.000 caracteres, un carácter chino cuenta como dos |
| Volcengine | Doubao Seed TTS 2.0 | AppID y Access Token compartidos; activar Speech Synthesis 2.0 | 3 ¥/10.000 caracteres; un carácter chino cuenta como uno |
| MiniMax | Speech 2.8 Turbo / HD | API Key y región China o Internacional | Turbo: 2 ¥/10.000 (China) o 60 $/millón; HD: 3,5 ¥/10.000 o 100 $/millón; un carácter chino cuenta como dos |

Las tarjetas se leen con un toque. Con **Generar automáticamente la lectura de traducciones**, el audio de cada traducción terminada se prepara al momento y suena al tocar sin esperar; la lectura automática se activa por separado en modo vertical y en modo cara a cara. El audio en la nube se transmite como PCM a 24 kHz, se almacena solo en memoria hasta 20 resultados y se interrumpe al empezar a grabar. Los idiomas sin voz integrada en el servicio elegido no se pueden leer en voz alta.

## 🌏 Idiomas compatibles

**Traducción e interfaz**: inglés, chino simplificado, chino tradicional, japonés, coreano, francés, alemán, español, italiano, portugués, ruso, árabe, tailandés, vietnamita e indonesio.

Los idiomas que se pueden reconocer realmente dependen del servicio de reconocimiento de voz seleccionado. Por ejemplo, el servicio en streaming de Volcengine solo admite chino e inglés.

## 🔒 Privacidad

- El audio y el texto solo se envían a los proveedores configurados y nunca pasan por un servidor intermediario.
- Las grabaciones solo se procesan en memoria y nunca se guardan. El original y la traducción de las tarjetas terminadas se cifran con Android Keystore en este dispositivo y se conservan hasta que elimines la tarjeta o elijas **Borrar conversación**.
- Las API Key se cifran con Android Keystore, no se escriben en los registros y no se incluyen en las copias de seguridad.

## ❓ Preguntas frecuentes

<details>
<summary><b>¿Por qué a veces es incorrecta la dirección de la traducción?</b></summary>

Cuando el servicio de reconocimiento de voz no devuelve información sobre el idioma, Parley lo identifica localmente. Las frases muy cortas o el contenido que mezcla chino e inglés pueden identificarse de forma incorrecta. Si no se puede determinar el idioma, o no es ninguno de los dos, Parley traduce a ambos.

</details>

<details>
<summary><b>¿Qué ocurre con el audio ya enviado al deslizar hacia arriba para cancelar?</b></summary>

Con un servicio en streaming, el audio se envía en tiempo real mientras mantienes pulsado el botón. Al cancelar se detienen inmediatamente los nuevos envíos y se descarta el resultado, pero el audio que ya se ha enviado no se puede retirar.

</details>

<details>
<summary><b>¿Por qué tengo que volver a introducir las claves después de reinstalar?</b></summary>

Las claves están vinculadas al almacenamiento cifrado del dispositivo y no se incluyen en las copias de seguridad. Debes configurarlas de nuevo después de reinstalar la aplicación o si el sistema restablece sus claves de cifrado.

</details>

<details>
<summary><b>¿Cómo puedo diagnosticar un problema?</b></summary>

Activa el **modo de depuración** en los ajustes para mostrar una ventana flotante con los registros de la aplicación. Copia el registro e informa del problema en [Issues](https://github.com/EvATive7/Parley/issues).

</details>

## 🛠️ Desarrollo

<details>
<summary>Compilación, publicación y estructura del proyecto</summary>

### Compilación

Se requieren JDK 17+ y Android SDK Platform 37.

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Los archivos se generan en `app/build/outputs/apk/{debug,release}/`. El paquete Debug es `io.github.evative7.parley.debug`, usa el nombre «Parley Debug» y un icono ámbar, y puede instalarse junto con la versión oficial. Las compilaciones locales usan la versión fija `0.1.0`. Si no existe `app/release.jks`, el APK release se firma con la clave debug.

### Publicación

El tag de Git es la única fuente del número de versión. Al enviar un tag anotado se ejecuta `.github/workflows/release.yml`:

```sh
git tag -a v0.5.0 -m "Release v0.5.0"
git push origin v0.5.0
```

La CI extrae `versionName` del tag, calcula `versionCode` mediante `major*10000 + minor*100 + patch`, ejecuta las pruebas unitarias, firma el APK y lo sube a una GitHub Release. El repositorio debe definir los siguientes secrets: `KEYSTORE_BASE64` (base64 de `release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.

Identificadores de recursos de Volcengine: `volc.seedasr.sauc.duration`, `volc.seedasr.auc` y `volc.bigasr.auc_turbo`.

### Estructura del proyecto

```
app/src/main/java/io/github/evative7/parley/
├─ ui/        Interfaz, botón de voz, tarjetas, diseño cara a cara, ajustes de servicios
├─ state/     TranslateViewModel, SettingsViewModel, máquina de estados del botón de voz
├─ audio/     Grabación, volumen, WAV y remuestreo
├─ asr/       Preajustes y enrutamiento de voz; reconocimiento de DashScope, OpenAI y Volcengine
├─ translate/ Traducción en streaming compatible con OpenAI
├─ network/   Análisis SSE, precalentamiento de conexiones, errores de servicio depurados
├─ model/     Idiomas e identificación local del idioma
├─ data/      Ajustes, servicios de traducción, almacenamiento de claves con Keystore
├─ debug/     Registro de depuración integrado
├─ tts/       Síntesis de voz en la nube, reproducción PCM, enrutamiento y caché en memoria
└─ di/        Enlaces de Hilt y configuración de red
```

### Documentación

- [Referencia de API de los servicios de voz](docs/APIs/transcriber/README.md)

</details>
