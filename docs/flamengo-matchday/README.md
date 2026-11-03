# Matchday Flamengo — jornada única de dia de jogo

Protótipo da jornada "Monte o seu dia de jogo": ingresso com setor e poltrona,
estacionamento, comidas e bebidas e a Camisa Jogo 2 personalizada num só
carrinho e num só pagamento, no SAP Commerce Cloud com Composable Storefront.

## Modelo do ingresso: poltrona como produto

Cada jogo é um produto com variantes em duas dimensões, **Setor > Poltrona**.
A poltrona é a variante final, o item que vai para o carrinho, com estoque 1:

```
JOGO-20261019-PAL                     Flamengo x Palmeiras · 19 OUT · 20h00
├── Setor Norte
│   ├── JOGO-20261019-PAL-NORTE-A01   poltrona A1   R$ 80    estoque 1 (livre) / 0 (vendida)
│   └── ...
├── Setor Leste Inferior
│   ├── JOGO-20261019-PAL-LESTE-D04   poltrona D4   R$ 160
│   └── ...
```

O mapa do estádio fica num **template** (`FLA-TEMPLATE-MARACANA`, só na versão
Staged e não aprovado, nunca aparece na loja). Ciclo de cada partida:

1. **Abrir jogo** (`scripts/abrir-jogo.groovy`): copia o template para o jogo
   novo, direto na versão Online, com adversário, data, preço por setor e
   estoque 1 por poltrona; reindexa a busca (só o produto do jogo vai para o
   índice, as poltronas não).
2. **Vender**: a poltrona vendida fica sem estoque e aparece ocupada no mapa;
   o pedido no Backoffice mostra o código da poltrona.
3. **Fechar jogo** (`scripts/fechar-jogo.groovy`): tira o jogo da loja, apaga as
   poltronas **não vendidas** (e as tira de carrinhos abandonados) e mantém as
   **vendidas**, porque os pedidos apontam para elas (histórico, reembolso,
   atendimento). Elas saem depois, com os pedidos antigos, pela política de
   retenção de dados.

Demo: 4 setores × 8 fileiras × 12 poltronas = 384 por jogo. Mesmo modelo para o
Maracanã inteiro (~78 mil lugares), com três cuidados de produção:

- **Reserva temporária**: o Commerce só baixa estoque no fechamento do pedido;
  para jogos disputados é preciso segurar a poltrona por alguns minutos no
  carrinho (desenvolvimento ou bilheteria).
- **Estoque único** com bilheteria física, sócio e cortesias.
- **Mapa por endpoint próprio**: a matriz de variantes da API OCC traz todas as
  poltronas numa resposta (~380 KB para 384 lugares); para 78 mil lugares, o
  mapa deve vir por setor, num endpoint dedicado.

## Front (este repositório)

Código em `projects/storefrontapp/src/app/matchday/`, importado no `AppModule`.
Três componentes ligados ao CMS por `CMSFlexComponent` (`flexType`):

| flexType | Onde aparece | O que faz |
|---|---|---|
| `MatchdayJourneyComponent` | página `/matchday` | stepper de 6 etapas com resumo lateral; o mapa lê as poltronas reais do jogo (livre = com estoque); a poltrona entra no carrinho, reservada, assim que é escolhida (ver "Reserva de poltrona"); no fim põe o resto no carrinho e segue para o checkout padrão |
| `MatchdayHomeBannerComponent` | topo da home | faixa "Próximo jogo em casa" com acesso à jornada |
| `MatchdayOrderConfirmationComponent` | confirmação do pedido | cartões de ingresso (QR ilustrativo), vaga, comida e camisa |

Placa do carro, forma de entrega da comida e nome/número da camisa não têm campo
na linha de carrinho padrão: no protótipo ficam no `localStorage` do navegador e
aparecem na confirmação.

## Backend (ordem para recriar o ambiente)

HAC › Console › ImpEx Import:

1. `01-template-maracana.impex` — dimensões Setor > Poltrona e o template
   (equivale a rodar `scripts/criar-template.groovy`)
2. `02-servicos-e-camisa.impex` — categorias, estacionamento, comidas, Camisa Jogo 2, personalização
3. `03-cms.impex` — página `/matchday`, banner da home, bloco da confirmação, item "Ingressos" no menu
4. `04-entrega-brasil.impex` — Brasil como país de entrega e o modo "Ingresso digital e retirada no estádio"

HAC › Console › Scripting Languages (Groovy, modo commit):

5. `scripts/abrir-jogo.groovy` — uma vez por jogo, ajustando o bloco `JOGO`
6. `scripts/fechar-jogo.groovy` — depois da partida

Loja: site `electronics-spa`, catálogo `flamengoProductCatalog`, BRL, estoque no `warehouse_s`.

## Pedido concluído = poltrona ocupada

O `order-process` padrão (OMS) manda o pedido para separação no depósito
(`sourceOrder`), que na demo falha porque os operadores de depósito de exemplo
estão com login bloqueado. Sem essa etapa nada baixa o estoque, e a poltrona
vendida continuaria livre no mapa. Na demo o processo foi ajustado pela versão
dinâmica (Backoffice › Sistema › Processos de negócio › Definições de processo
dinâmicas, código `order-process`):

| Versão | Situação |
|---|---|
| 0 | original do OMS (inativa) |
| 1 | `geocodeShippingAddress` vai direto para `postTaxes`, sem `sourceOrder` (inativa) |
| 2 (ativa) | igual à 1, e o passo `completeOrder` vira um `scriptAction` (`scripts/order-process-completeOrder.xml`) |

O `completeOrder` original do OMS marca como CANCELLED o pedido sem
consignação. O novo passo conclui o pedido (COMPLETED), baixa no `warehouse_s`
a quantidade de cada item (poltrona vendida fica com estoque 0) e grava
`statusInfo = matchday:estoque-baixado`. Fluxo de um pedido novo:
`checkOrder → fraudCheck → sendOrderPlacedNotification → geocodeShippingAddress → postTaxes → takePayment → completeOrder → success`.

Rede de segurança: o CronJob `matchdayBaixaEstoqueCronJob` (ScriptingJob
`matchdayBaixaEstoqueJob`, script `scripts/matchday-baixa-estoque.groovy`,
gatilho a cada minuto) baixa o estoque de qualquer pedido COMPLETED ainda sem a
marca, por exemplo um pedido concluído à mão no Backoffice.

Observações:
- Uma instância de processo fica presa à versão em que nasceu; reiniciar um
  pedido antigo roda a definição antiga. Para esses, basta pôr o pedido em
  COMPLETED que a rotina baixa o estoque.
- Para voltar ao fluxo padrão: ativar a versão 0 da definição `order-process`
  e desativar o gatilho do `matchdayBaixaEstoqueCronJob`. O caminho definitivo
  é liberar os operadores de depósito e usar a separação do OMS.

## Reserva de poltrona (extensão `flamengomatchday`)

Garante que duas pessoas não comprem a mesma poltrona.

| Momento | O que acontece |
|---|---|
| Torcedor clica na poltrona | a jornada já põe a poltrona no carrinho; o servidor reserva a unidade (`StockLevel.reserved`). A reserva é atômica no banco: se duas pessoas clicarem juntas, só uma consegue, a outra recebe "acabou de ser reservada" e o mapa se atualiza |
| Durante a compra | a poltrona aparece ocupada para todo mundo; a jornada mostra "reservada por 14:59" |
| Troca de poltrona, setor ou jogo | a anterior sai do carrinho e é liberada na hora |
| Carrinho abandonado | depois de 15 min o CronJob `seatHoldExpiryCronJob` (a cada minuto) libera a poltrona |
| Administrador apaga a reserva (Backoffice › Reservas de poltrona) | `SeatHoldRemoveInterceptor`: em ~2 s a poltrona volta à venda e sai do carrinho do torcedor (total recalculado). A loja relê o carrinho a cada 3 s enquanto há reserva e avisa "Houve um problema com a reserva da poltrona X… Faça uma nova reserva": na jornada, volta para Setor e poltrona; fora dela, mensagem no topo |
| Pagamento | o servidor confere a reserva (se venceu e a poltrona continua livre, reserva de novo; se outra pessoa pegou, recusa) e, antes de o processo do pedido começar, converte a reserva em venda e baixa o estoque de todos os itens |

Reservar exige login (ingresso é nominal): sem login, a poltrona clicada fica
marcada e a jornada pede para entrar. Na volta do login, ela reabre o mesmo jogo
na etapa Setor e poltrona e reserva a poltrona escolhida; se outra pessoa a pegou
nesse meio-tempo, avisa e deixa o setor aberto (a escolha vale por 15 min, em
`localStorage`, chave `fla-matchday-login`). As reservas ativas aparecem no Backoffice como
**Reserva de poltrona** (`SeatHold`).

Peças da extensão (`backend/flamengomatchday`):

| Peça | Papel |
|---|---|
| `SeatHold` (items.xml) | poltrona, carrinho, torcedor e quando vence; uma reserva por poltrona (índice único) |
| `SeatHoldAddToCartMethodHook` | reserva ao adicionar; recusa sem login; desfaz no empate |
| `SeatHoldUpdateCartEntryHook` | libera ao tirar do carrinho |
| `SeatHoldCartValidationStrategy` | para o carrinho dono, a poltrona reservada continua válida (para os outros está esgotada). Vale também na API de comércio: o `commerceWebServicesCartService` (usado pela loja Spartacus ao validar e fechar o pedido) é redefinido para usar esta estratégia; sem isso a poltrona reservada saía do carrinho como "sem estoque" |
| `SeatHoldPlaceOrderMethodHook` | confere a reserva e converte em venda ao fechar o pedido |
| `SeatHoldExpiryJob` | libera reservas vencidas |
| `SeatHoldRemoveInterceptor` + `SeatHoldRemovalContext` | reserva apagada fora do serviço (Backoffice): devolve a unidade e tira a poltrona do carrinho em segundo plano; remoções internas (venda, vencimento, troca, jogo encerrado) são marcadas e não disparam nada |

Configuração em `project.properties` (dá para sobrescrever no `local.properties`):
`flamengomatchday.seathold.minutes=15` (o front usa o mesmo valor em
`MATCHDAY_HOLD_MINUTES`), `flamengomatchday.seat.codeprefix=JOGO-`,
`flamengomatchday.warehouse=warehouse_s`.

Com a extensão, o pedido já sai com o estoque baixado (`statusInfo =
matchday:estoque-baixado`), então o passo `completeOrder` do `order-process` e o
`matchdayBaixaEstoqueCronJob` não baixam de novo; eles continuam como rede de
segurança.

### Deploy da extensão

No servidor (host do Docker), com o repositório atualizado na branch `flamengo`:

```bash
cd <pasta do repositório>
git fetch origin && git checkout flamengo && git pull
./backend/deploy-flamengomatchday.sh          # container "flamengo"
```

O script copia a extensão para `/opt/sap/hybris/bin/custom/flamengomatchday`
dentro do container, registra no `config/localextensions.xml` (guarda um
`.bak-antes-flamengomatchday`), para o servidor, roda `ant all` e sobe de novo.
O mesmo passo a passo, à mão:

```bash
docker cp backend/flamengomatchday flamengo:/opt/sap/hybris/bin/custom/
docker exec -u root flamengo chown -R hybris:hybris /opt/sap/hybris/bin/custom/flamengomatchday
docker exec -it -u hybris flamengo bash
  # dentro do container:
  vi /opt/sap/hybris/config/localextensions.xml   # adicionar <extension name='flamengomatchday' />
  cd /opt/sap/hybris/bin/platform
  ./hybrisserver.sh stop
  . ./setantenv.sh && ant all
  ./hybrisserver.sh start
```

Quando o HAC voltar:

1. **HAC › Platform › Update**: marcar só **Update running system** e
   **Localize types** (sem dados essenciais/de projeto de outras extensões) e
   clicar em **Update**. Isso cria a tabela `matchdayseatholds`.
2. **HAC › Console › ImpEx Import**: importar
   `backend/flamengomatchday/resources/impex/essentialdata-flamengomatchday.impex`
   (cria o CronJob que libera reservas vencidas).
3. Conferir: em **HAC › Console › FlexibleSearch**, `SELECT {code} FROM
   {CronJob} WHERE {code}='seatHoldExpiryCronJob'` devolve uma linha.

Para atualizar depois: rodar o script de novo e reiniciar; o Update só é
necessário quando o `flamengomatchday-items.xml` mudar.

**Atualizar sem acesso ao servidor (pelo HAC):** `backend/hac-deploy/gerar.py`
gera um Groovy que confere o md5 dos arquivos no servidor, faz backup da
extensão em `/backup/flamengomatchday-antes-<rótulo>-<data>/fontes.tgz`, grava
só os arquivos alterados e confere o md5 final. Depois basta parar o servidor,
`ant all` e subir. Detalhes em `backend/hac-deploy/README.md`.

Para remover: tirar a linha do `localextensions.xml`, `ant all`, reiniciar e
desativar o gatilho do `seatHoldExpiryCronJob`; a reserva volta a acontecer só na
conclusão do pedido.

## Tela "Jogos" no Backoffice (cadastro de jogo sem script)

Menu **Matchday Flamengo › Jogos** (Backoffice em português: Configurações › Idioma › UI Locale = Portuguese).

| Ação do usuário | O que acontece na loja |
|---|---|
| **+ Criar Jogo** (3 passos: Partida, Preços por setor, Publicação) com situação **À venda** | gera o código `JOGO-aaaammdd-ADV`, cria o produto do jogo + 384 poltronas com preço e estoque, entra na categoria da loja e reindexa (~10 s) |
| Criar com **Rascunho** | só guarda o cadastro; nada vai para a loja até mudar para À venda |
| Mudar **À venda → Rascunho** | pausa a venda (produto reprovado, sai da loja) |
| Mudar **Rascunho → À venda** | retoma a venda |
| Editar adversário, data, competição, pré-venda ou preços e **Salvar** | atualiza nome, descrição e preços na loja; estoque e poltronas vendidas não mudam |
| Mudar para **Encerrado** | tira da loja, apaga poltronas não vendidas e o produto (mantém as vendidas e os pedidos) |
| Apagar | só permitido para jogo Encerrado (ou rascunho que nunca foi para a loja) |

O campo **Último resultado** na ficha mostra o que foi feito no último salvamento. Erros (ex.: jogo encerrado não volta à venda) aparecem como mensagem do Backoffice e nada é gravado.
A lista não se atualiza sozinha depois de criar: aperte Enter na busca.

### Deploy da tela (além do deploy da extensão)
1. `ant all` + start (como hybris).
2. HAC › Platform › Update só com **Update running system** + **Localize types** (cria a tabela de jogos).
3. **Restart** do servidor (as regras do jogo só se ligam ao tipo na subida, depois que ele existe).
4. Backoffice › F4 › menu ☰ › *Show cockpit-config.xml › Reset to Defaults* (o Backoffice guarda a configuração da tela no banco; sem isso ele não lê mudanças no `flamengomatchday-backoffice-config.xml`). F4 de novo para sair.
5. Jogos criados antes da tela por `abrir-jogo.groovy`: cadastrar com `product` preenchido (o cadastro não reabre o jogo).

### Papel "Gestor de jogos" (usuário que só cria jogos)
- Papel `matchdaygamemanagerrole` (BackofficeRole) em `resources/impex/essentialdata-flamengomatchday-role.impex`:
  criar/editar/apagar Jogo, ler Reservas de poltrona e leitura de Produto/Catálogo/Usuário; leitura e escrita no
  `flamengoProductCatalog` (a tela grava produtos, preços e estoque dos jogos em nome do usuário).
- Menu reduzido: contexto `explorer-tree` com `principal="matchdaygamemanagerrole"` (sem merge-by) no
  `flamengomatchday-backoffice-config.xml` — o usuário só vê Matchday Flamengo › Jogos / Reservas de poltrona.
- Novo usuário: Backoffice › Usuário › Funcionários › criar, grupo `matchdaygamemanagerrole`, definir senha.
  Usuário de demonstração já criado no servidor: `gestor.jogos` (senha fora do repositório).
- Deploy: importar o impex (HAC › Console › ImpEx Import, ou Update com *Create essential data*) e
  Backoffice › F4 › *cockpit-config.xml › Reset to Defaults*.

### Perspectiva "Matchday Flamengo" (30/10)
- Aparece na lista de perspectivas do cabeçalho do Backoffice, junto de Administration Cockpit e Product Cockpit, para o administrador (`backofficeadministratorrole`) e para o papel Gestor de jogos (`matchdaygamemanagerrole`).
- `resources/flamengomatchday-backoffice-widgets.xml`: mesmo desenho da Administration Cockpit, só com o menu do Matchday. Menu à esquerda, busca e lista no centro, ficha embaixo. Ao abrir, "Jogos" já vem selecionado.
- O menu está no contexto `matchday-explorer-tree` em `flamengomatchday-backoffice-config.xml`. O rótulo é `matchday.perspective`.
- Quem vê: o `access` do widget compara com as *authorities* do papel, não com o uid. Por isso os dois papéis ganharam a authority `matchdayperspective` (impex do papel; no servidor foi gravado pelo HAC).
- Deploy:
  1. `ant all` e reinício.
  2. Backoffice › F4 › menu (☰) › **as duas** opções *Reset to Defaults*: a de baixo de *Show widgets.xml* (widgets) e a de baixo de *Show cockpit-config.xml* (telas). Só o reset de telas não traz a perspectiva nova.
  3. Sair e entrar de novo: as authorities são lidas no login.
- Backup no container (`/backup/antes-perspectiva-20261030/`):
  - os arquivos anteriores (`resources`);
  - os widgets gravados antes do reset (`cockpitng-widgets-config.xml`).

### Ficha do jogo sem a aba "Administração" para o Gestor de jogos (30/10)
- A aba "Administração" vem do GenericItem e mostra dados técnicos: PK, tipo, data de criação e de alteração, dono, **histórico de alterações** e sincronização.
- O papel Gestor de jogos tem a própria ficha: contexto `editor-area` com `principal="matchdaygamemanagerrole"`, sem merge-by. Com isso só aparece a aba "Jogo". O administrador continua vendo as duas abas.
- O histórico de alterações já é gravado pelo Backoffice (SavedValues): toda criação, alteração ou exclusão feita na tela, com usuário e data. Alterações feitas por script ou impex não entram.
- Deploy: Backoffice › F4 › *Reset to Defaults* da cockpit-config. Não precisa de `ant all`.

### Backoffice em português (30/10)
- Todos os rótulos do Matchday (perspectiva, árvore, assistente, ficha, campos, status) já existem em português e inglês. O idioma segue a escolha de cada usuário: avatar › *Settings* › *Language* › *UI Locale* › *Portuguese*.
- A busca do Backoffice só indexava alemão e inglês. Em português aparecia o aviso "O idioma da sessão atual não está registrado no mecanismo de pesquisa". O impex `essentialdata-flamengomatchday-backoffice-pt.impex` inclui o português no índice "Solr Config for Backoffice". Depois de importar, rode uma indexação completa desse índice.

### Relatórios de vendas no Backoffice (30/10)
Fica em Backoffice › perspectiva **Matchday Flamengo** › **Relatórios**, abaixo de Jogos e Reservas de poltrona. É uma página só, com um seletor de jogo no topo (um jogo ou todos os jogos somados):
- **Números do topo:** ingressos à venda, vendidos, disponíveis, receita total e ticket médio (receita ÷ ingressos pagos).
- **Rosca** de vendidos x disponíveis. Os reservados em carrinho aparecem quando houver.
- **Por setor:** as mesmas informações para Norte, Sul, Leste e Oeste.
- **Vendas por dia:** gráfico de linha da abertura das vendas até hoje, ou até o jogo, se ele já passou.
- **Tipo de ingresso:** inteira, meia-entrada, sócio, gratuidade e cortesia, com quantidade (#) e receita (R$).
- **Gerar PDF:** botão ao lado de Atualizar. Abre a impressão do navegador só com o relatório (A4 deitado, com cabeçalho Flamengo e nome de arquivo sugerido). Basta escolher **Salvar como PDF**. Os gráficos são SVG e saem nítidos no PDF, sem biblioteca de PDF no servidor.

De onde vêm os números:
- Poltrona vendida = estoque zerado; disponível = estoque livre, sem reserva.
- Vendas com pedido na loja usam o preço e a data do pedido. Preço abaixo da inteira conta como sócio; preço zero, como cortesia.
- Poltronas vendidas sem pedido vêm da ocupação simulada ao abrir o jogo. Para elas, o tipo, o preço e a data são gerados a partir do código da poltrona e saem sempre iguais:
  - mistura: 40% inteira, 22% meia, 26% sócio (Maraca 1/2/3 com 75/65/60% de desconto), 7% gratuidade e 5% cortesia;
  - datas: a partir da onda de cada tipo, a maioria logo na abertura e o resto perto do jogo.
- O rodapé do relatório mostra quantas vendas são simuladas.

Código:
- `MatchdayReportService` (core, `src/.../report`).
- Widget `br.com.flamengo.matchday.widgets.matchdayreport` (`backoffice/src` e `backoffice/resources/widgets/matchdayreport`). Os gráficos são SVG gerados no servidor, sem biblioteca no navegador.
- Na perspectiva, o centro virou um `perspectiveContainer` que troca entre a lista com a ficha e os relatórios, conforme o clique na árvore.

Deploy:
1. `ant all` e reiniciar (Java novo no core e no Backoffice).
2. Backoffice › F4 › menu ☰ › os dois *Reset to Defaults*: widgets.xml e cockpit-config.xml.
3. Sair e entrar de novo.

Não faça o reset antes do `ant all`: sem o widget compilado, a tela de relatórios fica vazia.

Os textos do widget (`backoffice/resources/widgets/matchdayreport/labels/*.properties`) ficam em **UTF-8 puro**, com acento direto, e não em `\u00e9`. O Backoffice lê esses arquivos sem converter os `\uXXXX` e mostraria o código na tela. Outra regra: uma chave usada no zul (`${labels.pdf}`) não pode ser o começo de outra chave com ponto (`pdf.file`). O ZK transforma a chave em grupo e o botão mostra `{file=...}`.

## Perspectiva Sócio-Torcedor: cadastro de planos (30/10)

O gestor do programa cria e mantém os planos no Backoffice, sem script. A loja e o desconto no ingresso seguem o cadastro.

- **Perspectiva "Sócio-Torcedor"**, com o menu **Planos**. Aparece para o admin e para o papel **Gestor do sócio-torcedor** (`sociomanagerrole`, autoridade `socioperspective`). O usuário `gestor.socio` é criado **sem senha**: defina a senha no Backoffice (Usuário › Funcionários). O gestor não vê a aba técnica Administração.
- **Plano** (`SocioPlan`):
  - nome, programa (Nação Maraca, Nação Sem Fronteiras...), resumo do card e benefícios (um por linha);
  - mensalidade e vagas (em branco = sem limite);
  - onda de compra: 1, 2, 3, ou 0 = sem prioridade (venda geral);
  - desconto na inteira (%), convidados e mensalidade do convidado;
  - situação: Rascunho, À venda ou Fora da loja;
  - titulares simulados (só para os relatórios) e sócios na loja (calculado).
- **Novo plano:** assistente de 4 passos (plano, ingressos e convidados, card, publicação).
- **Ao salvar "À venda"** (`SocioPlanInterceptor` → `SocioPlanService.publish`, executado como admin):
  - produto de assinatura com 1ª mensalidade no ato + 11 recorrentes;
  - estoque de vagas, grupo dos titulares e grupo marcador de carrinho;
  - convidado (`NACAO-CONVIDADO-<valor>`, reaproveitado entre planos com o mesmo valor);
  - benefícios (Entitlement);
  - promoção `X% no ingresso` para o grupo, publicada no módulo de regras numa thread alguns segundos depois de salvar;
  - reindexação da busca.
- **"Fora da loja":** o produto sai da loja (`unapproved`, categoria `socio_torcedor_fora`). Quem já é sócio continua com os benefícios. Plano que já foi para a loja não pode ser apagado.
- **Regras que a loja lê:** ficam num comentário no fim da descrição do produto: `<!--socio-plan {"wave":1,"discount":75,"maxGuests":3,"guest":"NACAO-CONVIDADO-60","group":"nacao-maraca-1","pending":"nacao-pendente-1","family":"Nação Maraca","order":1}-->`.
  - O Spartacus (`socio.service.ts`) monta os planos e o plano do torcedor a partir delas. Não há mais regra fixa no código, exceto a compatibilidade com `NACAO-MARACA-N` sem marcador.
  - A API pode levar alguns segundos para mostrar uma descrição nova (cache).
- **Carrinho e pedido:** o `SocioCartService` e o order-process (`socioPlanService.applyOrder`) usam o cadastro de planos, então valem para qualquer plano novo.
- **Migração:** `scripts/socio-planos-migracao.groovy`.
  - Põe Nação Maraca 1, 2 e 3 no cadastro, ligados aos produtos, grupos e promoções existentes.
  - Cria Nação Sem Fronteiras Anual e Mensal **fora da loja**, só com base simulada para os relatórios.
  - Troca o bloco do sócio no order-process e cria o papel e o `gestor.socio`.
  - Backup em `/backup/antes-socio-planos-<data>/`.

Deploy (nesta ordem):
1. `ant all` e reiniciar (Java e `items.xml` novos).
2. HAC › Platform › Update: **Update running system** + **Localize types**.
   - **Reiniciar de novo depois do update.** Tipo novo (`SocioPlan`) que não existia no start fica sem interceptor e sem rótulos no Backoffice até o próximo restart (em 30/10 o interceptor foi registrado em memória pelo HAC: `interceptorRegistry.registerInterceptor(socioPlanInterceptorMapping)`).
3. HAC › Console: rodar `scripts/socio-planos-migracao.groovy` em modo commit.
4. Backoffice › F4 › ☰ › os dois *Reset to Defaults* (widgets.xml e cockpit-config.xml) e entrar de novo.
5. Publicar a loja (`publicar-flamengo.sh`).

Para voltar: código anterior em `/backup/antes-socio-planos-codigo-20261030/`; dados e order-process em `/backup/antes-socio-planos-<data>/`.

### "Erro inesperado" depois de salvar no Backoffice (30/10)
- **Sintoma:** ao salvar um plano (ou um jogo) que publica na loja, aparecia "Itens gravados" e logo depois "Ocorreu um erro: Erro inesperado"; a lista ficava vazia até atualizar.
- **Causa:** o ImpEx síncrono roda como CronJob e abre e fecha uma sessão na própria thread. O Backoffice escuta o fechamento de sessão (`FallbackRequestOperationScopeController`) e apaga o contexto de operação da tela; a lista que se atualiza depois falha com `There is no context assigned to current thread`.
- **Correção:** `BackofficeRequestContext` guarda o contexto antes do ImpEx e devolve depois (`DefaultSocioPlanService` e `DefaultMatchdayGameService`). Fora do Backoffice não faz nada.

## Relatórios do sócio-torcedor (30/10)

Ficam em Backoffice › perspectiva **Sócio-Torcedor** › **Relatórios**: um painel no modelo do "Dashboard - Base" do clube, com **Atualizar** e **Gerar PDF** (impressão do navegador em A4 deitado).

Blocos:
- **Total da base ativa:** ativos total (titulares + convidados) e contratos ativos, com a variação sobre ontem. Também um cartão por programa (Nação Maraca, Nação Sem Fronteiras) com contratos, inativos e adimplentes / inadimplentes / não renovado.
- **Novos contratos por operação** (renovação, adesão, upgrade) e **por horário**, por programa, com o acumulado do dia.
- **Receita do mês:** evolução diária, competências pagas, recorrências futuras e receita prevista.
- **Base ativa** por onda de abertura e por plano (titulares, convidados, ativos total).
- **Cupons**, **churn mensal por plano** (com a diferença para a média móvel de 12 meses), **adesões por plano** (abandono de carrinho, hoje, no mês, mês passado e %) e **top 10 estados** do Sem Fronteiras.

De onde vêm os números (`DefaultSocioReportService`):
- **Base simulada (demonstração):** o campo "Titulares simulados" de cada plano define a base.
  - Os convidados seguem o máximo de convidados do plano, nas proporções do painel do clube.
  - A partir da base são gerados adimplência, adesões, receita (mensalidade × base), churn, cupons e estados.
  - Os valores são determinísticos: no mesmo dia saem iguais e mudam ao longo das horas.
- **Dados reais da loja**, somados à base:
  - sócios no grupo de cada plano;
  - pedidos com plano: adesões de hoje, do mês e do mês passado, receita do mês e estado de entrega;
  - carrinhos com plano sem pedido nos últimos 30 dias, como abandono.
- O rodapé do painel informa quanto veio da loja.

Deploy: `ant all` e reiniciar, depois Backoffice › F4 › ☰ › os dois *Reset to Defaults* (o nó Relatórios e o widget entram na perspectiva). Não precisa de update do sistema.

## Sócio-Torcedor Nação Maraca

Os três planos de https://nacao.flamengo.com.br/home/nacao-maraca, com os preços dos cards, como **produtos de assinatura** do SAP Commerce, e ligados à compra de ingressos.

| Plano | Mensalidade | Desconto na inteira | Onda de compra | Convidados |
|---|---|---|---|---|
| Nação Maraca 1 | R$ 357,90 | 75% | 1 | até 3 × R$ 60 |
| Nação Maraca 2 | R$ 165,00 | 65% | 2 | até 2 × R$ 60 |
| Nação Maraca 3 | R$ 67,10 | 60% | 3 | 1 × R$ 35 |
| Não sócio | — | — | venda geral | — |

### Servidor — `scripts/socio-torcedor-cadastro.groovy` (HAC, Groovy, commit)

- Grupos `nacao-maraca-1/2/3` (dentro de `customergroup`, senão a API `/users/{id}/customergroups` não os lista).
- Plano de cobrança mensal, termo anual renovável (`nacao-anual`), categoria `socio_torcedor`.
- Produtos `NACAO-MARACA-1/2/3`, `NACAO-CONVIDADO-60` e `NACAO-CONVIDADO-35`: plano de preço com a 1ª mensalidade paga na hora e as mensalidades 2 a 12 recorrentes; benefícios como `Entitlement`. Maraca 1 tem 50 vagas (estoque).
- Promoções `nacao_maraca_N_desconto_ingresso`: grupo do cliente + categorias dos setores → desconto % no item do ingresso. Estacionamento, comida e camisa não têm desconto.
- `flamengoProductCatalog:Online` → `promotions-context` (sem isso nenhuma promoção do catálogo era avaliada). Não mapear o Staged: dá "More than one Rules Modules".
- `order-process`: no `completeOrder`, pedido com plano põe o cliente no grupo do plano (troca de plano substitui o grupo). Testado: pedido 00004012 → `socio.teste@example.com` em `nacao-maraca-3`.
- A publicação das regras roda numa thread separada: dentro da transação do console do HAC ela trava.

### Loja

- `src/app/socio/`: `SocioService` (planos pela busca + descrição, plano do torcedor pelos grupos, ondas de venda) e `SocioPlansComponent` (página `/socio-torcedor`).
- Assinar põe o plano e os convidados no carrinho e vai para o checkout. A página mostra "Seu plano" para quem já é sócio.
- Jornada de matchday:
  - barra com o plano do torcedor;
  - selo da onda em cada jogo;
  - jogo bloqueado (com convite para virar sócio ou subir de plano) quando a venda ainda não chegou na onda do torcedor;
  - preço de sócio nos setores e no resumo. O valor cobrado vem da promoção do SAP Commerce.
- Ondas (regra simplificada da demo): cada jogo tem 4 datas no Backoffice (tela Jogos, seção "Ondas de venda"): onda 1, onda 2, onda 3 e venda geral, nessa ordem e antes do jogo. Em branco num jogo novo: 7, 6, 5 e 4 dias antes, às 10h. A loja lê as datas da descrição do produto do jogo ("Onda 1 (Nação Maraca 1): dd/mm/aaaa HHhMM; ..."). Quem já entrou continua comprando até o jogo. Jogo sem datas está em venda geral. Hoje o bloqueio é só na loja; o carrinho não confere a onda.
- CMS: `06-cms-socio.impex` (página e item "Sócio-Torcedor" no menu). Importar **depois** de publicar a loja. Para desfazer: `06-cms-socio-remover.impex`.

### Backup e como voltar

- Banco + extensão + config: `/backup/antes-socio-torcedor-20261030/` no container. `restaurar.sh` para o servidor, restaura o HSQLDB de 30/10 08:23 e sobe de novo; perde o que foi feito depois.
- Só o sócio-torcedor, sem perder o resto: `scripts/socio-torcedor-remover.groovy`. Ele volta o order-process, tira as promoções, e apaga planos, grupos e o cliente de teste. Depois, `06-cms-socio-remover.impex`.
- Front: a branch `backup-antes-socio-torcedor` é a loja de antes. Para voltar, publique por ela.

### Deploy das ondas por jogo (30/10)

Mudou o `flamengomatchday-items.xml` (4 datas no jogo), então precisa:
1. Backup: `/backup/antes-ondas-20261030/` (banco de 30/10 09:59, extensão anterior e `restaurar.sh`).
2. `./backend/deploy-flamengomatchday.sh` (branch `matchday`).
3. HAC › Platform › Update só com **Update running system** + **Localize types**.
4. Restart do servidor.
5. Backoffice › F4 › ☰ › *Show cockpit-config.xml › Reset to Defaults* (passo novo no cadastro e seção nova na ficha).

### Plano no carrinho já vale como sócio (30/10)

- Escolher o plano na página Sócio-Torcedor põe o plano no carrinho e volta para a escolha de jogos (para o mesmo jogo, se veio dele). O pagamento é um só, no fim.
- Enquanto o plano está no carrinho, o `SocioCartService` (extensão `flamengomatchday`, ganchos de incluir/alterar item do carrinho) põe o torcedor no grupo `nacao-maraca-N` e no marcador `nacao-pendente-N`. Com isso valem a onda do plano e a promoção de desconto do SAP Commerce.
- Se o plano sai do carrinho, os dois grupos saem, o carrinho é recalculado (sem desconto), e os ingressos de jogos cuja onda ainda não chegou para o torcedor saem do carrinho, liberando a poltrona.
- Quando o pedido fecha, o order-process (versão 4) tira os marcadores `nacao-pendente-*` e deixa só o grupo do plano comprado.
- Testado por OCC com `torcedor.teste@example.com`:
  - Maraca 3 no carrinho: ingresso do Bahia a R$ 32.
  - Sem o plano: os grupos saem e o ingresso do Bahia sai do carrinho.
  - Maraca 1 + Fluminense: R$ 20 com o plano, R$ 80 depois de tirar o plano.

### Sócio suspenso por inadimplência e linha do tempo (item 1 do plano de evolução)

Regra do Termo: os benefícios valem com o contrato ativo e adimplente; a inadimplência suspende os benefícios e a regularização os devolve.

| O quê | Como funciona |
|---|---|
| Situação | Grupo `socio-suspenso` (dentro de `customergroup`, a loja lê pela API). O torcedor continua no grupo do plano: mantém o vínculo e a vaga (o Maraca 1 não libera vaga). |
| Desconto | As promoções de ingresso e de loja de cada plano excluem `socio-suspenso` (`excluded_customer_groups` na condição `y_target_customers`). Regra antiga sem a exclusão é republicada na próxima publicação do plano. |
| Onda | `SocioCartService.memberWave`: suspenso compra na venda geral. Na suspensão, os carrinhos do torcedor são recalculados em segundo plano (2 s) e os ingressos de jogos cuja venda geral ainda não abriu saem do carrinho. Na regularização, os carrinhos são recalculados e o desconto volta. |
| Linha do tempo | Tipo `SocioEvent` (torcedor, tipo, data, plano, origem, autor, motivo, resultado). Backoffice › Sócio-Torcedor › **Eventos do sócio**. |
| Suspender / regularizar | Criar um evento **Suspensão** ou **Regularização** (Backoffice ou Claude) aplica a mudança na hora (`SocioEventInterceptor`). Suspensão de quem não é sócio é recusada. |
| Regularização pelo torcedor | Cartão novo salvo na conta (Meu sócio-torcedor › trocar cartão, ou no pagamento) de um sócio suspenso: "mensalidade em atraso cobrada no cartão novo" e os benefícios voltam (`SocioCardRegularizationInterceptor`, tipo CreditCardPaymentInfo, só cartão salvo e não duplicado). |
| Eventos automáticos | Adesão ou mudança de plano no pedido (`applyOrder`) e cancelamento ao fechar o chamado (`SocioCancelTicketInterceptor`). O cancelamento tira também o grupo `socio-suspenso`. |
| Relatório | Cartão de cada família (Relatórios): Inadimplentes = base simulada pelas taxas + suspensos reais da loja, que saem de Adimplentes (cada suspensão sobe Inadimplentes em 1). Embaixo de Inadimplentes, em vermelho: "dos quais N suspenso(s) agora na loja: nomes" (até 3 nomes). |
| Chave | `flamengo.socio.suspensao.ativa` (padrão `true`). `false`: ninguém novo é suspenso; regularizar continua funcionando. |
| Papel | `sociomanagerrole`: lê e cria `SocioEvent`, lê `SocioEventType`. |

Depois do `ant all`: Update no HAC (tipo novo `SocioEvent` e enum `SocioEventType`), ImpEx do papel (`essentialdata-flamengomatchday-socio-role.impex`) e republicar os planos à venda para as promoções ganharem a exclusão.

- **Republicar vários planos**: chamar `publish` de cada plano em sequência faz as publicações de regras competirem e só a última publica (as outras ficam `UNPUBLISHED`, a versão anterior continua valendo). Publique as regras de uma vez (`publishRules` com todos os códigos). Cada publicação também reindexa a busca: o servidor tem 2 GB de heap e caiu depois de três reindexações seguidas (01/11).
- Testado em 01/11 com o torcedor21 (sócio Maraca 2 simulado): suspensão tira o ingresso do Bahia (onda de sócio) do carrinho e põe o do Fluminense a preço cheio (R$ 80); regularização pelo Backoffice e por cartão novo devolvem o desconto (R$ 28); suspender quem não é sócio é recusado.

**Loja e Claude (PR B)**

| Onde | O que aparece |
|---|---|
| Selo no header | "Benefícios suspensos" em âmbar, leva a Meu sócio-torcedor |
| Meu sócio-torcedor | Situação "Benefícios suspensos" e aviso com **Regularizar agora**, que abre o cadastro de cartão novo; ao salvar, mensagem "Pagamento regularizado…" e selo, ondas e descontos voltam |
| Jornada de matchday | Barra "Plano X com benefícios suspensos · Regularizar"; jogo fora da venda geral mostra o bloqueio com o motivo e o botão Regularizar |
| `SocioService` | `Membership.suspended` (grupo `socio-suspenso` pela API `customergroups`); suspenso vale como venda geral, sem desconto |
| Claude, gestor do sócio | `situacao_socio`, `suspender_socio`, `regularizar_socio` (objeto de integração `FlamengoEventoSocio`, permissão no `socioapigroup`); `buscar_torcedor` mostra a situação |
| Claude, torcedor | `minha_conta` mostra "Benefícios suspensos" e como regularizar |

**Tela Sócio-Torcedor › Sócios no Backoffice (PR C)**

Atendimento do sócio sem sair do Backoffice, para a demo mostrar o fluxo pelo sistema e deixar o Claude para o final.

| Parte | Como funciona |
|---|---|
| Menu | Sócio-Torcedor › **Sócios**, entre Planos e Eventos do sócio (nó `socio.tree.members`, widget `sociomembers`). |
| Busca | Nome ou e-mail (sem acento e sem maiúsculas), filtro de plano e de situação. Lista quem é ou já foi sócio: grupo de algum plano, evento na linha do tempo ou chamado de cancelamento. Até 200 por busca. |
| Situação | Em dia · Benefícios suspensos · Cancelamento pedido (chamado "Cancelamento do plano…" aberto) · Ex-sócio. |
| Ficha | Plano, família, sócio desde (primeiro pedido do plano ou adesão registrada), vigência (12 meses), mensalidade, convidados (do último pedido), total por mês, onda, descontos, próxima mensalidade (mesmo dia do mês da adesão), cartão padrão (final) e chamado de cancelamento aberto. |
| Ações | **Simular mensalidade atrasada** (evento Suspensão), **Regularizar pagamento** (Regularização), **Registrar observação** e **Confirmar cancelamento** (fecha o chamado aberto; o `SocioCancelTicketInterceptor` tira o torcedor do plano). Cada ação pede confirmação, aceita um motivo e grava o evento com o gestor logado como autor e origem Backoffice. Botões desligados quando a ação não cabe (ex.: regularizar quem não está suspenso). |
| Histórico | Linha do tempo (eventos), pedidos do plano e chamados do torcedor. |
| Código | `SocioMemberService` / `DefaultSocioMemberService` (busca e ficha sem restrições de busca; ações com o usuário da sessão) e `SocioMembersController` + `SocioMemberHtml` no Backoffice. |

### Elegibilidade para comprar ingresso, com motivo (item 2 do plano de evolução)

Cada tentativa de pôr uma poltrona no carrinho é conferida e gravada, aprovada ou negada, com o motivo. **PR 1 (servidor) começa em modo observação**: nada muda para o torcedor; a decisão só fica registrada para o atendimento e para os relatórios.

| Condição | Regra |
|---|---|
| Plano | Melhor plano do torcedor (grupo do plano), contando o plano que está só no carrinho. |
| Situação | Suspenso (`socio-suspenso`) perde a onda de sócio e compra na venda geral. |
| Janela | Onda aberta no jogo (`SocioCartService.currentWave`) ≥ onda do torcedor (1, 2, 3; 4 = venda geral). Jogo sem datas de onda está em venda geral. |
| Biometria | Grupo `biometria-validada`. Só informativa; com `flamengo.socio.biometria.exigida=true`, biometria pendente nega. |
| Limite por jogo | Ingressos do jogo nos pedidos (sem versões antigas nem cancelados) + no carrinho + os que estão entrando ≤ limite. Sócio em dia: titular + convidados do plano (Maraca 1 = 4, Maraca 2 = 3, Maraca 3 = 2). Não sócio ou suspenso: `flamengo.ingressos.limite.geral` (4). |

| Parte | Como funciona |
|---|---|
| Registro | Tipo `SocioEligibilityDecision` (torcedor, jogo, poltrona, carrinho, pode comprar, recusada, motivos, condições conferidas, plano, onda do torcedor, onda aberta, ingressos que já tinha, limite, origem). Backoffice › Sócio-Torcedor › **Decisões de elegibilidade** (só leitura). |
| Gancho | `SocioEligibilityAddToCartHook` (antes de a poltrona entrar no carrinho) chama `SocioEligibilityService.checkAddToCart`. Erro na conferência nunca impede a compra. |
| Chaves | `flamengo.socio.elegibilidade.registro` (true: grava), `flamengo.socio.elegibilidade.bloqueio` (false: só observa; true: recusa a poltrona com o motivo), `flamengo.socio.biometria.exigida` (false), `flamengo.ingressos.limite.geral` (4). |
| Tela Sócios | Ficha mostra **Biometria facial** (Validada / Pendente); ações **Biometria validada** e **Biometria pendente** (grupo `biometria-validada` + observação na linha do tempo); histórico ganha **Tentativas de compra de ingresso** (últimas 10, com a decisão e o motivo). |
| Papel | `sociomanagerrole` lê `SocioEligibilityDecision`. |

Depois do `ant all`: Update no HAC (tipo novo `SocioEligibilityDecision`) e ImpEx do papel (`essentialdata-flamengomatchday-socio-role.impex`, cria o grupo `biometria-validada`).

**Loja: cartão "Sua elegibilidade para este jogo" (PR 2)**

Na etapa 1 da jornada, com o jogo escolhido e o torcedor logado, o cartão mostra as mesmas condições que o servidor confere, cada uma com ✓ (atendida), ! (aviso que não impede) ou ✕ (não atendida), e um resumo ("Tudo certo…" ou "Você ainda não pode comprar…").

| Condição | De onde vem na loja |
|---|---|
| Plano e situação | `SocioService.getMembership()` (grupos do torcedor pela API `customergroups`; plano no carrinho já vale) |
| Janela | `waveFor(match)` (datas das ondas do jogo) |
| Biometria | grupo `biometria-validada` (`Membership.biometry`) |
| Limite por jogo | pedidos do torcedor (`SocioAccountService.orderedQuantities`, últimos 20, sem cancelados) + carrinho; limite = 1 + convidados do plano, ou `ELIGIBILITY.generalLimit` |

`ELIGIBILITY` (`socio.model.ts`) espelha as chaves do servidor `flamengo.socio.biometria.exigida` e `flamengo.ingressos.limite.geral`; mudar uma chave no servidor pede a mesma mudança ali. A loja continua bloqueando só pela onda (como antes); o limite e a biometria aparecem no cartão, e o bloqueio por eles fica para o PR 3, junto com a chave de bloqueio do servidor.

**Bloqueio ligado e pergunta ao Claude (PR 3)**

| Parte | Como funciona |
|---|---|
| Servidor | `flamengo.socio.elegibilidade.bloqueio=true` no `local.properties` do servidor da demo (o padrão do `project.properties` continua `false`). Tentativa negada é recusada no carrinho com o motivo e fica gravada como "negada e recusada". |
| Loja | `ELIGIBILITY.blocking = true` (espelha a chave). Com uma condição ✕ no cartão, "Continuar" e a escolha de poltrona ficam bloqueadas, como já acontecia com a onda. Se o servidor recusar a poltrona, a jornada mostra "Não foi possível reservar a poltrona:" + o motivo (antes: "reservada por outra pessoa"). A biometria segue só como aviso (`biometryRequired = false`). |
| Claude (gestor do sócio) | Ferramenta `elegibilidade_torcedor`: tentativas de um torcedor (ou as negadas de todos), com a decisão, o motivo e as condições conferidas. Objeto de integração `FlamengoElegibilidade` (só leitura; `docs/flamengo-matchday/mcp/integracao-gestores.impex`), permissão de leitura no `socioapigroup`. |

Desligar o bloqueio: tirar a linha do `local.properties` e reiniciar; na loja, `ELIGIBILITY.blocking = false` e publicar. Próximos PRs do item: cartão "Sua elegibilidade para este jogo" na jornada (loja) e, depois do teste, ligar o bloqueio e a pergunta ao Claude ("por que o torcedor X não conseguiu comprar").

Cuidado: nenhum valor de rótulo do widget pode ter `\n`. O Backoffice vira quebra de linha real e, se a linha seguinte tiver `{` (ex.: "O chamado {1}…"), o leitor do ZK recusa e o login do Backoffice inteiro dá erro 500 (aconteceu na primeira subida do PR #63; corrigido trocando por espaço).

Deploy: sem tipo novo, então `ant all` e subir (sem Update no HAC); depois Backoffice › F4 › ☰ › os dois *Reset to Defaults* (widgets.xml e cockpit-config.xml) para o nó Sócios e o widget entrarem na perspectiva. Não fazer o reset antes do `ant all`.

### Convidados nominais (item 3 do plano de evolução)

O convidado já é **contratado e cobrado** na compra do plano (produto `NACAO-CONVIDADO-60` ou `-35`, quantidade escolhida na página de planos). O item 3 deixa o titular dar nome a essas vagas em Minha conta › Meu sócio-torcedor › **Meus convidados**. **PR 1 (servidor)**: tipo, regras, endpoints e telas do gestor; a loja chega no PR 2.

| Regra | Como funciona |
|---|---|
| Limite | Vagas de convidado contratadas no último pedido do plano (`SocioGuestService.contractedSlots`). Sem vaga, a recusa explica e indica mudar o plano. |
| Situação | Só sócio inclui; suspenso (`socio-suspenso`) vê os convidados e não inclui novos. |
| Dados | Nome completo, CPF, nascimento e e-mail obrigatórios; celular opcional (10 ou 11 dígitos). |
| CPF | Dígito verificador; não pode ser convidado ativo de outro sócio nem repetido no mesmo titular. O titular não tem CPF na demo, então não há comparação com ele. |
| Troca | Livre: tirar encerra o registro (`active=false`, `until`, `endReason`) e libera a vaga. Convidado com ingresso de jogo futuro não poderá sair (entra no PR 3, com a compra para o convidado). |
| Cancelamento | Fechar o chamado de cancelamento (`SocioCancelTicketInterceptor`) encerra os convidados ativos ("Cancelamento do plano"). |
| Linha do tempo | Incluir e tirar gravam uma observação no sócio (CPF mascarado `***.456.789-**`). |
| Chave | `flamengo.socio.convidados.ativo` (true); false recusa inclusões e a loja esconde o cadastro. |

| Parte | Como funciona |
|---|---|
| Tipo | `SocioGuest` (titular no atributo `customer` — `owner` já existe em todo item —, nome, CPF só dígitos, e-mail, celular, nascimento, plano, ativo, desde, até, motivo do encerramento), typecode 25906. |
| API de comércio | `GET`, `POST` e `DELETE /{baseSiteId}/users/current/socioguests[/{id}]` (`SocioGuestsController`, primeiros endpoints próprios da extensão). Resposta: plano, vagas (`contracted`, `used`), `canAdd` e `reason`, convidados ativos e os últimos 10 encerrados. Recusa: 400 com `{"errors":[{"type":"SocioGuestError","message":"…"}]}`. O bean fica em `resources/occ/v2/flamengomatchdayocc/web/spring/flamengomatchday-web-spring.xml`: a `commercewebservices` importa `classpath*:/occ/v2/*occ/web/spring/*-web-spring.xml`, por isso a pasta termina em `occ`. |
| Tela Sócios | Histórico ganha **Convidados nominais** (vagas em uso, ativos e encerrados). |
| Menu | Sócio-Torcedor › **Convidados** (só leitura). Papel `sociomanagerrole` lê `SocioGuest`. |

Deploy: `ant all`; Update no HAC (tipo novo `SocioGuest`, só "Update running system" + "Localize types"); ImpEx do papel à parte; reiniciar o servidor antes de testar o Backoffice (cache dos nomes do tipo novo); Backoffice › F4 › ☰ › *Reset to Defaults* do cockpit-config para o menu Convidados aparecer.

**Loja: Meus convidados (PR 2)**

Cartão **Meus convidados** em Minha conta › Meu sócio-torcedor (`SocioGuestsComponent`, dentro da página do sócio; some para ex-sócio, para quem não é sócio e com `flamengo.socio.convidados.ativo=false`).

| Parte | Como funciona |
|---|---|
| Vagas | Bolinhas e "1 de 2 vaga(s) de convidado em uso", com o valor da vaga ("já está na sua mensalidade"). |
| Lista | Nome (desde quando), CPF, nascimento e contato; **Tirar convidado** pede confirmação na própria linha. |
| Incluir | Formulário com nome completo, CPF (máscara), nascimento, e-mail e celular opcional (máscara). A tela confere nome, CPF (dígito verificador), data, e-mail e celular antes de enviar; o servidor confere de novo e a recusa dele aparece embaixo do formulário. |
| Sem poder incluir | Mensagem conforme o caso: suspenso, nenhuma vaga contratada (o máximo do plano), todas as vagas ocupadas. |
| Anteriores | "Ver convidados anteriores" lista os encerrados com o período e o motivo. |
| API | `SocioGuestsService`: `GET`, `POST`, `DELETE /users/current/socioguests`. A recusa (`SocioGuestError`) não gera aviso global do Spartacus; a mensagem vai só para o cartão. |
| Textos | `sg.*` em `fla-texts.ts` (pt e en). |

### Termo do Nação versionado (item 4 do plano de evolução)

A adesão exige o aceite do **Termo do Nação**, e cada versão nova pede um novo aceite. Desenho aprovado em 02/11: aceite na revisão do checkout (só com plano no carrinho); versão nova só gera aviso (nada é bloqueado); a v2026.10 conta como aceita por quem já era sócio (migração); o texto fica no Backoffice.

| Regra | Como funciona |
|---|---|
| Versão atual | A última `SocioTermVersion` com **Publicado em** preenchido e não futuro. Sem "Publicado em" = rascunho. Sem versão publicada, nada muda. |
| Adesão | Pedido com plano (`SocioPlanService.findForProduct`) só fecha com o aceite da versão atual **para aquele carrinho** (`SocioTermPlaceOrderMethodHook.beforePlaceOrder`). Recusa: "Para concluir a adesão, leia e aceite o Termo do Nação v…". |
| Depois do pedido | O aceite ganha o número do pedido e o plano (`beforeSubmitOrder`) e vira evento **Aceite do Termo** na linha do tempo. |
| Versão nova | O sócio aceita na área do sócio (aceite sem carrinho, origem "Área do sócio", evento na hora). Quem não aceita continua comprando; só aparece como pendente. |
| Migração | `scripts/socio-termo-migracao.groovy`: cria a v2026.10 publicada em 01/10/2026 e a v2026.11 em rascunho; grava o aceite da v2026.10 de cada sócio atual (origem "Migração", data da adesão). Rodar **depois** de a loja com a caixa de aceite estar publicada (PR 4b): com versão publicada e a loja antiga, o checkout do plano seria recusado. |
| Chave | `flamengo.socio.termo.ativo` (true); false = checkout e área do sócio como antes. |

| Parte | Como funciona |
|---|---|
| Tipos | `SocioTermVersion` (versão única, título, texto e "o que mudou" em pt/en, publicado em) e `SocioTermAcceptance` (torcedor, versão, data, origem Checkout / Área do sócio / Migração, plano, carrinho, pedido). Valor novo `ACEITE_TERMO` em `SocioEventType`. |
| API de comércio | `GET /{baseSiteId}/users/current/socioterm` (versão atual com título e texto na língua da chamada, aceitou?, último aceite) e `POST /{baseSiteId}/users/current/socioterm/acceptance` (`{"cartId"}` no checkout; sem corpo na área do sócio). Recusa: 400 `SocioTermError`. |
| Backoffice | Sócio-Torcedor › **Termo do Nação** (versões; o gestor cria, edita e publica preenchendo "Publicado em") e **Aceites do Termo** (só leitura). Ficha do sócio: campo **Termo do Nação** ("v2026.10 aceito em 01/10/2026 (Checkout, pedido …)" ou "Pendente: versão v2026.11 ainda não aceita"). Papel `sociomanagerrole`: versões leitura/criação/alteração, aceites leitura. |
| Claude (MCP) | Gestor do sócio: `termo_aceites` (versão atual, rascunhos, quem aceitou e quem falta; com e-mail, o histórico do torcedor). Torcedor: `finalizar_compra` com plano mostra o Termo no resumo e grava o aceite ao confirmar. Objeto `FlamengoTermo` em `mcp/integracao-gestores.impex`. |

Deploy (PR 4a): `ant all`; Update no HAC (tipos novos e o valor novo do enum, só "Update running system" + "Localize types"); ImpEx do papel e da Integration API à parte; reiniciar antes de testar o Backoffice. A migração entra depois do PR 4b.

### Regras de saída: arrependimento, multa e reembolso (item 5 do plano de evolução)

O cancelamento passa a mostrar e registrar quanto o sócio recebe de volta ou paga. Desenho aprovado em 02/11: arrependimento de 7 dias com reembolso total; fora dele, multa de 10% sobre as mensalidades restantes; vale o dia do pedido de cancelamento; o texto das regras entra só na v2026.11 do Termo.

| Regra | Como funciona |
|---|---|
| Contrato | Começa no pedido mais novo com o plano atual (o mesmo que a área do sócio usa). 12 mensalidades: a 1ª paga na compra, as outras no mesmo dia dos meses seguintes. Pagas até a data-base = meses completos + 1. |
| Arrependimento | Pedido até 7 dias corridos depois da adesão (inclusive): **reembolso** do que foi pago na adesão (plano + convidados), sem multa. |
| Multa | Depois do prazo: **10%** × mensalidades restantes × mensalidade (plano + convidados). Ex.: Maraca 3 (R$ 67,10) pedido no 3º mês → 9 × 67,10 × 10% = R$ 60,39. No último mês, sem multa. |
| Data-base | A abertura do chamado "Cancelamento do plano…": o valor mostrado na loja ao pedir é o que vale quando o atendimento confirma. Sem pedido aberto, a simulação é com a data de hoje. |
| Registro | Ao fechar o chamado (`SocioCancelTicketInterceptor`), o evento **Cancelamento** ganha `refundAmount` ou `fineAmount` e o resultado diz o valor ("…; reembolso de R$ 67,10 (arrependimento até 09/11/2026)"). |
| Chave | `flamengo.socio.saida.ativo` (true); `flamengo.socio.saida.arrependimento.dias` (7) e `flamengo.socio.saida.multa.percentual` (10). false = cancelamento como antes, sem valores. |

| Parte | Como funciona |
|---|---|
| Tipo | `SocioEvent` ganha `refundAmount` e `fineAmount` (R$), só no evento de cancelamento confirmado. Nada é renomeado. |
| API de comércio | `GET /{baseSiteId}/users/current/socioexit`: simulação (prazo e último dia, dentro/fora, mensalidades pagas e restantes, mensalidade, pago na adesão, reembolso, multa, resumo em uma linha, chamado aberto) e `settlement` com o valor do último cancelamento confirmado. Chave desligada: `{"enabled": false}`. |
| Loja (PR 5b) | Painel de cancelamento em Meu sócio-torcedor mostra a simulação antes de confirmar e a manda na mensagem do chamado ("Simulação: …"); com o pedido aberto, mostra o valor previsto; o ex-sócio vê o valor confirmado. |
| Backoffice | Ficha do sócio: campo **Se cancelar hoje** / **Saída (pedido de …)** / **Saída confirmada** com reembolso (verde) ou multa (laranja). "Confirmar cancelamento" responde com o valor. Evento do sócio mostra Reembolso e Multa. Relatório do sócio: quadro **Estornos e multas** (total, quantidade e mês) e coluna Reembolso / multa nos últimos pedidos (previsto enquanto o pedido está aberto). |
| Termo | `scripts/socio-saida-termo.groovy`: troca o parágrafo de cancelamento da v2026.11 (rascunho) pelo que traz prazo e multa e atualiza o "O que mudou". Não altera versão publicada. |

Deploy (PR 5a): `ant all`; Update no HAC (atributos novos de `SocioEvent`, só "Update running system" + "Localize types"); reiniciar antes de testar o Backoffice (Reset to Defaults para ver os campos novos do evento); rodar `socio-saida-termo.groovy` (conferir, depois aplicar).

### Vigência, renovação, upgrade e downgrade (item 6 do plano de evolução)

O sócio tem um contrato de 12 meses com renovação automática. Desenho aprovado em 02/11: desligar a renovação ou agendar um plano menor até 30 dias antes do fim; upgrade recomeça o contrato; a virada (ficha do sócio ou Claude) simula o aniversário com 12 meses a partir de hoje; sem renovação, encerra sem multa.

| Regra | Como funciona |
|---|---|
| Contrato | `SocioContract` (um por torcedor): plano, início, fim, renovação automática, plano na renovação, origem (pedido ou Renovação), renovações, encerrado em. **Sem contrato gravado vale o cálculo de antes**: pedido mais novo do plano + 12 meses, renovação ligada. O contrato é gravado na primeira escolha do sócio ou na virada. |
| Prazo | Desligar a renovação e agendar a mudança só até `fim − 30 dias` (`flamengo.socio.vigencia.prazoRenovacao.dias`). Depois disso fica travado até a virada ("Prazo para mudar encerrado em …"). |
| Plano menor | Da mesma família, à venda e com mensalidade menor. Fica agendado (`nextPlan`) e só vale na virada. Pode ser cancelado até o prazo. |
| Upgrade | Pedido novo com plano: o evento Adesão recomeça o contrato gravado (início = pedido, renovação ligada, sem mudança agendada). |
| Virada | Evento **Renovação** criado na ficha ("Virar a vigência agora"), em Eventos do sócio ou pelo Claude (`virar_vigencia`): renovação ligada → 12 meses a partir de hoje no plano agendado (troca o grupo do plano; convidados acima do limite do plano novo são encerrados); renovação desligada → o evento vira **Fim de vigência**, sai do plano sem multa e os convidados são encerrados. |
| Saída (item 5) | Depois de uma renovação, a simulação de cancelamento conta as mensalidades a partir da renovação e não tem arrependimento. |
| Chave | `flamengo.socio.vigencia.ativo` (true); false = vigência calculada do pedido, sem escolhas nem virada. |

| Parte | Como funciona |
|---|---|
| Tipos | `SocioContract` (typecode 25909) e valores novos `RENOVACAO` e `FIM_VIGENCIA` em `SocioEventType`. Nada renomeado. |
| API de comércio | `GET /{baseSiteId}/users/current/sociocontract` (vigência, renovação, mudança agendada, prazo, planos menores); `POST /sociocontract/autorenew` `{"autoRenew": false}`; `POST /sociocontract/nextplan` `{"nextPlanCode": "NACAO-MARACA-3"}` (vazio cancela). Recusa: 400 `SocioContractError`. Cada escolha grava uma Observação na linha do tempo. |
| Loja (PR 6b) | Meu sócio-torcedor: vigência real, chave de renovação automática e "Mudar para um plano menor" agendado. |
| Backoffice | Ficha do sócio: "Vigência até" pelo contrato, campo **Renovação** (automática / muda para … / desligada, com o prazo) e botão **Virar a vigência agora**. Sócio-Torcedor › **Contratos** (só leitura). Papel `sociomanagerrole`: leitura de `SocioContract`. |
| Claude (MCP) | Gestor do sócio: `virar_vigencia` (com confirmação) e a vigência em `situacao_socio`. Objeto `FlamengoContrato` em `mcp/integracao-gestores.impex`. |

Deploy (PR 6a): `ant all`; Update no HAC (tipo novo e valores novos do enum, só "Update running system" + "Localize types"); ImpEx do papel (`essentialdata-flamengomatchday-socio-role.impex`) e da Integration API (`FlamengoContrato`) à parte; reiniciar antes de testar o Backoffice; Reset to Defaults para o nó Contratos.

### Pix e boleto à vista e plano aguardando pagamento (item 7 do plano de evolução)

Desenho aprovado em 02/11: a forma de pagamento só aparece com plano no carrinho (sem plano, o checkout continua só com cartão). Cartão: mensalidade (como antes) ou o ano inteiro em até 12x. Pix e boleto: o ano inteiro (12 mensalidades do plano e dos convidados), sem desconto. Pix/boleto: o pedido fica aguardando pagamento e só vira sócio quando o gestor confirma.

| Regra | Como funciona |
|---|---|
| Formas | `CARTAO_MENSAL` (vazio = como antes), `CARTAO_ANUAL` (parcelas 1 a `flamengo.socio.pagamento.parcelas`), `PIX`, `BOLETO`. Gravadas no carrinho e copiadas para o pedido (`socioPaymentMethod`, `socioInstallments`). |
| Ano inteiro | `CARTAO_ANUAL`, `PIX` e `BOLETO`: o preço base da linha do plano e das linhas de convidado é 12 × mensalidade (`SocioAnnualPricing` embrulha a estratégia de preço do `calculationService`; o resto do carrinho não muda). |
| Planos | Campo **Aceita Pix e boleto (ano inteiro)** no cadastro do plano (`upfrontPayment`, ligado por padrão). Desligado: só cartão. |
| Pix e boleto | Fecham o pedido sem cartão (`POST .../carts/{id}/sociopayment/order`), com código fictício (Pix copia e cola no formato BR Code, linha digitável de boleto) e vencimento (Pix 30 min, boleto 3 dias, propriedades). |
| Aguardando | O order-process (`applyOrder`) vê o pedido não pago: tira os grupos que o plano no carrinho deu (o plano que o torcedor já pagava fica) e grava **Aguardando pagamento**. Os ingressos do mesmo pedido ficam reservados com o preço de sócio. Pedido aguardando não conta como contrato, convidados nem saída. |
| Confirmação | Evento **Pagamento confirmado** (botão **Confirmar pagamento** na ficha, Eventos do sócio ou Claude `confirmar_pagamento`): grava `socioPaymentConfirmedAt` e roda o `applyOrder` (adesão, grupos, contrato). Observação com o número do pedido escolhe o pedido; sem, vale o mais novo aguardando. |
| Cartão com Pix escolhido | O gancho do pedido recusa fechar com cartão se o carrinho está com Pix/boleto; carrinho sem plano perde a forma. |
| Chave | `flamengo.socio.pagamento.ativo` (true); false = só cartão com a mensalidade (carrinhos voltam à mensalidade; pedidos guardam o que foi cobrado). |
| Fraude | A checagem de fraude padrão manda para revisão manual pedidos acima de R$ 1.000 (+50 pontos a cada R$ 100; revisão a partir de 500), e o ano inteiro do Maraca 1 e 2 passa disso. `flamengo.fraude.limitePedido=10000` sobe o limite do sintoma "Price threshold" na subida do servidor (`SocioFraudThreshold`); vazio = padrão. |

| Parte | Como funciona |
|---|---|
| Tipos | Enum `SocioPaymentMethod`; valores novos `AGUARDANDO_PAGAMENTO` e `PAGAMENTO_CONFIRMADO` em `SocioEventType`; `SocioPlan.upfrontPayment`; atributos `socioPayment*` no `AbstractOrder`. Nada renomeado. |
| API de comércio | `GET/PUT /{baseSiteId}/users/current/carts/{cartId}/sociopayment` (opções e escolha `{"method":"PIX"}` / `{"method":"CARTAO_ANUAL","installments":10}`); `POST .../sociopayment/order` (Pix/boleto); `GET /users/current/orders/{code}/sociopayment` (código, vencimento, confirmação); `GET /users/current/sociopayment` (Pix/boleto mais novo aguardando). Recusa: 400 `SocioPaymentError`. |
| Backoffice | Situação **Aguardando pagamento** na lista e na ficha (aviso laranja), campo **Pagamento do plano** e botão **Confirmar pagamento**. Cadastro do plano: campo novo na seção de preço. |
| Claude (MCP) | Gestor do sócio: `confirmar_pagamento` (com confirmação) e `pagamentoPendente` em `situacao_socio`. |

Deploy (PR 7a): `ant all`; Update no HAC (atributos e valores novos, só "Update running system" + "Localize types"); reiniciar antes de testar o Backoffice.

### Pontos, experiências e carteirinha (item 8 do plano de evolução)

Desenho aprovado em 02/11: pontos calculados no servidor (nada de saldo gravado), vitrine de experiências cadastrada no Backoffice e resgate com voucher; carteirinha digital na área do sócio e numa página para o celular (PR 8b, loja).

| Regra | Como funciona |
|---|---|
| Ganho | Cada ingresso dá `flamengo.socio.pontos.porIngresso` (100) e cada real em produtos dá `flamengo.socio.pontos.porReal` (1). Plano e convidados não contam. Contam os pedidos fechados desde a adesão (primeiro pedido com plano; sem pedido, o primeiro evento Adesão). Pix/boleto aguardando e pedidos cancelados não contam. Mais um bônus de adesão de `flamengo.socio.pontos.bonusAdesao` (500), uma vez. Igual para todos os planos. |
| Saldo | Ganhos menos os resgates não cancelados. Calculado a cada consulta. |
| Resgate | Só sócio em dia (nem suspenso, nem aguardando pagamento, nem ex-sócio), experiência na vitrine, plano com mensalidade igual ou maior que a do plano mínimo, vaga sobrando e saldo suficiente. Gera `SocioRedemption` com voucher `NACAO-XXXX-XXXX` e o evento **Resgate de pontos** na linha do tempo. |
| Cancelar resgate | No Backoffice (Resgates de pontos), preencher **Cancelado em**: os pontos e a vaga voltam. |
| Chave | `flamengo.socio.pontos.ativo` (true); false = sem pontos na loja, na ficha e no Claude. |

| Parte | Como funciona |
|---|---|
| Tipos | `SocioExperience` (código, nome, descrição, pontos, quantidade, plano mínimo, na vitrine, ordem) e `SocioRedemption` (voucher, torcedor, experiência, pontos, data, plano, origem, cancelado em); valor `RESGATE_PONTOS` em `SocioEventType`. Nada renomeado. |
| Vitrine inicial | `07-socio-experiencias.impex`: tour do Maracanã (500), treino aberto no Ninho (800), manto autografado (1.500, Maraca 2), visita com foto com jogador (2.500, Maraca 1) e entrada em campo com o time (4.000, Maraca 1). |
| API de comércio | `GET /{baseSiteId}/users/current/sociopoints` (saldo, pedidos que deram pontos, resgates, vitrine com o que pode resgatar e por quê); `POST /users/current/sociopoints/redemptions` `{"experience":"TOUR-MARACANA"}` (voucher e saldo novo). Recusa: 400 `SocioPointsError`. |
| Backoffice | Sócio-Torcedor › **Experiências** (cadastro) e **Resgates de pontos**; na ficha do sócio, campo **Pontos** (saldo, ganhos, resgatados) e seção **Pontos e resgates** (regra, ganhos, vouchers). Papel do gestor: cria e edita experiências, cancela resgates. |
| Claude (MCP) | Torcedor: `meus_pontos` e `resgatar_experiencia` (com confirmação). |

Deploy (PR 8a): `ant all`; Update no HAC (tipos novos, só "Update running system" + "Localize types"); ImpEx do papel (`essentialdata-flamengomatchday-socio-role.impex`) e da vitrine (`07-socio-experiencias.impex`) importadas à parte; reiniciar antes de testar o Backoffice (rótulos dos tipos novos).

### Experiências à venda na loja (item 9 do plano de evolução) — servidor

As experiências da vitrine de pontos também são vendidas na loja, para qualquer torcedor, e continuam resgatáveis com pontos.

| Parte | Como funciona |
|---|---|
| Produto | Experiência com **Preço na loja** vira o produto `EXP-<código>` ao salvar (`SocioExperienceInterceptor` → `SocioExperienceService.publish`). Ele fica no catálogo Online, na categoria `experiencias` ("Experiências"), com preço em BRL e estoque, e a busca é reindexada. Sem preço ou inativa, o produto sai da loja (fica não aprovado). |
| Desconto de sócio | Não vale: a categoria `experiencias` está fora da regra de desconto da loja (`STORE_EXCLUDED`). |
| Vagas únicas | O estoque do produto é a quantidade da experiência menos os vouchers válidos, de resgates e de compras. O resgate com pontos, a compra e o cancelamento ajustam o mesmo estoque. |
| Compra | Ao fechar o pedido, cada unidade vira um voucher `NACAO-…` com origem "Compra", o pedido e o valor pago (`SocioExperiencePlaceOrderMethodHook`). O evento "Compra de experiência" entra na linha do tempo. Quem compra ganha pontos como em qualquer produto (1 por real). |
| Plano mínimo | Vale só para o resgate com pontos. Qualquer torcedor pode comprar. |
| Voucher | Preencher **Utilizado em** registra o uso (`usedAt`/`usedBy`) e o evento "Voucher utilizado"; um voucher usado não vale de novo. Preencher **Cancelado em** devolve a vaga (e, no resgate, os pontos) e registra o evento "Resgate cancelado". Numa compra, o reembolso é feito pelo atendimento. Tudo passa pelo `SocioRedemptionInterceptor` e pelo serviço (`useVoucher`, `cancelVoucher`), que o cockpit Gestão de experiências usa. |
| API | `GET …/sociopoints` traz, em cada experiência, `price`, `productCode` e `buyable`, e, em cada resgate, `orderCode`, `amount` e `usedAt`. |
| Chave | `flamengo.socio.experiencias.venda.ativo` (`false` = nada vai para a loja; o resgate continua). |

Dados: `07-socio-experiencias.impex` traz a coluna `price` (Tour R$ 120, Treino R$ 180, Foto com jogador R$ 450, Manto R$ 900, Entrada em campo R$ 1.200). Importar depois do Update.

#### Cockpit Gestão de experiências

Perspectiva própria (`expPerspective`, autoridade `experienceperspective`) para o papel **Gestor de experiências** (`experiencemanagerrole`, usuário `gestor.experiencias`, criado sem senha) e para o administrador. Papel e permissões ficam em `essentialdata-flamengomatchday-experience-role.impex`, importada à parte.

| Menu | Tela |
|---|---|
| Vitrine (abre primeiro) | Um card por experiência: pontos, preço, vagas livres, plano mínimo, resgates e vendas, prévia de como aparece na loja e situação na loja. **Nova experiência** e **Editar** abrem um formulário simples. Salvar passa pelo interceptor, que publica o produto (widget `expshowcase`). |
| Vouchers, resgates e compras | Campo para o voucher `NACAO-…`: mostra a experiência, o torcedor, a origem e a situação, e permite **Marcar como utilizado** ou **Cancelar voucher** (com motivo). Abaixo, a lista com filtro por experiência, situação, origem e texto; clicar numa linha abre o voucher (widget `expvouchers`). |
| Relatório | Resgates, vendas, receita, vouchers utilizados e ocupação das vagas, por experiência e pelos últimos 6 meses (widget `expreport`). |
| Experiências / Resgates (cadastro completo) | Lista e ficha padrão do Backoffice. |

No Cockpit de administração, o gestor de experiências vê só Experiências e Resgates, e o painel inicial dele é o relatório das experiências.

O mesmo Update cria os tipos do item 10 (`MatchdayTicket` e `MatchdayTicketEvent`, com os enums `MatchdayTicketStatus` e `MatchdayTicketEventType`), usados a partir do item 10 (abaixo).

#### Entrega das experiências

A regra de entrega fica em `FlamengoDeliveryModeLookupStrategy`, que substitui o `deliveryModeLookupStrategy` e herda do padrão.

| Carrinho | Modos de entrega |
|---|---|
| Só experiências com voucher (tour, treino, foto, entrada em campo) | Só **Ingresso digital** (`matchday-digital`, R$ 0). O voucher fica em Meus resgates. |
| Só experiências, com pelo menos uma enviada pelo correio | Só os modos com frete: padrão R$ 19,90 e expressa R$ 39,90. As experiências enviadas estão em `flamengo.socio.experiencias.comEnvio`, códigos separados por vírgula (padrão `MANTO-AUTOGRAFADO`). |
| Qualquer outro (ingresso, loja, misto) | Os modos de sempre. |

Chave: `flamengo.socio.experiencias.entrega.ativo` (`false` = os modos de sempre). Se a regra deixar o carrinho sem modo, valem os de sempre.

### Ingressos vendidos e ações do gestor de jogos (item 10 do plano de evolução)

Cada poltrona vendida vira um **ingresso** (`MatchdayTicket`), com código `<pedido>-<linha>`, o jogo, o setor, a poltrona, quem vai usar, o comprador, o valor pago e o desconto de sócio. O pedido não muda. As ações mexem no ingresso e no estoque das poltronas, e cada uma entra no histórico (`MatchdayTicketEvent`).

| Parte | Como funciona |
|---|---|
| Criação | Ao fechar o pedido (`MatchdayTicketPlaceOrderMethodHook`, depois da baixa do estoque e dos convidados), com o evento **Venda**. Os pedidos anteriores ganham ingresso com `matchdayTicketService.backfill()`, rodado uma vez pelo HAC. |
| Trocar poltrona | Só no mesmo jogo e setor, para uma poltrona livre (estoque disponível e sem reserva), sem diferença de preço. A poltrona antiga volta para a venda. Não vale depois do início do jogo. |
| Trocar quem vai usar | Um convidado ativo do comprador, outro nome digitado ou de volta o titular. |
| Cancelar | Reembolso integral até `flamengo.matchday.ingressos.reembolso.horasIntegral` horas antes do jogo (48); depois, `…percentualDepois` % (80) até o início. Depois do início não cancela. A poltrona volta para a venda. O valor fica no ingresso e o atendimento faz o estorno. |
| Marcar como utilizado | Entrada no estádio. Depois disso o ingresso não troca nem cancela. |
| Observação | Texto livre no histórico, em qualquer situação. |
| Pontos | Ingresso cancelado deixa de dar pontos ao sócio (`DefaultSocioPointsService`). |
| Relatório | Depois de uma troca, a venda aparece na poltrona nova, e não como venda simulada. |
| Chave | `flamengo.matchday.ingressos.ativo` (`false` = os ingressos continuam registrados e consultáveis, mas as ações ficam recusadas). |

**Onde o gestor de jogos usa:**
- **Backoffice:** perspectiva Matchday Flamengo › **Ingressos** (widget `matchdaytickets`). Tem a consulta pelo código, a ficha com histórico, as ações acima e a lista com filtros por jogo, setor, situação e texto. As permissões estão em `essentialdata-flamengomatchday-role.impex`.
- **Claude (MCP, persona gestor de jogos):** `ingressos`, `detalhe_ingresso` e `acao_ingresso`. A ação é um `MatchdayTicketEvent` novo, criado pela Integration API (objeto `FlamengoIngressoEvento`). O `MatchdayTicketEventInterceptor` executa esse evento com as mesmas regras e o grava como o registro do histórico. Os objetos de integração estão em `mcp/integracao-ingressos.impex`.

Depois do deploy:
- reset do `widgets.xml` e do `cockpit-config` no Backoffice;
- importar as ImpEx do papel e da integração;
- rodar o backfill.

### Retirada na loja física (item 12 do plano de evolução)

O torcedor compra na loja online e retira o pedido numa Loja Flamengo. O atendente da loja trabalha a remessa no cockpit padrão **Expedição de pedidos** (Order Fulfillment) do OMS.

| Parte | Como funciona |
|---|---|
| Lojas | Loja Flamengo Barra (`barrastore`), Loja Flamengo Copacabana (`copacabanastore`) e Loja Flamengo Maracanã (`maracanastore`), cada uma com endereço, coordenadas, horário (`flamengo-rj-store-hours`) e depósito próprio com o mesmo código. Os pontos de venda de exemplo (Tóquio, Reino Unido…) saíram da loja online, para não aparecerem na busca de lojas. |
| Estoque | 10 unidades de cada SKU físico em cada loja. Ingresso, plano, experiência, estacionamento, comida e personalização não têm estoque em loja, então não oferecem retirada. |
| Loja online | Na página do produto, a biblioteca de retirada do Spartacus oferece "Retirar grátis na loja" com a busca de lojas com estoque. No carrinho e no checkout, a linha fica com a loja escolhida, e o modo de entrega é "Retirada na loja". |
| Pedido | O passo `sourcePickup` do `order-process` cria, para as linhas com loja, a remessa (Consignment) daquela loja pelo `allocationService` do OMS e inicia o `consignment-process` padrão (caminho de retirada). Esse passo fica entre `takePayment` e `completeOrder`. A remessa reserva o estoque da loja. A baixa do depósito online (`commitSale`) pula essas linhas. As outras linhas (entrega, ingresso, plano, serviços) seguem como antes. O pedido fica Concluído e a remessa tem a sua própria situação. |
| Atendente | `atendente.barra`, `atendente.copacabana` e `atendente.maracana` estão nos grupos `warehouseagentgroup` e `warehouseagentrole` e no grupo de funcionários da loja (`<loja>storeemployees`), por isso cada um vê só as remessas da sua loja. Foram criados sem senha: o Emerson define no Backoffice. No cockpit, o atendente faz três coisas: **separar**, **embalar** (a remessa fica Pronta para retirada) e **confirmar a retirada** (Retirada concluída). |
| Torcedor | Em Meus pedidos, o detalhe do pedido mostra a remessa da loja como "Pronto para retirada" e, depois, "Retirada concluída". Não há e-mail. |
| Chave | `flamengo.retirada.ativo` (`false` = sem remessa; a linha baixa o estoque como as outras). |

Scripts (HAC, Groovy; primeiro `commit=false`):
- `scripts/lojas-retirada.groovy`:
  - etapa `lojas`: lojas, depósitos, grupos, atendentes e nome do modo de entrega, com backup em `/backup/lojas-retirada-antes-<data>.txt`;
  - etapa `estoque`: estoque das lojas.
- `scripts/order-process-retirada.groovy`: nova versão do `order-process` com o passo `sourcePickup`. A versão anterior vai para `/backup/order-process-v<n>-antes-retirada.xml`. Para voltar, ativar a versão anterior em Backoffice › Sistema › Processos de negócio › Definições de processo dinâmicas.

### Dados de demonstração da loja virtual (item 13 do plano, ato 7 do roteiro)

Script `scripts/loja-virtual-demo.groovy` (HAC). Tudo leva o prefixo `demo_` ou `FLADEMO` e fica no grupo de promoções da loja.

| Fluxo | O que existe | Situação |
|---|---|---|
| 7.1 Promoção | `demo_calcados_15`: 15% em Calçados (`flamengo_c132`) | Rascunho: publicar ao vivo no cockpit Merchandising Flamengo e despublicar depois |
| 7.2 Cupom | Cupom `FLADEMO20` (código único, 1 uso por torcedor, 100 no total, até 31/12/2026) e a regra `demo_cupom_flademo20` (R$ 20 de desconto acima de R$ 150) | Publicada. Só vale com o cupom, então os outros atos não mudam |
| 7.3 Regras | `demo_frete_gratis_299`: acima de R$ 299, a entrega vira o modo `frete-gratis` (R$ 0), que não aparece para escolha. `demo_brinde_manto`: Manto no carrinho ganha o chaveiro `flamengo_3911` | Rascunhos. O brinde também vale para a Camisa Jogo 2 da jornada de matchday, por isso precisa ser despublicado depois da demo |
| 7.4 Busca | `scripts/busca-adaptavel.groovy`: perfil `flamengoLoja` da Pesquisa adaptável e conjunto de ativação do índice da loja | Criados, sem regras. Funciona na loja: um produto fixado no perfil (Online) passou do 6º para o 1º lugar na busca "camisa" da API, na hora e sem reindexar. A fixação é feita ao vivo no Backoffice (perspectiva Pesquisa adaptável, como administrador) e removida depois |
| 7.6 Extras | `scripts/loja-virtual-extras.groovy`: a Bola Flamengo Oficial Assinada pelo Zico (`flamengo_3095`) fica sem estoque no site e nas 3 lojas, para o avise-me quando chegar. O atendente `atendente.asm` (grupo `asagentsalesgroup`, sem senha) é para o atendimento assistido: entrar na loja com `?asm=true`. A lista de desejos não precisa de dado | Aplicado. Para devolver o estoque: `ACAO = 'repor'` |

Testado com torcedor25 pela API:
- **Cupom:** R$ 329,99 → R$ 309,99.
- **As três regras em rascunho, publicadas para o teste e despublicadas depois:**
  - Calçados: chinelo de R$ 199,99 → R$ 170,00;
  - frete: R$ 19,90 → R$ 0;
  - brinde: chaveiro adicionado a R$ 0.
- **Depois de despublicar:** o mesmo carrinho voltou sem desconto, frete R$ 19,90.

Para publicar ou tirar do ar as regras em rascunho pelo HAC: `scripts/loja-virtual-demo-publicar.groovy` (variável `ACAO`).

### Cockpit Merchandising Flamengo (item 11 do plano de evolução)

Perspectiva nova no Backoffice para o time de marketing: promoções e cupons num lugar só, em português. Só configuração: as telas são as padrão do SAP Commerce (lista, busca e ficha, com o construtor de regras e a publicação no motor de regras).

| Parte | Como funciona |
|---|---|
| Menu | **Promoções**: Regras de promoção (`PromotionSourceRule`), Modelos de regra, Grupos de promoção, Grupos de regras. **Cupons**: código único, vários códigos, cupons do cliente e resgates de cupom. |
| Acesso | Autoridade `merchandisingperspective`: papel novo **Gestor de merchandising** (`merchandisingmanagerrole`, usuário `gestor.merchandising` criado sem senha) e o administrador. Permissões em `essentialdata-flamengomatchday-merchandising-role.impex`: cria e mantém promoções e cupons; leitura no resto. |
| Cuidado | As promoções `nacao_maraca_*` são geradas pelo cadastro de Planos (Sócio-Torcedor): o que for mudado nelas aqui volta ao republicar o plano. |

Deploy: `ant all`, reiniciar, importar a ImpEx do papel à parte, definir a senha do `gestor.merchandising` no Backoffice e, no Backoffice do admin, F4 › Reset to Defaults (widgets e configuração).

#### Painel inicial dos gestores no Cockpit de administração

O painel padrão do Cockpit de administração mostra gráficos do sistema (threads, CPU, memória). Para os gestores ele foi trocado pelo painel do assunto de cada um. O administrador continua com o painel do sistema.

| Papel | Painel |
|---|---|
| Gestor de merchandising | **Painel de merchandising** (widget `merchdashboard`, `MerchDashboardService`). Mostra regras publicadas (e quantas vencem em 7 dias), regras em rascunho, cupons ativos, usos de cupom e desconto concedido nos pedidos dos últimos 30 dias. Traz também a lista das regras publicadas por prioridade, os 5 cupons mais usados e os atalhos Nova regra, Novo cupom, Ver regras e Ver cupons. |
| Gestor de jogos | Relatório de vendas de ingressos (o mesmo da perspectiva Matchday Flamengo). |
| Gestor do sócio | Relatório do sócio-torcedor (o mesmo da perspectiva Sócio-Torcedor). |

- **Configuração.** Os widgets ficam no slot `dashboardWidgets` do `backoffice_dashboard` (`flamengomatchday-backoffice-widgets.xml`). Cada papel escolhe o seu no contexto `dashboard` com `principal` (`flamengomatchday-backoffice-config.xml`).
- **Regras consideradas.** Só as do grupo de promoção `flamengo.merch.painel.grupoPromocao` (padrão `electronicsPromoGrp`), na última versão de cada código.
- **Janelas.** `flamengo.merch.painel.dias` (padrão 30) e `flamengo.merch.painel.vencendoDias` (padrão 7).
- **Desconto.** Soma o desconto do pedido e o dos itens. Pedidos cancelados não contam.
- **Deploy.** `ant all`, reiniciar e, no Backoffice do admin, F4 › Reset to Defaults (widgets e configuração).

### Página de produto em português (30/10)

- `src/app/i18n/pt-produto.ts`: traz em português os textos da página de produto e da busca. O Spartacus só traz inglês; o que faltar continua em inglês.
- Abas (Detalhes do produto, Especificações, Avaliações, Entrega) alinhadas ao container de 1140px da página. Ver `tema-flamengo.scss`.
- O texto da aba Entrega fica no CMS: `deliveryTab` do `electronics-spaContentCatalog`, Staged e Online. O texto em pt/en foi gravado pelo HAC, e o lorem ipsum anterior está salvo em `/backup/antes-ondas-20261030/deliveryTab-*.html`.
- `src/app/i18n/pt-carrinho-checkout.ts`: carrinho, checkout, endereço, pagamento, confirmação do pedido, "Olá / Entrar / Cadastrar" e tema em português.
- `brl-price.interceptor.ts`: preços no formato R$ 229,99 com o site em português. A API manda R$229.99, porque o idioma `pt` do SAP Commerce não tem país.
- O rótulo "Language" vem de uma constante do Spartacus; com o site em pt, o CSS troca o texto por "Idioma:".
- Pelo HAC foram gravados em pt (Staged e Online) os nomes dos links do topo, do menu Minha conta e do rodapé, e o aviso de copyright.

### Loja em inglês (30/10)

Com o idioma em English, todas as telas próprias do Flamengo aparecem em inglês; com Português, em português.

- `src/app/i18n/fla-texts.ts`: textos pt/en da jornada de matchday, da página Sócio-Torcedor, do banner da home e da confirmação do pedido.
- `src/app/i18n/fla-i18n.service.ts`: idioma atual (acompanha o seletor do site), pipe `flaT`, preço (`R$ 1.234,56` / `R$1,234.56`) e datas (`09/11 10h00` / `Nov 9, 10:00 AM`).
- Ao trocar o idioma, jogos, planos e serviços são buscados de novo na API no novo idioma; jogo e poltrona escolhidos continuam.
- As datas das ondas são lidas da descrição do jogo nos dois formatos:
  - pt: `Onda 1 (...): 30/10/2026 08h09`
  - en: `Wave 1 (...): 2026-10-30 08:09`
- Extensão `flamengomatchday` (`DefaultMatchdayGameService`): ao abrir um jogo, grava também em inglês a descrição com as ondas, o resumo (`Rodada` vira `Round`) e o nome das poltronas com o setor em inglês. Precisa de `ant all` e reinício, como nas outras mudanças da extensão.
- Dados gravados pelo HAC (`scripts/ingles-dados.groovy`):
  - textos em inglês dos planos, convidados, estacionamentos, comidas, camisa e personalização;
  - descrição e resumo dos jogos em inglês;
  - nomes das poltronas em inglês;
  - categorias, menu e carrosséis da home em inglês.
- Backup: o valor anterior de cada campo está em `/backup/antes-ingles-20261030/data-en-antes.txt`. A classe Java anterior está na mesma pasta.
- Produtos da loja oficial (656 produtos e seus tamanhos), nome e descrição em inglês:
  - Traduções em `traducao/produtos-en.json`, feitas segundo `traducao/glossario.md`.
  - Os tamanhos aparecem no padrão em inglês: P → S, G → L, GG → XL…
  - Gravação: o JSON vai para `/tmp/fla-en.json` no container e depois roda `scripts/ingles-produtos.groovy` (HAC, Staged e Online).
  - Backup em `/backup/antes-ingles-20261030/produtos-en-antes.json`. Antes, o inglês era uma cópia do português.

### Busca e categoria em português (31/10)

- Loja (`src/app/i18n/pt-produto.ts`): "Ordenar por", filtros ("Filtrar por", "Ver mais/menos"), visualização em grade/lista e os textos de acessibilidade da lista de produtos.
- Dados gravados pelo HAC (só o `pt`; o `en` ficou igual), no tipo indexado `electronicsProductType`:
  - opções de ordenação (`SolrSort`): Relevância, Mais bem avaliados, Nome (A a Z), Nome (Z a A), Menor preço, Maior preço;
  - nomes dos filtros (`SolrIndexedProperty.displayName`): Categoria, Marca, Preço, Lojas, Promoção, Cor etc.
- Carrinho vazio: o parágrafo `EmptyCartParagraphComponent` ganhou o texto em `pt` (Staged e Online).
- Continua em inglês só o texto de acessibilidade da paginação ("first page", "page 2"), que é fixo no código do Spartacus.
