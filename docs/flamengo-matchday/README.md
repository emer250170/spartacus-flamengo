# Matchday Flamengo — jornada única de dia de jogo

Protótipo da jornada "Monte o seu dia de jogo": ingresso com setor e poltrona,
estacionamento, comidas e bebidas e a Camisa Jogo 2 personalizada num só
carrinho e num só pagamento, no SAP Commerce Cloud com Composable Storefront.

## Front (este repositório)

Código em `projects/storefrontapp/src/app/matchday/`, importado no `AppModule`.
Três componentes ligados ao CMS por `CMSFlexComponent` (`flexType`):

| flexType | Onde aparece | O que faz |
|---|---|---|
| `MatchdayJourneyComponent` | página `/matchday` | stepper de 6 etapas com resumo lateral; no fim põe tudo no carrinho ativo e segue para o checkout padrão |
| `MatchdayHomeBannerComponent` | topo da home | faixa "Próximo jogo em casa" com acesso à jornada |
| `MatchdayOrderConfirmationComponent` | confirmação do pedido | cartões de ingresso (QR ilustrativo), vaga, comida e camisa |

Os dados (jogos, setores, preços, estoque) vêm das APIs OCC da loja. Poltrona,
placa, forma de entrega da comida e nome/número da camisa não têm campo na
linha de carrinho padrão: no protótipo ficam no `localStorage` do navegador e
aparecem na confirmação. Em projeto, viram atributos da entrada do carrinho
(extensão OCC) e integração com o sistema de ingressos.

## Backend (HAC › Console › ImpEx Import, nesta ordem)

1. `01-jogos.impex` — 3 jogos × 4 setores (variantes), Staged e Online
2. `02-servicos-e-camisa.impex` — estacionamento, comidas, Camisa Jogo 2, personalização
3. `03-cms.impex` — página `/matchday`, banner da home, bloco da confirmação, item "Ingressos" no menu
4. Rodar o `full-electronicsIndex-cronJob` para reindexar a busca

Loja: site `electronics-spa`, catálogo `flamengoProductCatalog`, BRL, estoque no `warehouse_s`.
