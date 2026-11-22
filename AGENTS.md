# AGENTS.md — google-tv-launch

Launcher para Google TV. Alvo: Android 14 (API 34).

## Regras de ouro (não negociáveis)

### Na TV: só isto, e só sobre o nosso pacote `com.mrcarromesa.tvlauncher`
- instalar — `android install` / `adb install -r`
- iniciar — `android run` / `adb shell am start -n com.mrcarromesa.tvlauncher/.MainActivity`
- parar — `adb shell am force-stop com.mrcarromesa.tvlauncher`
- remover — `adb uninstall com.mrcarromesa.tvlauncher`

### Leitura (sem risco, sempre ok)
`adb devices`, `adb logcat`, `adb shell dumpsys …`, `android screen`, `android layout`

### Proibido, sem exceção
- `uninstall` / `pm clear` de qualquer outro pacote
- `settings put`, `setprop`, `pm disable|enable`, `cmd *` (inclui `cmd package set-home-activity`), `reboot`, `shell rm|mv|cp`, `root`/`su`
- `android device` (espelha e instala helper na TV), `android emulator`, `android profiler`
- **Definir-nos como launcher por ADB.** O app deteta que não é o HOME e mostra
  instruções ao utilizador (Settings → Apps → Default apps → Home). Nunca por comando.

### Guard do `input` (exceção autorizada)
`android input` só depois de confirmar o foco:

```
adb shell dumpsys activity activities | findstr mResumedActivity
```

Se o resultado não for `com.mrcarromesa.tvlauncher`, **parar**. Nunca `KEYCODE_HOME`,
nunca cadeias de `BACK`, nunca `input` com foco fora da app.

### Antes de qualquer comando
`adb devices`, confirmar o serial. Com mais de um device, sempre `-s <serial>`.

## Ambiente (verificado 2026-11-22)

| | |
|---|---|
| JDK 17 | `C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot` |
| Android CLI | `…\WinGet\Packages\Google.AndroidCLI_…\android.exe` (v1.1.16919734) |
| SDK | `C:\Users\rodo\AppData\Local\Android\Sdk` |
| adb | `C:\platform-tools\adb.exe` |

Sessões abertas antes da instalação do JDK 17 têm `java` = 8 no PATH. Corrigir sem reiniciar:

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

## Stack

Kotlin · `com.mrcarromesa.tvlauncher` · compileSdk 34 · targetSdk 34 · minSdk 31.
Sem permissões que não sejam usadas. Sem dependências a mais.

## Comandos canónicos

```
android sdk install "platforms;android-34"
android sdk install "build-tools;34.0.0"
android build
android run --device <serial>
adb shell am force-stop com.mrcarromesa.tvlauncher
adb uninstall com.mrcarromesa.tvlauncher
```

## Notas

- `.specify/` é spec-kit; `constitution.md` ainda é template (princípios de produto, não regras da TV).
- Regras da TV vivem **só** aqui. Não duplicar.
