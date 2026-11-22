# Feature Specification: Ecrã Inicial do Launcher Google TV

**Feature Branch**: `001-launcher-home-screen`

**Created**: 2026-11-22

**Status**: Draft

**Input**: User description: "v1 do launcher pessoal para Google TV: um único ecrã inicial com quatro áreas, por prioridade. P1 — Dock e apps: dock horizontal com Netflix, YouTube, HDMI 1, HDMI 2 e 'Todos os Apps'; abrir a app nativa selecionada; ecrã 'Todos os Apps' com as apps abríveis em grelha. P2 — Cabeçalho: hora e data atuais, estado do Wi-Fi, temperatura. P3 — Clima: localização automática, temperatura, condição, máxima e mínima, previsão das próximas horas. P4 — JW.org: novidades do site, texto diário, último Broadcasting, último Boletim do Corpo Governante, com atualização automática e abertura/reprodução do conteúdo. Navegação só com D-pad, em 1920x1080 e 4K. Estética: painéis translúcidos com desfoque de fundo, reflexos, bordas luminosas e animações suaves, inspirada em iOS 26 / Liquid Glass. Referência visual: references/model-v1.png. Premissas: o utilizador define o launcher como HOME manualmente; o acesso a entradas HDMI depende de APIs do fabricante e pode não ser viável; não existe API oficial do jw.org; a TV pode estar sem rede. Fora do âmbito da v1: pesquisa, biblioteca, definições, wallpapers próprios, múltiplos ecrãs."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Abrir uma app a partir do dock (Priority: P1)

O utilizador liga a TV e, sem tocar em mais nada, vê um dock horizontal com Netflix, YouTube, HDMI 1, HDMI 2 e "Todos os Apps". Move o foco com o D-pad, carrega em OK e a app escolhida abre.

**Why this priority**: sem isto não há launcher. É o caminho que o utilizador percorre dezenas de vezes por dia e o único que tem de funcionar sempre, mesmo sem rede.

**Independent Test**: instalar a app na TV, defini-la como HOME, navegar no dock só com o D-pad e abrir cada um dos cinco itens. Entrega valor sozinho: a TV fica utilizável como ponto de partida para o conteúdo.

**Acceptance Scenarios**:

1. **Given** o ecrã inicial visível, **When** o utilizador carrega em OK com foco em Netflix, **Then** a app Netflix arranca em ecrã completo.
2. **Given** o ecrã inicial visível, **When** o utilizador move o foco para a direita, **Then** o foco avança uma posição por cada pressão, sem saltos e com o item focado visualmente distinto.
3. **Given** o foco em "Todos os Apps", **When** o utilizador carrega em OK, **Then** abre a grelha de todas as apps abríveis do dispositivo.
4. **Given** a grelha de apps aberta, **When** o utilizador carrega em OK numa app, **Then** essa app arranca; **When** carrega em Voltar, **Then** regressa ao ecrã inicial com o foco em "Todos os Apps".
5. **Given** a Netflix não está instalada, **When** o utilizador foca o respetivo item, **Then** o item mostra-se como indisponível e carregar em OK apresenta uma mensagem clara, sem fechar nem bloquear o launcher.
6. **Given** a app não é o HOME predefinido, **When** o utilizador a abre, **Then** vê instruções para a definir como HOME nos ajustes da TV, e o launcher não altera essa definição por si.

---

### User Story 2 - Saber as horas, o dia e o estado da ligação (Priority: P2)

O utilizador olha para o topo do ecrã e vê a hora, a data, se está ligado ao Wi-Fi e a temperatura atual, sem abrir nada.

**Why this priority**: é informação de relance, de custo baixo e utilidade diária, mas o launcher continua utilizável sem ela.

**Independent Test**: com o ecrã inicial aberto, comparar hora e data com a hora do sistema, desligar o Wi-Fi e confirmar que o estado muda.

**Acceptance Scenarios**:

1. **Given** o ecrã inicial visível, **When** o minuto muda, **Then** a hora apresentada acompanha sem interação do utilizador.
2. **Given** a meia-noite, **When** o dia muda, **Then** a data apresentada passa a mostrar o novo dia.
3. **Given** a TV sem ligação à rede, **When** o ecrã inicial está visível, **Then** a hora e a data continuam corretas e o estado de Wi-Fi mostra a ligação em falta.
4. **Given** a temperatura atual disponível, **When** o cabeçalho é apresentado, **Then** mostra o mesmo valor atual da área de clima.

---

### User Story 3 - Ver o clima de hoje e das próximas horas (Priority: P3)

O utilizador vê a temperatura, a condição, a máxima e a mínima do dia e a previsão das próximas horas, para a localização onde está, sem configurar nada.

**Why this priority**: informação útil mas secundária; depende de rede e não impede o uso do launcher.

**Independent Test**: com rede, confirmar que a localização é detetada automaticamente e que os valores coincidem com um serviço meteorológico público para essa localização; desligar a rede e confirmar que os últimos valores continuam visíveis.

**Acceptance Scenarios**:

1. **Given** a primeira utilização com rede, **When** o ecrã inicial abre, **Then** a localização é determinada sem qualquer pedido ao utilizador e o painel mostra temperatura, condição, máxima e mínima.
2. **Given** o painel de clima visível, **When** o utilizador olha para a previsão horária, **Then** vê pelo menos as próximas cinco horas, cada uma com hora e temperatura.
3. **Given** valores de clima em cache, **When** a TV fica sem rede, **Then** o painel mantém os últimos valores e indica que estão desatualizados, em vez de mostrar um erro.
4. **Given** a localização não pôde ser determinada, **When** o painel é apresentado, **Then** mostra que o clima está indisponível e o resto do ecrã funciona normalmente.

---

### User Story 4 - Acompanhar o conteúdo do jw.org (Priority: P4)

O utilizador vê no ecrã inicial as novidades do site, o texto do dia, o último Broadcasting e o último Boletim do Corpo Governante, e abre o que lhe interessa — texto para ler, vídeo para reproduzir.

**Why this priority**: é o conteúdo de maior valor pessoal, mas também o mais frágil (fonte sem API oficial, depende de rede) e por isso o último a estabilizar.

**Independent Test**: com rede, confirmar que as quatro entradas mostram o conteúdo mais recente publicado e que cada uma abre ou reproduz o respetivo conteúdo; reiniciar sem rede e confirmar que o último conteúdo guardado continua visível.

**Acceptance Scenarios**:

1. **Given** o ecrã inicial com rede, **When** o painel JW.org é apresentado, **Then** mostra quatro entradas distintas: novidades do site, texto do dia, último Broadcasting e último Boletim do Corpo Governante.
2. **Given** uma entrada de texto, **When** o utilizador carrega em OK, **Then** o conteúdo abre em modo de leitura e o utilizador consegue voltar ao ecrã inicial com Voltar.
3. **Given** uma entrada de vídeo, **When** o utilizador carrega em OK, **Then** o vídeo começa a reproduzir.
4. **Given** conteúdo em cache de uma atualização anterior, **When** o ecrã inicial volta a estar visível, **Then** a atualização automática decorre sem pedir nada ao utilizador e o ecrã não bloqueia enquanto decorre.
5. **Given** a atualização falha ou não há rede, **When** o painel é apresentado, **Then** mantém o último conteúdo guardado e indica a data da última atualização.

---

### Edge Cases

- **Primeira utilização sem rede**: o dock e as apps funcionam; clima e JW.org mostram estados de indisponibilidade sem mensagens de erro técnicas e sem bloquear a navegação.
- **App em falta**: Netflix, YouTube ou qualquer app da grelha pode não estar instalada; o item mantém-se visível e identificado como indisponível, e o launcher não falha.
- **Entrada HDMI sem sinal**: os itens HDMI 1 e HDMI 2 mantêm-se no dock; ao selecionar, o launcher tenta a troca via passthrough; se a entrada não estiver disponível, não altera o estado do ecrã (sem diálogo).
- **Localização negada ou indisponível**: o clima apresenta-se como indisponível; o cabeçalho omite a temperatura em vez de mostrar um valor errado.
- **Fonte do jw.org alterada**: a estrutura da fonte pode mudar sem aviso; a atualização falha em silêncio, o conteúdo anterior mantém-se e a data de atualização expõe a idade do conteúdo.
- **Regresso de uma app**: ao voltar de uma app aberta a partir do dock, o foco está no item que a lançou.
- **Navegação rápida com D-pad**: pressões repetidas não abrem apps em duplicado nem deixam o foco em itens invisíveis.
- **Nomes de apps longos**: na grelha, nomes compridos não se sobrepõem nem são cortados de forma ilegível.
- **Ecrãs 4K com overscan**: nenhum conteúdo fica cortado nas margens do ecrã.
- **Mudança de fuso horário ou hora de verão**: hora e data acompanham o sistema sem reinício do launcher.

## Requirements *(mandatory)*

### Functional Requirements

**Ecrã inicial e dock**

- **FR-001**: O launcher MUST apresentar um único ecrã inicial com quatro áreas: cabeçalho no topo, painel de clima e painel JW.org na zona central, e dock na base.
- **FR-002**: O dock MUST ser horizontal e conter, por esta ordem, Netflix, YouTube, HDMI 1, HDMI 2 e "Todos os Apps".
- **FR-003**: O utilizador MUST poder abrir a app nativa correspondente a cada item do dock, exceto os itens HDMI.
- **FR-004**: Um item do dock cuja app não está instalada MUST apresentar-se como indisponível e MUST NOT impedir a navegação nem lançar outra app.
- **FR-005**: Os itens HDMI MUST tentar mudar a entrada da TV para a entrada correspondente; quando a troca não for possível, MUST NOT alterar o estado do ecrã (sem diálogo; o foco mantém-se no item).
- **FR-006**: O item "Todos os Apps" MUST abrir uma grelha com todas as apps abríveis instaladas no dispositivo, incluindo as do dock, sem incluir o próprio launcher.
- **FR-007**: A grelha de apps MUST permitir abrir qualquer app apresentada e MUST regressar ao ecrã inicial com Voltar, repondo o foco no item que a abriu.
- **FR-008**: O launcher MUST detetar que não é o HOME predefinido e MUST mostrar instruções para o definir; MUST NOT alterar essa definição por si.

**Cabeçalho**

- **FR-009**: O cabeçalho MUST mostrar a hora atual, a data atual, o estado da ligação Wi-Fi e a temperatura atual.
- **FR-010**: A hora MUST atualizar-se sem interação e a data MUST mudar quando o dia muda, incluindo com a TV sem rede.
- **FR-011**: O estado de Wi-Fi MUST refletir a ligação no momento e MUST NOT mostrar ligação quando não existe.

**Clima**

- **FR-012**: O painel de clima MUST mostrar temperatura atual, condição, máxima e mínima do dia e previsão para pelo menos as próximas cinco horas.
- **FR-013**: A localização MUST ser determinada automaticamente, sem pedir ao utilizador para a indicar ou confirmar.
- **FR-014**: O clima MUST atualizar-se automaticamente, sem ação do utilizador, no máximo uma vez por hora.
- **FR-015**: Sem rede ou sem localização, o painel MUST manter os últimos valores conhecidos com indicação de desatualização, ou apresentar-se como indisponível quando nunca houve valores.

**JW.org**

- **FR-016**: O painel JW.org MUST apresentar quatro entradas: novidades do site, texto do dia, último Broadcasting e último Boletim do Corpo Governante.
- **FR-017**: Cada entrada MUST identificar claramente o que é e mostrar a informação mínima para o utilizador decidir abrir: título e, para vídeos, uma imagem de pré-visualização.
- **FR-018**: Selecionar uma entrada de texto MUST abrir o conteúdo em modo de leitura; selecionar uma entrada de vídeo MUST iniciar a reprodução.
- **FR-019**: O conteúdo MUST atualizar-se automaticamente sem ação do utilizador e MUST ficar guardado para apresentação offline.
- **FR-020**: Quando não há conteúdo guardado, o painel MUST mostrar um estado de indisponibilidade que não bloqueie o resto do ecrã.

**Interação e apresentação**

- **FR-021**: Toda a interface MUST ser operável apenas com o D-pad (direções, OK e Voltar), sem exigir toque, gesto ou teclado.
- **FR-022**: O foco MUST estar sempre visível e MUST indicar de forma inequívoca o item selecionado.
- **FR-023**: O ecrã inicial MUST ser apresentado corretamente em 1920x1080 e em 3840x2160, sem cortes, sobreposições ou conteúdo fora da área segura.
- **FR-024**: Os painéis MUST ter tratamento visual translúcido com desfoque do fundo, reflexos, bordas luminosas e transições de foco animadas, em linha com a referência visual `references/model-v1.png`.
- **FR-025**: As animações MUST NOT atrasar a resposta ao D-pad: a mudança de foco MUST ficar visível em menos de 200 ms.
- **FR-026**: O launcher MUST NOT modificar definições da TV, de outros pacotes ou do HOME predefinido.

### Key Entities

- **Item do dock**: posição no dock, tipo (app, entrada HDMI, grelha), app associada quando aplicável, estado de disponibilidade.
- **App instalada**: nome apresentado, identificação da app, ícone, disponibilidade para abrir.
- **Estado de ligação**: ligado ou sem ligação à rede, no momento.
- **Observação de clima**: localização, temperatura atual, condição, máxima, mínima, previsão horária, momento da leitura.
- **Entrada JW.org**: tipo (novidades, texto do dia, Broadcasting, Boletim do Corpo Governante), título, imagem de pré-visualização quando aplicável, ligação ao conteúdo, data de publicação.
- **Estado de atualização**: momento da última atualização bem-sucedida de clima e de JW.org, usado para indicar desatualização.

## Out of Scope (v1)

- Pesquisa, biblioteca e definições — apesar de aparecerem na referência visual, não fazem parte da v1.
- Wallpapers próprios ou troca de fundo pelo utilizador.
- Múltiplos ecrãs, páginas ou separadores de início.
- Definição automática do launcher como HOME e qualquer alteração de definições da TV.
- Gestão de contas, favoritos ou histórico do conteúdo JW.org.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com o ecrã inicial visível, o utilizador abre qualquer app do dock em no máximo 3 pressões de tecla.
- **SC-002**: 100% dos fluxos da v1 — dock, grelha de apps, painel de clima e painel JW.org — são concluídos apenas com o D-pad, sem qualquer entrada adicional.
- **SC-003**: Em cada mudança de foco, o destaque visual do item selecionado aparece em menos de 200 ms e nenhum item focado fica invisível.
- **SC-004**: O ecrã inicial completo fica visível e navegável em menos de 2 segundos após a TV acordar ou após Voltar de uma app.
- **SC-005**: A interface apresenta-se sem cortes, sobreposições ou elementos fora da área segura em 1920x1080 e em 3840x2160.
- **SC-006**: Sem rede, 100% dos itens do dock continuam navegáveis e abríveis, a hora e a data continuam corretas, e nenhuma área mostra mensagens de erro técnicas.
- **SC-007**: O clima e o conteúdo JW.org atualizam-se sem qualquer ação do utilizador, pelo menos uma vez por hora e uma vez por dia respetivamente, enquanto o launcher está em uso.
- **SC-008**: Todo o conteúdo de clima e JW.org visível offline tem no máximo 24 horas de idade e é identificado como guardado quando ultrapassa a última atualização.
- **SC-009**: Os textos principais são legíveis a 3 metros de distância num televisor de 55 polegadas, confirmado por leitura direta no dispositivo.
- **SC-010**: Em nenhum fluxo o launcher altera definições da TV, de outros pacotes ou do HOME predefinido.

## Assumptions

- **HOME manual**: o utilizador define o launcher como HOME nos ajustes da TV; o launcher apenas explica como o fazer.
- **HDMI depende do fabricante**: a troca usa o passthrough TvInput; depende de o fabricante expor entradas HDMI no TvInputManager. Se a entrada não estiver disponível, não há efeito visível (sem diálogo).
- **jw.org sem API oficial**: o conteúdo é obtido da fonte pública do site; a estrutura pode mudar e a atualização pode falhar sem aviso, pelo que o conteúdo guardado é a garantia de continuidade.
- **Rede intermitente**: assume-se que a TV pode estar sem rede durante períodos longos; tudo o que é essencial funciona offline e o que depende de rede degrada com indicação de idade do conteúdo.
- **Um único utilizador, uma única TV**: sem perfis, sem sincronização entre dispositivos, sem contas.
- **Localização por indicação da rede**: a localização é grosseira (nível de cidade), suficiente para clima e sem pedido de permissão interativa ao utilizador.
- **Idioma e formatos**: interface em português, hora em formato de 24 horas, data por extenso abreviada, temperatura em graus Celsius.
- **Ordem das áreas**: cabeçalho no topo, clima e JW.org lado a lado ao centro, dock na base, como na referência visual; a prioridade P1–P4 é de importância funcional, não de posição no ecrã.
- **Apps de exemplo**: Netflix e YouTube são assumidas como instaladas; se não estiverem, aplica-se o comportamento de app indisponível.
- **Sem dependências de conta**: nenhuma área exige início de sessão para mostrar informação.
