# CLAUDE.md — Demo Flamengo (SAP Commerce Cloud + Composable Storefront)

Demo do Emerson (SSE SAP Brasil) para o Clube de Regatas do Flamengo: jornada de
matchday (ingresso com poltrona + estacionamento + comida + camisa num só
carrinho), programa sócio-torcedor Nação Maraca e MCP para LLMs. Sempre
responder e escrever em **português**.

## Regras de trabalho (não negociáveis)

- **Nunca** commitar/dar push direto na `flamengo` (branch principal). Sempre
  branch nova + PR. A API GraphQL do GitHub costuma estar bloqueada: criar PR por
  REST, `gh api repos/emer250170/spartacus-flamengo/pulls -f title=... -f head=... -f base=flamengo -f body=...`.
- O Emerson aprova o PR, **publica a loja** (front) e roda o **`ant all`** (back).
- Não quebrar o que funciona: conferir antes, backup antes de gravar no servidor
  (`/backup/...`), testar depois na loja/Backoffice.
- Senhas: nunca gravar em arquivo, commit ou memória. Pedir ao Emerson quando
  precisar; login no HAC/Backoffice quem faz é ele (no navegador do Playwright).
- Testes com escrita (carrinho, pedido, chamado, plano) só em **usuários de
  teste** (`torcedorN@example.com`), nunca em dados que o Emerson usa na demo
  (ex.: o carrinho do torcedor1). Registrar o que sobrou de teste.
- Ao terminar uma mudança no servidor, verificar na loja/Backoffice e contar o
  resultado com evidência.

## Ambientes

| O quê | Onde |
|---|---|
| Loja (Spartacus) | https://spartacus-flamengo.democc.online/electronics-spa/pt/BRL |
| OCC | https://api-flamengo.democc.online/occ/v2/electronics-spa (sempre `lang=pt&curr=BRL`) |
| OAuth (OCC) | https://api-flamengo.democc.online/authorizationserver/oauth/token, cliente `mobile_android` / `secret` |
| HAC | https://187.127.62.169:9105 (certificado autoassinado) |
| Backoffice | https://187.127.62.169:9105/backoffice |
| Integration API (OData) | https://187.127.62.169:9105/odata2webservices |
| Servidor | container Docker, Commerce em `/opt/sap/hybris`, extensão em `/opt/sap/hybris/bin/custom/flamengomatchday` |

Site `electronics-spa`, catálogo de produtos `flamengoProductCatalog`, conteúdo
`electronics-spaContentCatalog` (Staged e Online), moeda BRL, depósito `warehouse_s`.

## Estrutura do repositório (o que é nosso)

| Pasta | Conteúdo |
|---|---|
| `projects/storefrontapp/src/app/matchday/` | jornada de matchday, banner da home, confirmação do pedido (CMSFlexComponent por `flexType`) |
| `projects/storefrontapp/src/app/socio/` | planos, selo, área "Meu sócio-torcedor" (`/my-account/socio-torcedor`), `SocioNavigationService` (esconde o menu de quem nunca foi sócio) |
| `projects/storefrontapp/src/app/i18n/` | `fla-texts.ts` (textos pt/en das telas próprias, via `FlaI18nService`/pipe) e `pt-*.ts` (traduções que sobrescrevem as do Spartacus) |
| `projects/storefrontapp/src/app/pickup/` | retirada em loja |
| `projects/storefrontapp/src/styles/tema-flamengo.scss` | tema (vermelho/preto) |
| `backend/flamengomatchday/` | extensão Commerce: reserva de poltrona, jogos, sócio, relatórios e perspectivas do Backoffice |
| `backend/hac-deploy/` | gerador do deploy da extensão pelo HAC (ver abaixo) |
| `docs/flamengo-matchday/` | README funcional completo, ImpEx e scripts Groovy do ambiente |
| `mcp/` | servidor MCP "Loja Flamengo" (Claude Desktop): personas torcedor / gestor do sócio / gestor de jogos |

O resto é o Spartacus padrão: não mexer sem necessidade.

## Front (Spartacus)

- Textos novos: sempre em `fla-texts.ts`, nas duas línguas (pt e en).
- Textos padrão do Spartacus em inglês: sobrescrever em `i18n/pt-*.ts`.
- Textos de conteúdo CMS (parágrafos, banners) em inglês: não é código; gravar o
  conteúdo `pt` do componente no catálogo de conteúdo, **Staged e Online**,
  sem tocar no `en` (Groovy no HAC).
- Armadilhas já vistas:
  - o tema pinta qualquer `<header>` de vermelho (`html header {... !important}`): usar `div`;
  - o CSS global do Spartacus posiciona `input[type=radio]` como `absolute`: resetar
    (`position: static; appearance: auto`);
  - o slot `SiteLogin` no desktop fica em coluna para o mini-carrinho caber na linha do logo.
- Para mudar menu de navegação: estender `NavigationService` e registrar com `useExisting`.
- Conferir tipos antes do PR (o build completo é pesado; o Emerson publica).

## Backend (extensão `flamengomatchday`)

- Pacote `br.com.flamengo.matchday` (reserva de poltrona `SeatHold*`, `game`,
  `socio`, `report`, `interceptor`, `job`). Spring em `resources/flamengomatchday-spring.xml`.
- Interceptors sem dependência de compilação de outras extensões: mapear por
  `typeCode` e ler atributos com `modelService.getAttributeValue`.
- Salvar outro item dentro de interceptor: `ctx.registerElementFor(item, PersistenceOperation.SAVE)`.
- Código de jogo `JOGO-AAAAMMDD-ADV`; poltronas com o código do jogo como prefixo.
- Cancelamento de plano: chamado (`CsTicket`) com headline "Cancelamento do plano…";
  ao fechar o chamado, `SocioCancelTicketInterceptor` tira o torcedor dos grupos do plano.

### Deploy do backend (sem acesso ao servidor)

O código vai para o servidor **pelo HAC**; o Emerson só para, roda `ant all` e sobe.

```bash
# 1. conferir que o servidor está na versão BASE (normalmente origin/flamengo antes do merge)
python3 backend/hac-deploy/gerar.py --base origin/flamengo --head <branch-do-PR> --rotulo prNN --modo conferir --saida /tmp/conf.groovy
# 2. aplicar: confere md5, faz backup em /backup/flamengomatchday-antes-prNN-<data>/fontes.tgz,
#    grava os arquivos, confere md5 final
python3 backend/hac-deploy/gerar.py --base origin/flamengo --head <branch-do-PR> --rotulo prNN --modo aplicar --saida /tmp/aplicar.groovy
# auditoria a qualquer momento (servidor == repositório?)
python3 backend/hac-deploy/gerar.py --auditar origin/flamengo --saida /tmp/auditoria.groovy
```

Depois do `ant all`: se mudou `items.xml` ou ImpEx de `essentialdata`, fazer o
Update no HAC (Platform › Update). Verificar no Backoffice/loja.

### Rodar Groovy no HAC a partir de uma sessão

Com o HAC aberto e logado no navegador do Playwright do notebook do Emerson
(`browser_evaluate` na página do HAC):

```js
async () => {
  const csrf = document.querySelector('meta[name="_csrf"]').content;
  const r = await fetch('/console/scripting/execute', { method: 'POST',
    headers: { 'X-CSRF-TOKEN': csrf, 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
    body: new URLSearchParams({ script: SCRIPT, scriptType: 'groovy', commit: 'false' }) });
  const j = await r.json(); return j.executionResult + (j.stacktraceText || '');
}
```

- `commit: 'false'` = teste (transação desfeita); `'true'` grava. Escrita em disco
  (deploy) acontece nos dois modos.
- Sempre rodar primeiro em modo de leitura/teste e mostrar o que vai mudar.
- Groovy do HAC: `Date.format(...)` não existe, usar `java.text.SimpleDateFormat`.
- ImpEx com `$` (macros, `$START_USERRIGHTS`): passar o texto em base64 e importar
  com `ImportConfig` + `importService.importData`, para evitar escape.
- Texto com acento dentro do Groovy: usar `á` etc. quando montado por código.

## Integration API (OData) e MCP

- Cada objeto de integração precisa de `InboundChannelConfiguration` com
  autenticação BASIC; sem ela a resposta é 403 mesmo para administrador.
- `integrationservices.authorization.accessrights.enabled=true`: permissão por tipo.
  Dar as permissões em **UserGroups** (`socioapigroup`, `jogosapigroup`), não em
  BackofficeRole (só valem com o papel ativo no Backoffice).
- `gestor.socio` ∈ sociomanagerrole + socioapigroup; `gestor.jogos` ∈ matchdaygamemanagerrole + jogosapigroup.
- Chave do Order = `code|versionID`; datas `/Date(ms)/`; `Accept-Language: pt`.
- Configuração no servidor: `docs/flamengo-matchday/mcp/integracao-gestores.impex`.
- MCP: código em `mcp/src` (`core.js` sem dependências + `server.mjs`), empacotado
  com esbuild num único `flamengo-mcp.mjs`. Escrita exige `confirmar=true`;
  certificado do servidor fixado por SHA-256. Detalhes em `mcp/README.md`.

## Navegador (Playwright no notebook)

- O HAC mostra "Sua conexão não é particular": o Emerson clica em Avançadas › Ir para… e faz o login.
- Se as ferramentas do Playwright sumirem da sessão, pedir ao Emerson para
  desvincular e vincular de novo o computador à conversa.
