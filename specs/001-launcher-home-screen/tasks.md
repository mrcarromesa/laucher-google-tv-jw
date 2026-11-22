---

description: "Task list for the Google TV launcher home screen feature"
---

# Tasks: Ecrã Inicial do Launcher Google TV

**Input**: Design documents from `/specs/001-launcher-home-screen/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md, contracts/ (app-catalog.md, ui-behavior.md, weather-sources.md, jw-sources.md), `.specify/memory/constitution.md`

**Tests**: Incluídos para US3 e US4 porque o plan.md e os contratos `weather-sources.md`/`jw-sources.md` pedem explicitamente testes JUnit 4 + `kotlinx-coroutines-test` para os parsers e a seleção de conteúdo, com fixtures capturadas das respostas reais. US1 e US2 não têm testes unitários na v1 (verificação manual na TV, Princípio V).

**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1…US4)
- Include exact file paths in descriptions

## Path Conventions

- Single Android module: `app/src/main/kotlin/com/mrcarromesa/tvlauncher/` (abreviado `src/…`), `app/src/test/…`, `app/src/main/res/…`
- Tests JVM: `app/src/test/kotlin/com/mrcarromesa/tvlauncher/…` e fixtures em `app/src/test/resources/fixtures/`
- Verificação na TV: sempre segundo `quickstart.md` e `AGENTS.md` (na TV só instalar/iniciar/parar/remover `com.mrcarromesa.tvlauncher`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Scaffold do projeto, toolchain fixada (R6) e manifesto base. Nada de TV ainda.

- [X] T001 Scaffold via Android CLI em diretório temporário e copiar para a raiz do repositório: `app/`, `gradle/`, `gradlew`, `gradlew.bat`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `.gitignore` (NÃO copiar `local.properties`); comando e lista em `quickstart.md` §2
- [X] T002 Fixar versões em `gradle/libs.versions.toml`: `androidGradlePlugin = "8.7.3"`, `kotlin = "2.0.21"`, `androidxComposeBom = "2024.06.00"`, `androidxCore = "1.13.1"`, `androidxActivity = "1.9.0"`, `androidxLifecycle = "2.8.3"` (R6)
- [X] T003 Configurar `app/build.gradle.kts`: `compileSdk = 34`, `targetSdk = 34`, `minSdk = 31`, `jvmToolchain(17)`, e **remover** as dependências Navigation3 geradas pelo template (R6, plan.md "Structure Decision")
- [X] T004 [P] Definir `gradle/wrapper/gradle-wrapper.properties` para `gradle-8.9-bin.zip` (R6)
- [X] T005 [P] Acrescentar dependências em `app/build.gradle.kts`: `androidx.tv:tv-material:1.0.0`, `androidx.media3:media3-exoplayer:1.3.1`, `androidx.media3:media3-ui:1.3.1`, `io.coil-kt:coil-compose:2.6.0`, `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`, `com.squareup.okhttp3:okhttp:4.12.0` (quickstart.md §2)
- [X] T006 Configurar `app/src/main/AndroidManifest.xml`: permissões `INTERNET` e `ACCESS_NETWORK_STATE`; `<uses-feature android:name="android.software.leanback" android:required="true"/>` e `android.hardware.touchscreen required="false"`; bloco `<queries>` com `MAIN`/`LEANBACK_LAUNCHER` e `MAIN`/`LAUNCHER` (contracts/app-catalog.md §1 e §7 — sem `QUERY_ALL_PACKAGES`)
- [X] T007 Configurar `app/src/main/AndroidManifest.xml`: `<activity android:name=".MainActivity" android:exported="true">` com intent-filter `MAIN` + `HOME` + `DEFAULT` + `LEANBACK_LAUNCHER` (contracts/app-catalog.md §7)
- [X] T008 [P] Criar `app/src/main/res/values/strings.xml` com os textos PT de base (nome da app, banner HOME, diálogos de app em falta e HDMI, estados de painel, "Dados: Open-Meteo (CC BY 4.0)")
- [X] T009 [P] Criar ícone em `app/src/main/res/` e definir o mesmo recurso como `icon` e `android:banner` da activity (necessário para a lista de apps da TV — contracts/app-catalog.md §7)
- [X] T010 Verificar que o projeto compila com a toolchain fixada: `android build` (ou `.\gradlew.bat :app:assembleDebug`) sem erros

**Checkpoint**: Setup concluído — projeto compila com AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 / compileSdk 34.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestrutura partilhada que bloqueia TODAS as histórias: camada de dados (HTTP + cache), fundação visual "Liquid Glass" (R1) e o shell da app (MainActivity → LauncherRoot → HomeScreen/HomeViewModel).

**CRITICAL**: nenhuma história começa antes desta fase estar completa.

- [X] T011 [P] Implementar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/Http.kt`: GET com `User-Agent` fixo `TvLauncher/1.0 (Android TV)`, timeout configurável por chamada (weather 5 s / 8 s, jw 8 s — contracts/weather-sources.md §1–2 e jw-sources.md), erro de rede sem exceção lançada para a UI (resultado `Result`/selado)
- [X] T012 [P] Implementar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/JsonCache.kt`: envelope `CacheEnvelope<T>` `{schema, fetchedAt, payload}` com kotlinx-serialization; escrita atómica (`*.tmp` + `renameTo`); invariantes de data-model.md — "`schema != 1` ⇒ ficheiro ignorado (nunca apagado)" e "Falha de rede/parsing não altera o ficheiro"
- [X] T013 [P] Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/ui/theme/Theme.kt`: cores, tipografia com "Texto principal ≥ 24 sp; títulos ≥ 32 sp" (contracts/ui-behavior.md §7, SC-009), tema escuro base para os painéis translúcidos
- [X] T014 [P] Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/ui/theme/Glass.kt`: modificador de painel "Brush.verticalGradient de branco 0.18 → 0.08, border de 1 dp com gradiente, clip(RoundedCornerShape(28.dp)), sombra suave"; reflexo "faixa diagonal branca 0.06 no topo"; modificador de foco "escala 1.06 + brilho da borda, tween(120)"; contraste mínimo 3:1 (contracts/ui-behavior.md §6 e §2)
- [X] T015 [P] Criar o fundo estático (gradiente + arte) em `app/src/main/res/drawable/` — será desfocado uma vez com `Modifier.blur(48.dp)` (R1, contracts/ui-behavior.md §6)
- [X] T016 Ligar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/MainActivity.kt`: uma única activity, `setContent { LauncherRoot() }` (contracts/ui-behavior.md §1 — "Um só MainActivity")
- [X] T017 Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/ui/LauncherRoot.kt`: tipo `Screen` selado (só `Home` por agora; estendido por US1/US4) e `BackHandler` — Voltar em `Home` não faz nada; estado de ecrã vive no `HomeViewModel` e sobrevive a rotação/ida a outra app (contracts/ui-behavior.md §1)
- [X] T018 Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/ui/home/HomeViewModel.kt`: esqueleto com `Screen` em `StateFlow`; campos de `HomeUiState` adicionados incrementalmente por história (US1: dock/foco/isHome; US2: now/isOnline; US3: weather; US4: jw)
- [X] T019 Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/ui/home/HomeScreen.kt`: `Box` raiz com `padding(horizontal = 48.dp, vertical = 32.dp)` (margem segura contra overscan — contracts/ui-behavior.md §7), fundo estático desfocado uma vez (sem animação, sem recomposição por frame — R1), e as quatro áreas em placeholder (cabeçalho topo, clima + JW ao centro, dock na base)
- [X] T020 Instalar e iniciar na TV: `android install --device <serial>` + `android run --device <serial>`; confirmar que o shell abre sem rede, sem erros e sem crash (Princípio V; comandos de TV conforme `AGENTS.md`)

**Checkpoint**: Fundação pronta — a app instala e mostra o shell com fundo "Liquid Glass"; as histórias podem começar em paralelo.

---

## Phase 3: User Story 1 — Abrir uma app a partir do dock (Priority: P1) — MVP

**Goal**: dock horizontal com Netflix, YouTube, HDMI 1, HDMI 2 e "Todos os Apps"; abrir a app nativa com OK; grelha de apps; deteção de HOME com banner de instruções; diálogos de app em falta e HDMI.

**Independent Test**: "instalar a app na TV, defini-la como HOME, navegar no dock só com o D-pad e abrir cada um dos cinco itens" (spec.md US1; passos em quickstart.md §US1).

### Implementation for User Story 1

- [X] T021 [P] [US1] Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/model/Dock.kt`: selado `DockItem` com `App(packageName, installed, icon)` / `Hdmi(index: 1|2)` / `AllApps`, ordem fixa 0…4 (Netflix, YouTube, HDMI 1, HDMI 2, Todos os Apps); invariantes de data-model.md — "A ordem e o número de itens são constantes; um item nunca desaparece por a app não estar instalada" e "Hdmi nunca está 'indisponível': OK abre sempre o diálogo de ajuda"
- [X] T022 [P] [US1] Implementar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/AppCatalog.kt`: enumeração com `queryIntentActivities` para `MAIN`+`CATEGORY_LEANBACK_LAUNCHER` e `MAIN`+`CATEGORY_LAUNCHER`, união por `packageName`, excluir `com.mrcarromesa.tvlauncher`; `label = loadLabel`, `icon = loadIcon` (exceção → ícone genérico); manter só quem tem `getLeanbackLaunchIntentForPackage(pkg) ?: getLaunchIntentForPackage(pkg) != null`; ordenar por `label` com `Collator` de `pt` sem distinguir maiúsculas; lista vazia é estado válido (contracts/app-catalog.md §2)
- [X] T023 [P] [US1] Implementar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/HomeRole.kt`: `RoleManager.isRoleHeld(RoleManager.ROLE_HOME)`; sem nenhuma escrita em `RoleManager`, `Settings` ou pacotes (contracts/app-catalog.md §5)
- [X] T024 [US1] Em `ui/home/HomeViewModel.kt`: construir os 5 `DockItem` a partir de `AppCatalog`; índice de foco inicial 0, persistido e restaurado ao voltar de uma app; guarda `launching` — "duas pressões rápidas em OK não lançam duas instâncias: a ação é ignorada enquanto a anterior não regressa ao estado Resumed" (contracts/app-catalog.md §4)
- [X] T025 [US1] Em `ui/home/HomeScreen.kt`: composable do dock — linha horizontal com itens 168×120 dp, espaçamento 20 dp, altura 132 dp (contracts/ui-behavior.md §7); navegação ←/→ uma posição por pressão, sem saltar nos extremos; anel de foco via `Glass`; `contentDescription` com nome + estado ("Netflix, indisponível") (contracts/ui-behavior.md §9)
- [X] T026 [US1] Em `ui/home/HomeScreen.kt`: ações de OK do dock — app instalada → `startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))`; `ActivityNotFoundException`/intent nulo → diálogo "«nome» não está disponível" e foco fica onde está; HDMI 1/2 → diálogo "A TV não permite mudar de entrada a partir de uma app. No comando da TV, carregue no botão de entrada/origem e escolha HDMI n."; "Todos os Apps" → abrir grelha; criar um único composable de diálogo (título, corpo, "OK") reutilizado em todos os casos, OK/Voltar fecham e repõem o foco (contracts/ui-behavior.md §3, app-catalog.md §6)
- [X] T027 [US1] Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/ui/apps/AllAppsScreen.kt`: `LazyVerticalGrid` de 5 colunas, espaçamento 16 dp, ícones 96 dp (contracts/ui-behavior.md §7); nomes longos com `maxLines = 2` + `TextOverflow.Ellipsis` sem sobrepor o ícone; scroll para manter o item focado visível; estado "Sem apps" quando a lista é vazia
- [X] T028 [US1] Em `ui/home/HomeViewModel.kt` e `ui/LauncherRoot.kt`: navegação Home ↔ AllApps — OK numa app da grelha lança; Voltar regressa ao Home com foco em "Todos os Apps" (índice 4); ao voltar de uma app lançada da grelha, foco na app lançada (contracts/ui-behavior.md §2, FR-007)
- [X] T029 [US1] Em `ui/home/HomeScreen.kt`: banner de HOME quando `isHome == false` — texto "Para usar este ecrã como Início: Definições → Apps → Apps predefinidas → Início → Escolher «TV Launcher»." + nota "O launcher não altera esta definição."; não focável, não bloqueante, mantém-se enquanto o papel não for nosso (contracts/app-catalog.md §5); re-verificar o papel ao voltar ao ecrã
- [X] T030 [US1] Verificação na TV: executar os 10 passos de quickstart.md §US1 (dock, foco, abrir apps, grelha, app em falta, HDMI, banner HOME) — só termina com todos os passos esperados cumpridos

**Checkpoint**: US1 completa — dock + grelha + banner + diálogos funcionam na TV só com D-pad. Este é o MVP entregável.

---

## Phase 4: User Story 2 — Saber as horas, o dia e o estado da ligação (Priority: P2)

**Goal**: cabeçalho com hora `HH:mm` (24 h), data `EEE, d MMM` em `pt-PT`, estado Wi-Fi e temperatura (a temperatura é preenchida pela US3; até lá é omitida).

**Independent Test**: "com o ecrã inicial aberto, comparar hora e data com a hora do sistema, desligar o Wi-Fi e confirmar que o estado muda" (spec.md US2; passos em quickstart.md §US2).

### Implementation for User Story 2

- [X] T031 [US2] Implementar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/ConnectivityMonitor.kt`: `NetworkCallback` → `StateFlow<Boolean>` com `NET_CAPABILITY_INTERNET`; "Atualiza por callback, não por polling" (contracts/ui-behavior.md §4, FR-011)
- [X] T032 [US2] Em `ui/home/HomeViewModel.kt`: ticker de `now: LocalDateTime` — "now atualiza-se no minuto; a data muda à meia-noite sem reinício" (data-model.md `HomeUiState`); ligar `isOnline` do `ConnectivityMonitor` — "false não limpa nada" (data-model.md)
- [X] T033 [US2] Em `ui/home/HomeScreen.kt`: composable do cabeçalho — hora `HH:mm` e data `EEE, d MMM` em `pt-PT` (ex.: `sáb., 22 nov.`); Wi-Fi: ícone + "Wi-Fi" com rede, ícone cortado + "Sem ligação" sem rede; temperatura omitida quando o clima está indisponível (nunca um valor errado) (contracts/ui-behavior.md §4)
- [X] T034 [US2] Verificação na TV: quickstart.md §US2 (hora muda ao minuto sem interação, comparação com o sistema, desligar Wi-Fi → "Sem ligação", temperatura igual ao painel quando a US3 estiver feita)

**Checkpoint**: US2 completa — hora/data corretas e estado Wi-Fi fiel; temperatura aparece quando a US3 fornecer o clima.

---

## Phase 5: User Story 3 — Ver o clima de hoje e das próximas horas (Priority: P3)

**Goal**: painel de clima com temperatura, condição, máxima/mínima e previsão de ≥ 5 horas; localização automática (ipwho.is) e previsão Open-Meteo; cache JSON; degradação offline com idade visível.

**Independent Test**: "com rede, confirmar que a localização é detetada automaticamente e que os valores coincidem com um serviço meteorológico público para essa localização; desligar a rede e confirmar que os últimos valores continuam visíveis" (spec.md US3; passos em quickstart.md §US3).

### Tests for User Story 3

> **NOTE: escrever os testes PRIMEIRO e garantir que FALHAM antes da implementação** (WeatherSources.kt ainda não existe).

- [X] T035 [P] [US3] Capturar respostas reais e guardar em `app/src/test/resources/fixtures/ipwhois.json` e `app/src/test/resources/fixtures/open-meteo.json` (estrutura em contracts/weather-sources.md §1–2)
- [X] T036 [P] [US3] Criar `app/src/test/kotlin/com/mrcarromesa/tvlauncher/data/weather/WeatherSourcesTest.kt` (JUnit 4 + kotlinx-coroutines-test): sucesso com `success:true`/coordenadas/cidade; falha com `success != true` ou coordenadas ausentes; parsing de `current`/`daily`/`hourly`; "Pontos com localTime >= agora (no fuso do dispositivo), ordenados, primeiros 5"; `ParseFailure` quando "hourly tem de ter os três arrays com o mesmo comprimento", "Menos de 5 pontos futuros", ou campo obrigatório ausente (contracts/weather-sources.md §2); `weather_code` desconhecido → condição "—" (não é falha)
- [X] T037 [US3] Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/model/Weather.kt`: `Place`, `WeatherSnapshot(currentTempC, currentCode, maxC, minC, hourly, fetchedAt)`, `HourlyPoint(localTime, tempC, code)`; invariantes de data-model.md — "hourly é sempre filtrada para localTime >= agora e ordenada; mostram-se 5" e "maxC >= minC"; `WeatherCondition(code, labelPt, icon)`
- [X] T038 [US3] Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/weather/WeatherCode.kt`: tabela WMO fixa (0–3, 45/48, 51–57, 61–67, 71–77, 80–82, 85/86, 95–99) → `WeatherCondition`; "código desconhecido → '—'" (data-model.md)
- [X] T039 [US3] Implementar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/weather/WeatherSources.kt`: `GET https://ipwho.is/` (UA fixo, timeout 5 s; sucesso ⇒ gravar `place.json` e só voltar a pedir se faltar ou tiver > 30 dias; falha ⇒ usar `place.json` se existir) e `GET https://api.open-meteo.com/v1/forecast` com `current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&hourly=temperature_2m,weather_code&timezone=auto&forecast_days=2` (timeout 8 s); regras de parsing exatamente como em contracts/weather-sources.md §2 (R4)
- [X] T040 [US3] Implementar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/weather/WeatherRepository.kt`: ler cache `filesDir/weather.json` no arranque; política de atualização — "Ticker de 15 min (ecrã visível): se age > 60 min, atualizar"; "Refresh em curso: não iniciar outro (uma corrotina por fonte)"; "Falha: manter cache; não escrever; sem mensagem de erro técnica"; "Cache > 24 h: estado Unavailable com data do último sucesso" (contracts/weather-sources.md §3, R7)
- [X] T041 [US3] Em `ui/home/HomeViewModel.kt`: máquina de estados do clima — `Loading → Available(cache, age)` com cache válido ≤ 24 h; `Loading → Unavailable(null)` sem cache e sem rede; refresh em background com `age > 60 min`; `Available` com `age > 24 h → Unavailable(lastAttemptAt)` (data-model.md "Estados e transições — Clima")
- [X] T042 [US3] Em `ui/home/HomeScreen.kt`: painel de clima — temperatura grande, condição, máxima/mínima, 5 horas (hora + temperatura); rodapé `Atualizado há X min` ou `Sem dados recentes` + `Dados: Open-Meteo (CC BY 4.0)`; `Loading` com esqueleto da mesma altura (sem salto de layout); `Unavailable` sem erro técnico (contracts/ui-behavior.md §5, weather-sources.md §4)
- [X] T043 [US3] Em `ui/home/HomeScreen.kt`: ligar a temperatura do cabeçalho ao `currentTempC` do painel — "O cabeçalho usa exatamente currentTempC do painel (FR-009/US2-4) — uma só fonte" (data-model.md); omitida quando `Unavailable` (contracts/ui-behavior.md §4)
- [X] T044 [US3] Verificação na TV: quickstart.md §US3 (localização automática, ≥ 5 horas, offline mantém valores + idade, sem rede desde o início → indisponível, atribuição Open-Meteo)

**Checkpoint**: US3 completa — painel de clima e temperatura do cabeçalho funcionam; offline degrada com idade visível.

---

## Phase 6: User Story 4 — Acompanhar o conteúdo do jw.org (Priority: P4)

**Goal**: painel com 4 entradas (novidades, texto do dia, último Broadcasting, último Boletim do Corpo Governante); leitura em WebView; reprodução com Media3; atualização automática + cache.

**Independent Test**: "com rede, confirmar que as quatro entradas mostram o conteúdo mais recente publicado e que cada uma abre ou reproduz o respetivo conteúdo; reiniciar sem rede e confirmar que o último conteúdo guardado continua visível" (spec.md US4; passos em quickstart.md §US4).

### Tests for User Story 4

> **NOTE: escrever os testes PRIMEIRO e garantir que FALHAM antes da implementação** (JwSources.kt ainda não existe).

- [X] T045 [P] [US4] Capturar respostas reais e guardar em `app/src/test/resources/fixtures/whats-new.html`, `wol-daily.html`, `mediator-latest-videos.json`, `mediator-news.json` (contracts/jw-sources.md §8)
- [X] T046 [US4] Criar testes em `app/src/test/kotlin/com/mrcarromesa/tvlauncher/data/jw/JwSourcesTest.kt` para os parsers HTML: novidades — primeiro bloco `div.synopsis`+`docId`, `p.meta.pubDate`, `h3 > a[href]`, href relativo resolvido contra `https://www.jw.org`, `ParseFailure` se nenhum bloco válido; texto do dia — bloco `div.tabContent[data-date]` de hoje, senão o do meio (índice 1), `ParseFailure` se qualquer dos três textos vazio (contracts/jw-sources.md §1–2)
- [X] T047 [US4] Criar testes em `app/src/test/kotlin/com/mrcarromesa/tvlauncher/data/jw/JwSourcesTest.kt` para os parsers JSON: LatestVideos — primeiro item `primaryCategory == "StudioMonthlyPrograms"` ordenado por `firstPublished` desc; StudioNewsReports — título casa `^Boletim do Corpo Governante`, primeiro por `firstPublished` desc, `ParseFailure` se nenhum casar; escolha do vídeo — filtrar `mimetype == "video/mp4"`, maior altura entre `240p`…`1080p`, nunca `video/3gpp` nem URL fora de `*.jw-cdn.org` (contracts/jw-sources.md §3–5)

### Implementation for User Story 4

- [X] T048 [US4] Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/model/Jw.kt`: `JwSnapshot` com `whatsNew: JwTextEntry?`, `dailyText: JwDailyText?`, `broadcasting: JwVideoEntry?`, `governingBody: JwVideoEntry?`, `fetchedAt`; `JwTextEntry(title, pageUrl, imageUrl?, publishedOn?)`, `JwDailyText(dateLabel, scripture, comment, pageUrl)`, `JwVideoEntry(title, publishedAt?, posterUrl?, videoUrl, durationSeconds?)`; invariantes de data-model.md — "videoUrl é sempre um MP4 progressivo (mimetype == video/mp4, altura ≤ 1080)", "pageUrl de texto tem de pertencer a jw.org/wol.jw.org (allowlist do leitor)", "Entrada sem title ou sem URL de conteúdo é descartada"
- [X] T049 [US4] Implementar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/jw/JwSources.kt`: quatro fontes — `https://www.jw.org/pt/novidades/` (HTML), `https://wol.jw.org/pt/wol/h/r5/lp-t` (HTML), `https://b.jw-cdn.org/apis/mediator/v1/categories/T/LatestVideos?detailed=1&clientType=www` (JSON), `https://b.jw-cdn.org/apis/mediator/v1/categories/T/StudioNewsReports?detailed=1&clientType=www` (JSON); regras de seleção, validação de campos mínimos e `ParseFailure` exatamente como contracts/jw-sources.md §1–5 (R3); sem escrita de cache em falha
- [X] T050 [US4] Implementar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/data/jw/JwRepository.kt`: "Snapshot novo funde-se campo a campo com o anterior: uma fonte falhada mantém o valor antigo desse campo"; "Atualizar no arranque/volta ao ecrã e a cada 6 h enquanto visível"; "Cache > 24 h ⇒ painel Unavailable com a data do último sucesso"; falha de rede/HTTP≠200/parsing ⇒ `ParseFailure`, sem escrever cache nem `fetchedAt` (contracts/jw-sources.md §7, R7)
- [X] T051 [US4] Em `ui/home/HomeViewModel.kt`: máquina de estados JW (data-model.md "Estados e transições — JW.org") + agendamento de refresh + navegação para `Reader`/`Player` com reposição de foco na entrada de origem (contracts/ui-behavior.md §1)
- [X] T052 [US4] Em `ui/home/HomeScreen.kt`: painel JW.org — 4 linhas com tipo, título (2 linhas) e miniatura 16:9 nos vídeos via Coil; rodapé `Atualizado há X h`; `Unavailable` → "Conteúdo indisponível" + última data conhecida, sem erro técnico; foco: ↑/↓ entre entradas, ←/→ sem efeito (clima não é interativo), ↓ → dock no índice que estava focado; ↑ no dock → última entrada JW focada (contracts/ui-behavior.md §2)
- [X] T053 [US4] Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/ui/reader/ReaderScreen.kt`: WebView com o `pageUrl`, "JavaScript ativo só para injetar CSS de leitura; navegação limitada a jw.org/wol.jw.org; sem addJavascriptInterface"; Voltar fecha e repõe o foco na entrada (contracts/jw-sources.md §6, ui-behavior.md §2)
- [X] T054 [US4] Criar `app/src/main/kotlin/com/mrcarromesa/tvlauncher/ui/player/PlayerScreen.kt`: Media3 `ExoPlayer` + `PlayerView` com `videoUrl`, controlador visível, OK = play/pause, Voltar = sair e libertar o player (contracts/jw-sources.md §6)
- [X] T055 [US4] Em `ui/LauncherRoot.kt`: adicionar ramos `Reader` e `Player` ao `Screen` e respetivo `BackHandler` (Voltar → Home com foco na entrada de origem)
- [X] T056 [US4] Verificação na TV: quickstart.md §US4 (4 entradas, leitura, reprodução dos dois vídeos, reinício sem rede mantém cache, atualização automática sem bloquear)

**Checkpoint**: US4 completa — 4 entradas com leitura/reprodução e cache offline; foco dock ↔ JW integrado.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: validação completa na TV e garantias transversais da constituição.

- [ ] T057 Executar a validação completa de quickstart.md "Critérios de sucesso": SC-001…SC-010 confirmados na TV (incluindo SC-003 com `adb shell dumpsys gfxinfo`, SC-004, SC-005 em 1080p e 4K, SC-009 leitura a 3 m, SC-010 sem alterações de definições)
- [X] T058 [P] Desempenho: confirmar destaque de foco < 200 ms (`animateFloatAsState(tween(120))`), primeiro frame com cache < 500 ms e ecrã completo < 2 s após acordar/voltar, e que o desfoque do fundo é aplicado uma vez e nunca por frame (contracts/ui-behavior.md §8, R1, SC-003/SC-004)
- [X] T059 [P] Limpeza: remover código morto do template (imports/dependências Navigation3 remanescentes, recursos não usados); confirmar um único `MainActivity`, sem permissões ou dependências não usadas (Princípio IV, plan.md "Structure Decision")
- [X] T060 [P] Passagem de segurança: confirmar que nenhum caminho de código escreve definições da TV, de outros pacotes ou do HOME (SC-010, FR-026), que não há intents de fabricante para HDMI, e que o WebView não tem `addJavascriptInterface` e aplica a allowlist `jw.org`/`wol.jw.org` (Princípio I, R2)
- [ ] T061 Casos-limite de spec.md "Edge Cases": primeira utilização sem rede, app em falta, HDMI não suportado, localização negada, fonte jw.org alterada (falha silenciosa + cache), regresso de app com foco restaurado, navegação rápida sem lançamentos duplicados, nomes longos na grelha, 4K com overscan, mudança de fuso/hora de verão

**Checkpoint**: feature completa e validada na TV contra todos os critérios de sucesso.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências — pode começar de imediato
- **Foundational (Phase 2)**: depende do Setup — BLOQUEIA todas as histórias
- **User Stories (Phase 3–6)**: todas dependem da Foundational
  - Podem seguir em paralelo (se houver equipa) ou por prioridade (US1 → US2 → US3 → US4)
- **Polish (Phase 7)**: depende de todas as histórias pretendidas completas

### User Story Dependencies

- **US1 (P1)**: começa após a Foundational — sem dependências de outras histórias (MVP)
- **US2 (P2)**: começa após a Foundational — independente; a temperatura do cabeçalho é um fio deixado para a US3 (T043), até lá é omitida (contracts/ui-behavior.md §4)
- **US3 (P3)**: começa após a Foundational — independente; usa `Http`/`JsonCache` da Foundational
- **US4 (P4)**: começa após a Foundational — integra o foco ↑/↓ dock ↔ painel JW (T052); independente do resto

### Within Each User Story

- Testes (US3/US4) escritos e a FALHAR antes da implementação
- Modelos antes de serviços; serviços antes de UI; integração antes de verificação na TV
- Verificação na TV (quickstart.md) antes de dar a história por concluída (Princípio V)

### Parallel Opportunities

- **Setup**: T004, T005, T008, T009 em paralelo (ficheiros diferentes)
- **Foundational**: T011–T015 em paralelo (Http, JsonCache, Theme, Glass, fundo — ficheiros diferentes)
- **US1**: T021, T022, T023 em paralelo (Dock, AppCatalog, HomeRole)
- **US3**: T035 e T036 em paralelo (fixtures e testes)
- **Histórias**: US1–US4 podem ser trabalhadas em paralelo por pessoas diferentes após a Foundational

---

## Parallel Example: User Story 1 (models)

```bash
# Launch all models for User Story 1 together:
Task: "Criar model/Dock.kt"          # T021
Task: "Implementar data/AppCatalog.kt" # T022
Task: "Implementar data/HomeRole.kt"   # T023
```

## Parallel Example: User Story 3 (tests)

```bash
# Launch fixtures and tests for User Story 3 together:
Task: "Capturar fixtures ipwhois.json + open-meteo.json"  # T035
Task: "Criar WeatherSourcesTest.kt"                        # T036
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1: Setup (toolchain fixada, R6)
2. Completar Phase 2: Foundational (CRITICAL — bloqueia todas as histórias)
3. Completar Phase 3: User Story 1
4. **STOP e VALIDAR**: verificação na TV (quickstart.md §US1) — dock, grelha, banner HOME, diálogos
5. Deploy/demo: a TV fica utilizável como ponto de partida para o conteúdo

### Incremental Delivery

1. Setup + Foundational → fundação pronta (shell "Liquid Glass" navegável)
2. US1 → testar na TV → Deploy/Demo (MVP)
3. US2 → cabeçalho → testar na TV → Deploy/Demo
4. US3 → clima (com testes JUnit dos parsers) → testar na TV → Deploy/Demo
5. US4 → jw.org (leitura + reprodução, com testes JUnit) → testar na TV → Deploy/Demo
6. Cada história acrescenta valor sem partir as anteriores; verificação na TV a cada passo (Princípio V)

### Parallel Team Strategy

Com várias pessoas:

1. Equipa completa Setup + Foundational em conjunto
2. Depois da Foundational:
   - Pessoa A: US1 (dock) · Pessoa B: US2 (cabeçalho) · Pessoa C: US3 (clima) · Pessoa D: US4 (jw.org)
3. Histórias completam e integram-se de forma independente; US4 integra o foco dock ↔ JW no final

---

## Notes

- **Ambiente**: sessões com `java` 8 no PATH precisam de `$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"` antes de compilar (quickstart.md §1)
- **Na TV**: apenas instalar/iniciar/parar/remover `com.mrcarromesa.tvlauncher` (`AGENTS.md`). Nunca definir o HOME por ADB.
- **HDMI**: R2 — a troca automática de entrada não é implementável sem permissão de assinatura; implementa-se só o diálogo de ajuda (FR-005, segunda metade)
- **Toolchain**: fixada (R6); subir qualquer peça exige rever a constituição
- **Ficheiros de cache**: `filesDir/weather.json`, `filesDir/jw.json`, `filesDir/place.json` (data-model.md "Armazenamento")
- **Testes**: `.\gradlew.bat :app:testDebugUnitTest` (quickstart.md §3); fixtures detetam mudanças de estrutura das fontes (R3)
- **[P]** = ficheiros diferentes, sem dependências; **[Story]** = mapeia a tarefa à história para rastreabilidade
- Commitar após cada tarefa ou grupo lógico; parar em qualquer checkpoint para validar a história
