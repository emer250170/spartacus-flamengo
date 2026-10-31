# MCP "Loja Flamengo" — SAP Commerce Cloud como ferramenta de IA

Um único arquivo (`flamengo-mcp.mjs`) que o Claude Desktop inicia sozinho. Três personas, cada uma com o usuário dela no SAP Commerce:

| Persona | Usuário no Commerce | API | O que pode |
|---|---|---|---|
| **Torcedor** | o torcedor (ex. torcedor1) | OCC (API da loja) | plano, jogos e preço de sócio, comprar ingressos, loja, carrinho, cupom, finalizar compra, pedidos, chamados, pedir cancelamento |
| **Gestor do sócio-torcedor** | `gestor.socio` | Integration API (OData) | planos (ler/alterar), sócios, torcedores, chamados, confirmar cancelamento, relatórios de cancelamento e adesão, cupons (ler/alterar) |
| **Gestor de jogos** | `gestor.jogos` | Integration API (OData) | jogos (ler/criar/alterar/encerrar), vendas de ingressos por jogo e setor |

## Instalar (5 minutos)

1. A pasta `flamengo-mcp` fica em `C:\Users\I844870\Downloads\flamengo-mcp\` (já está aí).
2. Claude Desktop → **Configurações → Desenvolvedor → Editar configuração** (abre o `claude_desktop_config.json`).
3. Copie os três blocos de `claude_desktop_config-trecho.json` para dentro de `"mcpServers"` (mantendo os servidores que já existem) e troque `COLOQUE_AQUI` pelas senhas:
   - torcedor1: a senha dos torcedores de teste;
   - `gestor.socio` e `gestor.jogos`: as senhas que você definiu no Backoffice.
4. Feche o Claude Desktop por completo (ícone da bandeja → Sair) e abra de novo.
5. Em uma conversa, no botão **+ → Conectores**, aparecem `flamengo-torcedor`, `flamengo-gestor-socio` e `flamengo-gestor-jogos`. Ligue **só a persona da cena** da demo.

Teste rápido no terminal (opcional):
```
set FLAMENGO_PERSONA=jogos& set GESTOR_USUARIO=gestor.jogos& set GESTOR_SENHA=...& node C:\Users\I844870\Downloads\flamengo-mcp\flamengo-mcp.mjs --teste
```

## Segurança (bom para mostrar ao cliente)

- **Cada persona usa o próprio login do Commerce**; quem bloqueia é o Commerce, não o MCP: o gestor do sócio-torcedor recebe *"o Commerce negou"* se tentar ver jogos, e o gestor de jogos não enxerga planos nem chamados. Permissões por tipo nos grupos `socioapigroup` e `jogosapigroup`.
- **Lista fechada de ações**: não existe ferramenta genérica de consulta ou de "executar qualquer coisa".
- **Dupla confirmação nas ações que alteram dados**: o Claude pede aprovação para usar a ferramenta *e* o servidor só executa com `confirmar=true` depois de mostrar o resumo ao usuário.
- **Auditoria**: cada chamada fica em `logs\auditoria-<persona>.jsonl` (quem, ferramenta, parâmetros, resultado), sem senhas.
- **Dados sensíveis**: cartões só com os 4 últimos dígitos; checkout usa cartão e endereço já salvos (o agente nunca vê número de cartão).
- **Certificado fixado**: o servidor de integração usa certificado autoassinado; o MCP só aceita aquele certificado específico (impressão digital SHA-256).
- As senhas ficam só no `claude_desktop_config.json` do seu notebook.

## Roteiro sugerido da demo

**1. Torcedor (ligue só `flamengo-torcedor`)**
- "Quem sou eu e qual meu plano de sócio?"
- "Quais os próximos jogos no Maracanã? Quanto eu pago no setor Leste contra o Bahia com meu desconto?"
- "Coloca 2 ingressos no Leste pra esse jogo e um boné tamanho G" → o Claude mostra o resumo, você confirma → abra a loja e mostre o carrinho.
- "Aplica o cupom mengao. Quanto ficou?" (sócio 10% + cupom 15% nos produtos da loja)
- "Finaliza a compra" → resumo com endereço e cartão final 1111 → confirmar → pedido criado, aparece em Minha conta.
- "Quero cancelar meu plano, vou me mudar" → abre o chamado.

**2. Gestor do sócio-torcedor (ligue só `flamengo-gestor-socio`)**
- "Como estão os planos? Quantas vagas restam no Nação Maraca 1?"
- "Quantos cancelamentos tivemos este mês e por quê?"
- "Confirma o cancelamento que acabou de chegar" → o torcedor perde selo e descontos na hora (mostre a loja).
- "Aumenta o desconto na loja do Nação Maraca 2 para 12%" → o Commerce republica as promoções sozinho.
- "Quantas vezes o cupom mengao foi usado?"
- Mostre o bloqueio: "Cria um jogo contra o Vasco" → ele não tem essa ferramenta nem acesso.

**3. Gestor de jogos (ligue só `flamengo-gestor-jogos`)**
- "Quais jogos estão à venda e como está a ocupação?"
- "Cadastra Flamengo x Vasco, 29/11 às 16h, Brasileirão Rodada 37, Oeste a R$ 240" → o Commerce cria produto, 384 poltronas e as 4 ondas → abra a loja e mostre o jogo.
- "Baixa o Norte desse jogo para R$ 70" / "Quanto vendemos de ingresso no jogo do Bahia, por setor?"

Dicas: antes da demo, deixe o carrinho do torcedor vazio; para o cancelamento, use um torcedor sócio que você possa reativar (ou recompre o plano depois pela loja).
