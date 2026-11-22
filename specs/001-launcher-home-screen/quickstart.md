# Quickstart — Ecrã inicial do launcher

**Feature**: `001-launcher-home-screen` | **Alvo**: Google TV / Android 14 (API 34)

Guia de construção, instalação e verificação na TV. As regras de segurança do
dispositivo estão em `AGENTS.md` e prevalecem sobre este documento.

---

## 1. Pré-requisitos

| Item | Valor |
|---|---|
| JDK | `C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot` |
| Android CLI | `…\WinGet\Packages\Google.AndroidCLI_…\android.exe` (1.1.16919734) |
| SDK | `C:\Users\rodo\AppData\Local\Android\Sdk` |
| adb | `C:\platform-tools\adb.exe` |
| Pacotes SDK | `platforms;android-34`, `build-tools;34.0.0` |

Sessões abertas antes da instalação do JDK 17 podem ter `java` 8 no PATH:

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

Antes de qualquer comando: `adb devices` e confirmar o serial. Com mais de um
dispositivo, usar sempre `-s <serial>`.

## 2. Scaffold (uma vez)

O projeto nasce do template do Android CLI, não escrito à mão. O template atual gera
AGP 9 / `compileSdk 37`, incompatível com a constituição; por isso o scaffold é criado
num diretório temporário e as versões são fixadas ao entrar no repositório.

```powershell
$out="$env:TEMP\tvlauncher-scaffold"
android create empty-activity `
  --name "TV Launcher" `
  --application-id "com.mrcarromesa.tvlauncher" `
  --namespace "com.mrcarromesa.tvlauncher" `
  --min-sdk 31 `
  --output $out
```

Copiar para a raiz do repositório: `app/`, `gradle/`, `gradlew`, `gradlew.bat`,
`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `.gitignore`.
Não copiar `local.properties` (cada máquina tem o seu).

Depois, fixar em `gradle/libs.versions.toml` (ver
[research.md](research.md#r6--cadeia-de-build-compatível-com-compilesdk-34)):

```toml
androidGradlePlugin = "8.7.3"
kotlin = "2.0.21"
androidxComposeBom = "2024.06.00"
androidxCore = "1.13.1"
androidxActivity = "1.9.0"
androidxLifecycle = "2.8.3"
```

e em `app/build.gradle.kts`: `compileSdk = 34`, `targetSdk = 34`,
`minSdk = 31`; remover as dependências Navigation3. `gradle-wrapper.properties`:
`gradle-8.9-bin.zip`.

Dependências a acrescentar:

```kotlin
implementation("androidx.tv:tv-material:1.0.0")
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-ui:1.3.1")
implementation("io.coil-kt:coil-compose:2.6.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

## 3. Construir, instalar, correr

```powershell
android sdk install "platforms;android-34"
android sdk install "build-tools;34.0.0"
android build
android install --device <serial>
android run --device <serial>
```

Alternativas diretas:

```powershell
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> shell am start -n com.mrcarromesa.tvlauncher/.MainActivity
adb -s <serial> shell am force-stop com.mrcarromesa.tvlauncher
```

Testes JVM (parsers):

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## 4. Definir como HOME (manual, na TV)

O launcher **não** se define como HOME. Na primeira execução aparece um banner com o
caminho:

> Definições → Apps → Apps predefinidas → Início → TV Launcher

Nunca usar `adb shell cmd package set-home-activity`, `settings put` ou equivalente.

## 5. Verificação na TV

Cada linha é uma verificação manual no dispositivo. Uma tarefa só conta como
concluída depois disto (Princípio V).

### US1 — Dock (P1)

| # | Passo | Esperado |
|---|---|---|
| 1 | Abrir o launcher, sem tocar em nada | dock visível, foco em Netflix |
| 2 | → quatro vezes | foco percorre YouTube, HDMI 1, HDMI 2, Todos os Apps, um por pressão |
| 3 | OK em Netflix / YouTube | app nativa abre em ecrã completo |
| 4 | Voltar da app | ecrã inicial com foco no item que lançou |
| 5 | OK em "Todos os Apps" | grelha com as apps instaladas, sem o launcher |
| 6 | OK numa app da grelha | app abre; Voltar volta ao ecrã inicial |
| 7 | Voltar da grelha | foco em "Todos os Apps" |
| 8 | Desinstalar Netflix (noutro dispositivo de teste, se aplicável) | item esbatido; OK mostra mensagem; nada fecha |
| 9 | OK em HDMI 1 | diálogo com o procedimento manual no comando |
| 10 | Launcher não é HOME | banner com instruções; nada é alterado |

### US2 — Cabeçalho (P2)

| # | Passo | Esperado |
|---|---|---|
| 1 | Observar a hora durante 2 min | muda ao minuto, sem interação |
| 2 | Comparar com a hora do sistema | iguais |
| 3 | Desligar o Wi-Fi | estado passa a "Sem ligação"; hora e data corretas |
| 4 | Comparar temperatura do cabeçalho e do painel | mesmo valor |

### US3 — Clima (P3)

| # | Passo | Esperado |
|---|---|---|
| 1 | Primeira execução com rede | localização automática; temperatura, condição, máxima, mínima |
| 2 | Ver previsão horária | ≥ 5 horas com hora e temperatura |
| 3 | Desligar a rede | mantém valores + "Atualizado há …"; sem erro |
| 4 | Sem rede desde o início | "Conteúdo indisponível"; resto do ecrã funciona |
| 5 | Confirmar atribuição | "Dados: Open-Meteo (CC BY 4.0)" |

### US4 — JW.org (P4)

| # | Passo | Esperado |
|---|---|---|
| 1 | Com rede | 4 entradas: novidades, texto do dia, Broadcasting, Boletim |
| 2 | OK no texto do dia | abre em modo de leitura; Voltar regressa |
| 3 | OK no Broadcasting | vídeo reproduz; Voltar regressa |
| 4 | OK no Boletim | vídeo reproduz; Voltar regressa |
| 5 | Reiniciar sem rede | últimas 4 entradas continuam visíveis + data de atualização |
| 6 | Deixar aberto > 1 h | clima atualiza sozinho; ecrã não bloqueia |

### Critérios de sucesso

| Critério | Como confirmar |
|---|---|
| SC-001 | qualquer app do dock em ≤ 3 teclas |
| SC-002 | todos os fluxos só com D-pad |
| SC-003 | destaque de foco < 200 ms (observação + `adb shell dumpsys gfxinfo`) |
| SC-004 | ecrã completo < 2 s após acordar/voltar |
| SC-005 | 1080p e 4K sem cortes nem sobreposições |
| SC-006 | sem rede: dock navegável, hora/data corretas, sem erros técnicos |
| SC-007 | clima atualiza ≥ 1×/h; JW ≥ 1×/dia |
| SC-008 | conteúdo offline ≤ 24 h, com idade visível |
| SC-009 | legível a 3 m num 55" |
| SC-010 | nenhuma definição da TV alterada (rever `AGENTS.md`) |

## 6. Diagnóstico

```powershell
adb -s <serial> logcat -s TvLauncher:D
adb -s <serial> shell dumpsys activity activities | findstr mResumedActivity
adb -s <serial> shell dumpsys window | findstr mCurrentFocus
```

- **Sem rede**: normal; os painéis mostram cache/indisponibilidade.
- **JW.org vazio**: verificar `logcat` para `ParseFailure`; a estrutura do site pode
  ter mudado — atualizar o parser e a fixture.
- **Clima errado**: o IP pode estar a sair por VPN; `place.json` guarda a localização
  usada (apagar o ficheiro força nova deteção).
- **Foco preso**: confirmar que o item está dentro da área segura e que a lista faz
  scroll; nenhum `focusRequester` deve apontar para um item não composto.
