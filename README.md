# Binuca Highlight

Aplicativo Android offline que mantém somente os últimos 31 segundos de vídeo H.264 e áudio AAC em memória. Ao tocar em **10 s**, **20 s** ou **30 s**, o trecho encerrado naquele instante é salvo em `Movies/Binuca Highlight`.

## Características

- Android 10+ (`minSdk 29`), pacote `com.binuca.highlight`.
- Abre diretamente na câmera após as permissões obrigatórias.
- 1080p/30 fps a aproximadamente 8 Mb/s, com fallback para 720p/30 fps a aproximadamente 4 Mb/s.
- Buffer circular somente em RAM; nenhum arquivo temporário ou vídeo contínuo.
- Highlights sobrepostos e pedidos consecutivos, com salvamento serializado em segundo plano.
- Zoom por pinça e atalhos, toque para foco/exposição, compensação de exposição, lanterna e troca de câmera.
- Controle remoto por `KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN` ou `KEYCODE_CAMERA` enquanto a Activity está em primeiro plano.
- Interface e mensagens em português brasileiro, sem conta, anúncios, analytics ou permissão de internet.

## Compilar

O projeto fixa JDK 17, Gradle 9.6, AGP 9.4, Kotlin 2.3.21, Compose BOM 2026.08.00, CameraX 1.6.2 e compile/target SDK 37.

No PowerShell, com `JAVA_HOME` e `ANDROID_SDK_ROOT` configurados:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat copyDebugApk
```

O APK final é copiado para `dist/Binuca-Highlight-debug.apk`.

## Instalar por ADB

Ative a depuração USB no aparelho, conecte-o e aceite a autorização exibida na tela. Depois execute:

```powershell
adb install -r .\dist\Binuca-Highlight-debug.apk
```

Para acompanhar erros de câmera/codec durante testes:

```powershell
adb logcat | Select-String "Binuca|CameraX|MediaCodec"
```

## Locuções da OpenAI

O APK não contém chave, cliente de API ou permissão de internet. Os três MP3s são recursos empacotados em `app/src/main/res/raw` e o painel informa que a voz foi gerada por IA.

Para gerar novamente os arquivos, defina `OPENAI_API_KEY` somente numa sessão local segura e execute:

```powershell
.\scripts\generate-openai-voices.ps1
```

Nunca grave a chave no repositório, `gradle.properties`, código-fonte ou histórico do terminal compartilhado.

## Limites do teste remoto

A Mi Band precisa estar pareada pelo Mi Fitness como controle de câmera Bluetooth/HID e realmente entregar uma tecla de volume ao Android. A compatibilidade final depende da combinação de firmware da pulseira, Mi Fitness e aparelho Samsung. O aplicativo não roda em segundo plano e libera câmera, microfone e RAM quando sai do primeiro plano.
