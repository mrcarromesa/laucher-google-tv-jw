# Implementation Plan: Ecrã Inicial do Launcher Google TV

**Branch**: `001-launcher-home-screen` | **Date**: 2026-11-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-launcher-home-screen/spec.md`

## Summary

Um único ecrã inicial em Compose for TV com quatro áreas — cabeçalho (hora, data,
Wi-Fi, temperatura), painel de clima, painel JW.org e dock (Netflix, YouTube, HDMI 1,
HDMI 2, Todos os Apps) — mais três ecrãs de apoio: grelha de apps, leitura e
reprodução de vídeo.

O launcher arranca e é navegável sem rede: dock, grelha, hora e data vêm do sistema.
Clima e JW.org são lidos de duas fontes públicas sem chave (Open-Meteo + `ipwho.is` e
as páginas/mediator API do jw.org), guardados em ficheiros JSON privados e
apresentados com indicação de idade. A estética "Liquid Glass" faz-se com um fundo
estático desfocado uma vez e painéis translúcidos; nunca há desfoque por frame. Toda
a interação é D-pad. O launcher deteta que não é o HOME e explica como o definir, sem
nunca alterar definições.

## Technical Context

**Language/Version**: Kotlin 2.0.21, JVM 17 (`jvmToolchain(17)`).

**Primary Dependencies**: Compose for TV `androidx.tv:tv-material:1.0.0`; Compose BOM
2024.06.00; `activity-compose:1.9.0`; `lifecycle-*:2.8.3`; Coil 2.6.0 (miniaturas
remotas); Media3 ExoPlayer/UI 1.3.1 (reprodução dos vídeos JW); kotlinx-serialization
JSON 1.7.3; OkHttp 4.12.0. Sem Navigation, sem Room, sem DataStore, sem Play Services.

**Storage**: ficheiros JSON privados da app (`filesDir/weather.json`,
`filesDir/jw.json`), envelope `{schema, fetchedAt, payload}`, escrita atómica.
Sem base de dados.

**Testing**: JUnit 4 + `kotlinx-coroutines-test` para os parsers e a seleção de
conteúdo (fixtures capturadas das respostas reais). Verificação funcional manual na TV
(Princípio V); não há testes instrumentados na v1.

**Target Platform**: Google TV / Android 14 (API 34). `compileSdk 34`, `targetSdk 34`,
`minSdk 31`. Sem ramos por versão, fabricante ou idioma.

**Project Type**: aplicação Android, um único módulo (`app`).

**Performance Goals**: primeiro frame do ecrã inicial < 2 s após acordar ou voltar de
uma app (SC-004); destaque de foco visível em < 200 ms (SC-003/FR-025); nenhum
desfoque de ecrã inteiro por frame; arranque não bloqueia em rede (cache primeiro).

**Constraints**: offline-first; D-pad apenas; nada escreve em definições da TV, de
outros pacotes ou do HOME; 1920×1080 e 3840×2160 sem cortes (margem segura de 48 dp);
interface em português, 24 h, °C.

**Scale/Scope**: uma TV, um utilizador; ~10–120 apps abríveis; 4 entradas JW.org;
5+ horas de previsão; 3 ecrãs de apoio (grelha, leitura, reprodução).

## Constitution Check

*GATE: verificado antes da Phase 0 e re-verificado depois do design da Phase 1.*

### Antes da Phase 0 (research)

| Princípio | Verificação | Estado |
|---|---|---|
| **I. Dispositivo inviolável** | O plano não contém `settings`, `cmd`, `pm`, `reboot` nem definição de HOME. O launcher só lê o papel HOME (`RoleManager`) e mostra instruções. Comandos de TV limitados aos de `AGENTS.md`. | PASS |
| **II. Alvo explícito** | Um alvo: API 34 / minSdk 31. Sem `Build.VERSION` condicional, sem fabricantes, sem idiomas alternativos. `Modifier.blur` só existe a partir de 31, logo não precisa de fallback. | PASS |
| **III. Só D-pad** | Todas as áreas são focáveis e acionáveis com direções, OK e Voltar. Sem toque, gestos ou teclado. | PASS |
| **IV. Mínimo justificado** | Dependências: tv-material (UI de TV), Coil (miniaturas remotas), Media3 (vídeo), serialization (JSON), OkHttp (HTTP). Nenhuma permissão além de `INTERNET` e `ACCESS_NETWORK_STATE`. Sem Navigation/Room/DataStore. | PASS |
| **V. Verificado na TV** | O plano termina com verificação manual por user story em `quickstart.md`; nenhuma tarefa é dada por concluída só por compilar. | PASS |

### Depois da Phase 1 (design)

| Princípio | Verificação | Estado |
|---|---|---|
| **I** | `contracts/app-catalog.md` e `contracts/ui-behavior.md` não contêm qualquer escrita de definições. O único caminho de HDMI é um diálogo informativo. | PASS |
| **II** | `data-model.md` não tem campos de versão/fabricante/idioma; os endpoints fixam `pt`/`T`. Toolchain fixada (R6) para manter `compileSdk 34`. | PASS |
| **III** | `contracts/ui-behavior.md` define ordem de foco, tratamento de OK/Voltar e reposição de foco para os quatro ecrãs. | PASS |
| **IV** | O design não acrescenta ecrãs além dos quatro da spec; o cache é ficheiro, não base de dados; a lista de apps usa `<queries>` em vez de `QUERY_ALL_PACKAGES`. | PASS |
| **V** | `quickstart.md` mapeia cada critério de sucesso (SC-001…SC-010) a uma verificação na TV. | PASS |

Sem violações. `Complexity Tracking` fica vazio.

## Project Structure

### Documentation (this feature)

```text
specs/001-launcher-home-screen/
├── plan.md              # Este ficheiro
├── research.md          # Phase 0 — decisões R1–R7
├── data-model.md        # Phase 1 — entidades, invariantes, estados
├── quickstart.md        # Phase 1 — build, instalação e verificação na TV
├── contracts/
│   ├── weather-sources.md
│   ├── jw-sources.md
│   ├── app-catalog.md
│   └── ui-behavior.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md             # Phase 2 (/speckit.tasks — não criado aqui)
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── kotlin/com/mrcarromesa/tvlauncher/
    │   │   ├── MainActivity.kt
    │   │   ├── data/
    │   │   │   ├── AppCatalog.kt          # apps abríveis, ícones, lançamento
    │   │   │   ├── ConnectivityMonitor.kt # Wi-Fi atual (callback → StateFlow)
    │   │   │   ├── HomeRole.kt            # RoleManager.isRoleHeld(ROLE_HOME)
    │   │   │   ├── Http.kt                # GET com timeout, UA fixo
    │   │   │   ├── JsonCache.kt           # envelope + escrita atómica
    │   │   │   ├── weather/
    │   │   │   │   ├── WeatherRepository.kt
    │   │   │   │   ├── WeatherSources.kt  # ipwho.is + Open-Meteo + WMO
    │   │   │   │   └── WeatherCode.kt
    │   │   │   └── jw/
    │   │   │       ├── JwRepository.kt
    │   │   │       └── JwSources.kt       # mediator, novidades, WOL
    │   │   ├── model/
    │   │   │   ├── Dock.kt
    │   │   │   ├── Weather.kt
    │   │   │   └── Jw.kt
    │   │   └── ui/
    │   │       ├── LauncherRoot.kt        # estado de ecrã + Voltar
    │   │       ├── home/HomeScreen.kt
    │   │       ├── home/HomeViewModel.kt
    │   │       ├── apps/AllAppsScreen.kt
    │   │       ├── reader/ReaderScreen.kt # WebView em modo leitura
    │   │       ├── player/PlayerScreen.kt # Media3
    │   │       └── theme/
    │   │           ├── Theme.kt
    │   │           └── Glass.kt           # painel, borda, reflexo, foco
    │   └── res/
    │       ├── drawable/                  # fundo estático, ícones
    │       └── values/                    # strings PT, tema
    └── test/
        ├── kotlin/com/mrcarromesa/tvlauncher/data/
        │   ├── jw/JwSourcesTest.kt
        │   └── weather/WeatherSourcesTest.kt
        └── resources/fixtures/            # respostas reais capturadas
```

**Structure Decision**: um módulo Android único (`app`), porque o produto é uma app
só e a constituição pede a menor superfície possível. O código separa `data`
(fontes, cache, catálogo), `model` (entidades de UI) e `ui` (quatro ecrãs + tema), sem
camadas de domínio/repositório abstratas além das duas que têm uso real (clima e
JW.org). O scaffold vem de `android create empty-activity`; as versões são fixadas em
`gradle/libs.versions.toml` conforme R6, e as dependências Navigation3 do template são
removidas — três ecrãs resolvem-se com um estado no `HomeViewModel`.

## Notas e Riscos

- **FR-005 (HDMI)**: a troca automática de entrada exige `BIND_TV_INPUT` (permissão de
  assinatura). Implementa-se o fallback obrigatório — diálogo com o procedimento no
  comando da TV — e não se escreve código de uma tentativa que não pode funcionar.
  Ver [research.md](research.md#r2--troca-de-entrada-hdmi).
- **jw.org**: HTML de terceiros; cada parser valida campos mínimos e, em falha, mantém
  o cache e a data anterior. Testes com fixtures detetam mudanças de estrutura.
- **Cache > 24 h**: o painel passa a "sem dados recentes" (SC-008) em vez de mostrar
  valores antigos como atuais; a data da última atualização continua visível.
- **Toolchain**: fixada em AGP 8.7.3 / Gradle 8.9 / Compose BOM 2024.06.00 para
  respeitar `compileSdk 34`. Subir qualquer peça exige rever a constituição (R6).

## Complexity Tracking

> Sem violações da constituição; nada a justificar.
