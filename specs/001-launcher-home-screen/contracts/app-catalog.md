# Contrato — Catálogo de apps, dock e HOME

**Consumidor**: `data/AppCatalog.kt`, `data/HomeRole.kt`, `ui/home/HomeViewModel.kt`

---

## 1. Visibilidade de pacotes (manifesto)

```xml
<queries>
  <intent>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
  </intent>
  <intent>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
  </intent>
</queries>
```

Sem `QUERY_ALL_PACKAGES`. Sem permissões.

## 2. Enumeração

Para cada `ResolveInfo` de `queryIntentActivities` (flags `0` — **não** `MATCH_DEFAULT_ONLY`):
- `packageName` do `activityInfo`;
- excluir `com.mrcarromesa.tvlauncher`;
- unir os dois resultados por `packageName` (uma app que declare as duas categorias
  aparece uma vez);
- `label = loadLabel(packageManager)`, `icon = loadIcon(packageManager)` (exceção →
  ícone genérico);
- **lançamento**: guardar um intent explícito `packageName + activity class` para cada
  app; `launchIntent(pkg)` prefere `getLeanbackLaunchIntentForPackage(pkg) ?:
  getLaunchIntentForPackage(pkg)` e só se ambos forem `null` usa o explícito;
- ordenar por `label` com `Collator` de `pt`, sem distinguir maiúsculas.

> `shortcut: MATCH_DEFAULT_ONLY e getLaunchIntentForPackage exigem CATEGORY_DEFAULT na
> resolução implícita. Verificado na TV (2026-11-22): a Netflix declara
> MAIN+LEANBACK_LAUNCHER+LAUNCHER SEM DEFAULT — os filtros de DEFAULT estão só nos
> deep-links `VIEW`. Com MATCH_DEFAULT_ONLY a Netflix ficava "indisponível" no dock
> mesmo instalada; o launch explícito resolve. Ao rever o contrato, documentar que a
> resolução implícita é apenas preferida.`

Resultado: `List<InstalledApp>`. Lista vazia é um estado válido (grelha mostra
"Sem apps").

## 3. Dock

| # | Item | Pacote canónico | Ação de OK |
|---|---|---|---|
| 0 | Netflix | `com.netflix.ninja` | lançar; se ausente → mensagem |
| 1 | YouTube | `com.google.android.youtube.tv` | lançar; se ausente → mensagem |
| 2 | HDMI 1 | — | diálogo de ajuda |
| 3 | HDMI 2 | — | diálogo de ajuda |
| 4 | Todos os Apps | — | abrir grelha |

- O item existe sempre; o estado `installed` vem do catálogo.
- Rótulo e ícone vêm do pacote quando instalado; caso contrário, rótulo fixo + ícone
  esbatido.
- Um pacote ausente **nunca** é substituído por outro.

## 4. Lançamento

```
intent = getLeanbackLaunchIntentForPackage(pkg) ?: getLaunchIntentForPackage(pkg)
intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
startActivity(intent)
```

- `ActivityNotFoundException` ou intent nulo ⇒ snackbar "«<nome>» não está
  disponível" e o foco fica onde está (FR-004).
- Duas pressões rápidas em OK não lançam duas instâncias: a ação é ignorada enquanto
  a anterior não regressa ao estado `Resumed` (guarda `launching` no ViewModel).
- Ao voltar, o foco repõe-se no item que lançou (índice guardado no ViewModel).

## 5. HOME

```
roleManager.isRoleHeld(RoleManager.ROLE_HOME)
```

- `true` ⇒ sem banner.
- `false` ⇒ banner não bloqueante no topo, com o texto:
  "Para usar este ecrã como Início: Definições → Apps → Apps predefinidas → Início →
  Escolher «<nome>»." e a nota "O launcher não altera esta definição."
- O banner mantém-se enquanto o papel não for nosso; não é focável e não interfere com
  o D-pad.
- **Nenhum** caminho de código escreve no `RoleManager`, em `Settings` ou em pacotes.

## 6. HDMI

- OK em `HDMI n` abre um diálogo: "A TV não permite mudar de entrada a partir de uma
  app. No comando da TV, carregue no botão de entrada/origem e escolha HDMI n."
- Sem `TvInputManager`, sem intents de fabricante, sem `Settings`.
- Fechar com OK ou Voltar devolve o foco ao item HDMI.

## 7. Manifesto (resumo)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<uses-feature android:name="android.software.leanback" android:required="true" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />

<activity android:name=".MainActivity" android:exported="true">
  <intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
  </intent-filter>
</activity>
```

`banner` e `android:banner` são necessários para a lista de apps da TV; usar o mesmo
recurso do ícone.
