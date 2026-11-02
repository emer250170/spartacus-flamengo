/*
 * Loja Flamengo (SAP Commerce Cloud) como ferramentas para um agente de IA (MCP).
 *
 * Núcleo sem dependências: recebe um `http` (adaptador de rede) e devolve as
 * ferramentas da persona. Três personas, cada uma com o próprio usuário do
 * Commerce e só as ferramentas do seu papel:
 *   - torcedor: API da loja (OCC) com o login do torcedor
 *   - socio:    API de integração (OData) com o gestor.socio  (planos, chamados, cupons)
 *   - jogos:    API de integração (OData) com o gestor.jogos  (jogos e venda de ingressos)
 * Quem decide o que cada usuário pode fazer é o próprio Commerce (permissões por
 * tipo); o MCP só expõe a lista fechada de ações. Ações que alteram dados exigem
 * confirmar=true (depois de o usuário concordar).
 */

const TZ = 'America/Sao_Paulo';
const SOCIO_CATEGORY = 'socio_torcedor';
const MATCHES_CATEGORY = 'flamengo_jogos_casa';
const SERVICES_CATEGORY = 'flamengo_matchday';
const PLAN_MARKER = /<!--\s*socio-plan\s*([\s\S]*?)-->/i;
const CANCEL_SUBJECT = 'Cancelamento do plano';
/** Grupo de quem está com os benefícios suspensos (mensalidade em atraso) */
const SUSPENDED_GROUP = 'socio-suspenso';
const CANCEL_REASONS = {
  price: 'O valor ficou alto',
  games: 'Não estou indo aos jogos',
  moving: 'Vou me mudar',
  service: 'Insatisfeito com o atendimento',
  other: 'Outro motivo',
};
const REASON_KEYS = {
  'o valor ficou alto': 'price', 'it got too expensive': 'price',
  'não estou indo aos jogos': 'games', 'i am not going to the games': 'games',
  'vou me mudar': 'moving', 'i am moving': 'moving',
  'insatisfeito com o atendimento': 'service', 'unhappy with the service': 'service',
  'outro motivo': 'other', 'other reason': 'other',
};
const SECTORS = { NORTE: 'priceNorte', SUL: 'priceSul', LESTE: 'priceLeste', OESTE: 'priceOeste' };

// ---------------------------------------------------------------- utilidades

class UserError extends Error {}

function brl(v) {
  const n = Number(v);
  if (!isFinite(n)) return null;
  return 'R$ ' + n.toFixed(2).replace('.', ',').replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

function toDate(v) {
  if (v == null || v === '') return null;
  if (v instanceof Date) return v;
  const m = /\/Date\((-?\d+)/.exec(String(v));
  if (m) return new Date(Number(m[1]));
  const d = new Date(v);
  return isNaN(d.getTime()) ? null : d;
}

function fmtDate(v, withTime = true) {
  const d = toDate(v);
  if (!d) return null;
  const p = Object.fromEntries(
    new Intl.DateTimeFormat('pt-BR', {
      timeZone: TZ, day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', hour12: false,
    }).formatToParts(d).map((x) => [x.type, x.value])
  );
  const date = `${p.day}/${p.month}/${p.year}`;
  return withTime ? `${date} ${p.hour}:${p.minute}` : date;
}

/** "2026-11-20T21:30" (horário de Brasília se não tiver fuso) → Date */
function parseLocal(s) {
  if (!s) return null;
  const str = String(s).trim();
  const br = /^(\d{2})\/(\d{2})\/(\d{4})(?:[ T](\d{2}):(\d{2}))?$/.exec(str);
  let iso = br ? `${br[3]}-${br[2]}-${br[1]}T${br[4] ?? '00'}:${br[5] ?? '00'}` : str;
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) iso += 'T00:00';
  if (!/([zZ]|[+-]\d{2}:?\d{2})$/.test(iso)) iso += '-03:00';
  const d = new Date(iso);
  if (isNaN(d.getTime())) throw new UserError(`Data inválida: "${s}". Use AAAA-MM-DD HH:MM (horário de Brasília).`);
  return d;
}

const odataDate = (d) => `/Date(${d.getTime()})/`;
const clean = (s) => String(s ?? '').replace(/<[^>]+>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();
/** Texto (HTML ou com quebras) → uma linha por item */
const lines = (html) => String(html ?? '').replace(/<\/?(li|p|br|ul|ol|div)[^>]*>/gi, '\n').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ')
  .split(/\n/).map((x) => x.replace(/\s+/g, ' ').trim()).filter(Boolean);
const num = (v) => (v == null || v === '' ? null : Number(v));
const enc = encodeURIComponent;
const lastDigits = (s) => String(s ?? '').replace(/\D/g, '').slice(-4);

function needConfirm(args, summary) {
  if (args?.confirmar === true) return null;
  return {
    executado: false,
    aviso: 'Ação NÃO executada. Mostre o resumo ao usuário e, se ele concordar, chame de novo com confirmar=true.',
    resumo: summary,
  };
}

function parseCancelMessage(text) {
  const lines = String(text ?? '').split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  let reason = null;
  let first = 0;
  if (lines[0] && /^(motivo|reason)\s*:/i.test(lines[0])) {
    reason = lines[0].replace(/^[^:]*:\s*/, '');
    first = 1;
  }
  let last = lines.length;
  if (last > first && lines[last - 1].includes('·')) last--;
  const key = reason ? REASON_KEYS[reason.toLowerCase()] ?? null : null;
  return {
    motivo: key ? CANCEL_REASONS[key] : reason,
    motivoChave: key ?? (reason ? 'outro' : null),
    comentario: last > first ? lines.slice(first, last).join(' ') : null,
  };
}

// ---------------------------------------------------------------- ferramentas

export function buildTools(ctx) {
  const { persona, cfg, http } = ctx;
  if (persona === 'torcedor') return torcedorTools(cfg, http);
  if (persona === 'socio') return socioTools(cfg, http);
  if (persona === 'jogos') return jogosTools(cfg, http);
  throw new Error(`Persona desconhecida: ${persona} (use torcedor, socio ou jogos)`);
}

const S = {
  str: (description) => ({ type: 'string', description }),
  int: (description) => ({ type: 'integer', description }),
  num: (description) => ({ type: 'number', description }),
  bool: (description) => ({ type: 'boolean', description }),
  confirm: { type: 'boolean', description: 'true só depois de o usuário concordar com o resumo da ação' },
};
const obj = (properties = {}, required = []) => ({ type: 'object', properties, required, additionalProperties: false });

// ================================================================ TORCEDOR (OCC)

function torcedorTools(cfg, http) {
  let session = { email: cfg.torcedorEmail, password: cfg.torcedorSenha, token: null, uid: null };
  let plansCache = null;

  async function login() {
    if (!session.email || !session.password) {
      throw new UserError('Nenhum torcedor logado. Use a ferramenta entrar_como (e-mail e senha da loja).');
    }
    const r = await http({
      base: 'oauth', method: 'POST',
      form: { grant_type: 'password', client_id: cfg.clientId, client_secret: cfg.clientSecret, username: session.email, password: session.password },
    });
    if (r.status !== 200 || !r.json?.access_token) {
      session.token = null;
      throw new UserError('Login recusado pela loja para ' + session.email + '. Confira e-mail e senha.');
    }
    session.token = r.json.access_token;
    session.uid = session.email.toLowerCase();
  }

  async function occ(method, path, { query, body, auth = true } = {}) {
    if (auth && !session.token) await login();
    const call = () => http({
      base: 'occ', method, path, body,
      query: { lang: cfg.lang ?? 'pt', curr: cfg.curr ?? 'BRL', ...(query ?? {}) },
      headers: auth ? { Authorization: 'Bearer ' + session.token } : {},
    });
    let r = await call();
    if (auth && r.status === 401) {
      await login();
      r = await call();
    }
    if (r.status >= 400) {
      const msg = r.json?.errors?.map((e) => e.message).filter(Boolean).join('; ') || r.text?.slice(0, 300);
      throw new UserError(`A loja recusou (${r.status}): ${msg}`);
    }
    return r.json ?? {};
  }

  async function plans() {
    if (plansCache) return plansCache;
    const res = await occ('GET', '/products/search', {
      auth: false,
      query: { query: `:relevance:allCategories:${SOCIO_CATEGORY}`, pageSize: 50, fields: 'products(code,name,summary,price(value),stock(stockLevel,stockLevelStatus))' },
    });
    const products = res.products ?? [];
    const out = [];
    for (const p of products) {
      const det = await occ('GET', `/products/${enc(p.code)}`, { auth: false, query: { fields: 'description' } });
      const desc = det.description ?? '';
      const m = PLAN_MARKER.exec(desc);
      if (!m) continue;
      let r;
      try { r = JSON.parse(m[1]); } catch { continue; }
      if (!r.group) continue;
      const guest = r.guest ? products.find((g) => g.code === r.guest) : null;
      out.push({
        code: p.code,
        nome: clean(p.name),
        resumo: clean(p.summary),
        programa: r.family || null,
        mensalidade: p.price?.value ?? null,
        mensalidadeTexto: brl(p.price?.value),
        onda: r.wave > 0 ? r.wave : null,
        prioridade: r.wave > 0 ? `Onda ${r.wave}` : 'Venda geral (sem prioridade)',
        descontoIngresso: Number(r.discount ?? 0),
        descontoLoja: Number(r.storeDiscount ?? 0),
        maxConvidados: Number(r.maxGuests ?? 0),
        convidado: guest ? { code: guest.code, mensalidade: brl(guest.price?.value) } : null,
        esgotado: p.stock?.stockLevelStatus === 'outOfStock' || p.stock?.stockLevel === 0,
        beneficios: lines(desc.replace(PLAN_MARKER, '')),
        _group: r.group,
        _pending: r.pending || null,
        _order: Number(r.order ?? 9999),
      });
    }
    out.sort((a, b) => a._order - b._order);
    plansCache = out;
    return out;
  }

  async function membership() {
    if (!session.token) await login();
    const groups = (await occ('GET', `/users/${enc(session.uid)}/customergroups`, { query: { fields: 'userGroups(uid)', pageSize: 100 } }))
      .userGroups?.map((g) => g.uid) ?? [];
    const all = await plans();
    const member = all.find((p) => groups.includes(p._group) && !(p._pending && groups.includes(p._pending))) ?? null;
    const inCart = all.find((p) => p._pending && groups.includes(p._pending)) ?? null;
    return { member, inCart, suspended: !!member && groups.includes(SUSPENDED_GROUP) };
  }

  const publicPlan = (p) => p && Object.fromEntries(Object.entries(p).filter(([k]) => !k.startsWith('_')));

  async function cartId(create = true) {
    const res = await occ('GET', '/users/current/carts', { query: { fields: 'carts(code,saveTime,totalItems)', savedCartsOnly: false, pageSize: 20 } });
    const open = (res.carts ?? []).filter((c) => !c.saveTime);
    if (open.length) return open[0].code;
    if (!create) return null;
    const c = await occ('POST', '/users/current/carts', { query: { fields: 'code' } });
    return c.code;
  }

  async function cartView(id) {
    const c = await occ('GET', `/users/current/carts/${enc(id)}`, { query: { fields: 'FULL' } });
    return {
      carrinho: c.code,
      itens: (c.entries ?? []).map((e) => ({
        entrada: e.entryNumber,
        produto: clean(e.product?.name),
        codigo: e.product?.code,
        quantidade: e.quantity,
        precoUnitario: e.basePrice?.formattedValue ?? brl(e.basePrice?.value),
        total: e.totalPrice?.formattedValue ?? brl(e.totalPrice?.value),
      })),
      cupons: (c.appliedVouchers ?? []).map((v) => v.voucherCode || v.code),
      promocoes: [...(c.appliedOrderPromotions ?? []), ...(c.appliedProductPromotions ?? [])].map((p) => clean(p.description)).filter(Boolean),
      subtotal: c.subTotal?.formattedValue,
      descontos: c.totalDiscounts?.formattedValue,
      total: c.totalPriceWithTax?.formattedValue ?? c.totalPrice?.formattedValue,
      linkCarrinho: `${cfg.storeUrl}/cart`,
    };
  }

  async function addEntry(id, code, quantity) {
    const r = await occ('POST', `/users/current/carts/${enc(id)}/entries`, { body: { product: { code }, quantity }, query: { fields: 'statusCode,statusMessage,quantityAdded' } });
    return r;
  }

  async function matchesList() {
    const res = await occ('GET', '/products/search', {
      auth: false,
      query: { query: `:relevance:allCategories:${MATCHES_CATEGORY}`, pageSize: 50, fields: 'products(code,name,summary,price(value),stock(stockLevelStatus))' },
    });
    return (res.products ?? []).map((p) => {
      const [title = '', dia = '', hora = ''] = clean(p.name).split(' · ');
      return { code: p.code, jogo: title, data: [dia, hora].filter(Boolean).join(' '), detalhes: clean(p.summary), aPartirDe: brl(p.price?.value), esgotado: p.stock?.stockLevelStatus === 'outOfStock' };
    }).sort((a, b) => a.code.localeCompare(b.code));
  }

  async function matchDetail(code) {
    const p = await occ('GET', `/products/${enc(code)}`, { auth: false, query: { fields: 'FULL' } });
    const sectors = (p.variantMatrix ?? []).filter((el) => el.variantOption?.code).map((el) => {
      const seats = (el.elements ?? []).filter((c) => c.variantOption?.code).map((c) => ({
        code: c.variantOption.code,
        poltrona: c.variantValueCategory?.name,
        disponivel: c.variantOption?.stock?.stockLevelStatus !== 'outOfStock' && (c.variantOption?.stock?.stockLevel ?? 1) > 0,
        preco: c.variantOption?.priceData?.value,
      }));
      const key = (seats[0]?.code ?? el.variantOption.code).split('-').slice(-2, -1)[0] ?? '';
      return { setor: el.variantValueCategory?.name ?? key, chave: key, preco: seats[0]?.preco ?? p.price?.value, poltronas: seats };
    });
    const [title = '', dia = '', hora = ''] = clean(p.name).split(' · ');
    return { code: p.code, jogo: title, data: [dia, hora].filter(Boolean).join(' '), detalhes: clean(p.summary), setores: sectors };
  }

  return [
    {
      name: 'entrar_como',
      description: 'Entra na loja como outro torcedor (e-mail e senha da conta da loja). A sessão anterior é descartada.',
      inputSchema: obj({ email: S.str('E-mail da conta na loja'), senha: S.str('Senha da conta na loja') }, ['email', 'senha']),
      handler: async ({ email, senha }) => {
        session = { email, password: senha, token: null, uid: null };
        await login();
        const me = await occ('GET', '/users/current', { query: { fields: 'name,uid' } });
        return { ok: true, torcedor: me.name, email: me.uid };
      },
    },
    {
      name: 'minha_conta',
      description: 'Dados do torcedor logado: nome, e-mail e situação no sócio-torcedor (plano, onda de compra, descontos, benefícios suspensos por mensalidade em atraso).',
      inputSchema: obj(),
      handler: async () => {
        const me = await occ('GET', '/users/current', { query: { fields: 'name,uid' } });
        const m = await membership();
        const tickets = await occ('GET', '/users/current/tickets', { query: { fields: 'tickets(id,subject,status(id),createdAt)', pageSize: 50 } }).catch(() => ({}));
        const cancel = (tickets.tickets ?? []).filter((t) => (t.subject ?? '').startsWith(CANCEL_SUBJECT)).sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))[0];
        const situacao = m.member
          ? (cancel && cancel.status?.id !== 'CLOSED' ? 'Ativo · cancelamento solicitado'
            : m.suspended ? 'Benefícios suspensos (mensalidade em atraso): compra na venda geral, sem desconto de sócio' : 'Ativo')
          : cancel?.status?.id === 'CLOSED' ? 'Inativo (plano cancelado)' : m.inCart ? 'Plano no carrinho (ainda não é sócio)' : 'Não é sócio';
        return {
          torcedor: me.name, email: me.uid,
          socioTorcedor: {
            situacao, plano: publicPlan(m.member), planoNoCarrinho: m.inCart?.nome ?? null,
            chamadoCancelamento: cancel ? { id: cancel.id, situacao: cancel.status?.id, aberto: fmtDate(cancel.createdAt) } : null,
            ...(m.suspended ? { comoRegularizar: 'Em Minha conta › Meu sócio-torcedor, botão "Regularizar agora": cadastrar um cartão novo cobra a mensalidade em atraso e devolve os benefícios na hora.' } : {}),
          },
          areaDoSocio: `${cfg.storeUrl}/my-account/socio-torcedor`,
        };
      },
    },
    {
      name: 'planos_socio',
      description: 'Planos de sócio-torcedor à venda: mensalidade, onda de prioridade, % de desconto no ingresso e na loja, convidados e benefícios.',
      inputSchema: obj(),
      handler: async () => ({ planos: (await plans()).map(publicPlan) }),
    },
    {
      name: 'assinar_plano',
      description: 'Coloca um plano de sócio (e convidados) no carrinho, trocando qualquer outro plano que esteja lá. Depois use finalizar_compra.',
      inputSchema: obj({ plano: S.str('Código do plano (de planos_socio)'), convidados: S.int('Quantos convidados (0 se nenhum)'), confirmar: S.confirm }, ['plano']),
      handler: async (a) => {
        const plan = (await plans()).find((p) => p.code === a.plano);
        if (!plan) throw new UserError('Plano não encontrado. Veja os códigos em planos_socio.');
        const guests = Math.max(0, Math.min(Number(a.convidados ?? 0), plan.maxConvidados));
        const c = needConfirm(a, `Colocar no carrinho o plano ${plan.nome} (${plan.mensalidadeTexto}/mês)` + (guests ? ` com ${guests} convidado(s) (${plan.convidado?.mensalidade}/mês cada)` : ''));
        if (c) return c;
        const id = await cartId();
        const cart = await occ('GET', `/users/current/carts/${enc(id)}`, { query: { fields: 'entries(entryNumber,product(code))' } });
        const socioCodes = new Set((await plans()).flatMap((p) => [p.code, p.convidado?.code].filter(Boolean)));
        for (const e of [...(cart.entries ?? [])].sort((x, y) => y.entryNumber - x.entryNumber)) {
          if (socioCodes.has(e.product?.code)) await occ('DELETE', `/users/current/carts/${enc(id)}/entries/${e.entryNumber}`);
        }
        await addEntry(id, plan.code, 1);
        if (guests && plan.convidado) await addEntry(id, plan.convidado.code, guests);
        plansCache = null;
        return { executado: true, ...(await cartView(id)) };
      },
    },
    {
      name: 'pedir_cancelamento_plano',
      description: 'Pede o cancelamento do plano de sócio: abre um chamado para o atendimento, que confirma o cancelamento. Motivos: price (valor alto), games (não vai aos jogos), moving (mudança), service (atendimento), other.',
      inputSchema: obj({ motivo: { type: 'string', enum: Object.keys(CANCEL_REASONS) }, comentario: S.str('Comentário opcional do torcedor'), confirmar: S.confirm }, ['motivo']),
      handler: async (a) => {
        const m = await membership();
        if (!m.member) throw new UserError('O torcedor não é sócio ativo, não há plano para cancelar.');
        const tickets = await occ('GET', '/users/current/tickets', { query: { fields: 'tickets(id,subject,status(id))', pageSize: 50 } });
        const open = (tickets.tickets ?? []).find((t) => (t.subject ?? '').startsWith(CANCEL_SUBJECT) && t.status?.id !== 'CLOSED');
        if (open) throw new UserError(`Já existe um pedido de cancelamento em aberto (chamado ${open.id}).`);
        const c = needConfirm(a, `Pedir o cancelamento do plano ${m.member.nome}. Motivo: ${CANCEL_REASONS[a.motivo]}` + (a.comentario ? `. Comentário: ${a.comentario}` : ''));
        if (c) return c;
        const orders = await occ('GET', '/users/current/orders', { query: { fields: 'orders(code,placed)', pageSize: 20 } });
        let since = null;
        for (const o of orders.orders ?? []) {
          const d = await occ('GET', `/users/current/orders/${enc(o.code)}`, { query: { fields: 'created,entries(product(code))' } });
          if ((d.entries ?? []).some((e) => e.product?.code === m.member.code)) since = d.created;
        }
        const message = [`Motivo: ${CANCEL_REASONS[a.motivo]}`, a.comentario || null, `${m.member.nome} (${m.member.code}) · Sócio desde ${fmtDate(since, false) ?? '—'}`].filter(Boolean).join('\n');
        const t = await occ('POST', '/users/current/tickets', { body: { subject: `${CANCEL_SUBJECT} ${m.member.nome}`, message, ticketCategory: { id: 'ENQUIRY' } } });
        return { executado: true, chamado: t.id, situacao: 'Cancelamento solicitado: o atendimento vai confirmar. Até lá o torcedor continua sócio.', acompanhar: `${cfg.storeUrl}/my-account/support-tickets` };
      },
    },
    {
      name: 'proximos_jogos',
      description: 'Próximos jogos do Flamengo em casa com ingressos à venda (data, competição, preço a partir de).',
      inputSchema: obj(),
      handler: async () => ({ jogos: await matchesList() }),
    },
    {
      name: 'detalhes_jogo',
      description: 'Setores de um jogo com preço da inteira, preço com o desconto de sócio do torcedor logado e poltronas livres.',
      inputSchema: obj({ jogo: S.str('Código do jogo (de proximos_jogos)') }, ['jogo']),
      handler: async ({ jogo }) => {
        const d = await matchDetail(jogo);
        const m = await membership().catch(() => ({}));
        const disc = m.member?.descontoIngresso ?? 0;
        return {
          ...d,
          setores: d.setores.map((s) => {
            const livres = s.poltronas.filter((x) => x.disponivel);
            return { setor: s.setor, chave: s.chave, inteira: brl(s.preco), comDescontoDeSocio: disc ? `${brl(s.preco * (1 - disc / 100))} (${disc}% ${m.member.nome})` : null, poltronasLivres: livres.length, exemplos: livres.slice(0, 6).map((x) => x.poltrona) };
          }),
          observacao: disc ? 'O desconto de sócio é aplicado no carrinho.' : 'O torcedor não tem desconto de sócio.',
          comprarNaLoja: `${cfg.storeUrl}/matchday`,
        };
      },
    },
    {
      name: 'comprar_ingressos',
      description: 'Coloca ingressos de um jogo no carrinho: escolhe poltronas livres do setor (ou as poltronas informadas). Depois use finalizar_compra.',
      inputSchema: obj({ jogo: S.str('Código do jogo'), setor: S.str('Setor: NORTE, SUL, LESTE ou OESTE'), quantidade: S.int('Quantos ingressos (1 a 6)'), poltronas: { type: 'array', items: { type: 'string' }, description: 'Opcional: poltronas desejadas, ex. ["D04","D05"]' }, confirmar: S.confirm }, ['jogo', 'setor', 'quantidade']),
      handler: async (a) => {
        const d = await matchDetail(a.jogo);
        const sector = d.setores.find((s) => s.chave.toUpperCase() === String(a.setor).toUpperCase() || clean(s.setor).toUpperCase() === String(a.setor).toUpperCase());
        if (!sector) throw new UserError(`Setor não encontrado. Setores: ${d.setores.map((s) => s.chave).join(', ')}`);
        const qty = Math.max(1, Math.min(6, Number(a.quantidade)));
        const free = sector.poltronas.filter((x) => x.disponivel);
        let chosen = a.poltronas?.length ? free.filter((x) => a.poltronas.map((p) => p.toUpperCase()).includes(String(x.poltrona).toUpperCase())) : free.slice(0, qty);
        if (chosen.length < qty) throw new UserError(`Só há ${chosen.length} poltrona(s) disponível(is) com esse critério no setor ${sector.chave}.`);
        chosen = chosen.slice(0, qty);
        const c = needConfirm(a, `${qty} ingresso(s) para ${d.jogo} (${d.data}), setor ${sector.setor}, poltronas ${chosen.map((x) => x.poltrona).join(', ')} — ${brl(sector.preco)} cada na inteira (desconto de sócio aplicado no carrinho).`);
        if (c) return c;
        const id = await cartId();
        const results = [];
        for (const seat of chosen) {
          const r = await addEntry(id, seat.code, 1);
          results.push({ poltrona: seat.poltrona, status: r.statusCode });
        }
        return { executado: true, adicionados: results, ...(await cartView(id)) };
      },
    },
    {
      name: 'servicos_matchday',
      description: 'Serviços do dia de jogo à venda (estacionamento, combos de comida e bebida, experiências).',
      inputSchema: obj(),
      handler: async () => {
        const res = await occ('GET', '/products/search', { auth: false, query: { query: `:relevance:allCategories:${SERVICES_CATEGORY}`, pageSize: 50, fields: 'products(code,name,summary,price(value),stock(stockLevelStatus))' } });
        return { servicos: (res.products ?? []).map((p) => ({ code: p.code, nome: clean(p.name), descricao: clean(p.summary), preco: brl(p.price?.value), esgotado: p.stock?.stockLevelStatus === 'outOfStock' })) };
      },
    },
    {
      name: 'buscar_produtos',
      description: 'Busca produtos na loja oficial (mantos, roupas, acessórios...) com preço.',
      inputSchema: obj({ texto: S.str('O que procurar, ex. "manto jogo 1"'), quantidade: S.int('Máximo de resultados (padrão 10)') }, ['texto']),
      handler: async ({ texto, quantidade }) => {
        const res = await occ('GET', '/products/search', { auth: false, query: { query: texto, pageSize: Math.min(20, quantidade ?? 10), fields: 'products(code,name,price(value),stock(stockLevelStatus)),pagination(totalResults)' } });
        return { total: res.pagination?.totalResults, produtos: (res.products ?? []).map((p) => ({ code: p.code, nome: clean(p.name), preco: brl(p.price?.value), disponivel: p.stock?.stockLevelStatus !== 'outOfStock', link: `${cfg.storeUrl}/p/${p.code}` })) };
      },
    },
    {
      name: 'detalhes_produto',
      description: 'Detalhes de um produto: descrição, preço, estoque e opções (tamanhos) com o código de cada uma para colocar no carrinho.',
      inputSchema: obj({ codigo: S.str('Código do produto') }, ['codigo']),
      handler: async ({ codigo }) => {
        const p = await occ('GET', `/products/${enc(codigo)}`, { auth: false, query: { fields: 'code,name,summary,description,price(value),stock(stockLevel,stockLevelStatus),baseOptions(options(code,stock(stockLevelStatus),priceData(value),variantOptionQualifiers(name,value))),variantOptions(code,stock(stockLevelStatus),priceData(value),variantOptionQualifiers(name,value)),purchasable' } });
        const opts = [...(p.variantOptions ?? []), ...(p.baseOptions ?? []).flatMap((b) => b.options ?? [])];
        return {
          code: p.code, nome: clean(p.name), resumo: clean(p.summary), descricao: clean(p.description).slice(0, 600),
          preco: brl(p.price?.value), compravel: p.purchasable !== false, estoque: p.stock?.stockLevelStatus,
          opcoes: opts.map((o) => ({ code: o.code, opcao: (o.variantOptionQualifiers ?? []).map((q) => `${q.name}: ${q.value}`).join(', '), preco: brl(o.priceData?.value), disponivel: o.stock?.stockLevelStatus !== 'outOfStock' })),
          link: `${cfg.storeUrl}/p/${p.code}`,
        };
      },
    },
    {
      name: 'ver_carrinho',
      description: 'Carrinho atual do torcedor: itens, cupons, promoções (descontos de sócio) e total.',
      inputSchema: obj(),
      handler: async () => {
        const id = await cartId(false);
        return id ? cartView(id) : { carrinho: null, itens: [], aviso: 'Carrinho vazio.' };
      },
    },
    {
      name: 'adicionar_ao_carrinho',
      description: 'Coloca um produto da loja ou serviço do matchday no carrinho (para ingresso use comprar_ingressos).',
      inputSchema: obj({ codigo: S.str('Código do produto (ou da opção/tamanho)'), quantidade: S.int('Quantidade'), confirmar: S.confirm }, ['codigo', 'quantidade']),
      handler: async (a) => {
        const c = needConfirm(a, `Colocar ${a.quantidade}x ${a.codigo} no carrinho.`);
        if (c) return c;
        const id = await cartId();
        const r = await addEntry(id, a.codigo, Math.max(1, Number(a.quantidade)));
        return { executado: true, status: r.statusCode, mensagem: r.statusMessage ?? null, ...(await cartView(id)) };
      },
    },
    {
      name: 'alterar_quantidade',
      description: 'Muda a quantidade de um item do carrinho (0 remove o item).',
      inputSchema: obj({ entrada: S.int('Número da entrada (de ver_carrinho)'), quantidade: S.int('Nova quantidade; 0 remove'), confirmar: S.confirm }, ['entrada', 'quantidade']),
      handler: async (a) => {
        const c = needConfirm(a, a.quantidade > 0 ? `Mudar a entrada ${a.entrada} para ${a.quantidade} unidade(s).` : `Remover a entrada ${a.entrada} do carrinho.`);
        if (c) return c;
        const id = await cartId(false);
        if (!id) throw new UserError('Carrinho vazio.');
        if (a.quantidade > 0) await occ('PATCH', `/users/current/carts/${enc(id)}/entries/${a.entrada}`, { body: { quantity: a.quantidade } });
        else await occ('DELETE', `/users/current/carts/${enc(id)}/entries/${a.entrada}`);
        return { executado: true, ...(await cartView(id)) };
      },
    },
    {
      name: 'aplicar_cupom',
      description: 'Aplica um cupom de desconto no carrinho (ex.: mengao).',
      inputSchema: obj({ cupom: S.str('Código do cupom'), confirmar: S.confirm }, ['cupom']),
      handler: async (a) => {
        const c = needConfirm(a, `Aplicar o cupom ${a.cupom} no carrinho.`);
        if (c) return c;
        const id = await cartId(false);
        if (!id) throw new UserError('Carrinho vazio.');
        await occ('POST', `/users/current/carts/${enc(id)}/vouchers`, { query: { voucherId: a.cupom } });
        return { executado: true, ...(await cartView(id)) };
      },
    },
    {
      name: 'remover_cupom',
      description: 'Tira um cupom do carrinho.',
      inputSchema: obj({ cupom: S.str('Código do cupom'), confirmar: S.confirm }, ['cupom']),
      handler: async (a) => {
        const c = needConfirm(a, `Remover o cupom ${a.cupom} do carrinho.`);
        if (c) return c;
        const id = await cartId(false);
        if (!id) throw new UserError('Carrinho vazio.');
        await occ('DELETE', `/users/current/carts/${enc(id)}/vouchers/${enc(a.cupom)}`);
        return { executado: true, ...(await cartView(id)) };
      },
    },
    {
      name: 'finalizar_compra',
      description: 'Finaliza a compra do carrinho com o endereço e o cartão padrão do torcedor (sem pedir dados de cartão). Mostra o resumo antes; só compra com confirmar=true.',
      inputSchema: obj({ confirmar: S.confirm }),
      handler: async (a) => {
        const id = await cartId(false);
        if (!id) throw new UserError('Carrinho vazio.');
        const view = await cartView(id);
        if (!view.itens.length) throw new UserError('Carrinho vazio.');
        const addresses = (await occ('GET', '/users/current/addresses', { query: { fields: 'addresses(id,defaultAddress,town,line1)' } })).addresses ?? [];
        const address = addresses.find((x) => x.defaultAddress) ?? addresses[0];
        const cards = (await occ('GET', '/users/current/paymentdetails', { query: { saved: true, fields: 'payments(id,defaultPayment,cardNumber,cardType(name))' } })).payments ?? [];
        const card = cards.find((x) => x.defaultPayment) ?? cards[0];
        if (!address) throw new UserError('O torcedor não tem endereço cadastrado.');
        if (!card) throw new UserError('O torcedor não tem cartão salvo.');
        const c = needConfirm(a, { itens: view.itens.map((i) => `${i.quantidade}x ${i.produto} (${i.total})`), total: view.total, entrega: `${address.line1 ?? ''}, ${address.town ?? ''}`, cartao: `${card.cardType?.name ?? 'Cartão'} final ${lastDigits(card.cardNumber)}` });
        if (c) return c;
        await occ('PUT', `/users/current/carts/${enc(id)}/addresses/delivery`, { query: { addressId: address.id } });
        const modes = (await occ('GET', `/users/current/carts/${enc(id)}/deliverymodes`, { query: { fields: 'deliveryModes(code,deliveryCost(value))' } })).deliveryModes ?? [];
        if (modes.length) {
          const mode = [...modes].sort((x, y) => (x.deliveryCost?.value ?? 0) - (y.deliveryCost?.value ?? 0))[0];
          await occ('PUT', `/users/current/carts/${enc(id)}/deliverymode`, { query: { deliveryModeId: mode.code } });
        }
        await occ('PUT', `/users/current/carts/${enc(id)}/paymentdetails`, { query: { paymentDetailsId: card.id } });
        const order = await occ('POST', '/users/current/orders', { query: { cartId: id, termsChecked: true, fields: 'code,totalPriceWithTax(formattedValue),statusDisplay' } });
        plansCache = null;
        return { executado: true, pedido: order.code, total: order.totalPriceWithTax?.formattedValue, situacao: order.statusDisplay, verNaLoja: `${cfg.storeUrl}/my-account/order/${order.code}` };
      },
    },
    {
      name: 'meus_pedidos',
      description: 'Pedidos do torcedor (código, data, situação, total).',
      inputSchema: obj(),
      handler: async () => {
        const r = await occ('GET', '/users/current/orders', { query: { fields: 'orders(code,placed,statusDisplay,total(formattedValue))', pageSize: 20 } });
        return { pedidos: (r.orders ?? []).map((o) => ({ pedido: o.code, data: fmtDate(o.placed), situacao: o.statusDisplay, total: o.total?.formattedValue })) };
      },
    },
    {
      name: 'detalhe_pedido',
      description: 'Itens, descontos, entrega e pagamento de um pedido do torcedor.',
      inputSchema: obj({ pedido: S.str('Código do pedido') }, ['pedido']),
      handler: async ({ pedido }) => {
        const o = await occ('GET', `/users/current/orders/${enc(pedido)}`, { query: { fields: 'FULL' } });
        return {
          pedido: o.code, data: fmtDate(o.created), situacao: o.statusDisplay,
          itens: (o.entries ?? []).map((e) => ({ produto: clean(e.product?.name), codigo: e.product?.code, quantidade: e.quantity, total: e.totalPrice?.formattedValue })),
          descontos: o.totalDiscounts?.formattedValue, total: o.totalPriceWithTax?.formattedValue,
          cupons: (o.appliedVouchers ?? []).map((v) => v.voucherCode || v.code),
          pagamento: o.paymentInfo ? `${o.paymentInfo.cardType?.name ?? 'Cartão'} final ${lastDigits(o.paymentInfo.cardNumber)}` : null,
          verNaLoja: `${cfg.storeUrl}/my-account/order/${o.code}`,
        };
      },
    },
    {
      name: 'meus_chamados',
      description: 'Chamados de atendimento do torcedor e a conversa de cada um.',
      inputSchema: obj(),
      handler: async () => {
        const r = await occ('GET', '/users/current/tickets', { query: { fields: 'tickets(id,subject,status(id,name),createdAt,modifiedAt,ticketCategory(id))', pageSize: 50 } });
        const list = [];
        for (const t of r.tickets ?? []) {
          const d = await occ('GET', `/users/current/tickets/${enc(t.id)}`, { query: { fields: 'ticketEvents(message,createdAt,author)' } }).catch(() => ({}));
          list.push({ chamado: t.id, assunto: t.subject, situacao: t.status?.name ?? t.status?.id, aberto: fmtDate(t.createdAt), mensagens: (d.ticketEvents ?? []).map((e) => ({ data: fmtDate(e.createdAt), autor: e.author, texto: e.message })) });
        }
        return { chamados: list };
      },
    },
    {
      name: 'abrir_chamado',
      description: 'Abre um chamado de atendimento (dúvida, reclamação ou problema). Para cancelar o plano use pedir_cancelamento_plano.',
      inputSchema: obj({ assunto: S.str('Assunto'), mensagem: S.str('Mensagem'), categoria: { type: 'string', enum: ['ENQUIRY', 'COMPLAINT', 'PROBLEM'], description: 'ENQUIRY dúvida, COMPLAINT reclamação, PROBLEM problema' }, confirmar: S.confirm }, ['assunto', 'mensagem']),
      handler: async (a) => {
        const c = needConfirm(a, `Abrir chamado "${a.assunto}": ${a.mensagem}`);
        if (c) return c;
        const t = await occ('POST', '/users/current/tickets', { body: { subject: a.assunto, message: a.mensagem, ticketCategory: { id: a.categoria ?? 'ENQUIRY' } } });
        return { executado: true, chamado: t.id };
      },
    },
    {
      name: 'meus_cartoes_e_enderecos',
      description: 'Cartões salvos (só os 4 últimos dígitos) e endereços do torcedor.',
      inputSchema: obj(),
      handler: async () => {
        const cards = (await occ('GET', '/users/current/paymentdetails', { query: { saved: true, fields: 'payments(defaultPayment,cardNumber,cardType(name),expiryMonth,expiryYear)' } })).payments ?? [];
        const addresses = (await occ('GET', '/users/current/addresses', { query: { fields: 'addresses(defaultAddress,town,region(isocodeShort),postalCode)' } })).addresses ?? [];
        return {
          cartoes: cards.map((c) => ({ cartao: `${c.cardType?.name ?? 'Cartão'} final ${lastDigits(c.cardNumber)}`, validade: `${c.expiryMonth}/${c.expiryYear}`, padrao: !!c.defaultPayment })),
          enderecos: addresses.map((x) => ({ cidade: x.town, uf: x.region?.isocodeShort, cep: x.postalCode, padrao: !!x.defaultAddress })),
        };
      },
    },
  ];
}

// ================================================================ GESTORES (OData)

function odataClient(cfg, http) {
  const auth = 'Basic ' + btoa(`${cfg.gestorUsuario}:${cfg.gestorSenha}`);
  return async function odata(method, path, { body, query } = {}) {
    if (!cfg.gestorUsuario || !cfg.gestorSenha) throw new UserError('Usuário e senha do gestor não configurados no MCP.');
    const r = await http({
      base: 'odata', method, path, query, body,
      headers: { Authorization: auth, Accept: 'application/json', 'Accept-Language': 'pt', 'Content-Language': 'pt', ...(body ? { Prefer: 'return=representation' } : {}) },
    });
    if (r.status === 401) throw new UserError('O Commerce recusou o login do gestor (usuário ou senha).');
    if (r.status === 403) throw new UserError('O Commerce negou: este gestor não tem permissão para essa operação.');
    if (r.status >= 400) {
      const m = r.json?.error?.message?.value ?? r.text?.slice(0, 300);
      throw new UserError(`O Commerce recusou (${r.status}): ${m}`);
    }
    const d = r.json?.d;
    return d?.results ?? d ?? r.json ?? {};
  };
}

const results = (x) => (Array.isArray(x) ? x : x?.results ?? []);

function socioTools(cfg, http) {
  const odata = odataClient(cfg, http);

  async function planList() {
    const ps = await odata('GET', '/FlamengoPlano/SocioPlans', { query: { $expand: 'status,customerGroup/members,pendingGroup/members', $top: 100 } });
    return results(ps).map((p) => {
      const members = results(p.customerGroup?.members).map((m) => m.uid);
      const pending = new Set(results(p.pendingGroup?.members).map((m) => m.uid));
      const real = members.filter((u) => !pending.has(u));
      return {
        code: p.code, nome: p.name, programa: p.family, situacao: p.status?.code,
        mensalidade: brl(p.monthlyPrice), descontoIngresso: p.ticketDiscount, descontoLoja: p.storeDiscount,
        onda: p.wave > 0 ? p.wave : 'venda geral', maxConvidados: p.maxGuests, mensalidadeConvidado: brl(p.guestPrice),
        vagas: p.slots ?? 'sem limite', sociosNaLoja: real.length, vagasRestantes: p.slots ? Math.max(0, p.slots - real.length) : null,
        planoNoCarrinho: pending.size, baseSimuladaDemo: p.demoBase, avisoPublicacao: p.statusMessage || null,
        _members: real,
      };
    });
  }

  async function tickets() {
    const ts = await odata('GET', '/FlamengoChamado/CsTickets', { query: { $expand: 'state,customer,category', $top: 200 } });
    return results(ts).map((t) => ({ id: t.ticketID, assunto: t.headline, situacao: t.state?.code, categoria: t.category?.code, torcedor: t.customer?.name, email: t.customer?.uid, aberto: toDate(t.creationtime), atualizado: toDate(t.modifiedtime) }))
      .sort((a, b) => (b.aberto?.getTime() ?? 0) - (a.aberto?.getTime() ?? 0));
  }

  async function message(id) {
    const ev = await odata('GET', '/FlamengoMensagem/CsTicketEvents', { query: { $filter: `code eq '${id}'` } });
    return results(ev)[0]?.text ?? null;
  }

  async function cancellations() {
    const list = (await tickets()).filter((t) => (t.assunto ?? '').startsWith(CANCEL_SUBJECT));
    for (const t of list) Object.assign(t, parseCancelMessage(await message(t.id)), { plano: t.assunto.slice(CANCEL_SUBJECT.length).trim() });
    return list;
  }

  const typeName = { ADESAO: 'Adesão', SUSPENSAO: 'Suspensão', REGULARIZACAO: 'Regularização', CANCELAMENTO: 'Cancelamento', OBSERVACAO: 'Observação' };

  async function customer(email) {
    const cs = results(await odata('GET', '/FlamengoTorcedor/Customers', { query: { $filter: `uid eq '${String(email).toLowerCase().replace(/'/g, "''")}'`, $expand: 'groups' } }));
    if (!cs.length) throw new UserError('Torcedor não encontrado.');
    return cs[0];
  }

  async function events(uid) {
    const es = await odata('GET', '/FlamengoEventoSocio/SocioEvents', { query: { $filter: `customer/uid eq '${uid.replace(/'/g, "''")}'`, $expand: 'type,plan', $top: 200 } });
    return results(es).map((e) => ({ data: toDate(e.date), tipo: typeName[e.type?.code] ?? e.type?.code, plano: e.plan?.name ?? e.plan?.code ?? null, origem: e.origin, registradoPor: e.author, motivo: e.note, resultado: e.result }))
      .sort((a, b) => (a.data?.getTime() ?? 0) - (b.data?.getTime() ?? 0))
      .map((e) => ({ ...e, data: fmtDate(e.data) }));
  }

  /** Cria Suspensão ou Regularização: o Commerce aplica na hora (SocioEventInterceptor) */
  async function statusEvent(a, type) {
    const c = await customer(a.email);
    const plan = (await planList()).find((p) => p._members.includes(c.uid));
    if (!plan) throw new UserError(`${c.uid} não é sócio.`);
    const suspended = results(c.groups).some((g) => g.uid === SUSPENDED_GROUP);
    if (type === 'SUSPENSAO' && suspended) throw new UserError(`${c.name} já está com os benefícios suspensos.`);
    if (type === 'REGULARIZACAO' && !suspended) throw new UserError(`${c.name} não está suspenso.`);
    const summary = type === 'SUSPENSAO'
      ? `Suspender os benefícios de ${c.name} (${c.uid}), sócio ${plan.nome}: perde a onda de sócio e os descontos na hora (continua sócio e mantém a vaga). Motivo: ${a.motivo ?? 'mensalidade em atraso'}.`
      : `Regularizar ${c.name} (${c.uid}), sócio ${plan.nome}: onda de sócio e descontos voltam na hora. Motivo: ${a.motivo ?? 'pagamento confirmado'}.`;
    const conf = needConfirm(a, summary);
    if (conf) return conf;
    const ev = await odata('POST', '/FlamengoEventoSocio/SocioEvents', {
      body: { customer: { uid: c.uid }, date: `/Date(${Date.now()})/`, type: { code: type }, origin: 'Claude', note: a.motivo ?? (type === 'SUSPENSAO' ? 'Mensalidade em atraso' : 'Pagamento confirmado') },
    });
    return { executado: true, torcedor: c.name, email: c.uid, plano: plan.nome, resultado: ev?.result ?? null, linhaDoTempo: await events(c.uid) };
  }

  /** Tentativas de pôr ingresso no carrinho (SocioEligibilityDecision), mais nova primeiro */
  async function decisions(uid) {
    const query = { $expand: 'customer,product,plan', $top: 200 };
    if (uid) query.$filter = `customer/uid eq '${uid.replace(/'/g, "''")}'`;
    const ds = await odata('GET', '/FlamengoElegibilidade/SocioEligibilityDecisions', { query });
    const lines = (t) => String(t ?? '').split('\n').map((x) => x.trim()).filter(Boolean);
    return results(ds)
      .map((d) => ({
        data: toDate(d.date), torcedor: d.customer?.name, email: d.customer?.uid, jogo: d.gameCode,
        poltrona: d.product?.name ?? d.product?.code ?? null,
        decisao: d.eligible ? 'aprovada' : d.blocked ? 'negada e recusada no carrinho' : 'negada (só observação: a compra seguiu)',
        motivos: lines(d.reasons), condicoes: lines(d.checks), plano: d.plan?.name ?? null,
        ingressosQueJaTinha: d.tickets, limitePorJogo: d.ticketLimit,
        _negada: !d.eligible,
      }))
      .sort((a, b) => (b.data?.getTime() ?? 0) - (a.data?.getTime() ?? 0))
      .map((d) => ({ ...d, data: fmtDate(d.data) }));
  }

  /** Convidados nominais (FlamengoConvidado) e ingressos marcados para eles (FlamengoPedido, ticketHolder) */
  async function guests(uid) {
    const query = { $expand: 'customer,plan', $top: 500 };
    if (uid) query.$filter = `customer/uid eq '${uid.replace(/'/g, "''")}'`;
    const gs = results(await odata('GET', '/FlamengoConvidado/SocioGuests', { query }));
    const mask = (cpf) => (String(cpf ?? '').length === 11 ? `***.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-**` : '***');
    return gs.map((g) => ({
      convidado: g.name, cpf: mask(g.cpf), titular: g.customer?.name, email: g.customer?.uid,
      situacao: g.active ? 'ativo' : `encerrado em ${fmtDate(toDate(g.until))} (${g.endReason ?? 'sem motivo'})`,
      desde: fmtDate(toDate(g.since)), plano: g.plan?.name ?? null, _ativo: !!g.active,
    }));
  }

  async function guestTickets(uid) {
    const query = { $expand: 'user,status,entries/product', $top: 500 };
    if (uid) query.$filter = `user/uid eq '${uid.replace(/'/g, "''")}'`;
    const os = results(await odata('GET', '/FlamengoPedido/Orders', { query })).filter((o) => o.versionID == null && !/CANCEL/.test(o.status?.code ?? ''));
    const out = [];
    for (const o of os) {
      for (const e of results(o.entries)) {
        const code = e.product?.code ?? '';
        if (!/^JOGO-\d{8}-/.test(code)) continue;
        out.push({
          jogo: code.split('-').slice(0, 3).join('-'), poltrona: e.product?.name ?? code, codigoPoltrona: code,
          quemVai: e.ticketHolder || `${o.user?.name ?? o.user?.uid} (titular)`, convidado: !!e.ticketHolder,
          pedido: o.code, data: fmtDate(toDate(o.date)), titular: o.user?.name, email: o.user?.uid,
        });
      }
    }
    return out;
  }

  const showTicket = (t) => ({ ...t, aberto: fmtDate(t.aberto), atualizado: fmtDate(t.atualizado), situacao: t.situacao === 'Closed' ? 'Fechado' : t.situacao === 'Open' ? 'Em atendimento' : 'Novo' });

  async function orders() {
    const os = await odata('GET', '/FlamengoPedido/Orders', { query: { $expand: 'status,user,entries/product', $top: 500 } });
    return results(os).filter((o) => o.versionID == null).map((o) => ({ pedido: o.code, data: toDate(o.date), torcedor: o.user?.name, email: o.user?.uid, total: num(o.totalPrice), descontos: num(o.totalDiscounts), situacao: o.status?.code, itens: results(o.entries).map((e) => ({ codigo: e.product?.code, produto: e.product?.name, quantidade: num(e.quantity), total: num(e.totalPrice) })) }));
  }

  return [
    {
      name: 'planos',
      description: 'Planos de sócio-torcedor: situação (à venda/rascunho/fora da loja), mensalidade, descontos, onda, convidados, vagas e quantos sócios reais cada plano tem.',
      inputSchema: obj(),
      handler: async () => ({ planos: (await planList()).map(({ _members, ...p }) => p) }),
    },
    {
      name: 'socios_do_plano',
      description: 'Lista os sócios (torcedores) de um plano.',
      inputSchema: obj({ plano: S.str('Código do plano') }, ['plano']),
      handler: async ({ plano }) => {
        const p = (await planList()).find((x) => x.code === plano);
        if (!p) throw new UserError('Plano não encontrado.');
        return { plano: p.nome, socios: p._members };
      },
    },
    {
      name: 'atualizar_plano',
      description: 'Altera um plano (mensalidade, descontos, onda, convidados, vagas, situação). Ao salvar, o Commerce atualiza o produto e as promoções da loja sozinho.',
      inputSchema: obj({
        plano: S.str('Código do plano'), mensalidade: S.num('Nova mensalidade em R$'), descontoIngresso: S.int('% de desconto no ingresso (0-100)'),
        descontoLoja: S.int('% de desconto na loja (0-100)'), onda: S.int('Onda de compra (1, 2, 3; 0 = venda geral)'), maxConvidados: S.int('Máximo de convidados'),
        mensalidadeConvidado: S.num('Mensalidade de cada convidado em R$'), vagas: S.int('Vagas do plano (0 = sem limite)'),
        situacao: { type: 'string', enum: ['ON_SALE', 'DRAFT', 'OFF_SALE'], description: 'ON_SALE à venda, DRAFT rascunho, OFF_SALE fora da loja' }, confirmar: S.confirm,
      }, ['plano']),
      handler: async (a) => {
        const p = (await planList()).find((x) => x.code === a.plano);
        if (!p) throw new UserError('Plano não encontrado.');
        const body = { code: a.plano };
        const changes = [];
        const set = (field, value, label, show = value) => { if (value != null) { body[field] = value; changes.push(`${label}: ${show}`); } };
        set('monthlyPrice', a.mensalidade != null ? String(a.mensalidade) : null, 'mensalidade', brl(a.mensalidade));
        set('ticketDiscount', a.descontoIngresso, 'desconto no ingresso', `${a.descontoIngresso}%`);
        set('storeDiscount', a.descontoLoja, 'desconto na loja', `${a.descontoLoja}%`);
        set('wave', a.onda, 'onda');
        set('maxGuests', a.maxConvidados, 'máximo de convidados');
        set('guestPrice', a.mensalidadeConvidado != null ? String(a.mensalidadeConvidado) : null, 'mensalidade do convidado', brl(a.mensalidadeConvidado));
        set('slots', a.vagas, 'vagas');
        if (a.situacao) { body.status = { code: a.situacao }; changes.push(`situação: ${a.situacao}`); }
        if (!changes.length) throw new UserError('Nada para alterar.');
        const c = needConfirm(a, `Alterar o plano ${p.nome}: ${changes.join('; ')}`);
        if (c) return c;
        await odata('PATCH', `/FlamengoPlano/SocioPlans('${enc(a.plano)}')`, { body });
        const after = (await planList()).find((x) => x.code === a.plano);
        return { executado: true, plano: after && (({ _members, ...x }) => x)(after), observacao: 'O Commerce republica produto e promoções em segundo plano; a loja reflete em instantes.' };
      },
    },
    {
      name: 'buscar_torcedor',
      description: 'Ficha de um torcedor: plano de sócio, pedidos e chamados.',
      inputSchema: obj({ email: S.str('E-mail do torcedor') }, ['email']),
      handler: async ({ email }) => {
        const cs = results(await odata('GET', '/FlamengoTorcedor/Customers', { query: { $filter: `uid eq '${String(email).toLowerCase().replace(/'/g, "''")}'`, $expand: 'groups' } }));
        if (!cs.length) throw new UserError('Torcedor não encontrado.');
        const c = cs[0];
        const groups = results(c.groups).map((g) => g.uid);
        const plans = await planList();
        const plan = plans.find((p) => p._members.includes(c.uid));
        const ts = (await tickets()).filter((t) => t.email === c.uid).map(showTicket);
        const os = (await orders()).filter((o) => o.email === c.uid).map((o) => ({ pedido: o.pedido, data: fmtDate(o.data), total: brl(o.total), itens: o.itens.map((i) => `${i.quantidade}x ${i.produto}`) }));
        const suspenso = !!plan && groups.includes(SUSPENDED_GROUP);
        return { torcedor: c.name, email: c.uid, clienteDesde: fmtDate(c.creationtime, false), plano: plan ? plan.nome : 'não é sócio', situacao: plan ? (suspenso ? 'Benefícios suspensos (mensalidade em atraso)' : 'Ativo') : null, grupos: groups, pedidos: os, chamados: ts };
      },
    },
    {
      name: 'situacao_socio',
      description: 'Situação de um sócio (ativo ou com benefícios suspensos por mensalidade em atraso) e a linha do tempo: adesão, suspensões, regularizações, cancelamento, com data, origem, quem registrou e motivo.',
      inputSchema: obj({ email: S.str('E-mail do torcedor') }, ['email']),
      handler: async ({ email }) => {
        const c = await customer(email);
        const plan = (await planList()).find((p) => p._members.includes(c.uid));
        const suspenso = !!plan && results(c.groups).some((g) => g.uid === SUSPENDED_GROUP);
        return { torcedor: c.name, email: c.uid, plano: plan ? plan.nome : 'não é sócio', situacao: plan ? (suspenso ? 'Benefícios suspensos (mensalidade em atraso): sem onda de sócio e sem descontos; mantém a vaga' : 'Ativo') : null, linhaDoTempo: await events(c.uid) };
      },
    },
    {
      name: 'suspender_socio',
      description: 'Suspende os benefícios de um sócio por mensalidade em atraso: perde a onda de sócio e os descontos na hora, continua sócio e mantém a vaga. Ingressos de jogos que ainda não abriram para a venda geral saem do carrinho dele.',
      inputSchema: obj({ email: S.str('E-mail do torcedor'), motivo: S.str('Motivo (ex.: mensalidade de outubro em atraso)'), confirmar: S.confirm }, ['email']),
      handler: async (a) => statusEvent(a, 'SUSPENSAO'),
    },
    {
      name: 'regularizar_socio',
      description: 'Regulariza um sócio suspenso (pagamento confirmado): onda de sócio e descontos voltam na hora.',
      inputSchema: obj({ email: S.str('E-mail do torcedor'), motivo: S.str('Observação (ex.: pagamento confirmado pelo financeiro)'), confirmar: S.confirm }, ['email']),
      handler: async (a) => statusEvent(a, 'REGULARIZACAO'),
    },
    {
      name: 'elegibilidade_torcedor',
      description: 'Por que um torcedor conseguiu ou não comprar ingresso: as tentativas de pôr poltrona no carrinho, com a decisão (aprovada ou negada), o motivo e as condições conferidas (plano, situação, janela da onda, biometria e limite por jogo). Sem e-mail, lista as últimas tentativas negadas de todos os torcedores.',
      inputSchema: obj({ email: S.str('E-mail do torcedor (opcional)'), jogo: S.str('Código do jogo, ex.: JOGO-20261115-BAH (opcional)'), soNegadas: S.bool('Só as tentativas negadas') }),
      handler: async ({ email, jogo, soNegadas = false }) => {
        let uid;
        if (email) uid = (await customer(email)).uid;
        let list = await decisions(uid);
        if (jogo) list = list.filter((d) => d.jogo === jogo);
        if (soNegadas || !email) list = list.filter((d) => d._negada);
        const show = list.slice(0, 20).map(({ _negada, ...d }) => d);
        const ultimaNegada = list.find((d) => d._negada);
        return {
          torcedor: email ? uid : 'todos',
          tentativas: show.length,
          resumo: !show.length
            ? 'Nenhuma tentativa registrada com esse filtro.'
            : ultimaNegada
              ? `Última negativa em ${ultimaNegada.data} (${ultimaNegada.jogo}): ${ultimaNegada.motivos.join(' ')}`
              : 'Todas as tentativas foram aprovadas.',
          lista: show,
        };
      },
    },
    {
      name: 'convidados_torcedor',
      description: 'Convidados nominais de um sócio (nome, CPF mascarado, situação e desde quando) e os ingressos comprados para eles, com quem vai em cada poltrona. Com o código do jogo, mostra quem vai no jogo (titular e convidados). Sem e-mail, lista os ingressos de convidados de todos os sócios (filtrável por jogo).',
      inputSchema: obj({ email: S.str('E-mail do sócio (opcional)'), jogo: S.str('Código do jogo, ex.: JOGO-20261115-BAH (opcional)') }),
      handler: async ({ email, jogo }) => {
        let uid;
        if (email) uid = (await customer(email)).uid;
        const lista = uid ? await guests(uid) : [];
        let ingressos = await guestTickets(uid);
        if (jogo) ingressos = ingressos.filter((t) => t.jogo === jogo);
        if (!uid) ingressos = ingressos.filter((t) => t.convidado);
        return {
          torcedor: uid ?? 'todos',
          convidados: lista.map(({ _ativo, ...g }) => g),
          ativos: lista.filter((g) => g._ativo).length,
          ingressos: ingressos.slice(0, 50).map(({ convidado, ...t }) => t),
          resumo: !ingressos.length
            ? 'Nenhum ingresso encontrado com esse filtro.'
            : `${ingressos.length} ingresso(s); para convidados: ${ingressos.filter((t) => t.convidado).map((t) => `${t.quemVai} (${t.poltrona})`).join(', ') || 'nenhum'}.`,
        };
      },
    },
    {
      name: 'chamados',
      description: 'Chamados de atendimento (filtro opcional por situação e só cancelamentos), com o motivo dos pedidos de cancelamento.',
      inputSchema: obj({ situacao: { type: 'string', enum: ['abertos', 'fechados', 'todos'], description: 'padrão: abertos' }, soCancelamentos: S.bool('Só pedidos de cancelamento de plano') }),
      handler: async ({ situacao = 'abertos', soCancelamentos = false }) => {
        let list = soCancelamentos ? await cancellations() : await tickets();
        if (situacao === 'abertos') list = list.filter((t) => t.situacao !== 'Closed');
        if (situacao === 'fechados') list = list.filter((t) => t.situacao === 'Closed');
        return { total: list.length, chamados: list.map(showTicket) };
      },
    },
    {
      name: 'detalhe_chamado',
      description: 'Mensagem completa de um chamado.',
      inputSchema: obj({ chamado: S.str('Número do chamado') }, ['chamado']),
      handler: async ({ chamado }) => {
        const t = (await tickets()).find((x) => x.id === chamado);
        if (!t) throw new UserError('Chamado não encontrado.');
        const text = await message(chamado);
        return { ...showTicket(t), mensagem: text, ...((t.assunto ?? '').startsWith(CANCEL_SUBJECT) ? parseCancelMessage(text) : {}) };
      },
    },
    {
      name: 'confirmar_cancelamento',
      description: 'Confirma um pedido de cancelamento de plano: fecha o chamado e o torcedor deixa de ser sócio na hora (perde selo, onda e descontos; a vaga volta ao plano).',
      inputSchema: obj({ chamado: S.str('Número do chamado de cancelamento'), confirmar: S.confirm }, ['chamado']),
      handler: async (a) => {
        const t = (await cancellations()).find((x) => x.id === a.chamado);
        if (!t) throw new UserError('Chamado de cancelamento não encontrado.');
        if (t.situacao === 'Closed') throw new UserError('Esse chamado já está fechado (plano já encerrado).');
        const c = needConfirm(a, `Confirmar o cancelamento de ${t.torcedor} (${t.email}) no plano ${t.plano}. Motivo: ${t.motivo ?? '—'}. O torcedor perde os benefícios na hora.`);
        if (c) return c;
        await odata('PATCH', `/FlamengoChamado/CsTickets('${enc(a.chamado)}')`, { body: { ticketID: a.chamado, state: { code: 'Closed' } } });
        const still = (await planList()).some((p) => p._members.includes(t.email));
        return { executado: true, chamado: a.chamado, situacao: 'Fechado', torcedorAindaSocio: still, resultado: still ? 'Atenção: o torcedor continua no grupo do plano.' : `${t.torcedor} deixou de ser sócio do ${t.plano}.` };
      },
    },
    {
      name: 'relatorio_cancelamentos',
      description: 'Cancelamentos pedidos na loja: aguardando atendimento, no mês, total, por motivo e por plano, com os últimos pedidos (dados reais).',
      inputSchema: obj(),
      handler: async () => {
        const list = await cancellations();
        const now = new Date();
        const monthStart = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1, 3));
        const count = (key) => list.reduce((acc, t) => ((acc[t[key] ?? '—'] = (acc[t[key] ?? '—'] ?? 0) + 1), acc), {});
        return {
          aguardandoAtendimento: list.filter((t) => t.situacao !== 'Closed').length,
          noMes: list.filter((t) => t.aberto && t.aberto >= monthStart).length,
          total: list.length,
          porMotivo: count('motivo'),
          porPlano: count('plano'),
          ultimos: list.slice(0, 10).map(showTicket),
        };
      },
    },
    {
      name: 'relatorio_adesoes',
      description: 'Adesões a planos de sócio pela loja (pedidos com plano) nos últimos N dias: por plano, receita da 1ª mensalidade e os pedidos.',
      inputSchema: obj({ dias: S.int('Janela em dias (padrão 30)') }),
      handler: async ({ dias = 30 }) => {
        const plans = await planList();
        const codes = new Set(plans.map((p) => p.code));
        const since = new Date(Date.now() - dias * 86400000);
        const os = (await orders()).filter((o) => o.data && o.data >= since && o.itens.some((i) => codes.has(i.codigo)));
        const byPlan = {};
        let revenue = 0;
        for (const o of os) for (const i of o.itens) if (codes.has(i.codigo)) { byPlan[i.produto] = (byPlan[i.produto] ?? 0) + 1; revenue += i.total ?? 0; }
        return { periodo: `últimos ${dias} dias`, adesoes: os.length, porPlano: byPlan, receitaPrimeiraMensalidade: brl(revenue), pedidos: os.map((o) => ({ pedido: o.pedido, data: fmtDate(o.data), torcedor: o.torcedor, plano: o.itens.filter((i) => codes.has(i.codigo)).map((i) => i.produto).join(', '), total: brl(o.total) })) };
      },
    },
    {
      name: 'cupons',
      description: 'Cupons de desconto da loja: ativo, limites de uso e quantas vezes cada um já foi usado.',
      inputSchema: obj(),
      handler: async () => {
        const cs = results(await odata('GET', '/FlamengoCupom/SingleCodeCoupons', { query: { $top: 200 } }));
        const uses = results(await odata('GET', '/FlamengoCupomUso/CouponRedemptions', { query: { $top: 1000, $expand: 'user,order' } }));
        const byCode = uses.reduce((acc, u) => ((acc[String(u.couponCode).toLowerCase()] = (acc[String(u.couponCode).toLowerCase()] ?? 0) + 1), acc), {});
        return {
          cupons: cs.map((c) => ({ cupom: c.couponId, ativo: c.active, usos: byCode[String(c.couponId).toLowerCase()] ?? 0, limiteTotal: c.maxTotalRedemptions, limitePorCliente: c.maxRedemptionsPerCustomer, inicio: fmtDate(c.startDate, false), fim: fmtDate(c.endDate, false) })),
          ultimosUsos: uses.slice(-10).reverse().map((u) => ({ cupom: u.couponCode, torcedor: u.user?.name, pedido: u.order?.code, data: fmtDate(u.creationtime), totalPedido: brl(u.order?.totalPrice) })),
        };
      },
    },
    {
      name: 'atualizar_cupom',
      description: 'Ativa/desativa um cupom ou muda os limites de uso e a data de fim.',
      inputSchema: obj({ cupom: S.str('Código do cupom'), ativo: S.bool('Ativo?'), limiteTotal: S.int('Máximo de usos no total'), limitePorCliente: S.int('Máximo de usos por cliente'), fim: S.str('Data de fim (AAAA-MM-DD)'), confirmar: S.confirm }, ['cupom']),
      handler: async (a) => {
        const body = { couponId: a.cupom };
        const changes = [];
        if (a.ativo != null) { body.active = a.ativo; changes.push(a.ativo ? 'ativar' : 'desativar'); }
        if (a.limiteTotal != null) { body.maxTotalRedemptions = a.limiteTotal; changes.push(`limite total ${a.limiteTotal}`); }
        if (a.limitePorCliente != null) { body.maxRedemptionsPerCustomer = a.limitePorCliente; changes.push(`limite por cliente ${a.limitePorCliente}`); }
        if (a.fim) { body.endDate = odataDate(parseLocal(a.fim + (a.fim.length <= 10 ? 'T23:59' : ''))); changes.push(`fim ${a.fim}`); }
        if (!changes.length) throw new UserError('Nada para alterar.');
        const c = needConfirm(a, `Cupom ${a.cupom}: ${changes.join('; ')}`);
        if (c) return c;
        await odata('PATCH', `/FlamengoCupom/SingleCodeCoupons('${enc(a.cupom)}')`, { body });
        return { executado: true, cupom: a.cupom, alteracoes: changes };
      },
    },
  ];
}

function jogosTools(cfg, http) {
  const odata = odataClient(cfg, http);

  const show = (g) => ({
    code: g.code, adversario: g.opponent, jogo: `Flamengo x ${g.opponent}`, data: fmtDate(g.kickoff), competicao: g.competition, estadio: g.venue,
    situacao: g.status?.code, prevendaSocio: g.presale,
    precos: Object.fromEntries(Object.entries(SECTORS).map(([s, f]) => [s, brl(g[f])])),
    poltronas: { total: g.seatsTotal, vendidas: g.seatsSold, livres: g.seatsAvailable },
    ondas: { onda1: fmtDate(g.wave1Start), onda2: fmtDate(g.wave2Start), onda3: fmtDate(g.wave3Start), vendaGeral: fmtDate(g.generalSaleStart) },
    aviso: g.statusMessage || null,
  });

  async function games() {
    const gs = await odata('GET', '/FlamengoJogo/MatchdayGames', { query: { $expand: 'status', $top: 200 } });
    return results(gs).sort((a, b) => (toDate(a.kickoff)?.getTime() ?? 0) - (toDate(b.kickoff)?.getTime() ?? 0));
  }

  async function game(code) {
    const g = (await games()).find((x) => x.code === code);
    if (!g) throw new UserError('Jogo não encontrado. Veja os códigos com a ferramenta jogos.');
    return g;
  }

  function gameBody(a, body, changes) {
    if (a.adversario) { body.opponent = a.adversario; changes.push(`adversário ${a.adversario}`); }
    if (a.dataHora) { body.kickoff = odataDate(parseLocal(a.dataHora)); changes.push(`data ${fmtDate(parseLocal(a.dataHora))}`); }
    if (a.competicao) { body.competition = a.competicao; changes.push(`competição ${a.competicao}`); }
    if (a.estadio) { body.venue = a.estadio; changes.push(`estádio ${a.estadio}`); }
    if (a.prevendaSocio != null) { body.presale = a.prevendaSocio; changes.push(a.prevendaSocio ? 'com pré-venda de sócio' : 'sem pré-venda'); }
    for (const [s, f] of Object.entries(SECTORS)) {
      const v = a[`preco${s[0]}${s.slice(1).toLowerCase()}`];
      if (v != null) { body[f] = String(v); changes.push(`${s} ${brl(v)}`); }
    }
    for (const [k, f, label] of [['onda1', 'wave1Start', 'onda 1'], ['onda2', 'wave2Start', 'onda 2'], ['onda3', 'wave3Start', 'onda 3'], ['vendaGeral', 'generalSaleStart', 'venda geral']]) {
      if (a[k]) { body[f] = odataDate(parseLocal(a[k])); changes.push(`${label} ${fmtDate(parseLocal(a[k]))}`); }
    }
    if (a.situacao) { body.status = { code: a.situacao }; changes.push(`situação ${a.situacao}`); }
  }

  const gameFields = {
    adversario: S.str('Adversário'), dataHora: S.str('Data e hora do jogo, ex. 2026-11-29 16:00 (Brasília)'), competicao: S.str('Competição, ex. "Brasileirão · Rodada 36"'),
    estadio: S.str('Estádio (padrão Maracanã)'), prevendaSocio: S.bool('Pré-venda para sócios'),
    precoNorte: S.num('Preço da inteira no setor Norte'), precoSul: S.num('Preço da inteira no setor Sul'), precoLeste: S.num('Preço da inteira no setor Leste'), precoOeste: S.num('Preço da inteira no setor Oeste'),
    onda1: S.str('Início da onda 1 (data e hora)'), onda2: S.str('Início da onda 2'), onda3: S.str('Início da onda 3'), vendaGeral: S.str('Início da venda geral'),
    situacao: { type: 'string', enum: ['ON_SALE', 'DRAFT', 'CLOSED'], description: 'ON_SALE à venda, DRAFT pausado/rascunho, CLOSED encerrado' },
  };

  return [
    {
      name: 'jogos',
      description: 'Jogos cadastrados: data, competição, situação, preços por setor, poltronas vendidas/livres e datas das ondas de venda.',
      inputSchema: obj(),
      handler: async () => ({ jogos: (await games()).map(show) }),
    },
    {
      name: 'detalhe_jogo',
      description: 'Dados completos de um jogo.',
      inputSchema: obj({ jogo: S.str('Código do jogo (JOGO-AAAAMMDD-ADV)') }, ['jogo']),
      handler: async ({ jogo }) => show(await game(jogo)),
    },
    {
      name: 'criar_jogo',
      description: 'Cadastra um jogo novo e abre a venda: o Commerce cria o produto, as poltronas e as ondas (se não informadas: 7, 6, 5 e 4 dias antes, às 10h; preços do modelo se não informados).',
      inputSchema: obj({ ...gameFields, confirmar: S.confirm }, ['adversario', 'dataHora', 'competicao']),
      handler: async (a) => {
        const kickoff = parseLocal(a.dataHora);
        const letters = String(a.adversario).normalize('NFD').replace(/[^A-Za-z]/g, '').toUpperCase();
        const ymd = new Intl.DateTimeFormat('en-CA', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' }).format(kickoff).replace(/-/g, '');
        const code = `JOGO-${ymd}-${(letters || 'ADV').slice(0, 3)}`;
        if ((await games()).some((g) => g.code === code)) throw new UserError(`Já existe o jogo ${code}. Mude a data ou o adversário.`);
        const body = { code };
        const changes = [];
        gameBody(a, body, changes);
        if (!body.status) body.status = { code: 'ON_SALE' };
        if (!body.venue) body.venue = 'Maracanã';
        if (body.presale == null) body.presale = false;
        const c = needConfirm(a, `Criar o jogo ${code} (à venda assim que criado, salvo situação informada): ${changes.join('; ')}`);
        if (c) return c;
        await odata('POST', '/FlamengoJogo/MatchdayGames', { body });
        const g = (await games()).find((x) => x.code === code);
        return { executado: true, jogo: g ? show(g) : code, loja: `${cfg.storeUrl}/matchday` };
      },
    },
    {
      name: 'atualizar_jogo',
      description: 'Altera um jogo: preços por setor, data, ondas de venda, pré-venda ou situação (DRAFT pausa a venda, ON_SALE retoma, CLOSED encerra e libera as poltronas não vendidas).',
      inputSchema: obj({ jogo: S.str('Código do jogo'), ...gameFields, confirmar: S.confirm }, ['jogo']),
      handler: async (a) => {
        const g = await game(a.jogo);
        const body = { code: g.code };
        const changes = [];
        gameBody(a, body, changes);
        if (!changes.length) throw new UserError('Nada para alterar.');
        const c = needConfirm(a, `Alterar ${g.code} (Flamengo x ${g.opponent}): ${changes.join('; ')}`);
        if (c) return c;
        await odata('PATCH', `/FlamengoJogo/MatchdayGames('${enc(g.code)}')`, { body });
        return { executado: true, jogo: show(await game(a.jogo)) };
      },
    },
    {
      name: 'vendas_ingressos',
      description: 'Venda de ingressos pela loja: por jogo e por setor (ingressos, receita e pedidos). Sem jogo informado, resume todos.',
      inputSchema: obj({ jogo: S.str('Código do jogo (opcional)') }),
      handler: async ({ jogo }) => {
        const gs = await games();
        const target = jogo ? gs.filter((g) => g.code === jogo) : gs;
        if (jogo && !target.length) throw new UserError('Jogo não encontrado.');
        const os = results(await odata('GET', '/FlamengoPedido/Orders', { query: { $expand: 'entries/product,user', $top: 500 } })).filter((o) => o.versionID == null);
        return {
          jogos: target.map((g) => {
            const bySector = {};
            let tickets = 0;
            let revenue = 0;
            const orders = new Set();
            for (const o of os) for (const e of results(o.entries)) {
              const code = e.product?.code ?? '';
              if (!code.startsWith(g.code + '-')) continue;
              const sector = code.slice(g.code.length + 1).split('-')[0];
              const q = num(e.quantity) ?? 0;
              bySector[sector] = bySector[sector] ?? { ingressos: 0, receita: 0 };
              bySector[sector].ingressos += q;
              bySector[sector].receita += num(e.totalPrice) ?? 0;
              tickets += q;
              revenue += num(e.totalPrice) ?? 0;
              orders.add(o.code);
            }
            return {
              jogo: `Flamengo x ${g.opponent}`, code: g.code, data: fmtDate(g.kickoff),
              vendidosNaLoja: tickets, receita: brl(revenue), pedidos: orders.size,
              porSetor: Object.fromEntries(Object.entries(bySector).map(([s, v]) => [s, { ingressos: v.ingressos, receita: brl(v.receita) }])),
              ocupacao: { total: g.seatsTotal, vendidas: g.seatsSold, livres: g.seatsAvailable },
            };
          }),
          observacao: '"vendidosNaLoja" conta só pedidos reais da loja; "ocupacao" inclui a carga simulada da demo.',
        };
      },
    },
  ];
}

export const _test = { parseCancelMessage, parseLocal, fmtDate, brl, toDate, UserError };
