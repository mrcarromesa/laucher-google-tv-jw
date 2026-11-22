# Contrato — Fontes de clima

**Consumidor**: `data/weather/WeatherSources.kt` → `WeatherRepository`
**Versão**: 1 | **Verificado**: 2026-11-22

---

## 1. Localização — `ipwho.is`

```
GET https://ipwho.is/
Accept: application/json
User-Agent: TvLauncher/1.0 (Android TV)
```

Timeout: 5 s (connect 3 s, read 5 s). Sem chave, sem corpo, sem headers especiais.

Resposta esperada (campos usados):

```json
{
  "success": true,
  "country_code": "BR",
  "city": "Ararangua",
  "latitude": -28.9357532,
  "longitude": -49.4953806,
  "timezone": { "id": "America/Sao_Paulo", "utc": "-03:00" }
}
```

Regras:
- `success != true`, `latitude`/`longitude` ausentes ou `city` vazio ⇒
  `LocationUnavailable`.
- Sucesso ⇒ gravar `place.json` e só voltar a pedir se o ficheiro faltar ou tiver
  mais de 30 dias.
- Falha ⇒ usar `place.json` se existir; senão o painel fica `Unavailable`.

## 2. Previsão — Open-Meteo

```
GET https://api.open-meteo.com/v1/forecast
    ?latitude={lat}&longitude={lon}
    &current=temperature_2m,weather_code
    &daily=temperature_2m_max,temperature_2m_min
    &hourly=temperature_2m,weather_code
    &timezone=auto
    &forecast_days=2
Accept: application/json
User-Agent: TvLauncher/1.0 (Android TV)
```

Timeout: 8 s. Sem chave.

Campos usados:

| Campo | Uso |
|---|---|
| `current.temperature_2m` | temperatura do painel e do cabeçalho |
| `current.weather_code` | condição atual (WMO) |
| `daily.temperature_2m_max[0]` | máxima de hoje |
| `daily.temperature_2m_min[0]` | mínima de hoje |
| `hourly.time[]` | hora local (`timezone=auto`) |
| `hourly.temperature_2m[]` | temperatura por hora |
| `hourly.weather_code[]` | condição por hora |
| `utc_offset_seconds` | converte `hourly.time` em instante absoluto |

Regras de parsing:
- `hourly` tem de ter os três arrays com o mesmo comprimento; senão `ParseFailure`.
- Pontos com `localTime >= agora` (no fuso do dispositivo), ordenados, primeiros 5.
- Menos de 5 pontos futuros ⇒ `ParseFailure` (não se inventa previsão).
- `weather_code` fora da tabela WMO conhecida ⇒ condição "—" (não é falha).
- Qualquer campo obrigatório ausente ⇒ `ParseFailure`; cache e `fetchedAt` intactos.

## 3. Política de atualização

| Evento | Ação |
|---|---|
| Arranque / volta ao ecrã inicial | ler cache; se `age > 60 min`, atualizar |
| Ticker de 15 min (ecrã visível) | se `age > 60 min`, atualizar |
| Refresh em curso | não iniciar outro (uma corrotina por fonte) |
| Falha | manter cache; não escrever; sem mensagem de erro técnica |
| Cache > 24 h | estado `Unavailable` com data do último sucesso |

FR-014 pede "no máximo uma vez por hora"; SC-007 pede "pelo menos uma vez por hora".
O limiar de 60 min satisfaz ambos.

## 4. Apresentação obrigatória

- Atribuição visível no painel: `Dados: Open-Meteo (CC BY 4.0)`.
- Sem rede: valores + `Atualizado há X h` (ou `Sem dados recentes` se > 24 h).
- Nunca mostrar temperatura de outra localização como se fosse a atual.
