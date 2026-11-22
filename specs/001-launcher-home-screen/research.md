# Research — Ecrã inicial do launcher

**Feature**: `001-launcher-home-screen` | **Data**: 2026-11-22 | **Plano**: [plan.md](plan.md)

Cada decisão abaixo foi verificada contra a fonte real nesta data (pedido HTTP e
payload inspecionados). Onde a verificação não foi possível, está dito.

---

## R1 — Desfoque de fundo e estética "Liquid Glass"

**Decision**: um único fundo estático (gradiente + arte) desfocado **uma vez** com
`Modifier.blur(40–60.dp)`; os painéis são superfícies translúcidas (alpha 0.10–0.18)
com gradiente de borda, reflexo especular e sombra; o foco anima escala e brilho em
120 ms. Nunca se desfoca por frame nem se desfoca o conteúdo dos painéis.

**Rationale**: `Modifier.blur` (API 31+) desenha o composable numa camada gráfica
separada cujo custo cresce com o raio e com a área. A 3840×2160, um desfoque de ecrã
inteiro por frame é incomportável e não é preciso: o que o olho lê como "vidro" é a
transluidez + a imagem de fundo já desfocada. Uma camada desfocada, criada uma vez,
dá o mesmo resultado com custo constante.

**Alternatives rejected**:
- `RenderEffect` sobre o backdrop vivo: exige capturar o conteúdo por baixo, não é
  exposto pela API pública do Compose e varia com o hardware.
- Shader AGSL (API 33+): custo de GPU por frame, sem ganho visual percetível na TV.
- Desfocar cada painel: desfoca o painel, não o fundo — não produz o efeito.

**Consequences**: o fundo é um recurso fixo (sem wallpapers, já fora do âmbito).
`Modifier.blur` é ignorado abaixo da API 31, o que coincide com `minSdk 31`: não há
ramo condicional (Princípio II).

---

## R2 — Troca de entrada HDMI

**Decision**: os itens "HDMI 1" e "HDMI 2" ficam sempre no dock. Ao carregar em OK,
o launcher **tenta a troca automática** para a entrada HDMI correspondente via o
passthrough do framework TvInput, resolvendo o identificador dinamicamente com
`TvInputManager`. Sem diálogo: se a troca não for possível, não faz nada (foco fica no
item). O launcher não escreve definições nem usa `adb`/`settings`.

> **Correção (2026-11-22, verificado na TV):** a versão original deste R2 concluía que
> a troca automática era impossível por app normal e que o OK devia abrir um diálogo
> de ajuda. **Refutado** ao instalar e testar no dispositivo real: a troca funciona
> com `TvContract.buildChannelUriForPassthroughInput(inputId)` + `ACTION_VIEW`, **desde
> que o manifesto declare a visibilidade de pacotes** (`<queries>` para
> `content://android.media.tv` e `com.mediatek.tvinput`). Sem esse `<queries>`, o
> intent não resolve (Android 11+ esconde o controlador) e o passthrough falha de
> forma silenciosa — foi o que se observou nos primeiros testes.

**Rationale**: a troca via passthrough TvInput não exige `BIND_TV_INPUT` (permissão de
assinatura): requer apenas que a app veja o input (visibilidade de pacotes via
`<queries>`) para que o `ACTION_VIEW` com o channel URI de passthrough seja entregue ao
`TvInputManager`. As IDs HDMI (`com.mediatek.tvinput/.hdmi.HDMIInputService/HW4|HW5` —
HDMI 1 = HW4, HDMI 2 = HW5) foram confirmadas com `dumpsys tv_input` no aparelho. O
fallback é específico do fabricante MediaTek, mas a resolução primária (`TvInputManager`)
é genérica — em outra TV, o fallback não é alcançado se o tvInputList tiver os HDMI.
A premissa "HDMI depende do fabricante" da spec mantém-se: depende de o fabricante
expor entradas HDMI no TvInputManager, mas a via de troca é do framework, não um
intent de fabricante.

**Alternatives rejected**:
- Diálogo de ajuda como comportamento único (versão antiga): substituído — a troca
  real é possível e preferível (FR-005 primeira metade satisfeita).
- `settings put` / `cmd` / ADB: proibidos por `AGENTS.md` e pelo Princípio I.
- `TvView.tune()`: ainda limitado a clientes de input com `BIND_TV_INPUT`; não usado —

  a via é o passthrough content-URI, que resolve sem essa permissão.

**Consequences**: a primeira metade de FR-005 ("tentar mudar a entrada") fica
satisfeita e **implementa-se**; a segunda metade (explicação/procedimento manual) deixa
de ser necessária como diálogo obrigatório, pois o fallback é simplesmente "não faz
nada". O `<queries>` no manifesto é código necessário, não opcional. Sem sinal na
entrada HDMI, o passthrough pode não ter efeito visível — comportamento aceite.

---

## R3 — Fontes de conteúdo do jw.org

Não há API oficial. Foram testadas as fontes que o site e as apps JW usam.

**Decision**: quatro fontes, todas verificadas com HTTP 200 nesta data.

| Entrada | Fonte | Regra de seleção |
|---|---|---|
| Novidades do site | `https://www.jw.org/pt/novidades/` (HTML) | primeiro bloco `div.synopsis` com `docId`, `p.meta.pubDate`, `h3 > a[href]` e imagem |
| Texto do dia | `https://wol.jw.org/pt/wol/h/r5/lp-t` (HTML) | bloco `div.tabContent[data-date]` cuja data é hoje; senão o bloco do meio |
| Último Broadcasting | `https://b.jw-cdn.org/apis/mediator/v1/categories/T/LatestVideos?detailed=1&clientType=www` (JSON) | primeiro item com `primaryCategory == "StudioMonthlyPrograms"` |
| Último Boletim do Corpo Governante | `https://b.jw-cdn.org/apis/mediator/v1/categories/T/StudioNewsReports?detailed=1&clientType=www` (JSON) | primeiro item cujo `title` casa `Boletim do Corpo Governante` |

**Dados confirmados** (2026-11-22):
- `LatestVideos` devolveu 10 itens; o de `StudioMonthlyPrograms` era
  "JW Broadcasting - Novembro de 2026" (`pub-jwb-143_T_1_VIDEO`), com ficheiros MP4
  de 240p a 720p e imagens `wss/lss/lsr/pnr/sqr`.
- `StudioNewsReports` devolveu 81 itens; o primeiro era
  "Boletim do Corpo Governante (2026) - n.º 7" (`docid-1112024071_T_1_VIDEO`),
  seguido dos boletins anteriores por data decrescente.
- A página de novidades tem blocos `div.synopsis … docId-<id> …` com
  `<p class="meta pubDate">2026-11-20</p>`, `<h3><a href="/en/news/…">título</a></h3>`
  e `data-img-size-md='https://cms-imgp.jw-cdn.org/…'`.
- A página WOL devolve **três** blocos `div.tabContent[data-date]` (ontem, hoje,
  amanhã) numa só resposta — dá margem para cache e para o fuso horário.
- `categories/T/LatestVideos` (português) devolve 200; `categories/E/...` também.
  Chaves testadas e inexistentes (404): `GoverningBodyUpdates`, `GoverningBody`,
  `GBUpdate`, `Video`, `Programs`, `Broadcasting`.

**Rationale**: o mediator API é o mesmo que serve o site e as apps JW, é JSON estável,
não pede chave e já traz imagem, duração e MP4 progressivo. As duas páginas HTML são
as únicas fontes de "novidades" e "texto do dia"; a sua estrutura é simples e
regular. `StudioNewsReports` evita ter de adivinhar qual dos vídeos de notícias é o
boletim: os boletins são os primeiros itens da categoria.

**Alternatives rejected**:
- Não existe API oficial nem feed RSS.
- Scraping da página do JW Broadcasting em vez do mediator: mais HTML, mesmos dados.
- `categories/E/Video`: 404.
- Filtrar por `primaryCategory` só em `LatestVideos`: funciona hoje, mas o boletim
  desaparece da lista "Latest" ao fim de alguns dias — daí a categoria dedicada.

**Deteção de mudança de estrutura**: cada parser valida os campos mínimos. Se faltar
qualquer um, o resultado é `ParseFailure`: não se escreve cache, não se atualiza
`lastSuccess`, o conteúdo anterior mantém-se e a data de atualização exposta fica
mais antiga. Na v1 sem testes JVM (verificado na TV, Princípio V); o sintoma visível
na TV é o painel JW ficar com a data anterior.

**Consequences**: o painel depende de HTML de terceiros; a degradação é silenciosa e
o cache é a garantia de continuidade (FR-019, FR-020, edge case "fonte alterada").
Idioma fixo `pt`/`T` (Princípio II: um só alvo, sem ramos de idioma).

---

## R4 — Clima sem chave + geolocalização por IP

**Decision**:
- Localização: `GET https://ipwho.is/` → `success`, `latitude`, `longitude`, `city`,
  `country_code`. Sem chave, HTTPS. Resultado guardado (a localização não muda); só
  se volta a pedir se não houver nenhuma guardada ou se tiver mais de 30 dias.
- Previsão: `GET https://api.open-meteo.com/v1/forecast` com
  `current=temperature_2m,weather_code`,
  `daily=temperature_2m_max,temperature_2m_min`,
  `hourly=temperature_2m,weather_code`, `timezone=auto`, `forecast_days=2`.
- Atualização: no máximo de hora a hora (FR-014), verificada a cada 15 min enquanto o
  ecrã está visível; também ao voltar de uma app se o último sucesso tiver >60 min.
- Atribuição visível no painel: "Dados: Open-Meteo (CC BY 4.0)".

**Dados confirmados** (2026-11-22):
- `ipwho.is` devolveu `{"success":true,"country_code":"BR","city":"Ararangua",
  "latitude":-28.9357532,"longitude":-49.4953806,"timezone":{"id":"America/Sao_Paulo",…}}`.
- Open-Meteo devolveu `current.temperature_2m`, `current.weather_code`,
  `daily.temperature_2m_max[0..1]`, `daily.temperature_2m_min[0..1]`,
  `hourly.time[]`, `hourly.temperature_2m[]`, `hourly.weather_code[]`,
  `utc_offset_seconds`, `timezone`, com 48 pontos horários para 2 dias.
- Limites Open-Meteo (plano gratuito não comercial): 10 000/dia, 5 000/hora,
  600/minuto. Uma TV a atualizar de hora a hora usa ~24/dia.

**Rationale**: nenhuma das duas fontes pede chave nem conta, o que evita segredos no
APK e ecrãs de configuração. `ipwho.is` dá cidade e coordenadas suficientes para
clima. Open-Meteo devolve exatamente os campos do painel (atual, máxima, mínima,
horária) numa resposta e é CC BY 4.0.

**Alternatives rejected**:
- `LocationManager`/GPS: a TV não tem GPS e a permissão interativa viola FR-013
  ("sem pedir ao utilizador").
- Geolocalização por Wi-Fi/Google Play Services: dependência pesada e permissão de
  localização.
- `ip-api.com`: nível gratuito só HTTP (sem TLS) — inaceitável.
- `ipapi.co`: limites mais apertados no nível gratuito.
- Pedir a cidade ao utilizador: viola FR-013.

**Consequences**: localização ao nível da cidade (assumido na spec). Se o IP for de
uma VPN ou de outro país, o clima segue esse IP — comportamento aceite. Sem rede e
sem cache, o painel fica indisponível (FR-015).

---

## R5 — Apps abríveis e deteção de HOME

**Decision**:
- Enumeração: `PackageManager.queryIntentActivities` para
  `ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER` **e** `CATEGORY_LAUNCHER`, união por
  `packageName`, excluindo `com.mrcarromesa.tvlauncher`.
- Lançamento: `getLeanbackLaunchIntentForPackage(pkg) ?: getLaunchIntentForPackage(pkg)`;
  se ambos nulos, o item mostra-se indisponível.
- HOME: `RoleManager.isRoleHeld(RoleManager.ROLE_HOME)`; se falso, banner com
  instruções (Definições → Apps → Apps predefinidas → Início). Nunca se altera.
- Manifesto: `MAIN`/`HOME`/`DEFAULT` (para poder ser HOME) **e**
  `MAIN`/`LEANBACK_LAUNCHER` (para aparecer na lista de apps da TV).

**Rationale**: desde a API 30 a visibilidade de pacotes é filtrada; sem `<queries>`
no manifesto, `queryIntentActivities` devolve uma lista incompleta. As apps de TV
declaram `LEANBACK_LAUNCHER`; algumas apps declaram só `LAUNCHER`, por isso a união.
`RoleManager` é a forma suportada de perguntar "sou o HOME?" sem permissão e sem
escrever nada.

**Alternatives rejected**:
- `getInstalledApplications` + `QUERY_ALL_PACKAGES`: permissão larga, viola o
  Princípio IV e a política da Play.
- `cmd package` / `pm`: proibidos por `AGENTS.md`.
- Assumir que somos HOME: falha silenciosa no primeiro arranque, quando o utilizador
  ainda não nos definiu.

**Consequences**: se o utilizador não nos definir como HOME, o banner aparece em
todos os arranques (é o comportamento pedido por FR-008). Apps sem atividade de
lançamento não entram na grelha.

---

## R6 — Cadeia de build compatível com `compileSdk 34`

**Decision**: scaffold por `android create empty-activity`, depois fixar:

| Peça | Versão | Porquê |
|---|---|---|
| AGP | 8.7.3 | AGP 9 exige `compileSdk ≥ 36` |
| Gradle | 8.9 | mínimo do AGP 8.7 |
| Kotlin | 2.0.21 | plugin Compose 2.0.x |
| Compose BOM | 2024.06.00 (Compose 1.6.8) | `minCompileSdk=34` verificado |
| `androidx.tv:tv-material` | 1.0.0 | `minCompileSdk=34` verificado |
| `activity-compose` | 1.9.0 | idem |
| `lifecycle-*` | 2.8.3 | idem |
| `core-ktx` | 1.13.1 | idem |
| `media3-exoplayer` / `media3-ui` | 1.3.1 | idem |
| Coil | 2.6.0 | `minCompileSdk=1` |
| kotlinx-serialization-json | 1.7.3 | compatível com Kotlin 2.0.21 |
| OkHttp | 4.12.0 | já vem com Coil; declarado explicitamente |

**Rationale**: o template atual do Android CLI gera AGP 9.0.1, Gradle 9.1,
Kotlin 2.3.20 e `compileSdk 37`. A constituição fixa `compileSdk 34`; além disso o
`android create` tentou instalar `platforms;android-37` e falhou ("not found"), pelo
que o próprio template não compila neste ambiente. Verificou-se o
`aar-metadata.properties` de cada AAR: todas as versões da tabela declaram
`minCompileSdk=34` (ou 1). `tv-foundation:1.0.0` exige 35 — **não é usado**;
`tv-material:1.0.0` não depende dele.

**Alternatives rejected**:
- Subir `compileSdk` para 35/36/37: alteraria a constituição (Princípio II) sem
  necessidade de produto.
- Manter AGP 9 e `compileSdk 34`: combinação não suportada.
- Escrever o Gradle à mão: o scaffold do CLI dá estrutura, wrapper e testes; só se
  fixam versões.

**Consequences**: o projeto fica numa linha de versões de 2024/2025. É intencional e
está alinhado com o alvo Android 14. Qualquer upgrade de biblioteca obriga a rever
`compileSdk` — logo, é uma decisão de constituição, não de dependência.

---

## R7 — Persistência e política de atualização

**Decision**: cache em ficheiros JSON privados da app (`filesDir/weather.json`,
`filesDir/jw.json`), envelope `{schema:1, fetchedAt, payload}`, escrita atómica
(ficheiro temporário + `renameTo`). Sem Room, sem DataStore, sem base de dados.

**Rationale**: são três documentos pequenos, lidos de uma vez no arranque e escritos
de hora a hora. Um ficheiro com escrita atómica sobrevive a process death e não
introduz schema nem migrações. Room/DataStore seriam dependências sem uso real
(Princípio IV).

**Política**:
- ticker de 15 min enquanto o ecrã está visível (e ao voltar de uma app);
- clima: atualizar se `now - fetchedAt > 60 min`;
- JW.org: atualizar se `now - fetchedAt > 6 h` (SC-007 pede ≥1×/dia);
- falha de rede ou de parsing: não escreve, não apaga, não muda `fetchedAt`;
- cache com mais de 24 h: o painel passa a "sem dados recentes" (SC-008), mantendo a
  data da última atualização visível.

**Alternatives rejected**: Room (schema+migrações para 2 documentos), DataStore
(dependência e API assíncrona para o mesmo efeito), `SharedPreferences` (sem escrita
atómica de blobs grandes).

---

## Riscos assumidos

1. **FR-005, primeira metade** — tratada: a troca HDMI implementa-se via passthrough
    TvInput + `<queries>` (R2 corrigido, 2026-11-22).
2. **jw.org** — HTML de terceiros pode mudar sem aviso; mitigado por validação de
   campos e cache (R3); sem testes JVM na v1.
3. **`ipwho.is`** — nível gratuito tem limites; mitigado por guardar a localização e
   só a renovar após 30 dias (R4).
4. **Toolchain antiga** — fixada pelo `compileSdk 34`; qualquer subida é uma emenda à
   constituição (R6).
