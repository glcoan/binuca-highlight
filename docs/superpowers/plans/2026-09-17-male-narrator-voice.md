# Male Sports Narrator Voice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Regenerate the three packaged Portuguese confirmations with a masculine, highly animated professional sports-narrator delivery and the approved “Pulta cagada!!” copy.

**Architecture:** Keep speech generation outside the Android runtime. Update the existing PowerShell generator defaults and prompts, generate immutable MP3 resources through the OpenAI speech endpoint, then rebuild the offline APK so `FeedbackController` continues using compile-time `R.raw` references.

**Tech Stack:** PowerShell, OpenAI `/v1/audio/speech`, `gpt-4o-mini-tts-2025-12-15`, Android raw resources, Gradle 9.6.

---

### Task 1: Update the repeatable voice generator

**Files:**
- Modify: `scripts/generate-openai-voices.ps1`
- Verify: `docs/superpowers/specs/2026-09-17-male-narrator-voice-design.md`

- [x] **Step 1: Run a static assertion that demonstrates the old defaults fail the new specification**

Run:

```powershell
$script = Get-Content .\scripts\generate-openai-voices.ps1 -Raw
if ($script -notmatch '\$Voice = "onyx"' -or $script -notmatch 'Pulta cagada') { exit 1 }
```

Expected: exit code `1`, because the current generator still uses `coral` and the old confirmation text.

- [x] **Step 2: Change the voice, texts, and direction**

Set the parameter default to:

```powershell
[string]$Voice = "onyx"
```

Set the three inputs to:

```powershell
@{ Seconds = 10; Text = "Clipe de dez segundos gravado! Pulta cagada!!" }
@{ Seconds = 20; Text = "Clipe de vinte segundos gravado! Pulta cagada!!" }
@{ Seconds = 30; Text = "Clipe de trinta segundos gravado! Pulta cagada!!" }
```

Use this instruction without allowing additional words or effects:

```text
Fale em português brasileiro com voz masculina grave de narrador esportivo profissional. Entregue a frase com energia muito alta, confiança, ritmo ágil e um crescendo explosivo de celebração no final. Mantenha dicção clara. Pronuncie exatamente o texto fornecido, sem acrescentar bordões, palavras, música, torcida ou efeitos.
```

- [x] **Step 3: Run the static assertion again**

Run the Step 1 command.

Expected: exit code `0`.

- [x] **Step 4: Commit the generator change**

```powershell
git add scripts\generate-openai-voices.ps1
git commit -m "feat: use male sports narrator confirmations"
```

### Task 2: Regenerate resources and verify the APK

**Files:**
- Replace: `app/src/main/res/raw/clip_voice_10.mp3`
- Replace: `app/src/main/res/raw/clip_voice_20.mp3`
- Replace: `app/src/main/res/raw/clip_voice_30.mp3`
- Replace: `dist/Binuca-Highlight-debug.apk`

- [x] **Step 1: Generate all three MP3 resources**

Load `OPENAI_API_KEY` from the ignored `.env` into the current process without printing it, run:

```powershell
.\scripts\generate-openai-voices.ps1
```

Expected: three `Gerado:` lines and exit code `0`.

- [x] **Step 2: Validate generated resource headers and sizes**

Read only the first three bytes and size of each MP3.

Expected: three non-empty files with an MPEG frame header beginning with `FF` or an `ID3` header.

- [x] **Step 3: Run the complete local verification**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug copyDebugApk
```

Expected: `BUILD SUCCESSFUL`, zero failed unit tests and zero lint errors.

- [x] **Step 4: Inspect the packaged APK**

Use `apkanalyzer manifest permissions` and list ZIP entries.

Expected: no `android.permission.INTERNET`, and all three compiled raw voice resources are present.

- [x] **Step 5: Attempt instrumented tests**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected with a connected Android 10+ device: tests pass. If no device is connected, report that environmental blocker rather than treating instrumented coverage as passed.

Actual result: instrumentation APK compiled successfully, but execution stopped with `No connected devices!`.

- [x] **Step 6: Commit generated resources and verified application changes**

```powershell
git add app scripts gradle build.gradle.kts settings.gradle.kts gradle.properties gradlew gradlew.bat README.md .gitignore
git commit -m "feat: build Binuca Highlight Android app"
```
