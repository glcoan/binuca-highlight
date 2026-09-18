# Binuca Highlight

Aplicativo Android para registrar as melhores jogadas de sinuca sem armazenar a partida inteira. Deixe o celular apontado para a mesa e, depois de uma jogada, toque em **10 s**, **20 s** ou **30 s** para salvar os últimos segundos com áudio diretamente na Galeria, em `Movies/Binuca Highlight`.

O app mantém aproximadamente 31 segundos de vídeo H.264 e áudio AAC em um buffer circular na RAM. Só os highlights solicitados são gravados no armazenamento. O uso da câmera continua consumindo bateria e memória enquanto a sessão está ativa.

## Características

- Android 10+ (`minSdk 29`), pacote `com.binuca.highlight`.
- Abre diretamente na câmera após as permissões obrigatórias.
- Qualidades selecionáveis: 1080p/30 fps a aproximadamente 8 Mb/s e 720p/30 fps a aproximadamente 4 Mb/s; resolução efetiva depende da câmera.
- Buffer circular somente em RAM; nenhum arquivo temporário ou vídeo contínuo.
- Highlights sobrepostos, com salvamento serializado. Salvar não limpa o buffer; há um intervalo de 5 segundos entre pedidos aceitos.
- Zoom por pinça e atalhos, toque para foco/exposição, compensação de exposição, lanterna e troca de câmera.
- Controle remoto por `KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN` ou `KEYCODE_CAMERA` enquanto a Activity está em primeiro plano.
- Interface e mensagens em português brasileiro, sem conta, anúncios, analytics ou permissão de internet.
- Confirmação por MP3 de caixa registradora, locução e vibração, configuráveis separadamente. Som e voz usam o volume de mídia e começam juntos quando ambos estão habilitados.
- Retrato e paisagem, com confirmação antes de mudar a orientação quando já existe conteúdo no buffer. A mudança de orientação reinicia a sessão.

## Compatibilidade e limites

Requer Android 10 ou superior, câmera, microfone e codecs H.264/AAC. Houve teste manual no Galaxy S24+; não há garantia de funcionamento em todo modelo Android. Se necessário, selecione 720p nas configurações.

Zoom, ultra-wide, flash e exposição dependem das capacidades públicas disponibilizadas pelo fabricante. Os atalhos de zoom e a barra usam a mesma sessão e o mesmo intervalo disponível. Quando uma lente ultra-wide separada é escolhida no início, as ampliações usam zoom nessa lente e podem perder qualidade. Modos exclusivos do app Câmera do fabricante não são reproduzidos.

A sessão existe em primeiro plano: sair do aplicativo libera câmera, microfone e buffer. Os clipes salvos permanecem na Galeria. Como o início precisa coincidir com um quadro-chave, o clipe pode ter aproximadamente um segundo adicional. O microfone pode captar os próprios sons de confirmação em clipes posteriores.

## Compilar

O projeto fixa JDK 17, Gradle 9.6, AGP 9.4, Kotlin 2.3.21, Compose BOM 2026.08.00, CameraX 1.6.2 e compile/target SDK 37.

### Preparação

1. Instale Git, JDK 17 e o Android SDK (pelo Android Studio ou pelas Command-line Tools).
2. No SDK Manager, instale **Android SDK Platform 37**, **Build-Tools 37.0.0** e **Platform-Tools**. Aceite as licenças do SDK.
3. Clone o repositório e abra um terminal na pasta do projeto.
4. Configure `JAVA_HOME` apontando para o JDK 17 e `ANDROID_HOME` para o SDK. Não é necessário instalar Gradle separadamente: use o wrapper incluído.

Exemplo no PowerShell (ajuste os caminhos):

```powershell
$env:JAVA_HOME = 'C:\caminho\para\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
```

Também é possível configurar o caminho do SDK em `local.properties` via Android Studio. Esse arquivo é local e não deve ser versionado. A primeira compilação precisa de internet para baixar dependências; o aplicativo instalado funciona offline. Nenhuma chave OpenAI é necessária para compilar ou usar o APK, pois os MP3s já estão incluídos.

### Gerar o APK debug

No Windows/PowerShell:

```powershell
.\gradlew.bat copyDebugApk
```

No Linux/macOS, com os mesmos requisitos configurados:

```sh
sh ./gradlew copyDebugApk
```

O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk` e copiado para `dist/Binuca-Highlight-debug.apk`. Após substituir os áudios em `app/src/main/res/raw`, execute novamente o mesmo comando. Nomes de recursos devem usar letras minúsculas, números e underscores, sem espaços ou hífens.

O build é **debug**, assinado com a chave de desenvolvimento da máquina. Para atualizar uma instalação existente, a assinatura deve ser a mesma; builds de outra máquina podem usar outra chave. Não publique nem versione chaves privadas de assinatura.

### Verificações

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

O último comando exige um dispositivo ou emulador conectado e autorizado. Testes locais não substituem testes de câmera, microfone, gravação prolongada, rotação e reprodução dos MP4s em aparelhos reais. Se o daemon Kotlin apresentar problemas de permissão, use `.\gradlew.bat copyDebugApk '-Pkotlin.compiler.execution.strategy=in-process'` no PowerShell.

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

As locuções originais foram geradas por IA da OpenAI. O APK não contém chave, cliente de API ou permissão de internet. Os três MP3s são recursos empacotados em `app/src/main/res/raw`. O quarto MP3, `cash_register_purchase.mp3`, é o efeito de caixa registradora fornecido ao projeto.

Para gerar novamente os arquivos, defina `OPENAI_API_KEY` somente numa sessão local segura e execute:

```powershell
.\scripts\generate-openai-voices.ps1
```

Nunca grave a chave no repositório, `gradle.properties`, código-fonte ou histórico do terminal compartilhado.

## Arquivos locais e segurança

O `.gitignore` exclui `.env*`, SDK/JDK locais (`.tooling/`), caches, `local.properties`, chaves de assinatura, builds e APKs. Os MP3s e o Gradle Wrapper são arquivos necessários ao projeto e ficam versionados. Não adicione credenciais nem force a inclusão de arquivos ignorados.

Os áudios fornecidos ao projeto devem ter sua origem e direitos de uso verificados antes de redistribuição pública. Este repositório não inclui licença de terceiros para o efeito sonoro.

## Limites do teste remoto

A Mi Band precisa estar pareada pelo Mi Fitness como controle de câmera Bluetooth/HID e realmente entregar uma tecla de volume ao Android. A compatibilidade final depende da combinação de firmware da pulseira, Mi Fitness e aparelho Samsung. O aplicativo não roda em segundo plano e libera câmera, microfone e RAM quando sai do primeiro plano.
