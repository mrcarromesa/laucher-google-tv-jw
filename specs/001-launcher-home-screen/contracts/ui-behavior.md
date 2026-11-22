# Contrato — Comportamento de UI

**Consumidor**: `ui/**` | **Requisitos**: FR-021…FR-026, SC-001…SC-005

---

## 1. Ecrãs

| Ecrã | Entrada | Saída |
|---|---|---|
| `Home` | arranque, Voltar de qualquer app/ecrã | — |
| `AllApps` | OK em "Todos os Apps" | Voltar → `Home` com foco em "Todos os Apps"; OK numa app → lança |
| `Reader` | OK numa entrada de texto | Voltar → `Home` com foco na entrada |
| `Player` | OK numa entrada de vídeo | Voltar → `Home` com foco na entrada |

Um só `MainActivity`. O ecrã atual vive no `HomeViewModel` (sobrevive a rotação e à ida
a outra app). `BackHandler` trata Voltar em `AllApps`, `Reader` e `Player`; em `Home`
Voltar não faz nada (é o HOME).

## 2. Foco

### `Home`

```
┌──────────────────────────────┐
│ cabeçalho (não focável)      │
├───────────────┬──────────────┤
│ clima         │ JW.org       │  ← 4 entradas, lista vertical
│ (não focável) │ (focável)    │
├───────────────┴──────────────┤
│ dock: 0 1 2 3 4              │
└──────────────────────────────┘
```

| Tecla | Onde | Efeito |
|---|---|---|
| ← / → | dock | item anterior/seguinte; nos extremos não salta |
| ↑ | dock | última entrada JW focada; se nenhuma, a primeira |
| ↑ / ↓ | JW | entrada anterior/seguinte |
| ← / → | JW | sem efeito (o painel de clima não é interativo) |
| ↓ | JW | dock, no item que estava focado |
| OK | dock/JW | ação do item |
| Voltar | Home | sem efeito |

- Foco inicial: dock, índice 0.
- O índice focado do dock e a última entrada JW guardam-se no ViewModel.
- Nenhum item focável pode ficar fora do ecrã: as listas fazem scroll para o manter
  visível.
- Anel de foco: borda de 2 dp + brilho + escala 1.06. Contraste mínimo 3:1 com o
  painel.

### `AllApps`

- Grelha de 5 colunas; ←/→/↑/↓ movem; OK lança; Voltar regressa.
- Foco inicial: primeira app. Ao voltar de uma app lançada, foco na app lançada.
- Nomes longos: `maxLines = 2`, `TextOverflow.Ellipsis`, sem sobrepor o ícone.

### `Reader` / `Player`

- Sem foco em elementos da app (o `WebView`/`PlayerView` trata o D-pad).
- Voltar fecha e repõe o foco no item de origem.

## 3. Ações e mensagens

| Situação | Comportamento |
|---|---|
| App do dock ausente | OK abre diálogo "«Netflix» não está instalada."; foco não muda |
| App da grelha indisponível no momento do OK | diálogo idêntico; a grelha mantém-se |
| HDMI 1/2 | tenta trocar para a entrada correspondente via passthrough TvInput; se a troca não for possível, sem efeito (foco fica no item) — sem diálogo |
| Sem rede | sem diálogo; os painéis mostram idade/indisponibilidade |
| Não é HOME | banner no topo, não focável e não bloqueante; mantém-se enquanto o papel não for nosso |
| Refresh em curso | sem indicador bloqueante; o conteúdo antigo continua visível |

Um único composable de diálogo (título, corpo, "OK") para todos os casos. Voltar e OK
fecham.

## 4. Cabeçalho

- Hora `HH:mm` (24 h) e data `EEE, d MMM` em `pt-PT` (ex.: `sáb., 22 nov.`).
- Atualização no minuto exato; à meia-noite a data muda sem reinício.
- Wi-Fi: ícone + "Wi-Fi" quando há rede com `NET_CAPABILITY_INTERNET`; caso contrário
  ícone cortado + "Sem ligação". Atualiza por callback, não por polling.
- Temperatura: o mesmo `currentTempC` do painel; se o clima estiver indisponível, a
  temperatura é **omitida** (nunca um valor errado).

## 5. Painéis

### Clima

- Temperatura grande, condição, máxima/mínima, 5 horas (hora + temperatura).
- Rodapé: `Atualizado há X min` ou `Sem dados recentes` + `Dados: Open-Meteo (CC BY 4.0)`.
- `Loading`: esqueleto com a mesma altura (sem salto de layout).

### JW.org

- Quatro linhas, cada uma com tipo, título (2 linhas) e, nos vídeos, miniatura 16:9.
- `Atualizado há X h` no rodapé do painel.
- `Unavailable`: "Conteúdo indisponível" + última data conhecida, sem erro técnico.

## 6. Estética "Liquid Glass"

- Fundo: um `Box` com gradiente + arte, `Modifier.blur(48.dp)`, desenhado **uma vez**
  (sem animação, sem recomposição por frame).
- Painel: `Brush.verticalGradient` de branco 0.18 → 0.08, `border` de 1 dp com
  gradiente, `clip(RoundedCornerShape(28.dp))`, sombra suave.
- Reflexo: faixa diagonal branca 0.06 no topo do painel.
- Foco: escala 1.06 + brilho da borda, `tween(120)`; sem desfoque animado.
- Sem blur por frame, sem shader, sem `RenderEffect` manual.

## 7. Layout e dimensões

- `Box` raiz com `padding(horizontal = 48.dp, vertical = 32.dp)` — margem segura
  contra overscan a 1080p e 4K.
- Tudo em `dp`/`sp`; nenhum tamanho em píxeis.
- Grelha: 5 colunas, `LazyVerticalGrid`, espaçamento 16 dp, ícones 96 dp.
- Dock: altura 132 dp, itens 168×120 dp, espaçamento 20 dp.
- Texto principal ≥ 24 sp; títulos ≥ 32 sp (legibilidade a 3 m, SC-009).

## 8. Orçamentos de tempo

| Operação | Orçamento | Como |
|---|---|---|
| Destaque de foco | ≤ 120 ms (limite 200) | `animateFloatAsState(tween(120))` |
| Primeiro frame com cache | < 500 ms | ler ficheiros em `Dispatchers.IO`, UI com placeholder |
| Volta de uma app | < 2 s | nada bloqueia; refresh é assíncrono |
| Refresh de clima/JW | sem bloquear a UI | corrotina no ViewModel, `StateFlow` |

## 9. Acessibilidade mínima

- Cada item focável tem `contentDescription` com o nome e o estado ("Netflix,
  indisponível").
- O anel de foco não depende só da cor (borda + escala).
- Nenhum fluxo exige toque, gesto ou teclado.
