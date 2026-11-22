# Data Model — Ecrã inicial do launcher

**Feature**: `001-launcher-home-screen` | **Plano**: [plan.md](plan.md)

Modelo de UI e de cache. Não há base de dados: as entidades de cache são serializadas
em JSON e as de UI são reconstruídas a partir delas.

---

## Entidades

### `DockItem` (selada)

| Campo | Tipo | Regra |
|---|---|---|
| `order` | `Int` | 0…4, fixo: Netflix, YouTube, HDMI 1, HDMI 2, Todos os Apps |
| `label` | `String` | "Netflix", "YouTube", "HDMI 1", "HDMI 2", "Todos os Apps" |
| `kind` | `App` / `Hdmi` / `AllApps` | determina a ação de OK |

- `App(packageName, installed: Boolean, icon: Drawable?)`
- `Hdmi(index: 1|2)`
- `AllApps`

**Invariantes**
- A ordem e o número de itens são constantes; um item nunca desaparece por a app não
  estar instalada (FR-002, FR-004).
- `installed == false` ⇒ o item é apresentado esbatido e OK mostra a mensagem de
  indisponibilidade; nunca lança outra app.
- `Hdmi` nunca está "indisponível": OK tenta a troca automática via passthrough (FR-005); se a troca não for possível, sem efeito — nunca um diálogo de indisponibilidade.

### `InstalledApp`

| Campo | Tipo | Regra |
|---|---|---|
| `packageName` | `String` | único; exclui `com.mrcarromesa.tvlauncher` |
| `label` | `String` | `loadLabel`; nomes longos truncam com reticências, sem sobrepor |
| `icon` | `Drawable` | `loadIcon`; falha → ícone genérico |

**Invariantes**
- Lista ordenada por `label` (comparação `pt`, sem distinguir maiúsculas).
- Só entram apps com atividade de lançamento resolvível.
- A lista inclui as apps do dock (FR-006), exceto o próprio launcher.

### `Place`

| Campo | Tipo | Origem |
|---|---|---|
| `city` | `String` | `ipwho.is.city` |
| `countryCode` | `String` | `ipwho.is.country_code` |
| `latitude` / `longitude` | `Double` | `ipwho.is` |
| `timeZoneId` | `String` | `ipwho.is.timezone.id` |

### `WeatherSnapshot`

| Campo | Tipo | Regra |
|---|---|---|
| `place` | `Place` | |
| `currentTempC` | `Double` | `current.temperature_2m` |
| `currentCode` | `Int` | `current.weather_code` (WMO) |
| `maxC` / `minC` | `Double` | `daily.temperature_2m_max[0]` / `min[0]` |
| `hourly` | `List<HourlyPoint>` | ≥ 5 pontos a partir da hora atual |
| `fetchedAt` | `Instant` | momento do sucesso |

- `HourlyPoint(localTime: LocalDateTime, tempC: Double, code: Int)`
- `WeatherCondition(code, labelPt, icon)` — tabela WMO fixa (0–3, 45/48, 51–57,
  61–67, 71–77, 80–82, 85/86, 95–99); código desconhecido → "—".

**Invariantes**
- `hourly` é sempre filtrada para `localTime >= agora` e ordenada; mostram-se 5.
- `maxC >= minC`.
- O cabeçalho usa exatamente `currentTempC` do painel (FR-009/US2-4) — uma só fonte.

### `JwSnapshot`

| Campo | Tipo | Regra |
|---|---|---|
| `whatsNew` | `JwTextEntry?` | |
| `dailyText` | `JwDailyText?` | |
| `broadcasting` | `JwVideoEntry?` | |
| `governingBody` | `JwVideoEntry?` | |
| `fetchedAt` | `Instant` | momento do sucesso |

- `JwTextEntry(title, pageUrl, imageUrl?, publishedOn: LocalDate?)`
- `JwDailyText(dateLabel, scripture, comment, pageUrl)`
- `JwVideoEntry(title, publishedAt: Instant?, posterUrl?, videoUrl, durationSeconds?)`

**Invariantes**
- Cada campo é independente: uma fonte pode falhar e as outras atualizar (o snapshot
  novo funde-se com o anterior campo a campo).
- `videoUrl` é sempre um MP4 progressivo (`mimetype == video/mp4`, altura ≤ 1080).
- `pageUrl` de texto tem de pertencer a `jw.org`/`wol.jw.org` (allowlist do leitor).
- Entrada sem `title` ou sem URL de conteúdo é descartada.

### `CacheEnvelope<T>`

```json
{ "schema": 1, "fetchedAt": "2026-11-22T13:45:00Z", "payload": { } }
```

**Invariantes**
- `schema != 1` ⇒ ficheiro ignorado (nunca apagado).
- Escrita atómica: `*.tmp` + `renameTo`; um ficheiro truncado nunca é lido como válido.
- Falha de rede/parsing não altera o ficheiro.

### `HomeUiState`

| Campo | Tipo |
|---|---|
| `now` | `LocalDateTime` |
| `isOnline` | `Boolean` |
| `isHome` | `Boolean` |
| `dock` | `List<DockItem>` |
| `weather` | `WeatherState` |
| `jw` | `JwState` |
| `focus` | `DockFocus` (índice atual + último índice) |

- `WeatherState = Loading | Available(snapshot, age) | Unavailable(lastAttemptAt?)`
- `JwState = Loading | Available(snapshot, age) | Unavailable(lastAttemptAt?)`

**Invariantes**
- `now` atualiza-se no minuto; a data muda à meia-noite sem reinício (FR-010).
- `isOnline` reflete a rede no momento (FR-011); `false` não limpa nada.
- `isHome == false` ⇒ banner de instruções visível, não focável e não bloqueante,
  enquanto o papel não for nosso (FR-008).
- `age = Duration.between(snapshot.fetchedAt, now)`; `age > 24 h` ⇒ `Unavailable`
  com a data do último sucesso (SC-008), não `Available`.

---

## Estados e transições

### Clima

| De | Evento | Para |
|---|---|---|
| `Loading` | cache válido (≤ 24 h) | `Available(cache, age)` |
| `Loading` | sem cache e sem rede | `Unavailable(null)` |
| `Available` | tick e `age > 60 min` | `Available` (refresh em curso, sem loading visível) |
| `Available` | refresh OK | `Available(novo, 0)` |
| `Available` | refresh falha | `Available(mesmo, age)` |
| `Available` | `age > 24 h` | `Unavailable(lastAttemptAt)` |
| `Unavailable` | refresh OK | `Available(novo, 0)` |

### JW.org

| De | Evento | Para |
|---|---|---|
| `Loading` | cache válido (≤ 24 h) | `Available(cache, age)` |
| `Loading` | sem cache e sem rede | `Unavailable(null)` |
| `Available` | tick e `age > 6 h` | `Available` (refresh em curso) |
| `Available` | refresh parcial | `Available` (campos fundidos) |
| `Available` | refresh falha | `Available(mesmo, age)` |
| `Available` | `age > 24 h` | `Unavailable(lastAttemptAt)` |

### Ecrã

`Home → AllApps → Home` (Voltar repõe o foco em "Todos os Apps") ·
`Home → Reader → Home` · `Home → Player → Home` ·
`Home → (app externa) → Home` (foco no item que a lançou).

---

## Armazenamento

```text
filesDir/
├── weather.json   # CacheEnvelope<WeatherSnapshot>
├── jw.json        # CacheEnvelope<JwSnapshot>
└── place.json     # Place (sem envelope; renovado após 30 dias)
```

Sem migrações: `schema` diferente ⇒ ignorar. Limpeza: nenhuma na v1 (os ficheiros são
substituídos por inteiro).
