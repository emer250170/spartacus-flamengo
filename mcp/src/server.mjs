/*
 * Servidor MCP "Loja Flamengo".
 *
 *   node flamengo-mcp.mjs            → stdio, uma persona (o Claude Desktop inicia sozinho)
 *   node flamengo-mcp.mjs --teste    → testa a conexão e o login da persona e sai
 *   node flamengo-mcp.mjs --http     → servidor HTTP (Streamable HTTP) com as quatro personas, para usar
 *                                      como conector personalizado no claude.ai e no app do celular
 *
 * Modo stdio (claude_desktop_config.json):
 *   FLAMENGO_PERSONA     torcedor | socio | jogos | loja
 *   TORCEDOR_EMAIL / TORCEDOR_SENHA      (persona torcedor)
 *   GESTOR_USUARIO / GESTOR_SENHA        (personas socio, jogos e loja: gestor.socio / gestor.jogos / gestor.loja)
 *
 * Modo --http (arquivo de ambiente no servidor; ver flamengo-mcp.env.exemplo):
 *   MCP_HOST (127.0.0.1), MCP_PORT (8787)
 *   Uma rota por persona; a rota só liga quando o token e o login dela estão preenchidos:
 *     /torcedor/mcp      MCP_TOKEN_TORCEDOR + TORCEDOR_EMAIL / TORCEDOR_SENHA
 *     /gestor-socio/mcp  MCP_TOKEN_SOCIO    + SOCIO_USUARIO  / SOCIO_SENHA
 *     /gestor-jogos/mcp  MCP_TOKEN_JOGOS    + JOGOS_USUARIO  / JOGOS_SENHA
 *     /gestor-loja/mcp   MCP_TOKEN_LOJA     + LOJA_USUARIO   / LOJA_SENHA
 *   O Claude manda o token no cabeçalho "Authorization: Bearer <token>" (cadastrado no conector).
 *   Plano B, se o cabeçalho não puder ser cadastrado: /<rota>/<token>/mcp (MCP_TOKEN_NA_URL=true).
 *
 * Opcionais nos dois modos: OCC_URL, OAUTH_URL, ODATA_URL, STORE_URL, OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET,
 *   ODATA_CERT_SHA256 (impressão digital do certificado do servidor), LOG_DIR
 */
import https from 'node:https';
import nodeHttp from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { buildTools } from './core.js';

const VERSION = '1.3.0';
const env = process.env;
const baseCfg = {
  occUrl: env.OCC_URL || 'https://api-flamengo.democc.online/occ/v2/electronics-spa',
  oauthUrl: env.OAUTH_URL || 'https://api-flamengo.democc.online/authorizationserver/oauth/token',
  odataUrl: env.ODATA_URL || 'https://187.127.62.169:9105/odata2webservices',
  storeUrl: env.STORE_URL || 'https://spartacus-flamengo.democc.online/electronics-spa/pt/BRL',
  clientId: env.OAUTH_CLIENT_ID || 'mobile_android',
  clientSecret: env.OAUTH_CLIENT_SECRET || 'secret',
  // certificado autoassinado do SAP Commerce: só este é aceito (pinning)
  odataCert: (env.ODATA_CERT_SHA256 || '5C:DF:0E:E5:CC:5B:CE:AB:C3:1E:7E:D6:BB:AA:FE:1E:57:D5:08:08:82:EE:EE:37:5E:8B:2B:60:C8:DC:1E:1F').toUpperCase(),
};
const here = path.dirname(fileURLToPath(import.meta.url));
const logDir = env.LOG_DIR || path.join(here, 'logs');

// ------------------------------------------------------------ auditoria

const SECRET = /senha|password|secret|token|authorization/i;
function redact(v) {
  if (Array.isArray(v)) return v.map(redact);
  if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, SECRET.test(k) ? '***' : redact(x)]));
  return v;
}
function audit(persona, entry) {
  try {
    fs.mkdirSync(logDir, { recursive: true });
    fs.appendFileSync(path.join(logDir, `auditoria-${persona}.jsonl`), JSON.stringify({ quando: new Date().toISOString(), persona, ...redact(entry) }) + '\n');
  } catch {
    /* auditoria nunca derruba a ferramenta */
  }
}

// ------------------------------------------------------------ rede (SAP Commerce)

const odataAgent = new https.Agent({
  keepAlive: true,
  rejectUnauthorized: false, // autoassinado: validado abaixo pela impressão digital
  checkServerIdentity: () => undefined,
});

function requestOData(url, { method, headers, body }) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, { method, headers, agent: odataAgent, timeout: 120000 }, (res) => {
      const fp = res.socket?.getPeerCertificate?.()?.fingerprint256?.toUpperCase();
      if (baseCfg.odataCert && fp && fp !== baseCfg.odataCert) {
        res.destroy();
        reject(new Error(`Certificado do servidor diferente do esperado (${fp}). Conexão recusada.`));
        return;
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve({ status: res.statusCode, text: Buffer.concat(chunks).toString('utf8') }));
    });
    req.on('timeout', () => req.destroy(new Error('Tempo esgotado falando com o SAP Commerce.')));
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

async function http({ base, method = 'GET', path: p = '', query, body, form, headers = {} }) {
  const root = base === 'occ' ? baseCfg.occUrl : base === 'oauth' ? baseCfg.oauthUrl : baseCfg.odataUrl;
  const url = new URL(root + p);
  for (const [k, v] of Object.entries(query ?? {})) if (v != null) url.searchParams.set(k, String(v));
  const h = { ...headers };
  let payload;
  if (form) {
    payload = new URLSearchParams(form).toString();
    h['Content-Type'] = 'application/x-www-form-urlencoded';
  } else if (body !== undefined) {
    payload = JSON.stringify(body);
    h['Content-Type'] = 'application/json';
  }
  let status;
  let text;
  if (base === 'odata') {
    if (payload) h['Content-Length'] = Buffer.byteLength(payload);
    ({ status, text } = await requestOData(url, { method, headers: h, body: payload }));
  } else {
    const r = await fetch(url, { method, headers: h, body: payload });
    status = r.status;
    text = await r.text();
  }
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* resposta não JSON */ }
  return { status, json, text };
}

// ------------------------------------------------------------ personas

const PERSONAS = {
  torcedor: { nome: 'flamengo-torcedor', titulo: 'Loja Flamengo · Torcedor', rota: 'torcedor' },
  socio: { nome: 'flamengo-gestor-socio', titulo: 'Loja Flamengo · Gestor do sócio-torcedor', rota: 'gestor-socio' },
  jogos: { nome: 'flamengo-gestor-jogos', titulo: 'Loja Flamengo · Gestor de jogos e ingressos', rota: 'gestor-jogos' },
  loja: { nome: 'flamengo-gestor-loja', titulo: 'Loja Flamengo · Gestor da loja', rota: 'gestor-loja' },
};

const INSTRUCTIONS = {
  torcedor: 'Você atende um torcedor do Flamengo na loja oficial (SAP Commerce Cloud). Responda em português. Antes de qualquer ação que altere o carrinho, compre, assine ou cancele plano, ou abra chamado, chame a ferramenta sem confirmar, mostre o resumo e só repita com confirmar=true depois que o torcedor concordar.',
  socio: 'Você apoia o gestor do programa sócio-torcedor do Flamengo (SAP Commerce Cloud). Responda em português. Ações que alteram planos, cupons, suspendem ou regularizam sócios ou confirmam cancelamentos: mostre o resumo e só execute com confirmar=true após o gestor concordar. Você não tem acesso a jogos e ingressos. Relatório por e-mail (enviar_relatorio): monte o conteúdo só com dados das ferramentas desta conversa, mostre o que vai no e-mail e envie com confirmar=true; o Commerce só manda para destinatários liberados. Cupom pessoal (criar_cupom_cliente): confirme cliente, desconto, onde vale e validade antes; o Commerce publica a promoção e envia o código ao cliente.',
  jogos: 'Você apoia o gestor de jogos e ingressos do Flamengo (SAP Commerce Cloud). Responda em português. Criar ou alterar jogos: mostre o resumo e só execute com confirmar=true após o gestor concordar. Você não tem acesso ao sócio-torcedor. Relatório por e-mail (enviar_relatorio): monte o conteúdo só com dados das ferramentas desta conversa, mostre o que vai no e-mail e envie com confirmar=true; o Commerce só manda para destinatários liberados. Cupom pessoal (criar_cupom_cliente): confirme cliente, desconto, onde vale e validade antes; o Commerce publica a promoção e envia o código ao cliente.',
  loja: 'Você apoia o gestor da loja virtual do Flamengo (SAP Commerce Cloud): produtos, preço, estoque, vendas, pedidos, promoções, cupons e experiências do sócio. Responda em português e em reais (R$). Mudar preço, estoque, cupom ou experiência: chame a ferramenta sem confirmar, mostre o resumo e só repita com confirmar=true depois que o gestor concordar. Promoções são só consulta (mudar é no Backoffice). Jogos e ingressos são do gestor de jogos; planos e sócios, do gestor do sócio-torcedor. Relatório por e-mail (enviar_relatorio): monte o conteúdo só com dados das ferramentas desta conversa, mostre o que vai no e-mail e envie com confirmar=true; o Commerce só manda para destinatários liberados. Cupom pessoal (criar_cupom_cliente): confirme cliente, desconto, onde vale e validade antes; o Commerce publica a promoção e envia o código ao cliente.',
};

/** Uma persona pronta: ferramentas (com a sessão de login no Commerce) e o servidor MCP que as expõe. */
function createPersona(persona, creds) {
  const info = PERSONAS[persona];
  const cfg = { ...baseCfg, ...creds };
  const tools = buildTools({ persona, cfg, http });
  const byName = new Map(tools.map((t) => [t.name, t]));
  const login = () => (persona === 'torcedor' ? cfg.torcedorEmail : cfg.gestorUsuario);

  function newServer(origem) {
    const server = new Server({ name: info.nome, title: info.titulo, version: VERSION }, { capabilities: { tools: {} }, instructions: INSTRUCTIONS[persona] });

    server.setRequestHandler(ListToolsRequestSchema, async () => ({
      tools: tools.map((t) => ({
        name: t.name,
        description: t.description,
        inputSchema: t.inputSchema,
        annotations: { readOnlyHint: !('confirmar' in (t.inputSchema.properties ?? {})) && t.name !== 'entrar_como' },
      })),
    }));

    server.setRequestHandler(CallToolRequestSchema, async (req) => {
      const name = req.params.name;
      const args = req.params.arguments ?? {};
      const tool = byName.get(name);
      const started = Date.now();
      if (!tool) return { isError: true, content: [{ type: 'text', text: `Ferramenta desconhecida: ${name}` }] };
      const base = { usuario: login(), ferramenta: name, argumentos: args, ...(origem ? { origem } : {}) };
      try {
        const result = await tool.handler(args);
        audit(persona, { ...base, resultado: result?.executado === false ? 'aguardando confirmação' : 'ok', ms: Date.now() - started });
        return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) }] };
      } catch (e) {
        audit(persona, { ...base, resultado: 'erro', erro: e.message, ms: Date.now() - started });
        return { isError: true, content: [{ type: 'text', text: e.message }] };
      }
    });
    return server;
  }

  return { persona, info, tools, byName, login, newServer };
}

// ------------------------------------------------------------ modo stdio (Claude Desktop)

async function runStdio() {
  const persona = (env.FLAMENGO_PERSONA || 'torcedor').toLowerCase();
  if (!PERSONAS[persona]) {
    console.error(`FLAMENGO_PERSONA inválida: "${persona}". Use torcedor, socio, jogos ou loja.`);
    process.exit(2);
  }
  const p = createPersona(persona, {
    torcedorEmail: env.TORCEDOR_EMAIL,
    torcedorSenha: env.TORCEDOR_SENHA,
    gestorUsuario: env.GESTOR_USUARIO,
    gestorSenha: env.GESTOR_SENHA,
  });

  if (process.argv.includes('--teste')) {
    const first = { torcedor: 'minha_conta', socio: 'planos', jogos: 'jogos', loja: 'experiencias' }[persona];
    try {
      const r = await p.byName.get(first).handler({});
      console.log(`OK · ${p.info.titulo} · ${p.tools.length} ferramentas · login ${p.login() ?? '—'}`);
      console.log(JSON.stringify(r, null, 2).slice(0, 800));
      process.exit(0);
    } catch (e) {
      console.error(`FALHOU · ${p.info.titulo}: ${e.message}`);
      process.exit(1);
    }
  }
  await p.newServer().connect(new StdioServerTransport());
}

// ------------------------------------------------------------ modo HTTP (conector personalizado)

const MAX_BODY = 1024 * 1024;
const FAIL_LIMIT = 20; // tentativas com token errado por IP …
const FAIL_WINDOW_MS = 10 * 60 * 1000; // … em 10 minutos bloqueiam o IP por 10 minutos
const failures = new Map();

function clientIp(req) {
  const fwd = String(req.headers['x-forwarded-for'] ?? '').split(',')[0].trim();
  return fwd || req.socket.remoteAddress || '?';
}

function blocked(ip) {
  const f = failures.get(ip);
  if (!f) return false;
  if (Date.now() - f.first > FAIL_WINDOW_MS) {
    failures.delete(ip);
    return false;
  }
  return f.count >= FAIL_LIMIT;
}

function registerFailure(ip) {
  const f = failures.get(ip);
  if (!f || Date.now() - f.first > FAIL_WINDOW_MS) failures.set(ip, { first: Date.now(), count: 1 });
  else f.count += 1;
}

function sameToken(given, expected) {
  if (!given || !expected) return false;
  const a = crypto.createHash('sha256').update(given).digest();
  const b = crypto.createHash('sha256').update(expected).digest();
  return crypto.timingSafeEqual(a, b);
}

function sendJson(res, status, obj, headers = {}) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers });
  res.end(JSON.stringify(obj));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(Object.assign(new Error('Requisição grande demais'), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      const text = Buffer.concat(chunks).toString('utf8');
      if (!text) return resolve(undefined);
      try {
        resolve(JSON.parse(text));
      } catch {
        reject(Object.assign(new Error('JSON inválido'), { status: 400 }));
      }
    });
    req.on('error', reject);
  });
}

function httpRoutes() {
  const defs = [
    { persona: 'torcedor', token: env.MCP_TOKEN_TORCEDOR, creds: { torcedorEmail: env.TORCEDOR_EMAIL, torcedorSenha: env.TORCEDOR_SENHA }, ok: () => env.TORCEDOR_EMAIL && env.TORCEDOR_SENHA },
    { persona: 'socio', token: env.MCP_TOKEN_SOCIO, creds: { gestorUsuario: env.SOCIO_USUARIO, gestorSenha: env.SOCIO_SENHA }, ok: () => env.SOCIO_USUARIO && env.SOCIO_SENHA },
    { persona: 'jogos', token: env.MCP_TOKEN_JOGOS, creds: { gestorUsuario: env.JOGOS_USUARIO, gestorSenha: env.JOGOS_SENHA }, ok: () => env.JOGOS_USUARIO && env.JOGOS_SENHA },
    { persona: 'loja', token: env.MCP_TOKEN_LOJA, creds: { gestorUsuario: env.LOJA_USUARIO, gestorSenha: env.LOJA_SENHA }, ok: () => env.LOJA_USUARIO && env.LOJA_SENHA },
  ];
  const routes = new Map();
  for (const d of defs) {
    const rota = PERSONAS[d.persona].rota;
    if (!d.token || !d.ok()) {
      console.log(`· /${rota}/mcp desligada (falta token ou login no arquivo de ambiente)`);
      continue;
    }
    if (d.token.length < 32) {
      console.error(`· /${rota}/mcp desligada: o token precisa ter pelo menos 32 caracteres (use: openssl rand -hex 32)`);
      continue;
    }
    routes.set(rota, { token: d.token, persona: createPersona(d.persona, d.creds) });
    console.log(`· /${rota}/mcp ligada (${PERSONAS[d.persona].titulo})`);
  }
  return routes;
}

async function runHttp() {
  const routes = httpRoutes();
  const tokenInUrl = /^(1|true|sim)$/i.test(env.MCP_TOKEN_NA_URL ?? '');
  const host = env.MCP_HOST || '127.0.0.1';
  const port = Number(env.MCP_PORT || 8787);

  const server = nodeHttp.createServer(async (req, res) => {
    const ip = clientIp(req);
    const url = new URL(req.url, 'http://local');
    const parts = url.pathname.split('/').filter(Boolean);

    if (url.pathname === '/saude') {
      return sendJson(res, 200, { ok: true, versao: VERSION, rotas: [...routes.keys()].map((r) => `/${r}/mcp`) });
    }

    // /<rota>/mcp  ou (plano B) /<rota>/<token>/mcp
    const route = routes.get(parts[0]);
    const shapeOk = route && ((parts.length === 2 && parts[1] === 'mcp') || (tokenInUrl && parts.length === 3 && parts[2] === 'mcp'));
    if (!shapeOk) return sendJson(res, 404, { erro: 'não encontrado' });

    if (blocked(ip)) return sendJson(res, 429, { erro: 'muitas tentativas; tente mais tarde' });
    const bearer = /^Bearer\s+(.+)$/i.exec(String(req.headers.authorization ?? ''))?.[1]?.trim();
    const given = parts.length === 3 ? parts[1] : bearer;
    if (!sameToken(given, route.token)) {
      registerFailure(ip);
      audit(route.persona.persona, { origem: ip, resultado: 'acesso negado (token)' });
      return sendJson(res, 401, { erro: 'não autorizado' }, { 'WWW-Authenticate': 'Bearer' });
    }

    if (req.method !== 'POST') {
      // sem sessão no servidor (stateless): não há fluxo SSE para abrir nem sessão para encerrar
      return sendJson(res, 405, { jsonrpc: '2.0', error: { code: -32000, message: 'Método não permitido.' }, id: null }, { Allow: 'POST' });
    }

    let body;
    try {
      body = await readBody(req);
    } catch (e) {
      return sendJson(res, e.status ?? 400, { jsonrpc: '2.0', error: { code: -32700, message: e.message }, id: null });
    }

    const mcp = route.persona.newServer(ip);
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
    res.on('close', () => {
      transport.close().catch(() => {});
      mcp.close().catch(() => {});
    });
    try {
      await mcp.connect(transport);
      await transport.handleRequest(req, res, body);
    } catch (e) {
      console.error('Erro atendendo MCP:', e.message);
      if (!res.headersSent) sendJson(res, 500, { jsonrpc: '2.0', error: { code: -32603, message: 'Erro interno' }, id: null });
    }
  });

  server.listen(port, host, () => {
    console.log(`Loja Flamengo MCP ${VERSION} ouvindo em http://${host}:${port} (${routes.size} persona(s))`);
  });
  const stop = () => server.close(() => process.exit(0));
  process.on('SIGTERM', stop);
  process.on('SIGINT', stop);
}

if (process.argv.includes('--http')) await runHttp();
else await runStdio();
