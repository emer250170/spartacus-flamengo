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
| `MatchdayJourneyComponent` | página `/matchday` | stepper de 6 etapas com resumo lateral; o mapa lê as poltronas reais do jogo (livre = com estoque); no fim põe tudo no carrinho e segue para o checkout padrão |
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
