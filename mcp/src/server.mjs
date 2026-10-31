/*
 * Servidor MCP "Loja Flamengo" (stdio) para o Claude Desktop.
 *
 *   node flamengo-mcp.mjs            → roda como servidor MCP (o Claude Desktop inicia sozinho)
 *   node flamengo-mcp.mjs --teste    → testa a conexão e o login da persona e sai
 *
 * Configuração por variáveis de ambiente (no claude_desktop_config.json):
 *   FLAMENGO_PERSONA     torcedor | socio | jogos
 *   TORCEDOR_EMAIL / TORCEDOR_SENHA      (persona torcedor)
 *   GESTOR_USUARIO / GESTOR_SENHA        (personas socio e jogos: gestor.socio / gestor.jogos)
 *   Opcionais: OCC_URL, OAUTH_URL, ODATA_URL, STORE_URL, OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET,
 *              ODATA_CERT_SHA256 (impressão digital do certificado do servidor), LOG_DIR
 */
import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { buildTools } from './core.js';

const VERSION = '1.0.0';
const env = process.env;
const persona = (env.FLAMENGO_PERSONA || 'torcedor').toLowerCase();
const cfg = {
  occUrl: env.OCC_URL || 'https://api-flamengo.democc.online/occ/v2/electronics-spa',
  oauthUrl: env.OAUTH_URL || 'https://api-flamengo.democc.online/authorizationserver/oauth/token',
  odataUrl: env.ODATA_URL || 'https://187.127.62.169:9105/odata2webservices',
  storeUrl: env.STORE_URL || 'https://spartacus-flamengo.democc.online/electronics-spa/pt/BRL',
  clientId: env.OAUTH_CLIENT_ID || 'mobile_android',
  clientSecret: env.OAUTH_CLIENT_SECRET || 'secret',
  torcedorEmail: env.TORCEDOR_EMAIL,
  torcedorSenha: env.TORCEDOR_SENHA,
  gestorUsuario: env.GESTOR_USUARIO,
  gestorSenha: env.GESTOR_SENHA,
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
function audit(entry) {
  try {
    fs.mkdirSync(logDir, { recursive: true });
    fs.appendFileSync(path.join(logDir, `auditoria-${persona}.jsonl`), JSON.stringify({ quando: new Date().toISOString(), persona, ...redact(entry) }) + '\n');
  } catch {
    /* auditoria nunca derruba a ferramenta */
  }
}

// ------------------------------------------------------------ rede

const odataAgent = new https.Agent({
  keepAlive: true,
  rejectUnauthorized: false, // autoassinado: validado abaixo pela impressão digital
  checkServerIdentity: () => undefined,
});

function requestOData(url, { method, headers, body }) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, { method, headers, agent: odataAgent, timeout: 120000 }, (res) => {
      const fp = res.socket?.getPeerCertificate?.()?.fingerprint256?.toUpperCase();
      if (cfg.odataCert && fp && fp !== cfg.odataCert) {
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
  const root = base === 'occ' ? cfg.occUrl : base === 'oauth' ? cfg.oauthUrl : cfg.odataUrl;
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

// ------------------------------------------------------------ MCP

const PERSONAS = {
  torcedor: { nome: 'flamengo-torcedor', titulo: 'Loja Flamengo · Torcedor', login: () => cfg.torcedorEmail },
  socio: { nome: 'flamengo-gestor-socio', titulo: 'Loja Flamengo · Gestor do sócio-torcedor', login: () => cfg.gestorUsuario },
  jogos: { nome: 'flamengo-gestor-jogos', titulo: 'Loja Flamengo · Gestor de jogos e ingressos', login: () => cfg.gestorUsuario },
};
const info = PERSONAS[persona];
if (!info) {
  console.error(`FLAMENGO_PERSONA inválida: "${persona}". Use torcedor, socio ou jogos.`);
  process.exit(2);
}
const tools = buildTools({ persona, cfg, http });
const byName = new Map(tools.map((t) => [t.name, t]));

const INSTRUCTIONS = {
  torcedor: 'Você atende um torcedor do Flamengo na loja oficial (SAP Commerce Cloud). Responda em português. Antes de qualquer ação que altere o carrinho, compre, assine ou cancele plano, ou abra chamado, chame a ferramenta sem confirmar, mostre o resumo e só repita com confirmar=true depois que o torcedor concordar.',
  socio: 'Você apoia o gestor do programa sócio-torcedor do Flamengo (SAP Commerce Cloud). Responda em português. Ações que alteram planos, cupons ou confirmam cancelamentos: mostre o resumo e só execute com confirmar=true após o gestor concordar. Você não tem acesso a jogos e ingressos.',
  jogos: 'Você apoia o gestor de jogos e ingressos do Flamengo (SAP Commerce Cloud). Responda em português. Criar ou alterar jogos: mostre o resumo e só execute com confirmar=true após o gestor concordar. Você não tem acesso ao sócio-torcedor.',
}[persona];

async function selfTest() {
  const first = persona === 'torcedor' ? 'minha_conta' : persona === 'socio' ? 'planos' : 'jogos';
  try {
    const r = await byName.get(first).handler({});
    console.log(`OK · ${info.titulo} · ${tools.length} ferramentas · login ${info.login() ?? '—'}`);
    console.log(JSON.stringify(r, null, 2).slice(0, 800));
    process.exit(0);
  } catch (e) {
    console.error(`FALHOU · ${info.titulo}: ${e.message}`);
    process.exit(1);
  }
}

if (process.argv.includes('--teste')) {
  await selfTest();
} else {
  const server = new Server({ name: info.nome, title: info.titulo, version: VERSION }, { capabilities: { tools: {} }, instructions: INSTRUCTIONS });

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
    try {
      const result = await tool.handler(args);
      audit({ usuario: info.login(), ferramenta: name, argumentos: args, resultado: result?.executado === false ? 'aguardando confirmação' : 'ok', ms: Date.now() - started });
      return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) }] };
    } catch (e) {
      audit({ usuario: info.login(), ferramenta: name, argumentos: args, resultado: 'erro', erro: e.message, ms: Date.now() - started });
      return { isError: true, content: [{ type: 'text', text: e.message }] };
    }
  });

  await server.connect(new StdioServerTransport());
}
