# CLAUDE.md — Demo Flamengo (SAP Commerce Cloud + Composable Storefront)

Demo do Emerson (SSE SAP Brasil) para o Clube de Regatas do Flamengo: jornada de
matchday (ingresso com poltrona + estacionamento + comida + camisa num só
carrinho), programa sócio-torcedor Nação Maraca e MCP para LLMs. Sempre
responder e escrever em **português**.

## Plano de evolução do sócio-torcedor

O que falta cobrir do programa Nação (a partir da apresentação de arquitetura de
negócios) está no Claude Doc **Plano de evolução da demo — Nação Sócio-Torcedor**:
https://claude.ai/artifact/DrAbpraEa5gqLCFZMemBVZ (ler e editar com as ferramentas
do Claude Docs).

- Itens feitos **um por vez**, na ordem do plano; antes de começar ou continuar um,
  ler o item e a tabela "Acompanhamento" (status, PR e decisões já tomadas).
- Ao fim de cada etapa (desenho, código, PR, instalação, teste), atualizar a tabela:
  Status, PR e a coluna **Decisões** (data + uma linha do que foi decidido).
- Roteiro de demo (Claude Doc **Roteiro de demonstração**):
  https://claude.ai/artifact/9fzhPtXPxJPnp4FoU9qB9N. Cada funcionalidade nova entra
  nele **só depois que o Emerson testar e aprovar**, no ato da jornada
  correspondente (onde clicar, usuário, o que mostrar, frase para o cliente); anotar
  na coluna Roteiro do Acompanhamento onde entrou. Nunca gravar senha nova no roteiro.
- Cautelas do plano: modelo de dados só cresce (nada renomeado/removido no
  `items.xml`), regra nova com chave liga/desliga por propriedade, comportamento atual
  como padrão quando não há dado novo.

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
| `projects/storefrontapp/src/app/visual/` | visual da loja oficial: header em três faixas (links e menu do portal, botão Ingressos), rodapé escuro em 4 colunas, logo sem tooltip e carrinho em box lateral à direita (`FlaCartDrawerComponent`, no `ADDED_TO_CART` com `SIDEBAR_END`; `FlaMiniCartComponent` abre o box); disposição no tema |
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
- **Alinhamento do header (regra):** no desktop o header não tem recuo; logo e carrinho ficam nas bordas
  do container (como no site oficial: tela menos 64px de cada lado a partir de 992px, até 1472px; 16px abaixo de 992px; variável `--cx-page-width-max`, calculada no tema), as mesmas do conteúdo (banner da home, fotos da listagem, produto, carrinho). Toda
  mudança de visual: medir na loja (logo × borda do conteúdo, carrinho × borda direita) em 1024, 1280, 1440 e
  1920px e nas páginas home, listagem, produto, carrinho, matchday, sócio e minha conta. Antes de dizer que
  algo está desalinhado, conferir se a loja publicada já tem o PR (o CSS no ar pode ser o antigo).
- **Celular (regra):** todo layout novo contempla o celular; conferir também em ~390px (sem rolagem lateral).
- Funcionalidade que a tela atual tem e o mockup aprovado não contempla: avisar o Emerson antes (retirar ou
  incluir no mockup).
- Para mudar menu de navegação: estender `NavigationService` e registrar com `useExisting`.
- Conferir tipos antes do PR (o build completo é pesado; o Emerson publica).

## Backend (extensão `flamengomatchday`)

- Pacote `br.com.flamengo.matchday` (reserva de poltrona `SeatHold*`, `game`,
  `socio`, `report`, `interceptor`, `job`). Spring em `resources/flamengomatchday-spring.xml`.
- Interceptors sem dependência de compilação de outras extensões: mapear por
  `typeCode` e ler atributos com `modelService.getAttributeValue`.
- Salvar outro item dentro de interceptor: `ctx.registerElementFor(item, PersistenceOperation.SAVE)`.
- Papéis do Backoffice: todo `BackofficeRole` alcançável pelos grupos do usuário vira um papel na troca de papel, e só os
  cockpits e as permissões do papel **ativo** valem. Para um usuário ver vários cockpits sem trocar de papel, use um papel só com
  as autoridades de todos (liberam o acesso às perspectivas) e as permissões copiadas (exemplo: `lojamanagerrole`). As **telas**
  dos cockpits padrão (contextos com `principal="customersupport..."` etc.) são comparadas com o papel ativo, não com as
  autoridades: copiar esses contextos com o principal do papel novo (PR #246; sem isso o menu do Suporte ao cliente fica vazio).
  Papel novo também precisa de leitura em `EnumerationValue` e nos tipos referenciados pelas telas, senão os campos mostram
  "Nenhum acesso de leitura".
- Usuário de Backoffice da demo **não** entra no `employeegroup`: esse grupo lê todos os catálogos de produtos do servidor
  (powertools, apparel, electronics) e a lista de produtos do Backoffice (Solr) segue os catálogos legíveis, não as restrições
  de busca. A leitura do `flamengoProductCatalog` vem do papel (`readPrincipals`); restrições de busca próprias do papel
  substituem as do `employeegroup` (PR #246).
- Assistente (`wz:flow`) com vários `<wz:custom-view>`/`<wz:property-list>` no mesmo passo: dar `id` único a cada um. Sem `id`, o Backoffice junta os elementos na mesclagem da configuração (blocos repetidos e campos fora do lugar; PR #298). Duas `property-list` com o mesmo `root` no mesmo passo são juntadas mesmo com `id`: nesse caso usar `<wz:property qualifier="newProduct.x">` avulso (PR #299). Mudou `*-backoffice-config.xml`: depois do `ant all`, como admin, F4 › menu › segundo "Reset to Defaults" (Reset UI Configuration), senão a configuração gravada antes continua valendo.
- Rótulos de widget do Backoffice (`backoffice/resources/widgets/*/labels/*.properties`): nunca usar `\n` nos valores. O Backoffice transforma em quebra real e, se a linha seguinte tiver `{`, o leitor do ZK falha e o login inteiro dá erro 500 (PR #63).
- Endpoints próprios na API de comércio (OCC v2): controller Spring em `br.com.flamengo.matchday.occ` e bean em
  `resources/occ/v2/flamengomatchdayocc/web/spring/flamengomatchday-web-spring.xml` (a `commercewebservices`
  importa `classpath*:/occ/v2/*occ/web/spring/*-web-spring.xml`; a pasta tem que terminar em `occ`). Sem
  dependência de compilação da commercewebservices: só Spring Web/Security (estão em `platform/ext/core/lib`).
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

Antes do PR, conferir a compilação das classes novas ou alteradas no próprio servidor (não grava nada):
`python3 backend/hac-deploy/compilar.py <arquivos .java> --saida /tmp/compilar.groovy` e rodar pelo
`<input type=file>` no HAC. Classes do Backoffice (`backoffice/src`): `--backoffice` (põe o ZK do Backoffice na frente; sem
isso aparecem centenas de erros de ZK antigo), junto com as interfaces novas do núcleo que elas usam. Compila contra o que está instalado: classe que usa modelo de tipo novo só compila
depois do `ant all` (ler e gravar esses atributos pelo nome, como em `br.com.flamengo.matchday.mcp`).

- Update só com "Update running system" + "Localize types" (sem "Create essential data",
  sem dados de projeto e sem patches opcionais): nada da demo é reimportado. A ImpEx
  nova do PR é importada à parte (base64 + `ImportConfig`).
- **Tipo novo**: o Backoffice guarda o tipo no cache antes de os nomes serem
  localizados e mostra os campos crus ("SocioEligibilityDecision.date") até o próximo
  reinício. Depois do Update, reiniciar o servidor (sem `ant all`) antes de testar
  o Backoffice (aconteceu no PR #66).
- O Backoffice do Emerson é usado em **português**: conferir rótulos e telas em pt.

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
- Texto com acento dentro do Groovy montado por código: usar escapes Unicode (`\u00e1` para á, `\u00e7` para ç).
- Script grande (dezenas de KB, ex.: checagem de compilação): gravar o `.groovy` no notebook
  (`C:\Users\I844870\Downloads\flamengo-hac\`, um nome novo a cada versão), criar um `<input type=file>`
  na página do HAC e usar `page.setInputFiles` no `browser_run_code_unsafe` para ler o texto e postar no
  `/console/scripting/execute`.

## Integration API (OData) e MCP

- Cada objeto de integração precisa de `InboundChannelConfiguration` com
  autenticação BASIC; sem ela a resposta é 403 mesmo para administrador.
- `integrationservices.authorization.accessrights.enabled=true`: permissão por tipo. Nos grupos de API use só `+`
  ou vazio, nunca `-`: a negação vence a permissão do papel e trava o Backoffice do gestor (PR do 08/11).
  Dar as permissões em **UserGroups** (`socioapigroup`, `jogosapigroup`), não em
  BackofficeRole (só valem com o papel ativo no Backoffice).
- `gestor.socio` ∈ sociomanagerrole + socioapigroup; `gestor.jogos` ∈ matchdaygamemanagerrole + jogosapigroup;
  `gestor.loja` ∈ lojamanagerrole + lojaapigroup (grupos de integração não leem catálogo: o Backoffice dele segue só Flamengo).
- Chave do Order = `code|versionID`; datas `/Date(ms)/`; `Accept-Language: pt`.
- Configuração no servidor: `docs/flamengo-matchday/mcp/integracao-gestores.impex`, `integracao-ingressos.impex` e
  `integracao-loja.impex` (persona gestor da loja).
- Relatório por e-mail e cupom pessoal dos gestores (PR do 08/11): tipos `FlaReportMail` e `FlaCouponGrant`, criados pela
  OData; interceptors em `br.com.flamengo.matchday.mcp` conferem a área de quem pediu pelos grupos de API (lojaapigroup,
  jogosapigroup, socioapigroup) e, numa linha de execução depois do commit, enviam o e-mail pelo `emailService` (SMTP de
  `mail.*`) e, no cupom, criam SingleCodeCoupon + promoção só para o cliente e publicam. Destinatários de relatório só os de
  `flamengo.mcp.relatorio.destinatarios`; tetos em `flamengo.mcp.cupom.*`. ImpEx: `integracao-relatorio-cupom.impex`.
  Cupom pessoal só do gestor da loja (`flamengo.mcp.cupom.areas=LOJA`; as personas de jogos e sócio não têm a ferramenta).
  A importação de ImpEx numa linha de execução própria fecha a sessão (usuário volta a anônimo): chamar
  `McpSupport.prepareSession()` depois de cada importação antes de buscar produto ou categoria.
- Análise de pedidos (MCP 1.4.0): objeto `FlamengoAnalisePedido` com atributo **virtual** `analise` (Integration API 2211:
  `IntegrationObjectVirtualAttributeDescriptor` + Script `model://flaAnalisePedido`, variável `itemModel`), um JSON com
  pagamento, entrega, cidade/UF, promoções, antifraude e plano atual. Dado novo para a API sem `ant all`: preferir atributo
  virtual em objeto próprio (não pesar o `FlamengoPedido`). Mudou o Groovy: reimportar `integracao-analise-pedidos.impex`
  (o Commerce cria uma versão nova do Script e desativa a anterior).
- Promoção por código (ImpEx de PromotionSourceRule): no `y_target_customers` o cliente vai pela **PK** (o motor compara
  `UserRAO.pk`), não pelo e-mail; categorias e produtos vão pelo código. Interceptor de **tipo novo** só passa a valer
  depois do reinício que vem após o Update (na subida do `ant all` o tipo ainda não existia).
- MCP: código em `mcp/src` (`core.js` sem dependências + `server.mjs`), empacotado
  com esbuild num único `flamengo-mcp.mjs`. Escrita exige `confirmar=true`;
  certificado do servidor fixado por SHA-256. Detalhes em `mcp/README.md`.
- MCP na internet (conectores do claude.ai/celular): `--http`, container `flamengo-mcp` no host do Commerce
  (`/opt/flamengo-mcp`, porta 127.0.0.1:8787) atrás do Caddy em `https://mcp-flamengo.democc.online/<persona>/mcp`;
  token por persona no `flamengo-mcp.env` do servidor (nunca no repositório). Atualizar = trocar o `.mjs` e `docker restart`.

## Navegador (Playwright no notebook)

- O HAC mostra "Sua conexão não é particular": o Emerson clica em Avançadas › Ir para… e faz o login.
- Se as ferramentas do Playwright sumirem da sessão, pedir ao Emerson para
  desvincular e vincular de novo o computador à conversa.
