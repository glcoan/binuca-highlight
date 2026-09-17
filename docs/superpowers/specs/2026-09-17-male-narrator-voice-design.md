# Binuca Highlight — voz masculina de narrador esportivo

## Objetivo

Substituir as três confirmações faladas atuais por locuções masculinas em português brasileiro, com energia de narrador esportivo profissional. A confirmação deve celebrar o highlight com intensidade, sem prejudicar a compreensão nem tornar o aviso longo.

## Direção vocal

- Modelo: `gpt-4o-mini-tts-2025-12-15`.
- Voz: `onyx`, escolhida pelo timbre masculino e grave.
- Estilo: narrador esportivo brasileiro profissional, muito animado, confiante e exaltado.
- Entonação: entrada forte, ritmo ágil, dicção clara e crescendo de celebração no final.
- Restrições: não acrescentar bordões, palavras, música, torcida ou efeitos sonoros ao texto fornecido.

## Textos

- “Clipe de dez segundos registrado com sucesso!”
- “Clipe de vinte segundos registrado com sucesso!”
- “Clipe de trinta segundos registrado com sucesso!”

## Integração

O script `scripts/generate-openai-voices.ps1` passará a usar `onyx` e a nova direção vocal por padrão. A geração substituirá `clip_voice_10.mp3`, `clip_voice_20.mp3` e `clip_voice_30.mp3` em `app/src/main/res/raw`. O aplicativo continuará offline e sem chave ou permissão de internet.

## Verificação

- Confirmar que os três arquivos foram recriados e possuem conteúdo MP3 válido.
- Recompilar os recursos Android e executar testes unitários e lint.
- Gerar novamente `dist/Binuca-Highlight-debug.apk`.
- Confirmar que o APK contém os três recursos de voz e não solicita a permissão `INTERNET`.
