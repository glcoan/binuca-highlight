param(
    [string]$Model = "gpt-4o-mini-tts-2025-12-15",
    [string]$Voice = "onyx"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
    throw "Defina OPENAI_API_KEY apenas nesta sessão do terminal antes de executar o script."
}

$outputDirectory = Join-Path $PSScriptRoot "..\app\src\main\res\raw"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$lines = @(
    @{ Seconds = 10; Text = "Clipe de dez segundos gravado! Pulta cagada!!" },
    @{ Seconds = 20; Text = "Clipe de vinte segundos gravado! Pulta cagada!!" },
    @{ Seconds = 30; Text = "Clipe de trinta segundos gravado! Pulta cagada!!" }
)

foreach ($line in $lines) {
    $body = @{
        model = $Model
        voice = $Voice
        input = $line.Text
        instructions = "Fale em português brasileiro com voz masculina grave de narrador esportivo profissional. Entregue a frase com energia muito alta, confiança, ritmo ágil e um crescendo explosivo de celebração no final. Mantenha dicção clara. Pronuncie exatamente o texto fornecido, sem acrescentar bordões, palavras, música, torcida ou efeitos."
        response_format = "mp3"
    } | ConvertTo-Json

    $output = Join-Path $outputDirectory "clip_voice_$($line.Seconds).mp3"
    Invoke-WebRequest `
        -Uri "https://api.openai.com/v1/audio/speech" `
        -Method Post `
        -Headers @{ Authorization = "Bearer $($env:OPENAI_API_KEY)" } `
        -ContentType "application/json" `
        -Body $body `
        -OutFile $output
    Write-Host "Gerado: $output"
}

Write-Host "As locuções foram geradas. Remova OPENAI_API_KEY da sessão quando terminar."
