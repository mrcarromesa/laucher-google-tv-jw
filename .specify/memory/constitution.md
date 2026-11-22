# Constituição do Launcher Google TV

## Core Principles

### I. O Dispositivo do Utilizador é Inviolável

As regras de interação com a TV estão em `AGENTS.md` e são vinculativas. Sobre o
dispositivo só é permitido instalar, iniciar, parar e remover o nosso pacote
`com.mrcarromesa.tvlauncher`. Nenhum comando pode alterar a configuração do sistema,
incluindo definições, propriedades, pacotes de terceiros ou o launcher predefinido.
`AGENTS.md` prevalece sobre qualquer conveniência de desenvolvimento.

**Racional**: a TV é do utilizador e pode ser o único ecrã da casa; um comando errado
não é recuperável por rollback e quebra a confiança no produto.

### II. Alvo Explícito, Sem Código Especulativo

Existe um único alvo: Google TV / Android 14 (API 34). Não são permitidos ramos
condicionais para versões de Android, fabricantes ou variantes fora do alvo. O suporte
declarado é `compileSdk` 34, `targetSdk` 34, `minSdk` 31 — abaixo da API 31 o desfoque
de fundo não existe, e um visual alternativo seria um segundo alvo não verificável.

**Racional**: cada ramo condicional é código que ninguém corre nem verifica no
dispositivo real; restringir o alvo mantém o comportamento testável.

### III. Navegável Só com o Controlo Remoto

Toda a interface é operável apenas com D-pad. O foco tem de estar sempre visível e
nenhum fluxo pode exigir toque, gesto ou teclado. Se um ecrã não for alcançável e
utilizável com o controlo remoto, não está concluído.

**Racional**: é o único método de entrada garantido numa Google TV; acessibilidade por
D-pad é requisito funcional, não polimento.

### IV. Mínimo Justificado

Cada permissão, dependência e ecrã tem de se justificar pelo uso real no produto. Não se
adicionam permissões não usadas, dependências a mais, abstrações preventivas nem
funcionalidades "para depois".

**Racional**: superfície pequena significa menos falhas, menos atualizações de
dependências e revisão possível por uma pessoa.

### V. Verificado na TV

Uma funcionalidade só está concluída depois de correr no dispositivo real. Compilar não é
verificar: a validação faz-se com a app instalada e operada na TV, dentro dos limites do
Princípio I.

**Racional**: emuladores e builds verdes não reproduzem foco, D-pad nem desempenho de um
dispositivo Google TV real.

## Restrições Técnicas

- Linguagem: Kotlin; pacote `com.mrcarromesa.tvlauncher`.
- `compileSdk` 34, `targetSdk` 34, `minSdk` 31; Android 14 como alvo de referência.
- Sem permissões que não sejam usadas e sem dependências além das necessárias.
- A app deteta quando não é o launcher predefinido e mostra instruções ao utilizador;
  nunca se define o launcher predefinido por ADB nem por qualquer comando.
- O ambiente de build e os comandos canónicos estão documentados em `AGENTS.md`.

## Fluxo de Desenvolvimento e Qualidade

- Antes de implementar, a especificação identifica o alvo único e os ecrãs necessários;
  ecrãs sem uso real são removidos da especificação, não adiados.
- Todo o trabalho passa por: especificação → plano → tarefas → implementação →
  verificação na TV.
- A revisão confirma os cinco princípios. Complexidade, permissões ou dependências novas
  exigem justificação explícita ligada a uso real.
- Uma tarefa que só foi compilada permanece aberta até ser validada no dispositivo.

## Governance

- A constituição prevalece sobre práticas, convenções e preferências de desenvolvimento.
  As regras operacionais do dispositivo vivem em `AGENTS.md` e não são duplicadas aqui.
- Emendas fazem-se através de `/speckit.constitution`, com a alteração documentada, a
  versão atualizada e o impacto propagado aos artefactos dependentes.
- Versionamento semântico: MAJOR para remoção ou redefinição incompatível de princípios
  ou regras de governance; MINOR para novo princípio ou secção, ou orientação
  materialmente expandida; PATCH para clarificações e correções não semânticas.
- Toda a revisão verifica a conformidade com esta constituição. Violações exigem
  correção ou emenda registada, nunca exceção informal.
- Datas em formato ISO `YYYY-MM-DD`.

**Version**: 1.0.1 | **Ratified**: 2026-11-22 | **Last Amended**: 2026-11-22
