# Contrato — Fontes JW.org

**Consumidor**: `data/jw/JwSources.kt` → `JwRepository`
**Versão**: 1 | **Verificado**: 2026-11-22 (todas as fontes com HTTP 200)

Não existe API oficial. As quatro fontes abaixo são as únicas usadas. Idioma fixo:
português (`pt` nas páginas, `T` no mediator).

Headers comuns: `User-Agent: TvLauncher/1.0 (Android TV)`, `Accept` conforme o tipo.
Timeouts: 8 s.

---

## 1. Novidades do site

```
GET https://www.jw.org/pt/novidades/
Accept: text/html
```

Seleção: o **primeiro** bloco que contenha, por esta ordem, todos os elementos:

1. `div` com classe que inclua `synopsis` e `docId-<dígitos>`;
2. `<p class="meta pubDate">YYYY-MM-DD</p>`;
3. `<h3>…<a href="…">título</a>…</h3>`;
4. opcional: `data-img-size-md='https://cms-imgp.jw-cdn.org/…'`.

Resultado: `JwTextEntry(title, pageUrl = https://www.jw.org + href, imageUrl,
publishedOn)`. Blocos sem 1–3 são ignorados; se nenhum bloco for válido ⇒
`ParseFailure`.

O `href` pode ser relativo (`/en/news/…`); resolve-se sempre contra
`https://www.jw.org`. Só se aceitam URLs desse anfitrião.

## 2. Texto do dia

```
GET https://wol.jw.org/pt/wol/h/r5/lp-t
Accept: text/html
```

A página devolve **três** blocos consecutivos:

```html
<div class="tabContent" data-date="2026-11-22T00:00:00.000Z">
  <h2>Sábado, 21 de novembro</h2>
  <p class="themeScrp">…<a class="b">Isa. 43:12</a>…</p>
  <div class="bodyTxt"><p class="sb">comentário…</p></div>
</div>
```

Regras:
- Escolher o bloco cujo `data-date` (parte da data) é `LocalDate.now()`; se não
  existir, o bloco do meio (índice 1).
- `dateLabel` = texto de `h2`; `scripture` = texto de `p.themeScrp` sem tags;
  `comment` = primeiro `p.sb` sem tags; `pageUrl` = o URL pedido.
- Qualquer dos três textos vazio ⇒ `ParseFailure`.

## 3. Último Broadcasting

```
GET https://b.jw-cdn.org/apis/mediator/v1/categories/T/LatestVideos?detailed=1&clientType=www
Accept: application/json
```

Seleção: primeiro item de `category.media` com
`primaryCategory == "StudioMonthlyPrograms"`. Ordenar por `firstPublished` desc antes
de escolher, para não depender da ordem do servidor.

Resultado: `JwVideoEntry` com
- `title` = `title`;
- `publishedAt` = `firstPublished`;
- `posterUrl` = `images.wss.lg` (senão `images.lss.lg`);
- `durationSeconds` = `duration`;
- `videoUrl` = ver §5.

## 4. Último Boletim do Corpo Governante

```
GET https://b.jw-cdn.org/apis/mediator/v1/categories/T/StudioNewsReports?detailed=1&clientType=www
Accept: application/json
```

Seleção: itens cujo `title` casa `^Boletim do Corpo Governante`, ordenados por
`firstPublished` desc; escolher o primeiro. Se nenhum casar, `ParseFailure` (não se
mostra uma notícia qualquer como se fosse o boletim).

> `shortcut: o filtro é textual e em português; se o título do boletim mudar, o teste
> com fixture falha e a cache segura o conteúdo — atualizar o padrão nessa altura.`

## 5. Escolha do ficheiro de vídeo

Dentro de `files`:
1. filtrar `mimetype == "video/mp4"`;
2. escolher o de maior altura entre os que tenham `label` `240p`…`1080p`;
3. se não houver nenhum, `ParseFailure`.

Nunca usar `video/3gpp`, HLS ou `progressiveDownloadURL` de outro domínio que não
`*.jw-cdn.org`.

## 6. Abertura do conteúdo

| Tipo | Ação |
|---|---|
| Texto (novidades, texto do dia) | ecrã de leitura: `WebView` com o `pageUrl` e um cursor virtual sobreposto (`TvCursorWebView`): D-pad move o cursor, OK clica o elemento nesse ponto (converte px de ecrã→CSS), nas bordas verticais o cursor rola a página. Sem alterar o HTML nem depender das classes do site; JS só para dispensar o aviso de cookies (re-injetado em cada página). Navegação limitada a `jw.org`/`wol.jw.org`; sem `addJavascriptInterface` |
| Vídeo (Broadcasting, Boletim) | ecrã de reprodução: Media3 `ExoPlayer` com `videoUrl`, foco pedido ao `PlayerView` (devolvido quando o controlador se auto-oculta); controlador com play/pause, recuar/avançar 10 s e velocidade (0,25×–1,5×); OK = play/pause, Voltar = sair e libertar |

## 7. Falha, cache e atualização

- Falha de rede, HTTP ≠ 200, JSON/HTML inválido ou campo obrigatório ausente ⇒
  `ParseFailure`; não se escreve cache nem `fetchedAt`.
- Snapshot novo funde-se campo a campo com o anterior: uma fonte falhada mantém o
  valor antigo desse campo.
- Atualizar no arranque/volta ao ecrã e a cada 6 h enquanto visível (SC-007 pede
  ≥ 1×/dia). O regresso ao ecrã faz refresh completo (novidades, texto do dia,
  Broadcasting, Boletim); à meia-noite local (00:00:01), refresh completo renova o
  texto do dia.
- Cache > 24 h ⇒ painel `Unavailable` com a data do último sucesso.
- Nunca mostrar mensagens de erro técnicas; a idade do conteúdo é a informação.

## 8. Fixtures de teste

> Removido por decisão de produto (2026-11-22): sem testes JVM na v1. A robustez dos
> parsers valida-se na TV real (Princípio V); a degradação silenciosa + cache mantém-se.
