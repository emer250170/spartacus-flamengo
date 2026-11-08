# MCP "Loja Flamengo" — SAP Commerce Cloud como ferramenta de IA

Um único arquivo (`flamengo-mcp.mjs`) que o Claude Desktop inicia sozinho. Quatro personas, cada uma com o usuário dela no SAP Commerce:

| Persona | Usuário no Commerce | API | O que pode |
|---|---|---|---|
| **Torcedor** | o torcedor (ex. torcedor1) | OCC (API da loja) | plano, jogos e preço de sócio, comprar ingressos, loja, carrinho, cupom, finalizar compra, pedidos, chamados, pedir cancelamento |
| **Gestor do sócio-torcedor** | `gestor.socio` | Integration API (OData) | planos (ler/alterar), sócios, torcedores, situação e elegibilidade (por que não conseguiu comprar), chamados, confirmar cancelamento, relatórios de cancelamento e adesão, cupons (ler/alterar) |
| **Gestor de jogos** | `gestor.jogos` | Integration API (OData) | jogos (ler/criar/alterar/encerrar), vendas de ingressos por jogo e setor, ingressos vendidos (consultar, trocar poltrona ou quem vai usar, cancelar com reembolso, marcar entrada, observação) |
| **Gestor da loja** | `gestor.loja` | Integration API (OData) + busca pública da loja | produtos (buscar, ficha com catálogo, preço e estoque por depósito), alterar preço e estoque, resumo de vendas por categoria, pedidos, promoções (só leitura), cupons (ler/alterar), experiências do sócio (vitrine, resgates, criar/alterar) |

Os três gestores também pedem ao Commerce **relatório por e-mail** (`enviar_relatorio`), cada um só com os dados da sua área. O **cupom pessoal enviado ao cliente** (`criar_cupom_cliente`, `cupons_enviados`) é só do gestor da loja (ver "Relatório por e-mail e cupom pessoal").

## Instalar (5 minutos)

1. A pasta `flamengo-mcp` fica em `C:\Users\I844870\Downloads\flamengo-mcp\` (já está aí).
2. Claude Desktop → **Configurações → Desenvolvedor → Editar configuração** (abre o `claude_desktop_config.json`).
3. Copie os blocos de `claude_desktop_config-trecho.json` para dentro de `"mcpServers"` (mantendo os servidores que já existem) e troque `COLOQUE_AQUI` pelas senhas:
   - torcedor1: a senha dos torcedores de teste;
   - `gestor.socio`, `gestor.jogos` e `gestor.loja`: as senhas que você definiu no Backoffice.
4. Feche o Claude Desktop por completo (ícone da bandeja → Sair) e abra de novo.
5. Em uma conversa, no botão **+ → Conectores**, aparecem `flamengo-torcedor`, `flamengo-gestor-socio`, `flamengo-gestor-jogos` e `flamengo-gestor-loja`. Ligue **só a persona da cena** da demo.

Teste rápido no terminal (opcional):
```
set FLAMENGO_PERSONA=jogos& set GESTOR_USUARIO=gestor.jogos& set GESTOR_SENHA=...& node C:\Users\I844870\Downloads\flamengo-mcp\flamengo-mcp.mjs --teste
```

## Na internet: Claude no celular e no claude.ai (07/11)

O mesmo arquivo roda como servidor HTTP (`--http`, transporte Streamable HTTP do MCP) no servidor do Commerce, atrás do Caddy, em `https://mcp-flamengo.democc.online`. Cada persona vira um **conector personalizado** da conta do Claude (plano Pro), que aparece também no app do celular. As chamadas vêm da nuvem da Anthropic, não do celular.

| Conector | Endereço | Token |
|---|---|---|
| Flamengo · Torcedor | `https://mcp-flamengo.democc.online/torcedor/mcp` | `MCP_TOKEN_TORCEDOR` |
| Flamengo · Gestor do sócio | `https://mcp-flamengo.democc.online/gestor-socio/mcp` | `MCP_TOKEN_SOCIO` |
| Flamengo · Gestor de jogos | `https://mcp-flamengo.democc.online/gestor-jogos/mcp` | `MCP_TOKEN_JOGOS` |
| Flamengo · Gestor da loja | `https://mcp-flamengo.democc.online/gestor-loja/mcp` | `MCP_TOKEN_LOJA` |

**Instalar no servidor (uma vez, quem tem acesso ao host):**

1. Pasta e arquivos:
   ```
   mkdir -p /opt/flamengo-mcp/logs && chown 1000:1000 /opt/flamengo-mcp/logs
   # copiar flamengo-mcp.mjs (gerado com npm run build) para /opt/flamengo-mcp/
   cp flamengo-mcp.env.exemplo /opt/flamengo-mcp/flamengo-mcp.env && chmod 600 /opt/flamengo-mcp/flamengo-mcp.env
   ```
2. Gerar os tokens (`openssl rand -hex 32`, um por linha `MCP_TOKEN_*`) e colocar as senhas de torcedor1, gestor.socio, gestor.jogos e gestor.loja no `flamengo-mcp.env`. As senhas ficam só nesse arquivo do servidor.
3. Subir o container (reinicia sozinho com o servidor):
   ```
   docker run -d --name flamengo-mcp --restart unless-stopped --network host -u 1000:1000 \
     --env-file /opt/flamengo-mcp/flamengo-mcp.env \
     -v /opt/flamengo-mcp/flamengo-mcp.mjs:/app/flamengo-mcp.mjs:ro -v /opt/flamengo-mcp/logs:/logs \
     node:20-alpine node /app/flamengo-mcp.mjs --http
   docker logs flamengo-mcp        # deve mostrar as rotas "ligada"
   curl -s http://127.0.0.1:8787/saude
   ```
   Ouve só em `127.0.0.1:8787`: de fora, só pelo Caddy.
4. Caddy: acrescentar o bloco de `Caddyfile-trecho` ao Caddyfile e recarregar (`systemctl reload caddy`, ou `docker exec <caddy> caddy reload --config /etc/caddy/Caddyfile` se o Caddy roda em container; nesse caso o `reverse_proxy` aponta para o endereço do host na rede do Docker, ex. `172.17.0.1:8787`). Conferir: `curl https://mcp-flamengo.democc.online/saude`.
5. Atualizar depois de um PR: trocar o `flamengo-mcp.mjs` e `docker restart flamengo-mcp`.
6. Persona nova (ex. Gestor da loja, 07/11): acrescentar `MCP_TOKEN_LOJA`, `LOJA_USUARIO` e `LOJA_SENHA` ao `flamengo-mcp.env`, trocar o `.mjs`, `docker restart flamengo-mcp` e incluir a rota no `@mcp` do Caddy (`/gestor-loja/*`, ver `Caddyfile-trecho`) antes de recarregar.

**Cadastrar no Claude (plano Pro):** claude.ai › Customize › Connectors › **+ Add** › **Add custom connector**: nome e endereço da persona; autenticação **No sign in**; em *Request headers*, `Authorization` = `Bearer <token da persona>`. Repetir para cada persona. No celular, os conectores aparecem na mesma conta; ligar só a persona da cena.
Se o cadastro não oferecer cabeçalho: `MCP_TOKEN_NA_URL=true` no `flamengo-mcp.env`, `docker restart flamengo-mcp` e endereço `https://mcp-flamengo.democc.online/<rota>/<token>/mcp` (o token aparece no log de acesso do Caddy; usar só se preciso).

**Proteções do modo internet:** token de 32+ caracteres por persona (o de uma não abre a outra), comparação em tempo constante, bloqueio de 10 min por IP depois de 20 tokens errados, corpo até 1 MB, sem sessão no servidor, tentativas negadas e chamadas na auditoria com o IP de origem (`/opt/flamengo-mcp/logs`). Continuam valendo o login próprio por persona no Commerce, a lista fechada de ações e o `confirmar=true`. Evite `entrar_como` pelo conector remoto (a senha passa pela conversa).

## Relatório por e-mail e cupom pessoal (08/11)

Relatório nas três personas de gestor (sócio, jogos e loja); cupom pessoal só na do gestor da loja (decisão de 08/11: jogos e sócio não criam cupom pelo Claude; no Commerce, `flamengo.mcp.cupom.areas=LOJA` recusa as outras áreas). Quem envia o e-mail é o **SAP Commerce** (SMTP configurado em `mail.*`, remetente da Loja Flamengo); o MCP só pede.

| Ferramenta | O que faz | Regras do Commerce |
|---|---|---|
| `enviar_relatorio` | O Claude monta o relatório com o que o gestor pedir (destaques, seções com texto e tabelas) a partir dos dados das outras ferramentas; o Commerce envia em HTML com as tabelas em CSV anexo | só para os e-mails de `flamengo.mcp.relatorio.destinatarios`; cada gestor só tem os dados da sua área |
| `criar_cupom_cliente` | Cupom de uso único, só na conta do cliente: % nos itens, R$ no pedido ou frete grátis, com escopo, compra mínima e validade; o Commerce cria a promoção, publica (até 2 min) e envia o código ao cliente | só o gestor da loja (`flamengo.mcp.cupom.areas`); escopo: produtos da loja, Mengo Estúdio, personalização, experiências, produtos ou categorias; teto 30% / R$ 100 e validade até 180 dias (propriedades `flamengo.mcp.cupom.*`) |
| `cupons_enviados` | Cupons que o gestor criou: situação (publicando, enviado, erro) e se o cliente já usou | — |

Objetos de integração em `docs/flamengo-matchday/mcp/integracao-relatorio-cupom.impex` (tipos `FlaReportMail` e `FlaCouponGrant` da extensão). No cupom da loja, o desconto acumula com o desconto de sócio na loja.

## Análise de pedidos (08/11)

Ferramenta `analise_pedidos` nas três personas de gestor: totais e agrupamentos (até 2 de uma vez) com filtros nos mesmos campos.

| Campo | De onde vem |
|---|---|
| forma de pagamento, parcelas, bandeira, gateway | método escolhido no checkout (sócio), método da Adyen, dados do cartão |
| entrega, cidade, UF | modo de entrega; cidade e UF do endereço (UF pela faixa do CEP quando falta); retirada = cidade da loja |
| promoção (com o desconto de cada uma), cupom, com/sem desconto | promoções que valeram no pedido (nome em português da regra) |
| antifraude | ClearSale: aprovado, recusado por suspeita de fraude, recusado por política, em análise, não analisado |
| situação, categoria, produto, perfil do cliente (plano atual), primeira compra ou recorrente, faixa de valor, dia, semana, mês, dia da semana, hora | pedido |

Totais: pedidos, receita, ticket médio, descontos, % com desconto, desconto por promoção, aguardando pagamento e o resumo do antifraude (% recusados dos analisados e por suspeita de fraude). Recorte por gestor: loja = todos os pedidos; jogos = pedidos com ingresso, estacionamento ou alimentação (receita = esses itens); sócio = pedidos com plano e compras de quem é sócio hoje. **Sem dados pessoais**: do endereço só cidade e UF.

No Commerce: objeto de integração `FlamengoAnalisePedido` com o atributo virtual `analise` (Script `flaAnalisePedido`, mesmo texto de `docs/flamengo-matchday/mcp/analise-pedido.groovy`), em `integracao-analise-pedidos.impex`. Não precisa de `ant all`; o objeto `FlamengoPedido` das outras ferramentas não mudou.

Perguntas de exemplo: "Receita por forma de pagamento nos últimos 30 dias", "Quantos pedidos tiveram os 5% no Pix e quanto demos de desconto?", "Qual o percentual de pedidos recusados por suspeita de fraude?", "Vendas por cidade e tipo de entrega", "Sócios ou não sócios: quem tem o maior ticket médio?", "Que dia da semana e hora vendem mais?".

## Segurança (bom para mostrar ao cliente)

- **Cada persona usa o próprio login do Commerce**; quem bloqueia é o Commerce, não o MCP: o gestor do sócio-torcedor recebe *"o Commerce negou"* se tentar ver jogos, e o gestor de jogos não enxerga planos nem chamados. O gestor da loja não vê sócios nem jogos (só os resgates de experiência e os pedidos). Permissões por tipo nos grupos `socioapigroup`, `jogosapigroup` e `lojaapigroup`; o `lojaapigroup` não lê catálogo nenhum, então o Backoffice do gestor.loja continua só com o catálogo do Flamengo.
- **Lista fechada de ações**: não existe ferramenta genérica de consulta ou de "executar qualquer coisa".
- **Dupla confirmação nas ações que alteram dados**: o Claude pede aprovação para usar a ferramenta *e* o servidor só executa com `confirmar=true` depois de mostrar o resumo ao usuário.
- **Auditoria**: cada chamada fica em `logs\auditoria-<persona>.jsonl` (quem, ferramenta, parâmetros, resultado), sem senhas.
- **Dados sensíveis**: cartões só com os 4 últimos dígitos; checkout usa cartão e endereço já salvos (o agente nunca vê número de cartão).
- **Certificado fixado**: o servidor de integração usa certificado autoassinado; o MCP só aceita aquele certificado específico (impressão digital SHA-256).
- As senhas ficam só no `claude_desktop_config.json` do seu notebook (no modo internet, só no `flamengo-mcp.env` do servidor).

## Roteiro sugerido da demo

**1. Torcedor (ligue só `flamengo-torcedor`)**
- "Quem sou eu e qual meu plano de sócio?"
- "Quais os próximos jogos no Maracanã? Quanto eu pago no setor Leste contra o Bahia com meu desconto?"
- "Coloca 2 ingressos no Leste pra esse jogo e um boné tamanho G" → o Claude mostra o resumo, você confirma → abra a loja e mostre o carrinho.
- "Aplica o cupom mengao. Quanto ficou?" (sócio 10% + cupom 15% nos produtos da loja)
- "Finaliza a compra" → resumo com endereço e cartão final 1111 → confirmar → pedido criado, aparece em Minha conta.
- "Quero cancelar meu plano, vou me mudar" → abre o chamado.
- "Quantos pontos eu tenho? O que dá para resgatar?" → saldo, de onde vieram (ingressos e compras desde a adesão) e a vitrine com o que ele pode resgatar ou quantos pontos faltam (`meus_pontos`). "Resgata o tour do Maracanã" → resumo → confirmar → voucher `NACAO-…`, que aparece na área do sócio e na ficha do Backoffice (`resgatar_experiencia`).

**2. Gestor do sócio-torcedor (ligue só `flamengo-gestor-socio`)**
- "Como estão os planos? Quantas vagas restam no Nação Maraca 1?"
- "Quantos cancelamentos tivemos este mês e por quê?"
- "Confirma o cancelamento que acabou de chegar" → o torcedor perde selo e descontos na hora (mostre a loja).
- "O torcedor21 está com a mensalidade de outubro atrasada, suspende os benefícios" → resumo → confirmar → o selo dele vira "Benefícios suspensos", o desconto some do carrinho e a jornada pede para regularizar (`suspender_socio`). Depois: "Ele pagou, regulariza" (`regularizar_socio`) e "Mostra a linha do tempo dele" (`situacao_socio`).
- "Por que o torcedor22 não conseguiu comprar ingresso para o Bahia?" → as tentativas dele, com a decisão, o motivo (ex.: "A venda geral deste jogo abre em 05/11…") e as condições conferidas (`elegibilidade_torcedor`). Sem e-mail: "Quem tentou comprar e não conseguiu hoje?"
- "Quem vai com o torcedor23 no jogo do Bahia?" → convidados nominais do sócio (CPF mascarado) e quem vai em cada poltrona dos pedidos (`convidados_torcedor`, objeto `FlamengoConvidado` + `ticketHolder` no `FlamengoPedido`). Sem e-mail: "Quais convidados têm ingresso para o Bahia?"
- "Quem ainda não aceitou a versão nova do Termo do Nação?" → versão atual, rascunhos, sócios que aceitaram (onde e quando) e os pendentes (`termo_aceites`, objeto `FlamengoTermo`). Com e-mail: histórico de aceites do torcedor. No `finalizar_compra` do torcedor, carrinho com plano mostra o Termo no resumo e o aceite é gravado ao confirmar.
- "Vire a vigência do torcedor24" → renova por 12 meses a partir de hoje com a mudança agendada, ou encerra sem multa se a renovação está desligada; grava Renovação ou Fim de vigência (`virar_vigencia`, com `confirmar=true`). A `situacao_socio` mostra a vigência (início, fim, renovação automática, mudança agendada; objeto `FlamengoContrato`).
- "O torcedor25 pagou o Pix? Confirma o pagamento" → `situacao_socio` mostra o pagamento pendente (Pix ou boleto do ano inteiro, com o pedido e o vencimento); `confirmar_pagamento` (com `confirmar=true`) grava Pagamento confirmado e o torcedor vira sócio na hora.
- "Aumenta o desconto na loja do Nação Maraca 2 para 12%" → o Commerce republica as promoções sozinho.
- "Quantas vezes o cupom mengao foi usado?"
- Mostre o bloqueio: "Cria um jogo contra o Vasco" → ele não tem essa ferramenta nem acesso.

**3. Gestor de jogos (ligue só `flamengo-gestor-jogos`)**
- "Quais jogos estão à venda e como está a ocupação?"
- "Cadastra Flamengo x Vasco, 29/11 às 16h, Brasileirão Rodada 37, Oeste a R$ 240" → o Commerce cria produto, 384 poltronas e as 4 ondas → abra a loja e mostre o jogo.
- "Baixa o Norte desse jogo para R$ 70" / "Quanto vendemos de ingresso no jogo do Bahia, por setor?"
- "Mostra os ingressos do torcedor24" / "Troca a poltrona do ingresso 00021000-0 para uma livre no mesmo setor" / "Cancela esse ingresso, o torcedor não pode ir" → `ingressos`, `detalhe_ingresso` (histórico, poltronas livres e reembolso se cancelar agora) e `acao_ingresso` (com `confirmar=true`). A ação é um evento novo no histórico (objeto `FlamengoIngressoEvento`) que o Commerce executa com as mesmas regras do Backoffice: troca só no mesmo setor, reembolso integral até 48 h antes e 80 % depois, nada depois do início do jogo. Objetos em `docs/flamengo-matchday/mcp/integracao-ingressos.impex`.

**4. Gestor da loja (ligue só `flamengo-gestor-loja`)**
- "Como foram as vendas nos últimos 30 dias? O que mais vendeu na loja?" → receita, pedidos, ticket médio, frete, descontos e a divisão por categoria (ingressos, sócio, experiências, estacionamento, alimentação, personalização, produtos), com as mesmas regras do painel de vendas do Backoffice (`resumo_vendas`).
- "Tem pedido aguardando pagamento?" / "Mostra o pedido 00021000" (`pedidos`, `detalhe_pedido`).
- "Quanto está o manto I e quanto tem em estoque?" → ficha com catálogo Staged/Online, preço e estoque por depósito; para roupa, as opções de tamanho com o código de cada uma (`buscar_produtos`, `detalhe_produto`).
- "Baixa o manto para R$ 299,90" / "Entraram 20 unidades do tamanho G" → resumo → confirmar → a loja mostra o preço e o estoque novos em instantes (`alterar_preco`, `alterar_estoque`). Preço de ingresso, plano ou experiência é recusado e o Claude indica quem cuida.
- "Quais promoções estão valendo?" (`promocoes`, só leitura: ativar ou criar regra é no Backoffice, que republica o motor de promoções) / "Desativa o cupom mengao" (`atualizar_cupom`).
- "Como está a vitrine de experiências? Quem resgatou o tour?" / "Cria a experiência Visita ao museu por 300 pontos ou R$ 60, 20 vagas" → o Commerce cria o produto `EXP-VISITA-AO-MUSEU` na loja (`experiencias`, `resgates_experiencias`, `criar_experiencia`, `atualizar_experiencia`). Objetos em `docs/flamengo-matchday/mcp/integracao-loja.impex`.
- Mostre o bloqueio: "Suspende o torcedor21" → ele não tem essa ferramenta nem acesso.

**4b. Análise de pedidos (qualquer gestor)**
- Gestor da loja: "Quanto vendemos por forma de pagamento nos últimos 30 dias? E quantos pedidos tiveram os 5% no Pix?" (`analise_pedidos`).
- "Qual o percentual de pedidos recusados por suspeita de fraude?" → resumo do antifraude da ClearSale.
- "Me manda isso por e-mail" → o relatório sai com esses números (`enviar_relatorio`).

**5. Relatório (qualquer gestor) e cupom (gestor da loja)**
- "Me manda por e-mail o resumo de vendas de outubro, só ingressos e experiências, com os 5 mais vendidos" → o Claude puxa os dados, mostra o que vai no e-mail, você confirma e o relatório chega no seu e-mail (com CSV) (`enviar_relatorio`).
- Gestor da loja: "Manda um cupom de 15% no Mengo Estúdio para o emerson.oliveira@sap.com, válido até o fim do mês, com uma mensagem de agradecimento" → resumo → confirmar → em até 2 minutos o cliente recebe o e-mail com o código; aplique no carrinho e mostre o desconto (`criar_cupom_cliente`, `cupons_enviados`).
- Gestor de jogos ou do sócio pedindo cupom: o Claude responde que cupom pessoal é com o gestor da loja (não tem a ferramenta).

Dicas: antes da demo, deixe o carrinho do torcedor vazio; para o cancelamento, use um torcedor sócio que você possa reativar (ou recompre o plano depois pela loja).
