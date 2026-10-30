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
| Pagamento | o servidor confere a reserva (se venceu e a poltrona continua livre, reserva de novo; se outra pessoa pegou, recusa) e, antes de o processo do pedido começar, converte a reserva em venda e baixa o estoque de todos os itens |

Reservar exige login (ingresso é nominal): sem login a jornada pede para entrar e
volta para a mesma página. As reservas ativas aparecem no Backoffice como
**Reserva de poltrona** (`SeatHold`).

Peças da extensão (`backend/flamengomatchday`):

| Peça | Papel |
|---|---|
| `SeatHold` (items.xml) | poltrona, carrinho, torcedor e quando vence; uma reserva por poltrona (índice único) |
| `SeatHoldAddToCartMethodHook` | reserva ao adicionar; recusa sem login; desfaz no empate |
| `SeatHoldUpdateCartEntryHook` | libera ao tirar do carrinho |
| `SeatHoldCartValidationStrategy` | para o carrinho dono, a poltrona reservada continua válida (para os outros está esgotada) |
| `SeatHoldPlaceOrderMethodHook` | confere a reserva e converte em venda ao fechar o pedido |
| `SeatHoldExpiryJob` | libera reservas vencidas |

Configuração em `project.properties` (dá para sobrescrever no `local.properties`):
`flamengomatchday.seathold.minutes=15` (o front usa o mesmo valor em
`MATCHDAY_HOLD_MINUTES`), `flamengomatchday.seat.codeprefix=JOGO-`,
`flamengomatchday.warehouse=warehouse_s`.

Com a extensão, o pedido já sai com o estoque baixado (`statusInfo =
matchday:estoque-baixado`), então o passo `completeOrder` do `order-process` e o
`matchdayBaixaEstoqueCronJob` não baixam de novo; eles continuam como rede de
segurança.

### Deploy da extensão

No servidor (host do Docker), com o repositório atualizado na branch `matchday`:

```bash
cd <pasta do repositório>
git fetch origin && git checkout matchday && git pull
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

Os textos do widget (`backoffice/resources/widgets/matchdayreport/labels/*.properties`) ficam em **UTF-8 puro**, com acento direto, e não em `\u00e9`. O Backoffice lê esses arquivos sem converter os `\uXXXX` e mostraria o código na tela.

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
